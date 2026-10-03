package com.verse.explorer.core.version

import android.util.Log
import com.verse.explorer.core.config.GameConfig
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .serializeNulls()
        .create()

    /**
     * PC 端对齐的版本清单加载：
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
            val jsonFile = File(versionDir, "${versionDir.name}.json")
            if (!jsonFile.exists()) {
                Log.d("VersionManager", "No JSON file for ${versionDir.name}, skipping")
                return@mapNotNull null
            }

            // PC端风格: 检查JAR - 可能在当前目录或父版本目录中
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
     * 与 FCL GameRepository.versionIdConflicts 对应：
     * 命名冲突 = versions 目录下已存在同名版本目录。
     */
    fun versionIdConflicts(id: String): Boolean {
        val versionsDir = GameConfig.getVersionsDir(gameDir)
        if (!versionsDir.exists() || !versionsDir.isDirectory) return false
        return versionsDir.listFiles { f -> f.isDirectory }?.any { it.name == id } == true
    }

    /**
     * 从版本 json 原文识别加载器（覆盖库、inheritsFrom 与 patches 三处来源）。
     * 顺序注意：neoforge/optifine/quilt 需先于 forge，因为 neoforge 库名含 "forge" 子串。
     */
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