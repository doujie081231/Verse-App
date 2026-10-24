package com.verse.explorer.app

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

/**
 * 应用「启动器部分屏幕全屏」与「忽略刘海」组合后的窗口显示模式：
 *  - 全屏开=沉浸式全屏（隐藏系统栏）并延伸到刘海区域（SHORT_EDGES/ALWAYS）；
 *  - 全屏关但忽略刘海开=系统栏保留悬浮，内容仍延伸到刘海/系统栏区域（edge-to-edge）；
 *  - 两者都关=退出沉浸、内容避让系统栏与刘海。
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
        setDisplayCutoutMode(activity, extendToCutout = true)
    } else if (ignoreNotch) {
        // 不隐藏系统栏，但内容延伸到刘海/系统栏后方（时间悬浮显示在画面上）
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
        setDisplayCutoutMode(activity, extendToCutout = true)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        setDisplayCutoutMode(activity, extendToCutout = false)
    }
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
