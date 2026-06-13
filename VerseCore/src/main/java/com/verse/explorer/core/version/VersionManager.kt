package com.verse.explorer.core.version

import android.util.Log
import com.verse.explorer.core.config.GameConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class VersionManager(private val gameDir: File) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .serializeNulls()
        .create()

    suspend fun fetchRemoteVersions(): VersionManifest = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (mirrorIndex in GameConfig.MOJANG_MIRRORS.indices) {
            val mirror = GameConfig.MOJANG_MIRRORS[mirrorIndex]
            try {
                val url = GameConfig.getVersionManifestUrl(mirrorIndex)
                Log.d("VersionManager", "Fetching version manifest from: $url (${mirror.name})")

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "VerseExplorerX/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("VersionManager", "HTTP ${response.code} from ${mirror.name}: $url")
                        lastException = Exception("HTTP ${response.code}: ${mirror.name}")
                        return@use
                    }

                    val body = response.body?.string()
                    if (body.isNullOrBlank()) {
                        Log.w("VersionManager", "Empty response from ${mirror.name}")
                        lastException = Exception("Empty response: ${mirror.name}")
                        return@use
                    }

                    if (!body.trimStart().startsWith("{")) {
                        Log.w("VersionManager", "Invalid JSON from ${mirror.name}: ${body.take(100)}")
                        lastException = Exception("Invalid JSON: ${mirror.name}")
                        return@use
                    }

                    Log.d("VersionManager", "Got response from ${mirror.name}, length: ${body.length}")

                    val manifest = gson.fromJson(body, VersionManifest::class.java)
                    Log.d("VersionManager", "Parsed ${manifest.versions.size} versions from ${mirror.name}")
                    Log.d("VersionManager", "Latest release: ${manifest.latest.release}, snapshot: ${manifest.latest.snapshot}")

                    return@withContext manifest
                }
            } catch (e: Exception) {
                Log.w("VersionManager", "Error from ${mirror.name}: ${e.message}", e)
                lastException = e
                continue
            }
        }

        throw lastException ?: Exception("Failed to fetch version manifest from all mirrors")
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
                version
            } catch (e: Exception) {
                Log.w("VersionManager", "Failed to parse ${jsonFile.name}: ${e.message}")
                null
            }
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