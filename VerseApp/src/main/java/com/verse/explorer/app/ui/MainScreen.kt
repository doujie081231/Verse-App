package com.verse.explorer.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.verse.explorer.app.ui.theme.VerseColors

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
    object Accounts : Screen("accounts", "账户", Icons.Outlined.Person, Icons.Filled.Person)
    object Settings : Screen("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings)
    object Java : Screen("java", "Java", Icons.Outlined.Code, Icons.Filled.Code)
    object Console : Screen("console", "控制台", Icons.Outlined.Terminal, Icons.Filled.Terminal)
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
    NavItem(screen = Screen.Accounts, icon = Icons.Outlined.Person, label = "账户"),
    NavItem(screen = Screen.Settings, icon = Icons.Outlined.Settings, label = "设置"),
    NavItem(screen = Screen.Java, icon = Icons.Outlined.Code, label = "Java")
)

private val footerNavItem = NavItem(
    screen = Screen.Console,
    icon = Icons.Outlined.Terminal,
    label = "控制台"
)

private val allSidebarRoutes = mainNavItems
    .flatMap { if (it.screen != null) listOf(it.screen.route) else it.submenuItems.map { s -> s.route } }
    .plus(footerNavItem.screen!!.route)
    .toSet()

private val submenuRoutes = mainNavItems
    .filter { it.isSubmenuToggle }
    .flatMap { it.submenuItems.map { s -> s.route } }
    .toSet()

// ==================== 页面切换动画（对齐 Material Motion System）====================
// 侧栏页切换：FadeThrough 手感（淡入 + 轻微缩放，不干扰固定侧栏）
private val TabEnterTransition: EnterTransition =
    fadeIn(animationSpec = tween(260, easing = LinearOutSlowInEasing)) +
        scaleIn(initialScale = 0.97f, animationSpec = tween(260, easing = LinearOutSlowInEasing))
private val TabExitTransition: ExitTransition =
    fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) +
        scaleOut(targetScale = 0.97f, animationSpec = tween(200, easing = FastOutLinearInEasing))

// 详情/钻取页：Shared X-Axis（从右滑入 1/3 屏 + 延迟淡入，返回反向），前后页面错位滑动更流畅
private val DetailEnterTransition: EnterTransition =
    fadeIn(animationSpec = tween(260, delayMillis = 80, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(animationSpec = tween(340, easing = FastOutSlowInEasing)) { it / 3 }
private val DetailExitTransition: ExitTransition =
    fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing)) +
        slideOutHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing)) { -it / 3 }
private val DetailPopEnterTransition: EnterTransition =
    fadeIn(animationSpec = tween(260, delayMillis = 80, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(animationSpec = tween(340, easing = FastOutSlowInEasing)) { -it / 3 }
private val DetailPopExitTransition: ExitTransition =
    fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing)) +
        slideOutHorizontally(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it / 3 }

@Composable
fun MainScreen(
    jreReady: Boolean = false,
    onLaunchGame: (String?) -> Unit = {}
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
                CircularProgressIndicator(color = VerseColors.PcAccent)
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在初始化Java运行环境...", color = VerseColors.PcTextSecondary, fontSize = 14.sp)
                Text("解压内置JRE，请稍候", color = VerseColors.PcTextMuted, fontSize = 12.sp)
            }
        }
        return
    }

    val showSidebar = currentRoute in allSidebarRoutes

    // 玻璃面板承载内容（页面根背景已透明化，由此处统一提供底色）
    Box(modifier = Modifier.fillMaxSize().background(VerseColors.PcGlassBg)) {
        Row(modifier = Modifier.fillMaxSize()) {
                if (showSidebar) {
                    PcStyleSidebar(
                        navController = navController,
                        currentRoute = currentRoute
                    )
                }

                NavHost(
                    navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.weight(1f),
            enterTransition = { TabEnterTransition },
            exitTransition = { TabExitTransition },
            popEnterTransition = { TabEnterTransition },
            popExitTransition = { TabExitTransition }
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
            composable(Screen.Java.route) { JavaScreen(navController) }
            composable(Screen.Console.route) { ConsoleScreen(navController) }
            composable(
                "installed_versions",
                enterTransition = { DetailEnterTransition },
                exitTransition = { DetailExitTransition },
                popEnterTransition = { DetailPopEnterTransition },
                popExitTransition = { DetailPopExitTransition }
            ) {
                InstalledVersionsScreen(navController = navController)
            }
            composable(
                "version_settings/{versionId}",
                enterTransition = { DetailEnterTransition },
                exitTransition = { DetailExitTransition },
                popEnterTransition = { DetailPopEnterTransition },
                popExitTransition = { DetailPopExitTransition }
            ) { backStackEntry ->
                val versionId = backStackEntry.arguments?.getString("versionId") ?: ""
                VersionSettingsScreen(versionId = versionId, navController = navController)
            }
            composable(
                "version_detail/{versionId}?type={versionType}&url={versionUrl}",
                enterTransition = { DetailEnterTransition },
                exitTransition = { DetailExitTransition },
                popEnterTransition = { DetailPopEnterTransition },
                popExitTransition = { DetailPopExitTransition }
            ) { backStackEntry ->
                val versionId = backStackEntry.arguments?.getString("versionId") ?: ""
                val versionType = backStackEntry.arguments?.getString("versionType") ?: "release"
                val versionUrl = backStackEntry.arguments?.getString("versionUrl") ?: ""
                VersionDetailScreen(versionId, versionType, versionUrl, navController)
            }
            composable(
                "mod_detail/{projectId}?source={source}",
                enterTransition = { DetailEnterTransition },
                exitTransition = { DetailExitTransition },
                popEnterTransition = { DetailPopEnterTransition },
                popExitTransition = { DetailPopExitTransition }
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val source = backStackEntry.arguments?.getString("source") ?: "modrinth"
                ModDetailScreen(navController, projectId, "mod", source)
            }
            composable(
                "modpack_detail/{projectId}?source={source}",
                enterTransition = { DetailEnterTransition },
                exitTransition = { DetailExitTransition },
                popEnterTransition = { DetailPopEnterTransition },
                popExitTransition = { DetailPopExitTransition }
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val source = backStackEntry.arguments?.getString("source") ?: "modrinth"
                ModDetailScreen(navController, projectId, "modpack", source)
            }
            composable(
                "resourcepack_detail/{projectId}?source={source}",
                enterTransition = { DetailEnterTransition },
                exitTransition = { DetailExitTransition },
                popEnterTransition = { DetailPopEnterTransition },
                popExitTransition = { DetailPopExitTransition }
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val source = backStackEntry.arguments?.getString("source") ?: "modrinth"
                ModDetailScreen(navController, projectId, "resourcepack", source)
            }
            composable(
                "shader_detail/{projectId}?source={source}",
                enterTransition = { DetailEnterTransition },
                exitTransition = { DetailExitTransition },
                popEnterTransition = { DetailPopEnterTransition },
                popExitTransition = { DetailPopExitTransition }
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                val source = backStackEntry.arguments?.getString("source") ?: "modrinth"
                ModDetailScreen(navController, projectId, "shader", source)
            }
        }
        }
    }
}

