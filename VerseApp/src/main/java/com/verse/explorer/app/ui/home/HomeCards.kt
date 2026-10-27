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
 * 账户 / 当前版本 / 启动入口是页面固定区域，不参与网格布局与持久化。
 */
object HomeCards {

    /** 版本卡片的类型 id */
    const val VERSION_CARD_TYPE_ID = "version_card"

    /** 版本卡片：显示单个已安装版本的启动入口与设置入口，可拖动与缩放 */
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

    /** 全部用户卡片类型（系统区域不在网格内，网格只承载版本卡片） */
    val userCardTypes: List<CardType> = listOf(versionCardType)

    /** 版本卡片类型 */
    fun versionCardType(): CardType = versionCardType
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
