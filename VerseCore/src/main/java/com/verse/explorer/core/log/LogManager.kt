package com.verse.explorer.core.log

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
    val source: String? = null,
    val throwable: Throwable? = null
)

enum class LogLevel(val displayName: String, val priority: Int) {
    VERBOSE("详细", 0),
    DEBUG("调试", 1),
    INFO("信息", 2),
    WARN("警告", 3),
    ERROR("错误", 4),
    FATAL("致命", 5)
}

class LogManager(private val logsDir: File) {
    
    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val maxEntries = 10000
    private var currentLogFile: File? = null
    private var minLogLevel = LogLevel.DEBUG
    
    private val _logFlow = MutableSharedFlow<LogEntry>(extraBufferCapacity = 64)
    val logFlow: SharedFlow<LogEntry> = _logFlow.asSharedFlow()
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    init {
        if (!logsDir.exists()) logsDir.mkdirs()
    }
    
    fun startNewLog(): File {
        val fileName = "verse_${dateFormat.format(Date())}.log"
        currentLogFile = File(logsDir, fileName)
        info("日志系统启动", "LogManager")
        return currentLogFile!!
    }
    
    fun setMinLogLevel(level: LogLevel) {
        minLogLevel = level
    }
    
    fun log(level: LogLevel, message: String, source: String? = null, throwable: Throwable? = null) {
        if (level.priority < minLogLevel.priority) return
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message,
            source = source,
            throwable = throwable
        )
        
        logEntries.offer(entry)
        
        while (logEntries.size > maxEntries) {
            logEntries.poll()
        }
        
        _logFlow.tryEmit(entry)
        
        writeToLogFile(entry)
    }
    
    fun verbose(message: String, source: String? = null) = log(LogLevel.VERBOSE, message, source)
    fun debug(message: String, source: String? = null) = log(LogLevel.DEBUG, message, source)
    fun info(message: String, source: String? = null) = log(LogLevel.INFO, message, source)
    fun warn(message: String, source: String? = null) = log(LogLevel.WARN, message, source)
    fun error(message: String, source: String? = null, throwable: Throwable? = null) = 
        log(LogLevel.ERROR, message, source, throwable)
    fun fatal(message: String, source: String? = null, throwable: Throwable? = null) = 
        log(LogLevel.FATAL, message, source, throwable)
    
    fun getEntries(): List<LogEntry> = logEntries.toList()
    
    fun getEntriesByLevel(level: LogLevel): List<LogEntry> {
        return logEntries.filter { it.level == level }
    }
    
    fun getEntriesSince(timestamp: Long): List<LogEntry> {
        return logEntries.filter { it.timestamp >= timestamp }
    }
    
    fun search(query: String): List<LogEntry> {
        val lowerQuery = query.lowercase()
        return logEntries.filter { 
            it.message.lowercase().contains(lowerQuery) ||
            it.source?.lowercase()?.contains(lowerQuery) == true
        }
    }
    
    fun clear() {
        logEntries.clear()
    }
    
    fun getLogFiles(): List<File> {
        if (!logsDir.exists()) return emptyList()
        return logsDir.listFiles()
            ?.filter { it.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
    
    fun deleteOldLogs(keepCount: Int = 10) {
        val logFiles = getLogFiles()
        if (logFiles.size > keepCount) {
            logFiles.drop(keepCount).forEach { it.delete() }
        }
    }
    
    fun exportLog(targetFile: File): Boolean {
        return try {
            targetFile.writeText(formatLogAsString())
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun exportLogAsJson(targetFile: File): Boolean {
        return try {
            val json = buildString {
                append("[")
                logEntries.forEachIndexed { index, entry ->
                    if (index > 0) append(",")
                    append("""
                        {
                            "timestamp": ${entry.timestamp},
                            "level": "${entry.level.name}",
                            "message": "${entry.message.replace("\"", "\\\"")}",
                            "source": ${entry.source?.let { "\"$it\"" } ?: "null"}
                        }
                    """.trimIndent())
                }
                append("]")
            }
            targetFile.writeText(json)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun writeToLogFile(entry: LogEntry) {
        try {
            currentLogFile?.appendText(formatEntry(entry) + "\n")
        } catch (e: Exception) {
            
        }
    }
    
    private fun formatLogAsString(): String {
        return logEntries.joinToString("\n") { formatEntry(it) }
    }
    
    private fun formatEntry(entry: LogEntry): String {
        val time = timeFormat.format(Date(entry.timestamp))
        val source = entry.source?.let { " [$it]" } ?: ""
        val throwable = entry.throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
        return "[$time] [${entry.level.displayName}]$source ${entry.message}$throwable"
    }
    
    companion object {
        @Volatile
        private var instance: LogManager? = null
        
        fun getInstance(logsDir: File): LogManager {
            return instance ?: synchronized(this) {
                instance ?: LogManager(logsDir).also { instance = it }
            }
        }
    }
}

object Logger {
    private var logManager: LogManager? = null
    
    fun init(logsDir: File) {
        logManager = LogManager.getInstance(logsDir)
    }
    
    fun v(message: String, source: String? = null) = logManager?.verbose(message, source)
    fun d(message: String, source: String? = null) = logManager?.debug(message, source)
    fun i(message: String, source: String? = null) = logManager?.info(message, source)
    fun w(message: String, source: String? = null) = logManager?.warn(message, source)
    fun e(message: String, source: String? = null, throwable: Throwable? = null) = 
        logManager?.error(message, source, throwable)
    fun f(message: String, source: String? = null, throwable: Throwable? = null) = 
        logManager?.fatal(message, source, throwable)
}
