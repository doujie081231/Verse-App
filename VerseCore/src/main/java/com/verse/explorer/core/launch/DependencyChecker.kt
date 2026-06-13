package com.verse.explorer.core.launch

import android.content.Context
import android.os.Build
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.java.JavaRuntimeManager
import com.verse.explorer.core.util.HashUtils
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File

data class DependencyStatus(
    val id: String,
    val name: String,
    val category: DependencyCategory,
    val status: CheckStatus,
    val message: String,
    val required: Boolean = true,
    val autoFixable: Boolean = true,
    val filePath: String? = null,
    val expectedSha1: String? = null,
    val downloadUrl: String? = null,
    val mirrorUrl: String? = null,
    val expectedSize: Long = 0,
    val subItems: List<DependencySubItem> = emptyList()
)

data class DependencySubItem(
    val name: String,
    val filePath: String,
    val downloadUrl: String,
    val mirrorUrl: String,
    val expectedSha1: String? = null,
    val expectedSize: Long = 0,
    val status: CheckStatus = CheckStatus.MISSING
)

enum class DependencyCategory {
    JAVA_RUNTIME,
    VERSION_JSON,
    CLIENT_JAR,
    LIBRARIES,
    ASSETS,
    NATIVES,
    RENDERER
}

enum class CheckStatus {
    OK,
    MISSING,
    CORRUPTED,
    INCOMPLETE,
    OUTDATED,
    UNKNOWN
}

data class DependencyCheckResult(
    val allOk: Boolean,
    val dependencies: List<DependencyStatus>,
    val missingCount: Int,
    val corruptedCount: Int,
    val canAutoFix: Boolean
)

class DependencyChecker(private val context: Context) {

    private val gameDir = GameConfig.getGameDir(context)
    private val gson = GsonBuilder().setLenient().create()

    fun checkAll(versionId: String): DependencyCheckResult {
        android.util.Log.d("DependencyChecker", "checkAll called with versionId: $versionId")
        android.util.Log.d("DependencyChecker", "gameDir: ${gameDir.absolutePath}")
        android.util.Log.d("DependencyChecker", "gameDir exists: ${gameDir.exists()}")
        
        val versionsDir = File(gameDir, "versions")
        android.util.Log.d("DependencyChecker", "versionsDir: ${versionsDir.absolutePath}")
        android.util.Log.d("DependencyChecker", "versionsDir exists: ${versionsDir.exists()}")
        
        val versionDir = File(versionsDir, versionId)
        android.util.Log.d("DependencyChecker", "versionDir: ${versionDir.absolutePath}")
        android.util.Log.d("DependencyChecker", "versionDir exists: ${versionDir.exists()}")
        
        if (versionDir.exists()) {
            versionDir.listFiles()?.forEach { file ->
                android.util.Log.d("DependencyChecker", "  - ${file.name} (${if (file.isFile) "file" else "dir"}, ${file.length()} bytes)")
            }
        }
        
        val dependencies = mutableListOf<DependencyStatus>()

        dependencies.addAll(checkJavaRuntime())
        dependencies.addAll(checkVersionJson(versionId))
        dependencies.addAll(checkClientJar(versionId))
        dependencies.addAll(checkLibraries(versionId))
        dependencies.addAll(checkAssets(versionId))
        dependencies.addAll(checkNatives(versionId))
        dependencies.addAll(checkRenderer())

        val missingCount = dependencies.count { it.status == CheckStatus.MISSING }
        val corruptedCount = dependencies.count {
            it.status == CheckStatus.CORRUPTED || it.status == CheckStatus.INCOMPLETE
        }
        val allOk = missingCount == 0 && corruptedCount == 0
        val canAutoFix = dependencies.filter { it.status != CheckStatus.OK }
            .all { it.autoFixable }

        return DependencyCheckResult(
            allOk = allOk,
            dependencies = dependencies,
            missingCount = missingCount,
            corruptedCount = corruptedCount,
            canAutoFix = canAutoFix
        )
    }

