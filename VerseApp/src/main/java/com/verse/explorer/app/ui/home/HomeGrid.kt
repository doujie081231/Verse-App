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

    // 首次使用（无快照）：等待真实列数测量完成后，居中排布 头像 → 版本选择 → 启动按钮
    LaunchedEffect(state.geometry.columns) {
        if (seededFirstLayout) return@LaunchedEffect
        val columns = state.geometry.columns
        // 几何默认值为 4 列/20dp，视为尚未完成实际测量
        if (columns <= MIN_GRID_COLUMNS && state.cellPx == 20f) return@LaunchedEffect
        val seeds = systemCardSeeds(columns)
        state.seed(types = HomeCards.userCardTypes, seeds = seeds, storedColumns = 0)
        ensureSystemCard(state, HomeCards.accountCardType(), "system_account")
        ensureSystemCard(state, HomeCards.currentVersionCardType(), "system_current_version")
        ensureSystemCard(state, HomeCards.launchCardType(), "system_launch")
        seededFirstLayout = true
    }

    // 退化布局自愈：首帧异常窗口宽度曾把全部卡片钳到左列并被持久化，
    // 真实列数测量完成后检测到该特征时，把固定功能卡片重新居中还原
    LaunchedEffect(state.geometry.columns, seededFirstLayout) {
        if (!seededFirstLayout) return@LaunchedEffect
        if (state.geometry.columns <= MIN_GRID_COLUMNS && state.cellPx == 20f) return@LaunchedEffect
        repairDegenerateLayout(state, state.geometry.columns)
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
    // 记录尚未从持久化载入时（列表为空）不撤卡，避免启动首帧把已固定卡片全部清掉。
    LaunchedEffect(Unit) {
        VersionCardManager.cards.collect { states ->
            if (!VersionCardManager.isInitialized()) return@collect
            val recordIds = states.map { it.record.cardId }.toSet()
            state.cards
                .filter { it.type.typeId == HomeCards.VERSION_CARD_TYPE_ID && it.id !in recordIds }
                .forEach { state.removeCard(it.id) }
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
private fun systemCardLayouts(columns: Int): Map<String, CardRect> {
    val accountSpan = 6.coerceAtMost(columns)
    val versionSpan = 12.coerceAtMost(columns)
    val launchSpan = 8.coerceAtMost(columns)
    val accountY = 1
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

private fun systemCardSeeds(columns: Int): List<CardSeed> =
    systemCardLayouts(columns).map { (id, rect) ->
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

/**
 * 修复退化布局：首帧异常窗口宽度会把全部卡片钳制到左列并持久化，
 * 加载后若检测到「所有卡片都贴左列」（且不止一张卡），把固定功能卡片重新居中。
 */
private fun repairDegenerateLayout(state: CardGridState, columns: Int) {
    if (state.cards.size < 2) return
    if (state.cards.any { it.layout.x != 0 }) return
    state.applyLayouts(systemCardLayouts(columns))
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
