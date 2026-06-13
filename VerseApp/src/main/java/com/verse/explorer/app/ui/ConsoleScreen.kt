package com.verse.explorer.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.config.GameConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LogEntry(
    val raw: String,
    val timestamp: String = "",
    val level: String = "INFO",
    val thread: String = "",
    val message: String = "",
    val isError: Boolean = false,
    val isWarning: Boolean = false,
    val isException: Boolean = false
)

private fun parseLogLine(line: String): LogEntry {
    if (line.isBlank()) return LogEntry(raw = line)
    val timeMatch = Regex("""\[(\d{2}:\d{2}:\d{2})]""").find(line)
    val timestamp = timeMatch?.groupValues?.get(1) ?: ""
    val threadMatch = Regex("""\[(\w[\w\s]*?)/(\w+)]""").find(line)
    val thread = threadMatch?.groupValues?.get(1) ?: ""
    val level = threadMatch?.groupValues?.get(2) ?: detectLevel(line)
    val messagePart = if (threadMatch != null) {
        val msgStart = threadMatch.range.last + 1
        if (msgStart < line.length) line.substring(msgStart).trimStart(':') else ""
    } else {
        if (timeMatch != null) {
            val msgStart = timeMatch.range.last + 1
            if (msgStart < line.length) line.substring(msgStart).trimStart(':') else ""
        } else line
    }
    val isError = level == "ERROR" || level == "FATAL" ||
        line.contains("Exception", ignoreCase = true) ||
        line.contains("Error:", ignoreCase = true) ||
        line.startsWith("\tat ") || line.startsWith("Caused by:")
    val isWarning = level == "WARN"
    val isException = isError && (line.contains("Exception") || line.contains("\tat ") || line.contains("Caused by:"))
    return LogEntry(line, timestamp, level, thread, messagePart, isError, isWarning, isException)
}

private fun detectLevel(line: String): String {
    return when {
        line.contains("[ERROR]") || line.contains("/ERROR") -> "ERROR"
        line.contains("[WARN]") || line.contains("/WARN") -> "WARN"
        line.contains("[INFO]") || line.contains("/INFO") -> "INFO"
        line.contains("[DEBUG]") || line.contains("/DEBUG") -> "DEBUG"
        else -> "INFO"
    }
}

