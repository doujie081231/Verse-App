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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
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
import com.verse.explorer.app.launch.LaunchPhase
import com.verse.explorer.app.launch.LaunchState
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * PC 端对齐的统一页面头部：左侧标题 + 右侧操作按钮组
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
 * PC 端对齐的卡片：12dp 圆角、20dp 内边距、1dp 边框
 */
@Composable
fun PcCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
 * PC 端对齐的 Tab 按钮组：PcBgTertiary 容器、2dp padding、6dp 圆角
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

/**
 * PC 端对齐的按钮变体
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
 * PC 端对齐的按钮组件
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

/**
 * PC 端对齐的统一 OutlinedTextField 颜色配置
 * 可选参数允许调用方覆盖 label / placeholder 颜色以保留各自的视觉细节
 */
@Composable
fun pcTextFieldColors(
    focusedLabelColor: Color = VerseColors.PcTextMuted,
    unfocusedLabelColor: Color = VerseColors.PcTextMuted,
    focusedPlaceholderColor: Color = VerseColors.PcTextMuted,
    unfocusedPlaceholderColor: Color = VerseColors.PcTextMuted
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VerseColors.PcAccent,
    unfocusedBorderColor = VerseColors.PcBorder,
    focusedContainerColor = VerseColors.PcBgTertiary,
    unfocusedContainerColor = VerseColors.PcBgTertiary,
    focusedTextColor = VerseColors.PcTextPrimary,
    unfocusedTextColor = VerseColors.PcTextPrimary,
    cursorColor = VerseColors.PcAccent,
    focusedLabelColor = focusedLabelColor,
    unfocusedLabelColor = unfocusedLabelColor,
    focusedPlaceholderColor = focusedPlaceholderColor,
    unfocusedPlaceholderColor = unfocusedPlaceholderColor
)

/**
 * PC 端对齐的文本输入框
 */
@Composable
fun PcTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        placeholder = if (placeholder != null) ({ Text(placeholder, fontSize = 13.sp) }) else null,
        shape = RoundedCornerShape(8.dp),
        textStyle = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = VerseColors.PcTextPrimary
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VerseColors.PcAccent,
            unfocusedBorderColor = VerseColors.PcBorder,
            focusedContainerColor = VerseColors.PcBgTertiary,
            unfocusedContainerColor = VerseColors.PcBgTertiary,
            focusedTextColor = VerseColors.PcTextPrimary,
            unfocusedTextColor = VerseColors.PcTextPrimary,
            cursorColor = VerseColors.PcAccent,
            focusedPlaceholderColor = VerseColors.PcTextMuted,
            unfocusedPlaceholderColor = VerseColors.PcTextMuted
        )
    )
}

/**
 * PC 端对齐的开关行：左侧标签 + 描述，右侧 Switch
 */
@Composable
fun PcSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = VerseColors.PcTextSecondary
            )
            if (description.isNotEmpty()) {
                Text(description, fontSize = 11.sp, color = VerseColors.PcTextMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle?.invoke(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = VerseColors.PcAccentText,
                checkedTrackColor = VerseColors.PcAccent,
                uncheckedThumbColor = VerseColors.PcTextMuted,
                uncheckedTrackColor = VerseColors.PcBgTertiary
            )
        )
    }
}

/**
 * PC 端对齐的文本输入行：顶部标签 + 下方输入框
 */
@Composable
fun PcTextInputRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = VerseColors.PcTextSecondary
        )
        Spacer(Modifier.height(6.dp))
        PcTextInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * PC 端对齐的下拉选择行：顶部标签 + 下方下拉框
 */
