package com.verse.explorer.core.resource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 连续 3 次失败熔断 60 秒，熔断期间跳过镜像源，避免镜像异常拖慢列表加载。
 */
internal object MirrorCircuitBreaker {
    private val fails = AtomicInteger(0)
    private val downUntil = AtomicLong(0L)

    fun isAvailable(): Boolean {
        val until = downUntil.get()
        if (until != 0L) {
            if (System.currentTimeMillis() >= until) {
                fails.set(0)
                downUntil.set(0L)
                return true
            }
            return false
        }
        return true
    }

    fun mirrorFailed() {
        if (fails.incrementAndGet() >= 3) {
            downUntil.set(System.currentTimeMillis() + 60_000)
        }
    }

    fun mirrorSuccess() {
        fails.set(0)
        downUntil.set(0L)
    }
}

/**
 * URL → body，超 2000 项清理过期项，命中缓存直接返回避免重复请求。
 */
internal object ApiUrlCache {
    private class Entry(val body: String, val ts: Long)
    private val map = ConcurrentHashMap<String, Entry>()

    fun get(url: String, ttlMs: Long = 60_000): String? {
        val e = map[url] ?: return null
        if (System.currentTimeMillis() - e.ts > ttlMs) {
            map.remove(url)
            return null
        }
        return e.body
    }

    fun put(url: String, body: String, ttlMs: Long = 60_000) {
        if (map.size > 2000) {
            val now = System.currentTimeMillis()
            map.entries.removeIf { now - it.value.ts > ttlMs * 2 }
        }
        map[url] = Entry(body, System.currentTimeMillis())
    }
}

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
    // Modrinth 官方 API 国内 TTFB 慢，镜像 mod.mcimirror.top 优先）
    private const val MODRINTH_API = "https://api.modrinth.com/v2"
    private const val MODRINTH_API_MIRROR = "https://mod.mcimirror.top/modrinth/v2"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = GsonBuilder()
        .serializeNulls()
        .create()

    /**
     *   - 镜像可用时：镜像(8s) → 官方(10s) → 官方(20s)
     *   - 镜像不可用（熔断）时：官方(10s) → 官方(20s)
     *   - 镜像 403/404 不阻断，继续官方；官方 403/404 直接失败
     *   - 镜像连续失败 3 次熔断 60s，期间只走官方，避免镜像异常拖慢列表
     */
    private suspend fun fetchWithMirror(path: String, useMirror: Boolean, cacheTtlMs: Long? = 60_000): String =
        withContext(Dispatchers.IO) {
            val officialUrl = "$MODRINTH_API$path"
            val mirrorUrl = officialUrl.replace(MODRINTH_API, MODRINTH_API_MIRROR)

            cacheTtlMs?.let {
                ApiUrlCache.get(officialUrl, it)?.let { cached -> return@withContext cached }
            }

            val steps = if (useMirror && MirrorCircuitBreaker.isAvailable()) {
                listOf(Pair(mirrorUrl, 8_000L), Pair(officialUrl, 10_000L), Pair(officialUrl, 20_000L))
            } else {
                listOf(Pair(officialUrl, 10_000L), Pair(officialUrl, 20_000L))
            }

            var lastError: Exception? = null
            for ((stepUrl, timeoutMs) in steps) {
                val isMirror = stepUrl.startsWith(MODRINTH_API_MIRROR)
                try {
                    val httpClient = client.newBuilder()
                        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .build()
                    val request = Request.Builder().url(stepUrl)
                        .header("User-Agent", "VerseExplorerX/1.0")
                        .header("Accept", "application/json")
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: throw Exception("Empty response")
                        if (response.isSuccessful) {
                            if (isMirror) MirrorCircuitBreaker.mirrorSuccess()
                            cacheTtlMs?.let { ApiUrlCache.put(officialUrl, body, it) }
                            return@withContext body
                        }
                        // 镜像 403/404 不阻断，继续官方；官方 403/404 直接失败
                        if (response.code == 403 || response.code == 404) {
                            if (!isMirror) throw Exception("HTTP ${response.code} for $stepUrl")
                        } else if (isMirror) {
                            MirrorCircuitBreaker.mirrorFailed()
                        }
                        lastError = Exception("HTTP ${response.code} for $stepUrl")
                    }
                } catch (e: Exception) {
                    if (isMirror) MirrorCircuitBreaker.mirrorFailed()
                    lastError = e
                }
            }
            throw lastError ?: Exception("Failed to fetch from all sources")
        }

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
        // facets 构造与 PCL 的 GetModrinthAddress 一致：
        //   [["project_type:mod"],["categories:'fabric'"],["versions:'1.20.1'"]]
        // 值统一用单引号包裹（Modrinth 允许两种写法，PCL 用单引号，含空格的分类名更稳）
        val facets = mutableListOf<List<String>>()
        facets.add(listOf("project_type:$projectType"))

        if (categories.isNotEmpty()) {
            facets.add(categories.map { "categories:'$it'" })
        }
        if (gameVersions.isNotEmpty()) {
            facets.add(gameVersions.map { "versions:'$it'" })
        }
        if (loaders.isNotEmpty()) {
            facets.add(loaders.map { "categories:'$it'" })
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

        val body = fetchWithMirror("/search?${params.joinToString("&")}", useMirror, 60_000)
        gson.fromJson(body, ModrinthSearchResult::class.java)
    }

    suspend fun getProject(projectId: String, useMirror: Boolean = true): ModrinthProject = withContext(Dispatchers.IO) {
        val body = fetchWithMirror("/project/$projectId", useMirror, null)
        gson.fromJson(body, ModrinthProject::class.java)
    }

    suspend fun getProjectVersions(projectId: String, useMirror: Boolean = true): List<ModrinthVersion> = withContext(Dispatchers.IO) {
        val body = fetchWithMirror("/project/$projectId/version", useMirror, null)
        val listType = object : com.google.gson.reflect.TypeToken<List<ModrinthVersion>>() {}.type
        // gson.fromJson 对空串/"null" 字面量返回 null（已本地复现），
        // 若放行到调用侧会变成"对 null 调 map"的 NPE（message=null），表现为版本列表静默为空。
        // 这里显式转成带信息的异常，方便日志/UI 定位网络层问题。
        val parsed: List<ModrinthVersion> = gson.fromJson(body, listType)
            ?: throw Exception("Modrinth 版本接口返回空 body (len=${body.length}, projectId=$projectId)")
        parsed
    }

    suspend fun getTagCategories(useMirror: Boolean = true): List<ModrinthCategory> = withContext(Dispatchers.IO) {
        val body = fetchWithMirror("/tag/category", useMirror, 60_000)
        val listType = object : com.google.gson.reflect.TypeToken<List<ModrinthCategory>>() {}.type
        gson.fromJson(body, listType)
    }
}