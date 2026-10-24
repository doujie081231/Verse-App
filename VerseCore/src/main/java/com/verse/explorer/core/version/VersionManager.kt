package com.verse.explorer.core.version

import android.util.Log
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.download.SmartDownloadClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class VersionManager(private val gameDir: File) {

    companion object {
        /** 删除版本时目录的改名后缀：改名腾出版本名后再删，删不干净也不会占名/回列表 */
        private const val REMOVED_SUFFIX = "_removed"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .connectionPool(SmartDownloadClient.sharedConnectionPool)
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = 128
        })
        .dns(SmartDownloadClient.ipv4Only())
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .serializeNulls()
        .create()

    /**
     * 1. 非强制刷新时优先读本地缓存（10 分钟有效），避免每次都走网络导致加载慢；
     * 2. 多源竞速拉取，先成功者胜出，避免慢源阻塞；
     * 3. 成功后写回缓存。
     */
    suspend fun fetchRemoteVersions(forceRefresh: Boolean = false): VersionManifest = withContext(Dispatchers.IO) {
        val cacheFile = File(gameDir, "cache/version-manifest.json")

        // 非强制刷新先读缓存（10 分钟内有效）
        if (!forceRefresh && cacheFile.exists()) {
            try {
                val cached = gson.fromJson(cacheFile.readText(), CachedManifest::class.java)
                if (cached != null && cached.timestamp > 0 &&
                    System.currentTimeMillis() - cached.timestamp < 600_000
                ) {
                    Log.d("VersionManager", "Using cached version manifest (${cached.manifest.versions.size} versions)")
                    return@withContext cached.manifest
                }
            } catch (e: Exception) {
                Log.w("VersionManager", "Failed to read cache, ignoring: ${e.message}")
            }
        }

        // 多源并发竞速：所有镜像同时请求，先成功者胜出（避免首源慢/超时阻塞整列表）
        coroutineScope {
            var lastException: Exception? = null
            val jobs = GameConfig.MOJANG_MIRRORS.indices.map { index ->
                async {
                    try {
                        fetchManifestFromMirror(index, cacheFile)
                    } catch (e: Exception) {
                        Log.w("VersionManager", "Error from ${GameConfig.MOJANG_MIRRORS[index].name}: ${e.message}", e)
                        lastException = e
                        null
                    }
                }
            }
            jobs.forEach { job ->
                val manifest = job.await()
                if (manifest != null) {
                    return@coroutineScope manifest
                }
            }
            throw lastException ?: Exception("Failed to fetch version manifest from all mirrors")
        }
    }

    /** 从单个镜像拉取版本清单（成功则写缓存并返回） */
    private fun fetchManifestFromMirror(mirrorIndex: Int, cacheFile: File): VersionManifest {
        val mirror = GameConfig.MOJANG_MIRRORS[mirrorIndex]
        val url = GameConfig.getVersionManifestUrl(mirrorIndex)
        Log.d("VersionManager", "Fetching version manifest from: $url (${mirror.name})")

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "VerseExplorerX/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${mirror.name}")
            }
            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                throw Exception("Empty response: ${mirror.name}")
            }
            if (!body.trimStart().startsWith("{")) {
                throw Exception("Invalid JSON: ${mirror.name}")
            }
            Log.d("VersionManager", "Got response from ${mirror.name}, length: ${body.length}")

            val manifest = gson.fromJson(body, VersionManifest::class.java)
            Log.d("VersionManager", "Parsed ${manifest.versions.size} versions from ${mirror.name}")
            Log.d("VersionManager", "Latest release: ${manifest.latest.release}, snapshot: ${manifest.latest.snapshot}")

            try {
                cacheFile.parentFile?.mkdirs()
                val cached = CachedManifest(System.currentTimeMillis(), manifest)
                cacheFile.writeText(gson.toJson(cached))
                Log.d("VersionManager", "Version manifest cached to ${cacheFile.absolutePath}")
            } catch (e: Exception) {
                Log.w("VersionManager", "Failed to write cache: ${e.message}")
            }
            return manifest
        }
    }

    fun getLocalVersions(): List<MinecraftVersion> {
        val versionsDir = GameConfig.getVersionsDir(gameDir)
        if (!versionsDir.exists() || !versionsDir.isDirectory) {
            Log.d("VersionManager", "Versions dir does not exist: ${versionsDir.absolutePath}")
            return emptyList()
        }

        val dirs = versionsDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        Log.d("VersionManager", "Found ${dirs.size} version directories in ${versionsDir.absolutePath}")

        return dirs.mapNotNull { versionDir ->
            // 排除删除流程保留的 <id>_removed 目录，避免将待清理版本重新列出。
            if (versionDir.name.endsWith(REMOVED_SUFFIX)) {
                return@mapNotNull null
            }
            val jsonFile = File(versionDir, "${versionDir.name}.json")
            if (!jsonFile.exists()) {
                Log.d("VersionManager", "No JSON file for ${versionDir.name}, skipping")
                return@mapNotNull null
            }

            // 检查当前版本或继承的父版本目录中是否存在游戏 JAR。
            val jarFile = File(versionDir, "${versionDir.name}.jar")
            val hasJar = jarFile.exists() || hasInheritedJar(versionDir.name, jsonFile, versionsDir)

            if (!hasJar) {
                Log.d("VersionManager", "No JAR for ${versionDir.name}, skipping")
                return@mapNotNull null
            }

            try {
                val jsonText = jsonFile.readText()
                val version = gson.fromJson(jsonText, MinecraftVersion::class.java)
                Log.d("VersionManager", "Loaded local version: ${version.id} (${version.type})")
                version.copy(loaderHint = detectLoaderFromJson(jsonText))
            } catch (e: Exception) {
                Log.w("VersionManager", "Failed to parse ${jsonFile.name}: ${e.message}")
                null
            }
        }
    }

    /**
     * 判断版本名是否已被占用（用于安装前的重名检查）。
     *
     * 只有存在 `<id>.json` 的目录才算占用命名：
     *  - 中断/失败的残留空目录不是版本，不应阻止同名重新安装（否则一次失败的下载会让该版本永远「重名」）；
     *  - 删除流程留下的 `<id>_removed` 残留目录（及其自身名字）也不算占用，否则换名重下会撞车。
     */
    fun versionIdConflicts(id: String): Boolean {
        val versionsDir = GameConfig.getVersionsDir(gameDir)
        if (!versionsDir.exists() || !versionsDir.isDirectory) return false
        return versionsDir.listFiles { f -> f.isDirectory }?.any {
            it.name == id && !it.name.endsWith(REMOVED_SUFFIX) &&
                File(it, "${it.name}.json").isFile
        } == true
    }

    /**
     * 从磁盘移除一个版本，返回是否成功。
     *
     * 顺序是刻意的，每一步都在防「删不掉但名字被永久占用」：
     *  1. 先整体改名为 `<id>_removed` —— 即使后续删除失败（文件被游戏进程占用等），
     *     原版本名已经腾出，可以立刻重新安装同名版本；
     *  2. 再删目录内的 json —— 残留目录带着 json 会被识别为有效版本，
     *     改名成功但删除失败时，先删 json 可确保它不会再出现在版本列表里；
     *  3. 最后删整个目录，失败只记录日志（`_removed` 目录不会被当成版本）。
     *
     * 只有第一步改名失败才返回 false（说明目录被占用，调用方应提示用户重试）。
     */
    fun removeVersionFromDisk(id: String): Boolean {
        val versionsDir = GameConfig.getVersionsDir(gameDir)
        val dir = File(versionsDir, id)
        if (!dir.exists()) return true

        val removed = File(versionsDir, "${id}$REMOVED_SUFFIX")
        // 上次删除残留的 _removed 目录先清掉，否则改名会失败
        if (removed.exists()) removed.deleteRecursively()

        if (!dir.renameTo(removed)) {
            Log.w("VersionManager", "删除版本失败（目录被占用，无法改名）: $id")
            return false
        }

        runCatching {
            removed.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
                ?.forEach { json ->
                    if (!json.delete()) Log.w("VersionManager", "无法删除版本描述文件: ${json.name}")
                }
            if (!removed.deleteRecursively()) {
                Log.w("VersionManager", "版本目录未能完全删除，已重命名为 ${removed.name}: $id")
            }
        }.onFailure {
            Log.w("VersionManager", "清理 ${removed.name} 时出错: ${it.message}")
        }
        return true
    }

    fun detectLoaderFromJson(jsonText: String): String? {
        val t = jsonText.lowercase()
        return when {
            t.contains("neoforge") -> "neoforge"
            t.contains("quilt-loader") || t.contains("org.quiltmc") -> "quilt"
            t.contains("fabric") -> "fabric"
            t.contains("optifine") -> "optifine"
            t.contains("minecraftforge") || t.contains("liteloader") -> "forge"
            else -> null
        }
    }

    private fun hasInheritedJar(versionId: String, jsonFile: File, versionsDir: File): Boolean {
        try {
            val jsonText = jsonFile.readText()
            val jsonObj = JsonParser.parseString(jsonText).asJsonObject
            val parentId = jsonObj.get("jar")?.asString
                ?: jsonObj.get("inheritsFrom")?.asString
                ?: return false
            val parentDir = File(versionsDir, parentId)
            val parentJar = File(parentDir, "$parentId.jar")
            return parentJar.exists() && parentJar.length() > 1024
        } catch (_: Exception) {
            return false
        }
    }

    suspend fun downloadVersion(version: MinecraftVersion, progressCallback: ((Int, Int) -> Unit)? = null): Boolean {
        return withContext(Dispatchers.IO) {
            val versionDir = GameConfig.getVersionsDir(gameDir).resolve(version.id)
            if (!versionDir.exists()) {
                versionDir.mkdirs()
            }

            for (mirrorIndex in GameConfig.MOJANG_MIRRORS.indices) {
                try {
                    val url = GameConfig.getVersionJsonUrl(version.id, mirrorIndex)
                    Log.d("VersionManager", "Downloading version JSON: $url")

                    val request = Request.Builder().url(url)
                        .header("Accept", "application/json")
                        .header("User-Agent", "VerseExplorerX/1.0")
                        .build()

                    val response = client.newCall(request).execute()
                    try {
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""

                            if (body.isNotBlank() && body.trimStart().startsWith("{")) {
                                val jsonFile = File(versionDir, "${version.id}.json")
                                jsonFile.writeText(body)
                                Log.d("VersionManager", "Downloaded version JSON from $url")
                                return@withContext true
                            } else {
                                Log.w("VersionManager", "Invalid response from $url: ${body.take(100)}")
                            }
                        } else {
                            Log.w("VersionManager", "HTTP ${response.code} from $url")
                        }
                    } finally {
                        response.close()
                    }
                } catch (e: Exception) {
                    Log.w("VersionManager", "Error downloading from mirror $mirrorIndex: ${e.message}")
                }
            }

            false
        }
    }
}

data class VersionManifest(
    val latest: LatestVersions = LatestVersions(),
    val versions: List<MinecraftVersion> = emptyList()
)

data class LatestVersions(
    val release: String = "",
    val snapshot: String = ""
)

/** 版本清单本地缓存：timestamp 为写入时毫秒时间戳，1 个 expiry 为 10 分钟 */
data class CachedManifest(
    val timestamp: Long = 0L,
    val manifest: VersionManifest = VersionManifest()
)