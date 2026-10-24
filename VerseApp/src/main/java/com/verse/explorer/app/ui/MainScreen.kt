package com.verse.explorer.app.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.verse.explorer.app.data.UpdateChecker
import com.verse.explorer.app.data.UpdateSources
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.components.ModpackImportFlow
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.UpdateDialog
import com.verse.explorer.app.ui.theme.VerseColors
import soup.compose.material.motion.animation.rememberSlideDistance

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    object Home : Screen("home", "主页", Icons.Outlined.Home, Icons.Filled.Home)
    object Versions : Screen("versions", "下载", Icons.Outlined.Download, Icons.Filled.Download)
    object Mods : Screen("mods", "模组", Icons.Outlined.Extension, Icons.Filled.Extension)
    object Modpacks : Screen("modpacks", "整合包", Icons.Outlined.Inventory2, Icons.Filled.Inventory2)
    object ResourcePacks : Screen("resourcepacks", "材质包", Icons.Outlined.Palette, Icons.Filled.Palette)
    object Shaders : Screen("shaders", "光影", Icons.Outlined.Flare, Icons.Filled.Flare)
    object Lan : Screen("lan", "联机", Icons.Outlined.Wifi, Icons.Filled.Wifi)
    object Transfer : Screen("transfer", "传输", Icons.Outlined.SwapHoriz, Icons.Filled.SwapHoriz)
    object Accounts : Screen("accounts", "账户", Icons.Outlined.Person, Icons.Filled.Person)
    object Settings : Screen("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings)
    object Java : Screen("java", "Java", Icons.Outlined.Code, Icons.Filled.Code)
    object Console : Screen("console", "问题反馈", Icons.Outlined.Feedback, Icons.Filled.Feedback)
}

internal data class NavItem(
    val screen: Screen? = null,
    val icon: ImageVector,
    val label: String,
    val isSubmenuToggle: Boolean = false,
    val submenuItems: List<Screen> = emptyList()
)

internal val mainNavItems = listOf(
    NavItem(screen = Screen.Home, icon = Icons.Outlined.Home, label = "主页"),
    NavItem(screen = Screen.Versions, icon = Icons.Outlined.Download, label = "下载"),
    NavItem(
        icon = Icons.Outlined.Widgets,
        label = "资源",
        isSubmenuToggle = true,
        submenuItems = listOf(Screen.Mods, Screen.Modpacks, Screen.ResourcePacks, Screen.Shaders)
    ),
    NavItem(screen = Screen.Lan, icon = Icons.Outlined.Wifi, label = "联机"),
    NavItem(screen = Screen.Transfer, icon = Icons.Outlined.SwapHoriz, label = "传输"),
    NavItem(screen = Screen.Accounts, icon = Icons.Outlined.Person, label = "账户"),
    NavItem(screen = Screen.Settings, icon = Icons.Outlined.Settings, label = "设置")
)

internal val footerNavItem = NavItem(
    screen = Screen.Console,
    icon = Icons.Outlined.Feedback,
    label = "问题反馈"
)

private val allSidebarRoutes = mainNavItems
    .flatMap { if (it.screen != null) listOf(it.screen.route) else it.submenuItems.map { s -> s.route } }
    .plus(footerNavItem.screen!!.route)
    .toSet()

private val submenuRoutes = mainNavItems
    .filter { it.isSubmenuToggle }
    .flatMap { it.submenuItems.map { s -> s.route } }
    .toSet()

