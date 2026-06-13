package com.verse.explorer.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.ui.theme.VerseColors

enum class VerseCardVariant {
    FLAT,
    ELEVATED,
    GLASS,
    OUTLINED,
    GRADIENT
}

@Composable
fun VerseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: VerseCardVariant = VerseCardVariant.ELEVATED,
    enabled: Boolean = true,
    interactive: Boolean = false,
    selected: Boolean = false,
    header: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val elevation by animateDpAsState(
        targetValue = when {
            variant == VerseCardVariant.FLAT -> 0.dp
            variant == VerseCardVariant.OUTLINED -> 0.dp
            isPressed && interactive -> 2.dp
            interactive -> 4.dp
            else -> when (variant) {
                VerseCardVariant.ELEVATED -> 3.dp
                else -> 0.dp
            }
        },
        animationSpec = tween(200),
        label = "elevation"
    )

    val backgroundColor = when (variant) {
        VerseCardVariant.GLASS -> VerseColors.McCardBg
        VerseCardVariant.GRADIENT -> Color.Transparent
        else -> VerseColors.McCardBg
    }

    val borderStroke = when {
        selected -> BorderStroke(1.5.dp, VerseColors.McGreen)
        variant == VerseCardVariant.OUTLINED -> BorderStroke(1.dp, VerseColors.McCardBorder)
        else -> null
    }

    val contentModifier = if (onClick != null) {
        modifier
            .shadow(elevation, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    } else {
        modifier.shadow(elevation, RoundedCornerShape(4.dp))
    }

    Surface(
        modifier = contentModifier,
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor,
        border = borderStroke
    ) {
        Column(
            modifier = Modifier.animateContentSize()
        ) {
            if (variant == VerseCardVariant.GRADIENT) {
                Box(
                    modifier = Modifier.background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(VerseColors.McGreen.copy(alpha = 0.05f), VerseColors.McGreenLight.copy(alpha = 0.05f))
                        )
                    )
                ) {
                    Column {
                        header?.invoke()
                        Column(
                            modifier = Modifier.padding(16.dp),
                            content = content
                        )
                    }
                }
            } else {
                header?.invoke()
                Column(
                    modifier = Modifier.padding(16.dp),
                    content = content
                )
            }
            footer?.invoke()
        }
    }
}

@Composable
fun VerseClickableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = VerseColors.McGreen,
    iconBackground: Color = VerseColors.McGreen.copy(alpha = 0.15f),
    trailing: @Composable (() -> Unit)? = null
) {
    VerseCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        interactive = true,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = VerseColors.McTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = VerseColors.McTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            trailing?.invoke() ?: Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = VerseColors.McTextDim,
                modifier = Modifier.size(20.dp)
            )
        }
    })
}

@Composable
fun VerseInfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = VerseColors.McGreen,
    subtitle: String? = null
) {
    VerseCard(modifier = modifier, content = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = VerseColors.McTextMuted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = VerseColors.McTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = VerseColors.McTextSecondary
                    )
                }
            }
        }
    })
}

@Composable
fun VerseListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    VerseCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        variant = if (onClick != null) VerseCardVariant.OUTLINED else VerseCardVariant.FLAT,
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    )
}
