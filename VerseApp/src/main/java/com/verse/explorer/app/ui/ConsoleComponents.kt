package com.verse.explorer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
fun ConsoleLogRow(entry: VerseLogger.LogEntry) {
    val levelColor = when (entry.level) {
        "ERROR", "FATAL" -> VerseColors.PcRed
        "WARN" -> VerseColors.PcOrange
        else -> VerseColors.PcTextPrimary
    }

    val line = "[${entry.timestamp}] [${entry.tag}/${entry.level}] ${entry.message}"
    Text(
        text = line,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = levelColor,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        lineHeight = 19.2.sp
    )
}
