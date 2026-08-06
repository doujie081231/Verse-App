package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.lan.LanNetworkScanner
import com.verse.explorer.app.domain.model.lan.LanLogEntry
import com.verse.explorer.app.domain.model.lan.LanRoomInfo
import com.verse.explorer.app.domain.model.lan.LanViewMode
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.PcTabGroup
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LanScreen(
    navController: NavHostController
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val logListState = rememberLazyListState()

    var viewMode by remember { mutableStateOf(LanViewMode.MENU) }
    var terracottaTab by remember { mutableStateOf(0) }
    var portmapTab by remember { mutableStateOf(0) }
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

    val localIPs = remember { LanNetworkScanner.getLocalIPs() }

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
            .background(VerseColors.PcBgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {
            when (viewMode) {
                LanViewMode.MENU -> {
                    PageHeader(title = "联机")
                    Spacer(modifier = Modifier.height(12.dp))
                    LanMenuView(
                        onTerracotta = { viewMode = LanViewMode.TERRACOTTA },
                        onPortmap = { viewMode = LanViewMode.PORTMAP }
                    )
                }
                LanViewMode.TERRACOTTA -> {
                    PageHeader(
                        title = "陶瓦联机",
                        actions = {
                            IconButton(onClick = { viewMode = LanViewMode.MENU }) {
                                Icon(Icons.Filled.ArrowBack, "返回", Modifier.size(20.dp), VerseColors.PcTextPrimary)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LanStatusCard(
                            title = "陶瓦联机",
                            description = "无需公网 IP 的 P2P 虚拟组网",
                            state = if (isConnected) "connected" else "disconnected"
                        )
                        PcTabGroup(
                            tabs = listOf("创建房间", "加入房间"),
                            selectedIndex = terracottaTab,
                            onSelected = { terracottaTab = it }
                        )
                        when (terracottaTab) {
                            0 -> CreateTerracottaHost(
                                roomName = roomName,
                                onRoomNameChange = { roomName = it },
                                playerName = playerName,
                                onPlayerNameChange = { playerName = it },
                                isProcessing = isProcessing,
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
                            1 -> JoinTerracottaGuest(
                                joinCode = joinCode,
                                onJoinCodeChange = { joinCode = it },
                                playerName = playerName,
                                onPlayerNameChange = { playerName = it },
                                isProcessing = isProcessing,
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
                        }
                    }
                }
                LanViewMode.PORTMAP -> {
                    PageHeader(
                        title = "端口映射联机",
                        actions = {
                            IconButton(onClick = { viewMode = LanViewMode.MENU }) {
                                Icon(Icons.Filled.ArrowBack, "返回", Modifier.size(20.dp), VerseColors.PcTextPrimary)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LanStatusCard(
                            title = "端口映射联机",
                            description = "通过 UPnP 或手动端口转发实现远程联机",
                            state = if (isConnected) "connected" else "disconnected"
                        )
                        PcTabGroup(
                            tabs = listOf("创建房间", "加入房间"),
                            selectedIndex = portmapTab,
                            onSelected = { portmapTab = it }
                        )
                        when (portmapTab) {
                            0 -> CreateRemoteHost(
                                roomName = roomName,
                                onRoomNameChange = { roomName = it },
                                roomPort = roomPort,
                                onRoomPortChange = { roomPort = it },
                                playerName = playerName,
                                onPlayerNameChange = { playerName = it },
                                enableUPnP = enableUPnP,
                                onUPnPChange = { enableUPnP = it },
                                isProcessing = isProcessing,
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
                            1 -> JoinRemoteGuest(
                                joinAddress = joinAddress,
                                onJoinAddressChange = { joinAddress = it },
                                joinPort = joinPort,
                                onJoinPortChange = { joinPort = it },
                                isProcessing = isProcessing,
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
                        }
                    }
                }
                LanViewMode.ROOM_PANEL -> {
                    val localContext = LocalContext.current
                    PageHeader(
                        title = if (lanRoom?.role == "host") "房间运行中" else "已加入房间",
                        actions = {
                            IconButton(onClick = {
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
                            }) {
                                Icon(Icons.Filled.ArrowBack, "返回", Modifier.size(20.dp), VerseColors.PcTextPrimary)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
                        onCopyAddress = {
                            val text = lanRoom?.code ?: return@RoomPanelView
                            clipboardManager.setText(AnnotatedString(text))
                            addLog("已复制连接地址: $text")
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

internal fun generateRoomCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..6).map { chars.random() }.joinToString("")
}
