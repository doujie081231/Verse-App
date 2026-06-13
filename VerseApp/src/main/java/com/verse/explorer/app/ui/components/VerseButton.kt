package com.verse.explorer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors

enum class VerseButtonVariant {
    PRIMARY,
    SECONDARY,
    GHOST,
    OUTLINED,
    DANGER,
    SUCCESS,
    GRADIENT,
    TRANSPARENT
}

enum class VerseButtonSize {
    SMALL,
    MEDIUM,
    LARGE
}

@Composable
fun VerseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    iconPosition: IconPosition = IconPosition.LEFT,
    variant: VerseButtonVariant = VerseButtonVariant.PRIMARY,
    size: VerseButtonSize = VerseButtonSize.MEDIUM,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()

    val topHighlight = Color(0xFFFFFFFF).copy(alpha = 0.18f)
    val bottomShadow = Color(0xFF000000).copy(alpha = 0.35f)

    val (bgColor, fgColor, borderColorVal) = when {
        !enabled -> Triple(
            VerseColors.McButtonDisabled,
            VerseColors.McTextMuted,
            VerseColors.McCardBorder
        )
        else -> when (variant) {
            VerseButtonVariant.PRIMARY -> Triple(VerseColors.McGreen, Color.White, VerseColors.McGreenDark)
            VerseButtonVariant.DANGER -> Triple(VerseColors.McRedstone, Color.White, Color(0xFF770000))
            VerseButtonVariant.SUCCESS -> Triple(VerseColors.McGreen, Color.White, VerseColors.McGreenDark)
            VerseButtonVariant.GRADIENT -> Triple(VerseColors.McGreen, Color.White, VerseColors.McGreenDark)
            VerseButtonVariant.SECONDARY -> Triple(VerseColors.McCardBgLight, VerseColors.McTextPrimary, VerseColors.McCardBorder)
            VerseButtonVariant.OUTLINED -> Triple(Color.Transparent, VerseColors.McGreen, VerseColors.McGreen)
            VerseButtonVariant.GHOST -> Triple(Color.Transparent, VerseColors.McGreen, Color.Transparent)
            VerseButtonVariant.TRANSPARENT -> Triple(Color.Transparent, VerseColors.McGreen, Color.Transparent)
        }
    }

    val pressedBg = when (variant) {
        VerseButtonVariant.PRIMARY, VerseButtonVariant.SUCCESS, VerseButtonVariant.GRADIENT -> VerseColors.McGreenDark
        VerseButtonVariant.DANGER -> Color(0xFF770000)
        VerseButtonVariant.SECONDARY -> VerseColors.McCardBg
        else -> bgColor
    }

    val animatedBg by animateColorAsState(
        targetValue = if (isPressed && enabled) pressedBg else bgColor,
        animationSpec = tween(80), label = "bg"
    )

    val offsetY by animateDpAsState(
        targetValue = if (isPressed && enabled) 1.dp else 0.dp,
        animationSpec = tween(60), label = "offset"
    )

    val borderWidth = when (variant) {
        VerseButtonVariant.OUTLINED -> 1.5.dp
        VerseButtonVariant.SECONDARY -> 1.dp
        VerseButtonVariant.PRIMARY, VerseButtonVariant.DANGER, VerseButtonVariant.SUCCESS, VerseButtonVariant.GRADIENT -> 0.dp
        else -> 0.dp
    }

    val cornerRadius = when (size) {
        VerseButtonSize.SMALL -> 3.dp
        VerseButtonSize.MEDIUM -> 4.dp
        VerseButtonSize.LARGE -> 5.dp
    }

    val padding = when (size) {
        VerseButtonSize.SMALL -> PaddingValues(horizontal = 10.dp, vertical = 5.dp)
        VerseButtonSize.MEDIUM -> PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        VerseButtonSize.LARGE -> PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    }

    val textStyle = when (size) {
        VerseButtonSize.SMALL -> MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        VerseButtonSize.MEDIUM -> MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        VerseButtonSize.LARGE -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }

    val iconSize = when (size) {
        VerseButtonSize.SMALL -> 14.dp
        VerseButtonSize.MEDIUM -> 18.dp
        VerseButtonSize.LARGE -> 22.dp
    }

    val iconSpacing = when (size) {
        VerseButtonSize.SMALL -> 4.dp
        VerseButtonSize.MEDIUM -> 6.dp
        VerseButtonSize.LARGE -> 8.dp
    }

    val isSolidButton = variant in listOf(VerseButtonVariant.PRIMARY, VerseButtonVariant.DANGER, VerseButtonVariant.SUCCESS, VerseButtonVariant.GRADIENT)

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .offset(y = offsetY)
            .clip(RoundedCornerShape(cornerRadius))
            .background(animatedBg, RoundedCornerShape(cornerRadius))
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColorVal, RoundedCornerShape(cornerRadius))
                else Modifier
            )
            .then(
                if (isSolidButton && enabled) {
                    Modifier.background(
                        Color(0xFFFFFFFF).copy(alpha = if (isPressed) 0f else 0.08f),
                        RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick
            )
            .padding(padding)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = fgColor,
                    strokeWidth = 2.dp
                )
            } else {
                if (icon != null && iconPosition == IconPosition.LEFT) {
                    Icon(icon, null, modifier = Modifier.size(iconSize), tint = fgColor)
                    if (text != null) Spacer(Modifier.width(iconSpacing))
                }
                if (text != null) {
                    Text(text, style = textStyle, color = fgColor)
                }
                if (icon != null && iconPosition == IconPosition.RIGHT) {
                    Spacer(Modifier.width(iconSpacing))
                    Icon(icon, null, modifier = Modifier.size(iconSize), tint = fgColor)
                }
            }
        }
    }
}

enum class IconPosition {
    LEFT, RIGHT
}

@Composable
fun VerseTextButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    VerseButton(
        onClick = onClick,
        modifier = modifier,
        text = text,
        icon = icon,
        variant = VerseButtonVariant.TRANSPARENT,
        size = VerseButtonSize.MEDIUM,
        enabled = enabled
    )
}

@Composable
fun VerseIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: VerseButtonSize = VerseButtonSize.MEDIUM,
    enabled: Boolean = true
) {
    val buttonSize = when (size) {
        VerseButtonSize.SMALL -> 32.dp
        VerseButtonSize.MEDIUM -> 40.dp
        VerseButtonSize.LARGE -> 48.dp
    }

    val iconSize = when (size) {
        VerseButtonSize.SMALL -> 18.dp
        VerseButtonSize.MEDIUM -> 22.dp
        VerseButtonSize.LARGE -> 26.dp
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor by animateColorAsState(
        targetValue = if (isPressed && enabled) VerseColors.McGreen.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(100), label = "bg"
    )

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (enabled) VerseColors.McGreen else VerseColors.McTextMuted
        )
    }
}
