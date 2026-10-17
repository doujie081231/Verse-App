package com.verse.explorer.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.components.ModpackImportFlow
import com.verse.explorer.app.ui.theme.ActionMenuSide
import com.verse.explorer.app.ui.theme.VerseColors
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance
import android.net.Uri
import android.os.Build

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

private data class NavItem(
    val screen: Screen? = null,
    val icon: ImageVector,
    val label: String,
    val isSubmenuToggle: Boolean = false,
    val submenuItems: List<Screen> = emptyList()
)

private val mainNavItems = listOf(
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

private val footerNavItem = NavItem(
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

// ==================== 页面切换动画（Material 动效规范，fornewid/material-motion-compose）====================
// 顶层页签之间无强关联 → FadeThrough；详情/深入页面有层级关系 → SharedAxis X；
// 返回（pop）方向取反，时长为库内 Material 标准值（300ms），低配机不再受长动画拖累

/** 深入型页面路由：与来源页存在层级关系，使用 SharedAxis X 过渡 */
private val detailRoutes = setOf(
    "installed_versions",
    "version_settings/{versionId}",
    "installed_mods/{versionId}",
    "version_detail/{versionId}?type={versionType}&url={versionUrl}",
    "mod_detail/{projectId}?source={source}",
    "modpack_detail/{projectId}?source={source}",
    "resourcepack_detail/{projectId}?source={source}",
    "shader_detail/{projectId}?source={source}"
)

private fun navEnterTransition(
    detail: Boolean,
    pop: Boolean,
    slideDistance: Int
): EnterTransition = if (detail) {
    materialSharedAxisXIn(forward = !pop, slideDistance = slideDistance)
} else {
    materialFadeThroughIn()
}

private fun navExitTransition(
    detail: Boolean,
    pop: Boolean,
    slideDistance: Int
): ExitTransition = if (detail) {
    materialSharedAxisXOut(forward = !pop, slideDistance = slideDistance)
} else {
    materialFadeThroughOut()
}

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
private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun FloatingNavOverlay(
    navController: NavHostController,
    currentRoute: String?,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    glassEnabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val openSubmenus = remember { mutableStateMapOf<String, Boolean>() }

    // 操作菜单停靠侧：START=阅读方向起始侧（左），END=末尾侧（右），默认右侧；
    // 布局整体镜像（按钮与胶囊面板的锚点/边距对调），毛玻璃层为 backdrop 采样、与位置无关不受影响
    val dockEnd = VersePersonalization.actionMenuSide != ActionMenuSide.START.name
    val dockAlignment = if (dockEnd) Alignment.TopEnd else Alignment.TopStart

    // 子页激活时自动展开对应父级菜单(进入子页时)
    LaunchedEffect(currentRoute) {
        mainNavItems.filter { it.isSubmenuToggle }.forEach { item ->
            val isChildActive = item.submenuItems.any { it.route == currentRoute }
            if (isChildActive) {
                openSubmenus[item.label] = true
            }
        }
    }

    // 液态玻璃效果(AndroidLiquidGlass drawBackdrop:透镜折射 + 默认高光/投影),
    // 仅 Android 13+ 启用,低版本自动降级为普通面板
    val circleGlass = if (glassEnabled) Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { CircleShape },
        effects = {
            lens(24.dp.toPx(), 16.dp.toPx())
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default }
    ) else Modifier

    val pillGlass = if (glassEnabled) Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedCornerShape(50) },
        effects = {
            lens(24.dp.toPx(), 16.dp.toPx())
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default }
    ) else Modifier

    Box(modifier = Modifier.fillMaxSize()) {
        // 全屏遮罩:点击空白处收起导航
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = false }
            )
        }

        // 导航开关按钮:悬浮最上层、避开状态栏与刘海打孔（忽略刘海时画面仍铺满，仅控件避让）、
        // 不占页面布局（停靠侧按设置镜像）
        Box(
            modifier = Modifier
                .align(dockAlignment)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .statusBarsPadding()
                .padding(top = 8.dp, start = if (dockEnd) 0.dp else 12.dp, end = if (dockEnd) 12.dp else 0.dp)
                .size(40.dp)
                .then(circleGlass)
                .then(
                    if (glassEnabled) Modifier
                    else Modifier.background(VerseColors.PcBgCard.copy(alpha = 0.85f), CircleShape)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (expanded) Icons.Filled.Close else Icons.Filled.Menu,
                "导航菜单",
                Modifier.size(20.dp),
                VerseColors.PcTextPrimary
            )
        }

        // 展开容器:竖着的整个胶囊容器(两端半圆),图标按钮放里面,从按钮处向下划出
        // 胶囊宽 56dp、按钮宽 40dp,胶囊侧偏移取 4dp 使两者水平中心对齐（停靠侧按设置镜像）
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier
                .align(dockAlignment)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .statusBarsPadding()
                .padding(top = 56.dp, start = if (dockEnd) 0.dp else 4.dp, end = if (dockEnd) 4.dp else 0.dp),
            enter = expandVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(180)),
            exit = shrinkVertically(
                animationSpec = tween(160, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(120))
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .heightIn(max = 500.dp)
                    .then(pillGlass)
                    .then(
                        if (glassEnabled) Modifier
                        else Modifier.background(VerseColors.PcBgCard.copy(alpha = 0.85f), RoundedCornerShape(50))
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    mainNavItems.forEach { item ->
                        if (item.isSubmenuToggle) {
                            val isOpen = openSubmenus[item.label] ?: false
                            val isChildActive = item.submenuItems.any { it.route == currentRoute }
                            NavCircleButton(
                                icon = item.icon,
                                label = item.label,
                                isActive = isChildActive,
                                onClick = {
                                    openSubmenus[item.label] = !(openSubmenus[item.label] ?: false)
                                }
                            )
                            AnimatedVisibility(
                                visible = isOpen,
                                enter = expandVertically(tween(200)) + fadeIn(tween(180)),
                                exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    item.submenuItems.forEach { subScreen ->
                                        NavCircleMini(
                                            icon = subScreenIcon(subScreen),
                                            label = subScreen.title,
                                            isActive = currentRoute == subScreen.route,
                                            onClick = {
                                                expanded = false
                                                navigateToTab(navController, subScreen.route)
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            NavCircleButton(
                                icon = item.icon,
                                label = item.label,
                                isActive = currentRoute == item.screen?.route,
                                onClick = {
                                    expanded = false
                                    item.screen?.let { screen ->
                                        navigateToTab(navController, screen.route)
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        color = VerseColors.PcBorder,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    NavCircleButton(
                        icon = footerNavItem.icon,
                        label = footerNavItem.label,
                        isActive = currentRoute == footerNavItem.screen?.route,
                        onClick = {
                            expanded = false
                            footerNavItem.screen?.let { screen ->
                                navigateToTab(navController, screen.route)
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 胶囊容器内的主图标按钮(圆形,只放图标):内部内容为普通圆角面板,玻璃由胶囊容器统一提供 */
@Composable
private fun NavCircleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isActive) {
                    VerseColors.PcAccent.copy(alpha = 0.85f)
                } else {
                    VerseColors.PcBgTertiary.copy(alpha = 0.55f)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            label,
            Modifier.size(20.dp),
            if (isActive) VerseColors.PcAccentText else VerseColors.PcTextSecondary
        )
    }
}

/** 胶囊容器内的子图标按钮(资源子页面,更小一圈):普通圆角面板,样式与主按钮一致 */
@Composable
private fun NavCircleMini(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (isActive) {
                    VerseColors.PcAccent.copy(alpha = 0.8f)
                } else {
                    VerseColors.PcBgSecondary.copy(alpha = 0.5f)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            label,
            Modifier.size(16.dp),
            if (isActive) VerseColors.PcAccentText else VerseColors.PcTextMuted
        )
    }
}

/** 资源子页面对应的图标 */
private fun subScreenIcon(screen: Screen): ImageVector = when (screen) {
    Screen.Mods -> Icons.Outlined.Extension
    Screen.Modpacks -> Icons.Outlined.Inventory2
    Screen.ResourcePacks -> Icons.Outlined.Palette
    Screen.Shaders -> Icons.Outlined.Brush
    else -> Icons.Outlined.Widgets
}

