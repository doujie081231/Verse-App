package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 顶部横向导航条。
 *
 * 设计取舍：
 * - 只保留图标，选中项才展开文字。七个入口全带文字要占掉半屏宽，横屏手机上会把页面顶部的
 *   操作按钮挤走；选中项带文字既标明当前位置，又不常年占宽。
 * - 黑白：底色用卡片色 + 1dp 描边，选中项做「前景色填充 + 底色文字」的反相，不引入强调色。
 * - 固定停靠右上角，不做拖动：位置可预期，页面标题行按实测宽度让位即可。
 */
@Composable
internal fun TopNavBar(
    items: List<NavItem>,
    currentRoute: String?,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var barSize by remember { mutableStateOf(IntSize.Zero) }

    Row(
        modifier = modifier
            .onSizeChanged {
                barSize = it
                // 选中项展开文字后宽度会变，标题行按实测宽度让位才不会被挡住
                val measured = with(density) { it.width.toDp() } + 14.dp
                if (measured > 0.dp) TopNavMetrics.reservedWidth = measured
            }
            .background(VerseColors.PcBgCard.copy(alpha = 0.94f), RoundedCornerShape(50))
            .border(1.dp, VerseColors.PcBorder, RoundedCornerShape(50))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { item ->
            val screen = item.screen
            val active = screen != null && screen.route == currentRoute
            NavPillItem(
                icon = item.icon,
                label = item.label,
                active = active,
                showLabel = active,
                onClick = { screen?.let(onSelect) }
            )
        }
    }
}

/** 导航条实测宽度，供页面标题行做右侧让位（选中项展开文字后会变宽） */
internal object TopNavMetrics {
    var reservedWidth by mutableStateOf(236.dp)
}

/**
 * 导航条占用的宽度（含 4dp 内边距与 10dp 右边距的粗略值）。
 *
 * 页面标题行用它做 `padding(end = ...)`，让右对齐的刷新/操作按钮躲开导航条；
 * 这里保持**常驻图标、不展开文字**，宽度才可预测，标题行不必按展开态留两套间距。
 */
internal val TopNavBarReservedWidth = 236.dp

/** 单个入口：选中时反相填充（不展开文字，保证整条宽度可预测） */
@Composable
private fun NavPillItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    val background = if (active) VerseColors.PcTextPrimary else Color.Transparent
    val foreground = if (active) VerseColors.PcBgPrimary else VerseColors.PcTextSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (showLabel) 12.dp else 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = foreground)
        if (showLabel) {
            Text(
                text = label,
                color = foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
