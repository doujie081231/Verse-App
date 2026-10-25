package com.verse.explorer.core.resource

import android.content.Context
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.download.SmartDownloadClient
import com.verse.explorer.core.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 统一的资源仓库：整合 Modrinth + CurseForge 双源，提供搜索、详情、版本、下载。
 *
 *  - [search]：双源搜索并合并（按下载量排序）
 *  - [getDetail]：双源详情
 *  - [getVersions]：双源版本列表
 *  - [downloadVersion]：下载资源文件到游戏对应目录
 */
class ResourceRepository(
    private val context: Context,
    private val useMirror: Boolean = true,
    private val snapshot: com.verse.explorer.core.install.InstallationSnapshot? = null
) {

    private val smartClient = if (snapshot != null) SmartDownloadClient(source = snapshot.downloadSource) else SmartDownloadClient(context = context)

    /**
     * 双源搜索。
     *
     * @param source 可选过滤："modrinth" / "curseforge" / ""（全部）
     */
    suspend fun search(
        query: String = "",
        projectType: String = "mod",
        categories: List<String> = emptyList(),
        gameVersions: List<String> = emptyList(),
        loaders: List<String> = emptyList(),
        sort: String = "downloads",
        limit: Int = 20,
        offset: Int = 0,
        source: String = ""
    ): ResourceSearchResult = withContext(Dispatchers.IO) {
        val allHits = mutableListOf<ModrinthSearchHit>()
        var total = 0

        // 避免 source=全部 时串行等待两个慢源（旧实现先 CF 后 MR，最坏 2 倍超时）。
        coroutineScope {
            val cfDeferred = if (source.isBlank() || source == "curseforge") {
                async {
                    runCatching {
                        CurseForgeApi.search(
                            query = query,
                            projectType = projectType,
                            loader = loaders.firstOrNull() ?: "",
                            gameVersion = gameVersions.firstOrNull() ?: "",
                            sort = if (query.isBlank()) "downloads" else sort,
                            limit = limit,
                            offset = offset
                        )
                    }
                }
            } else null

            val mrDeferred = if (source.isBlank() || source == "modrinth") {
                async {
                    runCatching {
                        ModrinthApi.searchProjects(
                            query = query,
                            projectType = projectType,
                            categories = categories,
                            gameVersions = gameVersions,
                            loaders = loaders,
                            sort = if (query.isBlank()) "downloads" else sort,
                            limit = limit,
                            offset = offset,
                            useMirror = useMirror
                        )
                    }
                }
            } else null

            // 任一源失败不影响另一源（与旧逻辑 try/catch 语义一致）
            cfDeferred?.await()?.onSuccess {
                allHits.addAll(it.hits)
                total += it.total
            }
            mrDeferred?.await()?.onSuccess {
                allHits.addAll(it.hits)
                total += it.totalHits
            }
        }

        if (allHits.size > 1) {
            allHits.sortByDescending { it.downloads }
        }
        ResourceSearchResult(hits = allHits, total = total)
    }

    /** 获取项目详情（按 source 路由） */
    suspend fun getDetail(projectId: String, source: String): ResourceDetail = withContext(Dispatchers.IO) {
        if (source == "curseforge") {
            CurseForgeApi.getProjectDetail(projectId)
        } else {
            val p = ModrinthApi.getProject(projectId, useMirror)
            ResourceDetail(
                id = p.id,
                slug = p.slug,
                title = p.title,
                description = p.description,
                body = p.body,
                iconUrl = p.iconUrl,
                downloads = p.downloads,
                followers = p.followers,
                categories = p.categories,
                loaders = p.loaders,
                gameVersions = p.gameVersions,
                license = p.license?.name ?: "",
                sourceUrl = p.sourceUrl ?: "",
                issuesUrl = p.issuesUrl ?: "",
                wikiUrl = p.wikiUrl ?: "",
                discordUrl = p.discordUrl ?: "",
                gallery = (p.gallery ?: emptyList()).map { ResourceGallery(it.url, it.title) },
                dateCreated = p.dateCreated,
                dateModified = p.dateModified,
                source = "modrinth",
                projectType = ""
            )
        }
    }

    /** 获取版本列表（按 source 路由） */
    suspend fun getVersions(
        projectId: String,
        source: String,
        loader: String = "",
        gameVersion: String = ""
    ): List<ResourceVersion> = withContext(Dispatchers.IO) {
        if (source == "curseforge") {
            CurseForgeApi.getProjectFiles(projectId, loader, gameVersion)
        } else {
            // Gson 用 Unsafe 实例化 Kotlin data class，缺失/为 null 的字段会被注入 null（绕过默认值），
            // 任何一处未兜底都会在映射时抛"message 为 null 的 NPE"导致整批版本加载失败（详见 ModDetail 日志）。
            // 因此对所有字段兜底，并按版本粒度 try 单条失败不拖垮整批。
            val rawVersions = ModrinthApi.getProjectVersions(projectId, useMirror) ?: emptyList()
            rawVersions.mapNotNull { v ->
                val files = v.files.orEmpty()
                val primary = files.firstOrNull { it.primary } ?: files.firstOrNull()
                runCatching {
                    ResourceVersion(
                        id = v.id ?: "",
                        name = v.name ?: "",
                        versionNumber = v.versionNumber ?: "",
                        datePublished = v.datePublished ?: "",
                        gameVersions = v.gameVersions.orEmpty(),
                        loaders = v.loaders.orEmpty(),
                        releaseType = v.versionType ?: "release",
                        downloads = v.downloads.toLong(),
                        changelog = v.changelog ?: "",
                        primaryFile = primary?.let {
                            ResourceFile(
                                it.url ?: "",
                                it.filename ?: "",
                                it.size,
                                it.primary,
                                it.hashes?.get("sha1") ?: ""
                            )
                        },
                        files = files.map {
                            ResourceFile(
                                it.url ?: "",
                                it.filename ?: "",
                                it.size,
                                it.primary,
                                it.hashes?.get("sha1") ?: ""
                            )
                        },
                        dependencies = v.dependencies.orEmpty().map {
                            ResourceDependency(
                                projectId = it.projectId ?: "",
                                versionId = it.versionId ?: "",
                                fileName = it.fileName ?: "",
                                type = it.dependencyType ?: "required",
                                source = "modrinth"
                            )
                        },
                        source = "modrinth"
                    )
                }.getOrNull()
            }
        }
    }

    /**
     * 下载指定版本到游戏对应目录。
     *
     * @param isCancelled 取消轮询：返回 true 时下载尽快终止（半截 .part 保留供续传）
     * @return 下载完成后的文件路径
     */
    suspend fun downloadVersion(
        projectType: String,
        version: ResourceVersion,
        isCancelled: () -> Boolean = { false },
        onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit = { _, _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        val primaryFile = version.primaryFile ?: version.files.firstOrNull()
            ?: return@withContext Result.failure(Exception("该版本没有可下载的文件"))

        if (primaryFile.url.isBlank()) {
            return@withContext Result.failure(Exception("下载链接为空，该资源可能暂时不可用"))
        }

        val gameDir = snapshot?.paths?.root ?: GameConfig.getGameDir(context)
        val destDir = when (projectType) {
            "resourcepack" -> File(gameDir, "resourcepacks")
            "shader" -> File(gameDir, "shaderpacks")
            "datapack" -> File(gameDir, "datapacks")
            "modpack" -> File(gameDir, "modpacks")
            else -> GameConfig.getModsDir(gameDir)
        }
        destDir.mkdirs()

        val safeName = (primaryFile.filename.ifBlank { "${version.id}.jar" })
            .replace(Regex("""[^a-zA-Z0-9._\-]"""), "_")
        val destFile = File(destDir, safeName)

        // 若其他版本目录的 mods 已存在同名同 sha1 的文件，直接复制，避免重复下载
        if (projectType == "mod" && destFile.name.endsWith(".jar") && primaryFile.sha1.isNotBlank()) {
            val versionsDir = GameConfig.getVersionsDir(gameDir)
            val reuseTarget = findExistingMod(versionsDir, destFile.name, primaryFile.sha1)
            if (reuseTarget != null) {
                try {
                    destFile.parentFile?.mkdirs()
                    reuseTarget.copyTo(destFile, overwrite = true)
                    onProgress(primaryFile.size, primaryFile.size, 0)
                    return@withContext Result.success(destFile)
                } catch (_: Exception) {
                    // 复制失败则回退到正常下载
                }
            }
        }

        // 下载走 .part 临时文件，成功后改名到位：失败/取消保留 .part 供下次续传
        // （目标文件只有校验通过后才会生成，不会有坏文件混入游戏目录）
        val partFile = File(destDir, "$safeName.part")
        try {
            smartClient.download(
                originalUrl = primaryFile.url,
                targetFile = partFile,
                expectedSha1 = primaryFile.sha1.ifBlank { null },
                knownSize = primaryFile.size,
                isCancelled = isCancelled,
                onProgress = {
                    onProgress(it.downloaded, it.total, it.speed)
                }
            )
            if (destFile.exists()) destFile.delete()
            if (!partFile.renameTo(destFile)) {
                throw Exception("下载完成但写入失败: $safeName")
            }
            Result.success(destFile)
        } catch (e: Throwable) {
            // 取消不删除 .part（续传）；其余失败也保留半截文件，重试时从断点继续
            throw e
        }
    }

    /** 获取游戏 mods 目录（供 UI 展示下载落点） */
    fun getModsDir(): File = GameConfig.getModsDir(GameConfig.getGameDir(context))

    /**
     * 查询某资源文件是否已安装在任一版本的 [folder]（mods/resourcepacks 等）目录：
     * 文件名 + SHA1 双重匹配，命中返回已有文件（用于前置模组等批量下载的本地去重）。
     */
    fun findInstalledFile(folder: String, fileName: String, expectedSha1: String): File? =
        findExistingFileInVersions(
            GameConfig.getVersionsDir(GameConfig.getGameDir(context)),
            folder, fileName, expectedSha1
        )

    fun versionFolderFor(projectType: String): String? = when (projectType) {
        "resourcepack" -> "resourcepacks"
        "shader" -> "shaderpacks"
        "datapack" -> "datapacks"
        "modpack" -> null
        else -> "mods"
    }

    /**
     * 先下载到应用缓存，再复制到每个选中版本的 versions/<id>/<folder> 内，目标同名文件先删除。
     * 隔离安装：不写入全局目录。
     *
     * @param targetVersionIds 选中的游戏版本 id 列表
     * @return 各版本目录内的安装结果
     */
    suspend fun downloadVersionForVersions(
        projectType: String,
        version: ResourceVersion,
        targetVersionIds: List<String>,
        isCancelled: () -> Boolean = { false },
        onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit = { _, _, _ -> }
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val primaryFile = version.primaryFile ?: version.files.firstOrNull()
                ?: throw Exception("该版本没有可下载的文件")
            if (primaryFile.url.isBlank()) {
                throw Exception("下载链接为空，该资源可能暂时不可用")
            }
            val folder = versionFolderFor(projectType)
                ?: throw Exception("该资源类型不支持按版本安装")

            val safeName = (primaryFile.filename.ifBlank { "${version.id}.jar" })
                .replace(Regex("""[^a-zA-Z0-9._\-]"""), "_")
            val gameDir = snapshot?.paths?.root ?: GameConfig.getGameDir(context)

            val cacheDir = File(context.cacheDir, "assets")
            cacheDir.mkdirs()
            val cacheFile = File(cacheDir, "${java.util.UUID.randomUUID()}-${primaryFile.sha1.ifBlank { safeName }}")

            try {
                // 版本内复用：任一版本的对应文件夹里
                // 已有同名同 SHA1 的文件时直接复制，避免重复下载
                var reused = false
                if (primaryFile.sha1.isNotBlank()) {
                    val reuseSource = findExistingFileInVersions(
                        GameConfig.getVersionsDir(gameDir), folder, safeName, primaryFile.sha1
                    )
                    if (reuseSource != null) {
                        runCatching {
                            reuseSource.copyTo(cacheFile, overwrite = true)
                            onProgress(primaryFile.size, primaryFile.size, 0)
                            reused = true
                        }
                    }
                }
                if (!reused) {
                    smartClient.download(
                        originalUrl = primaryFile.url,
                        targetFile = cacheFile,
                        expectedSha1 = primaryFile.sha1.ifBlank { null },
                        knownSize = primaryFile.size,
                        isCancelled = isCancelled,
                        onProgress = {
                            onProgress(it.downloaded, it.total, it.speed)
                        }
                    )
                }

                val versionsDir = GameConfig.getVersionsDir(gameDir)
                val installed = mutableListOf<File>()
                targetVersionIds.forEach { versionId ->
                    val targetFolder = File(File(versionsDir, versionId), folder)
                    targetFolder.mkdirs()
                    val targetFile = File(targetFolder, safeName)
                    if (targetFile.exists() && !targetFile.delete()) {
                        throw IOException("无法覆盖已存在的文件: ${targetFile.absolutePath}")
                    }
                    cacheFile.copyTo(targetFile)
                    installed.add(targetFile)
                }
                installed
            } finally {
                cacheFile.delete()
            }
        }
    }

    /**
     * 在 VERSIONS_DIR 下所有版本目录的 mods 子目录中查找同名且同 SHA1 的 mod 文件
     */
    private fun findExistingMod(versionsDir: File, fileName: String, expectedSha1: String): File? =
        findExistingFileInVersions(versionsDir, "mods", fileName, expectedSha1)

    /** 在所有版本目录的 [folder] 子目录中查找同名且同 SHA1 的文件，找到则返回，否则返回 null */
    private fun findExistingFileInVersions(
        versionsDir: File,
        folder: String,
        fileName: String,
        expectedSha1: String
    ): File? {
        val versionDirs = versionsDir.listFiles { f -> f.isDirectory } ?: return null
        for (dir in versionDirs) {
            val candidate = File(File(dir, folder), fileName)
            if (!candidate.isFile || candidate.length() == 0L) continue
            try {
                val actualSha1 = FileUtils.calculateSha1(candidate)
                if (actualSha1.equals(expectedSha1, ignoreCase = true)) {
                    return candidate
                }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}