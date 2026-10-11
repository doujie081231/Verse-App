package com.verse.explorer.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verse.explorer.app.ui.LoaderBadge
import com.verse.explorer.app.ui.VersionIcon
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.cardgrid.model.CardInteraction
import com.verse.explorer.cardgrid.model.CardSizeClass
import com.verse.explorer.cardgrid.model.CardState

/**
 * 主页版本卡片内容：
 * 依据卡片形态档位切换横向 / 纵向布局，展示版本图标、名称、加载器徽章
 * 与启动入口；点按卡片（非调整态）打开版本设置。
 */
@Composable
fun CardState.VersionCardContent(cardId: String) {
    val states by VersionCardManager.cards.collectAsStateWithLifecycle()
    val card = remember(states, cardId) {
        states.firstOrNull { it.record.cardId == cardId }
    }
    val actions = LocalHomeVersionActions.current

    val currentVersion = (card?.status as? VersionCardStatus.Available)?.version
    val currentVersionId = card?.record?.versionId
    val currentInteraction by rememberUpdatedState(interaction)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .homeCardTap {
                if (currentInteraction == CardInteraction.Idle && currentVersionId != null) {
                    actions.onOpenVersionSettings(currentVersionId)
                }
            }
            .padding(12.dp)
    ) {
        when {
            card?.status is VersionCardStatus.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = VerseColors.PcAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            card?.status is VersionCardStatus.Deleted -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "版本已不存在",
                        fontSize = 12.sp,
                        color = VerseColors.PcTextMuted
                    )
                }
            }
            sizeClass.width >= CardSizeClass.MEDIUM &&
                    sizeClass.height >= CardSizeClass.LARGE -> TallCardContent(
                version = currentVersion,
                versionId = currentVersionId
            )
            else -> RowCardContent(
                version = currentVersion,
                versionId = currentVersionId,
                compact = sizeClass.width <= CardSizeClass.SMALL
            )
        }
    }
}

/** 纵向布局：大图标居中 + 名称 + 加载器 + 启动按钮 */
@Composable
private fun TallCardContent(version: com.verse.explorer.core.version.MinecraftVersion?, versionId: String?) {
    val actions = LocalHomeVersionActions.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (version != null) {
            VersionIcon(version = version, size = 52.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = version.id,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerseColors.PcTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            LoaderBadge(version = version)
        } else if (versionId != null) {
            Text(
                text = versionId,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerseColors.PcTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        CardLaunchButton(
            enabled = version != null,
            onClick = { versionId?.let(actions.onLaunch) }
        )
    }
}

/** 横向布局：图标 + 名称/加载器 + 启动按钮 */
@Composable
private fun RowCardContent(
    version: com.verse.explorer.core.version.MinecraftVersion?,
    versionId: String?,
    compact: Boolean
) {
    val actions = LocalHomeVersionActions.current
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
    ) {
        if (version != null) {
            VersionIcon(version = version, size = 34.dp)
            Spacer(Modifier.width(2.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = version.id,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                LoaderBadge(version = version)
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = versionId ?: "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        CardLaunchButton(
            enabled = version != null,
            onClick = { versionId?.let(actions.onLaunch) }
        )
    }
}

/** 卡片内启动按钮（与主页主启动按钮同风格的紧凑版） */
@Composable
private fun CardLaunchButton(enabled: Boolean, onClick: () -> Unit) {
    val textColor = if (enabled) VerseColors.PcAccentText else VerseColors.PcAccentText.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(VerseColors.PcLaunchGradientStart, VerseColors.PcLaunchGradientEnd)
                )
            )
            .homeCardTap(onTap = { if (enabled) onClick() })
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "启动",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

/**
 * 卡片内容的点按判定与交互状态辅助见 [homeCardTap]（HomeCards.kt 公共实现）。
 */
