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

    // MCBBS 镜像已移除：download.mcbbs.net 域名失效，DNS 解析实测挂起 11 秒，
    // 每个命中它的文件都要白等一轮系统 DNS 超时（镜像回退链 = [BMCLAPI, 官方]）

    /**
     * 资源下载 CDN 镜像（对齐 PC 端 to_mirror_url / to_curseforge_url）：
     *  - Modrinth CDN → mcimirror 国内镜像（host 替换，路径不变），官方 CDN 国内直连仅几十 KB/s
     *  - edge.forgecdn.net → mediafilez.forgecdn.net（同为 CurseForge 官方 CDN 节点）：
     *    edge 对带 Range 的请求直接返回 404，分块下载/测速探针全灭；mediafilez 支持 Range，
     *    换 host 后多源测速 + 分块下载才能生效。edge 原始 URL 保留为回退源（单流 302 跟随可下）。
     *  - -service.overwolf.wtf → .forgecdn.net（CurseForge 部分接口返回的镜像 host）
     */
    private val RESOURCE_MIRROR_PAIRS = listOf(
        "https://cdn.modrinth.com/" to "https://mod.mcimirror.top/",
        "https://cdn-alt.modrinth.com/" to "https://mod.mcimirror.top/",
        "https://edge.forgecdn.net/" to "https://mediafilez.forgecdn.net/",
        "-service.overwolf.wtf/" to ".forgecdn.net/"
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
        return getMirrorUrls(getCurrentSource(context), originalUrl)
    }

    /**
     * 不依赖 Context 的重载，由调用方传入 source
     */
    fun getMirrorUrls(source: String, originalUrl: String): List<String> {
        if (originalUrl.isBlank()) return listOf(originalUrl)
        if (source == "mojang") return listOf(originalUrl)

        val bmclapiUrl = applyMirror(originalUrl, MOJANG_TO_BMCLAPI)
        val resourceUrl = applyResourceMirror(originalUrl)

        val mirrorUrls = mutableListOf<String>()
        if (bmclapiUrl != originalUrl) mirrorUrls.add(bmclapiUrl)
        if (resourceUrl != originalUrl && resourceUrl !in mirrorUrls) mirrorUrls.add(resourceUrl)

        if (mirrorUrls.isEmpty()) return listOf(originalUrl)

        return when (source) {
            "official" -> listOf(originalUrl) + mirrorUrls
            else -> mirrorUrls + listOf(originalUrl)
        }
    }

    /** 资源 CDN 镜像：命中即返回替换后的 URL（各映射互斥，取首个命中） */
    private fun applyResourceMirror(url: String): String {
        for ((pattern, replacement) in RESOURCE_MIRROR_PAIRS) {
            if (url.contains(pattern)) {
                return url.replace(pattern, replacement)
            }
        }
        return url
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
