package com.verse.explorer.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors
import kotlin.math.roundToInt

/**
 * 顶部横向导航条。
 *
 * 设计取舍：
 * - 只保留图标，选中项才展开文字。七个入口全带文字要占掉半屏宽，横屏手机上会把页面顶部的
 *   操作按钮挤走；选中项带文字既标明当前位置，又不常年占宽。
 * - 黑白：底色用卡片色 + 1dp 描边，选中项做「前景色填充 + 底色文字」的反相，不引入强调色。
 * - 可拖动：主页上允许整条拖到任意位置（位置按 px 持久化，拖动时夹在屏幕内），
 *   其余页面固定在右上角，避免和页面标题抢位置。
 */
@Composable
internal fun TopNavBar(
    items: List<NavItem>,
    currentRoute: String?,
    onSelect: (Screen) -> Unit,
    draggable: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var offset by remember {
        mutableStateOf(Offset(prefs.getFloat(KEY_DX, 0f), prefs.getFloat(KEY_DY, 0f)))
    }
    var barSize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    fun clamp(candidate: Offset): Offset {
        // 锚点在右上角：x 向左为负，y 向下为正
        val minX = -(screenWidthPx - barSize.width).coerceAtLeast(0f)
        val maxY = (screenHeightPx - barSize.height).coerceAtLeast(0f)
        return Offset(
            x = candidate.x.coerceIn(minX, 0f),
            y = candidate.y.coerceIn(0f, maxY)
        )
    }

    val dragModifier = if (draggable) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    prefs.edit()
                        .putFloat(KEY_DX, offset.x)
                        .putFloat(KEY_DY, offset.y)
                        .apply()
                }
            ) { change, dragAmount ->
                change.consume()
                offset = clamp(offset + dragAmount)
            }
        }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .onSizeChanged {
                barSize = it
                // 选中项展开文字后宽度会变，标题行按实测宽度让位才不会被挡住
                val measured = with(density) { it.width.toDp() } + 14.dp
                if (measured > 0.dp) TopNavMetrics.reservedWidth = measured
            }
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .then(dragModifier)
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

private const val PREFS_NAME = "verse_top_nav"
private const val KEY_DX = "offset_x"
private const val KEY_DY = "offset_y"
