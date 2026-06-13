package com.verse.explorer.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.launch.GameLaunchController
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.account.AccountType
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import com.verse.explorer.core.version.VersionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    navController: NavHostController = rememberNavController(),
    onLaunchGame: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
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

    Box(modifier = Modifier.fillMaxSize().background(VerseColors.McDarkBg)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                StitchHeroSection(versions = sortedVersions, selectedVersion = selected, selectedVersionId = selectedVersionId,
                    onVersionSelected = { selectedVersionId = it }, isLaunching = launchState.isLaunching,
                    showBoatMenu = showBoatMenu, onToggleBoatMenu = { showBoatMenu = it },
                    onLaunch = { method -> if (!launchState.isLaunching) { SettingsRepository(context).setLaunchMethod(method); launchController.launchGame(selectedVersionId) } },
                    onPlay = { if (!launchState.isLaunching) launchController.launchGame(selectedVersionId) },
                    onStop = { launchController.stopGame() })
            }
            item {
                QuickActionsRow(modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-16).dp),
                    onInstallForge = { navController.navigate(Screen.Mods.route) },
                    onInstallFabric = { navController.navigate(Screen.Mods.route) },
                    onOpenModsFolder = { },
                    onBrowseMods = { navController.navigate(Screen.Mods.route) })
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-4).dp)) {
                    AccountSummaryCard(navController = navController)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("最新动态", color = VerseColors.McTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        TextButton(onClick = { navController.navigate(Screen.Versions.route) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("查看全部", fontSize = 11.sp, color = VerseColors.McGreen)
                            Icon(Icons.Filled.ArrowForward, null, Modifier.size(14.dp), VerseColors.McGreen)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NewsCard(Modifier.weight(1f), "正式版", VerseColors.McGreen, latestRelease, Icons.Filled.CheckCircle) { navController.navigate(Screen.Versions.route) }
                        NewsCard(Modifier.weight(1f), "快照版", VerseColors.McGold, latestSnapshot, Icons.Filled.Build) { navController.navigate(Screen.Versions.route) }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("已安装版本 (${localVersions.size})")
                        TextButton(onClick = { navController.navigate(Screen.Versions.route) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("查看全部", fontSize = 11.sp, color = VerseColors.McGreen)
                            Icon(Icons.Filled.ArrowForward, null, Modifier.size(14.dp), VerseColors.McGreen)
                        }
                    }
                }
            }
            if (sortedVersions.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = VerseColors.McCardBg) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.CloudDownload, null, Modifier.size(36.dp), VerseColors.McTextMuted)
                                    Spacer(Modifier.height(8.dp))
                                    Text("暂无已安装的版本", color = VerseColors.McTextSecondary, fontSize = 13.sp)
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(onClick = { navController.navigate(Screen.Versions.route) }) { Text("前往下载版本", color = VerseColors.McGreen, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            } else {
                items(sortedVersions.take(6), key = { it.id }) { version ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                        InstalledVersionCard(version = version, isSelected = selectedVersionId == version.id,
                            onClick = { selectedVersionId = version.id },
                            onClickSettings = { navController.navigate("version_settings/${version.id}") },
                            onDelete = { showDeleteDialog = version.id })
                    }
                }
                if (sortedVersions.size > 6) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            TextButton(onClick = { navController.navigate(Screen.Versions.route) }, modifier = Modifier.fillMaxWidth()) {
                                Text("查看全部 ${sortedVersions.size} 个版本 →", fontSize = 12.sp, color = VerseColors.McGreen)
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ServerStatusCard(navController = navController)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
        if (launchState.isLaunching || launchState.errors.isNotEmpty()) {
            LaunchModal(launchState = launchState, onDismiss = { launchController.stopGame() })
        }
        if (showDeleteDialog != null) {
            AlertDialog(onDismissRequest = { showDeleteDialog = null }, containerColor = VerseColors.McOverlayContentBg,
                titleContentColor = VerseColors.McTextPrimary, textContentColor = VerseColors.McTextSecondary,
                shape = RoundedCornerShape(16.dp), title = { Text("删除版本", fontWeight = FontWeight.Bold) },
                text = { Text("确定要删除 Minecraft ${showDeleteDialog} 吗？\n此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        val versionId = showDeleteDialog!!; showDeleteDialog = null
                        val vm = com.verse.explorer.app.viewmodel.VersionsViewModel(context.applicationContext as android.app.Application)
                        vm.deleteVersion(versionId)
                        localVersions = VersionManager(GameConfig.getGameDir(context)).getLocalVersions()
                        if (selectedVersionId == versionId) selectedVersionId = localVersions.firstOrNull()?.id
                    }, colors = ButtonDefaults.textButtonColors(contentColor = VerseColors.McRedstone)) { Text("删除", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消", color = VerseColors.McTextSecondary) } })
        }
    }
}



@Composable
private fun StitchHeroSection(
    versions: List<MinecraftVersion>, selectedVersion: MinecraftVersion?, selectedVersionId: String?,
    onVersionSelected: (String) -> Unit, isLaunching: Boolean, showBoatMenu: Boolean,
    onToggleBoatMenu: (Boolean) -> Unit, onLaunch: (String) -> Unit, onPlay: () -> Unit, onStop: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2C4A1E), VerseColors.McGreenDark, VerseColors.McGreen, VerseColors.McGreenLight.copy(alpha = 0.3f)))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Transparent, VerseColors.McDarkBg.copy(alpha = 0.5f)))))
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("MINECRAFT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 2.sp)
                    Text("LAUNCHER", color = VerseColors.McGreenLight, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 6.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Verse 探索者", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                IconButton(onClick = { onToggleBoatMenu(true) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, "启动方式", Modifier.size(22.dp), Color.White.copy(alpha = 0.8f))
                }
                DropdownMenu(expanded = showBoatMenu, onDismissRequest = { onToggleBoatMenu(false) }) {
                    DropdownMenuItem(text = { Text("Pojav 启动 (默认)", fontSize = 13.sp) },
                        onClick = { onToggleBoatMenu(false); onLaunch("pojav") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp), VerseColors.McGreen) })
                    DropdownMenuItem(text = { Text("Boat 启动 (JVMLoader)", fontSize = 13.sp) },
                        onClick = { onToggleBoatMenu(false); onLaunch("boat") },
                        leadingIcon = { Icon(Icons.Filled.RocketLaunch, null, Modifier.size(18.dp), VerseColors.McWater) })
                }
            }
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = Color.Black.copy(alpha = 0.35f)) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = VerseColors.McCardBg.copy(alpha = 0.6f), contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VerseColors.McCardBorder.copy(alpha = 0.4f))) {
                            Icon(Icons.Filled.ChevronRight, null, Modifier.size(16.dp), Color.White.copy(alpha = 0.5f))
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("Version: ${selectedVersion?.id ?: (selectedVersionId ?: "选择版本")}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp), Color.White.copy(alpha = 0.6f))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            versions.forEach { v ->
                                DropdownMenuItem(text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(v.id, fontWeight = if (v.id == selectedVersionId) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                        if (v.id == selectedVersionId) { Spacer(Modifier.width(8.dp)); Icon(Icons.Filled.Check, null, Modifier.size(14.dp), VerseColors.McGreen) }
                                    }
                                }, onClick = { onVersionSelected(v.id); expanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onPlay, enabled = selectedVersionId != null && !isLaunching, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McGreen, disabledContainerColor = VerseColors.McButtonDisabled),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                        if (isLaunching) CircularProgressIndicator(Modifier.size(16.dp), Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isLaunching) "启动中..." else "PLAY", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(modifier: Modifier = Modifier, onInstallForge: () -> Unit, onInstallFabric: () -> Unit, onOpenModsFolder: () -> Unit, onBrowseMods: () -> Unit) {
    Surface(modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = VerseColors.McCardBg) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickActionItem(Icons.Outlined.Build, "Forge", VerseColors.ForgeColor, onInstallForge, Modifier.weight(1f))
            QuickActionItem(Icons.Outlined.Texture, "Fabric", VerseColors.FabricGreen, onInstallFabric, Modifier.weight(1f))
            QuickActionItem(Icons.Outlined.FolderOpen, "Mods", VerseColors.McWater, onOpenModsFolder, Modifier.weight(1f))
            QuickActionItem(Icons.Outlined.Extension, "浏览", VerseColors.PrimaryPurple, onBrowseMods, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), color)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = VerseColors.McTextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NewsCard(modifier: Modifier = Modifier, label: String, color: Color, versionId: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), RoundedCornerShape(12.dp), color = VerseColors.McCardBg) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(16.dp), color) }
                Spacer(Modifier.width(8.dp))
                Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(versionId, color = VerseColors.McTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(if (label == "正式版") "最新稳定版本" else "最新测试版本", color = VerseColors.McTextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun AccountSummaryCard(navController: NavHostController) {
    val accountManager = VerseApplication.accountManager
    val accounts by accountManager.accounts.collectAsState()
    val selectedAccount by accountManager.selectedAccount.collectAsState()
    LaunchedEffect(Unit) { accountManager.loadAccounts() }
    val isLoggedIn = selectedAccount != null
    val accountName = selectedAccount?.username ?: "未登录"
    val accountType = selectedAccount?.type
    val skinUrl = selectedAccount?.skinUrl
    Surface(Modifier.fillMaxWidth().clickable { navController.navigate(Screen.Accounts.route) }, RoundedCornerShape(12.dp), color = VerseColors.McCardBg) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(
                if (isLoggedIn) {
                    when (accountType) { AccountType.MICROSOFT -> Brush.linearGradient(listOf(VerseColors.McGreen, VerseColors.McWater)); AccountType.AUTHLIB -> Brush.linearGradient(listOf(VerseColors.McWater, VerseColors.McGold)); else -> Brush.linearGradient(listOf(VerseColors.McStone, VerseColors.McTextMuted)) }
                } else { Brush.linearGradient(listOf(VerseColors.McTextMuted, VerseColors.McTextDim)) }
            ), contentAlignment = Alignment.Center) {
                if (isLoggedIn && !skinUrl.isNullOrBlank()) {
                    AsyncImage(model = skinUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else { Icon(Icons.Filled.Person, null, Modifier.size(22.dp), Color.White) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(accountName, color = VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(when (accountType) { AccountType.MICROSOFT -> "Microsoft 正版"; AccountType.AUTHLIB -> "外置登录"; AccountType.OFFLINE -> "离线模式"; null -> "点击登录" }, color = VerseColors.McTextMuted, fontSize = 11.sp)
            }
            if (isLoggedIn) {
                Surface(shape = RoundedCornerShape(6.dp), color = when (accountType) { AccountType.MICROSOFT -> VerseColors.McGreen.copy(alpha = 0.1f); AccountType.AUTHLIB -> VerseColors.McWater.copy(alpha = 0.1f); else -> VerseColors.McTextMuted.copy(alpha = 0.1f) }) {
                    Text(when (accountType) { AccountType.MICROSOFT -> "Microsoft"; AccountType.AUTHLIB -> "外置登录"; AccountType.OFFLINE -> "离线"; null -> "" }, color = when (accountType) { AccountType.MICROSOFT -> VerseColors.McGreen; AccountType.AUTHLIB -> VerseColors.McWater; else -> VerseColors.McTextMuted }, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), VerseColors.McTextMuted)
        }
    }
}

@Composable
private fun ServerStatusCard(navController: NavHostController) {
    Surface(Modifier.fillMaxWidth().clickable { navController.navigate(Screen.Lan.route) }, RoundedCornerShape(12.dp), color = VerseColors.McCardBg) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(VerseColors.McWater.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Dns, null, Modifier.size(18.dp), VerseColors.McWater) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("本地服务器", color = VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text("点击管理联机与服务器设置", color = VerseColors.McTextMuted, fontSize = 11.sp)
            }
            Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.McGreen.copy(alpha = 0.1f)) { Text("联机", color = VerseColors.McGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(16.dp), VerseColors.McTextMuted)
        }
    }
}

@Composable
private fun InstalledVersionCard(version: MinecraftVersion, isSelected: Boolean, onClick: () -> Unit, onClickSettings: () -> Unit, onDelete: () -> Unit = {}) {
    val context = LocalContext.current
    val iconAssetPath = when (version.type) { VersionType.SNAPSHOT -> "icons/version_icon_cobblestone.png"; VersionType.OLD_ALPHA, VersionType.OLD_BETA -> "icons/version_icon_old.png"; else -> "icons/version_icon_grass.png" }
    val iconBitmap = remember(iconAssetPath) { try { context.assets.open(iconAssetPath).use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() } } catch (e: Exception) { null } }
    val iconBg = when (version.type) { VersionType.SNAPSHOT -> VerseColors.McGold.copy(alpha = 0.15f); VersionType.OLD_ALPHA, VersionType.OLD_BETA -> VerseColors.McStone.copy(alpha = 0.15f); else -> if (isSelected) VerseColors.McGreen.copy(alpha = 0.15f) else VerseColors.McGreen.copy(alpha = 0.12f) }
    val borderColor = if (isSelected) VerseColors.McGreen.copy(alpha = 0.3f) else VerseColors.McCardBorder
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(12.dp), color = VerseColors.McCardBg, border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                if (iconBitmap != null) Image(bitmap = iconBitmap, contentDescription = version.id, modifier = Modifier.size(28.dp), contentScale = ContentScale.Fit)
                else Icon(when (version.type) { VersionType.RELEASE -> Icons.Filled.Diamond; VersionType.SNAPSHOT -> Icons.Filled.NewReleases; else -> Icons.Filled.History }, null, Modifier.size(20.dp), tint = when (version.type) { VersionType.RELEASE -> VerseColors.McGreen; VersionType.SNAPSHOT -> VerseColors.McGold; else -> VerseColors.McStone })
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(version.id, color = if (isSelected) VerseColors.McGreen else VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(when (version.type) { VersionType.RELEASE -> "正式版"; VersionType.SNAPSHOT -> "快照版"; VersionType.OLD_BETA -> "旧测试版"; VersionType.OLD_ALPHA -> "旧内测版" } + " · " + (version.releaseTime?.take(10) ?: "未知"), color = VerseColors.McTextSecondary, fontSize = 11.sp)
            }
            if (isSelected) { Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.McGreen.copy(alpha = 0.1f)) { Text("已选", color = VerseColors.McGreen, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.width(6.dp)) }
            IconButton(onClick = onClickSettings, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.Settings, null, Modifier.size(16.dp), VerseColors.McTextMuted) }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) { Icon(Icons.Filled.Delete, null, Modifier.size(16.dp), VerseColors.McRedstone.copy(alpha = 0.6f)) }
        }
    }
}



