package com.verse.explorer.core.install.modpack

import android.content.Context
import android.util.Log
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.download.SmartDownloadClient
import com.verse.explorer.core.install.GameInstaller
import com.verse.explorer.core.resource.CurseForgeApi
import com.verse.explorer.core.util.FileUtils
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 整合包安装进度。
 */
data class ModpackInstallProgress(
    val stage: ModpackInstallStage,
    val message: String,
    val progress: Float,
    val currentFile: String = ""
)

/**
 * 整合包安装阶段。
 */
enum class ModpackInstallStage {
    PARSE,         // 解析整包
    ENSURE_BASE,   // 确保 MC + loader 版本就绪（复用 GameInstaller）
    DOWNLOAD,      // 下载整包文件
    OVERRIDES,     // 解压 overrides
    VERSION,       // 写入整合包版本
    COMPLETED,
    FAILED
}

/**
 * 整合包安装引擎：
 * 解析（mrpack/CurseForge）→ 确保 MC+loader 版本（复用 GameInstaller）→
 * 下载整包文件（Modrinth 直链 / CurseForge 按 projectID+fileID 解析）→
 * 解压 overrides 到游戏目录 → 写入继承版本的整合包版本条目。
 *
 * 设计对齐现有资源安装流：整包文件落盘到共享 gameDir（mods/、config/ 等），
 * 与详情页下载其它资源的落盘目录一致，由启动器统一从 gameDir 加载。
 */
class ModpackInstaller(private val context: Context) {

    private val smartClient = SmartDownloadClient(context = context)

