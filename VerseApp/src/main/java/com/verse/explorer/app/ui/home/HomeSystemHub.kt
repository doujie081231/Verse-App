package com.verse.explorer.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.HomeAvatar
import com.verse.explorer.app.ui.LoaderBadge
import com.verse.explorer.app.ui.VersionIcon
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 主页固定区域：账户头像 → 当前版本 → 启动入口，纵向固定排列，不参与卡片网格。
 * 位置完全由页面布局决定，因此不会被网格快照挤到顶部或排成一行。
 */
@Composable
fun HomeSystemHub(modifier: Modifier = Modifier) {
    val hub = LocalHomeHub.current
    val enabled = hub.currentVersionId != null && !hub.launching
    val launchTextColor = if (enabled) {
        VerseColors.PcAccentText
    } else {
        VerseColors.PcAccentText.copy(alpha = 0.45f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 头像：固定方形，点按进入账户管理
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { hub.onGoAccounts() }
        ) {
            HomeAvatar(account = hub.account)
        }

        // 当前版本：白底一行，点按进入版本选择
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { hub.onOpenVersions() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hub.currentVersion != null) {
                    VersionIcon(version = hub.currentVersion, size = 32.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(VerseColors.PcBgTertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 15.sp, color = LocalCellTextMuted.current, fontWeight = FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hub.currentVersion?.id ?: "点击选择版本",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalCellTextPrimary.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    if (hub.currentVersion != null) {
                        LoaderBadge(version = hub.currentVersion)
                    } else {
                        Text("选择要启动的版本", fontSize = 10.sp, color = LocalCellTextMuted.current)
                    }
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = LocalCellTextMuted.current,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // 启动入口：渐变主按钮
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.linearGradient(
                        listOf(VerseColors.PcLaunchGradientStart, VerseColors.PcLaunchGradientEnd)
                    )
                )
                .clickable(enabled = enabled) { hub.onLaunch() }
                .padding(horizontal = 26.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = launchTextColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (hub.launching) "启动中..." else "启动游戏",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = launchTextColor
            )
        }
        Spacer(Modifier.width(0.dp))
    }
}