@Composable
fun ConsoleScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var logLines by remember { mutableStateOf(listOf<String>()) }
    var parsedLogs by remember { mutableStateOf(listOf<LogEntry>()) }
    var searchQuery by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf("ALL") }
    var autoRefresh by remember { mutableStateOf(true) }
    var autoScroll by remember { mutableStateOf(true) }
    var viewMode by remember { mutableIntStateOf(0) }
    var lastSize by remember { mutableStateOf(0L) }
    var logFilePath by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val getLogFile = { mode: Int ->
        when (mode) {
            1 -> File(context.filesDir, "verse_debug.log")
            else -> {
                val gameDir = GameConfig.getGameDir(context)
                val candidates = listOf(
                    File(gameDir, "logs/latest.log"),
                    File(gameDir, "logs/game.log"),
                    File(gameDir, "logs/launcher_log.txt")
                )
                candidates.firstOrNull { it.exists() && it.length() > 0 } ?: candidates[0]
            }
        }
    }

    val loadLog = suspend {
        withContext(Dispatchers.IO) {
            try {
                val logFile = getLogFile(viewMode)
                if (logFile.exists()) {
                    logFilePath = logFile.absolutePath
                    val allLines = logFile.readLines().takeLast(3000)
                    lastSize = logFile.length()
                    allLines
                } else {
                    logFilePath = logFile.absolutePath
                    lastSize = 0L
                    emptyList()
                }
            } catch (e: Exception) {
                listOf("读取日志失败: ${e.message}")
            }
        }
    }

    val readNewLines = suspend {
        withContext(Dispatchers.IO) {
            try {
                val logFile = getLogFile(viewMode)
                if (logFile.exists()) {
                    logFilePath = logFile.absolutePath
                    val currentSize = logFile.length()
                    if (currentSize < lastSize) {
                        lastSize = 0L
                    }
                    if (currentSize > lastSize) {
                        RandomAccessFile(logFile, "r").use { raf ->
                            raf.seek(lastSize)
                            val newLines = mutableListOf<String>()
                            var line: String?
                            while (raf.readLine().also { line = it } != null) {
                                newLines.add(line!!)
                            }
                            lastSize = currentSize
                            if (newLines.isNotEmpty()) newLines else null
                        }
                    } else null
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun refreshLogs() {
        scope.launch {
            lastSize = 0L
            val loaded = loadLog()
            logLines = loaded
        }
    }

    fun exportLog() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val exportDir = File(context.getExternalFilesDir(null), "VerseExplorerX/logs")
                    exportDir.mkdirs()
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val exportFile = File(exportDir, "terminal_export_${ts}.log")
                    exportFile.writeText(logLines.joinToString("\n"))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已导出 ${logLines.size} 行到\n${exportFile.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearLogs() {
        logLines = emptyList()
        parsedLogs = emptyList()
        lastSize = 0L
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val logFile = getLogFile(viewMode)
                    if (logFile.exists()) logFile.writeText("")
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        logLines = loadLog()
    }

    LaunchedEffect(viewMode) {
        lastSize = 0L
        logLines = loadLog()
    }

    LaunchedEffect(autoRefresh) {
        if (!autoRefresh) return@LaunchedEffect
        while (autoRefresh) {
            delay(1000)
            isRefreshing = true
            val newLines = readNewLines()
            if (newLines != null && newLines.isNotEmpty()) {
                logLines = (logLines + newLines).takeLast(3000)
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(logLines) {
        parsedLogs = logLines.map { parseLogLine(it) }
    }

    val filteredLogs = remember(parsedLogs, searchQuery, levelFilter) {
        parsedLogs.filter { entry ->
            val matchesLevel = levelFilter == "ALL" || entry.level == levelFilter ||
                (levelFilter == "ERROR" && entry.isError) ||
                (levelFilter == "WARN" && entry.isWarning)
            val matchesSearch = searchQuery.isBlank() ||
                entry.raw.contains(searchQuery, ignoreCase = true)
            matchesLevel && matchesSearch
        }
    }

    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    val bg = Color(0xFFFAFAFA)
    val cardBg = Color(0xFFFFFFFF)
    val textPri = Color(0xFF1A1A2E)
    val textSec = Color(0xFF4A4A5E)
    val textMuted = Color(0xFF787890)
    val border = Color(0xFFE2E5EA)
    val accent = Color(0xFF5D8C3E)

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("终端", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPri)
                    Text(
                        if (logFilePath.isNotEmpty()) logFilePath else "终端控制台 - 白色简约版",
                        fontSize = 12.sp, color = textMuted
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val text = logLines.joinToString("\n")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("verse_log", text))
                            Toast.makeText(context, "已复制 ${logLines.size} 行", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textSec)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制日志", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { exportLog() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textSec)
                    ) {
                        Icon(Icons.Outlined.FileDownload, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("导出日志", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { clearLogs() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC3545).copy(alpha = 0.08f),
                            contentColor = Color(0xFFDC3545)
                        )
                    ) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重置", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索日志关键字...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = textMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Filled.Close, null, Modifier.size(14.dp), tint = textMuted)
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent.copy(alpha = 0.4f),
                        unfocusedBorderColor = border,
                        focusedContainerColor = cardBg,
                        unfocusedContainerColor = Color(0xFFF5F7FA),
                        focusedTextColor = textPri,
                        unfocusedTextColor = textPri,
                        cursorColor = accent,
                        focusedPlaceholderColor = textMuted,
                        unfocusedPlaceholderColor = textMuted
                    )
                )

                val levels = listOf("ALL", "INFO", "WARN", "ERROR")
                levels.forEach { level ->
                    val selected = levelFilter == level
                    val chipColor = when (level) {
                        "INFO" -> Color(0xFF3F76E4)
                        "WARN" -> Color(0xFFFFC107)
                        "ERROR" -> Color(0xFFDC3545)
                        else -> accent
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) chipColor else Color(0xFFF0F2F5),
                        modifier = Modifier.clickable { levelFilter = level }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (level != "ALL") {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (selected) Color.White.copy(alpha = 0.8f) else chipColor)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                level,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color.White else textSec
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = cardBg,
                border = BorderStroke(1.dp, border)
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Terminal, null, Modifier.size(48.dp), tint = textMuted.copy(alpha = 0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (logLines.isEmpty()) "暂无日志" else "无匹配结果",
                                fontSize = 14.sp, color = textMuted, fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (logLines.isEmpty()) "启动游戏后，日志将在此处显示" else "尝试调整搜索条件或日志级别",
                                fontSize = 12.sp, color = textMuted.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            items(filteredLogs) { entry ->
                                ConsoleLogRow(entry)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF5F7FA),
                border = BorderStroke(1.dp, border)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (autoRefresh) Color(0xFF28A745) else textMuted)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (autoRefresh) "监听中" else "已暂停",
                        fontSize = 12.sp, color = textSec
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "· ${filteredLogs.size}/${logLines.size} 行",
                        fontSize = 11.sp, color = textMuted
                    )
                    Spacer(Modifier.weight(1f))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (autoRefresh) accent else Color(0xFFF0F2F5),
                            modifier = Modifier.clickable { autoRefresh = !autoRefresh }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (autoRefresh) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    null,
                                    Modifier.size(12.dp),
                                    tint = if (autoRefresh) Color.White else textSec
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (autoRefresh) "暂停" else "继续",
                                    fontSize = 11.sp,
                                    color = if (autoRefresh) Color.White else textSec
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF0F2F5),
                            modifier = Modifier.clickable {
                                autoScroll = !autoScroll
                                if (autoScroll && filteredLogs.isNotEmpty()) {
                                    scope.launch { listState.animateScrollToItem(filteredLogs.size - 1) }
                                }
                            }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (autoScroll) Icons.Outlined.VerticalAlignBottom else Icons.Outlined.VerticalAlignTop,
                                    null,
                                    Modifier.size(12.dp),
                                    tint = accent
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (autoScroll) "自动滚底" else "手动浏览",
                                    fontSize = 11.sp,
                                    color = textSec
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF0F2F5),
                            modifier = Modifier.clickable { refreshLogs() }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Refresh, null, Modifier.size(12.dp), tint = accent)
                                Spacer(Modifier.width(4.dp))
                                Text("刷新", fontSize = 11.sp, color = textSec)
                            }
                        }
                        val viewModes = listOf("游戏", "调试")
                        viewModes.forEachIndexed { idx, label ->
                            val active = viewMode == idx
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (active) accent else Color(0xFFF0F2F5),
                                modifier = Modifier.clickable { viewMode = idx }
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    color = if (active) Color.White else textSec,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConsoleLogRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        "ERROR", "FATAL" -> Color(0xFFDC3545)
        "WARN" -> Color(0xFFFF8C00)
        "DEBUG" -> Color(0xFF787890)
        "INFO" -> Color(0xFF5D8C3E)
        else -> Color(0xFF1A1A2E)
    }

    val rowBg = when {
        entry.isError && !entry.isException -> Color(0xFFDC3545).copy(alpha = 0.06f)
        entry.isException -> Color(0xFFDC3545).copy(alpha = 0.04f)
        entry.isWarning -> Color(0xFFFFC107).copy(alpha = 0.05f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(vertical = 1.dp, horizontal = 4.dp)
    ) {
        if (entry.timestamp.isNotEmpty()) {
            Text(
                text = "[${entry.timestamp}]",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFFA0A0B4),
                modifier = Modifier.width(80.dp).padding(end = 4.dp)
            )
        }

        if (entry.thread.isNotEmpty()) {
            Text(
                text = "[${entry.thread}]",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF8888AA),
                modifier = Modifier.widthIn(min = 80.dp).padding(end = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = entry.level,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = levelColor,
            modifier = Modifier.width(48.dp).padding(end = 6.dp)
        )

        Text(
            text = entry.message.ifEmpty { entry.raw },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = when {
                entry.isError -> Color(0xFFDC3545)
                entry.isWarning -> Color(0xFFB8860B)
                else -> Color(0xFF1A1A2E)
            },
            lineHeight = 15.sp
        )
    }
}
