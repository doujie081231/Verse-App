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
import androidx.compose.ui.text.font.FontWeight
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
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.components.ModpackImportFlow
import com.verse.explorer.app.ui.theme.VerseColors
import android.net.Uri
import android.os.Build

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    object Home : Screen("home", "??", Icons.Outlined.Home, Icons.Filled.Home)
    object Versions : Screen("versions", "??", Icons.Outlined.Download, Icons.Filled.Download)
    object Mods : Screen("mods", "??", Icons.Outlined.Extension, Icons.Filled.Extension)
    object Modpacks : Screen("modpacks", "???", Icons.Outlined.Inventory2, Icons.Filled.Inventory2)
    object ResourcePacks : Screen("resourcepacks", "???", Icons.Outlined.Palette, Icons.Filled.Palette)
    object Shaders : Screen("shaders", "??", Icons.Outlined.Flare, Icons.Filled.Flare)
    object Lan : Screen("lan", "??", Icons.Outlined.Wifi, Icons.Filled.Wifi)
    object Transfer : Screen("transfer", "??", Icons.Outlined.SwapHoriz, Icons.Filled.SwapHoriz)
    object Accounts : Screen("accounts", "??", Icons.Outlined.Person, Icons.Filled.Person)
    object Settings : Screen("settings", "??", Icons.Outlined.Settings, Icons.Filled.Settings)
    object Java : Screen("java", "Java", Icons.Outlined.Code, Icons.Filled.Code)
    object Console : Screen("console", "????", Icons.Outlined.Feedback, Icons.Filled.Feedback)
}

private data class NavItem(
    val screen: Screen? = null,
    val icon: ImageVector,
    val label: String,
    val isSubmenuToggle: Boolean = false,
    val submenuItems: List<Screen> = emptyList()
)

private val mainNavItems = listOf(
    NavItem(screen = Screen.Home, icon = Icons.Outlined.Home, label = "??"),
    NavItem(screen = Screen.Versions, icon = Icons.Outlined.Download, label = "??"),
    NavItem(
        icon = Icons.Outlined.Widgets,
        label = "??",
        isSubmenuToggle = true,
        submenuItems = listOf(Screen.Mods, Screen.Modpacks, Screen.ResourcePacks, Screen.Shaders)
    ),
    NavItem(screen = Screen.Lan, icon = Icons.Outlined.Wifi, label = "??"),
    NavItem(screen = Screen.Transfer, icon = Icons.Outlined.SwapHoriz, label = "??"),
    NavItem(screen = Screen.Accounts, icon = Icons.Outlined.Person, label = "??"),
    NavItem(screen = Screen.Settings, icon = Icons.Outlined.Settings, label = "??")
)

private val footerNavItem = NavItem(
    screen = Screen.Console,
    icon = Icons.Outlined.Feedback,
    label = "????"
)

private val allSidebarRoutes = mainNavItems
    .flatMap { if (it.screen != null) listOf(it.screen.route) else it.submenuItems.map { s -> s.route } }
    .plus(footerNavItem.screen!!.route)
    .toSet()

private val submenuRoutes = mainNavItems
    .filter { it.isSubmenuToggle }
    .flatMap { it.submenuItems.map { s -> s.route } }
    .toSet()

// ==================== ??????(?? Material Motion System)====================
// ?????:FadeThrough ??(?? + ????,???????)
private val TabEnterTransition: EnterTransition =
    fadeIn(animationSpec = tween(260, easing = LinearOutSlowInEasing)) +
        scaleIn(initialScale = 0.97f, animationSpec = tween(260, easing = LinearOutSlowInEasing))
private val TabExitTransition: ExitTransition =
    fadeOut(animationSpec = tween(200, easing = FastOutLinearInEasing)) +
        scaleOut(targetScale = 0.97f, animationSpec = tween(200, easing = FastOutLinearInEasing))