@Composable
fun PageHeader(title: String, subtitle: String, customInfo: String = "") {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, color = VerseColors.McTextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = VerseColors.McTextSecondary, fontSize = 13.sp)
        if (customInfo.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(customInfo, color = VerseColors.McTextMuted, fontSize = 11.sp) }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, color = VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
}

@Composable
private fun LaunchModal(launchState: com.verse.explorer.app.launch.LaunchState, onDismiss: () -> Unit) {
    val steps = launchState.steps
    val currentIdx = launchState.currentStepIndex
    val progress = if (steps.isNotEmpty()) ((currentIdx + 1).toFloat() / steps.size) else 0f
    val hasErrors = launchState.errors.isNotEmpty()
    val isFailed = launchState.phase == com.verse.explorer.app.launch.LaunchPhase.FAILED
    AlertDialog(onDismissRequest = { if (!launchState.isLaunching || isFailed) onDismiss() },
        modifier = Modifier.fillMaxWidth(0.95f), containerColor = VerseColors.McOverlayContentBg,
        titleContentColor = VerseColors.McTextPrimary, shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(if (isFailed) listOf(VerseColors.McRedstone, VerseColors.McLava) else listOf(VerseColors.McGreen, VerseColors.McWater))), contentAlignment = Alignment.Center) {
                    Icon(if (isFailed) Icons.Outlined.ErrorOutline else Icons.Filled.RocketLaunch, null, Modifier.size(18.dp), Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (isFailed) "启动检查失败" else "正在启动", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VerseColors.McTextPrimary)
                    Text(launchState.message, fontSize = 12.sp, color = if (isFailed) VerseColors.McRedstone else VerseColors.McTextSecondary)
                }
            }
        },
        text = {
            Column {
                if (hasErrors) {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = VerseColors.McRedstone.copy(alpha = 0.08f))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("请修复以下问题", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.McRedstone)
                            Spacer(Modifier.height(6.dp))
                            launchState.errors.forEach { error -> Row(Modifier.padding(vertical = 2.dp)) { Text("?", color = VerseColors.McRedstone, fontSize = 12.sp); Text(error, fontSize = 12.sp, color = VerseColors.McTextPrimary) } }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = if (isFailed || hasErrors) VerseColors.McRedstone else VerseColors.McGreen, trackColor = VerseColors.McBg)
                Spacer(Modifier.height(6.dp))
                Text("${(progress * 100).toInt()}% · ${launchState.message}", fontSize = 11.sp, color = VerseColors.McTextSecondary)
                Spacer(Modifier.height(10.dp))
                steps.forEachIndexed { idx, step ->
                    if (idx > currentIdx + 3 && idx < steps.size - 1) return@forEachIndexed
                    val isCurrent = idx == currentIdx; val isPast = idx < currentIdx
                    val failed = step.status.contains("失败") || step.status.contains("error")
                    val iconColor = when { failed -> VerseColors.McRedstone; isCurrent && !failed -> VerseColors.McWater; step.status.contains("完成") -> VerseColors.McGreen; else -> VerseColors.McTextMuted }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isCurrent && !failed) CircularProgressIndicator(Modifier.size(16.dp), VerseColors.McWater, strokeWidth = 2.dp)
                        else Icon(when { failed -> Icons.Filled.Cancel; step.status.contains("完成") -> Icons.Filled.CheckCircle; isPast -> Icons.Filled.CheckCircle; else -> Icons.Filled.FiberManualRecord }, null, Modifier.size(16.dp), iconColor)
                        Spacer(Modifier.width(8.dp))
                        Text(step.name, fontSize = 12.sp, color = if (idx > currentIdx) VerseColors.McTextMuted else VerseColors.McTextPrimary, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
                        Spacer(Modifier.weight(1f))
                        Text(step.status, fontSize = 9.sp, color = iconColor)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isFailed) "关闭" else "取消", color = if (isFailed) VerseColors.McRedstone else VerseColors.McTextMuted) } })
}

