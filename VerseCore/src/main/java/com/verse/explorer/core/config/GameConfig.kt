package com.verse.explorer.core.config

import android.content.Context
import android.os.Environment
import java.io.File

object GameConfig {

    const val GAME_DIR_NAME = "VerseExplorerX"

    private const val DIR_PREFS = "verse_game_dirs"
    private const val KEY_ACTIVE = "active"
    private const val KEY_LIST = "added"

    val MOJANG_MIRRORS = listOf(
        MojangMirror("BMCLAPI 镜像", "https://bmclapi2.bangbang93.com", 0, isOfficial = false),
        MojangMirror("Mojang 官方", "https://piston-meta.mojang.com", 2, isOfficial = true)
    )

    /** 默认目录解析结果缓存：避免每次取目录都写一次探针文件 */
    @Volatile
    private var cachedDefaultDir: File? = null

    fun getGameDir(context: Context): File {
        activeDirPath(context)?.let { path ->
            val dir = File(path)
            if (dir.isDirectory || dir.mkdirs()) return dir
        }
        return getDefaultGameDir(context)
    }

    /**
     * 内置默认游戏目录。
     *
     * 取舍（两个目录都实测过）：
     *  - 内部私有 `files/VerseExplorerX`：读写最稳，但文件管理器**完全看不到**，用户没法自己放
     *    mod、取存档 —— 这是反馈里反复出现的「目录里没有文件」；
     *  - 应用专属外部 `Android/data/<包名>/files/VerseExplorerX`：读写实测可用（带写入探针兜底），
     *    MT 管理器、部分文件管理器可以直接进，是 FCL/ZL2 的默认做法。
     *
     * 所以：**已有游戏数据的老用户继续留在内部目录**（几十 GB 不做静默搬迁，避免升级后版本「消失」），
     * 新装用户直接落在外部目录；想搬的老用户可以在设置里显式触发迁移（带进度，不删源）。
     */
    fun getDefaultGameDir(context: Context): File {
        cachedDefaultDir?.let { cached ->
            if (cached.isDirectory || cached.mkdirs()) return cached
        }

        val internal = File(context.filesDir, GAME_DIR_NAME)
        val resolved = when {
            // 老用户：内部已有 versions/libraries/assets，保持原位
            hasGameData(internal) -> internal
            // 新装：优先外部可见目录
            isWritable(getExternalGameDir(context)) -> getExternalGameDir(context)
            // 外部不可写（个别 ROM 的 FUSE 挂载异常）回退内部
            else -> internal
        }
        runCatching { resolved.mkdirs() }
        cachedDefaultDir = resolved
        return resolved
    }

    /** 版本/库/资源任一存在即视为「已有游戏数据」 */
    private fun hasGameData(dir: File): Boolean {
        if (!dir.isDirectory) return false
        return File(dir, "versions").isDirectory ||
            File(dir, "libraries").isDirectory ||
            File(dir, "assets").isDirectory
    }

    /** 目录可写探针：外部目录在个别 ROM 上 mkdir 成功但写入 EACCES */
    private fun isWritable(dir: File): Boolean = runCatching {
        if (!dir.exists() && !dir.mkdirs()) return false
        val probe = File(dir, ".write_probe")
        probe.writeText("1")
        probe.delete()
    }.getOrDefault(false)

    /** 当前激活的自定义游戏目录路径；未设置（使用内置目录）时为 null */
    fun activeDirPath(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE, null)?.takeIf { it.isNotBlank() }

    /** 切换激活的游戏目录；path 为空时回到内置默认目录 */
    fun setActiveDirPath(context: Context, path: String?) {
        val editor = prefs(context).edit()
        if (path.isNullOrBlank()) editor.remove(KEY_ACTIVE) else editor.putString(KEY_ACTIVE, path)
        editor.apply()
    }

    /** 用户添加的附加游戏目录（不含内置默认目录） */
    fun getAddedDirs(context: Context): List<String> =
        prefs(context).getString(KEY_LIST, null)
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** 添加游戏目录并切换为当前目录；重名/无效路径返回 false */
    fun addGameDir(context: Context, path: String): Boolean {
        val dir = File(path)
        if (!dir.isDirectory) return false
        if (path == getDefaultGameDir(context).absolutePath) return false
        val current = getAddedDirs(context)
        if (path in current) return false
        prefs(context).edit().putString(KEY_LIST, (current + path).joinToString("\n")).apply()
        setActiveDirPath(context, path)
        return true
    }

