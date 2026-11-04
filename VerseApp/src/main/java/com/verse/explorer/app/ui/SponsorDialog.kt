package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verse.explorer.app.data.SponsorEntry
import com.verse.explorer.app.data.SponsorMonth
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors

/** 赞助入口：Verse 的爱发电主页 */
const val SPONSOR_URL = "https://ifdian.net/u/399cb3c8117c11f1ba4152540025c377"

/**
 * 月度赞助者感谢弹窗。
 *
 * 注意：当前版本**不在任何入口自动弹出**（1.3.4 的月度弹出已被撤下），
 * 赞助入口只保留在「设置 → 关于 → 致谢」里，本组件仅保留以便后续按需启用。
 *
 * 名单按金额从高到低排列，只展示昵称与金额（不使用头像）；
 * 前三名卡片带渐变描边与更大字号，列表可上下滑动，底部固定「前往赞助 / 确定」。
 */
@Composable
fun SponsorDialog(
    month: SponsorMonth,
    onDismiss: () -> Unit,
    onGoSponsor: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        // 关掉平台默认宽度限制：默认宽度在窄屏上会把卡片挤出屏幕
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VerseColors.PcBgSecondary,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = VerseColors.PcRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${month.title}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = VerseColors.PcTextPrimary,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "感谢以下 ${month.sponsors.size} 位玩家对 Verse 的支持，服务器与开发都靠你们撑着。",
                        fontSize = 11.sp,
                        color = VerseColors.PcTextSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            // weight 让列表吸收剩余高度：内容再多也只压缩列表，
                            // 底部的「前往赞助 / 确定」不会被顶出屏幕
                            .weight(1f, fill = false)
                            .heightIn(max = 360.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(month.sponsors) { index, sponsor ->
                            SponsorCard(sponsor = sponsor, rank = index)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "共 ${"%.2f".format(month.total)} 元 · 数据月份 ${month.month}",
                        fontSize = 10.sp,
                        color = VerseColors.PcTextMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PcButton(
                            text = "前往赞助",
                            onClick = onGoSponsor,
                            variant = PcButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                        PcButton(
                            text = "确定",
                            onClick = onDismiss,
                            variant = PcButtonVariant.OUTLINE,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单张赞助者卡片：只展示名次、昵称、金额，不带头像。
 * 前三名（[rank] < 3）用渐变描边、更大的名次徽章与更粗的名字，视觉权重与金额一致。
 */
@Composable
private fun SponsorCard(sponsor: SponsorEntry, rank: Int) {
    val top = rank < 3
    val shape = RoundedCornerShape(if (top) 14.dp else 11.dp)
    val badgeSize = if (top) 26.dp else 22.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (top) {
                    Brush.horizontalGradient(
                        listOf(
                            VerseColors.PcAccent.copy(alpha = 0.16f),
                            VerseColors.PcBgCard
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(VerseColors.PcBgCard, VerseColors.PcBgCard)
                    )
                }
            )
            .then(
                if (top) Modifier.border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(VerseColors.PcAccent, VerseColors.PcPurple)
                    ),
                    shape
                ) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = if (top) 12.dp else 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 名次徽章
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(
                        if (top) VerseColors.PcAccent.copy(alpha = 0.18f)
                        else VerseColors.PcBgTertiary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${rank + 1}",
                    fontSize = if (top) 12.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (top) VerseColors.PcAccent else VerseColors.PcTextMuted
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = sponsor.name,
                fontSize = if (top) 14.sp else 13.sp,
                fontWeight = if (top) FontWeight.Bold else FontWeight.Medium,
                color = VerseColors.PcTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "¥${"%.2f".format(sponsor.amount)}",
                fontSize = if (top) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (top) VerseColors.PcAccent else VerseColors.PcTextSecondary
            )
        }
    }
}
