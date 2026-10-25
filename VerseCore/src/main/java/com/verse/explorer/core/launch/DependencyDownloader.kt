package com.verse.explorer.core.launch

import android.content.Context
import android.os.Build
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.config.isValidJsonResponse
import com.verse.explorer.core.java.JavaRuntimeManager
import com.verse.explorer.core.util.HashUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class RepairProgress(
    val stage: RepairStage,
    val message: String,
    val progress: Float,
    val currentFile: String = "",
    val totalFiles: Int = 0,
    val completedFiles: Int = 0,
    val mirrorName: String = ""
)

enum class RepairStage {
    CHECKING,
    DOWNLOADING_JAVA,
    DOWNLOADING_VERSION_JSON,
    DOWNLOADING_CLIENT,
    DOWNLOADING_LIBRARIES,
    DOWNLOADING_ASSETS,
    EXTRACTING_NATIVES,
    VERIFYING,
    COMPLETED,
    FAILED
}

class DependencyDownloader(private val context: Context, private val paths: com.verse.explorer.core.install.InstallationPaths) {

    private val gameDir = paths.root
    private val versionsDir = paths.versions
    private val librariesDir = paths.libraries
    private val assetsDir = paths.assets
    private val nativesDir = paths.natives
    private val gson = Gson()
    private val transfer = RepairDownloadExecutor()

    private val isArm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")
    private val isArm32 = Build.SUPPORTED_ABIS.contains("armeabi-v7a")