@Composable
fun PcDropdownRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.find { it.first == value }?.second ?: value

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = VerseColors.PcTextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = VerseColors.PcBgTertiary,
                border = BorderStroke(1.dp, VerseColors.PcBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        displayText,
                        fontSize = 13.sp,
                        color = VerseColors.PcTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        null,
                        Modifier.size(18.dp),
                        tint = VerseColors.PcTextMuted
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(VerseColors.PcBgCard, RoundedCornerShape(8.dp))
                    .border(1.dp, VerseColors.PcBorder, RoundedCornerShape(8.dp))
            ) {
                options.forEach { (key, lbl) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                lbl,
                                fontSize = 13.sp,
                                color = if (key == value) VerseColors.PcAccent else VerseColors.PcTextPrimary,
                                fontWeight = if (key == value) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(key); expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * PC 端对齐的紧凑下拉选择（对齐 versepc2 `.custom-select-sm`：6×10dp padding、12sp 值文本）。
 * 用于资源/下载页筛选，多个可并排成一行，替代占空间的筛选 chip 行。
 */
@Composable
fun PcCompactSelect(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.find { it.first == value }?.second ?: value

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = VerseColors.PcBgTertiary,
            border = BorderStroke(1.dp, VerseColors.PcBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    displayText,
                    fontSize = 12.sp,
                    color = VerseColors.PcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.ArrowDropDown,
                    null,
                    Modifier.size(16.dp),
                    tint = VerseColors.PcTextMuted
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(VerseColors.PcBgCard, RoundedCornerShape(8.dp))
                .border(1.dp, VerseColors.PcBorder, RoundedCornerShape(8.dp))
        ) {
            options.forEach { (key, lbl) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            lbl,
                            fontSize = 12.sp,
                            color = if (key == value) VerseColors.PcAccent else VerseColors.PcTextPrimary,
                            fontWeight = if (key == value) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(key); expanded = false
                    }
                )
            }
        }
    }
}

private val ErrorBg = Color(0xFFFEF2F2)
private val ErrorBorder = Color(0xFFFECACA)
private val RepairBg = Color(0xFFEFF6FF)
private val RepairBorder = Color(0xFFBFDBFE)
private val ProgressGradient = listOf(
    VerseColors.PcAccent,
    VerseColors.PcPurple,
    Color(0xFF06B6D4)
)

@Composable
fun PcLaunchOverlay(
    launchState: LaunchState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(250))
    }

    val logoScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
    }

    val transition = rememberInfiniteTransition()
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val progress = if (launchState.steps.isNotEmpty()) {
        ((launchState.currentStepIndex + 1).toFloat() / launchState.steps.size).coerceIn(0f, 1f)
    } else 0f

    val isFailed = launchState.phase == LaunchPhase.FAILED || launchState.errors.isNotEmpty()
    val showRepair = isFailed
    val showLog = launchState.phase == LaunchPhase.LAUNCHING

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VerseColors.PcBgPrimary)
            .alpha(alphaAnim.value)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = null,
                tint = VerseColors.PcTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "返回",
                fontSize = 13.sp,
                color = VerseColors.PcTextSecondary
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 启动图标：玫瑰曲线加载动画（等价迁移自 versepc2 rose-loader.js）
            Box(modifier = Modifier.size(96.dp)) {
                RoseCurveLoader(
                    modifier = Modifier.fillMaxSize(),
                    color = VerseColors.PcAccent
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(VerseColors.PcBgTertiary)
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .drawWithContent {
                                val brush = Brush.linearGradient(
                                    colors = ProgressGradient,
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, 0f)
                                )
                                drawRect(brush = brush, style = Fill)
                                val shimmerX = shimmerOffset * size.width
                                val shimmerBrush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.5f),
                                        Color.Transparent
                                    ),
                                    start = Offset(shimmerX - size.width * 0.3f, 0f),
                                    end = Offset(shimmerX + size.width * 0.3f, 0f)
                                )
                                drawRect(brush = shimmerBrush, style = Fill)
                                drawContent()
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(6.dp)
                                .blur(6.dp)
                                .background(ProgressGradient.last())
                        )
                    }
                }
            }

            Text(
                text = launchState.message,
                fontSize = 13.sp,
                color = VerseColors.PcTextSecondary,
                textAlign = TextAlign.Center
            )

            if (isFailed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorBg,
                    border = BorderStroke(1.dp, ErrorBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = VerseColors.PcRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "启动失败",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VerseColors.PcRed
                            )
                        }
                        val errorMessage = if (launchState.errors.isNotEmpty()) {
                            launchState.errors.joinToString("\n")
                        } else {
                            launchState.message
                        }
                        Text(
                            text = errorMessage,
                            fontSize = 12.sp,
                            color = VerseColors.PcTextSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        PcButton(
                            text = "导出日志",
                            onClick = {},
                            variant = PcButtonVariant.OUTLINE
                        )
                    }
                }
            }

            if (showRepair) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = RepairBg,
                    border = BorderStroke(1.dp, RepairBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = VerseColors.PcPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "尝试自动修复启动问题",
                            fontSize = 13.sp,
                            color = VerseColors.PcTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        PcButton(
                            text = "修复",
                            onClick = {},
                            variant = PcButtonVariant.PRIMARY
                        )
                    }
                }
            }

            if (showLog) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = VerseColors.PcBgTertiary
                ) {
                    Text(
                        text = launchState.message,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = VerseColors.PcTextMuted,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