// ??/???:Shared X-Axis(???? 1/3 ? + ????,????),???????????
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
                CircularProgressIndicator(color = VerseColors.PcAccent)
                Spacer(modifier = Modifier.height(16.dp))
                Text("?????Java????...", color = VerseColors.PcTextSecondary, fontSize = 14.sp)
                Text("????JRE,???", color = VerseColors.PcTextMuted, fontSize = 12.sp)
            }
        }
        return
    }

    val showSidebar = currentRoute in allSidebarRoutes

    // ???????????? scrim ???,??????(??????????)
    val customBgActive = VersePersonalization.backgroundStyle != "none"

    // ?????????(Kyant0/AndroidLiquidGlass backdrop-android 2.0.0):
    // RuntimeShader ????? Android 13+,??????????;
    // ????? ? ???????????????????,??????????
    val navGlassBackdrop = rememberLayerBackdrop()
    val glassSupported = Build.VERSION.SDK_INT >= 33 && VersePersonalization.sidebarGlass

    Box(modifier = Modifier.fillMaxSize()) {
        // ????:????? + ????????????????????
        Box(modifier = Modifier.fillMaxSize().then(
            if (glassSupported) Modifier.layerBackdrop(navGlassBackdrop) else Modifier
        )) {
        // ??:???????(??/??)
        AppBackground(Modifier.fillMaxSize())
        // ????????(?????????,?????????)
        Box(modifier = Modifier.fillMaxSize().background(
            if (customBgActive) VerseColors.PcGlassBg.copy(alpha = 0.60f)
            else VerseColors.PcGlassBg
        )) {
        NavHost(
                    navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize(),
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
            composable(Screen.Transfer.route) { TransferScreen() }
            composable(
                Screen.Java.route,
                enterTransition = { DetailEnterTransition },
                exitTransition = { DetailExitTransition },
                popEnterTransition = { DetailPopEnterTransition },
                popExitTransition = { DetailPopExitTransition }
            ) { JavaScreen(navController) }
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
                "installed_mods/{versionId}",
                enterTransition = { DetailEnterTransition },
                exitTransition = { DetailExitTransition },
                popEnterTransition = { DetailPopEnterTransition },
                popExitTransition = { DetailPopExitTransition }
            ) { backStackEntry ->
                val versionId = backStackEntry.arguments?.getString("versionId") ?: ""
                InstalledModsScreen(versionId = versionId, navController = navController)
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

        // ??????(?????,??????;??????????)
        if (showSidebar) {
            FloatingNavOverlay(
                navController = navController,
                currentRoute = currentRoute,
                backdrop = navGlassBackdrop,
                glassEnabled = glassSupported
            )
        }
        // ?????????(??? ACTION_SEND ? ?? ? FCL ????),
        // ??????:??????????,??????????
        ModpackImportFlow(
            importSource = shareImportUri,
            onHandled = onShareImportHandled
        )
    }
}

/**
 * ??????:???????????????,
 * ?? `popUpTo(startDestination)` + `restoreState` ????????
 * ???????????(???????/??????)????
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

    // ???????????????(????????)
    LaunchedEffect(currentRoute) {
        mainNavItems.filter { it.isSubmenuToggle }.forEach { item ->
            val isChildActive = item.submenuItems.any { it.route == currentRoute }
            if (isChildActive) {
                openSubmenus[item.label] = true
            }
        }
    }

    // ??????(AndroidLiquidGlass drawBackdrop:???? + ????/??),
    // Android 13+ ??,???????????
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
        // ?????:????????????
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

        // ??????(?? versepc2 .sidebar-toggle-fab:??????????????????)
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
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
                "????",
                Modifier.size(20.dp),
                VerseColors.PcTextPrimary
            )
        }

        // ????:?????????(????),???????,????????
        // ??? 56dp???? 40dp,?????? 4dp ?????????(???? x=12+20=32=4+28)
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 4.dp, top = 56.dp),
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

/** ???????????(??,????):???? LiquidBottomTab,???????????,??????????? */
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

/** ???????????(?????,????):????,?????? */
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

/** ?????????? */
private fun subScreenIcon(screen: Screen): ImageVector = when (screen) {
    Screen.Mods -> Icons.Outlined.Extension
    Screen.Modpacks -> Icons.Outlined.Inventory2
    Screen.ResourcePacks -> Icons.Outlined.Palette
    Screen.Shaders -> Icons.Outlined.Brush
    else -> Icons.Outlined.Widgets
}

