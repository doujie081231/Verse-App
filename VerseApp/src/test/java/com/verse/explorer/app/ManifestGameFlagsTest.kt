package com.verse.explorer.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 清单契约测试。
 *
 * 手机把应用识别成"游戏"依赖 `appCategory` / `isGame` 这两个清单属性，
 * 它们一旦在合并清单或重构中被删掉，表现是"游戏空间/游戏加速里找不到本应用"，
 * 而这种问题在代码层完全看不出来。用一条廉价断言把契约钉住。
 */
class ManifestGameFlagsTest {

    private val manifestText: String by lazy {
        File("src/main/AndroidManifest.xml").readText()
    }

    @Test
    fun `清单声明为游戏应用`() {
        assertTrue("缺少 android:appCategory=\"game\"", manifestText.contains("android:appCategory=\"game\""))
        assertTrue("缺少 android:isGame=\"true\"", manifestText.contains("android:isGame=\"true\""))
        assertTrue(
            "缺少 android:resizeableActivity=\"true\"（与游戏空间的多窗口判定相关）",
            manifestText.contains("android:resizeableActivity=\"true\"")
        )
    }

    @Test
    fun `启动活动声明横屏`() {
        assertTrue(
            "启动活动应为 sensorLandscape，否则游戏助手按竖屏应用处理",
            manifestText.contains("android:screenOrientation=\"sensorLandscape\"")
        )
    }
}
