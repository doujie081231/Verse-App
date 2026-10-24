package com.verse.explorer.cardgrid.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 卡片与用户的交互状态 */
enum class CardInteraction { Idle, Adjusting, Dragging, Resizing }

/**
 * 提供给卡片内容的自身状态，
 * 卡片依据尺寸形态与交互状态切换不同的显示形态。
 */
class CardState(
    val spanWidth: Int,
    val spanHeight: Int,
    val columns: Int,
    val interaction: CardInteraction
) {
    /** 依据跨度推导的形态分类 */
    val sizeClass: CardSize = deriveSizeClass(spanWidth, spanHeight, columns)
}

/** 卡片矩形在单元格内向四边的内缩量 */
val CardSpacing: Dp = 6.dp