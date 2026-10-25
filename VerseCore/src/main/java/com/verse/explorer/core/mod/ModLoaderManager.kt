package com.verse.explorer.core.mod

import com.google.gson.JsonParser
import com.verse.explorer.core.config.GameConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ModLoader(
    val name: String,
    val version: String,
    val type: ModLoaderType,
    val mcVersion: String,
    val url: String? = null,
    val stable: Boolean = true
)

enum class ModLoaderType(val displayName: String, val apiId: String) {
    FORGE("Forge", "forge"),
    NEOFORGE("NeoForge", "neoforge"),
    FABRIC("Fabric", "fabric"),
    QUILT("Quilt", "quilt"),
    OPTIFINE("OptiFine", "optifine"),
    LITELOADER("LiteLoader", "liteloader"),
    CLEANROOM("Cleanroom", "cleanroom")
}

data class ModInfo(
    val name: String,
    val version: String,
    val description: String,
    val authors: List<String>,
    val fileName: String,
    val enabled: Boolean = true
)

class ModLoaderManager {

    private val supportedLoaders = listOf(
        ModLoaderType.FABRIC,
        ModLoaderType.FORGE,
        ModLoaderType.NEOFORGE,
        ModLoaderType.QUILT,
        ModLoaderType.CLEANROOM
    )

    fun getSupportedLoaders(): List<ModLoaderType> = supportedLoaders

    suspend fun fetchAvailableVersions(
        loaderType: ModLoaderType,
        mcVersion: String
    ): List<ModLoader> {
        return when (loaderType) {
            ModLoaderType.FORGE -> fetchForgeVersions(mcVersion)
            ModLoaderType.NEOFORGE -> fetchNeoForgeVersions(mcVersion)
            ModLoaderType.FABRIC -> fetchFabricVersions(mcVersion)
            ModLoaderType.QUILT -> fetchQuiltVersions(mcVersion)
            ModLoaderType.OPTIFINE -> fetchOptiFineVersions(mcVersion)
            ModLoaderType.LITELOADER -> fetchLiteLoaderVersions(mcVersion)
            ModLoaderType.CLEANROOM -> fetchCleanroomVersions(mcVersion)
        }
    }

