package com.verse.explorer.app.game

import android.app.AlertDialog
import androidx.activity.ComponentActivity
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.game.GameStatusOverlay
import com.verse.explorer.launcher.crash.CrashResult

class LaunchErrorDialog(
    private val activity: ComponentActivity,
    private val statusOverlay: GameStatusOverlay,
    private val logManager: GameLogManager
) {

    fun showCrashResultDialog(
        code: Int,
        result: CrashResult,
        crashReport: String?,
        isAlive: Boolean,
        onRestart: () -> Unit
    ) {
        if (!isAlive) {
            VerseLogger.error("showCrashResultDialog skipped: activity not alive")
            return
        }
        statusOverlay.hide()

        val message = buildString {
            appendLine("退出码: $code")
            appendLine("崩溃类型: ${result.message}")
            if (result.suggestion.isNotEmpty()) {
                appendLine()
                appendLine("修复建议: ${result.suggestion}")
            }
            if (result.details.isNotEmpty()) {
                appendLine()
                appendLine("=== 日志详情 ===")
                appendLine(result.details.take(1500))
            }
            if (!crashReport.isNullOrEmpty()) {
                appendLine()
                appendLine("=== 崩溃报告 ===")
                appendLine(crashReport.take(2000))
            }
        }

        val title = if (result.isCrash) "游戏崩溃 - ${result.message}" else "游戏异常退出 (code=$code)"

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("导出日志") { _, _ ->
                logManager.exportLog()
            }
            .setNeutralButton("重新启动") { _, _ ->
                onRestart()
            }
            .setNegativeButton("关闭") { _, _ ->
                activity.finish()
            }
            .setCancelable(false)
            .show()
    }

    fun showCrashDialog(
        title: String,
        message: String,
        details: String,
        suggestion: String,
        isAlive: Boolean
    ) {
        if (!isAlive) {
            VerseLogger.error("showCrashDialog skipped: activity not alive")
            return
        }
        statusOverlay.hide()

        val fullMessage = buildString {
            appendLine(message)
            if (suggestion.isNotEmpty()) {
                appendLine()
                appendLine("建议: $suggestion")
            }
            if (details.isNotEmpty()) {
                appendLine()
                appendLine("=== 详情 ===")
                appendLine(details.take(1000))
            }
        }

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(fullMessage)
            .setPositiveButton("导出日志") { _, _ ->
                logManager.exportLog()
            }
            .setNegativeButton("关闭") { _, _ ->
                activity.finish()
            }
            .setCancelable(false)
            .show()
    }
}
