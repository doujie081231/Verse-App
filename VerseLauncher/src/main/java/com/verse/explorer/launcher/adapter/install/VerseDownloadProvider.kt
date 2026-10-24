package com.verse.explorer.launcher.adapter.install

import com.tungsten.fclcore.download.BMCLAPIDownloadProvider
import com.tungsten.fclcore.download.DownloadProvider
import com.tungsten.fclcore.download.MojangDownloadProvider
import com.tungsten.fclcore.download.VersionList
import com.tungsten.fclcore.download.game.GameVersionList
import com.tungsten.fclcore.util.io.NetworkUtils
import java.net.URL
import java.util.LinkedHashSet

/**
 * - 版本清单：piston-meta version_manifest_v2.json 直连（0 跳转、签名 URL 直接 200，
 *   规避 BMCLAPI 302 → 签名 CDN 边缘节点 404 的不稳定问题）
 *   FileDownloadTask 按序回退（对国内网络最稳）
 * - 加载器版本列表（fabric/forge/neoforge/liteloader/optifine）：走 BMCLAPI；
 *   quilt/quilt-api/fabric-api/cleanroom：走官方
 */
class VerseDownloadProvider(
    private val apiRoot: String = "https://bmclapi2.bangbang93.com"
) : DownloadProvider {

    private val bmcl = BMCLAPIDownloadProvider(apiRoot)
    private val mojang = MojangDownloadProvider()
    private val game = GameVersionList(this)

    override fun getVersionListURL(): String = bmcl.getVersionListURL()

    override fun getAssetBaseURL(): String = apiRoot + "/assets/"

    override fun getVersionListById(id: String): VersionList<*> = when (id) {
        "game" -> game
        "quilt", "quilt-api", "fabric-api", "cleanroom" -> mojang.getVersionListById(id)
        else -> bmcl.getVersionListById(id)
    }

    override fun injectURL(baseURL: String): String = bmcl.injectURL(baseURL)

    /** 多候选源：官方直连 → 官方归一化 → BMCLAPI 镜像 */
    override fun injectURLWithCandidates(baseURL: String): List<URL> = candidates(baseURL)

    override fun injectURLsWithCandidates(urls: List<String>): List<URL> =
        urls.flatMap { candidates(it) }

    override fun getAssetObjectCandidates(location: String): List<URL> {
        val set = LinkedHashSet<URL>()
        try {
            set.add(NetworkUtils.toURL("https://resources.download.minecraft.net/$location"))
        } catch (_: Exception) {
        }
        try {
            set.add(NetworkUtils.toURL(apiRoot + "/assets/" + location))
        } catch (_: Exception) {
        }
        return set.toList()
    }

    override fun getConcurrency(): Int = bmcl.getConcurrency()

    private fun candidates(baseURL: String): List<URL> {
        val set = LinkedHashSet<URL>()
        try {
            set.add(NetworkUtils.toURL(baseURL))
        } catch (_: Exception) {
        }
        try {
            set.add(NetworkUtils.toURL(mojang.injectURL(baseURL)))
        } catch (_: Exception) {
        }
        try {
            set.add(NetworkUtils.toURL(bmcl.injectURL(baseURL)))
        } catch (_: Exception) {
        }
        return set.toList()
    }
}
