package com.verse.explorer.app.crash

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染器缺失 GL 函数的判定（真机反馈 1.2.3：iQOO Neo11 + MobileGlues 进游戏闪退，
 * 崩溃报告为 java.lang.UnsatisfiedLinkError: 'void org.lwjgl.opengl.GL32C.nglDeleteSync(long)'）。
 * 这类缺失由渲染器插件决定，必须在报告里明确指出而不是笼统报「本地库加载失败」。
 */
class Crit3RendererGlTest {

    private fun analyze(crashLog: String) =
        Crit3.analyze(PreparedLogs(logCrash = crashLog))

    @Test
    fun lwjglGlMissing_isRendererIssue() {
        val report = """
            ---- Minecraft Crash Report ----
            Description: Unexpected error
            java.lang.UnsatisfiedLinkError: 'void org.lwjgl.opengl.GL32C.nglDeleteSync(long)'
                at org.lwjgl.opengl.GL32C.nglDeleteSync(Native Method)
        """.trimIndent()
        val reasons = analyze(report)
        assertTrue(reasons.containsKey(CrashReason.RendererMissingGlFunction))
    }

    @Test
    fun glFunctionNameCaptured() {
        val report = """
            java.lang.UnsatisfiedLinkError: 'void org.lwjgl.opengl.GL32C.nglDeleteSync(long)'
        """.trimIndent()
        val extra = analyze(report)[CrashReason.RendererMissingGlFunction]
        assertTrue(extra != null && extra.any { it.contains("nglDeleteSync") })
    }

    @Test
    fun otherUnsatisfiedLink_ignored() {
        // 普通本地库缺失不应被误判成渲染器问题
        val report = "java.lang.UnsatisfiedLinkError: dlopen failed: library \"libfoo.so\" not found"
        val reasons = analyze(report)
        assertTrue(!reasons.containsKey(CrashReason.RendererMissingGlFunction))
    }

    @Test
    fun missingGlWithoutLinkError_ignored() {
        val report = "org.lwjgl.opengl.GL32C.nglDeleteSync called but driver missing"
        val reasons = analyze(report)
        assertTrue(!reasons.containsKey(CrashReason.RendererMissingGlFunction))
    }
}
