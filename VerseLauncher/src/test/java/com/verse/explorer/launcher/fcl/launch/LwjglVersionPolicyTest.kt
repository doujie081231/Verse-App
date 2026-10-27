package com.verse.explorer.launcher.fcl.launch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LWJGL 栈版本选择测试。
 *
 * 26.x 需要 3.4.1 SDL 链；旧版本继续用 3.3.3。低于 3.3.3 的 LWJGL2
 * 还需要保留 lwjglx 兼容层，否则旧版 MC 启动找不到显示/输入类。
 */
class LwjglVersionPolicyTest {

    @Test
    fun lwjgl341_uses341Stack() {
        assertEquals(
            LwjglVersionPolicy.Selection("3.4.1", useLwjglX = false),
            LwjglVersionPolicy.select("3.4.1")
        )
    }

    @Test
    fun lwjgl333_uses333Stack() {
        assertEquals(
            LwjglVersionPolicy.Selection("3.3.3", useLwjglX = false),
            LwjglVersionPolicy.select("3.3.3")
        )
    }

    @Test
    fun lwjgl320_uses333StackWithCompat() {
        assertEquals(
            LwjglVersionPolicy.Selection("3.3.3", useLwjglX = true),
            LwjglVersionPolicy.select("3.2.3")
        )
    }

    @Test
    fun lwjgl294_uses333StackWithCompat() {
        assertEquals(
            LwjglVersionPolicy.Selection("3.3.3", useLwjglX = true),
            LwjglVersionPolicy.select("2.9.4")
        )
    }

    @Test
    fun nullVersion_fallsBackTo333() {
        assertEquals(
            LwjglVersionPolicy.Selection("3.3.3", useLwjglX = false),
            LwjglVersionPolicy.select(null)
        )
    }
}