    suspend fun repairDependencies(
        versionId: String,
        checkResult: DependencyCheckResult,
        onProgress: (RepairProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val missingDeps = checkResult.dependencies.filter { it.status != CheckStatus.OK && it.autoFixable }

            if (missingDeps.isEmpty()) {
                onProgress(RepairProgress(RepairStage.COMPLETED, "所有依赖已就绪", 1f))
                return@withContext true
            }

            onProgress(RepairProgress(RepairStage.CHECKING, "准备修复 ${missingDeps.size} 个依赖...", 0f))

            var overallProgress = 0f
            val stepProgress = 1f / missingDeps.size

            for (dep in missingDeps) {
                when (dep.category) {
                    DependencyCategory.JAVA_RUNTIME -> {
                        onProgress(RepairProgress(
                            RepairStage.DOWNLOADING_JAVA,
                            "正在下载Java运行时...",
                            overallProgress
                        ))
                        val success = downloadJavaRuntime(onProgress) { p ->
                            overallProgress + p * stepProgress * 0.5f
                        }
                        if (!success) {
                            onProgress(RepairProgress(RepairStage.FAILED, "Java运行时下载失败", overallProgress))
                            return@withContext false
                        }
                        overallProgress += stepProgress * 0.5f

                        onProgress(RepairProgress(
                            RepairStage.DOWNLOADING_JAVA,
                            "正在解压Java运行时...",
                            overallProgress
                        ))
                        onProgress(RepairProgress(RepairStage.DOWNLOADING_JAVA, "Java运行时安装完成", overallProgress + stepProgress * 0.5f))
                        overallProgress += stepProgress * 0.5f
                    }

                    DependencyCategory.VERSION_JSON -> {
                        onProgress(RepairProgress(
                            RepairStage.DOWNLOADING_VERSION_JSON,
                            "正在下载版本信息...",
                            overallProgress
                        ))
                        var success = false
                        for (mirrorIndex in GameConfig.MOJANG_MIRRORS.indices) {
                            try {
                                val mirror = GameConfig.MOJANG_MIRRORS[mirrorIndex]
                                onProgress(RepairProgress(
                                    RepairStage.DOWNLOADING_VERSION_JSON,
                                    "尝试从 ${mirror.name} 获取版本信息...",
                                    overallProgress,
                                    mirrorName = mirror.name
                                ))
                                success = downloadVersionJson(versionId, mirrorIndex)
                                if (success) break
                            } catch (_: Exception) {}
                        }
                        if (!success) {
                            onProgress(RepairProgress(RepairStage.FAILED, "版本信息下载失败", overallProgress))
                            return@withContext false
                        }
                        overallProgress += stepProgress
                    }

                    DependencyCategory.CLIENT_JAR -> {
                        onProgress(RepairProgress(
                            RepairStage.DOWNLOADING_CLIENT,
                            "正在下载游戏客户端...",
                            overallProgress
                        ))
                        val success = downloadClientJarWithRetry(versionId, onProgress) { p ->
                            overallProgress + p * stepProgress
                        }
                        if (!success) {
                            onProgress(RepairProgress(RepairStage.FAILED, "游戏客户端下载失败", overallProgress))
                            return@withContext false
                        }
                        overallProgress += stepProgress
                    }

                    DependencyCategory.LIBRARIES -> {
                        onProgress(RepairProgress(
                            RepairStage.DOWNLOADING_LIBRARIES,
                            "正在下载依赖库文件...",
                            overallProgress
                        ))
                        val success = downloadMissingLibraries(versionId, onProgress) { p ->
                            overallProgress + p * stepProgress * 0.8f
                        }
                        if (!success) {
                            onProgress(RepairProgress(RepairStage.FAILED, "依赖库下载失败", overallProgress))
                            return@withContext false
                        }

                        onProgress(RepairProgress(
                            RepairStage.DOWNLOADING_LIBRARIES,
                            "正在下载原生库文件...",
                            overallProgress + stepProgress * 0.8f
                        ))
                        val nativeSuccess = downloadNativeClassifiers(versionId, onProgress) { p ->
                            overallProgress + stepProgress * 0.8f + p * stepProgress * 0.2f
                        }
                        if (!nativeSuccess) {
                            println("[DependencyDownloader] Some native classifiers failed to download, will try anyway")
                        }

                        overallProgress += stepProgress
                    }

                    DependencyCategory.ASSETS -> {
                        onProgress(RepairProgress(
                            RepairStage.DOWNLOADING_ASSETS,
                            "正在下载资源文件...",
                            overallProgress
                        ))
                        val success = downloadMissingAssets(versionId, onProgress) { p ->
                            overallProgress + p * stepProgress
                        }
                        if (!success) {
                            onProgress(RepairProgress(RepairStage.FAILED, "资源文件下载失败", overallProgress))
                            return@withContext false
                        }
                        overallProgress += stepProgress
                    }

                    DependencyCategory.NATIVES -> {
                        onProgress(RepairProgress(
                            RepairStage.EXTRACTING_NATIVES,
                            "正在提取内置原生库...",
                            overallProgress
                        ))
                        
                        println("[DependencyDownloader] === USING BUILTIN NATIVES ===")
                        val builtinSuccess = BuiltinNatives.ensureNativesExist(context, gameDir)
                        
                        if (!builtinSuccess) {
                            println("[DependencyDownloader] Builtin natives incomplete, trying to download from network...")
                            downloadNativeClassifiers(versionId, onProgress) { p ->
                                overallProgress + p * stepProgress * 0.5f
                            }
                            extractNatives(versionId)
                        }
                        
                        overallProgress += stepProgress
                    }

                    DependencyCategory.RENDERER -> {
                        onProgress(RepairProgress(
                            RepairStage.EXTRACTING_NATIVES,
                            "渲染器库已内置在应用中，无需额外下载",
                            overallProgress
                        ))
                        overallProgress += stepProgress
                    }
                }
            }

            onProgress(RepairProgress(RepairStage.VERIFYING, "正在验证文件完整性...", 0.9f))
            
            println("[DependencyDownloader] === VERIFICATION ===")
            println("[DependencyDownloader] nativesDir: ${nativesDir.absolutePath}")
            println("[DependencyDownloader] nativesDir exists: ${nativesDir.exists()}")
            if (nativesDir.exists()) {
                val allFiles = nativesDir.listFiles()?.toList() ?: emptyList()
                val soFiles = allFiles.filter { it.name.endsWith(".so") && it.isFile }
                println("[DependencyDownloader] Total files in nativesDir: ${allFiles.size}")
                println("[DependencyDownloader] .so files: ${soFiles.size}")
                soFiles.sortedBy { it.name }.forEach { f ->
                    println("[DependencyDownloader]   - ${f.name} (${f.length()} bytes)")
                }
            }
            
            val verifyResult = DependencyChecker(context, gameDir).checkAll(versionId)
            
            println("[DependencyDownloader] Verify result: allOk=${verifyResult.allOk}")
            verifyResult.dependencies.forEach { dep ->
                println("[DependencyDownloader]   ${dep.category}: ${dep.status} - ${dep.message}")
            }
            
            if (!verifyResult.allOk) {
                val stillMissing = verifyResult.dependencies.filter { it.status != CheckStatus.OK }
                onProgress(RepairProgress(
                    RepairStage.COMPLETED,
                    "部分依赖修复失败: ${stillMissing.joinToString { it.name }}",
                    1f
                ))
                return@withContext false
            }

            onProgress(RepairProgress(RepairStage.COMPLETED, "所有依赖已就绪！", 1f))
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onProgress(RepairProgress(RepairStage.FAILED, "修复失败: ${e.message}", 0f))
            false
        }
    }

