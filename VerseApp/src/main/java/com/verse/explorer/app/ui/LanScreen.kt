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
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.lan.LanNetworkScanner
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
                        checkedTrackColor = VerseColors.PcGreen,
                        checkedThumbColor = VerseColors.PcAccentText,
                        checkedBorderColor = VerseColors.PcGreen,
                        checkedIconColor = VerseColors.PcAccentText,
                        // 未启用态：浅色轨道 + 深色圆球，避免白球在浅色模式下不可见
                        uncheckedThumbColor = VerseColors.PcTextSecondary,
                        uncheckedTrackColor = VerseColors.PcBgTertiary,
                        uncheckedBorderColor = VerseColors.PcBorder
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
 * 红石联机（RedStone 中转隧道）：启动器侧仅作引导，隧道创建/管理在
 * 游戏内控制面板右栏完成（进游戏后可用）；此处不再提供启动器侧创建入口。
 */
@Composable
private fun RedStoneContent() {
    LazyContentFill {
        Text(
            "红石联机",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = VerseColors.PcTextPrimary
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = VerseColors.PcBgCard
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "请在进入游戏后使用",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcAccent
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "红石联机隧道在游戏内创建与管理：进入游戏后打开右侧控制面板，在「红石联机」中创建隧道并获取联机地址。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = VerseColors.PcTextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "使用步骤：",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "1. 进入游戏，在存档暂停菜单中「对局域网开放」；\n" +
                        "2. 打开控制面板右栏的「红石联机」；\n" +
                        "3. 选择中转节点并创建隧道，得到联机地址；\n" +
                        "4. 好友在多人游戏中直接连接该地址（无需安装任何东西）。",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = VerseColors.PcTextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "仅支持 Java 版，中转走 TCP。",
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted
                )
            }
        }
    }
}
