package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 主题颜色选项卡片：选中时带强调色边框与光晕
 */
@Composable
fun RowScope.ThemeColorOption(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) VerseColors.PcAccent.copy(alpha = 0.08f) else VerseColors.PcBgTertiary,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) VerseColors.PcAccent else VerseColors.PcBorder
        ),
        modifier = Modifier
            .weight(1f)
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = VerseColors.PcAccentGlow,
                        spotColor = VerseColors.PcAccentGlow
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Text(
                label,
                fontSize = 14.sp,
                color = VerseColors.PcTextSecondary
            )
        }
    }
}

/**
 * 背景风格选项卡片：选中时带强调色边框与光晕
 */
@Composable
fun BackgroundStyleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) VerseColors.PcAccent.copy(alpha = 0.08f) else VerseColors.PcBgTertiary,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) VerseColors.PcAccent else VerseColors.PcBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = VerseColors.PcAccentGlow,
                        spotColor = VerseColors.PcAccentGlow
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(VerseColors.PcTextMuted)
            )
            Text(
                label,
                fontSize = 14.sp,
                color = VerseColors.PcTextSecondary
            )
        }
    }
}