    fun checkJavaRuntime(): List<DependencyStatus> {
        val results = mutableListOf<DependencyStatus>()
        val hasJava = JavaRuntimeManager.hasJava(context, 17)

        if (hasJava) {
            val runtimes = JavaRuntimeManager.getInstalledRuntimes(context)
            val bestRuntime = runtimes.maxByOrNull { it.version }
            if (bestRuntime != null) {
                val javaBin = File(bestRuntime.path, "bin/java")
                if (javaBin.exists() && javaBin.canExecute()) {
                    results.add(DependencyStatus(
                        id = "java_runtime",
                        name = "Java ${bestRuntime.version} 运行时",
                        category = DependencyCategory.JAVA_RUNTIME,
                        status = CheckStatus.OK,
                        message = "Java ${bestRuntime.version} 已安装 (${bestRuntime.path.name})",
                        filePath = javaBin.absolutePath
                    ))
                } else {
                    results.add(DependencyStatus(
                        id = "java_runtime",
                        name = "Java 运行时",
                        category = DependencyCategory.JAVA_RUNTIME,
                        status = CheckStatus.CORRUPTED,
                        message = "Java运行时已安装但可执行文件损坏",
                        autoFixable = true,
                        filePath = javaBin.absolutePath,
                        downloadUrl = "builtin"
                    ))
                }
            }
        } else {
            results.add(DependencyStatus(
                id = "java_runtime",
                name = "Java 17+ 运行时",
                category = DependencyCategory.JAVA_RUNTIME,
                status = CheckStatus.MISSING,
                message = "未安装Java 17或更高版本运行时",
                autoFixable = true,
                downloadUrl = "builtin"
            ))
        }

        return results
    }

    fun checkVersionJson(versionId: String): List<DependencyStatus> {
        val results = mutableListOf<DependencyStatus>()
        val versionDir = File(gameDir, "versions/$versionId")
        val versionJson = File(versionDir, "$versionId.json")

        if (!versionJson.exists()) {
            results.add(DependencyStatus(
                id = "version_json",
                name = "版本信息文件 ($versionId.json)",
                category = DependencyCategory.VERSION_JSON,
                status = CheckStatus.MISSING,
                message = "版本信息文件不存在",
                autoFixable = true,
                filePath = versionJson.absolutePath
            ))
        } else if (versionJson.length() == 0L) {
            results.add(DependencyStatus(
                id = "version_json",
                name = "版本信息文件 ($versionId.json)",
                category = DependencyCategory.VERSION_JSON,
                status = CheckStatus.CORRUPTED,
                message = "版本信息文件为空",
                autoFixable = true,
                filePath = versionJson.absolutePath
            ))
        } else {
            try {
                val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
                if (json.has("id") && json.has("libraries") && json.has("mainClass")) {
                    results.add(DependencyStatus(
                        id = "version_json",
                        name = "版本信息文件 ($versionId.json)",
                        category = DependencyCategory.VERSION_JSON,
                        status = CheckStatus.OK,
                        message = "版本信息文件完整",
                        filePath = versionJson.absolutePath
                    ))
                } else {
                    results.add(DependencyStatus(
                        id = "version_json",
                        name = "版本信息文件 ($versionId.json)",
                        category = DependencyCategory.VERSION_JSON,
                        status = CheckStatus.CORRUPTED,
                        message = "版本信息文件缺少必要字段",
                        autoFixable = true,
                        filePath = versionJson.absolutePath
                    ))
                }
            } catch (e: Exception) {
                results.add(DependencyStatus(
                    id = "version_json",
                    name = "版本信息文件 ($versionId.json)",
                    category = DependencyCategory.VERSION_JSON,
                    status = CheckStatus.CORRUPTED,
                    message = "版本信息文件解析失败: ${e.message}",
                    autoFixable = true,
                    filePath = versionJson.absolutePath
                ))
            }
        }

        return results
    }

