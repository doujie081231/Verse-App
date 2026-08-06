package com.verse.explorer.core.download

import android.content.Context

/**
 * 镜像源管理：根据用户下载源设置生成多源 URL 列表。
 *
 * 用户下载源设置存储在 SharedPreferences key="download_source"，
 * 取值：auto（智能混合）/ bmclapi / official / mojang
 *
 * - "auto"：返回 [BMCLAPI, MCBBS, 原始]，并行测速选最快
 * - "bmclapi"：返回 [BMCLAPI, 原始]，镜像在前
 * - "official"：返回 [原始, BMCLAPI]，原始在前
 * - "mojang"：仅 [原始]，不走镜像
 */
object MirrorSourceManager {

    private const val PREFS_NAME = "verse_explorer_settings"
    private const val KEY_DOWNLOAD_SOURCE = "download_source"
    private const val DEFAULT_SOURCE = "auto"

    private val MOJANG_TO_BMCLAPI = mapOf(
        "https://piston-data.mojang.com" to "https://bmclapi2.bangbang93.com",
        "https://piston-meta.mojang.com" to "https://bmclapi2.bangbang93.com",
        "https://launchermeta.mojang.com" to "https://bmclapi2.bangbang93.com",
        "https://launcher.mojang.com" to "https://bmclapi2.bangbang93.com",
        "https://resources.download.minecraft.net" to "https://bmclapi2.bangbang93.com/assets",
        "https://libraries.minecraft.net" to "https://bmclapi2.bangbang93.com/maven",
        "https://maven.minecraftforge.net" to "https://bmclapi2.bangbang93.com/maven",
        "https://files.minecraftforge.net/maven" to "https://bmclapi2.bangbang93.com/maven",
        "https://maven.fabricmc.net" to "https://bmclapi2.bangbang93.com/maven",
        "https://maven.neoforged.net" to "https://bmclapi2.bangbang93.com/maven",
        "https://maven.quiltmc.org" to "https://bmclapi2.bangbang93.com/maven"
    )

    private val MOJANG_TO_MCBBS = mapOf(
        "https://piston-data.mojang.com" to "https://download.mcbbs.net",
        "https://piston-meta.mojang.com" to "https://download.mcbbs.net",
        "https://launchermeta.mojang.com" to "https://download.mcbbs.net",
        "https://launcher.mojang.com" to "https://download.mcbbs.net",
        "https://resources.download.minecraft.net" to "https://download.mcbbs.net/assets",
        "https://libraries.minecraft.net" to "https://download.mcbbs.net/maven",
        "https://maven.minecraftforge.net" to "https://download.mcbbs.net/maven",
        "https://files.minecraftforge.net/maven" to "https://download.mcbbs.net/maven",
        "https://maven.fabricmc.net" to "https://download.mcbbs.net/maven",
        "https://maven.neoforged.net" to "https://download.mcbbs.net/maven",
        "https://maven.quiltmc.org" to "https://download.mcbbs.net/maven"
    )

    fun getCurrentSource(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DOWNLOAD_SOURCE, DEFAULT_SOURCE) ?: DEFAULT_SOURCE
    }

    fun setCurrentSource(context: Context, source: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DOWNLOAD_SOURCE, source).apply()
    }

    /**
     * 生成原始 URL 对应的多源列表，按下载源设置排序
     */
    fun getMirrorUrls(context: Context, originalUrl: String): List<String> {
        if (originalUrl.isBlank()) return listOf(originalUrl)

        val source = getCurrentSource(context)

        if (source == "mojang") return listOf(originalUrl)

        val bmclapiUrl = applyMirror(originalUrl, MOJANG_TO_BMCLAPI)
        val mcbbsUrl = applyMirror(originalUrl, MOJANG_TO_MCBBS)

        val mirrorUrls = mutableListOf<String>()
        if (bmclapiUrl != originalUrl) mirrorUrls.add(bmclapiUrl)
        if (mcbbsUrl != originalUrl && mcbbsUrl != bmclapiUrl) mirrorUrls.add(mcbbsUrl)

        if (mirrorUrls.isEmpty()) return listOf(originalUrl)

        return when (source) {
            "auto" -> mirrorUrls + listOf(originalUrl)
            "bmclapi" -> mirrorUrls + listOf(originalUrl)
            "official" -> listOf(originalUrl) + mirrorUrls
            else -> mirrorUrls + listOf(originalUrl)
        }
    }

    /**
     * 不依赖 Context 的重载，由调用方传入 source
     */
    fun getMirrorUrls(source: String, originalUrl: String): List<String> {
        if (originalUrl.isBlank()) return listOf(originalUrl)
        if (source == "mojang") return listOf(originalUrl)

        val bmclapiUrl = applyMirror(originalUrl, MOJANG_TO_BMCLAPI)
        val mcbbsUrl = applyMirror(originalUrl, MOJANG_TO_MCBBS)

        val mirrorUrls = mutableListOf<String>()
        if (bmclapiUrl != originalUrl) mirrorUrls.add(bmclapiUrl)
        if (mcbbsUrl != originalUrl && mcbbsUrl != bmclapiUrl) mirrorUrls.add(mcbbsUrl)

        if (mirrorUrls.isEmpty()) return listOf(originalUrl)

        return when (source) {
            "auto" -> mirrorUrls + listOf(originalUrl)
            "bmclapi" -> mirrorUrls + listOf(originalUrl)
            "official" -> listOf(originalUrl) + mirrorUrls
            else -> mirrorUrls + listOf(originalUrl)
        }
    }

    private fun applyMirror(url: String, mapping: Map<String, String>): String {
        var result = url
        for ((original, mirror) in mapping) {
            if (result.startsWith(original)) {
                result = result.replace(original, mirror)
                break
            }
        }
        return result
    }

    fun getSourceLabel(source: String): String = when (source) {
        "auto" -> "智能混合"
        "bmclapi" -> "BMCLAPI 镜像"
        "official" -> "官方源"
        "mojang" -> "Mojang"
        else -> source
    }
}
