package com.verse.explorer.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.HomeAvatar
import com.verse.explorer.app.ui.LoaderBadge
import com.verse.explorer.app.ui.VersionIcon
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.cardgrid.model.CardInteraction
import com.verse.explorer.cardgrid.model.CardSizeClass
import com.verse.explorer.cardgrid.model.CardState

/** 主页固定卡片的共享状态与操作回调（账户、当前版本、启动入口） */
data class HomeHubState(
    val account: com.verse.explorer.core.account.Account? = null,
    val currentVersion: com.verse.explorer.core.version.MinecraftVersion? = null,
    val currentVersionId: String? = null,
    val launching: Boolean = false,
    val onGoAccounts: () -> Unit = {},
    val onOpenVersions: () -> Unit = {},
    val onLaunch: () -> Unit = {}
)

val LocalHomeHub = staticCompositionLocalOf { HomeHubState() }

/**
 * 账户卡片：头像 + 用户名 + 账户类型，点按进入账户管理
 */
@Composable
fun CardState.AccountCardContent() {
    val hub = LocalHomeHub.current
    val currentInteraction by rememberUpdatedCardInteraction()
    val big = sizeClass.height >= CardSizeClass.LARGE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .homeCardTap {
                if (currentInteraction == CardInteraction.Idle) hub.onGoAccounts()
            }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (big) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    HomeAvatar(account = hub.account, onClick = {})
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hub.account?.username ?: "未登录",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerseColors.PcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = accountTypeLabel(hub.account),
                    fontSize = 10.sp,
                    color = VerseColors.PcTextMuted
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    HomeAvatar(account = hub.account, onClick = {})
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hub.account?.username ?: "未登录",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerseColors.PcTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = accountTypeLabel(hub.account),
                        fontSize = 10.sp,
                        color = VerseColors.PcTextMuted
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = VerseColors.PcTextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 当前版本卡片：版本图标 + 名称 + 加载器徽章，点按进入版本选择
 */
@Composable
fun CardState.CurrentVersionCardContent() {
    val hub = LocalHomeHub.current
    val currentInteraction by rememberUpdatedCardInteraction()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .homeCardTap {
                if (currentInteraction == CardInteraction.Idle) hub.onOpenVersions()
            }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hub.currentVersion != null) {
                VersionIcon(version = hub.currentVersion, size = 34.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(VerseColors.PcBgTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 16.sp, color = VerseColors.PcTextMuted, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(2.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hub.currentVersion?.id ?: "点击选择版本",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                if (hub.currentVersion != null) {
                    LoaderBadge(version = hub.currentVersion)
                } else {
                    Text("选择要启动的版本", fontSize = 10.sp, color = VerseColors.PcTextMuted)
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = VerseColors.PcTextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 启动卡片：主启动按钮（与主页主启动按钮同风格）
 */
@Composable
fun CardState.LaunchCardContent() {
    val hub = LocalHomeHub.current
    val enabled = hub.currentVersionId != null && !hub.launching
    val textColor = if (enabled) VerseColors.PcAccentText else VerseColors.PcAccentText.copy(alpha = 0.45f)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (sizeClass.height >= CardSizeClass.LARGE) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LaunchButtonCore(
                    launching = hub.launching,
                    enabled = enabled,
                    textColor = textColor,
                    big = true
                ) { hub.onLaunch() }
            }
        } else {
            LaunchButtonCore(
                launching = hub.launching,
                enabled = enabled,
                textColor = textColor,
                big = false
            ) { hub.onLaunch() }
        }
    }
}

@Composable
private fun LaunchButtonCore(
    launching: Boolean,
    enabled: Boolean,
    textColor: Color,
    big: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(VerseColors.PcLaunchGradientStart, VerseColors.PcLaunchGradientEnd)
                )
            )
            .homeCardTap(onTap = { if (enabled) onClick() })
            .padding(
                horizontal = if (big) 36.dp else 26.dp,
                vertical = if (big) 14.dp else 9.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(if (big) 18.dp else 14.dp)
            )
            Text(
                text = if (launching) "启动中..." else "启动游戏",
                fontSize = if (big) 15.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

private fun accountTypeLabel(account: com.verse.explorer.core.account.Account?): String = when (account?.type) {
    com.verse.explorer.core.account.AccountType.MICROSOFT -> "Microsoft 正版"
    com.verse.explorer.core.account.AccountType.AUTHLIB -> "外置登录"
    com.verse.explorer.core.account.AccountType.OFFLINE -> "离线模式"
    null -> "点击登录"
}
