package com.verse.explorer.core.install.modpack

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 整合包统一数据结构（Modrinth mrpack / CurseForge zip 解析后）。
 */
data class ParsedModpack(
    val name: String,
    val version: String,
    val minecraftVersion: String,
    val loaderType: String?, // "forge" / "fabric" / "neoforge" / "quilt"
    val loaderVersion: String?,
    val files: List<ModpackFile>,
    val overridesPath: String? // zip 内 overrides 文件夹名，通常 "overrides"
)

/**
 * 整合包内单个文件条目（需要下载并放置到对应路径）。
 *
 * 两种来源：
 *  - Modrinth：path 直接是目标路径，downloadUrls 已给出。
 *  - CurseForge：仅给出 curseProjectId + curseFileId，需经 CurseForge API 解析下载 URL，
 *    目标路径统一放 gameDir/mods/。
 */
data class ModpackFile(
    val path: String = "", // 相对于游戏目录的目标路径 (e.g. "mods/xxx.jar")
    val sha1: String? = null,
    val size: Long = 0L,
    val downloadUrls: List<String> = emptyList(), // 多个下载 URL 按优先级排列
    val required: Boolean = true, // 是否必需（整合包必需安装，可选可以跳过）
    val envClient: String = "required", // "required" / "optional" / "unsupported"
    val envServer: String = "required", // "required" / "optional" / "unsupported"
    // CurseForge manifest 专用：需要按 projectID+fileID 到 CR API 解析下载地址
    val curseProjectId: Int = 0,
    val curseFileId: Int = 0
)

/**
 * Modpack 解析器：统一入口，根据 zip 内容自动识别 Modrinth/CurseForge 格式。
 */
object ModpackArchive {

    private val gson: Gson = GsonBuilder().setLenient().create()

