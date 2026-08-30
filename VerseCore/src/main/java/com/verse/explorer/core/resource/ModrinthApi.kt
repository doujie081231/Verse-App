package com.verse.explorer.core.resource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ModrinthSearchResult(
    val offset: Int,
    val limit: Int,
    @SerializedName("total_hits")
    val totalHits: Int,
    val hits: List<ModrinthSearchHit>
)

data class ModrinthSearchHit(
    @SerializedName("project_id")
    val projectId: String = "",
    @SerializedName("project_type")
    val projectType: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val categories: List<String> = emptyList(),
    @SerializedName("display_categories")
    val displayCategories: List<String> = emptyList(),
    val downloads: Long = 0,
    @SerializedName("follows")
    val followers: Long = 0,
    @SerializedName("icon_url")
    var iconUrl: String? = null,
    @SerializedName("author")
    val author: String = "",
    val versions: List<String> = emptyList(),
    @SerializedName("latest_version")
    val latestVersion: String? = null,
    @SerializedName("date_modified")
    val dateModified: String = "",
    @SerializedName("game_versions")
    val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    /** 数据来源："modrinth" / "curseforge" */
    val source: String = "modrinth"
)

data class ModrinthProject(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val categories: List<String> = emptyList(),
    @SerializedName("client_side")
    val clientSide: String = "unknown",
    @SerializedName("server_side")
    val serverSide: String = "unknown",
    val downloads: Long = 0,
    val followers: Long = 0,
    @SerializedName("icon_url")
    var iconUrl: String? = null,
    val author: String = "",
    val versions: List<String> = emptyList(),
    @SerializedName("game_versions")
    val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    @SerializedName("body")
    val body: String = "",
    @SerializedName("body_url")
    val bodyUrl: String? = null,
    @SerializedName("date_created")
    val dateCreated: String = "",
    @SerializedName("date_modified")
    val dateModified: String = "",
    val status: String = "",
    val license: ModrinthLicense? = null,
    val team: String = "",
    // 以下链接字段 Modrinth 可能不返回（如 Indium 无 wiki/source 链接），Gson 会塞 null，
    // 必须声明为可空并在映射层兜底，否则 Kotlin 非空校验抛 "Parameter specified as non-null is null"
    @SerializedName("source_url")
    val sourceUrl: String? = null,
    @SerializedName("issues_url")
    val issuesUrl: String? = null,
    @SerializedName("wiki_url")
    val wikiUrl: String? = null,
    @SerializedName("discord_url")
    val discordUrl: String? = null,
    val gallery: List<ModrinthGallery>? = null
)

/** Modrinth 画廊项（project.gallery：url + title） */
data class ModrinthGallery(
    val url: String = "",
    val title: String = ""
)

data class ModrinthLicense(
    val id: String = "",
    val name: String = "",
    val url: String? = null
)

data class ModrinthVersion(
    val id: String? = null,
    @SerializedName("project_id")
    val projectId: String? = null,
    val name: String? = null,
    @SerializedName("version_number")
    val versionNumber: String? = null,
    val changelog: String? = null,
    @SerializedName("changelog_url")
    val changelogUrl: String? = null,
    @SerializedName("date_published")
    val datePublished: String? = null,
    val downloads: Int = 0,
    @SerializedName("version_type")
    val versionType: String? = null,
    // Gson 可注入 null（绕过 Kotlin 默认值），集合字段必须可空并在映射层 orEmpty()
    val files: List<ModrinthFile>? = null,
    val dependencies: List<ModrinthDependency>? = null,
    @SerializedName("game_versions")
    val gameVersions: List<String>? = null,
    val loaders: List<String>? = null,
    val featured: Boolean = false,
    val status: String? = null
)

data class ModrinthDependency(
    @SerializedName("version_id")
    val versionId: String? = null,
    @SerializedName("project_id")
    val projectId: String? = null,
    @SerializedName("file_name")
    val fileName: String? = null,
    @SerializedName("dependency_type")
    val dependencyType: String? = null
)

data class ModrinthFile(
    val hashes: Map<String, String>? = null,
    val url: String? = null,
    val filename: String? = null,
    val primary: Boolean = false,
    val size: Long = 0,
    @SerializedName("file_type")
    val fileType: String? = null
)

data class ModrinthCategory(
    val icon: String,
    val name: String,
    @SerializedName("project_type")
    val projectType: String,
    val header: String
)

object ModrinthApi {
    // BMCLAPI 不支持 modrinth API 镜像（返回 404），直接使用官方 API
    private const val MODRINTH_API = "https://api.modrinth.com/v2"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = GsonBuilder()
        .serializeNulls()
        .create()

    private fun getApiBaseUrl(useMirror: Boolean): String = MODRINTH_API

