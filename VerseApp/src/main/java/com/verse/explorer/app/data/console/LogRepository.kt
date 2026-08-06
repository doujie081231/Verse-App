package com.verse.explorer.app.data.console

import android.content.Context
import com.verse.explorer.app.ui.VerseLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {

    fun exportLog(context: Context, entries: List<VerseLogger.LogEntry>): File {
        val exportDir = File(context.getExternalFilesDir(null), "VerseExplorerX/logs")
        exportDir.mkdirs()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val exportFile = File(exportDir, "terminal_export_${ts}.log")
        val lines = entries.map { "[${it.timestamp}] [${it.tag}/${it.level}] ${it.message}" }
        exportFile.writeText(lines.joinToString("\n"))
        return exportFile
    }
}
