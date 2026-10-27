package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 启动宽高选择测试。
 *
 * 优先级必须保持：版本级强制分辨率 > Surface 按百分比缩放 > 启动参数回退。
 * 顺序错会让「强制分辨率」失效或 Surface 未创建时窗口为 0。
 */
class LaunchSizePolicyTest {

    private fun input(
        usesGlobal: Boolean = false,
        forceResolution: Boolean = true,
        forceWidth: Int = 1920,
        forceHeight: Int = 1080,
        surfaceWidth: Int = 2400,
        surfaceHeight: Int = 1080,
        scaledWidth: Int = 1200,
        scaledHeight: Int = 540,
        fallbackWidth: Int = 854,
        fallbackHeight: Int = 480
    ) = LaunchSizePolicy.Input(
        usesGlobal = usesGlobal,
        forceResolution = forceResolution,
        forceWidth = forceWidth,
        forceHeight = forceHeight,
        surfaceWidth = surfaceWidth,
        surfaceHeight = surfaceHeight,
        scaledWidth = scaledWidth,
        scaledHeight = scaledHeight,
        fallbackWidth = fallbackWidth,
        fallbackHeight = fallbackHeight
    )

    @Test
    fun forceResolution_wins() {
        assertEquals(1920 to 1080, LaunchSizePolicy.resolve(input()))
    }

    @Test
    fun forceResolution_ignoredWhenGlobalSettings() {
        assertEquals(1200 to 540, LaunchSizePolicy.resolve(input(usesGlobal = true)))
    }

    @Test
    fun forceResolution_ignoredWhenSizeInvalid() {
        assertEquals(
            1200 to 540,
            LaunchSizePolicy.resolve(input(forceWidth = 0, forceHeight = 0))
        )
    }

    @Test
    fun surfaceScaledSize_usedWhenSurfaceReady() {
        assertEquals(
            1200 to 540,
            LaunchSizePolicy.resolve(input(forceResolution = false))
        )
    }

    @Test
    fun fallbackSize_usedBeforeSurfaceReady() {
        assertEquals(
            854 to 480,
            LaunchSizePolicy.resolve(
                input(forceResolution = false, surfaceWidth = 0, surfaceHeight = 0)
            )
        )
    }
}