    fun checkClientJar(versionId: String): List<DependencyStatus> {
        val results = mutableListOf<DependencyStatus>()
        val versionDir = File(gameDir, "versions/$versionId")
        val clientJar = File(versionDir, "$versionId.jar")
        val versionJson = File(versionDir, "$versionId.json")

        if (!clientJar.exists()) {
            var downloadUrl: String? = null
            var expectedSha1: String? = null
            var expectedSize = 0L

            if (versionJson.exists()) {
                try {
                    val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
                    val clientDownload = json.getAsJsonObject("downloads")
                        ?.getAsJsonObject("client")
                    downloadUrl = clientDownload?.get("url")?.asString
                    expectedSha1 = clientDownload?.get("sha1")?.asString
                    expectedSize = clientDownload?.get("size")?.asLong ?: 0L
                } catch (_: Exception) {}
            }

            results.add(DependencyStatus(
                id = "client_jar",
                name = "游戏客户端 ($versionId.jar)",
                category = DependencyCategory.CLIENT_JAR,
                status = CheckStatus.MISSING,
                message = "游戏客户端JAR文件不存在",
                autoFixable = true,
                filePath = clientJar.absolutePath,
                expectedSha1 = expectedSha1,
                downloadUrl = downloadUrl,
                expectedSize = expectedSize
            ))
        } else if (clientJar.length() == 0L) {
            results.add(DependencyStatus(
                id = "client_jar",
                name = "游戏客户端 ($versionId.jar)",
                category = DependencyCategory.CLIENT_JAR,
                status = CheckStatus.CORRUPTED,
                message = "游戏客户端JAR文件为空",
                autoFixable = true,
                filePath = clientJar.absolutePath
            ))
        } else {
            var sha1Ok = true
            var expectedSha1: String? = null

            if (versionJson.exists()) {
                try {
                    val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
                    expectedSha1 = json.getAsJsonObject("downloads")
                        ?.getAsJsonObject("client")
                        ?.get("sha1")?.asString

                    if (expectedSha1 != null) {
                        val actualSha1 = HashUtils.sha1File(clientJar)
                        if (actualSha1?.lowercase() != expectedSha1.lowercase()) {
                            sha1Ok = false
                        }
                    }
                } catch (_: Exception) {}
            }

            if (sha1Ok) {
                results.add(DependencyStatus(
                    id = "client_jar",
                    name = "游戏客户端 ($versionId.jar)",
                    category = DependencyCategory.CLIENT_JAR,
                    status = CheckStatus.OK,
                    message = "游戏客户端文件完整 (${formatSize(clientJar.length())})",
                    filePath = clientJar.absolutePath
                ))
            } else {
                var downloadUrl: String? = null
                var expectedSize = 0L
                try {
                    val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
                    val clientDownload = json.getAsJsonObject("downloads")
                        ?.getAsJsonObject("client")
                    downloadUrl = clientDownload?.get("url")?.asString
                    expectedSize = clientDownload?.get("size")?.asLong ?: 0L
                } catch (_: Exception) {}

                results.add(DependencyStatus(
                    id = "client_jar",
                    name = "游戏客户端 ($versionId.jar)",
                    category = DependencyCategory.CLIENT_JAR,
                    status = CheckStatus.CORRUPTED,
                    message = "游戏客户端文件SHA1校验失败，文件已损坏",
                    autoFixable = true,
                    filePath = clientJar.absolutePath,
                    expectedSha1 = expectedSha1,
                    downloadUrl = downloadUrl,
                    expectedSize = expectedSize
                ))
            }
        }

        return results
    }

