package com.verse.explorer.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.lan.LanNetworkScanner
import com.verse.explorer.app.redstone.RedStoneTunnelManager
import com.verse.explorer.app.terracotta.TerracottaController
import com.verse.explorer.app.terracotta.TerracottaMultiplayerDialog
import com.verse.explorer.app.terracotta.TerracottaTexts
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 联机页（对齐设置页布局：左侧 160dp 侧边栏选择联机方式，右侧内容区）。
 * 包含：陶瓦联机、红石联机（RedStone 中转隧道）、本机局域网 IP 信息。
 */
@Composable
fun LanScreen(
    navController: NavHostController
) {
    // 0 = 陶瓦联机，1 = 红石联机，2 = 本机局域网IP
    var selectedPage by remember { mutableStateOf(0) }
    val pages = listOf("陶瓦联机", "红石联机", "本机局域网IP")

    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧边栏（与设置页一致：160dp，PcBgSecondary，纯文本项无图标）
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(VerseColors.PcBgSecondary)
                .padding(top = 32.dp, start = 8.dp, end = 8.dp)
        ) {
            Text(
                "联机",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = VerseColors.PcTextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            pages.forEachIndexed { index, label ->
                val selected = selectedPage == index
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) VerseColors.PcAccent.copy(alpha = 0.1f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clickable { selectedPage = index }
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) VerseColors.PcAccent else VerseColors.PcTextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // 右侧内容区（与设置页一致：PcBgPrimary）
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(VerseColors.PcBgPrimary)
        ) {
            when (selectedPage) {
                0 -> TerracottaContent()
                1 -> RedStoneContent()
                else -> IpInfoContent()
            }
        }
    }
}

/** 陶瓦联机内容（含房主/房客教程子页） */
@Composable
private fun TerracottaContent() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("third_party", Context.MODE_PRIVATE) }

    var subView by remember { mutableStateOf("main") } // main / host / guest
    var multiplayerEnabled by remember { mutableStateOf(prefs.getBoolean("terracotta", false)) }
    var showNoticeDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyContentFill {
        when (subView) {
            "host" -> {
                TutorialTitle(text = TerracottaTexts.TUTORIAL_HOST_BTN, onBack = { subView = "main" })
                TutorialTextView(TerracottaTexts.TUTORIAL_HOST)
            }
            "guest" -> {
                TutorialTitle(text = TerracottaTexts.TUTORIAL_GUEST_BTN, onBack = { subView = "main" })
                TutorialTextView(TerracottaTexts.TUTORIAL_GUEST)
            }
            else -> {
                Text(
                    "陶瓦联机",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary
                )
                Spacer(Modifier.height(16.dp))
                TerracottaMainSettings(
                    multiplayerEnabled = multiplayerEnabled,
                    onSwitchChanged = { isChecked ->
                        val version = prefs.getInt("terracotta_user_notice", 0)
                        if (version >= TerracottaController.getUserNoticeVersion() || !isChecked) {
                            prefs.edit().putBoolean("terracotta", isChecked).apply()
                            multiplayerEnabled = isChecked
                            if (isChecked) requestNotificationPermissionIfNeeded()
                        } else {
                            showNoticeDialog = true
                        }
                    },
                    onShowHostTutorial = { subView = "host" },
                    onShowGuestTutorial = { subView = "guest" }
                )
            }
        }
    }

    if (showNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showNoticeDialog = false },
            title = { Text("用户须知", color = VerseColors.PcTextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    TerracottaTexts.USER_NOTICE_CONFIRM,
                    fontSize = 12.sp,
                    color = VerseColors.PcTextSecondary
                )
            },
            confirmButton = {
                Text(
                    TerracottaTexts.CONFIRM_POSITIVE,
                    color = VerseColors.PcAccent,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable {
                            prefs.edit()
                                .putInt("terracotta_user_notice", TerracottaController.getUserNoticeVersion())
                                .apply()
                            prefs.edit().putBoolean("terracotta", true).apply()
                            multiplayerEnabled = true
                            showNoticeDialog = false
                            requestNotificationPermissionIfNeeded()
                        }
                )
            },
            dismissButton = {
                Text(
                    TerracottaTexts.CONFIRM_NEGATIVE,
                    color = VerseColors.PcTextSecondary,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { showNoticeDialog = false }
                )
            },
            containerColor = VerseColors.PcBgSecondary
        )
    }
}

