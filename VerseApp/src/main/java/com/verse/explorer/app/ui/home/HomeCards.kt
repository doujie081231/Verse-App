package com.verse.explorer.app.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import com.verse.explorer.cardgrid.model.CardInteraction
import com.verse.explorer.cardgrid.model.CardLimits
import com.verse.explorer.cardgrid.model.CardState
import com.verse.explorer.cardgrid.model.CardType
import kotlinx.coroutines.withTimeout

/**
 * 主页卡片注册表：声明网格可用的卡片类型与默认跨度。
 */
object HomeCards {

    /** 版本卡片的类型 id */
    const val VERSION_CARD_TYPE_ID = "version_card"

    /** 账户卡片的类型 id */
    const val ACCOUNT_CARD_TYPE_ID = "account_card"

    /** 当前版本卡片的类型 id */
    const val CURRENT_VERSION_CARD_TYPE_ID = "current_version_card"

    /** 启动卡片的类型 id */
    const val LAUNCH_CARD_TYPE_ID = "launch_card"

    private val versionCardType = CardType(
        typeId = VERSION_CARD_TYPE_ID,
        defaultSpan = IntOffset(10, 6),
        limits = CardLimits(
            minWidth = 10,
            minHeight = 4,
            maxHeight = 12
        ),
        content = { cardId ->
            VersionCardContent(cardId)
        }
    )

    /** 头像卡片：可自由放大缩小 */
    private val accountCardType = CardType(
        typeId = ACCOUNT_CARD_TYPE_ID,
        defaultSpan = IntOffset(6, 6),
        limits = CardLimits(
            minWidth = 4,
            minHeight = 4,
            maxWidth = 12,
            maxHeight = 12
        ),
        decorated = false,
        content = {
            AccountCardContent()
        }
    )

    /** 版本选择卡片：尺寸固定（最小=最大跨度，调整态不出现缩放手柄） */
    private val currentVersionCardType = CardType(
        typeId = CURRENT_VERSION_CARD_TYPE_ID,
        defaultSpan = IntOffset(12, 3),
        limits = CardLimits(
            minWidth = 12,
            minHeight = 3,
            maxWidth = 12,
            maxHeight = 3
        ),
        content = {
            CurrentVersionCardContent()
        }
    )

    /** 启动卡片：尺寸固定，且整体比版本选择卡片更小 */
    private val launchCardType = CardType(
        typeId = LAUNCH_CARD_TYPE_ID,
        defaultSpan = IntOffset(8, 3),
        limits = CardLimits(
            minWidth = 8,
            minHeight = 3,
            maxWidth = 8,
            maxHeight = 3
        ),
        decorated = false,
        content = {
            LaunchCardContent()
        }
    )

    /** 全部用户卡片类型 */
    val userCardTypes: List<CardType> = listOf(
        versionCardType, accountCardType, currentVersionCardType, launchCardType
    )

    /** 版本卡片类型 */
    fun versionCardType(): CardType = versionCardType

    /** 账户卡片类型 */
    fun accountCardType(): CardType = accountCardType

    /** 当前版本卡片类型 */
    fun currentVersionCardType(): CardType = currentVersionCardType

    /** 启动卡片类型 */
    fun launchCardType(): CardType = launchCardType
}

/** 卡片内容的自身交互状态（实时感知拖动/缩放会话） */
@Composable
internal fun CardState.rememberUpdatedCardInteraction(): State<CardInteraction> =
    rememberUpdatedState(interaction)

/**
 * 卡片内容的点按判定：长按阈值内未超出触摸 slop 才视为点按，
 * 不拦截指针事件，避免与卡片的长按拖拽手势冲突。
 */
internal fun Modifier.homeCardTap(onTap: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.isConsumed) return@awaitEachGesture
        val startPosition = down.position
        val slopPx = viewConfiguration.touchSlop
        val longPressMillis = viewConfiguration.longPressTimeoutMillis
        var tapped = false
        try {
            withTimeout(longPressMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                    if ((change.position - startPosition).getDistance() > slopPx) return@withTimeout
                    if (!change.pressed) {
                        tapped = true
                        return@withTimeout
                    }
                }
            }
        } catch (_: PointerEventTimeoutCancellationException) {
            tapped = false
        }
        if (tapped) onTap()
    }
}
