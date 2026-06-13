package com.verse.explorer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    borderAlpha: Float = 0.06f,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = VerseColors.McCardBg,
        border = BorderStroke(1.dp, VerseColors.McCardBorder),
        contentColor = contentColorFor(VerseColors.McCardBg),
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    borderAlpha: Float = 0.06f,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = VerseColors.McCardBg,
        border = BorderStroke(1.dp, VerseColors.McCardBorder),
        contentColor = contentColorFor(VerseColors.McCardBg),
        content = content
    )
}

@Composable
fun GlassOverlay(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = VerseColors.McOverlayContentBg,
        border = BorderStroke(1.dp, VerseColors.McCardBorder),
        contentColor = contentColorFor(VerseColors.McOverlayContentBg),
        content = content
    )
}

@Composable
fun GradientIconBox(
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.linearGradient(gradientColors),
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
        content = content
    )
}