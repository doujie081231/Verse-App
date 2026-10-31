package com.verse.explorer.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import com.verse.explorer.app.data.VersePersonalization
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut

// ==================== 页面切换动画 ====================
// 默认使用 Material 动效规范（fornewid/material-motion-compose）：
// 顶层页签之间无强关联 → FadeThrough；详情/深入页面有层级关系 → SharedAxis X。
// 其余样式（滑动 / 淡入淡出 / 缩放 / 上下共享轴 / 无动画）用 Compose 原生动画实现，
// 全部样式都受「动画速度」倍率控制，改设置后下一次导航即生效。

/** 可选的页面切换动画样式（key 与 SettingsRepository 持久化的字符串一致） */
val PageAnimOptions: List<Pair<String, String>> = listOf(
    "material" to "Material 共享轴（默认）",
    "slide" to "左右滑动",
    "fade" to "淡入淡出",
    "scale" to "缩放淡入",
    "sharedAxisY" to "上下共享轴",
    "none" to "无动画"
)

/** 可选的速度倍率（字符串用于持久化与下拉展示） */
val PageAnimSpeedOptions: List<Pair<String, String>> = listOf(
    "0.5" to "0.5x 很慢",
    "0.75" to "0.75x 慢",
    "1" to "1x 标准",
    "1.25" to "1.25x 快",
    "1.5" to "1.5x 很快",
    "2" to "2x 极快"
)

/** 深入型页面路由：与来源页存在层级关系，使用 SharedAxis X 过渡 */
internal val detailRoutes = setOf(
    "installed_versions",
    "version_settings/{versionId}",
    "installed_mods/{versionId}",
    "version_detail/{versionId}?type={versionType}&url={versionUrl}",
    "mod_detail/{projectId}?source={source}",
    "modpack_detail/{projectId}?source={source}",
    "resourcepack_detail/{projectId}?source={source}",
    "shader_detail/{projectId}?source={source}"
)

/** 页签过渡基础时长：略短于 Material 默认 300ms，缩短两页同帧绘制的窗口 */
private const val TAB_TRANSITION_MILLIS = 260
/** 详情页过渡基础时长：保持 Material 标准值，保留清晰的层级进入感 */
private const val DETAIL_TRANSITION_MILLIS = 300

/** 基础时长除以速度倍率：倍率越大越快，结果限制在 80~1200ms 避免极端值 */
private fun pageMillis(base: Int): Int {
    val speed = VersePersonalization.pageAnimSpeed.takeIf { it > 0f } ?: 1f
    return (base / speed).toInt().coerceIn(80, 1200)
}

internal fun navEnterTransition(
    detail: Boolean,
    pop: Boolean,
    slideDistance: Int
): EnterTransition {
    val d = pageMillis(if (detail) DETAIL_TRANSITION_MILLIS else TAB_TRANSITION_MILLIS)
    val spec = tween<Float>(d, easing = FastOutSlowInEasing)
    return when (VersePersonalization.pageAnimStyle) {
        "none" -> EnterTransition.None
        "slide" -> slideInHorizontally(
            animationSpec = tween(d, easing = FastOutSlowInEasing)
        ) { full -> if (pop) -full / 4 else full / 4 } + fadeIn(spec)
        "fade" -> fadeIn(spec)
        "scale" -> fadeIn(spec) + scaleIn(tween(d, easing = FastOutSlowInEasing), initialScale = 0.90f)
        "sharedAxisY" -> slideInVertically(
            animationSpec = tween(d, easing = FastOutSlowInEasing)
        ) { full -> if (pop) -full / 6 else full / 6 } + fadeIn(spec)
        else -> if (detail) {
            materialSharedAxisXIn(forward = !pop, slideDistance = slideDistance, durationMillis = d)
        } else {
            materialFadeThroughIn(durationMillis = d)
        }
    }
}

internal fun navExitTransition(
    detail: Boolean,
    pop: Boolean,
    slideDistance: Int
): ExitTransition {
    val d = pageMillis(if (detail) DETAIL_TRANSITION_MILLIS else TAB_TRANSITION_MILLIS)
    val spec = tween<Float>(d, easing = FastOutSlowInEasing)
    return when (VersePersonalization.pageAnimStyle) {
        "none" -> ExitTransition.None
        "slide" -> slideOutHorizontally(
            animationSpec = tween(d, easing = FastOutSlowInEasing)
        ) { full -> if (pop) full / 4 else -full / 4 } + fadeOut(spec)
        "fade" -> fadeOut(spec)
        "scale" -> fadeOut(spec) + scaleOut(tween(d, easing = FastOutSlowInEasing), targetScale = 0.94f)
        "sharedAxisY" -> slideOutVertically(
            animationSpec = tween(d, easing = FastOutSlowInEasing)
        ) { full -> if (pop) full / 6 else -full / 6 } + fadeOut(spec)
        else -> if (detail) {
            materialSharedAxisXOut(forward = !pop, slideDistance = slideDistance, durationMillis = d)
        } else {
            materialFadeThroughOut(durationMillis = d)
        }
    }
}
