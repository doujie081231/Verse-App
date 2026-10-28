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
 * 检测到新版本时的提示弹窗。
 *
 * 应用内不再下载 APK（整包 180MB，自带下载器既占带宽，又常被系统杀后台导致装不上）；
 * 只展示更新内容，并给出两个手动下载入口：
 *  - GitHub Release 页面（手机浏览器打开即可下载）
 *  - 网盘（夸克，国内网络更稳）
 */
@Composable
fun UpdateDialog(
    versionName: String,
    releaseDate: String,
    items: List<String>,
    onIgnore: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenPan: () -> Unit
) {
    Dialog(onDismissRequest = onIgnore) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
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
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "检测到版本更新 $versionName",
                        color = VerseColors.PcTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (releaseDate.isNotBlank()) {
                        Text(
                            text = "发布日期：$releaseDate",
                            color = VerseColors.PcTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (items.isEmpty()) {
                    Text(
                        text = "本次更新包含若干问题修复与体验优化。",
                        color = VerseColors.PcTextSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    items.forEachIndexed { index, item ->
                        Row {
                            Text(
                                text = "${index + 1}.",
                                color = VerseColors.PcAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item,
                                color = VerseColors.PcTextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "启动器已取消应用内下载，请选择下面的方式手动下载安装包，覆盖安装即可",
                color = VerseColors.PcTextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(12.dp))

            // 主入口：GitHub Release 页面
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerseColors.PcAccent)
                    .padding(vertical = 12.dp)
                    .clickable { onOpenGitHub() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "打开 GitHub 下载页",
                    color = if (isAccentLight()) Color.Black else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VerseColors.PcBgTertiary)
                        .padding(vertical = 12.dp)
                        .clickable { onOpenPan() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "网盘下载",
                        color = VerseColors.PcTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VerseColors.PcBgTertiary)
                        .padding(vertical = 12.dp)
                        .clickable { onIgnore() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "稍后再说",
                        color = VerseColors.PcTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 强调色偏亮时按钮文字改用深色，保证对比度 */
private fun isAccentLight(): Boolean {
    val c = VerseColors.PcAccent
    return (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue) > 0.65
}
