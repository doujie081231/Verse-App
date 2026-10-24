package com.verse.explorer.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 玫瑰曲线加载动画入口：参数与旧约定兼容（size 仅用于布局占位），
 */
@Composable
fun RoseLoader(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = VerseColors.PcAccent
) {
    RoseCurveLoader(modifier = modifier, color = color)
}
