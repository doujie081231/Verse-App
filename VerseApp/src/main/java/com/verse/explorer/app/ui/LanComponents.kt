package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.data.lan.LanNetworkScanner
import com.verse.explorer.app.domain.model.lan.LanLogEntry
import com.verse.explorer.app.domain.model.lan.LanRoomInfo
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
internal fun LanMenuView(
    onTerracotta: () -> Unit,
    onPortmap: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            LanModeCard(
                title = "陶瓦联机",
                description = "基于 Terracotta 的 P2P 虚拟组网，无需公网 IP 即可联机",
                icon = Icons.Filled.WifiTethering,
                gradient = listOf(VerseColors.PcGreen, VerseColors.PcPurple),
                badge = "推荐",
                onClick = onTerracotta
            )
        }
        item {
            LanModeCard(
                title = "端口映射联机",
                description = "通过 UPnP 或手动端口转发实现远程联机",
                icon = Icons.Filled.Router,
                gradient = listOf(VerseColors.PcOrange, VerseColors.PcPurple),
                onClick = onPortmap
            )
        }
        item {
            LocalIPsCard(LanNetworkScanner.getLocalIPs())
        }
    }
}

@Composable
internal fun LanModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    gradient: List<Color>,
    badge: String? = null,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(24.dp), Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.PcTextPrimary)
                        if (badge != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = VerseColors.PcGreen.copy(alpha = 0.12f)
                            ) {
                                Text(badge, fontSize = 10.sp, color = VerseColors.PcGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(description, fontSize = 12.sp, color = VerseColors.PcTextSecondary, lineHeight = 16.sp)
                }
                Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp).align(Alignment.CenterVertically), VerseColors.PcTextMuted)
            }
        }
    }
}

@Composable
internal fun LocalIPsCard(ips: List<String>) {
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lan, null, Modifier.size(16.dp), VerseColors.PcTextSecondary)
                Spacer(Modifier.width(6.dp))
                Text("本机局域网IP", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.PcTextPrimary)
            }
            Spacer(Modifier.height(6.dp))
            if (ips.isEmpty()) {
                Text("未检测到局域网IP", fontSize = 11.sp, color = VerseColors.PcTextMuted)
            } else {
                ips.forEach { ip ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(6.dp), VerseColors.PcGreen)
                        Spacer(Modifier.width(6.dp))
                        Text(ip, fontSize = 12.sp, color = VerseColors.PcTextSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
internal fun LanStatusCard(
    title: String,
    description: String,
    state: String
) {
    val borderColor = when (state) {
        "connected" -> VerseColors.PcGreen
        "connecting" -> VerseColors.PcOrange
        else -> VerseColors.PcBorder
    }
    val dotColor = when (state) {
        "connected" -> VerseColors.PcGreen
        "connecting" -> VerseColors.PcOrange
        else -> VerseColors.PcTextMuted
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        color = VerseColors.PcBgCard
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextPrimary)
                Text(description, fontSize = 11.sp, color = VerseColors.PcTextSecondary)
            }
        }
    }
}

