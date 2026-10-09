package com.verse.explorer.core.install

import android.content.Context
import android.os.Build
import android.util.Log
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.download.DownloadManager
import com.verse.explorer.core.download.MirrorSourceManager
import com.verse.explorer.core.download.SmartDownloadClient
import com.verse.explorer.core.version.MinecraftVersion
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

data class InstallProgress(
    val stage: InstallStage,
    val message: String,
    val progress: Float = 0f,
    val currentFile: String = "",
    val mirrorName: String = "",
    val speed: Long = 0L,
    val downloaded: Long = 0L,
    val totalBytes: Long = 0L
)

enum class InstallStage {
    PREPARING,
    DOWNLOADING_VERSION,
    DOWNLOADING_CLIENT,
    DOWNLOADING_LIBRARIES,
    DOWNLOADING_ASSETS,
    EXTRACTING_NATIVES,
    COMPLETED,
    FAILED
}

class GameInstaller(private val context: Context) {

    private val gameDir = GameConfig.getGameDir(context)
    private val versionsDir = GameConfig.getVersionsDir(gameDir)
    private val librariesDir = GameConfig.getLibrariesDir(gameDir)
    private val assetsDir = GameConfig.getAssetsDir(gameDir)
    private val nativesDir = GameConfig.getNativesDir(gameDir)

    private val downloadManager = DownloadManager()
    private val gson = GsonBuilder().setLenient().serializeNulls().create()

    // 智能混合下载客户端：小文件竞速、大文件测速分块、低速换源、镜像熔断
    private val smartClient = SmartDownloadClient(context = context)

    // 专用于 assets 小文件下载的快速客户端（短超时，对齐 PC 端 30s 策略）
    private val fastHttpClient = downloadManager.httpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .dns(SmartDownloadClient.ipv4Only())
        .build()

    private val isArm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")
    private val isArm32 = Build.SUPPORTED_ABIS.contains("armeabi-v7a")

    // 全局下载聚合：各阶段实时累计已下载字节与速度（供进度 UI 显示）
    private val downloadAgg = DownloadAggregator()

    // assets 小文件并发数（对齐 PC 端默认 64 线程）
    private val concurrentAssets = 64

    @Volatile
    var cancelled = false

    fun cancel() {
        cancelled = true
    }

    private fun checkCancelled() {
        if (cancelled) throw CancelledInstallException()
    }

    class CancelledInstallException : Exception("安装已取消")

    /** 并发下载聚合器：按文件累计已下载字节，500ms 窗口估算速度（供进度 UI 显示） */
    private class DownloadAggregator {
        private val lastByKey = ConcurrentHashMap<String, Long>()
        private val bytesNow = AtomicLong()
        private val lastTime = AtomicLong(System.currentTimeMillis())
        private val lastBytes = AtomicLong()

        @Volatile var speed: Long = 0L; private set
        @Volatile var downloaded: Long = 0L; private set

        fun reset() {
            lastByKey.clear()
            bytesNow.set(0L)
            downloaded = 0L
            speed = 0L
            lastTime.set(System.currentTimeMillis())
            lastBytes.set(0L)
        }

        fun report(key: String, fileDownloaded: Long) {
            val last = lastByKey.put(key, fileDownloaded) ?: 0L
            if (fileDownloaded <= last) return
            val total = bytesNow.addAndGet(fileDownloaded - last)
            downloaded = total
            val now = System.currentTimeMillis()
            val dt = now - lastTime.get()
            if (dt >= 500) {
                val db = total - lastBytes.get()
                speed = db * 1000 / dt
                lastBytes.set(total)
                lastTime.set(now)
            }
        }
    }

