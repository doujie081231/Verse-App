package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
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
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.launch.GameLaunchController
import com.verse.explorer.app.launch.LaunchPhase
import com.verse.explorer.app.ui.components.AccountAvatar
import com.verse.explorer.app.ui.components.PcLaunchOverlay
import com.verse.explorer.app.ui.home.VersionCardManager
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.core.account.AccountType
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主页（对齐 versepc2 首页：顶部居中标题 + 大头像 + 账户信息 + 版本卡片 + 启动按钮 + 运行中卡片）
 */
@Composable
fun HomeScreen(
    navController: NavHostController = rememberNavController(),
    onLaunchGame: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val launchController = remember { GameLaunchController(context) }
    val launchState by launchController.launchState.collectAsState()
    val scope = rememberCoroutineScope()

    var localVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var selectedVersionId by remember { mutableStateOf<String?>(settingsLastVersion()) }
    var selectedAccount by remember { mutableStateOf<com.verse.explorer.core.account.Account?>(null) }
    var runningInstance by remember { mutableStateOf<RunningGame?>(null) }

    // 启动流程状态 -> 运行中卡片
    LaunchedEffect(launchState.phase) {
        when (launchState.phase) {
            LaunchPhase.LAUNCHING -> {
                if (runningInstance == null && selectedVersionId != null) {
                    runningInstance = RunningGame(
                        versionId = selectedVersionId!!,
                        pid = android.os.Process.myPid(),
                        startTime = System.currentTimeMillis()
                    )
                }
            }
            LaunchPhase.IDLE, LaunchPhase.FAILED -> runningInstance = null
            else -> {}
        }
    }

    // 账户加载
    LaunchedEffect(Unit) {
        val vm = AccountsViewModel(context.applicationContext as android.app.Application)
        vm.loadAccounts()
        vm.selectedAccount.collect { selectedAccount = it }
    }

    // 版本列表加载（返回主页时刷新选中版本，因版本管理页可能改动 last_version）
    LaunchedEffect(navController.currentBackStackEntry) {
        val route = navController.currentBackStackEntry?.destination?.route
        scope.launch {
            try {
                val vm = VersionManager(GameConfig.getGameDir(context))
                val versions = withContext(Dispatchers.IO) { vm.getLocalVersions() }
                localVersions = versions
                // 从版本管理页返回后，重新读取最后选中版本
                if (route == Screen.Home.route) {
                    selectedVersionId = settingsLastVersion() ?: selectedVersionId
                }
                if (selectedVersionId == null && versions.isNotEmpty()) {
                    selectedVersionId = versions.first().id
                }
                // 主页版本卡片：核对可用性；首次使用自动固定当前版本
                VersionCardManager.initialize(context)
                VersionCardManager.refresh(context, versions)
                if (!VersionCardManager.hasSeeded(context) && versions.isNotEmpty()) {
                    val seed = settingsLastVersion()?.takeIf { last -> versions.any { it.id == last } }
                        ?: versions.first().id
                    VersionCardManager.addCard(context, seed)
                    VersionCardManager.markSeeded(context)
                }
            } catch (_: Exception) {}
        }
    }

    val selected = localVersions.find { it.id == selectedVersionId }
    val gridState = com.verse.explorer.cardgrid.state.rememberCardGridState()
    val homeActions = remember {
        com.verse.explorer.app.ui.home.HomeVersionActions(
            onLaunch = { versionId ->
                if (!launchState.isLaunching) launchController.launchGame(versionId)
            },
            onOpenVersionSettings = { versionId ->
                navController.navigate("version_settings/$versionId")
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.verse.explorer.app.ui.home.LocalHomeVersionActions provides homeActions
        ) {
            HomePageContent(
                selectedAccount = selectedAccount,
                selected = selected,
                selectedVersionId = selectedVersionId,
                isLaunching = launchState.isLaunching,
                runningInstance = runningInstance,
                gridState = gridState,
                onGoAccounts = { navController.navigate(Screen.Accounts.route) },
                onOpenVersions = { navController.navigate("installed_versions") },
                onLaunch = { if (!launchState.isLaunching) launchController.launchGame(selectedVersionId) }
            )
        }

        // 启动加载弹窗（ZL2 样式：遮罩 + 居中卡片，不可取消）
        if (launchState.isLaunching || launchState.errors.isNotEmpty() || launchState.phase == LaunchPhase.FAILED) {
            PcLaunchOverlay(
                launchState = launchState
            )
        }
    }
}

private fun settingsLastVersion(): String? = SettingsRepository.getCustomSetting("last_version")

@Composable
fun SectionTitle(title: String) {
    Text(title, color = VerseColors.PcTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
}

/** 运行中游戏实例（对齐 versepc2 VersePCGameStore.instances 条目） */
data class RunningGame(
    val versionId: String,
    val pid: Int,
    val startTime: Long
)

/**
 * 主页页面内容（对齐 versepc2 .home-page 布局）：
 * 顶部固定区（账户、当前版本、启动入口）+ 下方可自定义排布的版本卡片网格
 */
@Composable
private fun HomePageContent(
    selectedAccount: com.verse.explorer.core.account.Account?,
    selected: MinecraftVersion?,
    selectedVersionId: String?,
    isLaunching: Boolean,
    runningInstance: RunningGame?,
    gridState: com.verse.explorer.cardgrid.state.CardGridState,
    onGoAccounts: () -> Unit,
    onOpenVersions: () -> Unit,
    onLaunch: () -> Unit
) {
    // 无顶部标题（直接去掉，腾出纵向空间）；中心内容列竖排，整体紧凑化
    val homeScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(homeScrollState)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 头像
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.22f)
                    .aspectRatio(1f)
                    .weight(1f, fill = false),
                contentAlignment = Alignment.Center
            ) {
                HomeAvatar(account = selectedAccount, onClick = onGoAccounts)
            }

            // 账户信息（对齐 .home-account-bar）
            Spacer(Modifier.height(4.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selectedAccount?.username ?: "未登录",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerseColors.PcTextPrimary
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = when (selectedAccount?.type) {
                        AccountType.MICROSOFT -> "Microsoft 正版"
                        AccountType.AUTHLIB -> "外置登录"
                        AccountType.OFFLINE -> "离线模式"
                        null -> "点击登录"
                    },
                    fontSize = 9.sp,
                    color = VerseColors.PcTextMuted
                )
            }

            // 版本卡片（对齐 .home-version-card-wrap width min(420px, 80%)）
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.66f)
            ) {
                HomeCurrentVersionCard(
                    selected = selected,
                    onClick = onOpenVersions
                )
            }

            // 启动按钮（对齐 .home-launch-btn）
            Spacer(Modifier.height(6.dp))
            HomeLaunchButton(isLaunching = isLaunching, enabled = selectedVersionId != null, onClick = onLaunch)

            // 运行中游戏卡片（对齐 .home-running-card，有运行实例时显示）
            if (runningInstance != null) {
                Spacer(Modifier.height(6.dp))
                HomeRunningCard(instance = runningInstance)
            }
            Spacer(Modifier.height(10.dp))

            // 主页版本卡片网格：长按卡片拖动/缩放排布，工具条可删除，布局自动保存
            com.verse.explorer.app.ui.home.VerseHomeGrid(
                state = gridState,
                outerScrollState = homeScrollState,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
        }
}

