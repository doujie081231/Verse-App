package com.verse.explorer.core.download

import android.content.Context
import android.util.Log
import com.verse.explorer.core.util.FileUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Dns
import java.io.File
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 智能混合下载客户端：
 *  - 小文件（≤1MB）多源并发竞速，首个成功即取消其他
 *  - 大文件（>5MB）并行测速选最快源，多线程分块下载
 *  - 中等文件（1MB-5MB）单流下载 + 低速检测换源
 *  - 镜像熔断：连续失败 3 次熔断 1 分钟
 *
 *  所有 URL 均通过 [MirrorSourceManager.getMirrorUrls] 生成多源列表后选取。
 *
 *  @param context 用于读取用户下载源设置（SharedPreferences）
 */
class SmartDownloadClient(
    private val context: Context? = null,
    private val source: String? = null,
    private val httpClient: OkHttpClient = defaultClient()
) {

    companion object {
        private const val TAG = "SmartDownload"

        /**
         * 全局共享热连接池：
         * 高并发小文件下载（assets 滑动窗口 64 路、libraries 32 路）时，
         * OkHttp 默认池只保留 5 个空闲连接，稳态下每个新文件几乎都要重新
         * DNS 解析 + TCP/TLS 握手；国内网络下这一握手成本远超文件本体下载，
         * 表现为"每个小文件 2-3 秒"。池容量对齐最大并发，让整个下载阶段
         * 复用同一批长连接（DNS 只解析一次）。
         */
        val sharedConnectionPool: okhttp3.ConnectionPool =
            okhttp3.ConnectionPool(128, 5, TimeUnit.MINUTES)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .connectionPool(sharedConnectionPool)
            // OkHttp 默认每 host 仅 5 个并发连接：分块下载 8 块/多源竞速都会被
            // 每 host 队列卡住，必须显式放开
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 256
                maxRequestsPerHost = 128
            })
            .dns(ipv4Only())
            .retryOnConnectionFailure(true)
            .build()

        /** 单源探测（probe）最大等待：快速剔除死源，避免拖住整个测速阶段 */
        private const val PROBE_TIMEOUT_MS = 3_000L

        /**
         * 设备处于 IPv6 无出站路由环境（如模拟器 fec0:: 黑洞、校园网某类 IPv6 半联通）时，
         * OkHttp 按 DNS 返回顺序（AAAA 优先）连接会先挂满 connectTimeout 才回退 IPv4，
         * 表现为"每个阶段卡一段时间"。直接只解析 A 记录，连接一步到 IPv4。
         *
         * 同时带 DNS 负缓存：对解析失败的 host（如 download.mcbbs.net 域名失效）记忆 60s，
         * 直接抛 UnknownHostException 让其瞬时失败，避免每次请求都陷入系统 DNS 阻塞
         * （Android getaddrinfo 对不可解析域名约 10s 超时，且 withTimeout 无法中断该阻塞）。
         */
        private val dnsFailCache = ConcurrentHashMap<String, Long>()

        fun ipv4Only(): Dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val failUntil = dnsFailCache[hostname] ?: 0L
                if (System.currentTimeMillis() < failUntil) {
                    throw java.net.UnknownHostException("$hostname: DNS fail cached")
                }
                return try {
                    InetAddress.getAllByName(hostname).filterIsInstance<Inet4Address>().toList()
                        .ifEmpty { listOfNotNull(InetAddress.getAllByName(hostname).firstOrNull()) }
                } catch (e: Exception) {
                    if (e is java.net.UnknownHostException) {
                        dnsFailCache[hostname] = System.currentTimeMillis() + 60_000L
                    }
                    throw e
                }
            }
        }

        /** 小文件竞速阈值：≤1MB */
        private const val RACE_FILE_SIZE_THRESHOLD = 1L * 1024 * 1024

        /** 分块下载阈值：>5MB */
        private const val CHUNKED_FILE_SIZE_THRESHOLD = 5L * 1024 * 1024

        /** 分块大小下限：每个分块至少 1MB */
        private const val MIN_CHUNK_SIZE = 1L * 1024 * 1024

        /** 分块数量上限 */
        private const val MAX_CHUNKS = 8

        /** 低速检测阈值：20KB/s */
        private const val LOW_SPEED_THRESHOLD = 20L * 1024

        /** 低速检测窗口：15s */
        private const val LOW_SPEED_CHECK_INTERVAL = 15_000L

        /** 镜像熔断：连续失败次数 */
        private const val MIRROR_FAILS_THRESHOLD = 3

        /** 镜像熔断时长：60s */
        private const val MIRROR_CIRCUIT_BREAK_MS = 60_000L
    }

    /** 进度回调 */
    data class Progress(
        val downloaded: Long,
        val total: Long,
        val speed: Long,
        val mirrorName: String = "",
        val message: String = ""
    )

    private data class MirrorHealth(
        val fails: AtomicInteger = AtomicInteger(0),
        val circuitOpenUntil: AtomicLong = AtomicLong(0L)
    )

    private val mirrorHealthMap = ConcurrentHashMap<String, MirrorHealth>()
    private val healthLock = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun resolveUrls(originalUrl: String): List<String> {
        return when {
            context != null -> MirrorSourceManager.getMirrorUrls(context, originalUrl)
            source != null -> MirrorSourceManager.getMirrorUrls(source, originalUrl)
            else -> listOf(originalUrl)
        }
    }

    /**
     * 智能下载入口：根据文件大小自动选择策略
     *
     *  - assets/libraries 小文件：跳过 probe，直接顺序尝试镜像列表（单流 + 短超时）
     *  - 大文件（client.jar）：probe + 分块下载
     */
    suspend fun download(
        originalUrl: String,
        targetFile: File,
        expectedSha1: String? = null,
        knownSize: Long = -1L,
        isCancelled: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = { _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()

            if (isCancelled()) throw CancelledException()

            // 已存在文件的复用判定：
            // - 已知总量且长度吻合时做完整性校验，通过则直接跳过；
            // - 长度不符（半截文件）时保留文件进入续传流程，不做整文件校验
            // （否则中断残留的 .part 会被误判损坏直接删除，无法续传）
            if (targetFile.exists() && targetFile.length() > 0) {
                val sizeComplete = if (knownSize > 0) targetFile.length() == knownSize else true
                if (expectedSha1 != null && sizeComplete) {
                    val actualSha1 = calculateSHA1(targetFile)
                    if (actualSha1.equals(expectedSha1, ignoreCase = true)) {
                        return@withContext Result.success(targetFile)
                    }
                    targetFile.delete()
                } else if (expectedSha1 == null && sizeComplete) {
                    if (targetFile.length() > 1024) {
                        return@withContext Result.success(targetFile)
                    }
                }
            }

            val urls = resolveUrls(originalUrl)
            Log.d(TAG, "Download ${targetFile.name} from ${urls.size} sources: $urls")
            com.verse.explorer.core.log.DownloadLog.write(
                "download",
                "开始: ${targetFile.name} (${if (knownSize > 0) "${knownSize / 1024}KB" else "大小未知"}, ${urls.size} 源)"
            )

            // 大文件（已知大小 >5MB）才走分块；其他全部顺序镜像列表，跳过 probe 开销
            val result = if (knownSize > CHUNKED_FILE_SIZE_THRESHOLD && urls.size > 1) {
                downloadChunkedWithProbing(urls, targetFile, expectedSha1, knownSize, isCancelled, onProgress)
            } else {
                downloadWithMirrorFallback(urls, targetFile, expectedSha1, isCancelled, onProgress)
            }

            if (isCancelled()) throw CancelledException()

            if (result) {
                if (expectedSha1 != null) {
                    val actualSha1 = calculateSHA1(targetFile)
                    if (!actualSha1.equals(expectedSha1, ignoreCase = true)) {
                        Log.e(TAG, "SHA1 mismatch for ${targetFile.name}: expected=$expectedSha1 actual=$actualSha1")
                        com.verse.explorer.core.log.DownloadLog.write(
                            "download",
                            "SHA1 校验失败: ${targetFile.name} expected=$expectedSha1 actual=$actualSha1"
                        )
                        targetFile.delete()
                        return@withContext Result.failure(Exception("SHA1 校验失败: ${targetFile.name}"))
                    }
                }

                // 损坏则删除并抛错，由上层重试（mod/库/客户端 jar 均受益）
                if (targetFile.name.endsWith(".jar") && !FileUtils.isValidJar(targetFile)) {
                    Log.e(TAG, "JAR corrupted after download: ${targetFile.name}")
                    com.verse.explorer.core.log.DownloadLog.write("download", "JAR 损坏: ${targetFile.name}")
                    targetFile.delete()
                    return@withContext Result.failure(Exception("JAR 文件损坏: ${targetFile.name}"))
                }

                com.verse.explorer.core.log.DownloadLog.write(
                    "download",
                    "完成: ${targetFile.name} (${targetFile.length() / 1024}KB)"
                )
                Result.success(targetFile)
            } else {
                com.verse.explorer.core.log.DownloadLog.write("download", "失败: ${targetFile.name}（所有镜像均不可用）")
                Result.failure(Exception("下载失败: ${targetFile.name}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error for ${targetFile.name}: ${e.message}", e)
            com.verse.explorer.core.log.DownloadLog.write(
                "download",
                "失败: ${targetFile.name} — ${e.javaClass.simpleName}: ${e.message}"
            )
            Result.failure(e)
        }
    }

    private suspend fun probeFileSize(urls: List<String>): Long {
        if (urls.isEmpty()) return -1L

        // 对齐 probeMirrorsParallel：窗口超时收集，不被死源 DNS 解析拖住
        val sizes = java.util.concurrent.ConcurrentHashMap<String, Long>()
        val start = System.currentTimeMillis()

        urls.forEach { url ->
            scope.launch {
                var size = -1L
                try {
                    val request = Request.Builder().url(url)
                        .header("User-Agent", "VerseExplorerX/1.0")
                        .header("Range", "bytes=0-0")
                        .build()
                    withTimeout(PROBE_TIMEOUT_MS) {
                        httpClient.newCall(request).execute().use { response ->
                            val contentRange = response.header("Content-Range")
                            size = if (contentRange != null) {
                                val match = Regex("""/(\d+)""").find(contentRange)
                                match?.groupValues?.get(1)?.toLongOrNull() ?: -1L
                            } else {
                                response.header("Content-Length")?.toLongOrNull() ?: -1L
                            }
                        }
                    }
                } catch (_: Exception) {
                    size = -1L
                }
                sizes[url] = size
            }
        }

        while (sizes.size < urls.size && System.currentTimeMillis() - start < PROBE_TIMEOUT_MS) {
            delay(50)
        }

        return sizes.values.maxOrNull() ?: -1L
    }

    private suspend fun probeMirrorsParallel(urls: List<String>): List<String> {
        if (urls.size <= 1) return urls

        // 超时后直接返回已完成源（未完成/死源按 0 速度排最后），
        // 避免某个源 DNS 解析阻塞（如 download.mcbbs.net 域失效约 10s）拖住整个测速阶段。
        val results = java.util.concurrent.ConcurrentHashMap<String, Double>()
        val start = System.currentTimeMillis()

        urls.forEach { url ->
            scope.launch {
                val t0 = System.currentTimeMillis()
                var speed = 0.0
                try {
                    val request = Request.Builder().url(url)
                        .header("User-Agent", "VerseExplorerX/1.0")
                        .header("Range", "bytes=0-0")
                        .build()
                    withTimeout(PROBE_TIMEOUT_MS) {
                        httpClient.newCall(request).execute().use { response ->
                            response.body?.byteStream()?.use { it.read(ByteArray(1)) }
                        }
                    }
                    val elapsed = System.currentTimeMillis() - t0
                    speed = if (elapsed > 0) 1000.0 / elapsed else 0.0
                } catch (_: Exception) {
                    speed = 0.0
                }
                results[url] = speed
            }
        }

        while (results.size < urls.size && System.currentTimeMillis() - start < PROBE_TIMEOUT_MS) {
            delay(50)
        }

        return urls.sortedByDescending { results[it] ?: 0.0 }
    }

    private suspend fun downloadRace(
        urls: List<String>,
        targetFile: File,
        expectedSha1: String?,
        totalSize: Long,
        onProgress: (Progress) -> Unit
    ): Boolean {
        val winner = CompletableDeferred<Int>()
        val tempFiles = urls.mapIndexed { idx, _ -> File(targetFile.parentFile, "${targetFile.name}.race-$idx") }
        tempFiles.forEach { it.delete() }

        val jobs = urls.mapIndexed { idx, url ->
            scope.async {
                try {
                    val tempFile = tempFiles[idx]
                    val request = Request.Builder().url(url)
                        .header("User-Agent", "VerseExplorerX/1.0")
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        response.body?.byteStream()?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }

                    if (expectedSha1 != null) {
                        val actualSha1 = calculateSHA1(tempFile)
                        if (!actualSha1.equals(expectedSha1, ignoreCase = true)) {
                            throw Exception("SHA1 mismatch")
                        }
                    }

                    if (winner.complete(idx)) {
                        markMirrorSuccess(url)
                    } else {
                        tempFile.delete()
                    }
                    idx
                } catch (e: Exception) {
                    markMirrorFail(url)
                    -1
                }
            }
        }

        val winnerIdx = try { winner.await() } catch (_: Exception) { -1 }

        // 竞速：winner 已产生，其余任务（含 DNS 阻塞的死源）不再等待，
        // 直接清理临时文件，避免被慢源拖住整个下载阶段。
        jobs.forEach { it.cancel() }

        tempFiles.forEachIndexed { idx, file -> if (idx != winnerIdx) file.delete() }

        if (winnerIdx < 0) {
            Log.e(TAG, "Race failed: all mirrors failed for ${targetFile.name}")
            return false
        }

        val winnerFile = tempFiles[winnerIdx]
        targetFile.delete()
        if (!winnerFile.renameTo(targetFile)) {
            winnerFile.copyTo(targetFile, overwrite = true)
            winnerFile.delete()
        }

        onProgress(Progress(totalSize, totalSize, 0, urls[winnerIdx], "完成"))
        return true
    }

    private suspend fun downloadChunkedWithProbing(
        urls: List<String>,
        targetFile: File,
        expectedSha1: String?,
        totalSize: Long,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit
    ): Boolean {
        val availableUrls = filterAvailableMirrors(urls)
        if (availableUrls.isEmpty()) return false

        val rankedUrls = probeMirrorsParallel(availableUrls)
        Log.d(TAG, "Chunked download ranked: $rankedUrls")

        for (url in rankedUrls) {
            if (isCancelled()) return false
            val success = try {
                downloadChunked(url, targetFile, totalSize, isCancelled, onProgress)
            } catch (e: CancelledException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Chunked download from $url failed: ${e.message}")
                false
            }
            if (success) {
                markMirrorSuccess(url)
                if (expectedSha1 != null) {
                    val actualSha1 = calculateSHA1(targetFile)
                    if (actualSha1.equals(expectedSha1, ignoreCase = true)) {
                        return true
                    } else {
                        Log.w(TAG, "SHA1 mismatch from $url, trying next mirror")
                        targetFile.delete()
                    }
                } else {
                    return true
                }
            } else {
                markMirrorFail(url)
            }
        }

        Log.w(TAG, "All chunked downloads failed, fallback to single-stream for ${targetFile.name}")
        return downloadWithMirrorFallback(urls, targetFile, expectedSha1, isCancelled, onProgress)
    }

    private suspend fun downloadChunked(
        url: String,
        targetFile: File,
        totalSize: Long,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit
    ): Boolean {
        val chunkCount = calculateChunkCount(totalSize)
        val chunkSize = totalSize / chunkCount
        val chunks = mutableListOf<Pair<Long, Long>>()
        for (i in 0 until chunkCount) {
            val start = i * chunkSize
            val end = if (i == chunkCount - 1) totalSize - 1 else (i + 1) * chunkSize - 1
            chunks.add(start to end)
        }

        val tempDir = File(targetFile.parentFile, ".download_chunks")
        tempDir.mkdirs()
        val chunkFiles = chunks.mapIndexed { idx, _ -> File(tempDir, "${targetFile.name}.chunk_$idx") }

        // 断点续传：分块文件跨会话复用。布局标记（总量+分块数）一致才复用，
        // 否则（URL 换了总量或分块数变化）清空全部旧分块重建
        val metaFile = File(tempDir, "${targetFile.name}.meta")
        val metaExpected = "$totalSize:$chunkCount"
        if (metaFile.exists() && metaFile.readText().trim() != metaExpected) {
            chunkFiles.forEach { it.delete() }
        }
        runCatching { metaFile.writeText(metaExpected) }

        val chunkProgress = ConcurrentHashMap<Int, Long>()
        val completedChunks = ConcurrentHashMap<Int, Boolean>()
        val failed = AtomicBoolean(false)

        // 已完成的分块（长度吻合）计入初始进度，续传时进度从已下载量起步
        chunks.forEachIndexed { idx, (start, end) ->
            val f = chunkFiles[idx]
            if (f.isFile && f.length() >= (end - start + 1)) {
                chunkProgress[idx] = end - start + 1
            } else if (f.isFile) {
                chunkProgress[idx] = f.length()
            }
        }

        val jobs = chunks.mapIndexed { idx, (start, end) ->
            scope.async {
                try {
                    downloadChunkRange(url, chunkFiles[idx], start, end, isCancelled)
                    chunkProgress[idx] = end - start + 1
                    completedChunks[idx] = true
                } catch (e: Exception) {
                    failed.set(true)
                    Log.w(TAG, "Chunk $idx failed: ${e.message}")
                }
            }
        }

        val monitorJob = scope.launch {
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L
            while (completedChunks.size < chunks.size && !failed.get()) {
                delay(300)
                val current = chunkProgress.values.sum()
                val now = System.currentTimeMillis()
                val speed = if (now - lastTime > 0) (current - lastBytes) * 1000 / (now - lastTime) else 0
                onProgress(Progress(current, totalSize, speed, url, "分块下载中"))
                lastTime = now
                lastBytes = current
            }
        }

        jobs.forEach { it.join() }
        monitorJob.cancel()

        if (isCancelled()) {
            // 取消：保留分块文件供下次续传
            throw CancelledException()
        }

        if (failed.get() || completedChunks.size < chunks.size) {
            // 失败：保留已完成分块，重试时从未完成分块继续
            return false
        }

        RandomAccessFile(targetFile, "rw").use { raf ->
            chunkFiles.forEach { chunk ->
                if (isCancelled()) throw CancelledException()
                chunk.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        chunkFiles.forEach { it.delete() }
        metaFile.delete()
        tempDir.delete()

        onProgress(Progress(totalSize, totalSize, 0, url, "完成"))
        return true
    }

    private fun calculateChunkCount(totalSize: Long): Int {
        val bySize = (totalSize / MIN_CHUNK_SIZE).toInt().coerceAtMost(MAX_CHUNKS)
        return bySize.coerceAtLeast(1)
    }

    private fun downloadChunkRange(url: String, chunkFile: File, start: Long, end: Long, isCancelled: () -> Boolean) {
        val expected = end - start + 1

        // 断点续传：分块已有数据时从其末尾继续（起点 = start + 分块文件长度）
        var downloadedBytes = 0L
        if (chunkFile.exists() && chunkFile.length() > 0) {
            downloadedBytes = chunkFile.length()
            if (downloadedBytes >= expected) return
        }

        val request = Request.Builder().url(url)
            .header("User-Agent", "VerseExplorerX/1.0")
            .header("Range", "bytes=${start + downloadedBytes}-$end")
            .build()
        httpClient.newCall(request).execute().use { response ->
            // 200：服务端不支持 Range，响应体是完整文件——丢弃已有分块，
            // 跳过前 start 字节后重新读取本分块区间
            if (response.code == 200) {
                downloadedBytes = 0L
                chunkFile.delete()
            } else if (!response.isSuccessful && response.code != 206) {
                throw Exception("HTTP ${response.code} for range $start-$end")
            }
            response.body?.byteStream()?.use { input ->
                RandomAccessFile(chunkFile, "rw").use { output ->
                    if (downloadedBytes > 0) {
                        output.seek(downloadedBytes)
                    } else {
                        output.setLength(0)
                    }
                    // 200 时先跳过本分块区间之前的字节
                    var skipped = 0L
                    val skipTarget = if (response.code == 200) start else 0L
                    while (skipped < skipTarget) {
                        val n = input.skip(minOf(8192L, skipTarget - skipped))
                        if (n <= 0) break
                        skipped += n
                    }
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var written = downloadedBytes
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) throw CancelledException()
                        if (written >= expected) break
                        output.write(buffer, 0, bytesRead)
                        written += bytesRead
                    }
                }
            } ?: throw Exception("Empty body")
        }
    }

    private suspend fun downloadWithMirrorFallback(
        urls: List<String>,
        targetFile: File,
        expectedSha1: String?,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit
    ): Boolean {
        val availableUrls = filterAvailableMirrors(urls)
        if (availableUrls.isEmpty()) return false

        var lastError: Exception? = null
        for (url in availableUrls) {
            if (isCancelled()) return false
            try {
                // 小文件直接写目标文件，避免 .tmp rename 的 IO 开销
                val success = downloadSingleStreamWithLowSpeedDetection(url, targetFile, isCancelled, onProgress, url)
                if (success) {
                    if (expectedSha1 != null) {
                        val actualSha1 = calculateSHA1(targetFile)
                        if (!actualSha1.equals(expectedSha1, ignoreCase = true)) {
                            targetFile.delete()
                            markMirrorFail(url)
                            Log.w(TAG, "SHA1 mismatch from $url, trying next mirror")
                            continue
                        }
                    }
                    markMirrorSuccess(url)
                    return true
                } else {
                    markMirrorFail(url)
                }
            } catch (e: CancelledException) {
                // 取消：保留半截文件供下次续传
                throw e
            } catch (e: Exception) {
                lastError = e
                markMirrorFail(url)
                Log.w(TAG, "Mirror $url failed: ${e.message}")
            }
        }
        if (lastError != null) Log.e(TAG, "All mirrors failed: ${lastError.message}")
        return false
    }

    private suspend fun downloadSingleStreamWithLowSpeedDetection(
        url: String,
        targetFile: File,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit,
        mirrorName: String
    ): Boolean = withContext(Dispatchers.IO) {
        // 断点续传：目标存在半截数据时从其末尾字节续传（服务端 206 才续，200 全量重来）
        val resumeFrom = if (targetFile.exists() && targetFile.length() > 0) targetFile.length() else 0L

        val request = Request.Builder().url(url)
            .header("User-Agent", "VerseExplorerX/1.0")
            .apply { if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-") }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("Empty body")

            // 200：服务端不支持 Range（或没有半截数据），清空重来
            val resuming = response.code == 206 && resumeFrom > 0
            if (!resuming && resumeFrom > 0) {
                targetFile.delete()
            }
            // 总量优先取 Content-Range 的完整长度（206 时 contentLength 只是剩余量）
            val totalSize = if (resuming) {
                Regex("""/(\d+)""").find(response.header("Content-Range") ?: "")?.groupValues?.get(1)?.toLongOrNull()
                    ?: (resumeFrom + body.contentLength().coerceAtLeast(0L))
            } else {
                body.contentLength().coerceAtLeast(-1L)
            }

            var downloaded = if (resuming) resumeFrom else 0L
            var lastCheckTime = System.currentTimeMillis()
            var lastCheckBytes = downloaded
            var lastReportTime = System.currentTimeMillis()
            var lastReportBytes = downloaded

            body.byteStream().use { input ->
                // 续传：定位到半截文件末尾追加；全新下载：截空后从头写
                RandomAccessFile(targetFile, "rw").use { raf ->
                    if (resuming) {
                        raf.seek(downloaded)
                    } else {
                        raf.setLength(0)
                        raf.seek(0)
                    }
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) throw CancelledException()
                        raf.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val now = System.currentTimeMillis()

                        if (now - lastReportTime >= 300) {
                            val speed = if (now - lastReportTime > 0) {
                                (downloaded - lastReportBytes) * 1000 / (now - lastReportTime)
                            } else 0L
                            onProgress(Progress(downloaded, totalSize, speed, mirrorName, "下载中"))
                            lastReportTime = now
                            lastReportBytes = downloaded
                        }

                        if (now - lastCheckTime >= LOW_SPEED_CHECK_INTERVAL) {
                            val windowBytes = downloaded - lastCheckBytes
                            val windowMs = now - lastCheckTime
                            val speedBps = if (windowMs > 0) windowBytes * 1000 / windowMs else 0
                            // 不设已下载量门槛：小文件（库 jar 多为几十 KB）卡流时
                            // 永远达不到大门槛，readTimeout 又被 trickle 字节喂着不触发 → 整批挂住。
                            // 15s 窗口 <20KB/s 即换源，半截文件由下一源续传。
                            if (speedBps < LOW_SPEED_THRESHOLD) {
                                Log.w(TAG, "Low speed detected from $mirrorName: ${speedBps / 1024}KB/s, switching mirror")
                                throw LowSpeedException(speedBps)
                            }
                            lastCheckTime = now
                            lastCheckBytes = downloaded
                        }
                    }
                }
            }

            onProgress(Progress(downloaded, downloaded.coerceAtLeast(totalSize), 0, mirrorName, "完成"))
            true
        }
    }

    private class LowSpeedException(val speedBps: Long) : Exception("Low speed: ${speedBps / 1024}KB/s")

    /** 用户取消下载：抛出后由上层捕获，不再重试 */
    private class CancelledException : Exception("下载已取消")

    private suspend fun filterAvailableMirrors(urls: List<String>): List<String> {
        val now = System.currentTimeMillis()
        return urls.filter { url ->
            val health = mirrorHealthMap[url]
            if (health == null) return@filter true
            val until = health.circuitOpenUntil.get()
            if (until > 0 && now < until) {
                Log.d(TAG, "Mirror $url is in circuit break, skip")
                false
            } else {
                if (until > 0 && now >= until) {
                    health.fails.set(0)
                    health.circuitOpenUntil.set(0L)
                }
                true
            }
        }
    }

    private suspend fun markMirrorFail(url: String) {
        healthLock.withLock {
            val health = mirrorHealthMap.getOrPut(url) { MirrorHealth() }
            val fails = health.fails.incrementAndGet()
            if (fails >= MIRROR_FAILS_THRESHOLD) {
                health.circuitOpenUntil.set(System.currentTimeMillis() + MIRROR_CIRCUIT_BREAK_MS)
                Log.w(TAG, "Mirror $url circuit broken for ${MIRROR_CIRCUIT_BREAK_MS / 1000}s (fails=$fails)")
            }
        }
    }

    private fun markMirrorSuccess(url: String) {
        val health = mirrorHealthMap[url] ?: return
        health.fails.set(0)
        health.circuitOpenUntil.set(0L)
    }

    private fun calculateSHA1(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun shutdown() {
        scope.cancel()
    }
}