    suspend fun installVersion(
        version: MinecraftVersion,
        installName: String = version.id,
        loaderType: String? = null,
        loaderVersion: String? = null,
        progressCallback: (InstallProgress) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                progressCallback(InstallProgress(InstallStage.PREPARING, "准备安装..."))

                // Pojav 语义（.pojav-src modloaders/ForgeDownloadTask、FabriclikeDownloadTask 实证）：
                // 原版基础版本永远落盘真实 MC 版本号目录，加载器版本由安装器生成独立 id。
                // 带加载器安装时，基础原版固定写入 versions/<version.id>/（可跨实例复用，
                // 已存在则整个下载阶段按文件级 SHA 校验跳过），加载器版本 id = 命名弹窗的
                // 实例名（见 installFcl 的 newVersionId），经 inheritsFrom/jar 引用基础版本。
                // 仅原版安装时实例名即版本目录名：json 的 id/jar 与 JAR 文件名同步写回，
                // 否则 getVersion 会因目录名与 json id 不一致报 'xxx does not exist'。
                val installId = installName.ifBlank { version.id }
                val baseId = if (loaderType.isNullOrBlank() || loaderVersion.isNullOrBlank()) installId else version.id
                val versionDir = File(versionsDir, baseId)
                if (!versionDir.exists()) versionDir.mkdirs()

                val versionJsonFile = File(versionDir, "$baseId.json")
                var versionJson: JsonObject? = if (versionJsonFile.exists()) {
                    try { gson.fromJson(versionJsonFile.readText(), JsonObject::class.java) } catch (_: Exception) { null }
                } else null

                if (versionJson == null) {
                    progressCallback(InstallProgress(InstallStage.DOWNLOADING_VERSION, "下载版本信息...", 0.02f))
                    var lastError: Exception? = null
                    val failedMirrors = mutableListOf<String>()
                    for (mirrorIndex in GameConfig.MOJANG_MIRRORS.indices) {
                        try {
                            val mirror = GameConfig.MOJANG_MIRRORS[mirrorIndex]
                            progressCallback(InstallProgress(
                                InstallStage.DOWNLOADING_VERSION,
                                "从 ${mirror.name} 获取版本信息...",
                                0.03f,
                                mirrorName = mirror.name
                            ))
                            versionJson = downloadVersionJson(version.id, mirrorIndex)
                            if (versionJson != null) {
                                // 与 FCL 仓库语义一致：json 的 id/jar 必须与目录名一致，
                                // 否则 getVersion(baseId) 找不到 '<baseId>.json' 报不存在
                                // （原版自定义名安装时为实例名；带加载器时为真实 MC 版本号，值不变）。
                                versionJson.addProperty("id", baseId)
                                versionJson.addProperty("jar", baseId)
                                versionJsonFile.writeText(versionJson.toString())
                                Log.d("GameInstaller", "Version JSON downloaded from ${mirror.name}")
                                break
                            } else {
                                failedMirrors.add(mirror.name)
                                Log.w("GameInstaller", "Version JSON from ${mirror.name} returned null")
                            }
                        } catch (e: Exception) {
                            Log.w("GameInstaller", "Version JSON from mirror ${mirrorIndex}: ${e.message}")
                            lastError = e
                            failedMirrors.add(GameConfig.MOJANG_MIRRORS.getOrNull(mirrorIndex)?.name ?: "mirror$mirrorIndex")
                        }
                    }
                    if (versionJson == null) {
                        val errorMsg = "下载版本信息失败: 所有镜像源均失败 (尝试了: ${failedMirrors.joinToString()})"
                        Log.e("GameInstaller", errorMsg)
                        progressCallback(InstallProgress(InstallStage.FAILED, errorMsg))
                        return@withContext false
                    }
                }

                val downloadsBlock = versionJson.get("downloads")?.asJsonObject

                checkCancelled()
                downloadAgg.reset()
                progressCallback(InstallProgress(InstallStage.DOWNLOADING_CLIENT, "下载游戏客户端...", 0.05f))

                val clientDownload = downloadsBlock?.get("client")?.asJsonObject
                if (clientDownload != null) {
                    val clientUrl = clientDownload.get("url")?.asString
                    val clientSize = clientDownload.get("size")?.asLong ?: 0L
                    val clientSha1 = clientDownload.get("sha1")?.asString
                    val clientJar = File(versionDir, "$baseId.jar")

                    if (clientUrl != null) {
                        val needDownload = !clientJar.exists() || clientJar.length() < 1024 ||
                            (clientSize > 0 && clientJar.length() != clientSize) ||
                            (clientSha1 != null && try { calculateSHA1(clientJar).lowercase() != clientSha1.lowercase() } catch (_: Exception) { true })

                        if (needDownload) {
                            // 智能混合下载：自动测速选最快源 + 分块并发 + 低速换源
                            val result = smartClient.download(
                                originalUrl = clientUrl,
                                targetFile = clientJar,
                                expectedSha1 = clientSha1,
                                knownSize = clientSize,
                                isCancelled = { cancelled }
                            ) { progress ->
                                val p = if (progress.total > 0) progress.downloaded.toFloat() / progress.total else 0f
                                val sizeStr = formatSize(progress.downloaded) + "/" + formatSize(progress.total)
                                val msg = "下载中 $sizeStr (${MirrorSourceManager.getSourceLabel(MirrorSourceManager.getCurrentSource(context))})"
                                progressCallback(InstallProgress(
                                    InstallStage.DOWNLOADING_CLIENT, msg, 0.05f + p * 0.15f,
                                    "$baseId.jar", progress.mirrorName,
                                    speed = progress.speed, downloaded = progress.downloaded, totalBytes = progress.total
                                ))
                            }
                            if (result.isFailure) {
                                Log.e("GameInstaller", "Client jar smart download failed: ${result.exceptionOrNull()?.message}")
                                throw result.exceptionOrNull() ?: Exception("客户端 JAR 下载失败")
                            }
                        }
                    }
                }

                val validLibraries = filterValidLibraries(versionJson)
                val totalLibs = validLibraries.size

                checkCancelled()
                progressCallback(InstallProgress(InstallStage.DOWNLOADING_LIBRARIES, "下载库文件 (0/$totalLibs)...", 0.2f))

                // 收集缺失库：保留原始 URL，由 SmartDownloadClient 自动多源选择
                val missingLibs = mutableListOf<Triple<String, String, String?>>()
                validLibraries.forEach { lib ->
                    val artifact = lib.get("artifact")?.asJsonObject ?: return@forEach
                    val libPath = artifact.get("path")?.asString ?: return@forEach
                    val libUrl = artifact.get("url")?.asString ?: return@forEach
                    val libSha1 = artifact.get("sha1")?.asString
                    val libFile = File(librariesDir, libPath)
                    if (!libFile.exists() || libFile.length() < 1024) {
                        missingLibs.add(Triple(libUrl, libPath, libSha1))
                    }
                }

                Log.d("GameInstaller", "Downloading ${missingLibs.size}/$totalLibs missing libraries (32 threads, smart)")

                var completedLibs = 0
                val batchSize = 32
                missingLibs.chunked(batchSize).forEach { batch ->
                    coroutineScope {
                        batch.map { (url, path, sha1) ->
                            async {
                                val libFile = File(librariesDir, path)
                                try {
                                    val result = smartClient.download(
                                        originalUrl = url,
                                        targetFile = libFile,
                                        expectedSha1 = sha1,
                                        isCancelled = { cancelled }
                                    ) { p -> downloadAgg.report(path, p.downloaded) }
                                    if (result.isFailure) {
                                        Log.w("GameInstaller", "Lib failed: $path - ${result.exceptionOrNull()?.message}")
                                    }
                                } catch (e: Exception) {
                                    Log.w("GameInstaller", "Lib failed: $path - ${e.message}")
                                }
                            }
                        }.forEach { it.await() }
                    }

                    completedLibs += batch.size
                    val progress = 0.2f + (0.40f * completedLibs / missingLibs.size.coerceAtLeast(1))
                    progressCallback(InstallProgress(
                        InstallStage.DOWNLOADING_LIBRARIES,
                        "下载库文件 ($completedLibs/${missingLibs.size})...",
                        progress,
                        "智能混合下载",
                        speed = downloadAgg.speed,
                        downloaded = downloadAgg.downloaded
                    ))
                }

                progressCallback(InstallProgress(InstallStage.DOWNLOADING_LIBRARIES, "下载原生库文件...", 0.60f))
                downloadNativeClassifiers(versionJson) { p, msg, speed, downloaded ->
                    progressCallback(InstallProgress(InstallStage.DOWNLOADING_LIBRARIES, msg, 0.60f + p * 0.05f, speed = speed, downloaded = downloaded))
                }
                checkCancelled()

                val assetIndex = versionJson.get("assetIndex")?.asJsonObject
                if (assetIndex != null) {
                    val assetId = assetIndex.get("id")?.asString ?: version.id
                    val assetUrl = assetIndex.get("url")?.asString
                    val assetSha1 = assetIndex.get("sha1")?.asString
                    val assetFile = File(assetsDir, "indexes/$assetId.json")

                    if (assetUrl != null && !assetFile.exists()) {
                        assetFile.parentFile?.mkdirs()
                        val result = smartClient.download(
                            originalUrl = assetUrl,
                            targetFile = assetFile,
                            expectedSha1 = assetSha1,
                            isCancelled = { cancelled }
                        ) { progress ->
                            val p = if (progress.total > 0) progress.downloaded.toFloat() / progress.total else 0f
                            progressCallback(InstallProgress(
                                InstallStage.DOWNLOADING_ASSETS,
                                "下载资源索引... (${MirrorSourceManager.getSourceLabel(MirrorSourceManager.getCurrentSource(context))})",
                                0.65f + p * 0.03f,
                                assetFile.name,
                                progress.mirrorName,
                                speed = progress.speed,
                                downloaded = progress.downloaded,
                                totalBytes = progress.total
                            ))
                        }
                        if (result.isFailure) {
                            Log.e("GameInstaller", "Asset index smart download failed: ${result.exceptionOrNull()?.message}")
                            throw result.exceptionOrNull() ?: Exception("资源索引下载失败")
                        }
                    }

                    if (assetFile.exists()) {
                        progressCallback(InstallProgress(InstallStage.DOWNLOADING_ASSETS, "解析资源列表...", 0.68f))

                        val assetIndexData = gson.fromJson(assetFile.readText(), JsonObject::class.java)
                        val objects = assetIndexData.get("objects")?.asJsonObject

                        if (objects != null) {
                            val entries = objects.entrySet().toList()
                            val totalAssets = entries.size

                            // assets 原始 URL 由 resources.download.minecraft.net 提供，
                            // SmartDownloadClient 会通过 MirrorSourceManager 自动转换为
                            // BMCLAPI/MCBBS 多源列表并选择最快源
                            val missingAssets = mutableListOf<Pair<String, File>>()
                            entries.forEach { entry ->
                                val hash = entry.value.asJsonObject.get("hash")?.asString ?: return@forEach
                                val subDir = hash.substring(0, 2)
                                val assetPath = File(assetsDir, "objects/$subDir/$hash")
                                if (!assetPath.exists() || assetPath.length() == 0L) {
                                    val originalUrl = "https://resources.download.minecraft.net/$subDir/$hash"
                                    missingAssets.add(originalUrl to assetPath)
                                }
                            }

                            Log.d("GameInstaller", "Downloading ${missingAssets.size}/$totalAssets missing assets (sliding window, smart)")

                            // 对齐 PC 端 download.js：滑动窗口并发调度。
                            // 始终保持 64 个活跃下载，一个完成立即补下一个（而非分批等待整批完成），
                            // 避免慢文件拖住整批导致"下一下卡一下"。
                            downloadAssetsSlidingWindow(missingAssets) { completed, total ->
                                val progress = 0.68f + (0.22f * completed / total.coerceAtLeast(1))
                                progressCallback(InstallProgress(
                                    InstallStage.DOWNLOADING_ASSETS,
                                    "下载资源 ($completed/$total)...",
                                    progress,
                                    "智能混合下载",
                                    speed = downloadAgg.speed,
                                    downloaded = downloadAgg.downloaded
                                ))
                            }
                        }
                    }
                }

                progressCallback(InstallProgress(InstallStage.EXTRACTING_NATIVES, "提取原生库...", 0.90f))
                extractNatives(versionJson)

                // 加载器安装（自 FCL fclcore/download 模块迁移：Forge 新/旧版、Fabric、NeoForge、OptiFine）
                if (!loaderType.isNullOrBlank() && !loaderVersion.isNullOrBlank()) {
                    checkCancelled()
                    progressCallback(InstallProgress(
                        InstallStage.DOWNLOADING_LIBRARIES,
                        "安装 $loaderType $loaderVersion...",
                        0.92f
                    ))
                    val loaderResult = com.verse.explorer.core.install.loader.FclLoaderInstaller.installFcl(
                        gameDir = gameDir,
                        versionId = version.id,
                        loaderType = loaderType,
                        loaderVersion = loaderVersion,
                        baseVersionId = baseId,
                        newVersionId = installId,
                        progress = { message, p ->
                            progressCallback(InstallProgress(
                                InstallStage.DOWNLOADING_LIBRARIES,
                                message,
                                0.92f + 0.06f * p.coerceIn(0f, 1f)
                            ))
                        }
                    )
                    loaderResult.getOrThrow()
                    progressCallback(InstallProgress(InstallStage.DOWNLOADING_LIBRARIES, "$loaderType 安装完成", 0.98f))
                }

                progressCallback(InstallProgress(InstallStage.COMPLETED, "安装完成", 1f))
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程取消必须原样抛出，交给上层（installJob.cancel）处理，不能被当成普通下载失败吞掉
                throw e
            } catch (e: CancelledInstallException) {
                Log.i("GameInstaller", "Install cancelled by user")
                progressCallback(InstallProgress(InstallStage.FAILED, "已取消下载"))
                false
            } catch (e: Exception) {
                if (cancelled) {
                    Log.i("GameInstaller", "Install cancelled (download aborted)")
                    progressCallback(InstallProgress(InstallStage.FAILED, "已取消下载"))
                } else {
                    Log.e("GameInstaller", "Install failed: ${e.message}", e)
                    progressCallback(InstallProgress(InstallStage.FAILED, "安装失败: ${e.message}"))
                }
                false
            }
        }
    }

    private suspend fun downloadVersionJson(versionId: String, mirrorIndex: Int): JsonObject? {
        val mirror = GameConfig.MOJANG_MIRRORS.getOrNull(mirrorIndex) ?: return null
        Log.d("GameInstaller", "Downloading version JSON from mirror: ${mirror.name}")

        val isMojang = mirror.name.contains("Mojang") || mirror.name.contains("官方")

        if (isMojang) {
            return downloadVersionJsonFromManifest(versionId, mirror.name)
        }

        val url = GameConfig.getVersionJsonUrl(versionId, mirrorIndex)
        Log.d("GameInstaller", "Version JSON URL: $url")

        val request = okhttp3.Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "VerseExplorerX/1.0")
            .build()

        downloadManager.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("GameInstaller", "HTTP ${response.code} for: $url")
                return null
            }
            val body = response.body?.string() ?: return null
            if (!body.trimStart().startsWith("{")) {
                Log.w("GameInstaller", "Non-JSON response from: $url")
                return null
            }
            return gson.fromJson(body, JsonObject::class.java)
        }
    }

    private suspend fun downloadVersionJsonFromManifest(versionId: String, mirrorName: String): JsonObject? {
        Log.d("GameInstaller", "Downloading version manifest for Mojang mirror")
        val manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

        val request = okhttp3.Request.Builder().url(manifestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "VerseExplorerX/1.0")
            .build()

        val manifestBody = downloadManager.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("GameInstaller", "Manifest HTTP ${response.code}")
                return null
            }
            response.body?.string() ?: return null
        }

        if (!manifestBody.trimStart().startsWith("{")) {
            Log.w("GameInstaller", "Manifest: non-JSON response")
            return null
        }

        val manifest = gson.fromJson(manifestBody, JsonObject::class.java)
        val versions = manifest.get("versions")?.asJsonArray
        if (versions == null) {
            Log.w("GameInstaller", "Manifest: no versions array")
            return null
        }

        for (element in versions) {
            val entry = element.asJsonObject
            val id = entry.get("id")?.asString
            if (id == versionId) {
                val versionUrl = entry.get("url")?.asString
                if (versionUrl != null) {
                    Log.d("GameInstaller", "Found version URL in manifest: $versionUrl")
                    val versionRequest = okhttp3.Request.Builder().url(versionUrl)
                        .header("Accept", "application/json")
                        .header("User-Agent", "VerseExplorerX/1.0")
                        .build()

                    return downloadManager.httpClient.newCall(versionRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w("GameInstaller", "Version JSON HTTP ${response.code}")
                            return null
                        }
                        val body = response.body?.string() ?: return null
                        if (!body.trimStart().startsWith("{")) return null
                        gson.fromJson(body, JsonObject::class.java)
                    }
                }
                break
            }
        }

        Log.w("GameInstaller", "Version $versionId not found in manifest")
        return null
    }

    private fun filterValidLibraries(versionJson: JsonObject): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val libsArray = versionJson.get("libraries")?.asJsonArray ?: return result

        for (element in libsArray) {
            val lib = element.asJsonObject
            val name = lib.get("name")?.asString ?: ""

            val rules = lib.get("rules")?.asJsonArray
            if (rules != null) {
                val allowed = evaluateRules(rules.map { it.asJsonObject })
                if (!allowed) continue
            }

            val downloads = lib.get("downloads")?.asJsonObject
            val artifact = downloads?.get("artifact")?.asJsonObject

            if (artifact != null && artifact.has("url") && artifact.has("path")) {
                result.add(downloads)
            } else if (lib.has("name")) {
                val nameParts = name.split(":")
                if (nameParts.size >= 3) {
                    val group = nameParts[0].replace(".", "/")
                    val artifactName = nameParts[1]
                    val version = nameParts[2]
                    val basePath = "$group/$artifactName/$version/$artifactName-$version"
                    val libUrl = lib.get("url")?.asString
                    val jarPath = "$basePath.jar"
                    if (libUrl != null) {
                        val fakeDownloads = JsonObject()
                        val fakeArtifact = JsonObject()
                        fakeArtifact.addProperty("path", jarPath)
                        fakeArtifact.addProperty("url", "$libUrl/$jarPath")
                        fakeDownloads.add("artifact", fakeArtifact)
                        result.add(fakeDownloads)
                    }
                }
            }
        }

        return result
    }