/**
 * 头像块：未登录显示虚线"添加账号"框；已登录显示皮肤头部
 */
@Composable
private fun HomeAvatar(
    account: com.verse.explorer.core.account.Account?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val sizeModifier = Modifier.fillMaxSize()
    if (account == null) {
        // 无账户：虚线框 + 十字 + 添加账号
        Box(
            modifier = sizeModifier
                .clip(shape)
                .background(Color(0x0BFFFFFF))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = VerseColors.PcTextMuted,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text("添加账号", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcTextMuted)
            }
            // 虚线边框
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Color.Transparent)
                    .then(Modifier.border(2.5.dp, VerseColors.PcBorder, shape))
            )
        }
    } else {
        Box(
            modifier = sizeModifier
                .clip(shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            AccountAvatar(account = account, modifier = Modifier.fillMaxSize(), shape = shape)
        }
    }
}

/**
 * 主页内嵌版本卡片（对齐 .home-current-version-card）。
 * 点击整卡进入「已安装版本」管理页（对齐 PC 主页版本卡片跳转 installed-versions）。
 */
@Composable
private fun HomeCurrentVersionCard(
    selected: MinecraftVersion?,
    onClick: () -> Unit
) {
    if (selected == null) {
        // 占位："点击选择版本"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(VerseColors.PcBgTertiary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = VerseColors.PcTextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("点击选择版本", fontSize = 12.sp, color = VerseColors.PcTextMuted)
            }
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(14.dp), VerseColors.PcTextMuted)
        }
    } else {
        // 版本卡片：图标 + 名称 + badge + 箭头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(VerseColors.PcBgTertiary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VersionIcon(version = selected, size = 26.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selected.id,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = VerseColors.PcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                LoaderBadge(version = selected)
            }
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(14.dp), VerseColors.PcTextMuted)
        }
    }
}

/**
 * 版本图标：优先整合包作者图标（安装时存 versions/<id>/modpack_icon.png），
 * 其次加载器图标（hint/名称），最后按版本类型渲染（grass/cobblestone/old）
 */