    private suspend fun fetchFabricVersions(mcVersion: String): List<ModLoader> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://meta.fabricmc.net/v2/versions/loader/${mcVersion}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "VerseExplorerX/1.0")
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val array = JsonParser.parseString(body).asJsonArray
            val result = mutableListOf<ModLoader>()
            for (elem in array) {
                val obj = elem.asJsonObject
                val loader = obj.getAsJsonObject("loader")
                val version = loader.get("version")?.asString ?: continue
                val stable = loader.get("stable")?.asBoolean ?: true
                result.add(ModLoader(
                    name = "Fabric $version",
                    version = version,
                    type = ModLoaderType.FABRIC,
                    mcVersion = mcVersion,
                    url = "https://meta.fabricmc.net/v2/versions/loader/${mcVersion}/${version}/profile/json",
                    stable = stable
                ))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchForgeVersions(mcVersion: String): List<ModLoader> = withContext(Dispatchers.IO) {
        try {
            val mirrorUrl = "https://bmclapi2.bangbang93.com/forge/minecraft/${mcVersion}"
            val conn = URL(mirrorUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "VerseExplorerX/1.0")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val array = JsonParser.parseString(body).asJsonArray
            val result = mutableListOf<ModLoader>()
            for (elem in array) {
                val obj = elem.asJsonObject
                val version = obj.get("version")?.asString ?: continue
                val branch = obj.get("branch")?.asString
                val fullVersion = if (branch != null) "$version-$branch" else version
                result.add(ModLoader(
                    name = "Forge $fullVersion",
                    version = fullVersion,
                    type = ModLoaderType.FORGE,
                    mcVersion = mcVersion,
                    url = obj.get("installer")?.asString,
                    stable = true
                ))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchNeoForgeVersions(mcVersion: String): List<ModLoader> = withContext(Dispatchers.IO) {
        // 与 FCL NeoForgeBMCLVersionList 一致：按 MC 版本查询 /neoforge/list/{mcVersion}。
        // 旧实现拉的是全局 maven 版本列表且不过滤 mcversion，会把别的 MC 版本的
        // NeoForge 版本（甚至 1.20.1 的 forge-* 命名）列出来 → 安装必然版本不匹配/下载失败。
        val mirrors = listOf(
            "https://bmclapi2.bangbang93.com/neoforge/list/$mcVersion",
            "https://bmclapi.nofated.win/neoforge/list/$mcVersion"
        )
        for (urlStr in mirrors) {
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "VerseExplorerX/1.0")

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    continue
                }

                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val array = JsonParser.parseString(body).asJsonArray
                val result = mutableListOf<ModLoader>()
                for (elem in array) {
                    val obj = elem.asJsonObject
                    val entryMc = obj.get("mcversion")?.asString ?: continue
                    if (entryMc != mcVersion) continue
                    val version = obj.get("version")?.asString ?: continue
                    result.add(ModLoader(
                        name = "NeoForge $version",
                        version = version,
                        type = ModLoaderType.NEOFORGE,
                        mcVersion = mcVersion,
                        url = "https://bmclapi2.bangbang93.com/neoforge/version/$version/download/installer.jar",
                        stable = true
                    ))
                }
                if (result.isNotEmpty()) return@withContext result.takeLast(20)
            } catch (_: Exception) {}
        }
        emptyList()
    }

    private suspend fun fetchQuiltVersions(mcVersion: String): List<ModLoader> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://meta.quiltmc.org/v3/versions/loader/${mcVersion}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "VerseExplorerX/1.0")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val array = JsonParser.parseString(body).asJsonArray
            val result = mutableListOf<ModLoader>()
            for (elem in array) {
                val obj = elem.asJsonObject
                val loader = obj.getAsJsonObject("loader")
                val version = loader.get("version")?.asString ?: continue
                result.add(ModLoader(
                    name = "Quilt $version",
                    version = version,
                    type = ModLoaderType.QUILT,
                    mcVersion = mcVersion,
                    url = "https://meta.quiltmc.org/v3/versions/loader/${mcVersion}/${version}/profile/json",
                    stable = true
                ))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchOptiFineVersions(mcVersion: String): List<ModLoader> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://bmclapi2.bangbang93.com/optifine/${mcVersion}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "VerseExplorerX/1.0")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val array = JsonParser.parseString(body).asJsonArray
            val result = mutableListOf<ModLoader>()
            for (elem in array) {
                val obj = elem.asJsonObject
                val type = obj.get("type")?.asString ?: ""
                val patch = obj.get("patch")?.asString ?: ""
                result.add(ModLoader(
                    name = "OptiFine ${type}_${patch}",
                    version = "${type}_${patch}",
                    type = ModLoaderType.OPTIFINE,
                    mcVersion = mcVersion,
                    url = obj.get("filename")?.asString,
                    stable = true
                ))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchLiteLoaderVersions(mcVersion: String): List<ModLoader> {
        return emptyList()
    }

    private suspend fun fetchCleanroomVersions(mcVersion: String): List<ModLoader> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/CleanroomMC/Cleanroom/releases")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "VerseExplorerX/2.0")
            conn.setRequestProperty("Accept", "application/vnd.github+json")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val array = JsonParser.parseString(body).asJsonArray
            val result = mutableListOf<ModLoader>()
            for (elem in array) {
                val obj = elem.asJsonObject
                val tagName = obj.get("tag_name")?.asString ?: continue
                if (!tagName.contains(mcVersion)) continue
                val version = tagName.removePrefix("v")
                result.add(ModLoader(
                    name = "Cleanroom $version",
                    version = version,
                    type = ModLoaderType.CLEANROOM,
                    mcVersion = mcVersion,
                    url = obj.getAsJsonArray("assets")?.firstOrNull {
                        it.asJsonObject.get("name")?.asString?.endsWith("installer.jar") == true
                    }?.asJsonObject?.get("browser_download_url")?.asString,
                    stable = !(obj.get("prerelease")?.asBoolean ?: false)
                ))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getFabricLibraries(mcVersion: String, loaderVersion: String): List<com.google.gson.JsonElement> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://meta.fabricmc.net/v2/versions/loader/${mcVersion}/${loaderVersion}/profile/json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "VerseExplorerX/1.0")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JsonParser.parseString(body).asJsonObject
            json.getAsJsonArray("libraries")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getForgeLibraries(mcVersion: String, loaderVersion: String): List<com.google.gson.JsonElement> = withContext(Dispatchers.IO) {
        try {
            val mirrorUrl = "https://bmclapi2.bangbang93.com/forge/minecraft/${mcVersion}/${loaderVersion}/profile/json"
            val conn = URL(mirrorUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "VerseExplorerX/1.0")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JsonParser.parseString(body).asJsonObject
            json.getAsJsonArray("libraries")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getNeoForgeLibraries(mcVersion: String, loaderVersion: String): List<com.google.gson.JsonElement> = withContext(Dispatchers.IO) {
        try {
            val mirrorUrl = "https://bmclapi2.bangbang93.com/neoforge/${mcVersion}/${loaderVersion}/profile/json"
            val conn = URL(mirrorUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "VerseExplorerX/1.0")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JsonParser.parseString(body).asJsonObject
            json.getAsJsonArray("libraries")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
