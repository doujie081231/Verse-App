package com.verse.explorer.app.game

import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.view.View
import com.verse.explorer.app.ui.VerseLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GameLogManager(private val activity: ComponentActivity) {

    private var statusLog: TextView? = null
    private var statusScrollView: ScrollView? = null

    var javaHomeProvider: () -> String? = { null }

    fun initGameLog(gameDirPath: String, versionId: String?, javaVersion: Int, launchMethod: String) {
        try {
            val gameDir = File(gameDirPath)
            VerseLogger.init(gameDir)
            // 镜像到外部存储可访问路径：/storage/emulated/0/Android/data/<pkg>/files/VerseExplorerX/logs/
            // 用户无需 root 即可通过文件管理器/远程工具取回完整启动日志。
            try {
                val extBase = activity.getExternalFilesDir(null)
                if (extBase != null) {
                    val extLogDir = File(File(extBase, "VerseExplorerX"), "logs")
                    extLogDir.mkdirs()
                    VerseLogger.setMirrorFile(File(extLogDir, "launch.log"))
                }
            } catch (e: Exception) {
                Log.e("GameLogManager", "外部镜像日志挂载失败: ${e.message}")
            }

            VerseLogger.info("==========================================", "GameActivity")
            VerseLogger.info("Verse 游戏启动 - 详细日志", "GameActivity")
            VerseLogger.info("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}", "GameActivity")
            VerseLogger.info("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", "GameActivity")
            VerseLogger.info("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})", "GameActivity")
            VerseLogger.info("ABI: ${android.os.Build.SUPPORTED_ABIS?.joinToString()}", "GameActivity")
            VerseLogger.info("版本: $versionId, Java: $javaVersion, 启动方式: $launchMethod", "GameActivity")
            VerseLogger.info("游戏目录: $gameDirPath", "GameActivity")
            VerseLogger.info("内部日志: ${VerseLogger.logState.value.logPath}", "GameActivity")
            VerseLogger.info("==========================================", "GameActivity")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun attachLogViews(logView: TextView, scrollView: ScrollView) {
        statusLog = logView
        statusScrollView = scrollView
    }

    fun appendStatusLog(message: String) {
        VerseLogger.info(message.trimEnd(), "GameActivity")
        activity.runOnUiThread {
            statusLog?.append(message)
            statusScrollView?.post {
                statusScrollView?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    fun exportLog() {
        try {
            val logDir = File(activity.getExternalFilesDir(null), "VerseExplorerX/logs")
            logDir.mkdirs()
            val logFile = File(logDir, "verse_debug_${System.currentTimeMillis()}.log")
            val sb = StringBuilder()
            sb.appendLine("=== Verse Explorer X Debug Log ===")
            sb.appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            sb.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            sb.appendLine("ABI: ${android.os.Build.SUPPORTED_ABIS?.joinToString()}")
            sb.appendLine()

            sb.appendLine("=== Internal Storage ===")
            val internalNatives = File(activity.filesDir, "natives")
            sb.appendLine("Internal natives dir: ${internalNatives.absolutePath}")
            sb.appendLine("Exists: ${internalNatives.exists()}")
            internalNatives.listFiles()?.forEach { sb.appendLine("  ${it.name} (${it.length()} bytes)") }
            sb.appendLine()

            val internalTmp = File(activity.filesDir, "tmp")
            sb.appendLine("Internal tmp dir: ${internalTmp.absolutePath}")
            sb.appendLine("Exists: ${internalTmp.exists()}")
            sb.appendLine()

            val internalRuntime = File(activity.filesDir, "runtime")
            sb.appendLine("Internal runtime dir: ${internalRuntime.absolutePath}")
            sb.appendLine("Exists: ${internalRuntime.exists()}")
            internalRuntime.walkTopDown().maxDepth(3).forEach {
                sb.appendLine("  ${it.absolutePath} ${if (it.isDirectory) "/" else "(${it.length()} bytes)"}")
            }
            sb.appendLine()

            val pluginsDir = File(activity.filesDir, "plugins")
            sb.appendLine("Plugins dir: ${pluginsDir.absolutePath}")
            sb.appendLine("Exists: ${pluginsDir.exists()}")
            pluginsDir.listFiles()?.forEach { sb.appendLine("  ${it.name} (${it.length()} bytes)") }
            sb.appendLine()

            sb.appendLine("=== JRE ===")
            val javaHome = javaHomeProvider()
            if (javaHome != null) {
                val jreDir = File(javaHome)
                sb.appendLine("JRE dir: ${jreDir.absolutePath}")
                sb.appendLine("Exists: ${jreDir.exists()}")
                val libDir = File(jreDir, "lib")
                sb.appendLine("lib/ exists: ${libDir.exists()}")
                val freetype = File(libDir, "libfreetype.so")
                sb.appendLine("libfreetype.so: ${freetype.exists()} (${if (freetype.exists()) freetype.length() else 0} bytes)")
                val aarch64Dir = File(libDir, "aarch64")
                sb.appendLine("lib/aarch64/ exists: ${aarch64Dir.exists()}")
                if (aarch64Dir.exists()) {
                    val ft2 = File(aarch64Dir, "libfreetype.so")
                    sb.appendLine("  libfreetype.so: ${ft2.exists()} (${if (ft2.exists()) ft2.length() else 0} bytes)")
                }
            }
            sb.appendLine()

            sb.appendLine("=== External Storage ===")
            val gameDir = File(activity.getExternalFilesDir(null), "VerseExplorerX")
            sb.appendLine("Game dir: ${gameDir.absolutePath}")
            sb.appendLine("Exists: ${gameDir.exists()}")
            val extNatives = File(gameDir, "natives")
            sb.appendLine("External natives: ${extNatives.absolutePath}")
            sb.appendLine("Exists: ${extNatives.exists()}")
            extNatives.listFiles()?.forEach { sb.appendLine("  ${it.name} (${it.length()} bytes)") }
            sb.appendLine()

            sb.appendLine("=== Game Log ===")
            val gameLogFile = File(gameDir, "logs/game.log")
            if (gameLogFile.exists()) {
                val fullLog = gameLogFile.readText()
                if (fullLog.length > 50000) {
                    sb.appendLine(fullLog.takeLast(30000))
                    sb.appendLine()
                    sb.appendLine("... (日志共 ${fullLog.length} 字符, 已截取末尾 30000 字符)")
                } else {
                    sb.appendLine(fullLog)
                }
            } else {
                sb.appendLine("No game.log found")
            }
            sb.appendLine()

            sb.appendLine("=== Status Log ===")
            sb.appendLine(statusLog?.text ?: "empty")
            sb.appendLine()

            sb.appendLine("=== Native Debug Log ===")
            val nativeLogFile = File(activity.getExternalFilesDir(null), "VerseExplorerX/logs/native_debug.log")
            if (nativeLogFile.exists()) {
                try {
                    val nativeLog = nativeLogFile.readText()
                    if (nativeLog.length > 80000) {
                        sb.appendLine(nativeLog.takeLast(50000))
                        sb.appendLine()
                        sb.appendLine("... (原生日志共 ${nativeLog.length} 字符, 已截取末尾 50000 字符)")
                    } else {
                        sb.appendLine(nativeLog)
                    }
                } catch (e: Exception) {
                    sb.appendLine("读取失败: ${e.message}")
                }
            } else {
                sb.appendLine("No native_debug.log found at ${nativeLogFile.absolutePath}")
            }

            logFile.writeText(sb.toString())
            Toast.makeText(activity, "日志已导出到: ${logFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "导出日志失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
