package com.verse.explorer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verse.explorer.app.R
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.launch.LaunchPhase
import com.verse.explorer.app.launch.LaunchState
import com.verse.explorer.app.ui.backgroundGlass
import com.verse.explorer.app.ui.influencedByBackgroundColor
import com.verse.explorer.app.ui.theme.ActionMenuSide
import com.verse.explorer.app.ui.theme.VerseColors

/** 与 TopNavBar 实测宽度对齐的占位，导航条改宽高时同步改这里 */
private val NAV_RESERVED_WIDTH = 236.dp

/**
 * 顶部导航避让：导航条常驻右上角，页面本身铺满，只让标题行右对齐的操作按钮（刷新等）
 * 躲开导航条占用的宽度；不作用于页面内容区，也不会把整页往下推。
 */
@Composable
fun Modifier.navButtonAvoid(): Modifier {
    // 顶部横向导航常驻右上角，页面本身铺满；只让标题行右对齐的操作按钮（刷新等）
    // 躲开导航条占用的宽度，避免整页被往下推。
    return padding(end = NAV_RESERVED_WIDTH)
}

/**
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth().navButtonAvoid()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = if (compact) 17.sp else 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerseColors.PcTextPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
        Spacer(Modifier.height(if (compact) 8.dp else 24.dp))
    }
}

/**
 * 自定义背景有效时容器色按背景不透明度设置降低 alpha 透出背景；
 * 前景毛玻璃模式下在内容下方对齐绘制背景的模糊切片。
 */
@Composable
fun PcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = influencedByBackgroundColor(VerseColors.PcBgCard)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Column(
            modifier = Modifier
                .backgroundGlass(VersePersonalization.backgroundBlur, containerColor)
                .padding(20.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextSecondary
                )
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

/**
 */
@Composable
fun PcTabGroup(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgTertiary
    ) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(2.dp)) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) VerseColors.PcAccent else Color.Transparent,
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelected(index) }
                ) {
                    Text(
                        text = label,
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
                        modifier = Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 4.dp else 6.dp)
                    )
                }
            }
        }
    }
}
