package com.verse.explorer.app.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.cardgrid.model.CardRect
import com.verse.explorer.cardgrid.state.CardGridState
import com.verse.explorer.cardgrid.state.CardSeed
import com.verse.explorer.cardgrid.ui.CardGrid
import com.verse.explorer.cardgrid.ui.CardGridAutoScroll

/** 主页版本卡片的操作回调：启动指定版本 / 打开版本设置 */
val LocalHomeVersionActions = staticCompositionLocalOf<HomeVersionActions> {
    HomeVersionActions(onLaunch = {}, onOpenVersionSettings = {})
}

/** 白色网格单元内的文字配色：白底使用深色文字保证可读性 */
val LocalCellTextPrimary = staticCompositionLocalOf { Color(0xFF1B1B1B) }
val LocalCellTextMuted = staticCompositionLocalOf { Color(0xFF9A9A9A) }

data class HomeVersionActions(
    val onLaunch: (String) -> Unit,
    val onOpenVersionSettings: (String) -> Unit
)

/**
 * 主页卡片网格：只承载用户卡片（已固定的版本卡片）。
 * 账户 / 当前版本 / 启动入口属于网格外的固定区域（见 [HomeSystemHub]），
 * 这样系统区域的位置由页面布局决定，不受持久化网格数据影响，
 * 也就不会出现被旧快照挤到顶部或排成一排的问题。
 * 卡片拖拽的边缘自动滚动直接驱动外层滚动容器，网格自身不再嵌套滚动。
 * @param screenBoundsHeightPx 外层滚动容器的像素高度，作为拖动/缩放的底部边界
 */
@Composable
fun VerseHomeGrid(
    state: CardGridState,
    outerScrollState: ScrollState,
    modifier: Modifier = Modifier,
    screenBoundsHeightPx: Float = 0f
) {
    val context = LocalContext.current

    // 上报屏幕可用高度：网格底部边界与屏幕底边对齐
    LaunchedEffect(screenBoundsHeightPx) {
        state.onScreenBounds(screenBoundsHeightPx)
    }

    // 播种持久化的用户卡片布局；有快照时按存储列数折算恢复
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val snapshot = HomeGridStore.load(context)
        val seeds: List<CardSeed> = snapshot?.cards.orEmpty()
            // 旧快照里可能残留已移出网格的系统卡片（system_*），只恢复用户卡片
            .filter { it.type == HomeCards.VERSION_CARD_TYPE_ID }
            .map { entry ->
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
            }
        state.seed(
            types = HomeCards.userCardTypes,
            seeds = seeds,
            storedColumns = snapshot?.columns ?: 0
        )
        seeded = true
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

    // 与版本卡片记录保持同步：记录已删除的版本卡片撤出网格，记录存在而网格缺卡时补齐。
    // 等首次布局播种完成后再同步，避免记录先到而把新卡片落到左上角（0,0）。
    LaunchedEffect(seeded) {
        if (!seeded) return@LaunchedEffect
        VersionCardManager.cards.collect { states ->
            if (!VersionCardManager.isInitialized()) return@collect
            val recordIds = states.map { it.record.cardId }.toSet()
            state.cards
                .filter { it.type.typeId == HomeCards.VERSION_CARD_TYPE_ID && it.id !in recordIds }
                .forEach { state.removeCard(it.id) }
            states.forEach { cardState ->
                if (state.cards.none { it.id == cardState.record.cardId }) {
                    state.addCard(
                        HomeCards.versionCardType(),
                        cardState.record.cardId,
                        newVersionCardLayout(state, state.geometry.columns)
                    )
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
        // 白色网格单元：卡片表面为纯白，内部文字切换为深色配色
        CompositionLocalProvider(
            LocalCellTextPrimary provides Color(0xFF1B1B1B),
            LocalCellTextMuted provides Color(0xFF9A9A9A)
        ) {
            CardGrid(
                state = state,
                containerColor = Color.White,
                contentColor = LocalCellTextPrimary.current,
                adjustingBar = { barModifier, card ->
                    CardToolbar(
                        modifier = barModifier,
                        state = state,
                        card = card
                    )
                }
            )
        }
        CardGridAutoScroll(
            state = state,
            scrollState = outerScrollState
        )
    }
}

/** 新增版本卡片的默认落位：水平居中，取自上而下的第一个空行，避免落到左上角 (0,0) */
private fun newVersionCardLayout(state: CardGridState, columns: Int): CardRect {
    val type = HomeCards.versionCardType()
    val w = type.defaultSpan.x.coerceIn(1, columns)
    val h = type.defaultSpan.y.coerceAtLeast(1)
    val x = ((columns - w) / 2).coerceAtLeast(0)
    var y = 0
    while (state.cards.any { card ->
            val r = card.layout
            x < r.right && x + w > r.x && y < r.bottom && y + h > r.y
        }) {
        y++
    }
    return CardRect("", x, y, w, h)
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
                // 版本卡片可移除（可从版本列表重新固定），固定功能卡片已不在网格内
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
