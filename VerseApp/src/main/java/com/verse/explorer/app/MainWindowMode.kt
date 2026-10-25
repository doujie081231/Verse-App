package com.verse.explorer.app

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

/**
 * 应用「启动器部分屏幕全屏」与「忽略刘海」相互独立组合后的窗口显示模式：
 *  - 全屏开关只控制系统栏（状态栏/导航栏）是否沉浸隐藏；
 *  - 忽略刘海只控制内容是否延伸进刘海/挖孔区域，与全屏开关互不影响。
 */
internal fun applyWindowDisplayMode(activity: ComponentActivity, fullScreen: Boolean, ignoreNotch: Boolean) {
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
        WindowCompat.setDecorFitsSystemWindows(window, true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
    // 刘海延伸只由「忽略刘海」决定：全屏开启也不会顺带占满刘海区
    setDisplayCutoutMode(activity, extendToCutout = ignoreNotch)
}

/** 刘海显示模式：延伸=ALWAYS（API 30+ 全朝向）/SHORT_EDGES；避让=NEVER */
private fun setDisplayCutoutMode(activity: ComponentActivity, extendToCutout: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val mode = if (extendToCutout) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
        val params = activity.window.attributes
        if (params.layoutInDisplayCutoutMode != mode) {
            params.layoutInDisplayCutoutMode = mode
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            activity.window.attributes = params
        }
    }
}
