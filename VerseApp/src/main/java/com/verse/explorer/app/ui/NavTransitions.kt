package com.verse.explorer.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut

// 页面切换动画（Material 动效规范，fornewid/material-motion-compose）
// 顶层页签之间无强关联 → FadeThrough；详情/深入页面有层级关系 → SharedAxis X；
// 返回（pop）方向取反，页签时长略短于标准值以减少两页同帧绘制，详情页保留标准 300ms

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

/** 页签过渡时长：略短于 Material 默认 300ms，缩短两页同帧绘制的窗口，切换更跟手 */
private const val TAB_TRANSITION_MILLIS = 260
/** 详情页过渡时长：保持 Material 标准值，保留清晰的层级进入感 */
private const val DETAIL_TRANSITION_MILLIS = 300

internal fun navEnterTransition(
    detail: Boolean,
    pop: Boolean,
    slideDistance: Int
): EnterTransition = if (detail) {
    materialSharedAxisXIn(
        forward = !pop,
        slideDistance = slideDistance,
        durationMillis = DETAIL_TRANSITION_MILLIS
    )
} else {
    materialFadeThroughIn(durationMillis = TAB_TRANSITION_MILLIS)
}

internal fun navExitTransition(
    detail: Boolean,
    pop: Boolean,
    slideDistance: Int
): ExitTransition = if (detail) {
    materialSharedAxisXOut(
        forward = !pop,
        slideDistance = slideDistance,
        durationMillis = DETAIL_TRANSITION_MILLIS
    )
} else {
    materialFadeThroughOut(durationMillis = TAB_TRANSITION_MILLIS)
}
