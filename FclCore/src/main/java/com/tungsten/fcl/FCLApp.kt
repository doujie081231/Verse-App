package com.tungsten.fcl

import android.content.Context

/**
 * 宿主 shim（直搬 FCL 时替换 fcl 模块的 FCLApp）。
 * 由 VerseApp 在 Application.onCreate 注入 appContext；fclcore 直接搬来的
 * 任务代码通过 FCLApp.getAppContext() 读取错误提示等字符串。
 */
object FCLApp {

    @Volatile
    private var sAppContext: Context? = null

    fun attach(context: Context) {
        sAppContext = context.applicationContext
    }

    @JvmStatic
    fun getAppContext(): Context =
        sAppContext ?: throw IllegalStateException("FCLApp 未初始化（VerseApp 需在 Application.onCreate 调用 attach）")
}