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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.staticCompositionLocalOf
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.cardgrid.model.MIN_GRID_COLUMNS
import com.verse.explorer.cardgrid.state.CardGridState
import com.verse.explorer.cardgrid.state.CardSeed
import com.verse.explorer.cardgrid.model.CardRect
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
 * 主页卡片网格：负责布局的播种、持久化与卡片记录同步，
 * 系统区域（账户、启动入口等）绘制在网格之外；
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
    var seededFirstLayout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val snapshot = HomeGridStore.load(context)
        if (snapshot != null) {
            val seeds: List<CardSeed> = snapshot.cards.map { entry ->
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
                storedColumns = snapshot.columns
            )
            ensureSystemCard(state, HomeCards.accountCardType(), "system_account")
            ensureSystemCard(state, HomeCards.currentVersionCardType(), "system_current_version")
            ensureSystemCard(state, HomeCards.launchCardType(), "system_launch")
            seededFirstLayout = true
        }
    }

    // 首次使用（无快照）：等待真实列数与可视高度测量完成后，居中排布 头像 → 版本选择 → 启动按钮
    LaunchedEffect(state.geometry.columns, state.screenBoundsPx) {
        if (seededFirstLayout) return@LaunchedEffect
        val columns = state.geometry.columns
        // 几何默认值为 4 列/20dp，视为尚未完成实际测量
        if (columns <= MIN_GRID_COLUMNS && state.cellPx == 20f) return@LaunchedEffect
        // 可视高度未上报时先不播种，避免按未测得的高度把固定卡片放到不居中的位置
        val visibleRows = state.visibleRowCount()
        if (visibleRows <= 0) return@LaunchedEffect
        val seeds = systemCardSeeds(columns, visibleRows)
        state.seed(types = HomeCards.userCardTypes, seeds = seeds, storedColumns = 0)
        ensureSystemCard(state, HomeCards.accountCardType(), "system_account")
        ensureSystemCard(state, HomeCards.currentVersionCardType(), "system_current_version")
        ensureSystemCard(state, HomeCards.launchCardType(), "system_launch")
        seededFirstLayout = true
    }

    // 屏幕尺寸变化（网格列数改变）时，引擎会按阅读顺序把所有卡片重新打包，固定功能卡片会被
    // 打到最顶部——这正是「改变屏幕尺寸后三块挤到最上面」的来源。这里只在列数真正变化时
    // 把三块固定卡片按设计布局重新居中；用户固定的版本卡片保持引擎的重排结果，不改动。
    var lastColumns by remember { mutableStateOf(0) }
    LaunchedEffect(state.geometry.columns, seededFirstLayout) {
        if (!seededFirstLayout) return@LaunchedEffect
        val columns = state.geometry.columns
        // 几何默认值（4 列/20dp）视为尚未完成实际测量
        if (columns <= MIN_GRID_COLUMNS && state.cellPx == 20f) return@LaunchedEffect
        val previous = lastColumns
        lastColumns = columns
        if (previous == 0 || previous == columns) return@LaunchedEffect
        state.applyLayouts(systemCardLayouts(columns, state.visibleRowCount()))
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
    LaunchedEffect(seededFirstLayout) {
        if (!seededFirstLayout) return@LaunchedEffect
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

/** 确保固定功能卡片存在于网格（id 固定，位置随用户排布持久化） */
private fun ensureSystemCard(state: CardGridState, type: com.verse.explorer.cardgrid.model.CardType, id: String) {
    if (state.cards.none { it.id == id }) {
        state.addCard(type, id)
    }
}

/**
 * 固定功能卡片的居中布局：跨度按实际列数自适应（窄屏时退化为整行），
 * 居中偏移不会出现负坐标，避免窄屏首帧把卡片挤到左列。
 */
private fun systemCardLayouts(columns: Int, visibleRows: Int = 0): Map<String, CardRect> {
    val accountSpan = 6.coerceAtMost(columns)
    val versionSpan = 12.coerceAtMost(columns)
    val launchSpan = 8.coerceAtMost(columns)
    // 账户 → 版本 → 启动的纵向总跨度（含两处间隙）
    val blockSpan = accountSpan + 2 + 3 + 1 + 3
    // 可视行数够放整块时垂直居中，否则退回贴顶；避免高屏机型上整组卡片挤在最上面
    val accountY = if (visibleRows > blockSpan + 2) (visibleRows - blockSpan) / 2 else 1
    val versionY = accountY + accountSpan + 2
    val launchY = versionY + 3 + 1
    return mapOf(
        "system_account" to CardRect(
            "system_account", (columns - accountSpan) / 2, accountY, accountSpan, accountSpan
        ),
        "system_current_version" to CardRect(
            "system_current_version", (columns - versionSpan) / 2, versionY, versionSpan, 3
        ),
        "system_launch" to CardRect(
            "system_launch", (columns - launchSpan) / 2, launchY, launchSpan, 3
        )
    )
}

private fun systemCardSeeds(columns: Int, visibleRows: Int = 0): List<CardSeed> =
    systemCardLayouts(columns, visibleRows).map { (id, rect) ->
        CardSeed(
            id = id,
            typeId = when (id) {
                "system_account" -> HomeCards.ACCOUNT_CARD_TYPE_ID
                "system_current_version" -> HomeCards.CURRENT_VERSION_CARD_TYPE_ID
                else -> HomeCards.LAUNCH_CARD_TYPE_ID
            },
            layout = rect
        )
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
