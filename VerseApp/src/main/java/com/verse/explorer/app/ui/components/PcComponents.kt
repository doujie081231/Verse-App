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
 * 悬浮导航避让：页面顶部栏与悬浮的圆形导航按钮重叠时，沿按钮停靠侧给顶部栏留出让位内边距，
 * 把标题与返回键挪到旁边；只作用于顶部栏，页面内容区宽度不受影响。
 */
@Composable
fun Modifier.navButtonAvoid(): Modifier {
    val dockEnd = VersePersonalization.actionMenuSide != ActionMenuSide.START.name
    return if (dockEnd) padding(end = 44.dp) else padding(start = 44.dp)
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

/**
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
 * 整型滑块行：标题 + 当前值 + 滑块，用于控制设置中的数值项。
 */
@Composable
fun PcSliderRow(
    label: String,
    value: Int,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    description: String = "",
    suffix: String = "",
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) VerseColors.PcTextSecondary else VerseColors.PcTextMuted
                )
                if (description.isNotEmpty()) {
                    Text(description, fontSize = 11.sp, color = VerseColors.PcTextMuted)
                }
            }
            Text(
                "$value$suffix",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) VerseColors.PcAccent else VerseColors.PcTextMuted
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = VerseColors.PcAccent,
                activeTrackColor = VerseColors.PcAccent,
                inactiveTrackColor = VerseColors.PcBgTertiary,
                disabledThumbColor = VerseColors.PcTextMuted,
                disabledActiveTrackColor = VerseColors.PcTextMuted
            )
        )
    }
}

/**
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
 * 选项列表内联展开（AnimatedVisibility 插在触发框正下方）：浮层 Popup 在
 * LazyColumn 滚动容器中相对父锚的定位会漂移（展开项与触发框错位），内联
 * 展开天然与触发框左对齐、同宽，且随页面滚动保持对位。
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
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VerseColors.PcBgTertiary,
            border = BorderStroke(1.dp, VerseColors.PcBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
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
        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = VerseColors.PcBgCard,
                border = BorderStroke(1.dp, VerseColors.PcBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column {
                    options.forEach { (key, lbl) ->
                        Text(
                            lbl,
                            fontSize = 13.sp,
                            color = if (key == value) VerseColors.PcAccent else VerseColors.PcTextPrimary,
                            fontWeight = if (key == value) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(key); expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 筛选下拉条目：value 为当前选中键，options 为 (键, 显示文本) 列表 */
data class PcFilterSelect(
    val value: String,
    val options: List<Pair<String, String>>,
    val onSelect: (String) -> Unit
)

/**
 * 触发框与 leading（搜索框等）并排一行；点击触发框时选项列表内联展开在整行下方，
 * 把底部内容顶下去（不使用浮层，规避 Popup 在滚动容器中的锚定漂移与无限约束崩溃）。
 */
@Composable
fun PcCompactFilterBar(
    selects: List<PcFilterSelect>,
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null
) {
    var expandedIndex by remember { mutableStateOf(-1) }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leading?.let { it() }
            selects.forEachIndexed { index, sel ->
                val displayText = sel.options.find { it.first == sel.value }?.second ?: sel.value
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VerseColors.PcBgTertiary,
                    border = BorderStroke(1.dp, VerseColors.PcBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expandedIndex = if (expandedIndex == index) -1 else index }
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
            }
        }
        AnimatedVisibility(visible = expandedIndex >= 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = VerseColors.PcBgCard,
                border = BorderStroke(1.dp, VerseColors.PcBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column {
                    val sel = selects[expandedIndex.coerceIn(0, selects.lastIndex)]
                    sel.options.forEach { (key, lbl) ->
                        Text(
                            lbl,
                            fontSize = 12.sp,
                            color = if (key == sel.value) VerseColors.PcAccent else VerseColors.PcTextPrimary,
                            fontWeight = if (key == sel.value) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sel.onSelect(key); expandedIndex = -1
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * BasicTextField 自绘装饰，图标与文本严格垂直居中，避免 Material3 OutlinedTextField
 * 高度压到 40dp 时文本被 48dp 图标按钮顶到下方的问题。
 */
@Composable
fun PcCompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(VerseColors.PcBgTertiary)
            .border(1.dp, VerseColors.PcBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = VerseColors.PcTextPrimary),
        cursorBrush = SolidColor(VerseColors.PcAccent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = VerseColors.PcTextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            fontSize = 13.sp,
                            color = VerseColors.PcTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Outlined.Clear,
                        contentDescription = null,
                        tint = VerseColors.PcTextMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable {
                                onValueChange("")
                                onSearch?.invoke()
                            }
                    )
                }
            }
        }
    )
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
    modifier: Modifier = Modifier,
    /** 游戏已进入创建窗口阶段时允许关闭提示（关闭后启动继续在后台进行） */
    onDismiss: () -> Unit = {}
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

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .widthIn(max = 360.dp)
                .alpha(alphaAnim.value),
            shape = RoundedCornerShape(24.dp),
            color = VerseColors.PcBgSecondary,
            contentColor = VerseColors.PcTextPrimary,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 创建窗口阶段：给出可关闭的阶段提示（与「正在启动 Java 虚拟机」同形式的弹窗）
                if (showLog) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "正在创建游戏窗口…",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerseColors.PcTextPrimary
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭提示",
                                tint = VerseColors.PcTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

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
}
