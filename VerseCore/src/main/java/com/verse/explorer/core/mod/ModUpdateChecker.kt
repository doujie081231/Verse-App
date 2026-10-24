package com.verse.explorer.core.mod

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 模组更新检查（按本地 jar 的 SHA-1 反查远端版本）。
 *
 * 只用 Modrinth 官方 API：
 * 1. `POST /v2/version_files` 用 SHA-1 批量反查「这个文件属于哪个项目、哪个版本」；
 * 2. `GET /v2/project/{id}/version` 取该项目在当前 MC 版本 + 加载器下的最新版本。
 *
 * **CurseForge 不支持**：它需要 murmur2 指纹（排除 class 后的规范化哈希），
 * 实现成本高且易误判，这里明确不做——UI 上会说明只有 Modrinth 来源的模组能检查。
 */
object ModUpdateChecker {

    private const val BASE = "https://api.modrinth.com/v2"
    private const val UA = "VerseExplorerX/0.1 (mod-update-check)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = com.google.gson.Gson()

    data class ModUpdate(
        val modId: String,
        val name: String,
        val currentVersion: String,
        val newVersion: String,
        val projectId: String,
        val downloadUrl: String,
        val fileName: String,
        /** 当前本地 jar 的文件名，更新时用于删除旧文件 */
        val currentFile: String
    )

    data class CheckResult(
        val updates: List<ModUpdate>,
        /** Modrinth 上查不到的项目（多为 CurseForge 专属或手动放入的 jar） */
        val unknownCount: Int
    )

    fun sha1Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * @param mods 本地模组（`sha1` 为空时会现场计算）
     * @param mcVersion 基础 MC 版本，如 `1.20.1`
     * @param loader Fabric/Forge 等的小写标识；用于筛选兼容版本
     */
    suspend fun checkUpdates(
        mods: List<ModConflictScanner.LocalMod>,
        mcVersion: String,
        loader: String
    ): Result<CheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (mods.isEmpty()) return@runCatching CheckResult(emptyList(), 0)

            val sha1Map = mods.associateBy { it.sha1.ifBlank { sha1Of(it.file) } }
            val body = gson.toJson(mapOf("hashes" to sha1Map.keys.toList(), "algorithm" to "sha1"))

            val matched = postJson("$BASE/version_files", body)
                ?: error("Modrinth 无响应，请检查网络")

            val versionByHash = mutableMapOf<String, Pair<String, String>>() // sha1 -> (projectId, versionNumber)
            matched.entrySet().forEach { (hash, element) ->
                if (!element.isJsonObject) return@forEach
                val obj = element.asJsonObject
                val projectId = obj.get("project_id")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                val versionNumber = obj.get("version_number")?.takeIf { !it.isJsonNull }?.asString ?: ""
                versionByHash[hash.lowercase()] = projectId to versionNumber
            }

            val updates = mutableListOf<ModUpdate>()
            var unknown = 0

            mods.forEach { mod ->
                val hash = mod.sha1.ifBlank { sha1Of(mod.file) }.lowercase()
                val hit = versionByHash[hash]
                if (hit == null) {
                    unknown++
                    return@forEach
                }
                val (projectId, currentVersion) = hit
                val latest = fetchLatestVersion(projectId, mcVersion, loader) ?: run {
                    unknown++
                    return@forEach
                }
                val (newVersion, url, fileName) = latest
                if (newVersion.isBlank() || newVersion == currentVersion) return@forEach
                updates += ModUpdate(
                    modId = mod.modId,
                    name = mod.name,
                    currentVersion = currentVersion.ifBlank { mod.version },
                    newVersion = newVersion,
                    projectId = projectId,
                    downloadUrl = url,
                    fileName = fileName,
                    currentFile = mod.file.name
                )
            }

            CheckResult(updates = updates.sortedBy { it.name.lowercase() }, unknownCount = unknown)
        }
    }

    /** 取该项目在指定 MC 版本 + 加载器下最新的版本（Modrinth 版本列表按发布时间倒序） */
    private fun fetchLatestVersion(projectId: String, mcVersion: String, loader: String): Triple<String, String, String>? {
        val url = buildString {
            append("$BASE/project/").append(projectId).append("/version")
            val query = mutableListOf<String>()
            if (mcVersion.isNotBlank()) query += "game_versions=%5B%22${mcVersion}%22%5D"
            if (loader.isNotBlank()) query += "loaders=%5B%22${loader}%22%5D"
            if (query.isNotEmpty()) append("?").append(query.joinToString("&"))
        }
        val text = getText(url) ?: return null
        val array = runCatching { JsonParser.parseString(text).asJsonArray }.getOrNull() ?: return null
        if (array.size() == 0) return null

        val version = array[0].asJsonObject
        val versionNumber = version.get("version_number")?.takeIf { !it.isJsonNull }?.asString ?: return null
        val files = version.getAsJsonArray("files") ?: return null
        val primary = files.firstOrNull { it.asJsonObject.get("primary")?.asBoolean == true }
            ?: files.firstOrNull()
            ?: return null
        val fileObj = primary.asJsonObject
        val downloadUrl = fileObj.get("url")?.takeIf { !it.isJsonNull }?.asString ?: return null
        val fileName = fileObj.get("filename")?.takeIf { !it.isJsonNull }?.asString ?: "$projectId.jar"
        return Triple(versionNumber, downloadUrl, fileName)
    }

    private fun getText(url: String): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", UA).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()

    private fun postJson(url: String, body: String): com.google.gson.JsonObject? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string() ?: return null
            JsonParser.parseString(text).asJsonObject
        }
    }.getOrNull()
}
