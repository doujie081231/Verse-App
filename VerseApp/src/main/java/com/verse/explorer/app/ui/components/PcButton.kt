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
/**
 */
enum class PcButtonVariant {
    PRIMARY,      // 主按钮：PcAccent 背景 + 白字
    SECONDARY,    // 次按钮：PcBgTertiary 背景 + 边框
    OUTLINE,      // 描边按钮：透明背景 + PcAccent 边框
    GHOST,        // 幽灵按钮：透明背景
    LAUNCH,       // 启动按钮：绿色渐变
    DANGER        // 危险按钮：红色
}

/**
 */
@Composable
fun PcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PcButtonVariant = PcButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val (backgroundColor, textColor, borderStroke) = when (variant) {
        PcButtonVariant.PRIMARY -> Triple(VerseColors.PcAccent, VerseColors.PcAccentText, null as BorderStroke?)
        PcButtonVariant.SECONDARY -> Triple(VerseColors.PcBgTertiary, VerseColors.PcTextSecondary, BorderStroke(1.dp, VerseColors.PcBorder))
        PcButtonVariant.OUTLINE -> Triple(Color.Transparent, VerseColors.PcAccent, BorderStroke(1.dp, VerseColors.PcAccent))
        PcButtonVariant.GHOST -> Triple(Color.Transparent, VerseColors.PcTextSecondary, null)
        PcButtonVariant.LAUNCH -> Triple(Color.Transparent, Color.White, null)  // 渐变背景单独处理
        PcButtonVariant.DANGER -> Triple(VerseColors.PcRed, Color.White, null)
    }

    val isLaunch = variant == PcButtonVariant.LAUNCH
    val fontSize = if (isLaunch) 15.sp else 13.sp
    val fontWeight = if (isLaunch) FontWeight.SemiBold else FontWeight.Medium
    val horizontalPadding = if (isLaunch) 28.dp else 16.dp
    val verticalPadding = if (isLaunch) 10.dp else 8.dp

    val bgModifier = if (isLaunch) {
        Modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(VerseColors.PcLaunchGradientStart, VerseColors.PcLaunchGradientEnd)
            )
        )
    } else {
        Modifier.background(backgroundColor)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(bgModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val displayIcon = icon ?: if (isLaunch) Icons.Default.PlayArrow else null
        if (displayIcon != null) {
            Icon(
                imageVector = displayIcon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = textColor
        )
    }
}