    fun checkLibraries(versionId: String): List<DependencyStatus> {
        val results = mutableListOf<DependencyStatus>()
        val versionDir = File(gameDir, "versions/$versionId")
        val versionJson = File(versionDir, "$versionId.json")
        val librariesDir = File(gameDir, "libraries")

        if (!versionJson.exists()) {
            results.add(DependencyStatus(
                id = "libraries",
                name = "依赖库文件",
                category = DependencyCategory.LIBRARIES,
                status = CheckStatus.UNKNOWN,
                message = "无法检查库文件（版本信息缺失）",
                autoFixable = true
            ))
            return results
        }

        try {
            val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
            val libraries = json.getAsJsonArray("libraries") ?: return results

            var missingCount = 0
            var corruptedCount = 0
            var totalCount = 0
            val missingLibs = mutableListOf<String>()
            val corruptedLibs = mutableListOf<String>()
            val subItems = mutableListOf<DependencySubItem>()

            for (libElement in libraries) {
                val lib = libElement.asJsonObject
                val rules = lib.getAsJsonArray("rules")
                if (rules != null && !evaluateRules(rules)) continue

                val downloads = lib.getAsJsonObject("downloads") ?: continue
                val artifact = downloads.getAsJsonObject("artifact") ?: continue
                val path = artifact.get("path")?.asString ?: continue
                val url = artifact.get("url")?.asString ?: continue

                totalCount++
                val libFile = File(librariesDir, path)
                val expectedSha1 = artifact.get("sha1")?.asString
                val expectedSize = artifact.get("size")?.asLong ?: 0L

                if (!libFile.exists()) {
                    missingCount++
                    if (missingLibs.size < 5) {
                        missingLibs.add(libFile.name)
                    }
                    val mirrorUrl = GameConfig.getMirrorUrl(url, path)
                    subItems.add(DependencySubItem(
                        name = libFile.name,
                        filePath = libFile.absolutePath,
                        downloadUrl = url,
                        mirrorUrl = mirrorUrl,
                        expectedSha1 = expectedSha1,
                        expectedSize = expectedSize,
                        status = CheckStatus.MISSING
                    ))
                } else if (expectedSha1 != null) {
                    val actualSha1 = HashUtils.sha1File(libFile)
                    if (actualSha1?.lowercase() != expectedSha1.lowercase()) {
                        corruptedCount++
                        if (corruptedLibs.size < 5) {
                            corruptedLibs.add(libFile.name)
                        }
                        val mirrorUrl = GameConfig.getMirrorUrl(url, path)
                        subItems.add(DependencySubItem(
                            name = libFile.name,
                            filePath = libFile.absolutePath,
                            downloadUrl = url,
                            mirrorUrl = mirrorUrl,
                            expectedSha1 = expectedSha1,
                            expectedSize = expectedSize,
                            status = CheckStatus.CORRUPTED
                        ))
                    }
                }
            }

            when {
                missingCount == 0 && corruptedCount == 0 -> {
                    results.add(DependencyStatus(
                        id = "libraries",
                        name = "依赖库文件 (${totalCount}个)",
                        category = DependencyCategory.LIBRARIES,
                        status = CheckStatus.OK,
                        message = "所有依赖库文件完整 (${totalCount}个)"
                    ))
                }
                missingCount > 0 -> {
                    val msg = buildString {
                        append("缺少 $missingCount/$totalCount 个库文件")
                        if (missingLibs.isNotEmpty()) {
                            append(" (如: ${missingLibs.joinToString(", ")})")
                        }
                    }
                    results.add(DependencyStatus(
                        id = "libraries",
                        name = "依赖库文件 (${totalCount}个)",
                        category = DependencyCategory.LIBRARIES,
                        status = CheckStatus.MISSING,
                        message = msg,
                        autoFixable = true,
                        subItems = subItems
                    ))
                }
                else -> {
                    val msg = buildString {
                        append("$corruptedCount/$totalCount 个库文件损坏")
                        if (corruptedLibs.isNotEmpty()) {
                            append(" (如: ${corruptedLibs.joinToString(", ")})")
                        }
                    }
                    results.add(DependencyStatus(
                        id = "libraries",
                        name = "依赖库文件 (${totalCount}个)",
                        category = DependencyCategory.LIBRARIES,
                        status = CheckStatus.CORRUPTED,
                        message = msg,
                        autoFixable = true,
                        subItems = subItems
                    ))
                }
            }
        } catch (e: Exception) {
            results.add(DependencyStatus(
                id = "libraries",
                name = "依赖库文件",
                category = DependencyCategory.LIBRARIES,
                status = CheckStatus.UNKNOWN,
                message = "检查库文件时出错: ${e.message}",
                autoFixable = true
            ))
        }

        return results
    }