@Composable
fun MainScreen(
    jreReady: Boolean = false,
    onLaunchGame: (String?) -> Unit = {},
    shareImportUri: Uri? = null,
    onShareImportHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    if (!jreReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VerseColors.PcBgPrimary),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RoseLoader(
    color = VerseColors.PcAccent
)
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在初始化Java运行环境...", color = VerseColors.PcTextSecondary, fontSize = 14.sp)
                Text("首次解压JRE,请稍候", color = VerseColors.PcTextMuted, fontSize = 12.sp)
            }
        }
        return
    }

    val showSidebar = currentRoute in allSidebarRoutes

    // 启动时自动检查更新：检测到新版本则弹窗提示；「忽略」后同一版本不再提示
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val info = UpdateChecker.check() ?: return@LaunchedEffect
        val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        if (prefs.getInt("update_ignored_version", 0) >= info.versionCode) return@LaunchedEffect
        updateInfo = info
    }
    updateInfo?.let { info ->
        UpdateDialog(
            versionName = "v${info.versionName}",
            releaseDate = info.releaseDate,
            items = info.changelog,
            part1Urls = UpdateSources.part1Urls(info.versionName),
            onIgnore = {
                context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
                    .edit().putInt("update_ignored_version", info.versionCode).apply()
                updateInfo = null
            },
            onGoUpdate = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            Uri.parse(info.downloadUrl)
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                updateInfo = null
            }
        )
    }

    // Material 动效库的 SharedAxis 滑动距离（组合层解析一次，过渡 lambda 内不可调用组合函数）
    val navSlideDistance = rememberSlideDistance()

    // 启用自定义背景时叠加 scrim 遮罩保证前景内容可读性(仅自定义背景时生效)；
    // 不透明度语义作用于卡片容器色：不透明度越低卡片遮蔽越弱，scrim 相应增强，
    // 不透明度接近 100% 时卡片自身足够遮挡，scrim 趋近于零
    val customBgActive = VersePersonalization.backgroundStyle != "none"
    val customBgScrimAlpha =
        0.60f * (100 - VersePersonalization.backgroundOpacity) / 100f

    // 悬浮导航液态玻璃(Kyant0/AndroidLiquidGlass backdrop-android 2.0.0):
    // RuntimeShader 实现仅支持 Android 13+,低版本自动降级为普通面板;
    // 内容层与玻璃层分离渲染,圆形按钮与胶囊容器从背后内容透镜采样
    val navGlassBackdrop = rememberLayerBackdrop()
    val glassSupported = Build.VERSION.SDK_INT >= 33 && VersePersonalization.sidebarGlass

    Box(modifier = Modifier.fillMaxSize()) {
        // 采样源层:自定义背景 + 页面内容,悬浮导航的玻璃层从此处透镜采样
        Box(modifier = Modifier.fillMaxSize().then(
            if (glassSupported) Modifier.layerBackdrop(navGlassBackdrop) else Modifier
        )) {
        // 背景:自定义背景(渐变/图片)
        AppBackground(Modifier.fillMaxSize())
        // 玻璃半透明遮罩(保证前景内容可读性,自定义背景时随不透明度减弱)
        Box(modifier = Modifier.fillMaxSize().background(
            if (customBgActive) VerseColors.PcGlassBg.copy(alpha = customBgScrimAlpha)
            else VerseColors.PcGlassBg
        )) {
        NavHost(
                    navController = navController,
            startDestination = Screen.Home.route,
            // 刘海避让由根容器统一按黑边处理，这里不再二次内缩
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                navEnterTransition(
                    detail = targetState.destination.route in detailRoutes,
                    pop = false,
                    slideDistance = navSlideDistance
                )
            },
            exitTransition = {
                navExitTransition(
                    detail = initialState.destination.route in detailRoutes,
                    pop = false,
                    slideDistance = navSlideDistance
                )
            },
            popEnterTransition = {
                navEnterTransition(
                    detail = targetState.destination.route in detailRoutes,
                    pop = true,
                    slideDistance = navSlideDistance
                )
            },
            popExitTransition = {
                navExitTransition(
                    detail = initialState.destination.route in detailRoutes,
                    pop = true,
                    slideDistance = navSlideDistance
                )
            }
        ) {
            composable(Screen.Home.route) { HomeScreen(navController, onLaunchGame) }
            composable(Screen.Versions.route) { VersionsScreen(navController) }
            composable(Screen.Mods.route) { ModsScreen(navController) }
            composable(Screen.Modpacks.route) { ModpacksScreen(navController) }
            composable(Screen.ResourcePacks.route) { ResourcePacksScreen(navController) }
            composable(Screen.Shaders.route) { ShadersScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController) }
            composable(Screen.Accounts.route) { AccountsScreen(navController) }
            composable(Screen.Lan.route) { LanScreen(navController) }
            composable(Screen.Transfer.route) { TransferScreen() }
            composable(Screen.Java.route) { JavaScreen(navController) }
            composable(Screen.Console.route) { ConsoleScreen(navController) }
            composable("installed_versions") {
                InstalledVersionsScreen(navController = navController)
            }
            composable("version_settings/{versionId}") { backStackEntry ->
                val versionId = backStackEntry.arguments?.getString("versionId") ?: ""
                VersionSettingsScreen(versionId = versionId, navController = navController)
            }
            composable("installed_mods/{versionId}") { backStackEntry ->
                val versionId = backStackEntry.arguments?.getString("versionId") ?: ""
                InstalledModsScreen(versionId = versionId, navController = navController)
            }
            composable("version_detail/{versionId}?type={versionType}&url={versionUrl}") { backStackEntry ->
                val versionId = backStackEntry.arguments?.getString("versionId") ?: ""
                val versionType = backStackEntry.arguments?.getString("versionType") ?: "release"
                val versionUrl = backStackEntry.arguments?.getString("versionUrl") ?: ""
                VersionDetailScreen(versionId, versionType, versionUrl, navController)
            }
            composable("mod_detail/{projectId}?source={source}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val source = backStackEntry.arguments?.getString("source") ?: "modrinth"
                ModDetailScreen(navController, projectId, "mod", source)
            }
            composable("modpack_detail/{projectId}?source={source}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val source = backStackEntry.arguments?.getString("source") ?: "modrinth"
                ModDetailScreen(navController, projectId, "modpack", source)
            }
            composable("resourcepack_detail/{projectId}?source={source}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val source = backStackEntry.arguments?.getString("source") ?: "modrinth"
                ModDetailScreen(navController, projectId, "resourcepack", source)
            }
            composable("shader_detail/{projectId}?source={source}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val source = backStackEntry.arguments?.getString("source") ?: "modrinth"
                ModDetailScreen(navController, projectId, "shader", source)
            }
        }
        }
        }

        // 悬浮圆形导航(最上层图层,不占页面布局;液态玻璃采样上层内容)
        if (showSidebar) {
            FloatingNavOverlay(
                navController = navController,
                currentRoute = currentRoute,
                backdrop = navGlassBackdrop,
                glassEnabled = glassSupported
            )
        }
        // 挂在导航外层:无论当前处于哪个页面,分享导入弹窗都能弹出
        ModpackImportFlow(
            importSource = shareImportUri,
            onHandled = onShareImportHandled
        )
    }
}

/**
 * 页签切换策略:切换时清空返回栈并定位到目标页,
 * 保证返回键行为可预期、各页签状态独立,
 * 同时避免多层返回栈导致的行为不一致问题
 */
internal fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
