package com.verse.explorer.app.game

import androidx.activity.ComponentActivity
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.launcher.crash.CrashDetector
import com.verse.explorer.launcher.crash.CrashResult
import com.verse.explorer.launcher.crash.CrashType
import java.io.File

class LaunchCrashAnalyzer(private val activity: ComponentActivity) {

    fun analyzeCrash(currentGameDir: File?): CrashResult {
        val logFile = findLatestLogFile(currentGameDir) ?: run {
            return CrashResult(
                isCrash = true,
                crashType = CrashType.UNKNOWN,
                message = "未找到日志文件",
                details = "无法定位游戏日志文件，无法分析崩溃原因"
            )
        }

        VerseLogger.info("开始分析崩溃日志: ${logFile.absolutePath}", TAG)
        return CrashDetector.detectCrash(logFile)
    }

    private fun findLatestLogFile(gameDir: File?): File? {
        val currentGameDir = gameDir ?: return null

        val latestLog = File(currentGameDir, "logs/latest.log")
        if (latestLog.exists() && latestLog.length() > 0) return latestLog

        val gameLog = File(currentGameDir, "logs/game.log")
        if (gameLog.exists() && gameLog.length() > 0) return gameLog

        val extLog = File(activity.getExternalFilesDir(null), "VerseExplorerX/logs/game.log")
        if (extLog.exists() && extLog.length() > 0) return extLog

        val debugLog = File(activity.filesDir, "verse_debug.log")
        if (debugLog.exists() && debugLog.length() > 0) return debugLog

        val logsDir = File(currentGameDir, "logs")
        if (logsDir.exists()) {
            return logsDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".log") }
                ?.maxByOrNull { it.lastModified() }
        }

        return null
    }

    companion object {
        private const val TAG = "LaunchCrashAnalyzer"
    }
}
