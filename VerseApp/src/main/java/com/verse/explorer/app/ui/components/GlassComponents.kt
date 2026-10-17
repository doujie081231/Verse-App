package com.verse.explorer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.backgroundGlass
import com.verse.explorer.app.ui.influencedByBackgroundColor
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    borderAlpha: Float = 0.06f,
    content: @Composable () -> Unit
) {
    // 自定义背景有效时容器色按背景不透明度设置降低 alpha；
    // 前景毛玻璃模式下在内容下方对齐绘制背景的模糊切片
    val containerColor = influencedByBackgroundColor(VerseColors.PcBgCard)
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, VerseColors.PcBorder),
        contentColor = contentColorFor(containerColor),
        content = {
            Box(modifier = Modifier.backgroundGlass(VersePersonalization.backgroundBlur, containerColor)) {
                content()
            }
        }
    )
}