@Composable
internal fun CreateTerracottaHost(
    roomName: String,
    onRoomNameChange: (String) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    isProcessing: Boolean,
    onCreate: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("创建陶瓦联机房间", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.PcTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("在游戏内开放局域网后，创建房间让好友加入", fontSize = 11.sp, color = VerseColors.PcTextSecondary)
                Spacer(Modifier.height(12.dp))
                LanTextField("房间名", roomName, onRoomNameChange, "我的世界")
                Spacer(Modifier.height(8.dp))
                LanTextField("玩家名", playerName, onPlayerNameChange, "主机")
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VerseColors.PcOrange.copy(alpha = 0.08f)
        ) {
            Row(Modifier.padding(12.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), VerseColors.PcOrange)
                Spacer(Modifier.width(8.dp))
                Text("请先在游戏内开放局域网联机（按Esc → 对局域网开放），再创建房间", fontSize = 11.sp, color = VerseColors.PcOrange)
            }
        }

        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcGreen)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(if (isProcessing) "创建中..." else "创建房间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun JoinTerracottaGuest(
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    isProcessing: Boolean,
    onJoin: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("加入陶瓦联机房间", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.PcTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("输入好友分享的房间号即可加入", fontSize = 11.sp, color = VerseColors.PcTextSecondary)
                Spacer(Modifier.height(12.dp))
                LanTextField("房间号", joinCode, onJoinCodeChange, "输入好友的房间号")
                Spacer(Modifier.height(8.dp))
                LanTextField("玩家名", playerName, onPlayerNameChange, "玩家")
            }
        }

        Button(
            onClick = onJoin,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = joinCode.isNotBlank() && !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcGreen)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(if (isProcessing) "加入中..." else "加入房间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun CreateRemoteHost(
    roomName: String,
    onRoomNameChange: (String) -> Unit,
    roomPort: String,
    onRoomPortChange: (String) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    enableUPnP: Boolean,
    onUPnPChange: (Boolean) -> Unit,
    isProcessing: Boolean,
    onCreate: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("创建远程联机房间", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.PcTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("配置远程联机服务，允许外网玩家直接连接", fontSize = 11.sp, color = VerseColors.PcTextSecondary)
                Spacer(Modifier.height(12.dp))
                LanTextField("房间名", roomName, onRoomNameChange, "远程房间")
                Spacer(Modifier.height(8.dp))
                LanTextField("游戏端口", roomPort, onRoomPortChange, "25565", isNumeric = true)
                Spacer(Modifier.height(8.dp))
                LanTextField("玩家名", playerName, onPlayerNameChange, "主机")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("UPnP 端口映射", fontSize = 12.sp, color = VerseColors.PcTextPrimary, fontWeight = FontWeight.Medium)
                        Text("自动映射路由器端口", fontSize = 10.sp, color = VerseColors.PcTextMuted)
                    }
                    Switch(checked = enableUPnP, onCheckedChange = onUPnPChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = VerseColors.PcGreen, checkedTrackColor = VerseColors.PcGreen.copy(alpha = 0.3f)))
                }
            }
        }

        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcOrange)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(if (isProcessing) "配置中..." else "创建房间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun JoinRemoteGuest(
    joinAddress: String,
    onJoinAddressChange: (String) -> Unit,
    joinPort: String,
    onJoinPortChange: (String) -> Unit,
    isProcessing: Boolean,
    onJoin: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("加入远程联机", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.PcTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("输入主机IP地址和端口直接连接", fontSize = 11.sp, color = VerseColors.PcTextSecondary)
                Spacer(Modifier.height(12.dp))
                LanTextField("服务器地址", joinAddress, onJoinAddressChange, "127.0.0.1")
                Spacer(Modifier.height(8.dp))
                LanTextField("端口", joinPort, onJoinPortChange, "25565", isNumeric = true)
            }
        }

        Button(
            onClick = onJoin,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = joinAddress.isNotBlank() && !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcPurple)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(if (isProcessing) "连接中..." else "连接", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun RoomPanelView(
    room: LanRoomInfo?,
    logs: List<LanLogEntry>,
    logListState: LazyListState,
    clipboardManager: ClipboardManager,
    onLeave: () -> Unit,
    onCopyCode: () -> Unit,
    onLaunchGame: () -> Unit = {},
    onCopyAddress: () -> Unit = {}
) {
    if (room == null) return

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                when (room.mode) {
                                    "terracotta" -> listOf(VerseColors.PcGreen, VerseColors.PcPurple)
                                    "remote" -> listOf(VerseColors.PcOrange, VerseColors.PcPurple)
                                    else -> listOf(VerseColors.PcPurple, VerseColors.PcGreen)
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (room.role) { "host" -> Icons.Filled.WifiTethering; else -> Icons.Filled.MeetingRoom },
                        null, Modifier.size(28.dp), Color.White
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (room.role == "host") "房间运行中" else "已加入房间",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VerseColors.PcTextPrimary
                )

                Spacer(Modifier.height(10.dp))

                Text("房间号", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        room.code.ifEmpty { "--" },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = VerseColors.PcTextPrimary,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onCopyCode, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp), VerseColors.PcTextSecondary)
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("端口", fontSize = 10.sp, color = VerseColors.PcTextMuted)
                        Spacer(Modifier.height(2.dp))
                        Text("${room.gamePort}", fontSize = 13.sp, color = VerseColors.PcTextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("玩家", fontSize = 10.sp, color = VerseColors.PcTextMuted)
                        Spacer(Modifier.height(2.dp))
                        Text("${room.peerCount + 1}", fontSize = 13.sp, color = VerseColors.PcTextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (room.role == "guest") {
                    Spacer(Modifier.height(10.dp))
                    Text("连接地址", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            room.code.ifEmpty { "--" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = VerseColors.PcTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onCopyAddress, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp), VerseColors.PcTextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onLaunchGame,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcGreen)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("启动游戏", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onLeave,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VerseColors.PcRed)
                    ) {
                        Icon(Icons.Outlined.ExitToApp, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("离开", fontSize = 12.sp)
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VerseColors.PcGreen.copy(alpha = 0.08f)
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, Modifier.size(14.dp), VerseColors.PcGreen)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (room.role == "host") "将房间号分享给好友即可加入" else "已连接到主机，可启动游戏",
                    fontSize = 11.sp,
                    color = VerseColors.PcGreen
                )
            }
        }

        if (room.localIPs.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("连接信息", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.PcTextPrimary)
                    Spacer(Modifier.height(6.dp))
                    room.localIPs.forEach { ip ->
                        Row {
                            Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(6.dp).align(Alignment.CenterVertically), VerseColors.PcGreen)
                            Spacer(Modifier.width(6.dp))
                            Text("局域网: $ip:${room.gamePort}", fontSize = 11.sp, color = VerseColors.PcTextSecondary, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("日志", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.PcTextPrimary)
                    Text("${logs.size}", fontSize = 10.sp, color = VerseColors.PcTextMuted)
                }
                Spacer(Modifier.height(6.dp))
                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无日志", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                    }
                } else {
                    LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                        items(logs) { log ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text("[${log.time}]", fontSize = 9.sp, color = VerseColors.PcTextMuted, fontFamily = FontFamily.Monospace, modifier = Modifier.width(60.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(log.message, fontSize = 10.sp, color = when (log.type) {
                                    "error" -> VerseColors.PcRed
                                    "warn" -> VerseColors.PcOrange
                                    else -> VerseColors.PcTextSecondary
                                }, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LanTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isNumeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newVal ->
            if (isNumeric) {
                if (newVal.isEmpty() || newVal.all { it.isDigit() }) onValueChange(newVal)
            } else {
                onValueChange(newVal)
            }
        },
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp, color = VerseColors.PcTextMuted) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VerseColors.PcGreen.copy(alpha = 0.3f),
            unfocusedBorderColor = VerseColors.PcBorder,
            focusedContainerColor = VerseColors.PcBgCard,
            unfocusedContainerColor = VerseColors.PcBgCard,
            focusedLabelColor = VerseColors.PcGreen,
            unfocusedLabelColor = VerseColors.PcTextSecondary,
            focusedTextColor = VerseColors.PcTextPrimary,
            unfocusedTextColor = VerseColors.PcTextPrimary,
            cursorColor = VerseColors.PcGreen
        )
    )
}
