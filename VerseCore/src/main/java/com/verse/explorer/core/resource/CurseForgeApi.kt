package com.verse.explorer.core.resource

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * CurseForge API 封装。
 *
 *  - 搜索：`GET /mods/search`
 *  - 详情：`GET /mods/{modId}`
 *  - 版本文件：`GET /mods/{modId}/files`
 *  - 单文件：`GET /mods/{modId}/files/{fileId}`
 *
 * CurseForge API 需要 x-api-key 头，官方分类 classId：
 *  - 6       = mod（模组）
 *  - 12      = resourcepack（资源包）
 *  - 4471    = modpack（整合包）
 *  - 6552    = shader（光影）
 *  - 3743    = datapack（数据包）
 */
object CurseForgeApi {
    // CurseForge 官方 API 国内 TTFB 慢，镜像 mod.mcimirror.top 优先）
    private const val CURSEFORGE_API = "https://api.curseforge.com/v1"
    private const val CURSEFORGE_API_MIRROR = "https://mod.mcimirror.top/curseforge/v1"

    private const val DEFAULT_API_KEY = "\$2a\$10\$bL4bIL5pUWqfcO7KQtnMReakwtfHbNKh6v1uTpKlzhwoueEJQnPnm"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** 资源类型 -> CurseForge classId */
    fun classIdForType(projectType: String): Int? = when (projectType) {
        "mod" -> 6
        "resourcepack" -> 12
        "modpack" -> 4471
        "shader" -> 6552
        // 与 PCL 一致：数据包 6945（旧值 3743 是错的，会返回空列表）、世界 17
        "datapack" -> 6945
        "world" -> 17
        else -> null
    }

    /** 排序 -> CurseForge sortField（与 PCL 的 CompSortType 映射一致） */
    private fun sortFieldFor(sort: String): Int = when (sort.lowercase()) {
        "downloads" -> 6
        "follows" -> 2
        "newest" -> 11
        "updated" -> 3
        else -> 4
    }

    /** 加载器名 -> CurseForge modLoaderType id */
    private fun loaderToId(loader: String): Int? = when (loader.lowercase()) {
        "forge" -> 1
        "fabric" -> 4
        "quilt" -> 5
        "neoforge" -> 6
        else -> null
    }

    private fun safeString(value: String?, fallback: String = ""): String = value ?: fallback

    private fun buildHeaders(): Map<String, String> = mapOf(
        "x-api-key" to DEFAULT_API_KEY,
        "Accept" to "application/json",
        "User-Agent" to "VerseExplorerX/1.0"
    )

