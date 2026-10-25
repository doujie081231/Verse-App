package com.verse.explorer.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 应用内更新 APK 下载器。
 *
 * 下载源由 [UpdateSources.part1Urls] 给出（Gitee 主、GitHub 备），**逐卷回退**：
 * 对每个分卷先试 Gitee，失败/被拦再试 GitHub；全部源都 404 才视为分卷结束。
 * 分卷在本地按序拼接为完整 APK 后交由调用方触发系统安装器。
 *
 * - 单文件 URL（不以 .part1 结尾）同样支持：按候选顺序试完即止；
 * - 进度回调 (downloaded, total, speed)，total 探测不到时为 -1；
 * - cancel() 可中断；失败/中断只留 .part 临时文件，不残留"看起来完整"的包。
 */
object UpdateDownloader {

    private const val APK_DIR = "updates"
    private const val APK_NAME = "Verse-update.apk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 当前进行中的请求（下载或大小探测）；取消下载即中断它 */
    private val activeCall = AtomicReference<okhttp3.Call?>(null)

    /** 用户取消标记：探测阶段没有活跃下载连接时靠它中止循环 */
    @Volatile
    private var cancelRequested = false

    fun cancel() {
        cancelRequested = true
        activeCall.getAndSet(null)?.cancel()
    }

    private fun throwIfCancelled() {
        if (cancelRequested) throw CancellationException("已取消")
    }

    fun apkFile(context: Context): File = File(File(context.cacheDir, APK_DIR), APK_NAME)

    /**
     * 下载更新包，成功返回完整 APK 文件，失败抛异常（取消抛 IOException）。
     * @param part1Urls 分卷第一卷（或单文件）的候选 URL 列表，顺序即优先级
     */
    suspend fun download(
        context: Context,
        part1Urls: List<String>,
        onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        cancelRequested = false
        val dir = File(context.cacheDir, APK_DIR)
        if (!dir.isDirectory) dir.mkdirs()
        val destFile = apkFile(context)
        destFile.delete()

        val startedAt = System.currentTimeMillis()
        var downloadedAll = 0L
        val multiPart = part1Urls.any { it.endsWith(".part1") }

        if (multiPart) {
            // 分卷：逐卷下载并拼接
            val grandTotal = probeGrandTotal(part1Urls)
            java.io.FileOutputStream(destFile).use { output ->
                var n = 1
                while (true) {
                    throwIfCancelled()
                    val candidates = part1Urls.map { it.replace(Regex("\\.part1$"), ".part$n") }
                    val partFile = File(dir, "$APK_NAME.part$n")
                    var ended = false
                    try {
                        var bytes: File? = null
                        var lastError: Exception? = null
                        for (url in candidates) {
                            throwIfCancelled()
                            try {
                                bytes = downloadSingle(url, partFile) { d, _, s ->
                                    onProgress(downloadedAll + d, grandTotal, s)
                                }
                                if (bytes != null) break
                                // 该源 404：记为该源缺卷，继续试下一源
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: IOException) {
                                // 取消（用户中断）直接上抛
                                throw e
                            } catch (e: Exception) {
                                lastError = e
                            }
                        }
                        val part = bytes
                        if (part == null) {
                            if (lastError != null && n == 1) {
                                // 第一卷在所有源上都失败：真错误，停止启动更新
                                throw IllegalStateException("所有下载源均失败：${lastError.message}")
                            }
                            // 后续卷所有源都 404/失败视为结束；失败仅发生在尾卷时保守结束
                            ended = true
                        } else {
                            part.inputStream().use { it.copyTo(output) }
                            downloadedAll += part.length()
                        }
                    } finally {
                        partFile.delete()
                    }
                    if (ended) break
                    n++
                }
            }
            if (destFile.length() <= 0) throw IllegalStateException("未收到任何数据")
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1)
            onProgress(downloadedAll, grandTotal, downloadedAll * 1000 / elapsed)
            destFile
        } else {
            // 单文件：按候选顺序试
            var lastError: Exception? = null
            for (url in part1Urls) {
                throwIfCancelled()
                val tmp = File(dir, "$APK_NAME.part")
                try {
                    val f = downloadSingle(url, tmp) { d, t, s -> onProgress(d, t, s) }
                    if (f != null && f.renameTo(destFile)) return@withContext destFile
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                } finally {
                    tmp.delete()
                }
            }
            throw IllegalStateException(lastError?.message ?: "所有下载源均失败")
        }
    }

    /** 用各源 HEAD 探测分卷总大小；全部失败返回 -1（进度条只显示已下载量） */
    private fun probeGrandTotal(part1Urls: List<String>): Long {
        for (first in part1Urls) {
            throwIfCancelled()
            var total = 0L
            var any = false
            var n = 1
            while (n <= 8) { // 上限保护：最多探测 8 卷
                throwIfCancelled()
                val size = probeSize(first.replace(Regex("\\.part1$"), ".part$n"))
                if (size <= 0) break
                total += size
                any = true
                n++
            }
            if (any) return total
        }
        return -1L
    }

    /** 下载单个文件；404 返回 null（该源缺此卷），网络错误抛异常，成功返回目标文件 */
    private fun downloadSingle(url: String, target: File, onProgress: (Long, Long, Long) -> Unit): File? {
        val call = client.newCall(Request.Builder().url(url).build())
        activeCall.set(call)
        try {
            call.execute().use { resp ->
                if (resp.code == 404) return null
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val body = resp.body ?: throw IllegalStateException("空响应体")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    var downloaded = 0L
                    var lastReport = 0L
                    val startedAt = System.currentTimeMillis()
                    java.io.FileOutputStream(target).use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            throwIfCancelled()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 200) {
                                lastReport = now
                                val elapsed = (now - startedAt).coerceAtLeast(1)
                                onProgress(downloaded, total, downloaded * 1000 / elapsed)
                            }
                        }
                        output.flush()
                    }
                    if (total > 0 && downloaded != total) {
                        throw IllegalStateException("下载不完整：$downloaded/$total")
                    }
                    if (downloaded <= 0) return null
                }
                return target
            }
        } catch (e: IOException) {
            // OkHttp 取消连接时抛 IOException("Canceled")，转成取消异常交给 UI 静默处理
            if (cancelRequested) throw CancellationException("已取消")
            throw e
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    /** HEAD 探测远端文件大小；不可得返回 -1 */
    private fun probeSize(url: String): Long {
        if (!url.startsWith("http")) return -1
        val call = client.newCall(Request.Builder().url(url).head().build())
        activeCall.set(call)
        return try {
            call.execute().use { resp ->
                if (resp.isSuccessful) resp.header("Content-Length")?.toLongOrNull() ?: -1 else -1
            }
        } catch (_: Exception) {
            throwIfCancelled()
            -1
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }
}