    fun checkAssets(versionId: String): List<DependencyStatus> {
        val results = mutableListOf<DependencyStatus>()
        val versionDir = File(gameDir, "versions/$versionId")
        val versionJson = File(versionDir, "$versionId.json")
        val assetsDir = File(gameDir, "assets")

        if (!versionJson.exists()) {
            results.add(DependencyStatus(
                id = "assets",
                name = "资源文件",
                category = DependencyCategory.ASSETS,
                status = CheckStatus.UNKNOWN,
                message = "无法检查资源文件（版本信息缺失）",
                autoFixable = true
            ))
            return results
        }

        try {
            val json = gson.fromJson(versionJson.readText(), JsonObject::class.java)
            val assetIndex = json.getAsJsonObject("assetIndex")
            val assetId = assetIndex?.get("id")?.asString ?: versionId
            val indexFile = File(assetsDir, "indexes/$assetId.json")
            val objectsDir = File(assetsDir, "objects")

            if (!indexFile.exists()) {
                results.add(DependencyStatus(
                    id = "assets_index",
                    name = "资源索引 ($assetId)",
                    category = DependencyCategory.ASSETS,
                    status = CheckStatus.MISSING,
                    message = "资源索引文件不存在",
                    autoFixable = true,
                    filePath = indexFile.absolutePath,
                    downloadUrl = assetIndex?.get("url")?.asString
                ))
                return results
            }

            val indexJson = gson.fromJson(indexFile.readText(), JsonObject::class.java)
            val objects = indexJson.getAsJsonObject("objects") ?: return results
            val totalAssets = objects.size()

            var missingAssets = 0
            var checkedCount = 0
            val checkLimit = 200

            for ((name, element) in objects.entrySet()) {
                if (checkedCount >= checkLimit) break
                checkedCount++

                val hash = element.asJsonObject.get("hash")?.asString ?: continue
                val subDir = hash.substring(0, 2)
                val assetFile = File(objectsDir, "$subDir/$hash")

                if (!assetFile.exists()) {
                    missingAssets++
                }
            }

            val estimatedMissing = if (checkedCount < totalAssets) {
                (missingAssets * totalAssets / checkedCount)
            } else {
                missingAssets
            }

            if (estimatedMissing == 0) {
                results.add(DependencyStatus(
                    id = "assets",
                    name = "资源文件 (${totalAssets}个)",
                    category = DependencyCategory.ASSETS,
                    status = CheckStatus.OK,
                    message = "资源文件完整 (已抽样检查${checkedCount}个)"
                ))
            } else {
                results.add(DependencyStatus(
                    id = "assets",
                    name = "资源文件 (${totalAssets}个)",
                    category = DependencyCategory.ASSETS,
                    status = CheckStatus.INCOMPLETE,
                    message = "约缺少 $estimatedMissing/$totalAssets 个资源文件",
                    autoFixable = true
                ))
            }
        } catch (e: Exception) {
            results.add(DependencyStatus(
                id = "assets",
                name = "资源文件",
                category = DependencyCategory.ASSETS,
                status = CheckStatus.UNKNOWN,
                message = "检查资源文件时出错: ${e.message}",
                autoFixable = true
            ))
        }

        return results
    }

    fun checkNatives(versionId: String): List<DependencyStatus> {
        val results = mutableListOf<DependencyStatus>()
        val nativesDir = File(gameDir, "natives")
        
        if (!nativesDir.exists()) {
            nativesDir.mkdirs()
        }
        
        val soFiles = nativesDir.listFiles()?.filter {
            it.name.endsWith(".so") && it.isFile && it.length() > 1024
        } ?: emptyList()
        
        android.util.Log.d("DependencyChecker", "checkNatives: nativesDir=${nativesDir.absolutePath}, soFiles.count=${soFiles.size}")
        soFiles.forEach { f ->
            android.util.Log.d("DependencyChecker", "  - ${f.name} (${f.length()} bytes)")
        }

        if (soFiles.isEmpty()) {
            println("[DependencyChecker] No .so files found, will use builtin natives")
            results.add(DependencyStatus(
                id = "natives",
                name = "原生库文件",
                category = DependencyCategory.NATIVES,
                status = CheckStatus.MISSING,
                message = "原生库文件缺失，将使用内置的LWJGL ARM64库",
                autoFixable = true,
                filePath = nativesDir.absolutePath
            ))
        } else {
            val hasLwjgl = soFiles.any { it.name.contains("lwjgl", ignoreCase = true) }
            val hasGlfw = soFiles.any { it.name.contains("glfw", ignoreCase = true) }
            val hasOpenal = soFiles.any { it.name.contains("openal", ignoreCase = true) }
            
            android.util.Log.d("DependencyChecker", "  hasLwjgl=$hasLwjgl, hasGlfw=$hasGlfw, hasOpenal=$hasOpenal")
            
            if (hasLwjgl && (hasGlfw || hasOpenal)) {
                results.add(DependencyStatus(
                    id = "natives",
                    name = "原生库文件 (${soFiles.size}个)",
                    category = DependencyCategory.NATIVES,
                    status = CheckStatus.OK,
                    message = "原生库文件完整 (${soFiles.size}个.so文件)"
                ))
            } else {
                println("[DependencyChecker] Missing critical libs, will use builtin natives")
                results.add(DependencyStatus(
                    id = "natives",
                    name = "原生库文件 (${soFiles.size}个)",
                    category = DependencyCategory.NATIVES,
                    status = CheckStatus.INCOMPLETE,
                    message = "原生库文件不完整，将使用内置的LWJGL ARM64库补充",
                    autoFixable = true,
                    filePath = nativesDir.absolutePath
                ))
            }
        }

        return results
    }