/**
 * 侧栏标签切换：彻底清空现有返回栈后重建目标页，
 * 避免 `popUpTo(startDestination)` + `restoreState` 组合在多次切页后
 * 回到起始页时复现旧状态（如误显示上一页/无法回到主页）的问题。
 */
private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
private fun PcStyleSidebar(
    navController: NavHostController,
    currentRoute: String?
) {
    val openSubmenus = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(currentRoute) {
        mainNavItems.filter { it.isSubmenuToggle }.forEach { item ->
            val isChildActive = item.submenuItems.any { it.route == currentRoute }
            if (isChildActive) {
                openSubmenus[item.label] = true
            }
        }
    }

    Surface(
        color = VerseColors.PcBgSecondary,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                mainNavItems.forEach { item ->
                    if (item.isSubmenuToggle) {
                        val isOpen = openSubmenus[item.label] ?: false
                        val isChildActive = item.submenuItems.any { it.route == currentRoute }
                        val isToggleActive = isChildActive

                        SidebarNavButton(
                            icon = item.icon,
                            label = item.label,
                            isActive = isToggleActive,
                            showIndicator = isToggleActive,
                            onClick = {
                                val currentState = openSubmenus[item.label] ?: false
                                openSubmenus[item.label] = !currentState
                            }
                        )

                        AnimatedVisibility(
                            visible = isOpen,
                            enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                item.submenuItems.forEach { subScreen ->
                                    val isSubActive = currentRoute == subScreen.route
                                    SidebarSubButton(
                                        label = subScreen.title,
                                        isActive = isSubActive,
                                        onClick = {
                                            navigateToTab(navController, subScreen.route)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        val isActive = currentRoute == item.screen?.route
                        SidebarNavButton(
                            icon = item.icon,
                            label = item.label,
                            isActive = isActive,
                            showIndicator = isActive,
                            onClick = {
                                item.screen?.let { screen ->
                                    navigateToTab(navController, screen.route)
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = VerseColors.PcBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(4.dp))

            SidebarNavButton(
                icon = footerNavItem.icon,
                label = footerNavItem.label,
                isActive = currentRoute == footerNavItem.screen?.route,
                showIndicator = currentRoute == footerNavItem.screen?.route,
                onClick = {
                    footerNavItem.screen?.let { screen ->
                    navigateToTab(navController, screen.route)
                }
                }
            )
        }
    }
}

@Composable
private fun SidebarNavButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    showIndicator: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) VerseColors.PcAccent.copy(alpha = 0.1f) else Color.Transparent
    val iconColor = if (isActive) VerseColors.PcTextPrimary else VerseColors.PcTextMuted
    val textColor = if (isActive) VerseColors.PcTextPrimary else VerseColors.PcTextMuted

    Box(
        modifier = Modifier
            .width(60.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(VerseColors.PcAccent)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                icon,
                label,
                Modifier.size(22.dp),
                iconColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SidebarSubButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) VerseColors.PcAccent.copy(alpha = 0.08f) else Color.Transparent
    val textColor = if (isActive) VerseColors.PcTextPrimary else VerseColors.PcTextMuted

    Box(
        modifier = Modifier
            .width(60.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1
        )
    }
}
