package com.verse.explorer.core.config

import android.content.Context
import java.io.File

object GameConfig {

    const val GAME_DIR_NAME = "VerseExplorerX"

    val MOJANG_MIRRORS = listOf(
        MojangMirror("BMCLAPI 镜像", "https://bmclapi2.bangbang93.com", 0, isOfficial = false),
        MojangMirror("MCBBS 镜像", "https://download.mcbbs.net", 1, isOfficial = false),
        MojangMirror("Mojang 官方", "https://piston-meta.mojang.com", 2, isOfficial = true)
    )

    fun getGameDir(context: Context): File {
        // 使用 internal storage 避免 Android 11+ SELinux 限制
        // 应用无法访问通过 adb shell 创建的文件（即使有 MANAGE_EXTERNAL_STORAGE 权限）
        // internal storage 由应用自己管理，adb 可通过 run-as 推送文件
        return File(context.filesDir, GAME_DIR_NAME)
    }

    fun getExternalGameDir(context: Context): File {
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