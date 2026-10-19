package com.verse.explorer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import com.verse.explorer.app.ui.home.HomeHubState
import com.verse.explorer.app.ui.home.HomeVersionActions
import com.verse.explorer.app.ui.home.LocalHomeHub
import com.verse.explorer.app.ui.home.LocalHomeVersionActions
import com.verse.explorer.app.ui.home.VerseHomeGrid
import com.verse.explorer.app.ui.home.VersionCardManager
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.cardgrid.state.rememberCardGridState
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 头像、版本选择与启动入口均为可控卡片，可拖动换位；
 * 头像支持放大缩小，版本选择与启动按钮尺寸固定。
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

    // 版本列表加载(返回主页时重新加载,并同步版本管理页修改的 last_version)
    LaunchedEffect(navController.currentBackStackEntry) {
        val route = navController.currentBackStackEntry?.destination?.route
        scope.launch {
            try {
                val vm = VersionManager(GameConfig.getGameDir(context))
                val versions = withContext(Dispatchers.IO) { vm.getLocalVersions() }
                localVersions = versions
                // 从版本管理页返回后,重新读取最后选中版本
                if (route == Screen.Home.route) {
                    selectedVersionId = settingsLastVersion() ?: selectedVersionId
                }
                if (selectedVersionId == null && versions.isNotEmpty()) {
                    selectedVersionId = versions.first().id
                }
                // 卡片列表刷新;首次使用自动固定当前版本
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
    val gridState = rememberCardGridState()
    val homeActions = remember {
        HomeVersionActions(
            onLaunch = { versionId ->
                if (!launchState.isLaunching) launchController.launchGame(versionId)
            },
            onOpenVersionSettings = { versionId ->
                navController.navigate("version_settings/$versionId")
            }
        )
    }

    // 创建窗口阶段的启动提示可被用户关闭；新一轮启动开始时恢复显示
    var launchOverlayDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(launchState.isLaunching) {
        if (launchState.isLaunching) launchOverlayDismissed = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val hubState = HomeHubState(
            account = selectedAccount,
            currentVersion = selected,
            currentVersionId = selectedVersionId,
            launching = launchState.isLaunching,
            onGoAccounts = { navController.navigate(Screen.Accounts.route) },
            onOpenVersions = { navController.navigate("installed_versions") },
            onLaunch = { if (!launchState.isLaunching) launchController.launchGame(selectedVersionId) }
        )
        CompositionLocalProvider(
            LocalHomeVersionActions provides homeActions,
            LocalHomeHub provides hubState
        ) {
            HomePageContent(gridState = gridState)
        }

        if (!launchOverlayDismissed &&
            (launchState.isLaunching || launchState.errors.isNotEmpty() || launchState.phase == LaunchPhase.FAILED)
        ) {
            PcLaunchOverlay(
                launchState = launchState,
                onDismiss = { launchOverlayDismissed = true }
            )
        }
    }
}

private fun settingsLastVersion(): String? = SettingsRepository.getCustomSetting("last_version")

@Composable
fun SectionTitle(title: String) {
    Text(title, color = VerseColors.PcTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
}

/** 运行中游戏实例 */
data class RunningGame(
    val versionId: String,
    val pid: Int,
    val startTime: Long
)

/**
 * 主页页面内容：整页均为可自定义排布的卡片网格，
 * 账户、当前版本、启动入口与已固定的版本全部是卡片组件。
 */
@Composable
private fun HomePageContent(
    gridState: com.verse.explorer.cardgrid.state.CardGridState
) {
    val homeScrollState = rememberScrollState()
    var screenBoundsHeightPx by remember { mutableStateOf(0f) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(homeScrollState)
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .onSizeChanged { screenBoundsHeightPx = it.height.toFloat() }
    ) {
        VerseHomeGrid(
            state = gridState,
            outerScrollState = homeScrollState,
            modifier = Modifier.fillMaxWidth(),
            screenBoundsHeightPx = screenBoundsHeightPx
        )
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * 头像:未登录显示虚线"添加账号"框;已登录显示皮肤头部。
 * 不内建点击手势:点按由账户卡片内容统一处理,长按拖拽由网格容器处理,
 * 内部任何手势消费都会让网格的长按会话失效。
 */
@Composable
internal fun HomeAvatar(
    account: com.verse.explorer.core.account.Account?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val sizeModifier = modifier.fillMaxSize()
    if (account == null) {
        // 无账户:虚线框 + 十字 + 添加账号
        Box(
            modifier = sizeModifier
                .clip(shape)
                .background(Color(0x0BFFFFFF)),
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
            modifier = sizeModifier.clip(shape)
        ) {
            AccountAvatar(account = account, modifier = Modifier.fillMaxSize(), shape = shape)
        }
    }
}

/**
 * 版本图标:优先整合包作者图标(安装时存 versions/<id>/modpack_icon.png),
 * 其次加载器图标(hint/名称),最后按版本类型回退(grass/cobblestone/old)
 */
@Composable
internal fun VersionIcon(
    version: MinecraftVersion,
    size: androidx.compose.ui.unit.Dp,
    iconKey: Int = 0
) {
    val context = LocalContext.current
    // 整合包作者图标(安装整合包时写入 versions/<id>/modpack_icon.png)；iconKey 变化（重设图标）时重新读取
    val packIconBitmap = remember(version.id, iconKey) {
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
 * 加载器徽章(原版/Forge/Fabric/NeoForge/整合包等)。
 * 优先读取 json 解析出的加载器(loaderHint),否则按版本名推断
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
