package com.verse.explorer.app.horizon

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Horizon 目录解析与选包测试。
 *
 * 两个移植仓库的资产形态不同（Rayan-XOO 单通用包、Volt 按 ABI 分包），
 * 选错包会装上跑不起来的架构；测试用真实 Releases 的字段结构做样例。
 */
class HorizonCatalogTest {

    private fun releaseJson(tag: String, assets: String, prerelease: Boolean = false): String =
        """[{"tag_name":"$tag","prerelease":$prerelease,"published_at":"2026-11-14T10:00:00Z",
           "html_url":"https://github.com/x/releases/tag/$tag",
           "assets":[$assets]}]""".trimIndent().replace("\n", "")

    private fun asset(name: String, size: Long): String =
        """{"name":"$name","size":$size,"browser_download_url":"https://github.com/x/releases/download/$name"}"""

    @Test
    fun `通用包解析出单 APK`() {
        val json = JsonParser.parseString(
            releaseJson("android-v0.4.7", asset("horizon-0.4.7.apk", 65_600_000))
        ).asJsonArray
        val source = HorizonCatalog.sources.first { it.repo == "Rayan-XOO/horizon" }

        val releases = HorizonCatalog.parseReleases(source, json, "arm64-v8a")

        assertEquals(1, releases.size)
        val release = releases[0]
        assertEquals("android-v0.4.7", release.tag)
        assertEquals("0.4.7", release.versionName)
        assertEquals("horizon-0.4.7.apk", release.apkName)
        assertEquals(65_600_000L, release.apkSize)
        assertFalse(release.experimental)
    }

    @Test
    fun `按 ABI 分包时选中设备架构`() {
        val json = JsonParser.parseString(
            releaseJson(
                "v1.1.1",
                listOf("Volt-Launcher-1.1.1-arm64-v8a.apk", "Volt-Launcher-1.1.1-x86_64.apk")
                    .joinToString(",") { asset(it, 73_400_000) }
            )
        ).asJsonArray
        val source = HorizonCatalog.sources.first { it.repo == "entitybrian69-bit/Volt-launcher" }

        val releases = HorizonCatalog.parseReleases(source, json, "arm64-v8a")

        assertEquals("Volt-Launcher-1.1.1-arm64-v8a.apk", releases[0].apkName)
        assertFalse(releases[0].experimental)
    }

    @Test
    fun `32 位设备回退到对应架构并标记实验性`() {
        val json = JsonParser.parseString(
            releaseJson("v1.1.1", asset("Volt-Launcher-1.1.1-armeabi-v7a.apk", 51_500_000))
        ).asJsonArray
        val source = HorizonCatalog.sources.first { it.repo == "entitybrian69-bit/Volt-launcher" }

        val releases = HorizonCatalog.parseReleases(source, json, "armeabi-v7a")

        assertEquals("Volt-Launcher-1.1.1-armeabi-v7a.apk", releases[0].apkName)
        assertTrue("非 arm64 构建必须标记实验性", releases[0].experimental)
    }

    @Test
    fun `预发布标记为实验性`() {
        val json = JsonParser.parseString(
            releaseJson("android-v0.5.0-rc1", asset("horizon-0.5.0-rc1.apk", 66_000_000), prerelease = true)
        ).asJsonArray
        val source = HorizonCatalog.sources.first { it.repo == "Rayan-XOO/horizon" }

        val releases = HorizonCatalog.parseReleases(source, json, "arm64-v8a")

        assertTrue(releases[0].experimental)
    }

    @Test
    fun `没有 APK 资产的发布被跳过`() {
        val json = JsonParser.parseString(releaseJson("source-only", asset("README.md", 100)))
            .asJsonArray
        val source = HorizonCatalog.sources.first { it.repo == "Rayan-XOO/horizon" }

        assertTrue(HorizonCatalog.parseReleases(source, json, "arm64-v8a").isEmpty())
    }

    @Test
    fun `设备 ABI 按优先级选择`() {
        assertEquals(
            "arm64-v8a",
            HorizonCatalog.primaryAbi(listOf("armeabi-v7a", "arm64-v8a", "x86"))
        )
        assertEquals(
            "未知架构兜底到 arm64（绝大多数现代手机）",
            "arm64-v8a",
            HorizonCatalog.primaryAbi(listOf())
        )
    }
}
