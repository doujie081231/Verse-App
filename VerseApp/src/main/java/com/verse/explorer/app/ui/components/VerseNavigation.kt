package com.verse.explorer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
fun VerseBottomNavigation(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = VerseColors.BgCard,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                BottomNavItemView(
                    item = item,
                    isSelected = index == selectedIndex,
                    onClick = { onItemSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItemView(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) VerseColors.PrimaryPurple else VerseColors.TextMuted,
        animationSpec = tween(200),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) VerseColors.PrimaryPurple else VerseColors.TextMuted,
        animationSpec = tween(200),
        label = "textColor"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                modifier = Modifier.size(24.dp),
                tint = iconColor
            )
            if (item.badgeCount > 0) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd),
                    containerColor = VerseColors.RedDanger,
                    contentColor = Color.White
                ) {
                    Text(
                        text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

@Composable
fun VerseBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VerseColors.PrimaryPurple
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun VerseStatusBadge(
    status: StatusType,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, text) = when (status) {
        StatusType.SUCCESS -> Triple(
            VerseColors.GreenSuccessBg,
            VerseColors.GreenSuccessText,
            "成功"
        )
        StatusType.ERROR -> Triple(
            VerseColors.RedDangerBg,
            VerseColors.RedDanger,
            "错误"
        )
        StatusType.WARNING -> Triple(
            VerseColors.OrangeWarningBg,
            VerseColors.OrangeWarning,
            "警告"
        )
        StatusType.INFO -> Triple(
            VerseColors.PrimaryPurpleSoft,
            VerseColors.PrimaryPurple,
            "信息"
        )
        StatusType.PENDING -> Triple(
            VerseColors.BgTertiary,
            VerseColors.TextMuted,
            "待处理"
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

enum class StatusType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO,
    PENDING
}

@Composable
fun VerseProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Int = 6,
    backgroundColor: Color = VerseColors.BgTertiary,
    progressColor: Color = VerseColors.PrimaryPurple,
    showPercentage: Boolean = false
) {
    Column(modifier = modifier) {
        if (showPercentage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "进度",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.TextMuted
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(height / 2))
                .background(backgroundColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(height / 2))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(progressColor, progressColor.copy(alpha = 0.8f))
                        )
                    )
            )
        }
    }
}

@Composable
fun VerseCircularProgress(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    size: Int = 48,
    strokeWidth: Int = 4,
    color: Color = VerseColors.PrimaryPurple
) {
    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = strokeWidth.dp,
                trackColor = VerseColors.BgTertiary
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = VerseColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = strokeWidth.dp,
                trackColor = VerseColors.BgTertiary
            )
        }
    }
}

@Composable
fun VerseEmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(VerseColors.PrimaryPurpleSoft.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = VerseColors.PrimaryPurple
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = VerseColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )

        if (description != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = VerseColors.TextSecondary
            )
        }

        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
fun VerseLoadingState(
    message: String = "加载中...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        VerseCircularProgress(size = 48, strokeWidth = 4)

        Spacer(Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = VerseColors.TextSecondary
        )
    }
}

@Composable
fun VerseErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(VerseColors.RedDangerBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Error,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = VerseColors.RedDanger
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "出错了",
            style = MaterialTheme.typography.bodyLarge,
            color = VerseColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = VerseColors.TextSecondary
        )

        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            VerseButton(
                onClick = onRetry,
                text = "重试",
                variant = VerseButtonVariant.PRIMARY,
                size = VerseButtonSize.SMALL
            )
        }
    }
}

@Composable
fun VerseDivider(
    modifier: Modifier = Modifier,
    color: Color = VerseColors.BorderColor,
    thickness: Int = 1
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness.dp)
            .background(color)
    )
}

@Composable
fun VerseSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = VerseColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        action?.invoke()
    }
}
