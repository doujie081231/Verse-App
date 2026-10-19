package com.verse.explorer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 导航栏 item 组件（对位 ZL2 ui/components/_SimpleRail.kt 的 TextRailItem）。
 *
 * 可包含图标与文字。当 item 被选中时，会触发一个从中心向外扩展的胶囊形背景动画。
 */
@Composable
fun TextRailItem(
    modifier: Modifier = Modifier,
    text: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    icon: @Composable RowScope.() -> Unit = {},
    selected: Boolean,
    selectedPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    unSelectedPadding: PaddingValues = selectedPadding,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    backgroundColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true
) {
    val animationProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "SelectionAnimation"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else DisabledAlpha)
    ) {
        // 背景扩散动画
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .alpha(animationProgress)
        ) {
            val maxWidth = size.width
            val minWidth = 0f
            val currentWidth = minWidth + (maxWidth - minWidth) * animationProgress

            val left = (maxWidth - currentWidth) / 2

            // 绘制胶囊形状背景
            drawRoundRect(
                color = backgroundColor,
                topLeft = Offset(left, 0f),
                size = Size(currentWidth, size.height),
                cornerRadius = CornerRadius(size.height / 2, size.height / 2)
            )
        }

        val paddingLeft by animateDpAsState(
            if (selected) selectedPadding.calculateLeftPadding(LayoutDirection.Ltr) else unSelectedPadding.calculateLeftPadding(LayoutDirection.Ltr)
        )
        val paddingRight by animateDpAsState(
            if (selected) selectedPadding.calculateRightPadding(LayoutDirection.Ltr) else unSelectedPadding.calculateRightPadding(LayoutDirection.Ltr)
        )
        val paddingTop by animateDpAsState(
            if (selected) selectedPadding.calculateTopPadding() else unSelectedPadding.calculateTopPadding()
        )
        val paddingBottom by animateDpAsState(
            if (selected) selectedPadding.calculateBottomPadding() else unSelectedPadding.calculateBottomPadding()
        )

        Row(
            modifier = Modifier.padding(
                start = paddingLeft,
                end = paddingRight,
                top = paddingTop,
                bottom = paddingBottom
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val contentColor by animateColorAsState(
                targetValue = if (selected) selectedContentColor else unselectedContentColor
            )
            CompositionLocalProvider(
                LocalContentColor provides contentColor
            ) {
                icon()
                text()
            }
        }
    }
}
