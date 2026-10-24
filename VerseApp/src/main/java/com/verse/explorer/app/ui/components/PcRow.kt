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
