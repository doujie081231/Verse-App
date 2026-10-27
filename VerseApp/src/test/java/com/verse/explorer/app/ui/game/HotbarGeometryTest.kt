package com.verse.explorer.app.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 快捷栏触控层几何：尺寸来自「物品栏缩放」百分比，0 表示关闭；
 * 命中槽位与 FCL GameItemBar 的 9 等分一致。
 */
class HotbarGeometryTest {

    @Test
    fun bothZero_disabled() {
        assertNull(HotbarGeometry.rect(1000, 500, 0, 0))
    }

    @Test
    fun widthOnly_derivesHeightByRatio() {
        val rect = HotbarGeometry.rect(1000, 500, 45, 0)
        assertNotNull(rect)
        val r = rect!!
        assertEquals(450f, r.width, 0.01f)
        assertEquals(450f * 2f / 9f, r.height, 0.01f)
    }

    @Test
    fun heightOnly_derivesWidthByRatio() {
        val r = HotbarGeometry.rect(1000, 500, 0, 20)!!
        // 20% 屏高 = 100px，宽度按 9:2 推导 = 450px
        assertEquals(100f, r.height, 0.01f)
        assertEquals(450f, r.width, 0.01f)
    }

    @Test
    fun centeredAndBottomAligned() {
        val r = HotbarGeometry.rect(1000, 500, 50, 10)!!
        assertEquals(250f, r.left, 0.01f)
        assertEquals(750f, r.right, 0.01f)
        assertEquals(500f, r.bottom, 0.01f)
        assertEquals(450f, r.top, 0.01f)
    }

    @Test
    fun oversize_clampedToScreen() {
        val r = HotbarGeometry.rect(1000, 500, 100, 100)!!
        assertEquals(1000f, r.width, 0.01f)
        assertEquals(500f, r.height, 0.01f)
        assertEquals(0f, r.left, 0.01f)
    }

    @Test
    fun slotAt_nineEvenSlots() {
        val r = HotbarGeometry.rect(900, 500, 90, 10)!!
        // 宽 810、9 格每格 90px
        assertEquals(0, HotbarGeometry.slotAt(r, r.left + 1f, r.top + 1f))
        assertEquals(4, HotbarGeometry.slotAt(r, r.left + 90f * 4 + 45f, r.top + 5f))
        assertEquals(8, HotbarGeometry.slotAt(r, r.right - 1f, r.bottom - 1f))
    }

    @Test
    fun slotAt_outsideIsMinusOne() {
        val r = HotbarGeometry.rect(900, 500, 50, 10)!!
        assertEquals(-1, HotbarGeometry.slotAt(r, r.left - 5f, r.top + 5f))
        assertEquals(-1, HotbarGeometry.slotAt(r, r.left + 5f, r.top - 5f))
    }

    @Test
    fun invalidScreen_returnsNull() {
        assertNull(HotbarGeometry.rect(0, 500, 50, 10))
        assertNull(HotbarGeometry.rect(1000, 0, 50, 10))
    }
}
