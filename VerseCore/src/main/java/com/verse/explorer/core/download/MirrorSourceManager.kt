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

    // BMCLAPI 的 /maven/ 对部分旧版官方库未收录（实测 gson-2.8.9 / asm-9.6 等返回 403），
    // 未收录的库只能退官方源直连，手机网络下极慢 → 库阶段整批卡住。
    // 阿里云 Maven（代理 Central）与 BMCLAPI 收录互补：官方 libraries 域名的库
    // 全部发布在 Central，403 时由阿里云补位（实测 gson/asm/icu4j/guava 均秒回）。
    private val LIBRARIES_TO_ALIYUN = mapOf(
        "https://libraries.minecraft.net" to "https://maven.aliyun.com/repository/public"
    )

    /**
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
        // BMCLAPI 会把文件 302 到各地 CDN 节点，个别节点在部分运营商网络下不可达
        // （实测 26.3 的 jtracy 在移动网络下跳转到境外节点后连接超时，
        //  官方源又极慢，导致"所有镜像均不可用"）。
        // bmclapi.bangbang93.com 与 bmclapi2 是不同入口，会分配到不同节点，
        // 作为同一文件的备用候选——任一入口给出可达节点即可下完。
        val bmclapiAltUrl = bmclapiUrl.replace(
            "https://bmclapi2.bangbang93.com",
            "https://bmclapi.bangbang93.com"
        )
        val aliyunUrl = applyMirror(originalUrl, LIBRARIES_TO_ALIYUN)
        val resourceUrl = applyResourceMirror(originalUrl)

        val mirrorUrls = mutableListOf<String>()
        if (bmclapiUrl != originalUrl) mirrorUrls.add(bmclapiUrl)
        if (bmclapiAltUrl != bmclapiUrl && bmclapiAltUrl !in mirrorUrls) mirrorUrls.add(bmclapiAltUrl)
        if (aliyunUrl != originalUrl && aliyunUrl !in mirrorUrls) mirrorUrls.add(aliyunUrl)
        if (resourceUrl != originalUrl && resourceUrl !in mirrorUrls) mirrorUrls.add(resourceUrl)

        if (mirrorUrls.isEmpty()) return listOf(originalUrl)

        return when (source) {
            "official" -> listOf(originalUrl) + mirrorUrls
            else -> mirrorUrls + listOf(originalUrl)
        }
    }

    /**
     * 资源文件（assets objects）专用候选源。
     *
     * 源集合与 ZL2 完全一致：官方 `resources.download.minecraft.net` 与
     * `BMCLAPI.ASSETS`（`https://bmclapi2.bangbang93.com/assets`），
     * 见 ZL2 `BMCLAPI.mapBMCLMirrorUrls()` / `BaseMinecraftDownloader.loadAssetsDownload()`。
     * 顺序沿用 ZL2 `_MirrorPreferences.orderCandidates()` 的两种输出，并按实测可用性排布：
     *  - official / mojang：`[官方]`（ZL2 `MirrorPriority.OFFICIAL`）
     *  - bmclapi：`[BMCLAPI, 官方]`（ZL2 `MirrorPriority.MIRROR_FIRST`，用户显式选镜像）
     *  - auto（默认）：`[官方, BMCLAPI]` —— 官方优先、镜像兜底
     *
     * 为什么 auto 不照搬 ZL2 大陆默认的「镜像优先」：BMCLAPI 的 `/assets/` 会把每个请求
     * 302 到第三方节点（*.hurtmc.com / *.sakura.sld.tw / open-bmcl-api.lunar.cc）。
     * 2026-11-14 用本地 asset index 的真实 hash 实测（12 并发）：
     *  - 官方：12/12 成功，总耗时 1.68s，SHA-1 全部匹配，无重定向
     *  - BMCLAPI：仅 2/12 成功，总耗时 13.35s，其余 502（多数要 11.26s 才超时）
     * 「镜像优先」下每个资源要先烧完 5 次失败重试才轮到官方，64 路窗口被全占住，
     * 这正是资源阶段只有几百 KB/s 的原因 —— 与并发数、缓冲区、SHA-1 校验都无关。
     * `[官方, 镜像]` 与 `getMirrorUrls()` 的 official 档同语义，不是新增策略。
     *
     * ZL2 对单个资源只给 2 个候选，不注入第三镜像入口、不做测速探测；
     * 下载引擎按列表顺序下载，首个成功即止，失败才换源。
     */
    fun getAssetCandidateUrls(context: Context, originalUrl: String): List<String> {
        if (originalUrl.isBlank()) return listOf(originalUrl)
        return getAssetCandidateUrls(getCurrentSource(context), originalUrl)
    }

    fun getAssetCandidateUrls(source: String, originalUrl: String): List<String> {
        if (originalUrl.isBlank()) return listOf(originalUrl)
        if (source == "official" || source == "mojang") return listOf(originalUrl)
        val mirror = applyMirror(originalUrl, MOJANG_TO_BMCLAPI)
        if (mirror == originalUrl) return listOf(originalUrl)
        return if (source == "bmclapi") listOf(mirror, originalUrl) else listOf(originalUrl, mirror)
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
