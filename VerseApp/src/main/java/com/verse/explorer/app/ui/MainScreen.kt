package com.verse.explorer.app.ui

import androidx.compose.animation.*
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
    object Console : Screen("console", "日志", Icons.Outlined.Terminal, Icons.Filled.Terminal)
    object SteveChat : Screen("stevechat", "Steve", Icons.Outlined.Chat, Icons.Filled.Chat)
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
    NavItem(screen = Screen.SteveChat, icon = Icons.Outlined.Chat, label = "Steve"),
    NavItem(screen = Screen.Versions, icon = Icons.Outlined.Download, label = "下载"),
    NavItem(
        icon = Icons.Outlined.Widgets,
        label = "资源",
        isSubmenuToggle = true,
        submenuItems = listOf(Screen.Mods, Screen.Modpacks, Screen.ResourcePacks, Screen.Shaders)
    ),
    NavItem(screen = Screen.Lan, icon = Icons.Outlined.Wifi, label = "联机"),
    NavItem(screen = Screen.Accounts, icon = Icons.Outlined.Person, label = "账户"),
    NavItem(
        icon = Icons.Outlined.Settings,
        label = "设置",
        isSubmenuToggle = true,
        submenuItems = listOf(Screen.Settings)
    ),
    NavItem(screen = Screen.Java, icon = Icons.Outlined.Code, label = "Java")
)

private val footerNavItem = NavItem(
    screen = Screen.Console,
    icon = Icons.Outlined.Terminal,
    label = "日志"
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
    onLaunchGame: (String?) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    if (!jreReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VerseColors.McDarkBg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = VerseColors.McGreen)
                Spacer(modifier = Modifier.height(16.dp))
                Text("正在初始化Java运行环境...", color = VerseColors.McTextSecondary, fontSize = 14.sp)
                Text("解压内置JRE，请稍候", color = VerseColors.McTextMuted, fontSize = 12.sp)
            }
        }
        return
    }

    val showSidebar = currentRoute in allSidebarRoutes

    Row(modifier = Modifier.fillMaxSize().background(VerseColors.McBg)) {
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
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(150)) }
        ) {
            composable(Screen.Home.route) { HomeScreen(navController, onLaunchGame) }
            composable(Screen.SteveChat.route) { SteveChatScreen(navController) }
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
            composable("version_settings/{versionId}") { backStackEntry ->
                val versionId = backStackEntry.arguments?.getString("versionId") ?: ""
                VersionSettingsScreen(versionId = versionId, navController = navController)
            }
            composable("version_detail/{versionId}?type={versionType}&url={versionUrl}") { backStackEntry ->
                val versionId = backStackEntry.arguments?.getString("versionId") ?: ""
                val versionType = backStackEntry.arguments?.getString("versionType") ?: "release"
                val versionUrl = backStackEntry.arguments?.getString("versionUrl") ?: ""
                VersionDetailScreen(versionId, versionType, versionUrl, navController)
            }
            composable("mod_detail/{projectId}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                ModDetailScreen(navController, projectId, "mod")
            }
            composable("modpack_detail/{projectId}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                ModDetailScreen(navController, projectId, "modpack")
            }
            composable("resourcepack_detail/{projectId}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                ModDetailScreen(navController, projectId, "resourcepack")
            }
            composable("shader_detail/{projectId}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                ModDetailScreen(navController, projectId, "shader")
            }
        }
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
        color = VerseColors.McSidebarBg,
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
                                            navController.navigate(subScreen.route) {
                                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
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
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = VerseColors.McCardBorder.copy(alpha = 0.5f),
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
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
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
    val bgColor = if (isActive) VerseColors.McGreen.copy(alpha = 0.12f) else Color.Transparent
    val iconColor = if (isActive) VerseColors.McGreen else VerseColors.McTextMuted
    val textColor = if (isActive) VerseColors.McGreen else VerseColors.McTextMuted

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
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
                    .height(24.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(VerseColors.McGreen)
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
    val bgColor = if (isActive) VerseColors.McGreen.copy(alpha = 0.08f) else Color.Transparent
    val textColor = if (isActive) VerseColors.McGreen else VerseColors.McTextMuted

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
