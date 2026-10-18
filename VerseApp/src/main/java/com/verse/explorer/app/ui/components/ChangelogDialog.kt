package com.verse.explorer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 版本更新内容卡片弹窗：进入主界面时按 versionCode 判定展示一次，
 * 点击「我已知晓」后记录已读版本号，不再弹出。
 */
@Composable
fun ChangelogDialog(
    versionName: String,
    items: List<String>,
    onAcknowledge: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(VerseColors.PcBgActive)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(VerseColors.PcAccent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$versionName 更新内容",
                    color = VerseColors.PcTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items.forEachIndexed { index, item ->
                    Row {
                        Text(
                            text = "${index + 1}.",
                            color = VerseColors.PcAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item,
                            color = VerseColors.PcTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerseColors.PcAccent)
                    .padding(vertical = 12.dp)
                    .clickable { onAcknowledge() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "我已知晓",
                    color = if (isAccentLight()) Color.Black else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun isAccentLight(): Boolean {
    val c = VerseColors.PcAccent
    return (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue) > 0.65
}
