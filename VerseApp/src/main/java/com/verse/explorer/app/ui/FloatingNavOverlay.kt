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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.theme.ActionMenuSide
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
internal fun FloatingNavOverlay(
    navController: NavHostController,
    currentRoute: String?,
    backdrop: LayerBackdrop,
    glassEnabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val openSubmenus = remember { mutableStateMapOf<String, Boolean>() }

    // 操作菜单停靠侧：START=阅读方向起始侧（左），END=末尾侧（右），默认右侧；
    // 布局整体镜像（按钮与胶囊面板的锚点/边距对调），毛玻璃层为 backdrop 采样、与位置无关不受影响
    val dockEnd = VersePersonalization.actionMenuSide != ActionMenuSide.START.name
    val dockAlignment = if (dockEnd) Alignment.TopEnd else Alignment.TopStart
    // 按钮是否在图标下显示功能名（默认开）：开启时胶囊容器加宽以容纳文字
    val showLabels = VersePersonalization.sidebarLabels

    // 忽略刘海时页面内容已铺满整屏，悬浮导航不再额外避让刘海，
    // 否则按钮停在更靠内的位置，会压住页面标题与返回键；
    // 「启动器全屏」只控制系统栏沉浸，不影响这里的刘海避让
    val avoidCutout = !DisplayNotch.ignoreNotch
    val cutoutPadding = if (avoidCutout) {
        Modifier.windowInsetsPadding(WindowInsets.displayCutout)
    } else {
        Modifier
    }

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

        // 导航开关按钮:悬浮最上层、避开状态栏与刘海打孔（忽略刘海/全屏时随内容一起贴边）、
        // 不占页面布局（停靠侧按设置镜像）
        Box(
            modifier = Modifier
                .align(dockAlignment)
                .then(cutoutPadding)
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
                .then(cutoutPadding)
                .statusBarsPadding()
                .padding(top = 56.dp, start = if (dockEnd) 0.dp else 4.dp, end = if (dockEnd) 4.dp else 0.dp),
            // 只做高度展开/收起：叠加 fadeIn/fadeOut 会为每次动画额外分配透明层，
            // 实测侧边栏展开时帧时间从 17ms 升到 21~29ms，去掉淡入淡出后更跟手
            enter = expandVertically(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top
            ),
            exit = shrinkVertically(
                animationSpec = tween(140, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Top
            )
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .heightIn(max = 500.dp)
                    // 展开/滚动过程中内容必须裁在胶囊形状内，否则按钮会从两端溢出来
                    .clip(RoundedCornerShape(50))
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
                    // 带功能字时按钮本身变高，条目间距收紧，避免相邻按钮视觉上隔得太远
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    mainNavItems.forEach { item ->
                        if (item.isSubmenuToggle) {
                            val isOpen = openSubmenus[item.label] ?: false
                            val isChildActive = item.submenuItems.any { it.route == currentRoute }
                            NavCircleButton(
                                icon = item.icon,
                                label = item.label,
                                isActive = isChildActive,
                                showLabel = showLabels,
                                onClick = {
                                    openSubmenus[item.label] = !(openSubmenus[item.label] ?: false)
                                }
                            )
                            AnimatedVisibility(
                                visible = isOpen,
                                // 子菜单与胶囊保持一致：只做高度展开/收起
                                enter = expandVertically(tween(160)),
                                exit = shrinkVertically(tween(130))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    item.submenuItems.forEach { subScreen ->
                                        NavCircleMini(
                                            icon = subScreenIcon(subScreen),
                                            label = subScreen.title,
                                            isActive = currentRoute == subScreen.route,
                                            showLabel = showLabels,
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
                                showLabel = showLabels,
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
                        showLabel = showLabels,
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

/**
 * 胶囊容器内的主图标按钮(圆形,只放图标):内部内容为普通圆角面板,玻璃由胶囊容器统一提供。
 * showLabel 时在按钮下方显示功能名（与 VersePC2 一致，可在个性化设置关闭）。
 */
@Composable
internal fun NavCircleButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isActive) VerseColors.PcAccentText else VerseColors.PcTextSecondary
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
            Icon(icon, label, Modifier.size(20.dp), tint)
        }
        if (showLabel) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = tint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
            )
        }
    }
}

/** 胶囊容器内的子图标按钮(资源子页面,更小一圈):普通圆角面板,样式与主按钮一致 */
@Composable
internal fun NavCircleMini(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isActive) VerseColors.PcAccentText else VerseColors.PcTextMuted
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
            Icon(icon, label, Modifier.size(16.dp), tint)
        }
        if (showLabel) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = tint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
            )
        }
    }
}

/** 资源子页面对应的图标 */
internal fun subScreenIcon(screen: Screen): ImageVector = when (screen) {
    Screen.Mods -> Icons.Outlined.Extension
    Screen.Modpacks -> Icons.Outlined.Inventory2
    Screen.ResourcePacks -> Icons.Outlined.Palette
    Screen.Shaders -> Icons.Outlined.Brush
    else -> Icons.Outlined.Widgets
}