/** 教程子页：标题 + 返回按钮 */
@Composable
private fun TutorialTitle(text: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = VerseColors.PcTextPrimary
        )
        Spacer(Modifier.weight(1f))
        Text(
            "返回陶瓦联机",
            fontSize = 12.sp,
            color = VerseColors.PcAccent,
            modifier = Modifier
                .padding(8.dp)
                .clickable(onClick = onBack)
        )
    }
    Spacer(Modifier.height(16.dp))
}

/** 本机局域网 IP 信息内容 */
@Composable
private fun IpInfoContent() {
    val ips = remember { LanNetworkScanner.getLocalIPs() }
    LazyContentFill {
        Text(
            "本机局域网IP",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = VerseColors.PcTextPrimary
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = VerseColors.PcBgCard
        ) {
            Column(Modifier.padding(14.dp)) {
                if (ips.isEmpty()) {
                    Text("未检测到局域网IP", fontSize = 12.sp, color = VerseColors.PcTextMuted)
                } else {
                    ips.forEach { ip ->
                        Text(
                            ip,
                            fontSize = 13.sp,
                            color = VerseColors.PcTextSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 右侧内容通用容器（对齐设置页 contentPadding 与滚动） */
@Composable
private fun LazyContentFill(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

/** 陶瓦联机主设置（原 FCL ui_multiplayer 布局；外层 LazyContentFill 已滚动，内部不设滚动） */
@Composable
private fun TerracottaMainSettings(
    multiplayerEnabled: Boolean,
    onSwitchChanged: (Boolean) -> Unit,
    onShowHostTutorial: () -> Unit,
    onShowGuestTutorial: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 启用多人联机
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = VerseColors.PcBgCard
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        TerracottaTexts.ABOUT,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerseColors.PcTextPrimary
                    )
                    Text(
                        TerracottaTexts.ENABLE,
                        fontSize = 11.sp,
                        color = VerseColors.PcTextSecondary
                    )
                }
                Switch(
                    checked = multiplayerEnabled,
                    onCheckedChange = onSwitchChanged,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = VerseColors.PcAccent
                    )
                )
            }
        }

        if (multiplayerEnabled) {
            // 导出联机核心日志
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { TerracottaMultiplayerDialog.shareLogFile(context) },
                shape = RoundedCornerShape(12.dp),
                color = VerseColors.PcBgCard
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        TerracottaTexts.EXPORT_LOG,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerseColors.PcTextPrimary
                    )
                    Text(
                        TerracottaTexts.EXPORT_LOG_DESC,
                        fontSize = 11.sp,
                        color = VerseColors.PcTextSecondary
                    )
                }
            }
        }

        // 房主教程
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowHostTutorial),
            shape = RoundedCornerShape(12.dp),
            color = VerseColors.PcBgCard
        ) {
            Text(
                TerracottaTexts.TUTORIAL_HOST_BTN,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerseColors.PcTextPrimary,
                modifier = Modifier.padding(14.dp)
            )
        }

        // 房客教程
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowGuestTutorial),
            shape = RoundedCornerShape(12.dp),
            color = VerseColors.PcBgCard
        ) {
            Text(
                TerracottaTexts.TUTORIAL_GUEST_BTN,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerseColors.PcTextPrimary,
                modifier = Modifier.padding(14.dp)
            )
        }

        // 关于 EasyTier
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://easytier.cn/"))
                    )
                },
            shape = RoundedCornerShape(12.dp),
            color = VerseColors.PcBgCard
        ) {
            Text(
                TerracottaTexts.ABOUT_EASYTIER,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerseColors.PcTextPrimary,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

/** 教程长文本 */
@Composable
private fun TutorialTextView(content: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VerseColors.PcBgCard
    ) {
        Text(
            content,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = VerseColors.PcTextPrimary,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * 红石联机内容（RedStone 中转隧道，按《RedStone 内核接入文档》对接）：
 * 房主拉起 hongshi 内核创建隧道 → 状态文件 open 后展示联机地址；
 * 访客无需任何内核，在多人游戏中直接连接该地址。
 */
@Composable
private fun RedStoneContent() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val available = remember { RedStoneTunnelManager.isAvailable(context) }
    val state by RedStoneTunnelManager.state.collectAsState()
    val logs by RedStoneTunnelManager.logs.collectAsState()
    val nodes by RedStoneTunnelManager.nodes.collectAsState()

    // 进入页面刷新节点注册表（https://hongshi.site/newserver.json）
    LaunchedEffect(Unit) { RedStoneTunnelManager.refreshNodes() }

    var node by remember { mutableStateOf(RedStoneTunnelManager.DEFAULT_NODE.address) }
    var port by remember { mutableStateOf("25565") }
    val selectedNodeName = nodes.find { it.address == node }?.name ?: node

    LazyContentFill {
        Text(
            "红石联机",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = VerseColors.PcTextPrimary
        )

        if (!available) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = VerseColors.PcOrange.copy(alpha = 0.10f)
            ) {
                Text(
                    "当前设备不支持红石联机（缺少 arm64 内核，仅支持 arm64 设备）",
                    fontSize = 12.sp,
                    color = VerseColors.PcOrange,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            // ===== 状态展示 =====
            when (val s = state) {
                is RedStoneTunnelManager.State.Starting -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = VerseColors.PcBgCard
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), VerseColors.PcAccent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "正在连接节点 ${s.node}...",
                                fontSize = 13.sp,
                                color = VerseColors.PcTextSecondary
                            )
                        }
                    }
                }
                is RedStoneTunnelManager.State.Reconnecting -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = VerseColors.PcBgCard
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), VerseColors.PcOrange, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${s.reason}（${s.attempt}/${s.maxAttempts}）",
                                fontSize = 13.sp,
                                color = VerseColors.PcOrange
                            )
                        }
                    }
                }
                is RedStoneTunnelManager.State.Open -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = VerseColors.PcGreen.copy(alpha = 0.10f)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "隧道已建立，联机地址",
                                fontSize = 11.sp,
                                color = VerseColors.PcTextSecondary
                            )
                            Text(
                                s.address,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = VerseColors.PcGreen,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable {
                                        clipboard.setText(AnnotatedString(s.address))
                                    }
                            )
                            Text(
                                "点击复制。好友在多人游戏中直接连接该地址，无需任何安装；房主先在存档暂停菜单中「对局域网开放」。",
                                fontSize = 11.sp,
                                color = VerseColors.PcTextSecondary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
                is RedStoneTunnelManager.State.Closed -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = VerseColors.PcOrange.copy(alpha = 0.10f)
                    ) {
                        Text(
                            s.message,
                            fontSize = 12.sp,
                            color = VerseColors.PcOrange,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                is RedStoneTunnelManager.State.Failed -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = VerseColors.PcRed.copy(alpha = 0.08f)
                    ) {
                        Text(
                            s.message,
                            fontSize = 12.sp,
                            color = VerseColors.PcRed,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                RedStoneTunnelManager.State.Idle -> {}
            }

            // ===== 创建/关闭控制 =====
            val canConfigure = state is RedStoneTunnelManager.State.Idle ||
                state is RedStoneTunnelManager.State.Closed ||
                state is RedStoneTunnelManager.State.Failed
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = VerseColors.PcBgCard
            ) {
                Column(Modifier.padding(14.dp)) {
                    if (canConfigure) {
                        Text(
                            "创建隧道",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerseColors.PcTextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = node,
                            onValueChange = { node = it },
                            label = {
                                Text(
                                    if (selectedNodeName != node) "中转节点（$selectedNodeName）" else "中转节点",
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            nodes.forEach { n ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (node == n.address) {
                                        VerseColors.PcAccent.copy(alpha = 0.12f)
                                    } else {
                                        VerseColors.PcBgTertiary
                                    },
                                    modifier = Modifier.clickable { node = n.address }
                                ) {
                                    Text(
                                        n.name,
                                        fontSize = 11.sp,
                                        color = if (node == n.address) VerseColors.PcAccent else VerseColors.PcTextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label = { Text("本地 MC 端口", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "先在游戏内存档暂停菜单中「对局域网开放」，再把游戏显示的端口填到这里（默认 25565）。",
                            fontSize = 11.sp,
                            color = VerseColors.PcTextMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        PcButton(
                            text = "创建隧道",
                            onClick = {
                                RedStoneTunnelManager.start(context, node.trim(), port.toIntOrNull() ?: 25565)
                            },
                            variant = PcButtonVariant.PRIMARY
                        )
                    } else {
                        Text(
                            "隧道运行中",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerseColors.PcTextPrimary
                        )
                        Text(
                            "关闭后地址立即失效；好友连接会断开。",
                            fontSize = 11.sp,
                            color = VerseColors.PcTextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        PcButton(
                            text = "关闭隧道",
                            onClick = { RedStoneTunnelManager.stop() },
                            variant = PcButtonVariant.OUTLINE
                        )
                    }
                }
            }

            // ===== 最近日志 =====
            if (logs.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = VerseColors.PcBgCard
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "最近日志",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerseColors.PcTextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        logs.takeLast(20).forEach { line ->
                            Text(
                                line,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = VerseColors.PcTextMuted,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}