    /**
     * 从本地下载的 zip 文件解析整合包。
     */
    fun parseFromFile(zipFile: File): Result<ParsedModpack> {
        return try {
            zipFile.inputStream().use { input ->
                parseFromInputStream(input, zipFile.name)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从输入流解析（可用于已打开的 zip）。
     */
    fun parseFromInputStream(input: InputStream, fileName: String): Result<ParsedModpack> {
        return try {
            val zis = ZipInputStream(input)
            var modrinthIndex: String? = null
            var curseManifest: String? = null
            val entries = mutableListOf<String>()

            // 第一遍扫：找 modrinth.index.json 或 manifest.json
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    entries.add(name)
                    if (name == "modrinth.index.json") {
                        modrinthIndex = zis.readBytes().decodeToString()
                    } else if (name == "manifest.json") {
                        curseManifest = zis.readBytes().decodeToString()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()

            val parsed = when {
                modrinthIndex != null -> parseModrinth(modrinthIndex, entries)
                curseManifest != null -> parseCurseForge(curseManifest, entries)
                else -> throw Exception("无法识别整合包格式：找不到 modrinth.index.json 或 manifest.json")
            }

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 解析 Modrinth mrpack 格式（modrinth.index.json）。
     */
    private fun parseModrinth(json: String, allEntries: List<String>): ParsedModpack {
        val root = gson.fromJson(json, ModrinthRoot::class.java)

        var mcVer: String? = null
        var loaderType: String? = null
        var loaderVer: String? = null

        // dependencies map: "minecraft" → version, "fabric-loader" → version etc.
        root.dependencies?.forEach { (k, v) ->
            when (k) {
                "minecraft" -> mcVer = v
                "fabric-loader" -> {
                    loaderType = "fabric"
                    loaderVer = v
                }
                "forge" -> {
                    loaderType = "forge"
                    loaderVer = v
                }
                "neoforge" -> {
                    loaderType = "neoforge"
                    loaderVer = v
                }
                "quilt-loader" -> {
                    loaderType = "quilt"
                    loaderVer = v
                }
            }
        }

        if (mcVer == null) {
            throw Exception("Modrinth 整合包格式错误：dependencies 中缺少 minecraft 版本")
        }

        val files = root.files?.map { mr ->
            ModpackFile(
                path = mr.path ?: "",
                sha1 = mr.hashes?.get("sha1"),
                size = mr.fileSize ?: 0L,
                downloadUrls = mr.downloads ?: emptyList(),
                required = true, // Modrinth mrpack 所有 files 都是必需的
                envClient = mr.env?.get("client") ?: "required",
                envServer = mr.env?.get("server") ?: "required"
            )
        } ?: emptyList()

        // 检查 overrides 目录
        val hasOverrides = allEntries.any { it.startsWith("overrides/") }

        return ParsedModpack(
            name = root.name ?: "Unnamed",
            version = root.versionId ?: "1.0",
            minecraftVersion = mcVer,
            loaderType = loaderType,
            loaderVersion = loaderVer,
            files = files,
            overridesPath = if (hasOverrides) "overrides" else null
        )
    }

    /**
     * 解析 CurseForge zip 格式（manifest.json）。
     */
    private fun parseCurseForge(json: String, allEntries: List<String>): ParsedModpack {
        val root = gson.fromJson(json, CurseRoot::class.java)

        val mcVer = root.minecraft?.version ?: throw Exception("CurseForge 整合包缺少 minecraft.version")

        var loaderType: String? = null
        var loaderVer: String? = null

        // modLoaders: [{"id": "forge-14.23.5.2860"}]
        root.minecraft?.modLoaders?.firstOrNull()?.let { ml ->
            val id = ml.id ?: ""
            // split "forge-14.23.5.2860" → ("forge", "14.23.5.2860")
            val split = id.split("-", limit = 2)
            if (split.size == 2) {
                loaderType = split[0]
                loaderVer = split[1]
            }
            // handle "fabric-0.14.22-1.19.2" → still loaderType=fabric
            if (loaderType == "fabric" && split.size >= 2) {
                // fabric version is first part after split, keep as-is
            }
        }

        val files = root.files?.mapNotNull { cf ->
            // CurseForge 文件在整合包里只有 projectID/fileID，需要 API 获取下载 URL
            // 我们只记录元数据，下载时通过 API 获取 URL
            ModpackFile(
                path = "",
                sha1 = null,
                size = 0L,
                downloadUrls = emptyList(),
                required = cf.required ?: true,
                envClient = "required",
                envServer = "required",
                curseProjectId = cf.projectID,
                curseFileId = cf.fileID
            )
        } ?: emptyList()

        val overrides = root.overrides ?: "overrides"
        val hasOverrides = allEntries.any { it.startsWith("$overrides/") }

        return ParsedModpack(
            name = root.name ?: "Unnamed",
            version = root.version ?: "1.0",
            minecraftVersion = mcVer,
            loaderType = loaderType,
            loaderVersion = loaderVer,
            files = files,
            overridesPath = if (hasOverrides) overrides else null
        )
    }

    // Gson 数据类（Modrinth 结构）
    private data class ModrinthRoot(
        val formatVersion: Int = 0,
        val game: String = "",
        val versionId: String? = null,
        val name: String? = null,
        val dependencies: Map<String, String>? = null,
        val files: List<ModrinthFile>? = null
    )

    private data class ModrinthFile(
        val path: String? = null,
        val hashes: Map<String, String>? = null,
        val fileSize: Long? = null,
        val downloads: List<String>? = null,
        val env: Map<String, String>? = null
    )

    // Gson 数据类（CurseForge 结构）
    private data class CurseRoot(
        val manifestType: String = "",
        val manifestVersion: Int = 0,
        val name: String? = null,
        val version: String? = null,
        val author: String? = null,
        val minecraft: CurseMinecraft? = null,
        val files: List<CurseFile>? = null,
        val overrides: String? = null
    )

    private data class CurseMinecraft(
        val version: String? = null,
        val modLoaders: List<CurseModLoader>? = null
    )

    private data class CurseModLoader(
        val id: String? = null
    )

    private data class CurseFile(
        val projectID: Int = 0,
        val fileID: Int = 0,
        val required: Boolean? = true
    )
}
