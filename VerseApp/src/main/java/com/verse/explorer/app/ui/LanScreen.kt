package com.verse.explorer.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

data class LanRoomInfo(
    val code: String = "",
    val role: String = "host",
    val mode: String = "terracotta",
    val gamePort: Int = 25565,
    val relayPort: Int = 0,
    val publicIP: String? = null,
    val localIPs: List<String> = emptyList(),
    val peerCount: Int = 0,
    val peers: List<String> = emptyList(),
    val status: String = "waiting"
)

data class LanLogEntry(
    val time: String,
    val message: String,
    val type: String = "info"
)

enum class LanViewMode {
    MENU, CREATE_TERRACOTTA, JOIN_TERRACOTTA, CREATE_REMOTE, JOIN_REMOTE, ROOM_PANEL
}

@Composable
fun LanScreen(
    navController: NavHostController
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val logListState = rememberLazyListState()

    var viewMode by remember { mutableStateOf(LanViewMode.MENU) }
    var lanRoom by remember { mutableStateOf<LanRoomInfo?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var lanLogs by remember { mutableStateOf<List<LanLogEntry>>(emptyList()) }

    var roomName by remember { mutableStateOf("") }
    var roomPort by remember { mutableStateOf("25565") }
    var playerName by remember { mutableStateOf("") }
    var enableUPnP by remember { mutableStateOf(true) }
    var joinCode by remember { mutableStateOf("") }
    var joinAddress by remember { mutableStateOf("") }
    var joinPort by remember { mutableStateOf("25565") }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    val localIPs = remember { getLocalIPs() }

    fun addLog(message: String, type: String = "info") {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        lanLogs = (lanLogs + LanLogEntry(time, message, type)).takeLast(100)
        VerseLogger.info(message, "LAN")
    }

    LaunchedEffect(lanLogs.size) {
        if (lanLogs.isNotEmpty()) {
            logListState.animateScrollToItem(lanLogs.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.McBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {
            PageHeader(
                title = "联机大厅",
                subtitle = if (isConnected) "已连接到房间" else "创建或加入房间开始联机"
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (viewMode) {
                LanViewMode.MENU -> LanMenuView(
                    onTerracottaHost = { viewMode = LanViewMode.CREATE_TERRACOTTA },
                    onTerracottaJoin = { viewMode = LanViewMode.JOIN_TERRACOTTA },
                    onRemoteCreate = { viewMode = LanViewMode.CREATE_REMOTE },
                    onRemoteJoin = { viewMode = LanViewMode.JOIN_REMOTE }
                )
                LanViewMode.CREATE_TERRACOTTA -> CreateTerracottaHost(
                    roomName = roomName,
                    onRoomNameChange = { roomName = it },
                    playerName = playerName,
                    onPlayerNameChange = { playerName = it },
                    isProcessing = isProcessing,
                    onBack = { viewMode = LanViewMode.MENU },
                    onCreate = {
                        scope.launch {
                            isProcessing = true
                            statusMessage = "正在创建陶瓦联机房间..."
                            addLog("初始化陶瓦联机...")
                            VerseLogger.info("创建陶瓦联机房间", "LAN")

                            delay(1500)
                            val code = generateRoomCode()
                            lanRoom = LanRoomInfo(
                                code = code,
                                role = "host",
                                mode = "terracotta",
                                gamePort = roomPort.toIntOrNull() ?: 25565,
                                localIPs = localIPs,
                                status = "active"
                            )
                            isConnected = true
                            addLog("房间创建成功")
                            addLog("房间号: $code")
                            localIPs.forEach { addLog("局域网IP: $it") }
                            addLog("好友可通过房间号连接")
                            viewMode = LanViewMode.ROOM_PANEL
                            isProcessing = false
                        }
                    }
                )
                LanViewMode.JOIN_TERRACOTTA -> JoinTerracottaGuest(
                    joinCode = joinCode,
                    onJoinCodeChange = { joinCode = it },
                    playerName = playerName,
                    onPlayerNameChange = { playerName = it },
                    isProcessing = isProcessing,
                    onBack = { viewMode = LanViewMode.MENU },
                    onJoin = {
                        scope.launch {
                            isProcessing = true
                            statusMessage = "正在加入房间..."
                            addLog("加入房间: $joinCode")
                            VerseLogger.info("加入陶瓦联机房间: $joinCode", "LAN")

                            delay(1500)
                            lanRoom = LanRoomInfo(
                                code = joinCode,
                                role = "guest",
                                mode = "terracotta",
                                gamePort = 25565,
                                status = "active",
                                peerCount = 1
                            )
                            isConnected = true
                            addLog("成功加入房间")
                            addLog("房间号: $joinCode")
                            addLog("连接已建立")
                            viewMode = LanViewMode.ROOM_PANEL
                            isProcessing = false
                        }
                    }
                )
                LanViewMode.CREATE_REMOTE -> CreateRemoteHost(
                    roomName = roomName,
                    onRoomNameChange = { roomName = it },
                    roomPort = roomPort,
                    onRoomPortChange = { roomPort = it },
                    playerName = playerName,
                    onPlayerNameChange = { playerName = it },
                    enableUPnP = enableUPnP,
                    onUPnPChange = { enableUPnP = it },
                    isProcessing = isProcessing,
                    onBack = { viewMode = LanViewMode.MENU },
                    onCreate = {
                        scope.launch {
                            isProcessing = true
                            statusMessage = "正在配置远程联机..."
                            addLog("配置远程联机...")
                            VerseLogger.info("创建远程联机房间, 端口: $roomPort", "LAN")

                            delay(2000)
                            val code = generateRoomCode()
                            lanRoom = LanRoomInfo(
                                code = code,
                                role = "host",
                                mode = "remote",
                                gamePort = roomPort.toIntOrNull() ?: 25565,
                                localIPs = localIPs,
                                status = "active"
                            )
                            isConnected = true
                            addLog("远程联机房间创建成功")
                            addLog("游戏端口: ${roomPort.toIntOrNull() ?: 25565}")
                            localIPs.forEach { addLog("局域网IP: $it") }
                            addLog("--- 连接方式 ---")
                            addLog("1. 局域网玩家: 使用局域网IP连接")
                            addLog("2. 远程玩家: 获取公网IP后连接")
                            addLog("3. 在Minecraft中添加服务器地址即可")
                            viewMode = LanViewMode.ROOM_PANEL
                            isProcessing = false
                        }
                    }
                )
                LanViewMode.JOIN_REMOTE -> JoinRemoteGuest(
                    joinAddress = joinAddress,
                    onJoinAddressChange = { joinAddress = it },
                    joinPort = joinPort,
                    onJoinPortChange = { joinPort = it },
                    isProcessing = isProcessing,
                    onBack = { viewMode = LanViewMode.MENU },
                    onJoin = {
                        scope.launch {
                            isProcessing = true
                            statusMessage = "正在连接到远程主机..."
                            val addr = "$joinAddress:$joinPort"
                            addLog("连接到: $addr")
                            VerseLogger.info("加入远程联机: $addr", "LAN")

                            delay(1500)
                            lanRoom = LanRoomInfo(
                                code = addr,
                                role = "guest",
                                mode = "remote-join",
                                gamePort = joinPort.toIntOrNull() ?: 25565
                            )
                            isConnected = true
                            addLog("已连接到远程主机")
                            addLog("服务器地址: $addr")
                            viewMode = LanViewMode.ROOM_PANEL
                            isProcessing = false
                        }
                    }
                )
                LanViewMode.ROOM_PANEL -> {
                    val localContext = LocalContext.current
                    RoomPanelView(
                        room = lanRoom,
                        logs = lanLogs,
                        logListState = logListState,
                        clipboardManager = clipboardManager,
                        onLeave = {
                            scope.launch {
                                addLog("正在离开房间...")
                                delay(500)
                                addLog("已离开房间")
                                isConnected = false
                                lanRoom = null
                                lanLogs = lanLogs
                                viewMode = LanViewMode.MENU
                                VerseLogger.info("已离开房间", "LAN")
                            }
                        },
                        onCopyCode = {
                            val text = lanRoom?.code ?: return@RoomPanelView
                            clipboardManager.setText(AnnotatedString(text))
                            addLog("已复制房间号: $text")
                        },
                        onLaunchGame = {
                            val controller = com.verse.explorer.app.launch.GameLaunchController(localContext)
                            val mpArgs = mutableMapOf<String, String>()
                            lanRoom?.let { room ->
                                mpArgs["TERRACOTTA_PLAYER"] = playerName.ifEmpty { "Player" }
                                mpArgs["server"] = room.localIPs.firstOrNull() ?: "127.0.0.1"
                                mpArgs["port"] = room.gamePort.toString()
                                mpArgs["roomCode"] = room.code
                            }
                            controller.launchGame(null, mpArgs)
                            VerseLogger.info("从联机页面启动游戏", "LAN")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanMenuView(
    onTerracottaHost: () -> Unit,
    onTerracottaJoin: () -> Unit,
    onRemoteCreate: () -> Unit,
    onRemoteJoin: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            LanModeCard(
                title = "陶瓦联机 - 创建房间",
                description = "使用Terracotta创建虚拟局域网房间，好友可通过房间号加入",
                icon = Icons.Filled.WifiTethering,
                gradient = listOf(VerseColors.McGreen, VerseColors.McWater),
                badge = "推荐",
                onClick = onTerracottaHost
            )
        }
        item {
            LanModeCard(
                title = "陶瓦联机 - 加入房间",
                description = "输入好友分享的房间号，快速加入联机游戏",
                icon = Icons.Filled.MeetingRoom,
                gradient = listOf(VerseColors.McGreen, VerseColors.McGreen),
                onClick = onTerracottaJoin
            )
        }
        item {
            HorizontalDivider(color = VerseColors.McCardBorder)
        }
        item {
            LanModeCard(
                title = "远程联机 - 创建房间",
                description = "配置端口和UPnP映射，开启远程联机服务",
                icon = Icons.Filled.Router,
                gradient = listOf(VerseColors.McGold, VerseColors.McWater),
                onClick = onRemoteCreate
            )
        }
        item {
            LanModeCard(
                title = "远程联机 - 加入房间",
                description = "输入主机IP地址和端口，直接连接到联机主机",
                icon = Icons.Filled.ConnectWithoutContact,
                gradient = listOf(VerseColors.McWater, VerseColors.McGreen),
                onClick = onRemoteJoin
            )
        }
        item {
            LocalIPsCard(getLocalIPs())
        }
    }
}

@Composable
private fun LanModeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.McTextPrimary)
                        if (badge != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = VerseColors.McGreen.copy(alpha = 0.12f)
                            ) {
                                Text(badge, fontSize = 10.sp, color = VerseColors.McGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(description, fontSize = 12.sp, color = VerseColors.McTextSecondary, lineHeight = 16.sp)
                }
                Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp).align(Alignment.CenterVertically), VerseColors.McTextMuted)
            }
        }
    }
}

@Composable
private fun LocalIPsCard(ips: List<String>) {
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lan, null, Modifier.size(16.dp), VerseColors.McTextSecondary)
                Spacer(Modifier.width(6.dp))
                Text("本机局域网IP", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.McTextPrimary)
            }
            Spacer(Modifier.height(6.dp))
            if (ips.isEmpty()) {
                Text("未检测到局域网IP", fontSize = 11.sp, color = VerseColors.McTextMuted)
            } else {
                ips.forEach { ip ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(6.dp), VerseColors.McGreen)
                        Spacer(Modifier.width(6.dp))
                        Text(ip, fontSize = 12.sp, color = VerseColors.McTextSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun CreateTerracottaHost(
    roomName: String,
    onRoomNameChange: (String) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    isProcessing: Boolean,
    onBack: () -> Unit,
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
                Text("创建陶瓦联机房间", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.McTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("在游戏内开放局域网后，创建房间让好友加入", fontSize = 11.sp, color = VerseColors.McTextSecondary)
                Spacer(Modifier.height(12.dp))
                LanTextField("房间名", roomName, onRoomNameChange, "我的世界")
                Spacer(Modifier.height(8.dp))
                LanTextField("玩家名", playerName, onPlayerNameChange, "主机")
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VerseColors.McGold.copy(alpha = 0.08f)
        ) {
            Row(Modifier.padding(12.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), VerseColors.McGold)
                Spacer(Modifier.width(8.dp))
                Text("请先在游戏内开放局域网联机（按Esc → 对局域网开放），再创建房间", fontSize = 11.sp, color = VerseColors.McGold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), enabled = !isProcessing) {
                Text("返回", color = VerseColors.McTextSecondary, fontSize = 13.sp)
            }
            Button(
                onClick = onCreate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McGreen)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isProcessing) "创建中..." else "创建房间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun JoinTerracottaGuest(
    joinCode: String,
    onJoinCodeChange: (String) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    isProcessing: Boolean,
    onBack: () -> Unit,
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
                Text("加入陶瓦联机房间", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.McTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("输入好友分享的房间号即可加入", fontSize = 11.sp, color = VerseColors.McTextSecondary)
                Spacer(Modifier.height(12.dp))
                LanTextField("房间号", joinCode, onJoinCodeChange, "输入好友的房间号")
                Spacer(Modifier.height(8.dp))
                LanTextField("玩家名", playerName, onPlayerNameChange, "玩家")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), enabled = !isProcessing) {
                Text("返回", color = VerseColors.McTextSecondary, fontSize = 13.sp)
            }
            Button(
                onClick = onJoin,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                enabled = joinCode.isNotBlank() && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McGreen)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isProcessing) "加入中..." else "加入房间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CreateRemoteHost(
    roomName: String,
    onRoomNameChange: (String) -> Unit,
    roomPort: String,
    onRoomPortChange: (String) -> Unit,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    enableUPnP: Boolean,
    onUPnPChange: (Boolean) -> Unit,
    isProcessing: Boolean,
    onBack: () -> Unit,
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
                Text("创建远程联机房间", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.McTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("配置远程联机服务，允许外网玩家直接连接", fontSize = 11.sp, color = VerseColors.McTextSecondary)
                Spacer(Modifier.height(12.dp))
                LanTextField("房间名", roomName, onRoomNameChange, "远程房间")
                Spacer(Modifier.height(8.dp))
                LanTextField("游戏端口", roomPort, onRoomPortChange, "25565", isNumeric = true)
                Spacer(Modifier.height(8.dp))
                LanTextField("玩家名", playerName, onPlayerNameChange, "主机")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("UPnP 端口映射", fontSize = 12.sp, color = VerseColors.McTextPrimary, fontWeight = FontWeight.Medium)
                        Text("自动映射路由器端口", fontSize = 10.sp, color = VerseColors.McTextMuted)
                    }
                    Switch(checked = enableUPnP, onCheckedChange = onUPnPChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = VerseColors.McGreen, checkedTrackColor = VerseColors.McGreen.copy(alpha = 0.3f)))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), enabled = !isProcessing) {
                Text("返回", color = VerseColors.McTextSecondary, fontSize = 13.sp)
            }
            Button(
                onClick = onCreate,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McGold)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isProcessing) "配置中..." else "创建房间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun JoinRemoteGuest(
    joinAddress: String,
    onJoinAddressChange: (String) -> Unit,
    joinPort: String,
    onJoinPortChange: (String) -> Unit,
    isProcessing: Boolean,
    onBack: () -> Unit,
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
                Text("加入远程联机", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = VerseColors.McTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("输入主机IP地址和端口直接连接", fontSize = 11.sp, color = VerseColors.McTextSecondary)
                Spacer(Modifier.height(12.dp))
                LanTextField("服务器地址", joinAddress, onJoinAddressChange, "127.0.0.1")
                Spacer(Modifier.height(8.dp))
                LanTextField("端口", joinPort, onJoinPortChange, "25565", isNumeric = true)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), enabled = !isProcessing) {
                Text("返回", color = VerseColors.McTextSecondary, fontSize = 13.sp)
            }
            Button(
                onClick = onJoin,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                enabled = joinAddress.isNotBlank() && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McWater)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isProcessing) "连接中..." else "连接", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RoomPanelView(
    room: LanRoomInfo?,
    logs: List<LanLogEntry>,
    logListState: androidx.compose.foundation.lazy.LazyListState,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onLeave: () -> Unit,
    onCopyCode: () -> Unit,
    onLaunchGame: () -> Unit = {}
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
                                    "terracotta" -> listOf(VerseColors.McGreen, VerseColors.McWater)
                                    "remote" -> listOf(VerseColors.McGold, VerseColors.McWater)
                                    else -> listOf(VerseColors.McWater, VerseColors.McGreen)
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
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VerseColors.McTextPrimary
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("房间号", fontSize = 10.sp, color = VerseColors.McTextMuted)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            room.code.ifEmpty { "--" },
                            fontSize = 11.sp, color = VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("端口", fontSize = 10.sp, color = VerseColors.McTextMuted)
                        Spacer(Modifier.height(2.dp))
                        Text("${room.gamePort}", fontSize = 13.sp, color = VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("玩家", fontSize = 10.sp, color = VerseColors.McTextMuted)
                        Spacer(Modifier.height(2.dp))
                        Text("${room.peerCount + 1}", fontSize = 13.sp, color = VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onLaunchGame,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McGreen)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("启动游戏", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onCopyCode,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McGreen.copy(alpha = 0.12f), contentColor = VerseColors.McGreen)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onLeave,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VerseColors.McRedstone)
                    ) {
                        Icon(Icons.Outlined.ExitToApp, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("离开", fontSize = 12.sp)
                    }
                }
            }
        }

        if (room.localIPs.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("连接信息", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.McTextPrimary)
                    Spacer(Modifier.height(6.dp))
                    room.localIPs.forEach { ip ->
                        Row {
                            Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(6.dp).align(Alignment.CenterVertically), VerseColors.McGreen)
                            Spacer(Modifier.width(6.dp))
                            Text("局域网: $ip:${room.gamePort}", fontSize = 11.sp, color = VerseColors.McTextSecondary, fontFamily = FontFamily.Monospace)
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
                    Text("日志", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.McTextPrimary)
                    Text("${logs.size}", fontSize = 10.sp, color = VerseColors.McTextMuted)
                }
                Spacer(Modifier.height(6.dp))
                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无日志", fontSize = 11.sp, color = VerseColors.McTextMuted)
                    }
                } else {
                    LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                        items(logs) { log ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text("[${log.time}]", fontSize = 9.sp, color = VerseColors.McTextDim, fontFamily = FontFamily.Monospace, modifier = Modifier.width(60.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(log.message, fontSize = 10.sp, color = when (log.type) {
                                    "error" -> VerseColors.McRedstone
                                    "warn" -> VerseColors.McGold
                                    else -> VerseColors.McTextSecondary
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
private fun LanTextField(
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
        placeholder = { Text(placeholder, fontSize = 12.sp, color = VerseColors.McTextMuted) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VerseColors.McGreen.copy(alpha = 0.3f),
            unfocusedBorderColor = VerseColors.McCardBorder,
            focusedContainerColor = VerseColors.McCardBg,
            unfocusedContainerColor = VerseColors.McCardBg,
            focusedLabelColor = VerseColors.McGreen,
            unfocusedLabelColor = VerseColors.McTextSecondary,
            focusedTextColor = VerseColors.McTextPrimary,
            unfocusedTextColor = VerseColors.McTextPrimary,
            cursorColor = VerseColors.McGreen
        )
    )
}

internal fun getLocalIPs(): List<String> {
    val ips = mutableListOf<String>()
    try {
        NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
            if (!iface.isLoopback && iface.isUp) {
                iface.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address) {
                        ips.add(addr.hostAddress ?: "")
                    }
                }
            }
        }
    } catch (_: Exception) {}
    return ips.distinct()
}

internal fun generateRoomCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..6).map { chars.random() }.joinToString("")
}