    /**
     *   - 镜像可用时：镜像(8s) → 官方(10s) → 官方(20s)
     *   - 镜像不可用（熔断）时：官方(10s) → 官方(20s)
     *   - 镜像 403/404 不阻断，继续官方；官方 403/404 直接失败
     *   - 镜像连续失败 3 次熔断 60s（MirrorCircuitBreaker 为 Modrinth/CurseForge 共享）
     */
    private suspend fun executeWithMirror(path: String, cacheTtlMs: Long? = 60_000): String =
        withContext(Dispatchers.IO) {
            val officialUrl = "$CURSEFORGE_API$path"
            val mirrorUrl = officialUrl.replace(CURSEFORGE_API, CURSEFORGE_API_MIRROR)

            cacheTtlMs?.let {
                ApiUrlCache.get(officialUrl, it)?.let { cached -> return@withContext cached }
            }

            val steps = if (MirrorCircuitBreaker.isAvailable()) {
                listOf(Pair(mirrorUrl, 8_000L), Pair(officialUrl, 10_000L), Pair(officialUrl, 20_000L))
            } else {
                listOf(Pair(officialUrl, 10_000L), Pair(officialUrl, 20_000L))
            }

            var lastError: Exception? = null
            for ((stepUrl, timeoutMs) in steps) {
                val isMirror = stepUrl.startsWith(CURSEFORGE_API_MIRROR)
                try {
                    val httpClient = client.newBuilder()
                        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .build()
                    val builder = Request.Builder().url(stepUrl)
                    buildHeaders().forEach { (k, v) -> builder.header(k, v) }
                    httpClient.newCall(builder.build()).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            if (isMirror) MirrorCircuitBreaker.mirrorSuccess()
                            cacheTtlMs?.let { ApiUrlCache.put(officialUrl, body, it) }
                            return@withContext body
                        }
                        // 镜像 403/404 不阻断，继续官方；官方 403/404 直接失败
                        if (response.code == 403 || response.code == 404) {
                            if (!isMirror) throw Exception("CurseForge HTTP ${response.code}: ${body.take(200)}")
                        } else if (isMirror) {
                            MirrorCircuitBreaker.mirrorFailed()
                        }
                        lastError = Exception("CurseForge HTTP ${response.code}: ${body.take(200)}")
                    }
                } catch (e: Exception) {
                    if (isMirror) MirrorCircuitBreaker.mirrorFailed()
                    lastError = e
                }
            }
            throw lastError ?: Exception("CurseForge 请求失败: $path")
        }

    /**
     * 搜索 CurseForge 资源。
     * 仅当 projectType 有对应 classId 时才有意义（mod/modpack/resourcepack/shader/datapack）。
     */
    suspend fun search(
        query: String = "",
        projectType: String = "mod",
        loader: String = "",
        gameVersion: String = "",
        sort: String = "downloads",
        limit: Int = 20,
        offset: Int = 0
    ): ResourceSearchResult = withContext(Dispatchers.IO) {
        val classId = classIdForType(projectType) ?: return@withContext ResourceSearchResult()

        // 请求参数与 PCL 的 GetCurseForgeAddress 完全一致：
        //   gameId=432&sortOrder=desc&pageSize=N&classId=X[&categoryId][&modLoaderType][&gameVersion]
        //   [&searchFilter][&index][&sortField]
        val sb = StringBuilder("/mods/search?gameId=432&sortOrder=desc&pageSize=$limit")
        sb.append("&classId=$classId")
        if (query.isNotBlank()) {
            sb.append("&searchFilter=${java.net.URLEncoder.encode(query, "UTF-8")}")
        }
        if (offset > 0) sb.append("&index=$offset")
        // PCL 的 CompSortType → sortField：相关度 4、下载量 6、关注 2、最新 11、最近更新 3
        sb.append("&sortField=").append(sortFieldFor(sort))
        loaderToId(loader)?.let { sb.append("&modLoaderType=$it") }
        if (gameVersion.isNotBlank()) {
            sb.append("&gameVersion=${java.net.URLEncoder.encode(gameVersion, "UTF-8")}")
        }

        val body = executeWithMirror(sb.toString(), 60_000)

        val resp = gson.fromJson(body, CfSearchResponse::class.java)
        ResourceSearchResult(
            hits = (resp.data ?: emptyList()).map { mod -> mapHit(mod, projectType) },
            total = resp.pagination?.totalCount?.toInt() ?: (resp.data ?: emptyList()).size
        )
    }

    private fun mapHit(mod: CfMod, projectType: String): ModrinthSearchHit {
        return ModrinthSearchHit(
            projectId = mod.id.toString(),
            slug = safeString(mod.slug),
            title = if (mod.name.isNullOrBlank()) "Unknown" else mod.name,
            description = safeString(mod.summary),
            categories = (mod.categories ?: emptyList()).mapNotNull { it.name ?: it.classId?.toString() },
            downloads = mod.downloadCount ?: 0,
            followers = mod.followers ?: 0,
            iconUrl = mod.logo?.url,
            author = ((mod.authors ?: emptyList()).firstOrNull()?.name) ?: "Unknown",
            versions = (mod.latestFilesIndexes ?: emptyList()).mapNotNull { it.gameVersion },
            gameVersions = (mod.latestFilesIndexes ?: emptyList())
                .mapNotNull { it.gameVersion }
                .map { it.trim().lowercase() }
                .filter { McVersionGroups.isFormatFit(it) }
                .distinct(),
            loaders = (mod.latestFilesIndexes ?: emptyList()).mapNotNull { it.modLoader.let { l -> modLoaderLabel(l) } }.distinct(),
            source = "curseforge",
            projectType = projectType
        )
    }

    private fun modLoaderLabel(loader: Int?): String? = when (loader) {
        1 -> "forge"
        4 -> "fabric"
        5 -> "quilt"
        6 -> "neoforge"
        else -> null
    }

    /** 获取项目详情（映射为统一详情模型） */
    suspend fun getProjectDetail(modId: String): ResourceDetail = withContext(Dispatchers.IO) {
        val body = executeWithMirror("/mods/$modId", null)
        val resp = gson.fromJson(body, CfSingleResponse::class.java)
        val mod = resp.data ?: throw Exception("CurseForge 项目不存在")
        ResourceDetail(
            id = mod.id.toString(),
            slug = safeString(mod.slug),
            title = if (mod.name.isNullOrBlank()) "Unknown" else mod.name,
            description = safeString(mod.summary),
            body = safeString(mod.summary),
            iconUrl = mod.logo?.url,
            downloads = mod.downloadCount ?: 0,
            followers = mod.followers ?: 0,
            categories = (mod.categories ?: emptyList()).mapNotNull { it.name },
            loaders = (mod.latestFilesIndexes ?: emptyList()).mapNotNull { it.modLoader.let { l -> modLoaderLabel(l) } }.distinct(),
            gameVersions = (mod.latestFilesIndexes ?: emptyList())
                .mapNotNull { it.gameVersion }
                .map { it.trim().lowercase() }
                .filter { McVersionGroups.isFormatFit(it) }
                .distinct(),
            license = "",
            sourceUrl = "",
            dateCreated = mod.dateCreated ?: "",
            dateModified = mod.dateModified ?: "",
            gallery = (mod.screenshots ?: emptyList()).mapNotNull { shot ->
                val url = shot.url ?: shot.thumbnailUrl ?: return@mapNotNull null
                ResourceGallery(url, shot.title ?: shot.description ?: "")
            },
            source = "curseforge",
            projectType = ""
        )
    }

    /** 获取项目版本文件列表 */
    suspend fun getProjectFiles(
        modId: String,
        loader: String = "",
        gameVersion: String = ""
    ): List<ResourceVersion> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("/mods/$modId/files?pageSize=100")
        loaderToId(loader)?.let { sb.append("&modLoaderType=$it") }
        if (gameVersion.isNotBlank()) sb.append("&gameVersion=${java.net.URLEncoder.encode(gameVersion, "UTF-8")}")

        val body = executeWithMirror(sb.toString(), null)
        val resp = gson.fromJson(body, CfFilesResponse::class.java)
        (resp.data ?: emptyList()).map { file ->
            val sha1 = (file.hashes ?: emptyList()).firstOrNull { it.algo == 1 }?.value ?: ""
            val url = resolveDownloadUrl(file, modId)
            ResourceVersion(
                id = file.id.toString(),
                name = safeString(file.displayName, safeString(file.fileName)),
                versionNumber = safeString(file.fileName?.substringBeforeLast('.')),
                datePublished = file.fileDate ?: "",
                // PCL 的做法：保留原始 gameVersions（其中混着 forge/fabric 这类加载器名），
                // 只用 IsFormatFit 过滤出真正的版本号。旧实现用 `\d+\.\d+` 全匹配，
                // 把 1.20.1 / 1.21.4 这些三段式版本整段删掉了 → 详情页版本分类缺项。
                // 一个可识别版本都没有时给「未知版本」兜底（PCL 同样处理）：
                // 否则这个文件在任何版本标签下都取不到，只能靠「全部」翻到
                gameVersions = (file.gameVersions ?: emptyList())
                    .map { it.trim().lowercase() }
                    .filter { McVersionGroups.isFormatFit(it) }
                    .distinct()
                    .ifEmpty { listOf(McVersionGroups.UNKNOWN) },
                loaders = (file.gameVersions ?: emptyList())
                    .mapNotNull { modLoaderLabelFromName(it) }
                    .distinct(),
                releaseType = file.releaseType?.let { if (it == 1) "release" else "beta" } ?: "release",
                downloads = 0L,
                primaryFile = ResourceFile(
                    url = url,
                    filename = safeString(file.fileName),
                    size = file.fileLength ?: 0,
                    primary = (file.isAlternate ?: false) == false,
                    sha1 = sha1
                ),
                files = listOf(
                    ResourceFile(
                        url = url,
                        filename = safeString(file.fileName),
                        size = file.fileLength ?: 0,
                        primary = (file.isAlternate ?: false) == false,
                        sha1 = sha1
                    )
                ),
                // CurseForge 文件自带 dependencies（modId + relationType），映射为统一依赖模型
                dependencies = (file.dependencies ?: emptyList()).mapNotNull { dep ->
                    val mid = dep.modId ?: return@mapNotNull null
                    ResourceDependency(
                        projectId = mid.toString(),
                        type = cfRelationType(dep.relationType),
                        source = "curseforge"
                    )
                },
                source = "curseforge"
            )
        }
    }

    /**
     * 1=内嵌 2=可选 3=必需 4=工具 5=不兼容 6=包含
     */
    private fun cfRelationType(relationType: Int?): String = when (relationType) {
        1 -> "embedded"
        2 -> "optional"
        3 -> "required"
        4 -> "tool"
        5 -> "incompatible"
        6 -> "include"
        else -> "required"
    }

    private fun modLoaderLabelFromName(name: String): String? {
        val n = name.lowercase()
        return when {
            n.contains("fabric") -> "fabric"
            n.contains("neoforge") -> "neoforge"
            n.contains("quilt") -> "quilt"
            n.contains("forge") -> "forge"
            else -> null
        }
    }

    /**
     * 按 projectId + fileId 解析单个整合包依赖文件的下载信息（manifest.json files 条目）。
     * 返回 ResourceFile（含下载 URL、文件名、SHA1/大小），供 ModpackInstaller 下载。
     */
    suspend fun getModfile(projectId: Int, fileId: Int): ResourceFile? = withContext(Dispatchers.IO) {
        try {
            val body = executeWithMirror("/mods/$projectId/files/$fileId", null)
            val resp = gson.fromJson(body, CfSingleFileResponse::class.java)
            val file = resp.data ?: return@withContext null
            val sha1 = (file.hashes ?: emptyList()).firstOrNull { it.algo == 1 }?.value ?: ""
            val url = resolveDownloadUrl(file, projectId.toString())
            if (url.isBlank()) return@withContext null
            ResourceFile(
                url = url,
                filename = safeString(file.fileName),
                size = file.fileLength ?: 0,
                primary = true,
                sha1 = sha1
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析 CurseForge 文件下载 URL。
     * 部分文件 API 返回的 downloadUrl 为 null，但 CDN 实际可访问，
     * 需按 fileId 构造：https://edge.forgecdn.net/files/{前4位}/{剩余}/{fileName}
     */
    private fun resolveDownloadUrl(file: CfFile, projectId: String?): String {
        if (!file.downloadUrl.isNullOrBlank()) return file.downloadUrl
        val fileName = file.fileName ?: return ""
        val fileIdStr = file.id?.toString() ?: return ""
        if (fileIdStr.length < 5) return ""
        val part1 = fileIdStr.take(4).toIntOrNull() ?: return ""
        val part2 = fileIdStr.drop(4).toIntOrNull() ?: return ""
        return "https://edge.forgecdn.net/files/$part1/$part2/$fileName"
    }

    // ==================== CurseForge 响应模型 ====================

    data class CfSearchResponse(val data: List<CfMod>? = emptyList(), val pagination: CfPagination? = null)
    data class CfPagination(val totalCount: Long = 0)
    data class CfSingleResponse(val data: CfMod? = null)
    data class CfFilesResponse(val data: List<CfFile>? = emptyList())
    data class CfSingleFileResponse(val data: CfFile? = null)

    data class CfMod(
        val id: Long? = null,
        val slug: String? = null,
        val name: String? = null,
        val summary: String? = null,
        val downloadCount: Long? = null,
        val followers: Long? = null,
        val logo: CfImage? = null,
        val authors: List<CfAuthor>? = emptyList(),
        val categories: List<CfCategory>? = emptyList(),
        val latestFilesIndexes: List<CfFileIndex>? = emptyList(),
        val screenshots: List<CfScreenshot>? = emptyList(),
        val dateCreated: String? = null,
        val dateModified: String? = null
    )

    data class CfScreenshot(
        val title: String? = null,
        val description: String? = null,
        @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
        val url: String? = null
    )

    data class CfImage(@SerializedName("url") val url: String? = null)
    data class CfAuthor(val name: String? = null)
    data class CfCategory(val name: String? = null, val classId: Int? = null)
    data class CfFileIndex(val gameVersion: String? = null, val modLoader: Int? = null)

    data class CfFile(
        val id: Long? = null,
        val fileName: String? = null,
        @SerializedName("displayName") val displayName: String? = null,
        val fileLength: Long? = null,
        val fileDate: String? = null,
        val downloadUrl: String? = null,
        val releaseType: Int? = null,
        val isAlternate: Boolean? = null,
        val gameVersions: List<String>? = emptyList(),
        val hashes: List<CfHash>? = emptyList(),
        val dependencies: List<CfDependency>? = emptyList()
    )

    data class CfDependency(val modId: Long? = null, val relationType: Int? = null)

    data class CfHash(val algo: Int? = null, val value: String? = null)
}