    suspend fun searchProjects(
        query: String = "",
        projectType: String = "mod",
        categories: List<String> = emptyList(),
        gameVersions: List<String> = emptyList(),
        loaders: List<String> = emptyList(),
        sort: String = "relevance",
        limit: Int = 20,
        offset: Int = 0,
        useMirror: Boolean = true
    ): ModrinthSearchResult = withContext(Dispatchers.IO) {
        val facets = mutableListOf<List<String>>()
        facets.add(listOf("project_type:$projectType"))

        if (categories.isNotEmpty()) {
            facets.add(categories.map { "categories:$it" })
        }
        if (gameVersions.isNotEmpty()) {
            facets.add(gameVersions.map { "versions:$it" })
        }
        if (loaders.isNotEmpty()) {
            facets.add(loaders.map { "categories:$it" })
        }

        val params = mutableListOf<String>()
        if (query.isNotEmpty()) {
            params.add("query=${java.net.URLEncoder.encode(query, "UTF-8")}")
        }
        params.add("limit=$limit")
        params.add("offset=$offset")
        if (sort.isNotEmpty()) {
            params.add("index=$sort")
        }
        if (facets.isNotEmpty()) {
            val facetsJson = gson.toJson(facets)
            params.add("facets=${java.net.URLEncoder.encode(facetsJson, "UTF-8")}")
        }

        var lastException: Exception? = null
        for (tryMirror in listOf(useMirror, false)) {
            try {
                val url = "${getApiBaseUrl(tryMirror)}/search?${params.joinToString("&")}"
                val request = Request.Builder().url(url)
                    .header("User-Agent", "VerseExplorerX/1.0")
                    .header("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                try {
                    val body = response.body?.string() ?: throw Exception("Empty response")
                    if (response.isSuccessful) {
                        val result = gson.fromJson(body, ModrinthSearchResult::class.java)
                        // 替换 cdn.modrinth.com 为图片代理（国内可能无法直接访问）
                        result.hits.forEach { hit ->
                            hit.iconUrl?.let { url ->
                                if (url.contains("cdn.modrinth.com")) {
                                    val proxyUrl = url
                                        .removePrefix("https://")
                                        .removePrefix("http://")
                                    hit.iconUrl = "https://images.weserv.nl/?url=$proxyUrl"
                                }
                            }
                        }
                        return@withContext result
                    }
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        throw lastException ?: Exception("Failed to fetch from all sources")
    }

    suspend fun getProject(projectId: String, useMirror: Boolean = true): ModrinthProject = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (tryMirror in listOf(useMirror, false)) {
            try {
                val url = "${getApiBaseUrl(tryMirror)}/project/$projectId"
                val request = Request.Builder().url(url)
                    .header("User-Agent", "VerseExplorerX/1.0")
                    .header("Accept", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                try {
                    val body = response.body?.string() ?: throw Exception("Empty response")
                    if (response.isSuccessful) {
                        val project = gson.fromJson(body, ModrinthProject::class.java)
                        project.iconUrl?.let { url ->
                            if (url.contains("cdn.modrinth.com")) {
                                val proxyUrl = url
                                    .removePrefix("https://")
                                    .removePrefix("http://")
                                project.iconUrl = "https://images.weserv.nl/?url=$proxyUrl"
                            }
                        }
                        return@withContext project
                    }
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        throw lastException ?: Exception("Failed to fetch project from all sources")
    }

    suspend fun getProjectVersions(projectId: String, useMirror: Boolean = true): List<ModrinthVersion> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (tryMirror in listOf(useMirror, false)) {
            try {
                val url = "${getApiBaseUrl(tryMirror)}/project/$projectId/version"
                val request = Request.Builder().url(url)
                    .header("User-Agent", "VerseExplorerX/1.0")
                    .header("Accept", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                try {
                    val body = response.body?.string() ?: throw Exception("Empty response body for $url")
                    if (response.isSuccessful) {
                        val listType = object : com.google.gson.reflect.TypeToken<List<ModrinthVersion>>() {}.type
                        // gson.fromJson 对空串/"null" 字面量返回 null（已本地复现），
                        // 若放行到调用侧会变成"对 null 调 map"的 NPE（message=null），表现为版本列表静默为空。
                        // 这里显式转成带信息的异常，方便日志/UI 定位网络层问题。
                        val parsed: List<ModrinthVersion> = gson.fromJson(body, listType)
                            ?: throw Exception("Modrinth 版本接口返回空 body (len=${body.length}, url=$url)")
                        return@withContext parsed
                    } else {
                        throw Exception("HTTP ${response.code} for $url")
                    }
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        throw lastException ?: Exception("Failed to fetch versions from all sources")
    }

    suspend fun getTagCategories(useMirror: Boolean = true): List<ModrinthCategory> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (tryMirror in listOf(useMirror, false)) {
            try {
                val url = "${getApiBaseUrl(tryMirror)}/tag/category"
                val request = Request.Builder().url(url)
                    .header("User-Agent", "VerseExplorerX/1.0")
                    .header("Accept", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                try {
                    val body = response.body?.string() ?: throw Exception("Empty response")
                    if (response.isSuccessful) {
                        val listType = object : com.google.gson.reflect.TypeToken<List<ModrinthCategory>>() {}.type
                        return@withContext gson.fromJson(body, listType)
                    }
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        throw lastException ?: Exception("Failed to fetch categories from all sources")
    }
}