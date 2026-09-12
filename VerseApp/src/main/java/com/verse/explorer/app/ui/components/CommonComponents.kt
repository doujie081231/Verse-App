package com.verse.explorer.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
fun LabelWithIcon(
    icon: ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 11.sp
        )
    }
}

@Composable
fun FilterRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(options.size) { index ->
            val option = options[index]
            val isSelected = selected == option
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) VerseColors.PcAccent.copy(alpha = 0.12f) else VerseColors.PcBgCard,
                modifier = Modifier.clickable { onSelect(option) }
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) VerseColors.PcAccent else VerseColors.PcTextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * PC 式分页条（对齐 versepc2 `.pagination`：上一页 / 当前页/总页数 / 下一页）。
 * 翻页 disabled 规则对齐 PC updateModPagination：
 *   - 上一页 disabled: currentPage <= 0（即 offset<=0）
 *   - 下一页 disabled: (currentPage+1)*pageSize >= totalHits（即 offset+15>=total）
 */
@Composable
fun PcPagination(
    currentPage: Int,
    totalHits: Int,
    pageSize: Int = 15,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val totalPages = if (totalHits > 0) (totalHits + pageSize - 1) / pageSize else 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onPrev,
            enabled = currentPage > 0,
            shape = RoundedCornerShape(8.dp, 0.dp, 0.dp, 8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = VerseColors.PcBgCard,
                contentColor = VerseColors.PcTextSecondary,
                disabledContainerColor = VerseColors.PcBgCard,
                disabledContentColor = VerseColors.PcTextMuted
            )
        ) {
            Icon(Icons.Outlined.ChevronLeft, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("上一页", fontSize = 12.sp)
        }
        Text(
            text = " ${currentPage + 1} / $totalPages ",
            style = MaterialTheme.typography.labelSmall,
            color = VerseColors.PcTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        FilledTonalButton(
            onClick = onNext,
            enabled = totalHits > 0 && (currentPage + 1) * pageSize < totalHits,
            shape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = VerseColors.PcBgCard,
                contentColor = VerseColors.PcTextSecondary,
                disabledContainerColor = VerseColors.PcBgCard,
                disabledContentColor = VerseColors.PcTextMuted
            )
        ) {
            Text("下一页", fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
        }
    }
}
