package com.verse.explorer.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 通用按钮与文字组件。
 */

/** 按下时缩放到 0.95 的按钮 */
@Composable
fun ScalingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "ButtonScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/** 单行跑马灯文字 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    modifier: Modifier = Modifier,
    text: String,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null
) {
    Text(
        modifier = modifier.basicMarquee(Int.MAX_VALUE),
        text = text,
        color = color,
        style = style,
        maxLines = 1,
        textAlign = textAlign
    )
}

/** 图标 + 文字按钮（矢量图标重载） */
@Composable
fun IconTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    iconSize: Dp = 24.dp,
    imageVector: ImageVector,
    text: String,
    contentDescription: String? = text,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    enabled: Boolean = true,
    onLongClick: () -> Unit = {},
) {
    BaseIconTextButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        icon = { modifier1 ->
            Icon(
                modifier = modifier1.size(iconSize),
                imageVector = imageVector,
                contentDescription = contentDescription
            )
        },
        text = text,
        style = style,
        enabled = enabled,
        onLongClick = onLongClick
    )
}

/** 图标 + 文字按钮（Painter 重载） */
@Composable
fun IconTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    iconSize: Dp = 24.dp,
    painter: Painter,
    text: String,
    contentDescription: String? = text,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    enabled: Boolean = true,
    onLongClick: () -> Unit = {},
) {
    BaseIconTextButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        icon = { modifier1 ->
            Icon(
                modifier = modifier1.size(iconSize),
                painter = painter,
                contentDescription = contentDescription
            )
        },
        text = text,
        style = style,
        enabled = enabled,
        onLongClick = onLongClick
    )
}

/** 图标 + 文字按钮基础实现 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BaseIconTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    icon: @Composable (Modifier) -> Unit,
    text: String,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    enabled: Boolean = true,
    onLongClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .clip(shape = shape)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(PaddingValues(horizontal = 8.dp, vertical = 4.dp))
            .alpha(if (enabled) 1f else DisabledAlpha),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon(Modifier.align(Alignment.CenterVertically))
        Text(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(end = 4.dp),
            text = text,
            style = style
        )
    }
}
