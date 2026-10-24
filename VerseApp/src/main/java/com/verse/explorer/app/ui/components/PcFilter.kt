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
