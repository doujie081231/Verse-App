package com.verse.explorer.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * 系统区域（账户、启动入口等）绘制在网格之外。
 */
@Composable
fun VerseHomeGrid(
    state: CardGridState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 播种持久化的用户卡片布局
    LaunchedEffect(Unit) {
        val snapshot = HomeGridStore.load(context)
        state.seed(
            types = HomeCards.userCardTypes,
            seeds = snapshot?.cards?.map { entry ->
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
            } ?: emptyList(),
            storedColumns = snapshot?.columns ?: 0
        )
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

    val cardCount by VersionCardManager.cards.collectAsStateWithLifecycle()

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
            if (cardCount.isEmpty()) {
                Text(
                    text = "暂无主页卡片，可在「已安装版本」列表中将版本固定到这里",
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        CardGridAutoScroll(
            state = state,
            scrollState = scrollState
        )
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
            IconButton(
                modifier = Modifier.size(34.dp),
                onClick = { state.removeCard(card.id) }
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除卡片",
                    tint = MaterialTheme.colorScheme.error
                )
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
