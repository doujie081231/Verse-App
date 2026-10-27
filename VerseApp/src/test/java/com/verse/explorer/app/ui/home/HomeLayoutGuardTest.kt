package com.verse.explorer.app.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主页固定功能卡片的坏布局判定：
 * 设计布局是「头像 → 当前版本 → 启动」纵向三层，行区间互不重叠；
 * 三块排到同一排（真机反馈 1.2.3 主页所有 UI 排成一排）必须被判出来并还原。
 */
class HomeLayoutGuardTest {

    private fun band(top: Int, bottom: Int) = HomeLayoutGuard.RowBand(top, bottom)

    @Test
    fun verticalStack_healthy() {
        val bands = listOf(band(4, 10), band(12, 15), band(16, 19))
        assertFalse(HomeLayoutGuard.systemRowsOverlap(bands))
    }

    @Test
    fun sameRow_degenerate() {
        // 三块都在第 0 行（高度不同也算重叠）
        assertTrue(HomeLayoutGuard.systemRowsOverlap(listOf(band(0, 6), band(0, 3), band(0, 3))))
    }

    @Test
    fun partialOverlap_degenerate() {
        assertTrue(HomeLayoutGuard.systemRowsOverlap(listOf(band(2, 8), band(6, 9), band(10, 13))))
    }

    @Test
    fun missingCards_notJudged() {
        assertFalse(HomeLayoutGuard.systemRowsOverlap(listOf(band(0, 6))))
        assertFalse(HomeLayoutGuard.systemRowsOverlap(emptyList()))
    }
}
