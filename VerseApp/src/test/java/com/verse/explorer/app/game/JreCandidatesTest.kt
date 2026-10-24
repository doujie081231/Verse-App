package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * JRE 候选顺序测试。
 *
 * 这段顺序决定了启动时用哪个运行时：参数指定优先、同大版本已安装次之、
 * 安装器目标目录兜底。顺序错会导致「用户指定了 JRE 却被忽略」或
 * 「用错大版本启动失败」（如 Java 25 启动 1.12）。
 */
class JreCandidatesTest {

    private val target = "/data/app/files/jre/jre-17-arm64"

    @Test
    fun explicitOnly_explicitFirstThenTarget() {
        val plan = JreCandidates.plan(
            explicitHome = "/sdcard/custom-jre",
            installedForVersion = null,
            installTarget = target
        )
        assertEquals(listOf("/sdcard/custom-jre", target), plan)
    }

    @Test
    fun noExplicit_installedFirstThenTarget() {
        val plan = JreCandidates.plan(
            explicitHome = null,
            installedForVersion = "/data/app/files/jre/jre-17-arm64-other",
            installTarget = target
        )
        assertEquals(listOf("/data/app/files/jre/jre-17-arm64-other", target), plan)
    }

    @Test
    fun allThreeDistinct_keepFullOrder() {
        val plan = JreCandidates.plan(
            explicitHome = "/sdcard/custom-jre",
            installedForVersion = "/data/app/files/jre/jre-17-arm64-other",
            installTarget = target
        )
        assertEquals(
            listOf("/sdcard/custom-jre", "/data/app/files/jre/jre-17-arm64-other", target),
            plan
        )
    }

    @Test
    fun explicitEqualsInstalled_noDuplicate() {
        val plan = JreCandidates.plan(
            explicitHome = "/sdcard/custom-jre",
            installedForVersion = "/sdcard/custom-jre",
            installTarget = target
        )
        assertEquals(listOf("/sdcard/custom-jre", target), plan)
    }

    @Test
    fun everythingSame_singleEntry() {
        val plan = JreCandidates.plan(
            explicitHome = target,
            installedForVersion = target,
            installTarget = target
        )
        assertEquals(listOf(target), plan)
    }

    @Test
    fun blankExplicitIgnored() {
        // 空字符串等同于未指定，不能当成候选目录去校验
        val plan = JreCandidates.plan(
            explicitHome = "",
            installedForVersion = "/data/app/files/jre/jre-17-arm64-other",
            installTarget = target
        )
        assertEquals(listOf("/data/app/files/jre/jre-17-arm64-other", target), plan)
    }

    @Test
    fun installTargetOf_usesVersionAndArch() {
        val file = JreCandidates.installTargetOf(File("/data/app/files/jre"), 21, "arm64")
        assertEquals(
            File("/data/app/files/jre/jre-21-arm64").absolutePath,
            file.absolutePath
        )
    }
}
