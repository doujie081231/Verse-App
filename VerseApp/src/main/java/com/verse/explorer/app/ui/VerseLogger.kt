package com.verse.explorer.app.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VerseLogger {

    private val _logState = MutableStateFlow(LogState())
    val logState: StateFlow<LogState> = _logState.asStateFlow()

    private var logWriter: PrintWriter? = null
    private var logFile: File? = null
    // 外部镜像日志（外部存储可访问路径，便于用户直接取回/远程工具拉取）
    private var mirrorWriter: PrintWriter? = null
    private var mirrorFile: File? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // UI 状态批量刷新：刷日志高峰期（游戏启动）每行一次列表拷贝 + 全收集者重组会把
    // 主线程拖死，改为 250ms 防抖合并，积压超过 200 条立即刷新
    private val pendingEntries = ArrayList<LogEntry>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var flushScheduled = false

    data class LogState(
        val entries: List<LogEntry> = emptyList(),
        val logPath: String = ""
    )

    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    )

    fun init(gameDir: File) {
        try {
            val logDir = File(gameDir, "logs")
            logDir.mkdirs()
            logFile = File(logDir, "latest.log")
            logWriter = PrintWriter(FileWriter(logFile!!, true), true)
            _logState.value = _logState.value.copy(logPath = logFile!!.absolutePath)
        } catch (e: Exception) {
            Log.e("VerseLogger", "Failed to init log file", e)
        }
    }

    /**
     * 挂载外部镜像日志文件：与内部 latest.log 同步写入，便于用户不 root 即可取回日志。
     * 调用方负责确保父目录已创建。
     */
    fun setMirrorFile(file: File) {
        try {
            closeMirror()
            mirrorFile = file
            mirrorWriter = PrintWriter(FileWriter(file, true), true)
            Log.i("VerseLogger", "mirror log: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("VerseLogger", "Failed to init mirror log: ${file.absolutePath}", e)
        }
    }

    private fun closeMirror() {
        try {
            mirrorWriter?.flush()
            mirrorWriter?.close()
        } catch (_: Exception) {}
        mirrorWriter = null
    }

    fun info(message: String, tag: String = "VerseX") {
        log("INFO", tag, message)
    }

    fun warn(message: String, tag: String = "VerseX") {
        log("WARN", tag, message)
    }

    fun error(message: String, tag: String = "VerseX") {
        log("ERROR", tag, message)
    }

    fun debug(message: String, tag: String = "VerseX") {
        // 高频调试日志（触摸/指针等逐帧事件）轻量化：只输出到 logcat，不写文件、不进 StateFlow。
        // 否则滑动时每帧会触发 2 次磁盘 flush + logState 状态更新（UI 重组），把输入线程拖死导致卡顿。
        Log.d("VerseX", "[${dateFormat.format(Date())}] [$tag/DEBUG] $message")
    }

    private fun log(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val line = "[$timestamp] [$tag/$level] $message"
        Log.d("VerseX", line)
        try {
            logWriter?.println(line)
            logWriter?.flush()
        } catch (_: Exception) {}
        try {
            mirrorWriter?.println(line)
            mirrorWriter?.flush()
        } catch (_: Exception) {}

        val flushNow = synchronized(pendingEntries) {
            pendingEntries.add(LogEntry(timestamp, level, tag, message))
            pendingEntries.size >= 200
        }
        if (flushNow) {
            flushLogState()
        } else if (!flushScheduled) {
            flushScheduled = true
            mainHandler.postDelayed({ flushLogState() }, 250)
        }
    }

    private fun flushLogState() {
        flushScheduled = false
        synchronized(pendingEntries) {
            if (pendingEntries.isEmpty()) return
            val batch = ArrayList(pendingEntries)
            pendingEntries.clear()
            _logState.value = _logState.value.copy(
                entries = (_logState.value.entries + batch).takeLast(2000)
            )
        }
    }

    fun clear() {
        synchronized(pendingEntries) { pendingEntries.clear() }
        _logState.value = LogState(entries = emptyList(), logPath = _logState.value.logPath)
        try {
            logFile?.writeText("")
        } catch (_: Exception) {}
    }

    fun close() {
        try {
            logWriter?.flush()
            logWriter?.close()
        } catch (_: Exception) {}
        logWriter = null
        closeMirror()
    }
}
