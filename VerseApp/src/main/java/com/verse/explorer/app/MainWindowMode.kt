package com.verse.explorer.app

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 应用「启动器部分屏幕全屏」与「忽略刘海」相互独立组合后的窗口显示模式：
 *  - 全屏开关只控制系统栏（状态栏/导航栏）是否沉浸隐藏；
 *  - 忽略刘海只控制内容是否延伸进刘海/挖孔区域，与全屏开关互不影响。
 */
internal fun applyWindowDisplayMode(activity: ComponentActivity, fullScreen: Boolean, ignoreNotch: Boolean) {
    com.verse.explorer.app.ui.VerseLogger.debug(
        "applyWindowDisplayMode fullScreen=$fullScreen ignoreNotch=$ignoreNotch",
        "Window"
    )
    val window = activity.window
    if (fullScreen) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        }
    } else if (ignoreNotch) {
        // 不隐藏系统栏，但内容延伸到系统栏后方（时间悬浮显示在画面上）
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
    // 刘海延伸只由「忽略刘海」决定：全屏开启也不会顺带占满刘海区
    setDisplayCutoutMode(activity, extendToCutout = ignoreNotch)
}

/**
 * 协议页的强制沉浸全屏：与「启动器全屏」设置无关，同意协议后由
 * [applyWindowDisplayMode] 按用户设置恢复。协议页是首次启动的第一屏，
 * 留一条状态栏会让它看起来像"弹窗"而不是整页。
 */
internal fun applyEulaImmersive(activity: ComponentActivity, ignoreNotch: Boolean) {
    val window = activity.window
    window.setFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN
    )
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    } else {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    // 协议正文自身有内边距，整页铺满挖孔区才是"全屏"观感，因此这里始终延伸
    setDisplayCutoutMode(activity, extendToCutout = true)
}

/**
 * 刘海显示模式：延伸=SHORT_EDGES（API 28+ 横屏左右/竖屏上下短边），避让=NEVER。
 * 部分 OEM（华为/荣耀等）只实现 SHORT_EDGES，对 ALWAYS 不生效，横屏侧挖孔仍会内缩；
 * 统一使用 SHORT_EDGES 可覆盖这些机型。
 */
private fun setDisplayCutoutMode(activity: ComponentActivity, extendToCutout: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val mode = if (extendToCutout) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
        val params = activity.window.attributes
        if (params.layoutInDisplayCutoutMode != mode) {
            params.layoutInDisplayCutoutMode = mode
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            activity.window.attributes = params
            // 属性变更后强制重新布局，部分机型不会主动重算 displayCutout inset
            activity.window.decorView.requestLayout()
        }
    }
}
