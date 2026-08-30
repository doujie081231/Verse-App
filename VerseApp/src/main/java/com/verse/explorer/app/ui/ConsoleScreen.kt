package com.verse.explorer.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.console.LogRepository
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConsoleScreen(navController: NavHostController) {
    val context = LocalContext.current
    val logState by VerseLogger.logState.collectAsState()
    val logEntries = logState.entries
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 16.dp)
    ) {
        PageHeader(
            title = "游戏日志",
            actions = {
                PcButton(
                    text = "导出日志",
                    onClick = {
                        scope.launch {
                            try {
                                val exportFile = withContext(Dispatchers.IO) {
                                    LogRepository.exportLog(context, logEntries)
                                }
                                Toast.makeText(
                                    context,
                                    "已导出 ${logEntries.size} 行到\n${exportFile.absolutePath}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    variant = PcButtonVariant.SECONDARY,
                    icon = Icons.Outlined.FileDownload
                )
                PcButton(
                    text = "清空",
                    onClick = { VerseLogger.clear() },
                    variant = PcButtonVariant.OUTLINE,
                    icon = Icons.Outlined.DeleteOutline
                )
            }
        )

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = VerseColors.PcBgPrimary,
            border = BorderStroke(1.dp, VerseColors.PcBorder)
        ) {
            if (logEntries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Terminal,
                            null,
                            Modifier.size(48.dp),
                            tint = VerseColors.PcTextMuted.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "等待游戏启动...",
                            fontSize = 14.sp,
                            color = VerseColors.PcTextMuted,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "日志将在游戏运行时实时显示",
                            fontSize = 12.sp,
                            color = VerseColors.PcTextMuted.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    ) {
                        items(logEntries) { entry ->
                            ConsoleLogRow(entry)
                        }
                    }
                }
            }
        }
    }
}
