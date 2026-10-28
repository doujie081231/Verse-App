package com.verse.explorer.app.ui.home

import com.verse.explorer.cardgrid.engine.GridEngine
import com.verse.explorer.cardgrid.model.CardRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列数变化时的位置保持约束。
 *
 * 主页在「切换忽略刘海屏」等操作后可用宽度会变、列数随之变化，
 * 旧实现用 [GridEngine.reflow] 按阅读顺序从左上角重新打包，整页卡片会被压到最上面；
 * 现在改用 [GridEngine.validate]（越界钳制 + 就近避让），位置必须保留。
 */
class GridReflowKeepPositionTest {

    private fun rect(id: String, x: Int, y: Int, w: Int, h: Int) =
        CardRect(id = id, x = x, y = y, width = w, height = h)

    @Test
    fun `validate 保留纵向位置 不会压到顶部`() {
        val cards = listOf(
            rect("a", 0, 0, 4, 4),
            rect("b", 4, 0, 4, 4),
            rect("c", 0, 12, 6, 4)
        )
        val settled = GridEngine.validate(cards, columns = 12).associateBy { it.id }
        // 第三张卡原本在 12 行，不能被"从左上角重新打包"推到 4 行
        assertEquals(12, settled.getValue("c").y)
    }

    @Test
    fun `列数变少时越界卡片被钳制在网格内且纵向位置不变`() {
        val cards = listOf(
            rect("a", 0, 0, 4, 4),
            rect("wide", 8, 20, 4, 3)
        )
        val settled = GridEngine.validate(cards, columns = 6).associateBy { it.id }
        val wide = settled.getValue("wide")
        assertEquals(20, wide.y)
        assertTrue("右侧越界必须被钳制：x=${wide.x}, w=${wide.width}", wide.x + wide.width <= 6)
        assertTrue(wide.x >= 0)
    }

    @Test
    fun `位置重叠时就近避让 且不越界`() {
        val cards = listOf(
            rect("a", 0, 0, 6, 3),
            rect("b", 0, 0, 6, 3)
        )
        val settled = GridEngine.validate(cards, columns = 6)
        assertEquals(2, settled.size)
        assertFalse("结果不允许重叠", GridEngine.hasOverlap(settled))
        settled.forEach {
            assertTrue(it.x >= 0 && it.x + it.width <= 6)
            assertTrue(it.y >= 0)
        }
    }

    @Test
    fun `单卡片在窄列数下跨度被钳制但保持行号`() {
        val cards = listOf(rect("account", 0, 6, 6, 6))
        val settled = GridEngine.validate(cards, columns = 3).single()
        assertEquals(6, settled.y)
        assertEquals(3, settled.width)
    }
}
