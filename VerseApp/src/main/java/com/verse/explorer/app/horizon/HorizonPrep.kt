package com.verse.explorer.app.horizon

import android.content.Context
import com.google.gson.JsonParser
import com.verse.explorer.core.java.JavaRuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Rust 版前置准备：在手机上把 Horizon 需要的派生包生成出来。
 *
 * 上游 prepare 是 Rust 桌面二进制，手机跑不了，但它做的只是「下 server.jar → 跑官方数据生成器
 * → 挑四样产物打包」。启动器自带 java25 与下载能力，所以这段可以完整复刻，用户不需要电脑。
 *
 * 流程：26.3 版本 JSON → server.jar(62MB) → datagen（Java 25）→ horizon-derived.zip
 *      → 交给引擎 nativeStart 下载约 700MB 游戏文件。
 */
object HorizonPrep {

    enum class Stage { CHECK, VERSION_JSON, SERVER_JAR, DATAGEN, PACK, ENGINE, READY }

    data class Progress(
        val stage: Stage,
        val text: String,
        val fraction: Float
    )

    private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val TARGET_VERSION = "26.3"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Horizon 的游戏家目录（已与启动器其它数据隔离） */
    fun home(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "horizon").apply { mkdirs() }
    }

    fun derivedZip(context: Context): File = File(home(context), "horizon-derived.zip")

    /** 派生包已就绪（引擎 nativeStart 会用它） */
    fun hasDerivedPack(context: Context): Boolean = derivedZip(context).isFile

    /** 引擎侧是否已准备完毕（游戏文件齐了） */
    fun isEngineReady(context: Context): Boolean =
        runCatching { dev.horizonlab.horizon.SetupActivity.nativeReady(home(context).absolutePath) == 1 }
            .getOrDefault(false)

    /**
     * 生成派生包。已存在则直接返回。
     */
    suspend fun ensureDerivedPack(
        context: Context,
        onProgress: (Progress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val zip = derivedZip(context)
            if (zip.isFile && zip.length() > 0) {
                onProgress(Progress(Stage.PACK, "派生包已就绪", 1f))
                return@runCatching zip
            }

            onProgress(Progress(Stage.VERSION_JSON, "读取 26.3 版本信息…", 0f))
            val versionJson = fetchVersionJson()
            val serverJarInfo = HorizonDataPack.parseServerJar(versionJson)
                ?: error("26.3 版本信息里没有 server.jar 下载地址")

            val work = File(home(context), "prep-work").apply { mkdirs() }
            val serverJar = File(work, "server.jar")

            if (!serverJar.isFile || serverJar.length() != serverJarInfo.size) {
                downloadServerJar(serverJarInfo, serverJar) { done, total ->
                    val fraction = if (total > 0) done.toFloat() / total else 0f
                    onProgress(
                        Progress(
                            Stage.SERVER_JAR,
                            "下载 server.jar ${done / 1048576} / ${total / 1048576} MB",
                            fraction
                        )
                    )
                }
            }

            val java = JavaRuntimeManager.getJavaExecutable(context, "java25")
                ?: error("没有找到 Java 25 运行时，请先在「Java」页安装")

            onProgress(Progress(Stage.DATAGEN, "正在生成游戏数据（约 2-5 分钟）…", 0f))
            runDataGenerator(java, serverJar, work) { line ->
                onProgress(Progress(Stage.DATAGEN, line.take(80), -1f))
            }

            onProgress(Progress(Stage.PACK, "打包派生数据…", 0.5f))
            val reports = File(work, "out/reports")
            val entries = HorizonDataPack.buildDerivedZip(reports, zip)
            if (entries == 0) error("数据生成没有产出可打包的文件，请查看日志")
            work.deleteRecursively()
            onProgress(Progress(Stage.PACK, "派生包已生成（$entries 个文件）", 1f))
            zip
        }
    }

    /** 交给引擎下载/解包约 700MB 游戏文件，进度来自 nativeStatus() */
    suspend fun runEngineSetup(
        context: Context,
        onProgress: (Progress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val home = home(context)
            if (isEngineReady(context)) {
                onProgress(Progress(Stage.READY, "游戏文件已就绪", 1f))
                return@runCatching
            }
            onProgress(Progress(Stage.ENGINE, "正在准备游戏文件（约 700MB）…", 0f))
            // nativeStart 会阻塞到准备结束；进度靠 nativeStatus 轮询
            Thread {
                runCatching {
                    dev.horizonlab.horizon.SetupActivity.nativeStart(home.absolutePath, "")
                }
            }.start()

            while (true) {
                val status = runCatching {
                    dev.horizonlab.horizon.SetupActivity.nativeStatus()
                }.getOrNull()
                val parsed = parseStatus(status)
                if (parsed != null) {
                    val (stageLabel, fraction, state, error) = parsed
                    when (state) {
                        "done" -> {
                            onProgress(Progress(Stage.READY, "游戏文件已就绪", 1f))
                            return@runCatching
                        }
                        "error" -> error(error ?: "准备失败：$stageLabel")
                        else -> onProgress(Progress(Stage.ENGINE, stageLabel, fraction))
                    }
                }
                Thread.sleep(400)
                if (isEngineReady(context)) {
                    onProgress(Progress(Stage.READY, "游戏文件已就绪", 1f))
                    return@runCatching
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Unit
        }
    }

    /** nativeStatus 的制表符格式：stage / files / filesTotal / bytes / bytesTotal / state / error */
    internal fun parseStatus(raw: String?): StatusSnapshot? {
        val parts = raw?.split("\t") ?: return null
        if (parts.size < 7) return null
        val files = parts[1].toLongOrNull() ?: 0L
        val filesTotal = parts[2].toLongOrNull() ?: 0L
        val bytes = parts[3].toLongOrNull() ?: 0L
        val bytesTotal = parts[4].toLongOrNull() ?: 0L
        val fraction = when {
            bytesTotal > 0 -> (bytes.toFloat() / bytesTotal).coerceIn(0f, 1f)
            filesTotal > 0 -> (files.toFloat() / filesTotal).coerceIn(0f, 1f)
            else -> 0f
        }
        val label = when (parts[0]) {
            "preparing" -> "准备中"
            "download" -> "下载游戏文件"
            "unpack" -> "解包"
            "sounds" -> "音效、字体与语言"
            "derived" -> "Horizon 数据"
            "ready" -> "就绪"
            else -> parts[0]
        }
        val detail = when {
            bytesTotal > 0 -> "$label ${bytes / 1048576} / ${bytesTotal / 1048576} MB"
            filesTotal > 0 -> "$label $files / $filesTotal 个文件"
            else -> label
        }
        return StatusSnapshot(detail, fraction, parts[5], parts[6].takeIf { it.isNotBlank() })
    }

    data class StatusSnapshot(
        val label: String,
        val fraction: Float,
        val state: String,
        val error: String?
    )

    // ---------- 内部 ----------

    private fun fetchVersionJson(): com.google.gson.JsonObject {
        val manifest = get(MANIFEST_URL)
        val versions = JsonParser.parseString(manifest).asJsonObject.getAsJsonArray("versions")
        val entry = versions.map { it.asJsonObject }
            .firstOrNull { it.get("id")?.asString == TARGET_VERSION }
            ?: error("官方清单里没有 $TARGET_VERSION")
        val url = entry.get("url")?.asString ?: error("版本条目缺少 json 地址")
        return JsonParser.parseString(get(url)).asJsonObject
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            return resp.body?.string() ?: error("空响应")
        }
    }

    private fun downloadServerJar(
        info: HorizonDataPack.ServerJar,
        dest: File,
        onProgress: (Long, Long) -> Unit
    ) {
        dest.parentFile?.mkdirs()
        val request = Request.Builder().url(info.url).build()
        client.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            val body = resp.body ?: error("空响应")
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var done = 0L
                    var lastTick = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (done - lastTick >= 1024 * 1024) {
                            lastTick = done
                            onProgress(done, total)
                        }
                    }
                    onProgress(done, total)
                }
            }
        }
    }

    /** 跑官方数据生成器；返回后 reports 目录即有产物 */
    private fun runDataGenerator(
        java: File,
        serverJar: File,
        workDir: File,
        onLine: (String) -> Unit
    ) {
        val command = listOf(
            java.absolutePath,
            "-Xmx2G",
            "-DbundlerMainClass=net.minecraft.data.Main",
            "-jar",
            serverJar.absolutePath,
            "--reports",
            "--output",
            "out"
        )
        val process = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) onLine(line.trim())
            }
        }
        val code = process.waitFor()
        if (code != 0) error("数据生成器退出码 $code")
    }
}
