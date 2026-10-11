package com.verse.explorer.cardgrid.model

import com.verse.explorer.cardgrid.model.CardLimits.Companion.DEFAULT


/**
 * 卡片的尺寸边界（单元格跨度），由卡片类型自行声明，
 * 未声明的卡片使用 [DEFAULT] 默认值。
 */
data class CardLimits(
    val minWidth: Int = DEFAULT_MIN_SPAN,
    val minHeight: Int = DEFAULT_MIN_SPAN,
    val maxWidth: Int = Int.MAX_VALUE,
    val maxHeight: Int = Int.MAX_VALUE
) {
    /**
     * 依据实际网格宽度归一化边界：
     * 最大跨度不超过网格，最小跨度不超过最大跨度。
     */
    fun clampedFor(columns: Int): CardLimits {
        val maxW = maxWidth.coerceAtMost(columns)
        val minW = minWidth.coerceAtMost(maxW)
        val maxH = maxHeight
        val minH = minHeight.coerceAtMost(maxH)
        return CardLimits(minWidth = minW, minHeight = minH, maxWidth = maxW, maxHeight = maxH)
    }

    fun clampWidth(width: Int): Int = width.coerceIn(minWidth, maxWidth)

    fun clampHeight(height: Int): Int = height.coerceIn(minHeight, maxHeight)

    companion object {
        /** 默认最小跨度：4×4（约 80dp） */
        const val DEFAULT_MIN_SPAN = 4

        /** 默认最大高度跨度：12 行（约 240dp） */
        const val DEFAULT_MAX_HEIGHT = 12

        val DEFAULT = CardLimits(maxHeight = DEFAULT_MAX_HEIGHT)
    }
}