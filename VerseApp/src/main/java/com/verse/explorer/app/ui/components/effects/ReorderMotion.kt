package com.verse.explorer.app.ui.components.effects

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * 拖拽排序条目的「抬起 / 落下」动效。
 *
 * 拖起时轻微放大、浮起阴影并抬到同一列表其它条目之上；松手后弹性回位。
 * 所有逐帧变化都写在 graphicsLayer 的延迟 lambda 里，只让图层失效重绘，
 * 不会因此重组条目内容（版本条目里有菜单、图片、跑马灯，重组代价高）。
 *
 * 用法：在 ReorderableItem 的 content 里把 isDragging 传进来，
 * 加在条目根 Modifier 上即可，与 reorderable 自身的位移动画叠加不冲突。
 */
@Composable
fun Modifier.verseReorderMotion(isDragging: Boolean): Modifier {
    val motionSpec = spring<Float>(dampingRatio = 0.82f, stiffness = 520f)
    val dpSpec = spring<Dp>(dampingRatio = 0.85f, stiffness = 520f)

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = motionSpec,
        label = "reorderScale"
    )
    val itemAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.94f else 1f,
        animationSpec = motionSpec,
        label = "reorderAlpha"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 0.dp,
        animationSpec = dpSpec,
        label = "reorderElevation"
    )

    return this
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = itemAlpha
            shadowElevation = elevation.toPx()
            shape = ReorderItemShape
            clip = false
        }
}

private val ReorderItemShape = RoundedCornerShape(16.dp)