    /**
     * 安装本地已下载的整合包 zip。
     * @return 成功时返回创建的整合包版本 id
     */
    suspend fun install(
        modpackZip: File,
        progress: (ModpackInstallProgress) -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val gameDir = GameConfig.getGameDir(context)

                progress(ModpackInstallProgress(ModpackInstallStage.PARSE, "解析整合包...", 0.02f))
                val parsed = ModpackArchive.parseFromFile(modpackZip).getOrThrow()

                val baseId = ensureBaseVersion(gameDir = gameDir, parsed = parsed, progress = progress)

                // 整合包版本 id：继承自基版（MC 或 MC+loader）
                val modpackId = "${baseId}-modpack-${sanitizeId(parsed.name)}"

                downloadWorkspaceFiles(gameDir = gameDir, parsed = parsed, progress = progress)

                parsed.overridesPath?.let { ov ->
                    extractOverrides(modpackZip = modpackZip, overridesPath = ov, gameDir = gameDir, progress = progress)
                }

                writeModpackVersion(
                    versionsDir = GameConfig.getVersionsDir(gameDir),
                    modpackId = modpackId,
                    baseId = baseId,
                    parsed = parsed
                )

                progress(ModpackInstallProgress(ModpackInstallStage.COMPLETED, "整合包安装完成 ($modpackId)", 1f))
                Result.success(modpackId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ModpackInstaller", "安装失败: ${e.message}", e)
                progress(ModpackInstallProgress(ModpackInstallStage.FAILED, "安装失败: ${e.message}", 1f))
                Result.failure(e)
            }
        }
    }

    /**
     * 确保 MC + loader 版本就绪。
     * - 目标版本 id：若解析出加载器，则为 "<mc>-<loader>-<loaderVer>"（与 FCL 加载器版本 id 一致）；
     *   否则为 "<mc>"。
     * - 已存在则跳过；不存在则复用 GameInstaller.installVersion（内部会一并安装原版 + 加载器）。
     */
    private suspend fun ensureBaseVersion(
        gameDir: File,
        parsed: ParsedModpack,
        progress: (ModpackInstallProgress) -> Unit
    ): String {
        val mc = parsed.minecraftVersion
        val targetId = if (!parsed.loaderType.isNullOrBlank() && !parsed.loaderVersion.isNullOrBlank()) {
            "$mc-${parsed.loaderType}-${parsed.loaderVersion}"
        } else mc

        val local = VersionManager(gameDir).getLocalVersions()
        if (local.any { it.id == targetId }) {
            progress(ModpackInstallProgress(ModpackInstallStage.ENSURE_BASE, "基版已就绪 ($targetId)", 0.2f))
            return targetId
        }

        progress(ModpackInstallProgress(
            ModpackInstallStage.ENSURE_BASE,
            "安装基版 $mc${parsed.loaderType?.let { " + $it" } ?: ""} ($targetId)...",
            0.1f
        ))
        val installer = GameInstaller(context)
        val baseVersion = MinecraftVersion(id = mc)
        val ok = installer.installVersion(
            version = baseVersion,
            loaderType = parsed.loaderType,
            loaderVersion = parsed.loaderVersion
        ) { p ->
            progress(ModpackInstallProgress(
                ModpackInstallStage.ENSURE_BASE,
                p.message,
                0.1f + p.progress * 0.20f
            ))
        }
        if (!ok) throw Exception("基版安装失败或已取消")
        return targetId
    }

    /**
     * 下载整包依赖文件到游戏目录：
     *  - Modrinth：files[].path 即目标路径（如 mods/xxx.jar），downloads 列表多源下载，SHA1 校验。
     *  - CurseForge：files[].curseProjectId+curseFileId 经 CR API 解析下载 URL，落盘到 mods/<文件名>。
     */
    private suspend fun downloadWorkspaceFiles(
        gameDir: File,
        parsed: ParsedModpack,
        progress: (ModpackInstallProgress) -> Unit
    ) {
        val files = parsed.files
        if (files.isEmpty()) {
            progress(ModpackInstallProgress(ModpackInstallStage.DOWNLOAD, "整包无独立文件", 0.5f))
            return
        }

        var done = 0
        val total = files.size
        files.forEach { f ->
            // 客户端整合包：跳过标记为客户端不支持的条目（如仅服务端库）
            if (f.envClient.equals("unsupported", ignoreCase = true)) {
                done++
                return@forEach
            }
            progress(ModpackInstallProgress(
                ModpackInstallStage.DOWNLOAD,
                "下载 (${done + 1}/$total): ${f.pathOrName()}",
                0.35f + 0.30f * (done.toFloat() / total.coerceAtLeast(1))
            ))
            try {
                if (f.curseProjectId != 0 && f.curseFileId != 0) {
                    downloadCurseFile(gameDir, f)
                } else if (f.downloadUrls.isNotEmpty() && f.path.isNotBlank()) {
                    downloadDirectFile(gameDir, f)
                }
            } catch (_: Exception) {
                // 单个整包文件失败不中断整体安装（记录即可），保证尽量多文件落盘
                Log.w("ModpackInstaller", "整包文件失败: ${f.pathOrName()}")
            }
            done++
        }
        progress(ModpackInstallProgress(ModpackInstallStage.DOWNLOAD, "整包文件下载完成 ($done/$total)", 0.65f))
    }

    private suspend fun downloadCurseFile(gameDir: File, f: ModpackFile) {
        if (f.curseFileId == 0) return
        val rf = CurseForgeApi.getModfile(f.curseProjectId, f.curseFileId) ?: return
        val target = File(GameConfig.getModsDir(gameDir), sanitizeFileName(rf.filename))
        smartClient.download(
            originalUrl = rf.url,
            targetFile = target,
            expectedSha1 = rf.sha1.ifBlank { null },
            knownSize = rf.size
        )
    }

    private suspend fun downloadDirectFile(gameDir: File, f: ModpackFile) {
        // path 形如 "mods/xxx.jar"，避免路径逃逸
        val rel = f.path.removePrefix("/")
        val target = File(gameDir, rel)
        if (!target.canonicalPath.startsWith(gameDir.canonicalPath)) return
        val primaryUrl = f.downloadUrls.firstOrNull() ?: return
        smartClient.download(
            originalUrl = primaryUrl,
            targetFile = target,
            expectedSha1 = f.sha1,
            knownSize = f.size
        )
    }

    /**
     * 解压 zip 内 overrides 目录到游戏目录（覆盖 gameDir 下同名文件）。
     * overrides/config/xxx → gameDir/config/xxx
     */
    private suspend fun extractOverrides(
        modpackZip: File,
        overridesPath: String,
        gameDir: File,
        progress: (ModpackInstallProgress) -> Unit
    ) {
        if (!modpackZip.exists()) return
        var extracted = 0
        ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(modpackZip))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && name.startsWith(overridesPath + "/")) {
                    val rel = name.removePrefix(overridesPath + "/")
                    if (rel.isNotBlank() && !rel.contains("..")) {
                        val out = File(gameDir, rel)
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zis.copyTo(it) }
                        extracted++
                    }
                }
                try { zis.closeEntry() } catch (_: Exception) {}
                entry = zis.nextEntry
            }
        }
        progress(ModpackInstallProgress(ModpackInstallStage.OVERRIDES, "overrides 解压完成 ($extracted 个文件)", 0.8f))
    }

    /**
     * 写入整合包版本条目：versions/<id>/<id>.json，inheritsFrom 指向基版。
     * 启动器沿继承链解析 jar/资源/库/主类，getLocalVersions 也能识别该整合包版本。
     */
    private fun writeModpackVersion(
        versionsDir: File,
        modpackId: String,
        baseId: String,
        parsed: ParsedModpack
    ) {
        val now = java.time.Instant.now().toString()
        val json = JsonObject()
        json.addProperty("id", modpackId)
        json.addProperty("inheritsFrom", baseId)
        json.addProperty("type", "release")
        json.addProperty("time", now)
        json.addProperty("releaseTime", now)
        json.addProperty("mainClass", "net.minecraft.client.main.Main")
        val dj = JsonObject()
        dj.addProperty("id", "默认")
        json.add("javaVersion", dj)

        val dir = File(versionsDir, modpackId)
        if (!dir.exists()) dir.mkdirs()
        File(dir, "$modpackId.json").writeText(
            GsonBuilder().setLenient().serializeNulls().create().toJson(json)
        )
        Log.i("ModpackInstaller", "整合包版本已写入: $modpackId (inheritsFrom=$baseId)")
    }

    private fun sanitizeId(name: String): String {
        var s = name.trim()
        s = s.replace(Regex("""[^a-zA-Z0-9._\-]"""), "-")
        s = s.replace(Regex("[-_]+"), "-")
        return s.trim('-').ifBlank { "pack" }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[^a-zA-Z0-9._\-]"""), "_").ifBlank { "file.jar" }
    }

    private fun ModpackFile.pathOrName(): String = path.ifBlank { "curse $curseProjectId/$curseFileId" }
}