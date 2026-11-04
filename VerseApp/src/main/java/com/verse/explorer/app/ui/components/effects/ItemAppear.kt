package com.verse.explorer.app.ui.components.effects

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 列表项出现动画：淡入 + 轻微上移，按序号做小幅错峰。
 *
 * 只作用于首帧：进入组合后动画到终态就不再重算（滚动复用不会反复闪烁）；
 * 错峰上限 6 项（6×28ms≈168ms），即使一次进来 20 个条目也不会让最后一项等太久。
 */
@Composable
fun Modifier.verseItemAppear(index: Int, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    var appeared by remember { mutableStateOf(false) }
    val delay = (index.coerceAtMost(6)) * 28
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 200, delayMillis = delay),
        label = "itemAlpha"
    )
    val translate by animateFloatAsState(
        targetValue = if (appeared) 0f else 22f,
        animationSpec = tween(
            durationMillis = 260,
            delayMillis = delay,
            easing = FastOutSlowInEasing
        ),
        label = "itemTranslate"
    )
    LaunchedEffect(Unit) { appeared = true }
    return this.graphicsLayer {
        this.alpha = alpha
        translationY = translate
    }
}
