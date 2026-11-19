package com.verse.explorer.app.horizon

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Horizon（Rust 版 Minecraft Java 26.3）安卓移植的目录数据。
 *
 * 官方 horizon-rust/horizon 只有桌面构建；安卓能用的是两个社区移植，形态是独立 APK：
 * - Horizon Android（Rayan-XOO/horizon）：通用单 APK
 * - Volt Launcher（entitybrian69-bit/Volt-launcher）：按 ABI 分包
 *
 * 移植版自带启动器，首次启动在应用内从 Mojang 官方端点下载约 500MB 资源（哈希校验），
 * 存档与账号都在它自己的私有目录——启动器只负责「下载 APK → 触发安装 → 拉起」，不做进程内整合。
 */
enum class HorizonAssetMode { UNIVERSAL, BY_ABI }

data class HorizonSourceDef(
    val label: String,
    val repo: String,
    val mode: HorizonAssetMode
)

data class HorizonRelease(
    val sourceLabel: String,
    val repo: String,
    val tag: String,
    val versionName: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: String,
    val notesUrl: String,
    /** 实验性构建（32 位 / 预发布）：默认列表置灰，不作为推荐 */
    val experimental: Boolean
)

object HorizonCatalog {

    val sources = listOf(
        HorizonSourceDef("Horizon Android", "Rayan-XOO/horizon", HorizonAssetMode.UNIVERSAL),
        HorizonSourceDef("Volt", "entitybrian69-bit/Volt-launcher", HorizonAssetMode.BY_ABI)
    )

    /** 版本页里「Rust 版」页签的标题 */
    const val TAB_LABEL = "Rust 版"

    /** 官方桌面版下载页（详情页展示用） */
    const val OFFICIAL_SITE_URL = "https://playhorizon.dev/"

    /** 设备 ABI 的推荐顺序：64 位 ARM 优先，其余按兼容度排 */
    private val ABI_PRIORITY = listOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86")

    fun primaryAbi(supportedAbis: List<String>): String =
        ABI_PRIORITY.firstOrNull { supportedAbis.contains(it) }
            ?: supportedAbis.firstOrNull()
            ?: "arm64-v8a"

    /**
     * 从 GitHub releases JSON 数组解析出可安装的版本。
     *
     * 选包规则：
     * - UNIVERSAL：取第一个 .apk 资产
     * - BY_ABI：取文件名包含设备 ABI 的 .apk；没有匹配时按优先级回退并标记实验性
     * - 跳过没有可用 APK 的 release；prerelease 与非 arm64 包标记实验性
     */
    fun parseReleases(
        source: HorizonSourceDef,
        releasesJson: JsonArray,
        deviceAbi: String
    ): List<HorizonRelease> {
        val releases = mutableListOf<HorizonRelease>()
        for (element in releasesJson) {
            val release = element as? JsonObject ?: continue
            val tag = release.asString("tag_name") ?: continue
            val assets = release.getAsJsonArray("assets") ?: continue
            val asset = pickAsset(source.mode, assets, deviceAbi) ?: continue
            val experimental = release.asBoolean("prerelease") ||
                (source.mode == HorizonAssetMode.BY_ABI && deviceAbi != "arm64-v8a")
            releases.add(
                HorizonRelease(
                    sourceLabel = source.label,
                    repo = source.repo,
                    tag = tag,
                    versionName = tag.removePrefix("android-").removePrefix("v"),
                    apkName = asset.asString("name") ?: continue,
                    apkUrl = asset.asString("browser_download_url") ?: continue,
                    apkSize = asset.asLong("size"),
                    publishedAt = (release.asString("published_at") ?: "").take(10),
                    notesUrl = release.asString("html_url") ?: "https://github.com/${source.repo}/releases",
                    experimental = experimental
                )
            )
        }
        return releases
    }

    private fun pickAsset(mode: HorizonAssetMode, assets: JsonArray, deviceAbi: String): JsonObject? {
        val apks = assets.mapNotNull { it as? JsonObject }
            .filter { (it.asString("name") ?: "").endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        return when (mode) {
            HorizonAssetMode.UNIVERSAL -> apks.firstOrNull()
            HorizonAssetMode.BY_ABI -> apks.firstOrNull { (it.asString("name") ?: "").contains(deviceAbi) }
                ?: apks.firstOrNull { (it.asString("name") ?: "").contains("arm64-v8a") }
        }
    }

    private fun JsonObject.asString(key: String): String? =
        (get(key) as? com.google.gson.JsonPrimitive)?.takeIf { it.isString }?.asString

    private fun JsonObject.asBoolean(key: String): Boolean =
        (get(key) as? com.google.gson.JsonPrimitive)?.asBoolean ?: false

    private fun JsonObject.asLong(key: String): Long =
        (get(key) as? com.google.gson.JsonPrimitive)?.asLong ?: 0L
}
