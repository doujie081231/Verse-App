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

    fun getGameDir(context: Context): File {
        activeDirPath(context)?.let { path ->
            val dir = File(path)
            if (dir.isDirectory || dir.mkdirs()) return dir
        }
        return getDefaultGameDir(context)
    }

    /** 内置默认游戏目录 */
    fun getDefaultGameDir(context: Context): File {
        // 曾用共享根 /sdcard/VerseExplorerX 与外部私有 /sdcard/Android/data/...，
        // 二者在部分设备（ColorOS 等）的 FUSE 挂载上不可靠：共享根读取报
        // Permission denied（JVM 载不到主类 => 启动 exit 6），外部私有写入报
        // EACCES（分块下载 open failed）。内部 files 目录由系统保障读写，无上述问题。
        val internal = File(context.filesDir, GAME_DIR_NAME)
        try {
            internal.mkdirs()
            return internal
        } catch (_: Exception) {
            return getExternalGameDir(context)
        }
    }

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