package com.verse.explorer.app.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.cardgrid.state.CardGridState
import com.verse.explorer.cardgrid.state.CardSeed
import com.verse.explorer.cardgrid.model.CardRect
import com.verse.explorer.cardgrid.ui.CardGrid
import com.verse.explorer.cardgrid.ui.CardGridAutoScroll

/** 主页版本卡片的操作回调：启动指定版本 / 打开版本设置 */
val LocalHomeVersionActions = staticCompositionLocalOf<HomeVersionActions> {
    HomeVersionActions(onLaunch = {}, onOpenVersionSettings = {})
}

data class HomeVersionActions(
    val onLaunch: (String) -> Unit,
    val onOpenVersionSettings: (String) -> Unit
)

/**
 * 主页卡片网格：负责布局的播种、持久化与卡片记录同步，
 * 系统区域（账户、启动入口等）绘制在网格之外；
 * 卡片拖拽的边缘自动滚动直接驱动外层滚动容器，网格自身不再嵌套滚动。
 */
@Composable
fun VerseHomeGrid(
    state: CardGridState,
    outerScrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 播种持久化的用户卡片布局；首次使用（无快照）按原主页结构排布
    LaunchedEffect(Unit) {
        val snapshot = HomeGridStore.load(context)
        val seeds: List<CardSeed> = snapshot?.cards?.map { entry ->
            CardSeed(
                id = entry.id,
                typeId = entry.type,
                layout = CardRect(
                    id = entry.id,
                    x = entry.x,
                    y = entry.y,
                    width = entry.width,
                    height = entry.height
                )
            )
        } ?: listOf(
            // 头像（居中 2×2）→ 当前版本选择 → 启动按钮 → 版本卡片垫底
            CardSeed(
                id = "system_account",
                typeId = HomeCards.ACCOUNT_CARD_TYPE_ID,
                layout = CardRect("system_account", 4, 0, 2, 2)
            ),
            CardSeed(
                id = "system_current_version",
                typeId = HomeCards.CURRENT_VERSION_CARD_TYPE_ID,
                layout = CardRect("system_current_version", 1, 2, 8, 2)
            ),
            CardSeed(
                id = "system_launch",
                typeId = HomeCards.LAUNCH_CARD_TYPE_ID,
                layout = CardRect("system_launch", 1, 4, 8, 2)
            )
        )
        state.seed(
            types = HomeCards.userCardTypes,
            seeds = seeds,
            storedColumns = snapshot?.columns ?: 0
        )
        // 快照恢复缺卡时补齐固定功能卡片，保证主页基础功能常驻
        ensureSystemCard(state, HomeCards.accountCardType(), "system_account")
        ensureSystemCard(state, HomeCards.currentVersionCardType(), "system_current_version")
        ensureSystemCard(state, HomeCards.launchCardType(), "system_launch")
    }

    // 布局结算后持久化
    LaunchedEffect(Unit) {
        state.onLayoutCommitted = {
            HomeGridStore.save(context, state.cards, state.geometry.columns)
        }
    }

    // 卡片移除回调：同步卡片管理器的记录
    LaunchedEffect(Unit) {
        state.onCardRemoved = { cardId ->
            VersionCardManager.removeCard(context, cardId)
        }
    }

    // 与版本卡片记录保持同步：记录存在而网格缺卡时补齐
    LaunchedEffect(Unit) {
        VersionCardManager.cards.collect { states ->
            states.forEach { cardState ->
                if (state.cards.none { it.id == cardState.record.cardId }) {
                    state.addCard(HomeCards.versionCardType(), cardState.record.cardId)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                state.onViewportPositioned(
                    topPx = coordinates.positionInRoot().y,
                    heightPx = coordinates.size.height.toFloat()
                )
            }
    ) {
        CardGrid(
            state = state,
            containerColor = VerseColors.PcBgCard,
            contentColor = VerseColors.PcTextPrimary,
            adjustingBar = { barModifier, card ->
                CardToolbar(
                    modifier = barModifier,
                    state = state,
                    card = card
                )
            }
        )
        CardGridAutoScroll(
            state = state,
            scrollState = outerScrollState
        )
    }
}

/** 确保固定功能卡片存在于网格（id 固定，位置随用户排布持久化） */
private fun ensureSystemCard(state: CardGridState, type: com.verse.explorer.cardgrid.model.CardType, id: String) {
    if (state.cards.none { it.id == id }) {
        state.addCard(type, id)
    }
}

@Composable
private fun CardToolbar(
    state: CardGridState,
    card: com.verse.explorer.cardgrid.state.GridCard,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = VerseColors.PcBgSecondary,
        contentColor = VerseColors.PcTextPrimary,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(all = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (card.type.typeId == HomeCards.VERSION_CARD_TYPE_ID) {
                // 版本卡片可移除（可从版本列表重新固定），固定功能卡片不提供删除
                IconButton(
                    modifier = Modifier.size(34.dp),
                    onClick = { state.removeCard(card.id) }
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除卡片",
                        tint = VerseColors.PcOrange
                    )
                }
            }
            IconButton(
                modifier = Modifier.size(34.dp),
                onClick = { state.exitAdjusting() }
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "完成",
                    tint = VerseColors.PcAccent
                )
            }
        }
    }
}