/**
     * 参考 PC 端 server.js evaluateRules — 正确迭代所有规则
     * 对齐 FCL/PC端: 只匹配 OS 名称(linux)，其他 OS 视为不匹配
     */
    private fun evaluateRules(rules: List<JsonObject>): Boolean {
        if (rules.isEmpty()) return true
        
        for (rule in rules) {
            val action = rule.get("action")?.asString ?: continue
            val os = rule.get("os")?.asJsonObject
            
            var osMatch = true
            if (os != null) {
                val osName = os.get("name")?.asString ?: ""
                osMatch = osName.isEmpty() || osName == "linux"
            }
            
            if (action == "allow") {
                if (osMatch) return true
            } else {
                if (osMatch) return false
            }
        }
        
        return false
    }

    private suspend fun downloadFileWithRetry(
        url: String,
        targetFile: File,
        expectedSha1: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> },
        maxRetries: Int = 3
    ) {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                downloadFileWithProgress(url, targetFile, expectedSha1, onProgress)
                return
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) Thread.sleep(1000L * attempt)
            }
        }
        throw lastException ?: Exception("Download failed after $maxRetries attempts")
    }

    private suspend fun downloadFileWithProgress(
        url: String,
        targetFile: File,
        expectedSha1: String?,
        onProgress: (Float, String) -> Unit
    ) {
        targetFile.parentFile?.mkdirs()

        // FCL-style: 下载到临时文件，成功后原子移动到目标位置
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        val request = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "VerseExplorerX/1.0")
            .header("Accept", "*/*")
            .build()

        var totalSize = -1L
        var downloaded = 0L

        try {
            downloadManager.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")

                val body = response.body ?: throw Exception("Empty body: $url")
                totalSize = body.contentLength()

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalSize > 0) {
                                val progress = downloaded.toFloat() / totalSize.toFloat()
                                val sizeStr = formatSize(downloaded) + "/" + formatSize(totalSize)
                                onProgress(progress, "下载中 $sizeStr")
                            }
                        }
                    }
                }
            }

            // FCL-style: Content-Length 校验
            if (totalSize >= 0 && downloaded != totalSize) {
                tempFile.delete()
                throw Exception("文件大小不匹配: 已下载 $downloaded, 期望 $totalSize")
            }

            // FCL-style: SHA1 校验
            if (expectedSha1 != null) {
                val actualSha1 = calculateSHA1(tempFile)
                if (actualSha1.lowercase() != expectedSha1.lowercase()) {
                    tempFile.delete()
                    throw Exception("SHA1 校验失败 ${targetFile.name}: 期望=$expectedSha1, 实际=$actualSha1")
                }
            }

            // FCL-style: 原子移动临时文件到目标位置
            targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                // renameTo 可能因跨挂载点失败，用拷贝回退
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            // 下载失败时删除临时文件，避免残留
            tempFile.delete()
            throw e
        }
    }

    private suspend fun downloadFile(url: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        val request = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "VerseExplorerX/1.0")
            .header("Accept", "*/*")
            .build()

        try {
            downloadManager.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")
                val body = response.body ?: throw Exception("Empty body: $url")
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    // 对齐 PC 端：assets 小文件使用专用快速客户端（10s 连接超时 + 30s 读取超时）
    private suspend fun downloadAssetFile(
        url: String,
        targetFile: File,
        onBytes: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val request = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "VerseExplorerX/1.0")
            .header("Accept", "*/*")
            .build()
        try {
            fastHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")
                val body = response.body ?: throw Exception("Empty body: $url")
                val totalBytes = body.contentLength().coerceAtLeast(-1L)
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded = 0L
                        var lastReport = System.currentTimeMillis()
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancelled()) throw CancelledInstallException()
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 300) {
                                onBytes(downloaded, totalBytes)
                                lastReport = now
                            }
                        }
                        onBytes(downloaded, totalBytes)
                    }
                }
            }
            targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * 对齐 PC 端 download.js 的滑动窗口并发调度：
     * 始终保持 [concurrentAssets] 个活跃下载，一个完成立即补下一个，
     * 而非分批等待整批完成（避免慢文件拖住整批导致"下一下卡一下"）。
     *
     * 每个文件按镜像列表顺序（BMCLAPI/MCBBS 优先）用共享连接池直连下载，
     * 全部镜像失败后回退 SmartDownloadClient 多源重试，保证健壮性的同时最大化吞吐。
     */
    private suspend fun downloadAssetsSlidingWindow(
        assets: List<Pair<String, File>>,
        onProgress: (completed: Int, total: Int) -> Unit
    ) {
        if (assets.isEmpty()) return
        val total = assets.size
        val completed = AtomicInteger(0)
        val semaphore = Semaphore(concurrentAssets)

        coroutineScope {
            val worker = this
            assets.forEach { (url, assetPath) ->
                worker.launch {
                    semaphore.withPermit {
                        checkCancelled()
                        // 按镜像顺序直连，第一个成功即止（镜像源连接池复用，加速国内下载）
                        val urls = MirrorSourceManager.getMirrorUrls(context, url)
                        var success = false
                        for (mirrorUrl in urls) {
                            if (cancelled) throw CancelledInstallException()
                            try {
                                downloadAssetFile(
                                    mirrorUrl,
                                    assetPath,
                                    onBytes = { d, _ -> downloadAgg.report(assetPath.name, d) },
                                    isCancelled = { cancelled }
                                )
                                success = true
                                break
                            } catch (e: CancelledInstallException) {
                                throw e
                            } catch (_: Exception) {
                                // 尝试下一个镜像
                            }
                        }
                        if (!success) {
                            // 全部镜像直连失败：回退多源镜像重试
                            try {
                                val result = smartClient.download(
                                    originalUrl = url,
                                    targetFile = assetPath,
                                    isCancelled = { cancelled }
                                ) { p -> downloadAgg.report(assetPath.name, p.downloaded) }
                                if (result.isFailure) {
                                    Log.w("GameInstaller", "Asset failed: ${assetPath.name} - ${result.exceptionOrNull()?.message}")
                                }
                            } catch (e2: Exception) {
                                Log.w("GameInstaller", "Asset failed: ${assetPath.name} - ${e2.message}")
                            }
                        }
                        val done = completed.incrementAndGet()
                        if (done % 8 == 0 || done == total) {
                            onProgress(done, total)
                        }
                    }
                }
            }
        }
    }

    suspend fun extractNatives(versionJson: JsonObject) {
        val libsArray = versionJson.get("libraries")?.asJsonArray ?: return
        nativesDir.mkdirs()

        val osNativesKey = "linux"
        var extractedCount = 0

        for (element in libsArray) {
            val lib = element.asJsonObject
            val rules = lib.get("rules")?.asJsonArray
            if (rules != null && !evaluateRules(rules.map { it.asJsonObject })) continue

            val downloads = lib.get("downloads")?.asJsonObject ?: continue

            // 26.x 新格式：natives 为独立 library 条目（name 形如 org.lwjgl:lwjgl:3.4.1:natives-linux）
            val libName = lib.get("name")?.asString ?: ""
            if (libName.contains("natives-linux")) {
                val artifact = downloads.get("artifact")?.asJsonObject ?: continue
                val nativePath = artifact.get("path")?.asString ?: continue
                val nativeFile = File(librariesDir, nativePath)
                if (nativeFile.exists() && nativeFile.length() > 1024) {
                    try {
                        extractNativeJarToDir(nativeFile, nativesDir)
                        extractedCount++
                    } catch (e: Exception) {
                        Log.w("GameInstaller", "Failed to extract ${nativeFile.name}: ${e.message}")
                    }
                }
                continue
            }

            val classifiers = downloads.get("classifiers")?.asJsonObject
            val nativesObj = lib.get("natives")?.asJsonObject

            val nativeKey = if (nativesObj != null && nativesObj.has(osNativesKey)) {
                nativesObj.get(osNativesKey)?.asString
            } else if (classifiers != null) {
                when {
                    isArm64 -> classifiers.keySet().firstOrNull { it.contains("arm64") || (it.contains("linux") && !it.contains("arm32") && !it.contains("x86")) }
                    isArm32 -> classifiers.keySet().firstOrNull { it.contains("arm32") }
                    else -> classifiers.keySet().firstOrNull { it.contains("linux") && !it.contains("arm") }
                }
            } else null

            if (nativeKey == null || classifiers == null) continue

            val nativeLib = classifiers.get(nativeKey)?.asJsonObject ?: continue
            val nativePath = nativeLib.get("path")?.asString ?: continue
            val nativeFile = File(librariesDir, nativePath)

            if (nativeFile.exists() && nativeFile.length() > 1024) {
                try {
                    extractNativeJarToDir(nativeFile, nativesDir)
                    extractedCount++
                } catch (e: Exception) {
                    Log.w("GameInstaller", "Failed to extract ${nativeFile.name}: ${e.message}")
                }
            }
        }

        Log.d("GameInstaller", "Extracted $extractedCount native JARs to $nativesDir")
    }

    private suspend fun downloadNativeClassifiers(
        versionJson: JsonObject,
        onProgress: (Float, String, speed: Long, downloaded: Long) -> Unit
    ) {
        val libsArray = versionJson.get("libraries")?.asJsonArray ?: return

        val missingNatives = mutableListOf<Triple<String, String, String?>>()

        for (element in libsArray) {
            val lib = element.asJsonObject
            val rules = lib.get("rules")?.asJsonArray
            if (rules != null && !evaluateRules(rules.map { it.asJsonObject })) continue

            val downloads = lib.get("downloads")?.asJsonObject ?: continue

            // 26.x 新格式：独立 natives-linux 条目（name 带 natives-linux 后缀，走 artifact 下载）
            val libName = lib.get("name")?.asString ?: ""
            if (libName.contains("natives-linux")) {
                val artifact = downloads.get("artifact")?.asJsonObject ?: continue
                val nativePath = artifact.get("path")?.asString ?: continue
                val nativeUrl = artifact.get("url")?.asString ?: continue
                val nativeSha1 = artifact.get("sha1")?.asString
                val nativeFile = File(librariesDir, nativePath)

                val needDownload = if (!nativeFile.exists()) true
                else if (nativeSha1 != null) {
                    try { calculateSHA1(nativeFile).lowercase() != nativeSha1.lowercase() } catch (_: Exception) { true }
                } else false

                if (needDownload) {
                    missingNatives.add(Triple(nativeUrl, nativePath, nativeSha1))
                    if (nativeFile.exists()) nativeFile.delete()
                }
                continue
            }

            val classifiers = downloads.get("classifiers")?.asJsonObject ?: continue
            val nativesObj = lib.get("natives")?.asJsonObject

            val nativeKey: String? = if (nativesObj != null && nativesObj.has("linux")) {
                nativesObj.get("linux")?.asString
            } else {
                when {
                    isArm64 -> classifiers.keySet().firstOrNull { it.contains("arm64") || (it.contains("linux") && !it.contains("arm32") && !it.contains("x86")) }
                    isArm32 -> classifiers.keySet().firstOrNull { it.contains("arm32") }
                    else -> classifiers.keySet().firstOrNull { it.contains("linux") && !it.contains("arm") }
                }
            }

            if (nativeKey == null) continue

            val nativeLib = classifiers.get(nativeKey)?.asJsonObject ?: continue
            val nativePath = nativeLib.get("path")?.asString ?: continue
            val nativeUrl = nativeLib.get("url")?.asString ?: continue
            val nativeSha1 = nativeLib.get("sha1")?.asString

            val nativeFile = File(librariesDir, nativePath)
            val needDownload = if (!nativeFile.exists()) true
            else if (nativeSha1 != null) {
                try { calculateSHA1(nativeFile).lowercase() != nativeSha1.lowercase() } catch (_: Exception) { true }
            } else false

            if (needDownload) {
                missingNatives.add(Triple(nativeUrl, nativePath, nativeSha1))
                if (nativeFile.exists()) nativeFile.delete()
            }
        }

        if (missingNatives.isEmpty()) return

        Log.d("GameInstaller", "Downloading ${missingNatives.size} native classifier JARs (32 threads, smart)")

        var successCount = AtomicInteger(0)
        val batchSize = 32
        var done = 0
        missingNatives.chunked(batchSize).forEach { batch ->
            coroutineScope {
                batch.map { (originalUrl, path, sha1) ->
                    async {
                        val nativeFile = File(librariesDir, path)
                        try {
                            val result = smartClient.download(
                                originalUrl = originalUrl,
                                targetFile = nativeFile,
                                expectedSha1 = sha1,
                                isCancelled = { cancelled }
                            ) { p -> downloadAgg.report(path, p.downloaded) }
                            if (result.isSuccess) successCount.incrementAndGet()
                        } catch (_: Exception) {}
                    }
                }.forEach { it.await() }
            }
            done += batch.size
            onProgress(done.toFloat() / missingNatives.size, "下载原生库 ($done/${missingNatives.size})...", downloadAgg.speed, downloadAgg.downloaded)
        }

        Log.d("GameInstaller", "Native classifiers: ${successCount.get()}/${missingNatives.size} downloaded")
    }

    private fun extractNativeJarToDir(jarFile: File, targetDir: File) {
        ZipInputStream(jarFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && !entry.name.contains("META-INF")) {
                    val fileName = entry.name.substringAfterLast("/").ifEmpty { entry.name }
                    if (fileName.endsWith(".so") || fileName.endsWith(".dll") || fileName.endsWith(".dylib")) {
                        val file = File(targetDir, fileName)
                        file.outputStream().use { output -> zipIn.copyTo(output) }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    suspend fun downloadMissingLibraries(
        versionJson: JsonObject,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val libsArray = versionJson.get("libraries")?.asJsonArray ?: return
        var downloadedCount = 0

        val missingLibs = mutableListOf<Pair<String, JsonObject>>()

        for (element in libsArray) {
            val lib = element.asJsonObject
            val rules = lib.get("rules")?.asJsonArray
            if (rules != null && !evaluateRules(rules.map { it.asJsonObject })) continue

            val downloads = lib.get("downloads")?.asJsonObject ?: continue
            val artifact = downloads.get("artifact")?.asJsonObject ?: continue
            val path = artifact.get("path")?.asString ?: continue
            val url = artifact.get("url")?.asString ?: continue
            val sha1 = artifact.get("sha1")?.asString

            val targetFile = File(librariesDir, path)
            if (targetFile.exists() && targetFile.length() > 1024) {
                if (sha1 != null) {
                    try {
                        if (calculateSHA1(targetFile).lowercase() == sha1.lowercase()) continue
                    } catch (_: Exception) {}
                } else continue
            }

            missingLibs.add(url to artifact)
        }

        if (missingLibs.isEmpty()) return

        for ((index, pair) in missingLibs.withIndex()) {
            val (url, artifact) = pair
            val path = artifact.get("path")?.asString ?: continue
            val sha1 = artifact.get("sha1")?.asString
            val targetFile = File(librariesDir, path)

            try {
                val result = smartClient.download(
                    originalUrl = url,
                    targetFile = targetFile,
                    expectedSha1 = sha1,
                    isCancelled = { cancelled }
                )
                if (result.isSuccess) downloadedCount++
            } catch (e: Exception) {
                Log.w("GameInstaller", "Failed library: $path")
            }

            onProgress?.invoke((index + 1).toFloat() / missingLibs.size)
        }
    }

    private fun calculateSHA1(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}