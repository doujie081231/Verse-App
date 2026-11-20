package com.verse.explorer.app.horizon

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 准备流程的执行日志。
 *
 * 真机上出问题时，截图只能看到一句概括；这里把每步（HTTP 状态、java 退出码、引擎状态、异常栈）
 * 都落到内存环 + 文件，页面直接显示尾部并支持复制——排障不用再靠猜。
 */
object HorizonPrepLog {

    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun append(message: String) {
        val stamp = timeFormat.format(Date())
        lines.addLast("$stamp  $message")
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized
    fun append(context: Context?, message: String) {
        append(message)
        if (context == null) return
        runCatching {
            val file = File(HorizonPrep.home(context), "prep.log")
            file.parentFile?.mkdirs()
            file.appendText("${timeFormat.format(Date())}  $message\n")
        }
    }

    @Synchronized
    fun tail(count: Int = 8): String =
        lines.toList().takeLast(count).joinToString("\n")

    @Synchronized
    fun all(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() = lines.clear()
}