@Composable
internal fun VersionIcon(version: MinecraftVersion, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    // 整合包作者图标（安装整合包时写入 versions/<id>/modpack_icon.png）
    val packIconBitmap = remember(version.id) {
        try {
            val packIcon = File(
                GameConfig.getVersionsDir(GameConfig.getGameDir(context)),
                version.id
            ).resolve("modpack_icon.png")
            if (packIcon.exists() && packIcon.length() > 0) {
                android.graphics.BitmapFactory.decodeFile(packIcon.absolutePath)?.asImageBitmap()
            } else null
        } catch (e: Exception) { null }
    }
    val lower = version.id.lowercase()
    val hint = version.loaderHint?.lowercase()
    val loaderAsset = hint?.let { h ->
        when (h) {
            "fabric" -> "icons/loader_icon_fabric.png"
            "quilt" -> "icons/loader_icon_quilt.png"
            "neoforge" -> "icons/loader_icon_neoforge.png"
            "forge" -> "icons/loader_icon_forge.png"
            "optifine" -> "icons/loader_icon_optifine.png"
            else -> null
        }
    } ?: when {
        lower.contains("fabric") -> "icons/loader_icon_fabric.png"
        lower.contains("quilt") -> "icons/loader_icon_quilt.png"
        lower.contains("neoforge") -> "icons/loader_icon_neoforge.png"
        lower.contains("forge") -> "icons/loader_icon_forge.png"
        lower.contains("optifine") -> "icons/loader_icon_optifine.png"
        else -> null
    }
    val iconAssetPath = loaderAsset ?: when (version.type) {
        com.verse.explorer.core.version.VersionType.SNAPSHOT -> "icons/version_icon_cobblestone.png"
        com.verse.explorer.core.version.VersionType.OLD_ALPHA,
        com.verse.explorer.core.version.VersionType.OLD_BETA -> "icons/version_icon_old.png"
        else -> "icons/version_icon_grass.png"
    }
    val iconBg = when (version.type) {
        com.verse.explorer.core.version.VersionType.SNAPSHOT -> VerseColors.PcOrange.copy(alpha = 0.15f)
        com.verse.explorer.core.version.VersionType.OLD_ALPHA,
        com.verse.explorer.core.version.VersionType.OLD_BETA -> VerseColors.PcPurple.copy(alpha = 0.15f)
        else -> VerseColors.PcGreen.copy(alpha = 0.15f)
    }
    val assetBitmap = remember(iconAssetPath) {
        try {
            context.assets.open(iconAssetPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }
    val iconBitmap = packIconBitmap ?: assetBitmap
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(iconBg),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(size * 0.9f), contentScale = ContentScale.Fit)
        }
    }
}

/**
 * 加载器徽章（对齐 v-badge：原版/Forge/Fabric/NeoForge/整合包）。
 * 优先版本 json 识别的加载器（loaderHint），再退化名称关键词判定。
 */
@Composable
internal fun LoaderBadge(version: MinecraftVersion) {
    val hint = version.loaderHint?.lowercase()
    val (badge, color) = if (!hint.isNullOrBlank()) {
        when (hint) {
            "fabric" -> "Fabric" to VerseColors.ForgeColor
            "quilt" -> "Quilt" to VerseColors.PcAccent
            "neoforge" -> "NeoForge" to VerseColors.PcOrange
            "forge" -> "Forge" to VerseColors.PcOrange
            "optifine" -> "OptiFine" to VerseColors.PcYellow
            else -> "原版" to VerseColors.PcTextSecondary
        }
    } else {
        val lower = version.id.lowercase()
        when {
            lower.contains("fabric") -> "Fabric" to VerseColors.ForgeColor
            lower.contains("forge") -> "Forge" to VerseColors.PcOrange
            lower.contains("neoforge") -> "NeoForge" to VerseColors.PcOrange
            lower.contains("modpack") || lower.contains("整合") -> "整合包" to VerseColors.PcGreen
            else -> "原版" to VerseColors.PcTextSecondary
        }
    }
    Surface(shape = RoundedCornerShape(3.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text = badge,
            fontSize = 9.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * 主页启动按钮（对齐 .home-launch-btn：大号绿色渐变）
 */
@Composable
private fun HomeLaunchButton(isLaunching: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val textColor = if (enabled && !isLaunching) {
        VerseColors.PcAccentText
    } else {
        VerseColors.PcAccentText.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = Brush.linearGradient(listOf(VerseColors.PcLaunchGradientStart, VerseColors.PcLaunchGradientEnd))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled && !isLaunching,
                onClick = onClick
            )
            .padding(horizontal = 30.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isLaunching) "启动中..." else "启动游戏",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

/**
 * 运行中游戏卡片（对齐 .home-running-card）
 */
@Composable
private fun HomeRunningCard(instance: RunningGame) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val elapsedSec = ((now - instance.startTime) / 1000).coerceAtLeast(0)
    val mins = elapsedSec / 60
    val secs = elapsedSec % 60
    val elapsedText = if (mins > 0) "${mins}分${secs}秒" else "${secs}秒"

    Surface(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(0.8f),
        shape = RoundedCornerShape(14.dp),
        color = VerseColors.PcGlassCardBg,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(VerseColors.PcGreen))
                Text("启动任务", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextPrimary)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(VerseColors.PcBgPrimary)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(instance.versionId, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text("PID: ${instance.pid} · $elapsedText", fontSize = 11.sp, color = VerseColors.PcTextSecondary)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(VerseColors.PcRed)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("停止", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}