    private suspend fun downloadJavaRuntime(
        onProgress: (RepairProgress) -> Unit,
        progressMapper: (Float) -> Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            var success = false
            JavaRuntimeManager.installJre(context, "java17") { progress ->
                val mappedProgress = progressMapper(progress.progress / 100f)
                onProgress(RepairProgress(
                    RepairStage.DOWNLOADING_JAVA,
                    progress.message,
                    mappedProgress,
                    progress.currentFile
                ))
                if (progress.status == com.verse.explorer.core.java.JavaInstallStatus.COMPLETED) {
                    success = true
                }
                if (progress.status == com.verse.explorer.core.java.JavaInstallStatus.FAILED) {
                    success = false
                }
            }
            success
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun downloadVersionJson(versionId: String, mirrorIndex: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val versionDir = File(versionsDir, versionId)
            if (!versionDir.exists()) versionDir.mkdirs()

            val url = GameConfig.getVersionJsonUrl(versionId, mirrorIndex)
            println("[DependencyDownloader] Downloading version JSON from: $url")

            val versionConn = URL(url).openConnection() as HttpURLConnection
            versionConn.connectTimeout = 15000
            versionConn.readTimeout = 20000
            versionConn.instanceFollowRedirects = true
            versionConn.setRequestProperty("Accept", "application/json")
            versionConn.setRequestProperty("User-Agent", "VerseExplorerX/1.0")

            try {
                if (versionConn.responseCode != HttpURLConnection.HTTP_OK) {
                    println("[DependencyDownloader] HTTP error ${versionConn.responseCode} from: $url")
                    return@withContext false
                }

                val versionBody = versionConn.inputStream.bufferedReader().readText()

                if (!isValidJsonResponse(versionBody)) {
                    println("[DependencyDownloader] Invalid JSON from $url, body starts with: ${versionBody.take(50)}")
                    return@withContext false
                }

                val versionJson = File(versionDir, "$versionId.json")
                versionJson.writeText(versionBody)

                val json = gson.fromJson(versionBody, JsonObject::class.java)
                return@withContext json.has("id") && json.has("libraries") && json.has("mainClass")
            } finally {
                versionConn.disconnect()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[DependencyDownloader] Error downloading version JSON: ${e.message}")
            false
        }
    }

    private suspend fun downloadClientJarWithRetry(
        versionId: String,
        onProgress: (RepairProgress) -> Unit,
        progressMapper: (Float) -> Float
    ): Boolean = withContext(Dispatchers.IO) {
        for (attempt in 1..3) {
            try {
                val result = downloadClientJar(versionId, onProgress, progressMapper)
                if (result) return@withContext true
            } catch (_: Exception) {}
            if (attempt < 3) Thread.sleep(1000L * attempt)
        }
        false
    }

    private suspend fun downloadClientJar(
        versionId: String,
        onProgress: (RepairProgress) -> Unit,
        progressMapper: (Float) -> Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val versionDir = File(versionsDir, versionId)
            val versionJson = File(versionDir, "$versionId.json")

            if (!versionJson.exists()) return@withContext false

            val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
            val clientDownload = json.getAsJsonObject("downloads")
                ?.getAsJsonObject("client") ?: return@withContext false

            val originalUrl = clientDownload.get("url")?.asString ?: return@withContext false
            val expectedSha1 = clientDownload.get("sha1")?.asString
            val expectedSize = clientDownload.get("size")?.asLong ?: 0L

            var url = GameConfig.getClientJarUrl(versionId, expectedSha1, 0)
                ?: GameConfig.replaceWithMirror(originalUrl)

            val clientJar = File(versionDir, "$versionId.jar")
            if (clientJar.exists() && expectedSha1 != null) {
                val actualSha1 = HashUtils.sha1File(clientJar)
                if (actualSha1 == expectedSha1.lowercase()) return@withContext true
                clientJar.delete()
            }

            transfer.downloadFileWithProgress(url, clientJar, expectedSha1, expectedSize) { progress, message ->
                val mappedProgress = progressMapper(progress)
                onProgress(RepairProgress(
                    RepairStage.DOWNLOADING_CLIENT,
                    message,
                    mappedProgress,
                    "$versionId.jar"
                ))
            }

            if (expectedSha1 != null) {
                val actualSha1 = HashUtils.sha1File(clientJar) ?: return@withContext false
                if (actualSha1 != expectedSha1.lowercase()) {
                    clientJar.delete()
                    return@withContext false
                }
            }

            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun downloadMissingLibraries(
        versionId: String,
        onProgress: (RepairProgress) -> Unit,
        progressMapper: (Float) -> Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val versionDir = File(versionsDir, versionId)
            val versionJson = File(versionDir, "$versionId.json")

            if (!versionJson.exists()) return@withContext false

            val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
            val libraries = json.getAsJsonArray("libraries") ?: return@withContext true

            val missingLibs = mutableListOf<Triple<String, String, String>>()

            for (libElement in libraries) {
                val lib = libElement.asJsonObject
                val rules = lib.getAsJsonArray("rules")
                if (rules != null && !DependencyUtils.evaluateRules(rules)) continue

                val downloads = lib.getAsJsonObject("downloads") ?: continue
                val artifact = downloads.getAsJsonObject("artifact") ?: continue
                val path = artifact.get("path")?.asString ?: continue
                val url = artifact.get("url")?.asString ?: continue
                val sha1 = artifact.get("sha1")?.asString
                val size = artifact.get("size")?.asLong ?: -1L

                val libFile = File(librariesDir, path)
                // 与安装流程共用同一套判定：长度不符或 zip 结构损坏都会重下，
                // 避免把中断下载留下的半截 jar 当成已就绪
                val needDownload = com.verse.explorer.core.install.InstallFileUtils
                    .libraryNeedsDownload(libFile, sha1, size)

                if (needDownload) {
                    missingLibs.add(Triple(url, path, sha1 ?: ""))
                    if (libFile.exists() && sha1 != null) {
                        libFile.delete()
                    }
                }
            }

            if (missingLibs.isEmpty()) return@withContext true

            var failedCount = 0
            for ((index, triple) in missingLibs.withIndex()) {
                val (originalUrl, path, sha1) = triple
                val libFile = File(librariesDir, path)

                var downloadUrl = GameConfig.replaceWithMirror(originalUrl)

                try {
                    transfer.downloadFileWithRetry(downloadUrl, libFile, sha1.ifEmpty { null }, 3) { progress, _ ->
                        val libProgress = (index.toFloat() + progress) / missingLibs.size
                        val mappedProgress = progressMapper(libProgress)
                        onProgress(RepairProgress(
                            RepairStage.DOWNLOADING_LIBRARIES,
                            "下载库文件 (${index + 1}/${missingLibs.size}): ${libFile.name}",
                            mappedProgress,
                            libFile.name,
                            missingLibs.size,
                            index + 1
                        ))
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                    println("[DependencyDownloader] Mirror failed for $path, trying original URL")
                    try {
                        transfer.downloadFileWithRetry(originalUrl, libFile, sha1.ifEmpty { null }, 2) { progress, _ ->
                            val libProgress = (index.toFloat() + progress) / missingLibs.size
                            val mappedProgress = progressMapper(libProgress)
                            onProgress(RepairProgress(
                                RepairStage.DOWNLOADING_LIBRARIES,
                                "下载库文件 (${index + 1}/${missingLibs.size}): ${libFile.name}",
                                mappedProgress,
                                libFile.name,
                                missingLibs.size,
                                index + 1
                            ))
                        }
                    } catch (e2: Exception) {
                        println("[DependencyDownloader] Failed to download library $path: ${e2.message}")
                        failedCount++
                    }
                }
            }

            failedCount == 0
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun downloadNativeClassifiers(
        versionId: String,
        onProgress: (RepairProgress) -> Unit,
        progressMapper: (Float) -> Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val versionDir = File(versionsDir, versionId)
            val versionJson = File(versionDir, "$versionId.json")

            if (!versionJson.exists()) {
                println("[DependencyDownloader] downloadNativeClassifiers: version.json not found")
                return@withContext false
            }

            val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
            val libraries = json.getAsJsonArray("libraries") ?: return@withContext true

            val nativeKey = when {
                isArm64 -> "natives-linux-arm64"
                isArm32 -> "natives-linux-arm32"
                else -> "natives-linux"
            }
            val fallbackKeys = when {
                isArm64 -> listOf("natives-linux-arm64", "natives-linux", "linux-arm64")
                isArm32 -> listOf("natives-linux-arm32", "natives-linux", "linux-arm32")
                else -> listOf("natives-linux", "linux")
            }
            
            println("[DependencyDownloader] downloadNativeClassifiers: isArm64=$isArm64, isArm32=$isArm32, nativeKey=$nativeKey")
            println("[DependencyDownloader] downloadNativeClassifiers: fallbackKeys=$fallbackKeys")

            val missingNatives = mutableListOf<Triple<String, String, String>>()
            var totalNativeLibs = 0
            var foundNativeLibs = 0
            var notFoundLibs = 0

            for (libElement in libraries) {
                val lib = libElement.asJsonObject
                val libName = lib.get("name")?.asString ?: "unknown"
                val rules = lib.getAsJsonArray("rules")
                if (rules != null && !DependencyUtils.evaluateRules(rules)) continue

                val downloads = lib.getAsJsonObject("downloads") ?: continue
                val classifiers = downloads.getAsJsonObject("classifiers") ?: continue
                val nativesInfo = lib.getAsJsonObject("natives")
                
                if (nativesInfo == null) continue
                totalNativeLibs++

                var resolvedKey: String? = null
                var foundInNativesInfo = false
                
                for (key in fallbackKeys) {
                    if (nativesInfo.has(key)) {
                        resolvedKey = nativesInfo.get(key)?.asString
                        foundInNativesInfo = true
                        println("[DependencyDownloader]   $libName: found '$key' in nativesInfo -> resolvedKey='$resolvedKey'")
                        break
                    } else if (classifiers.has(key)) {
                        resolvedKey = key
                        println("[DependencyDownloader]   $libName: found '$key' directly in classifiers")
                        break
                    }
                }
                
                if (resolvedKey == null) {
                    val nativesKeys = nativesInfo.keySet().joinToString(",")
                    val classifierKeys = classifiers.keySet().joinToString(",")
                    println("[DependencyDownloader]   $libName: NO MATCH - nativesInfo keys: [$nativesKeys], classifiers keys: [$classifierKeys]")
                    notFoundLibs++
                    continue
                }

                val nativeLib = classifiers.get(resolvedKey)?.asJsonObject
                if (nativeLib == null) {
                    println("[DependencyDownloader]   $libName: classifier '$resolvedKey' not found in classifiers object")
                    notFoundLibs++
                    continue
                }
                
                val nativePath = nativeLib.get("path")?.asString
                val nativeUrl = nativeLib.get("url")?.asString
                val nativeSha1 = nativeLib.get("sha1")?.asString
                
                if (nativePath == null || nativeUrl == null) {
                    println("[DependencyDownloader]   $libName: missing path or url in classifier")
                    notFoundLibs++
                    continue
                }

                foundNativeLibs++
                val nativeFile = File(librariesDir, nativePath)

                val needDownload = if (!nativeFile.exists()) {
                    println("[DependencyDownloader]   $libName: NEED DOWNLOAD (file not exists)")
                    true
                } else if (nativeSha1 != null) {
                    val actualSha1 = HashUtils.sha1File(nativeFile)
                    val sha1Match = actualSha1 == nativeSha1.lowercase()
                    if (!sha1Match) {
                        println("[DependencyDownloader]   $libName: NEED DOWNLOAD (sha1 mismatch: expected=$nativeSha1, actual=$actualSha1)")
                    } else {
                        println("[DependencyDownloader]   $libName: SKIP (already exists with correct sha1)")
                    }
                    !sha1Match
                } else {
                    println("[DependencyDownloader]   $libName: SKIP (already exists, no sha1 check)")
                    false
                }

                if (needDownload) {
                    missingNatives.add(Triple(nativeUrl, nativePath, nativeSha1 ?: ""))
                    if (nativeFile.exists() && nativeSha1 != null) {
                        nativeFile.delete()
                    }
                }
            }

            println("[DependencyDownloader] downloadNativeClassifiers SUMMARY:")
            println("  Total native libs: $totalNativeLibs")
            println("  Found matching: $foundNativeLibs")
            println("  Not found / no match: $notFoundLibs")
            println("  Need download: ${missingNatives.size}")

            if (missingNatives.isEmpty()) {
                println("[DependencyDownloader] All native JARs already present or no matching natives found")
                return@withContext true
            }

            println("[DependencyDownloader] Downloading ${missingNatives.size} native classifier JARs for $nativeKey")

            var successCount = 0
            for ((index, triple) in missingNatives.withIndex()) {
                val (originalUrl, path, sha1) = triple
                val nativeFile = File(librariesDir, path)

                val downloadUrl = GameConfig.replaceWithMirror(originalUrl)
                try {
                    transfer.downloadFileWithRetry(downloadUrl, nativeFile, sha1.ifEmpty { null }, 2) { progress, _ ->
                        val natProgress = (index.toFloat() + progress) / missingNatives.size
                        val mappedProgress = progressMapper(natProgress)
                        onProgress(RepairProgress(
                            RepairStage.DOWNLOADING_LIBRARIES,
                            "下载原生库 (${index + 1}/${missingNatives.size}): ${nativeFile.name}",
                            mappedProgress,
                            nativeFile.name,
                            missingNatives.size,
                            index + 1
                        ))
                    }
                    successCount++
                } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                    println("[DependencyDownloader] Native classifier failed: $path, trying original")
                    try {
                        transfer.downloadFileWithRetry(originalUrl, nativeFile, sha1.ifEmpty { null }, 2) { progress, _ ->
                            val natProgress = (index.toFloat() + progress) / missingNatives.size
                            onProgress(RepairProgress(
                                RepairStage.DOWNLOADING_LIBRARIES,
                                "下载原生库 (${index + 1}/${missingNatives.size}): ${nativeFile.name}",
                                progressMapper(natProgress),
                                nativeFile.name,
                                missingNatives.size,
                                index + 1
                            ))
                        }
                        successCount++
                    } catch (e2: Exception) {
                        println("[DependencyDownloader] Failed to download native $path: ${e2.message}")
                    }
                }
            }

            println("[DependencyDownloader] Native classifiers: $successCount/${missingNatives.size} downloaded successfully")
            successCount > 0
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[DependencyDownloader] Error downloading native classifiers: ${e.message}")
            false
        }
    }

    private suspend fun downloadMissingAssets(
        versionId: String,
        onProgress: (RepairProgress) -> Unit,
        progressMapper: (Float) -> Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val versionDir = File(versionsDir, versionId)
            val versionJson = File(versionDir, "$versionId.json")

            if (!versionJson.exists()) return@withContext false

            val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
            val assetIndexInfo = json.getAsJsonObject("assetIndex") ?: return@withContext false
            val assetId = assetIndexInfo.get("id")?.asString ?: versionId
            val assetIndexUrl = assetIndexInfo.get("url")?.asString ?: return@withContext false

            val indexFile = File(assetsDir, "indexes/$assetId.json")
            if (!indexFile.exists()) {
                indexFile.parentFile?.mkdirs()
                val mirroredAssetIndexUrl = GameConfig.replaceWithMirror(assetIndexUrl)
                try {
                    transfer.downloadFileWithRetry(mirroredAssetIndexUrl, indexFile, null, 2) { _, _ -> }
                } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                    try {
                        transfer.downloadFileWithRetry(assetIndexUrl, indexFile, null, 2) { _, _ -> }
                    } catch (_: Exception) {}
                }
            }

            if (!indexFile.exists()) return@withContext false

            val indexJson = gson.fromJson(indexFile.readText(), JsonObject::class.java)
            val objects = indexJson.getAsJsonObject("objects") ?: return@withContext true
            val entries = objects.entrySet().toList()

            val missingAssets = mutableListOf<Pair<String, String>>()
            for ((name, element) in entries) {
                val hash = element.asJsonObject.get("hash")?.asString ?: continue
                val subDir = hash.substring(0, 2)
                val assetFile = File(assetsDir, "objects/$subDir/$hash")

                if (!assetFile.exists()) {
                    missingAssets.add(name to hash)
                }
            }

            if (missingAssets.isEmpty()) return@withContext true

            // 滑动窗口并发（对齐主安装链 64 路）：串行逐个下载在数千个资源文件场景极慢
            val totalMissing = missingAssets.size
            val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val semaphore = kotlinx.coroutines.sync.Semaphore(64)
            kotlinx.coroutines.coroutineScope {
                missingAssets.forEach { (name, hash) ->
                    launch {
                        semaphore.withPermit {
                            val subDir = hash.substring(0, 2)
                            val assetFile = File(assetsDir, "objects/$subDir/$hash")

                            try {
                                // assets 官方 CDN 国内实测远优于镜像（镜像现为 302 跳转节点池），官方优先
                                val originalAssetUrl = "https://resources.download.minecraft.net/$subDir/$hash"
                                try {
                                    transfer.downloadFile(originalAssetUrl, assetFile)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                                    val assetUrl = GameConfig.getAssetDownloadUrl(hash, 0)
                                    transfer.downloadFile(assetUrl, assetFile)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                                println("[DependencyDownloader] Failed to download asset $name: ${e.message}")
                            }

                            val done = completedCount.incrementAndGet()
                            if (done % 20 == 0 || done == totalMissing) {
                                val assetProgress = done.toFloat() / totalMissing
                                val mappedProgress = progressMapper(assetProgress)
                                onProgress(RepairProgress(
                                    RepairStage.DOWNLOADING_ASSETS,
                                    "下载资源文件 ($done/$totalMissing)...",
                                    mappedProgress,
                                    name,
                                    totalMissing,
                                    done
                                ))
                            }
                        }
                    }
                }
            }

            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private fun extractNatives(versionId: String) {
        val versionDir = File(versionsDir, versionId)
        val versionJson = File(versionDir, "$versionId.json")

        if (!versionJson.exists()) {
            println("[DependencyDownloader] extractNatives: versionJson not found: ${versionJson.absolutePath}")
            return
        }
        
        if (!nativesDir.exists()) {
            nativesDir.mkdirs()
            println("[DependencyDownloader] extractNatives: created natives dir: ${nativesDir.absolutePath}")
        }

        try {
            val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
            val libraries = json.getAsJsonArray("libraries") ?: run {
                println("[DependencyDownloader] extractNatives: no libraries in version json")
                return
            }

            val classifierKeys = when {
                isArm64 -> listOf("natives-linux-arm64", "natives-linux", "linux-arm64")
                isArm32 -> listOf("natives-linux-arm32", "natives-linux", "linux-arm32")
                else -> listOf("natives-linux", "linux")
            }
            println("[DependencyDownloader] extractNatives: architecture=${if(isArm64) "arm64" else if(isArm32) "arm32" else "x86"}, classifierKeys=$classifierKeys")

            var extractedCount = 0
            var skippedCount = 0
            var notFoundCount = 0
            var totalNativeLibs = 0
            
            for (libElement in libraries) {
                val lib = libElement.asJsonObject
                val libName = lib.get("name")?.asString ?: "unknown"
                val rules = lib.getAsJsonArray("rules")
                if (rules != null && !DependencyUtils.evaluateRules(rules)) continue

                val downloads = lib.getAsJsonObject("downloads") ?: continue
                val classifiers = downloads.getAsJsonObject("classifiers") ?: continue
                val nativesInfo = lib.getAsJsonObject("natives") ?: continue
                
                totalNativeLibs++

                var nativeKey: String? = null
                for (key in classifierKeys) {
                    if (nativesInfo.has(key)) {
                        nativeKey = nativesInfo.get(key)?.asString
                        if (nativeKey == null || !classifiers.has(nativeKey)) {
                            nativeKey = key
                        }
                        break
                    }
                }
                if (nativeKey == null) {
                    println("[DependencyDownloader] extractNatives: no matching classifier for $libName")
                    notFoundCount++
                    continue
                }

                val nativeLib = classifiers.get(nativeKey)?.asJsonObject ?: continue
                val nativePath = nativeLib.get("path")?.asString ?: continue
                val nativeFile = File(librariesDir, nativePath)

                if (nativeFile.exists() && nativeFile.length() > 1024) {
                    try {
                        val soFilesBefore = nativesDir.listFiles()?.filter { it.name.endsWith(".so") }?.size ?: 0
                        DependencyUtils.extractZip(nativeFile, nativesDir)
                        val soFilesAfter = nativesDir.listFiles()?.filter { it.name.endsWith(".so") }?.size ?: 0
                        val newSoFiles = soFilesAfter - soFilesBefore
                        
                        if (newSoFiles > 0) {
                            println("[DependencyDownloader] extractNatives: EXTRACTED $nativePath ($newSoFiles .so files)")
                            extractedCount++
                        } else {
                            println("[DependencyDownloader] extractNatives: SKIPPED $nativePath (no .so files inside)")
                            skippedCount++
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                        println("[DependencyDownloader] extractNatives: ERROR extracting $nativePath: ${e.message}")
                    }
                } else {
                    println("[DependencyDownloader] extractNatives: NOT FOUND $nativePath (exists=${nativeFile.exists()}, size=${nativeFile.length()})")
                    notFoundCount++
                }
            }

            val finalSoFiles = nativesDir.listFiles()?.filter { it.name.endsWith(".so") && it.isFile && it.length() > 1024 } ?: emptyList()
            
            println("[DependencyDownloader] extractNatives SUMMARY:")
            println("  Total native libs in JSON: $totalNativeLibs")
            println("  Successfully extracted: $extractedCount")
            println("  Skipped (no .so inside): $skippedCount")
            println("  Not found / too small: $notFoundCount")
            println("  Final .so files in ${nativesDir.name}: ${finalSoFiles.size}")
            finalSoFiles.sortedBy { it.name }.forEach { f ->
                println("    - ${f.name} (${f.length()} bytes)")
            }
            
            android.util.Log.d("DependencyDownloader", "Extracted natives: $extractedCount extracted, $skippedCount skipped, $notFoundCount not found, ${finalSoFiles.size} .so files total")
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[DependencyDownloader] extractNatives ERROR: ${e.message}")
            e.printStackTrace()
        }
    }

}
