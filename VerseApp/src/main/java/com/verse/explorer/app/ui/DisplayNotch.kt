package com.verse.explorer.app.ui

import android.app.Activity
import android.os.Build
import android.view.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.verse.explorer.core.data.SettingsRepository

/**
 * 刘海屏区域处理：开启「忽略刘海（全屏）」时画面铺满（含刘海区域），
 * 关闭时画面在刘海侧缩小避让（内容容器让出 displayCutout 区域）。
 * 状态响应式（mutableStateOf），启动器与游戏共用；
 * 持久化到 SettingsRepository，变更即时生效、下次启动保持。
 *
 * 不使用 layoutInDisplayCutoutMode=NEVER：targetSdk 35（Android 15 强制
 * edge-to-edge）会把它覆盖回 ALWAYS，改用内容容器 padding 实现同等避让。
 */
object DisplayNotch {

    /** 忽略刘海（全屏）开关，默认关闭（避让缩小） */
    var ignoreNotch by mutableStateOf(false)
        private set

    /** 启动时从持久化加载 */
    fun load() {
        ignoreNotch = SettingsRepository.getIgnoreNotch()
    }

    /** 切换开关：持久化 + 更新内存状态（Compose 方自动重组） */
    fun updateIgnoreNotch(value: Boolean) {
        ignoreNotch = value
        SettingsRepository.setIgnoreNotch(value)
    }

    /** 当前窗口的 displayCutout 左侧 inset（px）；无刘海时为 0 */
    fun cutoutLeft(activity: Activity): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        return activity.windowManager.currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.displayCutout()).left
    }
}