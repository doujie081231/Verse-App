package com.verse.explorer.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 滚动边缘渐隐（对位 ZL2 ui/components/_Scroll.kt 的 fadeEdge 部分）。
 *
 * 为可滚动组件增加边缘渐隐效果，直观提示用户该区域可滑动。
 * 依赖 graphicsLayer(compositingStrategy = Offscreen) 让混合模式只影响当前组件。
 */
@Composable
fun Modifier.fadeEdge(
    state: ScrollState,
    length: Dp = 12.dp,
    direction: EdgeDirection = EdgeDirection.Vertical,
    position: EdgeSide = EdgeSide.Both,
    style: FadeStyle = createDefaultFadeStyle(direction)
): Modifier {
    val fadePx = with(LocalDensity.current) { length.toPx() }

    val topDistance = state.value.toFloat()
    val startFade = if (state.canScrollBackward) {
        (topDistance / fadePx).coerceIn(0f, 1f) * fadePx
    } else 0f

    val bottomDistance = (state.maxValue - state.value).toFloat()
    val endFade = if (state.canScrollForward) {
        (bottomDistance / fadePx).coerceIn(0f, 1f) * fadePx
    } else 0f

    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawFadeEdges(startFade, endFade, direction, position, style)
        }
}

/** 滚动边缘渐隐（LazyListState 版本） */
@Composable
fun Modifier.fadeEdge(
    state: LazyListState,
    length: Dp = 12.dp,
    direction: EdgeDirection = EdgeDirection.Vertical,
    position: EdgeSide = EdgeSide.Both,
    style: FadeStyle = createDefaultFadeStyle(direction)
): Modifier {
    val fadePx = with(LocalDensity.current) { length.toPx() }
    val layoutInfo = state.layoutInfo

    val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
    val topDistancePx = when {
        firstItem == null -> 0f
        state.firstVisibleItemIndex == 0 -> -firstItem.offset.toFloat()
        else -> fadePx
    }
    val startFade = if (state.canScrollBackward) {
        (topDistancePx / fadePx).coerceIn(0f, 1f) * fadePx
    } else 0f

    val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val bottomDistancePx = when {
        lastItem == null -> 0f
        state.canScrollForward.not() -> 0f
        state.firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size == layoutInfo.totalItemsCount &&
                lastItem.offset + lastItem.size > viewportHeight ->
            (lastItem.offset + lastItem.size - viewportHeight).toFloat()
        else -> fadePx
    }
    val endFade = if (state.canScrollForward) {
        (bottomDistancePx / fadePx).coerceIn(0f, 1f) * fadePx
    } else 0f

    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawFadeEdges(startFade, endFade, direction, position, style)
        }
}

/** 统一渲染渐隐 */
private fun DrawScope.drawFadeEdges(
    startFade: Float,
    endFade: Float,
    direction: EdgeDirection,
    position: EdgeSide,
    style: FadeStyle
) {
    val totalSize = if (direction == EdgeDirection.Vertical) size.height else size.width

    if (position.includeStart && startFade > 0f) {
        drawFadeEdge(
            insetStart = 0f,
            insetEnd = totalSize - startFade,
            direction = direction,
            reverse = false,
            style = style
        )
    }

    if (position.includeEnd && endFade > 0f) {
        drawFadeEdge(
            insetStart = totalSize - endFade,
            insetEnd = 0f,
            direction = direction,
            reverse = true,
            style = style
        )
    }
}

private fun DrawScope.drawFadeEdge(
    insetStart: Float,
    insetEnd: Float,
    direction: EdgeDirection,
    reverse: Boolean,
    style: FadeStyle
) {
    val isVertical = direction == EdgeDirection.Vertical
    inset(
        left = if (!isVertical) insetStart else 0f,
        top = if (isVertical) insetStart else 0f,
        right = if (!isVertical) insetEnd else 0f,
        bottom = if (isVertical) insetEnd else 0f
    ) {
        rotate(if (reverse) 180f else 0f) {
            drawRect(brush = style.brush, blendMode = style.blendMode)
        }
    }
}

/** 渐隐方向 */
@Immutable
enum class EdgeDirection {
    Vertical, Horizontal
}

/** 渐隐所在侧 */
@Immutable
enum class EdgeSide(val includeStart: Boolean, val includeEnd: Boolean) {
    /** 上/左 */
    Start(true, false),

    /** 下/右 */
    End(false, true),

    /** 两边 */
    Both(true, true)
}

/** 渐隐样式 */
@Immutable
data class FadeStyle(
    val brush: Brush,
    val blendMode: BlendMode
)

/** 黑色 -> 透明的线性渐变，配合 BlendMode.DstOut 实现淡化 */
fun createDefaultFadeStyle(
    direction: EdgeDirection
): FadeStyle {
    val colorStops = arrayOf(
        0f to Color.Black,
        1f to Color.Transparent
    )

    return FadeStyle(
        brush = when (direction) {
            EdgeDirection.Vertical -> Brush.verticalGradient(*colorStops)
            EdgeDirection.Horizontal -> Brush.horizontalGradient(*colorStops)
        },
        blendMode = BlendMode.DstOut
    )
}