    fun checkRenderer(): List<DependencyStatus> {
        val results = mutableListOf<DependencyStatus>()
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        val searchPaths = mutableListOf<String>().apply {
            add(nativeLibDir)
            add("/system/lib64")
            add("/system/lib")
            val appDataLibs = File(context.applicationInfo.dataDir, "lib")
            if (appDataLibs.exists()) add(appDataLibs.absolutePath)
            val gameNativesDir = File(gameDir, "natives")
            if (gameNativesDir.exists()) add(gameNativesDir.absolutePath)
        }

        fun libExists(libName: String): Boolean {
            return searchPaths.any { path -> File(path, libName).exists() }
        }

        fun findLibPath(libName: String): String? {
            return searchPaths.firstOrNull { path -> File(path, libName).exists() }
                ?.let { File(it, libName).absolutePath }
        }

        val rendererLibs = mapOf(
            "Zink" to listOf("libzink.so", "libvulkan.so"),
            "VirGL" to listOf("libvirglrenderer.so", "libvirgl.so"),
            "GL4ES" to listOf("libgl4es.so"),
            "Mesa" to listOf("libEGL.so", "libGLESv2.so")
        )

        val availableRenderers = mutableListOf<String>()
        val foundLibs = mutableSetOf<String>()
        val allRendererPaths = mutableMapOf<String, List<String>>()

        for ((name, libs) in rendererLibs) {
            val foundForThis = mutableListOf<String>()
            val allExist = libs.all { lib ->
                val exists = libExists(lib)
                if (exists) {
                    foundLibs.add(lib)
                    val libPath = findLibPath(lib)
                    if (libPath != null) foundForThis.add("$lib@$libPath")
                }
                exists
            }
            if (allExist) {
                availableRenderers.add(name)
                allRendererPaths[name] = foundForThis
            }
        }

        android.util.Log.d("DependencyChecker", "checkRenderer: searchPaths=$searchPaths")
        android.util.Log.d("DependencyChecker", "  foundLibs=$foundLibs")
        android.util.Log.d("DependencyChecker", "  availableRenderers=$availableRenderers")

        if (availableRenderers.isNotEmpty()) {
            val best = availableRenderers.first()
            val paths = allRendererPaths[best] ?: emptyList()
            results.add(DependencyStatus(
                id = "renderer",
                name = "渲染器 ($best)",
                category = DependencyCategory.RENDERER,
                status = CheckStatus.OK,
                message = "$best 渲染器可用 (可用: ${availableRenderers.joinToString(", ")})",
                required = true,
                autoFixable = false
            ))
        } else {
            val detail = if (foundLibs.isNotEmpty()) "\n找到部分库: ${foundLibs.joinToString(", ")}" else ""
            results.add(DependencyStatus(
                id = "renderer",
                name = "渲染器库",
                category = DependencyCategory.RENDERER,
                status = CheckStatus.MISSING,
                message = "未找到完整的渲染器库$detail\n搜索路径:\n${searchPaths.joinToString("\n")}\n\n注意：如果游戏无法启动或显示黑屏，可能需要安装Turnip/VirGL/Mesa等OpenGL转译层",
                required = false,
                autoFixable = false
            ))
        }

        return results
    }

    private fun evaluateRules(rules: com.google.gson.JsonArray): Boolean {
        if (rules.size() == 0) return true

        var allowed = true
        for (ruleElement in rules) {
            val rule = ruleElement.asJsonObject
            val action = rule.get("action")?.asString ?: continue
            val os = rule.getAsJsonObject("os")
            val features = rule.getAsJsonObject("features")

            if (features != null) continue

            if (os != null) {
                val osName = os.get("name")?.asString ?: continue
                if (osName == "linux" || osName == "osx") {
                    allowed = action == "allow"
                }
            } else {
                allowed = action == "allow"
            }
        }
        return allowed
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024))
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
