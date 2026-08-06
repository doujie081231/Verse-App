package com.verse.explorer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.ui.theme.VerseColors

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
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, VerseColors.PcBorder),
        contentColor = contentColorFor(VerseColors.PcBgCard),
        content = content
    )
}
