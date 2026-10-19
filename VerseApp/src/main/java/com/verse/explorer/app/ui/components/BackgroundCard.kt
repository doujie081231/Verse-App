package com.verse.explorer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.backgroundGlass
import com.verse.explorer.app.ui.influencedByBackgroundColor
import com.verse.explorer.app.ui.theme.cardColor
import com.verse.explorer.app.ui.theme.cardTitleColor
import com.verse.explorer.app.ui.theme.onCardColor

/**
 * 背景卡片组件（对位 ZL2 ui/components/BackgroundCard.kt）。
 *
 * 使用方式与原本的 [Card] 无异，但 [BackgroundCard] 配置了更舒适的背景颜色：
 * 容器色取 cardColor（受背景内容不透明度影响），并在内容层叠加背景毛玻璃。
 */
@Composable
fun BackgroundCard(
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = cardColor(influencedByBackground),
        contentColor = onCardColor()
    ),
    elevation: CardElevation = CardDefaults.cardElevation(),
    blur: Int = VersePersonalization.backgroundBlur,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
    ) {
        Column(
            modifier = Modifier.backgroundGlass(blur, colors.containerColor, influencedByBackground),
            content = content
        )
    }
}

/**
 * 可点击的背景卡片组件（对位 ZL2 ui/components/BackgroundCard.kt 的第二重载）。
 */
@Composable
fun BackgroundCard(
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = cardColor(influencedByBackground),
        contentColor = onCardColor(),
        disabledContainerColor = cardColor(influencedByBackground)
    ),
    elevation: CardElevation = CardDefaults.cardElevation(),
    blur: Int = VersePersonalization.backgroundBlur,
    border: BorderStroke? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable @UiComposable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        onClick = onClick,
        enabled = enabled,
    ) {
        Column(
            modifier = Modifier.backgroundGlass(blur, colors.containerColor, influencedByBackground),
            content = content
        )
    }
}

/**
 * 适合在背景卡片组件顶部使用的标题栏 Layout（对位 ZL2 CardTitleLayout）。
 * @param alpha 组件背景颜色不透明度
 */
@Composable
fun CardTitleLayout(
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    alpha: Float = 0.5f,
    color: Color = influencedByBackgroundColor(
        color = cardTitleColor(alpha),
        enabled = influencedByBackground
    ),
    contentColor: Color = onCardColor(),
    blur: Int = VersePersonalization.backgroundBlur,
    content: @Composable @UiComposable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = color,
            contentColor = contentColor,
        ) {
            Column(
                modifier = Modifier.backgroundGlass(blur, color, influencedByBackground),
                content = content
            )
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
    }
}
