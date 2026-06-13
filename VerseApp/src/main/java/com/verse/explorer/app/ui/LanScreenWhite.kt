package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LanScreenWhite(navController: NavHostController) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val logListState = rememberLazyListState()

    var lanRoom by remember { mutableStateOf<LanRoomInfo?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var lanLogs by remember { mutableStateOf<List<LanLogEntry>>(emptyList()) }
    var roomName by remember { mutableStateOf("") }
    var playerName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var roomPort by remember { mutableStateOf("25565") }
    var isProcessing by remember { mutableStateOf(false) }

    val localIPs = remember { getLocalIPs() }

    fun addLog(msg: String, type: String = "info") {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        lanLogs = (lanLogs + LanLogEntry(time, msg, type)).takeLast(100)
    }

    LaunchedEffect(lanLogs.size) { if (lanLogs.isNotEmpty()) logListState.animateScrollToItem(lanLogs.size - 1) }

    // White theme colors
    val bg = VerseColors.WhiteBg
    val cardBg = VerseColors.WhiteBgCard
    val textPri = VerseColors.WhiteTextPrimary
    val textSec = VerseColors.WhiteTextSecondary
    val textMut = VerseColors.WhiteTextMuted
    val accent = VerseColors.WhiteAccent
    val accentBg = VerseColors.WhiteAccentSoft
    val border = VerseColors.WhiteBorder

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
            PageHeader(title = "联机大厅", subtitle = if (isConnected) "已连接到房间" else "创建或加入房间开始联机")

            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    LanModeCardW("陶瓦联机 - 创建房间", "使用Terracotta创建虚拟局域网房间", Icons.Filled.WifiTethering, listOf(accent, VerseColors.WhiteInfo), badge = "推荐") {
                        scope.launch {
                            isProcessing = true; addLog("创建陶瓦联机房间...")
                            delay(1000); val code = generateRoomCode()
                            lanRoom = LanRoomInfo(code = code, role = "host", mode = "terracotta", gamePort = 25565, localIPs = localIPs, status = "active")
                            isConnected = true; addLog("房间创建成功"); addLog("房间号: $code")
                            isProcessing = false
                        }
                    }
                }
                item {
                    LanModeCardW("陶瓦联机 - 加入房间", "输入好友分享的房间号加入", Icons.Filled.MeetingRoom, listOf(accent, accent)) {
                        scope.launch {
                            if (joinCode.isNotBlank()) {
                                isProcessing = true; addLog("加入房间: $joinCode")
                                delay(1000); lanRoom = LanRoomInfo(code = joinCode, role = "guest", mode = "terracotta", status = "active", peerCount = 1)
                                isConnected = true; addLog("成功加入房间")
                                isProcessing = false
                            }
                        }
                    }
                    if (isConnected == false || lanRoom?.role != "guest") {
                        OutlinedTextField(value = joinCode, onValueChange = { joinCode = it }, label = { Text("房间号") },
                            placeholder = { Text("输入好友的房间号") }, singleLine = true, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent.copy(alpha = 0.4f), unfocusedBorderColor = border, focusedContainerColor = cardBg, unfocusedContainerColor = cardBg, focusedTextColor = textPri, unfocusedTextColor = textPri, cursorColor = accent))
                    }
                }

                if (isConnected && lanRoom != null) {
                    val room = lanRoom!!
                    item {
                        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = cardBg, border = androidx.compose.foundation.BorderStroke(1.dp, border)) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(accent, VerseColors.WhiteInfo))), contentAlignment = Alignment.Center) {
                                    Icon(if (room.role == "host") Icons.Filled.WifiTethering else Icons.Filled.MeetingRoom, null, Modifier.size(28.dp), Color.White)
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(if (room.role == "host") "房间运行中" else "已加入房间", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPri)
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("房间号", fontSize = 10.sp, color = textMut)
                                        Text(room.code.ifEmpty { "--" }, fontSize = 16.sp, color = textPri, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("端口", fontSize = 10.sp, color = textMut)
                                        Text("${room.gamePort}", fontSize = 13.sp, color = textPri, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("玩家", fontSize = 10.sp, color = textMut)
                                        Text("${room.peerCount + 1}", fontSize = 13.sp, color = textPri, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        clipboardManager.setText(AnnotatedString(room.code))
                                        addLog("已复制房间号: ${room.code}")
                                    }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accent)) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("复制", fontSize = 12.sp) }
                                    OutlinedButton(onClick = {
                                        scope.launch { addLog("离开房间..."); delay(500); addLog("已离开房间"); isConnected = false; lanRoom = null }
                                    }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VerseColors.WhiteDanger)) { Icon(Icons.Outlined.ExitToApp, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("离开", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                    if (room.localIPs.isNotEmpty()) {
                        item {
                            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = cardBg, border = androidx.compose.foundation.BorderStroke(1.dp, border)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("连接信息", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = textPri)
                                    Spacer(Modifier.height(6.dp))
                                    room.localIPs.forEach { ip ->
                                        Row { Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(6.dp).align(Alignment.CenterVertically), accent); Spacer(Modifier.width(6.dp)); Text("局域网: $ip:${room.gamePort}", fontSize = 11.sp, color = textSec, fontFamily = FontFamily.Monospace) }
                                        Spacer(Modifier.height(2.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Logs
                if (lanLogs.isNotEmpty()) {
                    item {
                        Surface(Modifier.fillMaxWidth().heightIn(max = 200.dp), RoundedCornerShape(12.dp), color = cardBg, border = androidx.compose.foundation.BorderStroke(1.dp, border)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("日志", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = textPri)
                                    Text("${lanLogs.size}", fontSize = 10.sp, color = textMut)
                                }
                                Spacer(Modifier.height(6.dp))
                                LazyColumn(state = logListState) {
                                    items(lanLogs) { log ->
                                        Row(Modifier.padding(vertical = 1.dp)) {
                                            Text("[${log.time}]", fontSize = 9.sp, color = VerseColors.WhiteTextDim, fontFamily = FontFamily.Monospace, modifier = Modifier.width(60.dp))
                                            Text(log.message, fontSize = 10.sp, color = when (log.type) { "error" -> VerseColors.WhiteDanger; else -> textSec }, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LanModeCardW(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, gradient: List<Color>, badge: String? = null, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(14.dp), color = VerseColors.WhiteBgCard, border = androidx.compose.foundation.BorderStroke(1.dp, VerseColors.WhiteBorder)) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(gradient)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), Color.White) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.WhiteTextPrimary)
                        if (badge != null) { Spacer(Modifier.width(8.dp)); Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.WhiteAccentSoft) { Text(badge, fontSize = 10.sp, color = VerseColors.WhiteAccent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) } }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(desc, fontSize = 12.sp, color = VerseColors.WhiteTextSecondary, lineHeight = 16.sp)
                }
                Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp).align(Alignment.CenterVertically), VerseColors.WhiteTextMuted)
            }
        }
    }
}
