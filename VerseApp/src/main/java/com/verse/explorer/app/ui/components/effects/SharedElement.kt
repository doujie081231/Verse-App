package com.verse.explorer.app.ui.components.effects

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 共享元素转场的上下文传递。
 *
 * 背景：共享元素要求「发起方」和「目标方」处在同一个 [SharedTransitionLayout] 里，
 * 并且各自能拿到自己页面的 [AnimatedVisibilityScope]（导航时由 NavHost 提供）。
 * 这两个东西无法从卡片内部凭空取到，因此由导航宿主通过 CompositionLocal 下发：
 *
 * - [LocalSharedTransitionScope]：根布局的 SharedTransitionLayout 作用域；
 * - [LocalNavAnimatedVisibilityScope]：当前导航目的地的作用域。
 *
 * 任一为空时 [verseSharedElement] 直接返回原 Modifier，等于不启用共享元素。
 * 这样同一张卡片可以放在任意页面复用，没接共享元素的页面自然退化为普通渲染。
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 给元素挂上共享元素身份，[key] 相同的两个元素在页面切换时会做位置/尺寸补间。
 *
 * 只应在「卡片入口」与「详情页同一元素」上成对使用，key 必须全局唯一（建议拼业务 id）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.verseSharedElement(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val navScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val state = sharedScope.rememberSharedContentState(key)
    return with(sharedScope) {
        this@verseSharedElement.sharedElement(state, animatedVisibilityScope = navScope)
    }
}
