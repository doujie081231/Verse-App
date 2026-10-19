package com.verse.explorer.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.backgroundGlass
import com.verse.explorer.app.ui.theme.itemColor
import com.verse.explorer.app.ui.theme.onItemColor

/**
 * 通用布局组件（对位 ZL2 ui/components/Layouts.kt）。
 */

/** 缩放标签：出现时从 0.95 放大到 1 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScalingLabel(
    modifier: Modifier = Modifier,
    text: String,
    influencedByBackground: Boolean = true,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    color: Color = itemColor(influencedByBackground),
    contentColor: Color = onItemColor(),
    blur: Int = VersePersonalization.backgroundBlur,
) {
    val scale = remember { Animatable(initialValue = 0.95f) }
    LaunchedEffect(Unit) {
        scale.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 200))
    }
    Surface(
        modifier = modifier.graphicsLayer(scaleY = scale.value, scaleX = scale.value),
        shape = shape,
        color = color,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .backgroundGlass(blur, color, influencedByBackground)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = text)
        }
    }
}

/** 可点击的缩放标签 */
@Composable
fun ScalingLabel(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    influencedByBackground: Boolean = true,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    color: Color = itemColor(influencedByBackground = influencedByBackground),
    contentColor: Color = onItemColor(),
) {
    ScalingLabel(
        modifier = modifier,
        onClick = onClick,
        text = {
            Text(text = text)
        },
        influencedByBackground = influencedByBackground,
        shape = shape,
        color = color,
        contentColor = contentColor,
    )
}

/** 可点击的缩放标签（自定义行内容） */
@Composable
fun ScalingLabel(
    modifier: Modifier = Modifier,
    text: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    influencedByBackground: Boolean = true,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    color: Color = itemColor(influencedByBackground = influencedByBackground),
    contentColor: Color = onItemColor(),
) {
    val scale = remember { Animatable(initialValue = 0.95f) }
    LaunchedEffect(Unit) {
        scale.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 200))
    }
    Surface(
        modifier = modifier.graphicsLayer(scaleY = scale.value, scaleX = scale.value),
        shape = shape,
        color = color,
        contentColor = contentColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            content = text,
        )
    }
}

/** 小文字标签（药丸形，单行时跑马灯） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LittleTextLabel(
    modifier: Modifier = Modifier,
    text: String,
    singleLine: Boolean = true,
    color: Color = MaterialTheme.colorScheme.tertiary,
    contentColor: Color = MaterialTheme.colorScheme.onTertiary,
    shape: Shape = MaterialTheme.shapes.large,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium
) {
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        shape = shape
    ) {
        Text(
            modifier = Modifier
                .then(
                    if (singleLine) Modifier.basicMarquee(Int.MAX_VALUE)
                    else Modifier
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            text = text,
            style = textStyle,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE
        )
    }
}

/** 单选条目（对位 ZL2 SimpleListItem 单选重载） */
@Composable
fun SimpleListItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    itemName: String,
    summary: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.large)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = itemName,
                style = MaterialTheme.typography.labelMedium
            )
            summary?.invoke()
        }
    }
}

/** 多选条目（对位 ZL2 SimpleListItem 复选重载） */
@Composable
fun SimpleListItem(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    itemName: String,
    summary: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.large)
            .clickable(enabled = enabled) {
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = itemName,
                style = MaterialTheme.typography.labelMedium
            )
            summary?.invoke()
        }
    }
}

/** 带 id 的条目模型 */
data class IDItem(val id: String, val title: String)

/** 标题 + 摘要两行文字 */
@Composable
fun TitleAndSummary(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    summaryStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = titleStyle
        )
        summary?.let { text ->
            Text(
                modifier = Modifier.alpha(0.7f),
                text = text,
                style = summaryStyle
            )
        }
    }
}

/** 可请求焦点的空 Box */
@Composable
fun FocusableBox(
    modifier: Modifier = Modifier,
    requestKey: Any? = null,
    canRequestFocus: () -> Boolean = { true }
) {
    val focusRequester = remember { FocusRequester() }
    val currentCanRequestFocus by rememberUpdatedState(canRequestFocus)

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable(enabled = true)
    )

    LaunchedEffect(requestKey) {
        if (currentCanRequestFocus()) {
            focusRequester.requestFocus()
        }
    }
}
