package com.tungsten.fcl.setting

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MenuSetting.resolveRenderSize 的表驱动覆盖：
 * 百分比缩放、光标偏移补偿、下限收敛。windowScale/cursorOffset 为 object 级
 * 可变状态，每个用例显式设置、@After 复位，避免用例间污染。
 */
class MenuSettingResolveRenderSizeTest {

    @After
    fun resetState() {
        MenuSetting.windowScale = 1.0
        MenuSetting.cursorOffset = 0.0
    }

    @Test
    fun hundredPercent_keepsScreenSize() {
        MenuSetting.windowScale = 1.0
        MenuSetting.cursorOffset = 0.0
        assertEquals(2400 to 1080, MenuSetting.resolveRenderSize(2400, 1080))
    }

    @Test
    fun quarterScale_roundsDown() {
        MenuSetting.windowScale = 0.25
        MenuSetting.cursorOffset = 0.0
        assertEquals(600 to 270, MenuSetting.resolveRenderSize(2400, 1080))
    }

    @Test
    fun tripleScale() {
        MenuSetting.windowScale = 3.0
        MenuSetting.cursorOffset = 0.0
        assertEquals(7200 to 3240, MenuSetting.resolveRenderSize(2400, 1080))
    }

    @Test
    fun cursorOffsetWidensWidth() {
        MenuSetting.windowScale = 1.0
        MenuSetting.cursorOffset = 20.0
        assertEquals(2420 to 1080, MenuSetting.resolveRenderSize(2400, 1080))
    }

    @Test
    fun tinyScreen_coercesToAtLeastOne() {
        MenuSetting.windowScale = 0.001
        MenuSetting.cursorOffset = 0.0
        val (w, h) = MenuSetting.resolveRenderSize(0, 0)
        assertEquals(1, w)
        assertEquals(1, h)
    }
}
