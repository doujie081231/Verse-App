package com.verse.explorer.app.ui

import android.content.Intent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.verse.explorer.app.R
import com.verse.explorer.app.BuildConfig
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.app.data.MousePointerSetting
import com.verse.explorer.app.data.UpdateChecker
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.launcher.renderer.RendererPluginManager
import java.io.File
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.navButtonAvoid
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.PcDropdownRow
import com.verse.explorer.app.ui.components.PcSwitchRow
import com.verse.explorer.app.ui.components.PcSliderRow
import com.verse.explorer.app.ui.components.PcTextInput
import com.verse.explorer.app.ui.components.PcTextInputRow
import com.verse.explorer.app.ui.components.UpdateDialog
import com.verse.explorer.app.ui.components.pcTextFieldColors
import com.verse.explorer.app.ui.theme.ActionMenuSide
import com.verse.explorer.app.ui.theme.ColorThemeType
import com.verse.explorer.app.ui.theme.DarkMode
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.paletteStyleLabel
import com.verse.explorer.app.ui.theme.paletteSwatch
import com.materialkolor.PaletteStyle
import com.tungsten.fcl.setting.MenuSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.colorselector.ColorSelector
import net.kdt.pojavlaunch.prefs.LauncherPreferences
private fun shapeLabel(shape: MousePointerSetting.Shape): String = when (shape) {
    MousePointerSetting.Shape.ARROW -> "箭头"
    MousePointerSetting.Shape.IBEAM -> "输入光标"
    MousePointerSetting.Shape.CROSSHAIR -> "十字"
    MousePointerSetting.Shape.HAND -> "手形"
    MousePointerSetting.Shape.RESIZE_EW -> "左右调整"
    MousePointerSetting.Shape.RESIZE_NS -> "上下调整"
    MousePointerSetting.Shape.RESIZE_ALL -> "全向移动"
    MousePointerSetting.Shape.NOT_ALLOWED -> "禁止"
}

/** 鼠标指针（各形状的自定义图片 / 热点）设置卡片 */
@Composable
internal fun MousePointerCard(
    refreshed: Int,
    onClickImport: (MousePointerSetting.Shape) -> Unit
) {
    var localRefresh by remember { mutableIntStateOf(0) }
    var hotspotShape by remember { mutableStateOf<MousePointerSetting.Shape?>(null) }
    val tick = refreshed + localRefresh

    PcCard(title = "鼠标指针") {
        MousePointerSetting.Shape.entries.forEach { shape ->
            MousePointerRow(
                shape = shape,
                refreshKey = tick,
                onClickImport = onClickImport,
                onEditHotspot = { hotspotShape = shape },
                onReset = {
                    MousePointerSetting.resetImage(shape)
                    localRefresh++
                }
            )
        }
    }

    hotspotShape?.let { shape ->
        MouseHotspotDialog(shape = shape, onClose = { hotspotShape = null })
    }
}

@Composable
private fun MousePointerRow(
    shape: MousePointerSetting.Shape,
    refreshKey: Int,
    onClickImport: (MousePointerSetting.Shape) -> Unit,
    onEditHotspot: () -> Unit,
    onReset: () -> Unit
) {
    val hasImage = MousePointerSetting.hasImage(shape)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PointerPreview(shape = shape, refreshKey = refreshKey, sizeDp = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                shapeLabel(shape),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = VerseColors.PcTextPrimary
            )
            Text(
                if (hasImage) "已使用自定义图片" else "使用内置图片",
                fontSize = 11.sp,
                color = if (hasImage) VerseColors.PcAccent else VerseColors.PcTextMuted
            )
        }
        Text(
            "导入",
            fontSize = 12.sp,
            color = VerseColors.PcAccent,
            modifier = Modifier
                .clickable { onClickImport(shape) }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Text(
            "热点",
            fontSize = 12.sp,
            color = VerseColors.PcTextSecondary,
            modifier = Modifier
                .clickable { onEditHotspot() }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        if (hasImage) {
            Text(
                "重置",
                fontSize = 12.sp,
                color = VerseColors.PcRed,
                modifier = Modifier
                    .clickable { onReset() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

/** 指针图片预览：优先自定义图片，否则内置图片 */
@Composable
private fun PointerPreview(shape: MousePointerSetting.Shape, refreshKey: Int, sizeDp: Int) {
    val bitmap = remember(shape, refreshKey) { MousePointerSetting.loadBitmap(shape) }
    Box(modifier = Modifier.size(sizeDp.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(shape.defaultRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 指针热点编辑对话框：左侧预览图（红点标记热点），右侧两个滑块，横屏下有足够留白 */
@Composable
private fun MouseHotspotDialog(shape: MousePointerSetting.Shape, onClose: () -> Unit) {
    val initial = remember(shape) { MousePointerSetting.hotspot(shape) }
    var x by remember { mutableIntStateOf(initial[0]) }
    var y by remember { mutableIntStateOf(initial[1]) }

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = VerseColors.PcBgSecondary,
            modifier = Modifier.width(460.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "${shapeLabel(shape)}·指针热点",
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "热点是鼠标的实际作用点（点击/悬停命中位置），以图片宽高的百分比表示。",
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(modifier = Modifier.size(96.dp)) {
                        val bitmap = remember(shape) { MousePointerSetting.loadBitmap(shape) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(shape.defaultRes),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width * x / 100f
                            val cy = size.height * y / 100f
                            drawCircle(Color(0xFFC9350F), radius = 5.dp.toPx(), center = Offset(cx, cy))
                            drawCircle(
                                Color.White,
                                radius = 4.dp.toPx(),
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        PcSliderRow(
                            label = "热点 X",
                            value = x,
                            valueRange = 0..100,
                            suffix = "%",
                            onValueChange = { x = it }
                        )
                        PcSliderRow(
                            label = "热点 Y",
                            value = y,
                            valueRange = 0..100,
                            suffix = "%",
                            onValueChange = { y = it }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClose) { Text("取消", color = VerseColors.PcTextSecondary) }
                    TextButton(onClick = {
                        MousePointerSetting.setHotspot(shape, x, y)
                        onClose()
                    }) { Text("保存", color = VerseColors.PcAccent) }
                }
            }
        }
    }
}