    /** 移除附加游戏目录；若其为当前目录则回退内置默认目录。附加目录本身不做删除 */
    fun removeGameDir(context: Context, path: String) {
        val remaining = getAddedDirs(context).filterNot { it == path }
        val editor = prefs(context).edit()
        if (remaining.isEmpty()) editor.remove(KEY_LIST) else editor.putString(KEY_LIST, remaining.joinToString("\n"))
        editor.apply()
        if (activeDirPath(context) == path) setActiveDirPath(context, null)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(DIR_PREFS, Context.MODE_PRIVATE)

    fun getExternalGameDir(context: Context): File {
        val externalBase = context.getExternalFilesDir(null)
        if (externalBase != null) {
            val external = File(externalBase, GAME_DIR_NAME)
            external.mkdirs()
            return external
        }
        return File(context.filesDir, GAME_DIR_NAME)
    }

    fun getVersionsDir(gameDir: File): File = File(gameDir, "versions").apply { mkdirs() }
    fun getLibrariesDir(gameDir: File): File = File(gameDir, "libraries").apply { mkdirs() }
    fun getAssetsDir(gameDir: File): File = File(gameDir, "assets").apply { mkdirs() }
    fun getNativesDir(gameDir: File): File = File(gameDir, "natives").apply { mkdirs() }
    fun getSavesDir(gameDir: File): File = File(gameDir, "saves").apply { mkdirs() }
    fun getModsDir(gameDir: File): File = File(gameDir, "mods").apply { mkdirs() }
    fun getLogsDir(gameDir: File): File = File(gameDir, "logs").apply { mkdirs() }

    private fun MojangMirror.isOfficialMirror(): Boolean = isOfficial

    fun getVersionManifestUrl(mirrorIndex: Int = 0): String {
        val mirror = MOJANG_MIRRORS.getOrNull(mirrorIndex) ?: MOJANG_MIRRORS.first()
        return if (mirror.isOfficialMirror()) {
            "https://piston-meta.mojang.com/mc/game/version_manifest.json"
        } else {
            "${mirror.baseUrl}/mc/game/version_manifest.json"
        }
    }

    fun getVersionJsonUrl(versionId: String, mirrorIndex: Int = 0): String {
        val mirror = MOJANG_MIRRORS.getOrNull(mirrorIndex) ?: MOJANG_MIRRORS.first()
        return if (mirror.isOfficialMirror()) {
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        } else {
            "${mirror.baseUrl}/version/$versionId/json"
        }
    }

    fun getVersionJsonUrlDirect(versionId: String, mirrorIndex: Int = 0): String {
        val mirror = MOJANG_MIRRORS.getOrNull(mirrorIndex) ?: MOJANG_MIRRORS.first()
        return if (mirror.isOfficialMirror()) {
            "https://piston-meta.mojang.com/v1/packages/PLACEHOLDER/$versionId.json"
        } else {
            "${mirror.baseUrl}/version/$versionId/json"
        }
    }

    fun getClientJarUrl(versionId: String, sha1: String?, mirrorIndex: Int = 0): String? {
        val mirror = MOJANG_MIRRORS.getOrNull(mirrorIndex) ?: MOJANG_MIRRORS.first()
        return if (mirror.isOfficialMirror()) {
            if (sha1 != null) "https://piston-data.mojang.com/v1/objects/$sha1/client.jar"
            else null
        } else {
            "${mirror.baseUrl}/version/$versionId/client"
        }
    }

    fun getAssetDownloadUrl(hash: String, mirrorIndex: Int = 0): String {
        val subDir = hash.substring(0, 2)
        val mirror = MOJANG_MIRRORS.getOrNull(mirrorIndex) ?: MOJANG_MIRRORS.first()
        return if (mirror.isOfficialMirror()) {
            "https://resources.download.minecraft.net/$subDir/$hash"
        } else {
            "${mirror.baseUrl}/assets/$subDir/$hash"
        }
    }

    fun getAssetIndexUrl(originalUrl: String, mirrorIndex: Int = 0): String {
        val mirror = MOJANG_MIRRORS.getOrNull(mirrorIndex) ?: MOJANG_MIRRORS.first()
        return if (mirror.isOfficialMirror()) {
            originalUrl
        } else {
            replaceWithMirror(originalUrl)
        }
    }

    fun getLibraryDownloadUrl(path: String, originalUrl: String?, mirrorIndex: Int = 0): String {
        val mirror = MOJANG_MIRRORS.getOrNull(mirrorIndex) ?: MOJANG_MIRRORS.first()
        return if (mirror.isOfficialMirror()) {
            originalUrl ?: "https://libraries.minecraft.net/$path"
        } else {
            "${mirror.baseUrl}/libraries/$path"
        }
    }

    private val BMCLAPI_URL_MAP = mapOf(
        "https://piston-data.mojang.com/" to "https://bmclapi2.bangbang93.com/",
        "https://libraries.minecraft.net/" to "https://bmclapi2.bangbang93.com/libraries/",
        "https://resources.download.minecraft.net/" to "https://bmclapi2.bangbang93.com/assets/",
        "https://launchermeta.mojang.com/" to "https://bmclapi2.bangbang93.com/",
        "https://piston-meta.mojang.com/" to "https://bmclapi2.bangbang93.com/",
        // 第三方 Maven 仓库镜像 (通过 BMCLAPI 通用 Maven 代理)
        "https://files.minecraftforge.net/maven" to "https://bmclapi2.bangbang93.com/maven",
        "https://maven.minecraftforge.net" to "https://bmclapi2.bangbang93.com/maven",
        "https://maven.fabricmc.net/" to "https://bmclapi2.bangbang93.com/maven/",
        "https://maven.neoforged.net/releases/" to "https://bmclapi2.bangbang93.com/maven/",
        "https://maven.quiltmc.org/repository/release" to "https://bmclapi2.bangbang93.com/maven"
    )

    private val MCBBS_URL_MAP = mapOf(
        "https://piston-data.mojang.com/" to "https://download.mcbbs.net/",
        "https://libraries.minecraft.net/" to "https://download.mcbbs.net/maven/",
        "https://resources.download.minecraft.net/" to "https://download.mcbbs.net/assets/",
        "https://launchermeta.mojang.com/" to "https://download.mcbbs.net/",
        "https://piston-meta.mojang.com/" to "https://download.mcbbs.net/"
    )

    fun replaceWithMirror(url: String): String {
        if (url.isEmpty()) return url
        for ((original, mirror) in BMCLAPI_URL_MAP) {
            if (url.startsWith(original)) {
                return url.replace(original, mirror)
            }
        }
        return replaceGitHubUrl(url)
    }

    fun replaceGitHubUrl(url: String): String {
        if (!url.contains("github.com") && !url.contains("githubusercontent.com")) {
            return url
        }
        return url
            .replace("https://github.com/", "https://ghp.ci/https://github.com/")
            .replace("https://raw.githubusercontent.com/", "https://ghp.ci/https://raw.githubusercontent.com/")
            .replace("https://objects.githubusercontent.com/", "https://ghp.ci/https://objects.githubusercontent.com/")
    }

    fun getMirrorUrl(originalUrl: String?, path: String?): String {
        if (originalUrl == null) return ""
        if (path != null) {
            for ((original, mirror) in BMCLAPI_URL_MAP) {
                if (originalUrl.startsWith(original)) {
                    return originalUrl.replace(original, mirror)
                }
            }
            return "${BMCLAPI_ROOT}/libraries/$path"
        }
        return replaceWithMirror(originalUrl)
    }

    const val BMCLAPI_ROOT = "https://bmclapi2.bangbang93.com"
}

data class MojangMirror(
    val name: String,
    val baseUrl: String,
    val priority: Int,
    val isOfficial: Boolean = false
)

fun isValidJsonResponse(body: String?): Boolean {
    if (body.isNullOrBlank()) return false
    val trimmed = body.trimStart()
    return trimmed.startsWith("{") || trimmed.startsWith("[")
}