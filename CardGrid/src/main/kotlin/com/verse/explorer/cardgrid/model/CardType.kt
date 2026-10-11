package com.verse.explorer.cardgrid.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntOffset

/** 卡片内容的 composable 类型，参数为卡片自身 id */
typealias CardContent = @Composable CardState.(cardId: String) -> Unit

/**
 * 用户卡片的类型声明，未声明形状时使用主题默认形状。
 * @param defaultSpan 默认跨度
 * @param limits 尺寸边界限制
 * @param shape 形状
 * @param content 该卡片的 UI 内容
 */
class CardType(
    val typeId: String,
    val defaultSpan: IntOffset,
    val limits: CardLimits = CardLimits.DEFAULT,
    val shape: Shape? = null,
    val content: CardContent
)