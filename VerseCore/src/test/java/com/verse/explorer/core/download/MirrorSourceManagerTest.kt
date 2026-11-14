package com.verse.explorer.core.download

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * assets 候选源顺序回归测试。
 *
 * 源集合来自 ZL2 `BMCLAPI.mapBMCLMirrorUrls()`（官方 + BMCLAPI /assets）；
 * 顺序依据 2026-11-14 真实资源实测：官方 12/12 成功 / 1.68s，
 * BMCLAPI 2/12 成功 / 13.35s（302 到第三方节点后 502），故默认官方优先。
 */
class MirrorSourceManagerTest {

    private val official =
        "https://resources.download.minecraft.net/b6/b62ca8ec10d07e6bf5ac8dae0c8c1d2e6a1e3356"
    private val mirror =
        "https://bmclapi2.bangbang93.com/assets/b6/b62ca8ec10d07e6bf5ac8dae0c8c1d2e6a1e3356"

    @Test
    fun autoPrefersOfficialWithMirrorFallback() {
        assertEquals(listOf(official, mirror), MirrorSourceManager.getAssetCandidateUrls("auto", official))
    }

    @Test
    fun bmclapiKeepsZl2MirrorFirst() {
        assertEquals(listOf(mirror, official), MirrorSourceManager.getAssetCandidateUrls("bmclapi", official))
    }

    @Test
    fun officialAndMojangUseOfficialOnly() {
        assertEquals(listOf(official), MirrorSourceManager.getAssetCandidateUrls("official", official))
        assertEquals(listOf(official), MirrorSourceManager.getAssetCandidateUrls("mojang", official))
    }

    @Test
    fun unknownUrlHasNoMirrorCandidate() {
        val url = "https://example.com/some/file"
        assertEquals(listOf(url), MirrorSourceManager.getAssetCandidateUrls("auto", url))
        assertEquals(listOf(url), MirrorSourceManager.getAssetCandidateUrls("bmclapi", url))
    }

    @Test
    fun blankUrlIsNotMapped() {
        assertEquals(listOf(""), MirrorSourceManager.getAssetCandidateUrls("auto", ""))
    }
}
