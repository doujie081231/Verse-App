package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.verse.explorer.app.R
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.launch.GameLaunchController
import com.verse.explorer.app.launch.LaunchPhase
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.PcLaunchOverlay
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.core.account.AccountType
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    navController: NavHostController = rememberNavController(),
    onLaunchGame: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepo = SettingsRepository
    val launchController = remember { GameLaunchController(context) }
    val launchState by launchController.launchState.collectAsState()
    val scope = rememberCoroutineScope()

    var localVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var selectedVersionId by remember { mutableStateOf<String?>(settingsRepo.getCustomSetting("last_version")) }
    var latestRelease by remember { mutableStateOf("加载中...") }
    var latestSnapshot by remember { mutableStateOf("加载中...") }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showBoatMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val gameDir = GameConfig.getGameDir(context)
                val vm = VersionManager(gameDir)
                localVersions = vm.getLocalVersions()
                if (selectedVersionId == null && localVersions.isNotEmpty()) {
                    selectedVersionId = localVersions.first().id
                }
            } catch (_: Exception) {}
            try {
                val gameDir2 = GameConfig.getGameDir(context)
                val manifest = withContext(Dispatchers.IO) {
                    VersionManager(gameDir2).fetchRemoteVersions()
                }
                latestRelease = manifest.latest.release.ifEmpty { "未知" }
                latestSnapshot = manifest.latest.snapshot.ifEmpty { "未知" }
            } catch (_: Exception) {}
        }
    }

    val sortedVersions = remember(localVersions) {
        localVersions.sortedByDescending { v ->
            val parts = v.id.split(".")
            parts.map { it.toIntOrNull() ?: 0 }.let { (it.getOrNull(0) ?: 0) * 10000 + (it.getOrNull(1) ?: 0) * 100 + (it.getOrNull(2) ?: 0) }
        }
    }
    val selected = sortedVersions.find { it.id == selectedVersionId }

    Box(modifier = Modifier.fillMaxSize().background(VerseColors.PcBgPrimary)) {
        // 壁纸背景层 - 渐变模拟（使用有限坐标，Float.MAX_VALUE 会导致 LinearGradient.nativeCreate 崩溃）
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(
                colors = listOf(VerseColors.PcBgSecondary, VerseColors.PcBgTertiary)
            )
        ))

        // 启动卡放在最顶部（对齐 PC 端 home-quick-actions 布局）
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.TopStart) {
            HomeLaunchCard(
                versions = sortedVersions, selectedVersion = selected, selectedVersionId = selectedVersionId,
                onVersionSelected = { selectedVersionId = it }, isLaunching = launchState.isLaunching,
                showBoatMenu = showBoatMenu, onToggleBoatMenu = { showBoatMenu = it },
                onLaunch = { method -> if (!launchState.isLaunching) { SettingsRepository.setLaunchMethod(method); launchController.launchGame(selectedVersionId) } },
                onPlay = { if (!launchState.isLaunching) launchController.launchGame(selectedVersionId) },
                navController = navController
            )
        }

        // 全屏启动覆盖层（替换 LaunchModal）
        if (launchState.isLaunching || launchState.errors.isNotEmpty() || launchState.phase == LaunchPhase.FAILED) {
            PcLaunchOverlay(
                launchState = launchState,
                onDismiss = { launchController.stopGame() }
            )
        }

        // 删除确认对话框保留
        if (showDeleteDialog != null) {
            AlertDialog(onDismissRequest = { showDeleteDialog = null }, containerColor = VerseColors.PcBgCard,
                titleContentColor = VerseColors.PcTextPrimary, textContentColor = VerseColors.PcTextSecondary,
                shape = RoundedCornerShape(16.dp), title = { Text("删除版本", fontWeight = FontWeight.Bold) },
                text = { Text("确定要删除 Minecraft ${showDeleteDialog} 吗？\n此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        val versionId = showDeleteDialog!!; showDeleteDialog = null
                        val vm = com.verse.explorer.app.viewmodel.VersionsViewModel(context.applicationContext as android.app.Application)
                        vm.deleteVersion(versionId)
                        localVersions = VersionManager(GameConfig.getGameDir(context)).getLocalVersions()
                        if (selectedVersionId == versionId) selectedVersionId = localVersions.firstOrNull()?.id
                    }, colors = ButtonDefaults.textButtonColors(contentColor = VerseColors.PcRed)) { Text("删除", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消", color = VerseColors.PcTextSecondary) } })
        }
    }
}


// 首页启动卡片：对齐 PC 端 home-launch-card 横向布局（左账户 + 右版本选择&启动）
@Composable
private fun HomeLaunchCard(
    versions: List<MinecraftVersion>, selectedVersion: MinecraftVersion?, selectedVersionId: String?,
    onVersionSelected: (String) -> Unit, isLaunching: Boolean, showBoatMenu: Boolean,
    onToggleBoatMenu: (Boolean) -> Unit, onLaunch: (String) -> Unit, onPlay: () -> Unit,
    navController: NavHostController
) {
    val context = LocalContext.current
    val accountsViewModel = remember { AccountsViewModel(context.applicationContext as android.app.Application) }
    val selectedAccount by accountsViewModel.selectedAccount.collectAsState()
    LaunchedEffect(Unit) { accountsViewModel.loadAccounts() }
    val isLoggedIn = selectedAccount != null
    val accountName = selectedAccount?.username ?: "未登录"
    val accountType = selectedAccount?.type
    val skinUrl = selectedAccount?.skinUrl

    var expanded by remember { mutableStateOf(false) }

    PcCard(modifier = Modifier.fillMaxWidth()) {
        // 横向布局：左侧账户区(flex-shrink) + 右侧版本&启动(flex:1)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === 左侧：账户区（点击跳转账户管理）===
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { navController.navigate(Screen.Accounts.route) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 头像 56dp 圆角 10dp（对齐 PC account-avatar）
                val avatarBg: Brush = if (isLoggedIn) {
                    when (accountType) {
                        AccountType.MICROSOFT -> Brush.linearGradient(listOf(VerseColors.PcGreen, VerseColors.PcPurple))
                        AccountType.AUTHLIB -> Brush.linearGradient(listOf(VerseColors.PcPurple, VerseColors.PcOrange))
                        else -> Brush.linearGradient(listOf(VerseColors.PcTextMuted, VerseColors.PcTextSecondary))
                    }
                } else {
                    Brush.linearGradient(listOf(VerseColors.PcBgTertiary, VerseColors.PcBgTertiary))
                }
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(avatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoggedIn && !skinUrl.isNullOrBlank()) {
                        AsyncImage(model = skinUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    } else if (isLoggedIn) {
                        // 已登录但无皮肤：显示首字母
                        Text(accountName.take(1).uppercase(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        // 未登录：显示软件图标
                        Image(
                            painter = rememberAsyncImagePainter(model = R.mipmap.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                // 玩家名 + 账户类型
                Column {
                    Text(accountName, color = VerseColors.PcTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (accountType) { AccountType.MICROSOFT -> "Microsoft 正版"; AccountType.AUTHLIB -> "外置登录"; AccountType.OFFLINE -> "离线模式"; null -> "点击登录" },
                        color = VerseColors.PcTextMuted, fontSize = 11.sp
                    )
                }
            }

            // === 右侧：版本选择卡片 + 启动按钮（对齐 PC quick-launch）===
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 版本选择卡片（对齐 PC home-current-version-card）
                Box(modifier = Modifier.width(200.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(VerseColors.PcBgTertiary)
                            .clickable { expanded = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = selectedVersion?.id ?: (selectedVersionId ?: "选择版本"),
                                color = VerseColors.PcTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp), VerseColors.PcTextMuted)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = VerseColors.PcBgCard) {
                        versions.forEach { v ->
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(v.id, fontWeight = if (v.id == selectedVersionId) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, color = VerseColors.PcTextPrimary)
                                    if (v.id == selectedVersionId) { Spacer(Modifier.width(8.dp)); Icon(Icons.Filled.Check, null, Modifier.size(14.dp), VerseColors.PcAccent) }
                                }
                            }, onClick = { onVersionSelected(v.id); expanded = false })
                        }
                    }
                }
                // 启动按钮（对齐 PC home-launch-btn，带播放图标）
                PcButton(
                    text = if (isLaunching) "启动中..." else "启动游戏",
                    onClick = onPlay,
                    modifier = Modifier.width(120.dp),
                    variant = PcButtonVariant.LAUNCH,
                    icon = if (!isLaunching) Icons.Filled.PlayArrow else null,
                    enabled = selectedVersionId != null && !isLaunching
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, color = VerseColors.PcTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
}
