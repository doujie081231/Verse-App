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
/**
 * 拖动实时预览（VerseColors.customAccent），确定/取消/返回均先滑出再关闭。
 */
@Composable
internal fun ColorPickerOverlay(
    initialArgb: Int,
    onCancel: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val context = LocalContext.current
    var hostRef by remember { mutableStateOf<FrameLayout?>(null) }
    var selectorRef by remember { mutableStateOf<ColorSelector?>(null) }
    var latestArgb by remember { mutableIntStateOf(initialArgb) }

    fun close(picker: ColorSelector?) {
        picker?.disappear(true)
    }

    // 系统返回：等价取消（恢复原强调色）
    BackHandler {
        close(selectorRef)
        onCancel()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            FrameLayout(ctx).also { fl ->
                // SideDialogView 滑入动画依赖 currentDisplayMetrics，MainActivity 场景需先初始化
                Tools.init(ctx)
                hostRef = fl
            }
        }
    )

    LaunchedEffect(Unit) {
        val host = hostRef ?: return@LaunchedEffect
        val selector = ColorSelector(context, host, null).also { selectorRef = it }
        selector.setTitle(R.string.color_picker_title)
        selector.setAlphaEnabled(false)
        selector.setColorSelectionListener { argb ->
            latestArgb = argb
            // 拖动实时预览：自定义取色主题同步刷新种子色，黑白主题覆盖强调色
            if (VersePersonalization.colorTheme == ColorThemeType.CUSTOM.name) {
                VersePersonalization.applyThemeCustomColor(argb)
            }
            VerseColors.customAccent = Color(argb)
        }
        selector.setStartButtonListener(R.string.color_picker_cancel) {
            close(selector)
            onCancel()
        }
        selector.setEndButtonListener(R.string.color_picker_confirm) {
            close(selector)
            onConfirm(latestArgb)
        }
        selector.show(true, initialArgb)
    }
}

/**
 * 控制设置页：鼠标 / 手势 / 陀螺仪 / 显示与菜单 / 手柄。
 * 所有控制项写入 MenuSetting（menu_setting.json），改动即时持久化，
 * 与游戏内控制面板共用同一份配置。
 */
@Composable
internal fun ControlSettingsContent(context: android.content.Context) {
    // LauncherPreferences 承载陀螺仪等运行时项，写入前需初始化（幂等）
    LauncherPreferences.init(context)

    var mouseMoveMode by remember { mutableIntStateOf(MenuSetting.mouseMoveMode) }
    var mouseSensitivity by remember { mutableIntStateOf((MenuSetting.mouseSensitivity * 100).toInt()) }
    var mouseSensitivityCursor by remember { mutableIntStateOf((MenuSetting.mouseSensitivityCursor * 100).toInt()) }
    var mouseSize by remember { mutableIntStateOf(MenuSetting.mouseSize) }
    var mouseOffsetX by remember { mutableIntStateOf(MenuSetting.mouseOffsetX) }
    var mouseOffsetY by remember { mutableIntStateOf(MenuSetting.mouseOffsetY) }
    var physicalMouseMode by remember { mutableStateOf(MenuSetting.isPhysicalMouseMode) }

    var disableGesture by remember { mutableStateOf(MenuSetting.isDisableGesture) }
    var disableBEGesture by remember { mutableStateOf(MenuSetting.isDisableBEGesture) }
    var gestureMode by remember { mutableIntStateOf(MenuSetting.gestureMode) }
    var disableLeftTouch by remember { mutableStateOf(MenuSetting.isDisableLeftTouch) }

    var enableGyroscope by remember { mutableStateOf(MenuSetting.isEnableGyroscope) }
    var invertGyroscope by remember { mutableStateOf(MenuSetting.isInvertGyroscope) }
    var gyroscopeSensitivity by remember { mutableIntStateOf(MenuSetting.gyroscopeSensitivity.coerceIn(0, 100)) }
    var gyroscopeSampleRate by remember { mutableIntStateOf(MenuSetting.gyroscopeSampleRate) }
    var gyroscopeSmoothing by remember { mutableStateOf(MenuSetting.isGyroscopeSmoothing) }
    var gyroscopeSmoothingWindow by remember { mutableIntStateOf(MenuSetting.gyroscopeSmoothingWindow) }

    var gestureControl by remember { mutableStateOf(MenuSetting.isGestureControl) }
    var gestureTapAction by remember { mutableIntStateOf(MenuSetting.gestureTapMouseAction) }
    var gestureLongPressAction by remember { mutableIntStateOf(MenuSetting.gestureLongPressMouseAction) }
    var gestureLongPressDelay by remember { mutableIntStateOf(MenuSetting.gestureLongPressDelay) }
    var mouseLongPressDelay by remember { mutableIntStateOf(MenuSetting.mouseLongPressDelay) }

    var lockMenuView by remember { mutableStateOf(MenuSetting.isLockMenuView) }
    var hideMenuView by remember { mutableStateOf(MenuSetting.isHideMenuView) }
    var showMemory by remember { mutableStateOf(MenuSetting.isShowMemory) }
    var disableSoftKeyAdjust by remember { mutableStateOf(MenuSetting.isDisableSoftKeyAdjust) }
    var itemBarWidth by remember { mutableIntStateOf(MenuSetting.itemBarWidth) }
    var itemBarHeight by remember { mutableIntStateOf(MenuSetting.itemBarHeight) }
    var itemBarRule by remember { mutableIntStateOf(MenuSetting.itemBarRule) }
    var itemBarDoubleTap by remember { mutableStateOf(MenuSetting.itemBarDoubleTap) }
    var itemBarLongPress by remember { mutableStateOf(MenuSetting.itemBarLongPress) }
    var cursorOffset by remember { mutableIntStateOf(MenuSetting.cursorOffset.toInt()) }
    var performanceMode by remember { mutableStateOf(MenuSetting.isPerformanceMode) }
    var autoFit by remember { mutableStateOf(MenuSetting.isAutoFit) }
    var autoFitDist by remember { mutableIntStateOf(MenuSetting.autoFitDist) }
    var showLog by remember { mutableStateOf(MenuSetting.isShowLog) }
    var autoShowLog by remember { mutableStateOf(MenuSetting.isAutoShowLog) }

    var gamepadDeadzone by remember { mutableIntStateOf((MenuSetting.gamepadDeadzone * 100).toInt()) }

    fun persist(key: String) = MenuSetting.notifyChange(key)

    // 鼠标指针自定义图片：导入目标形状 + 刷新计数（导入/重置后刷新预览）
    var pointerRefresh by remember { mutableIntStateOf(0) }
    var importShape by remember { mutableStateOf<MousePointerSetting.Shape?>(null) }
    val pointerImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val shape = importShape
        importShape = null
        if (uri == null || shape == null) return@rememberLauncherForActivityResult
        val ok = MousePointerSetting.importImage(context, shape, uri)
        Toast.makeText(context, if (ok) "指针图片已导入" else "指针图片导入失败", Toast.LENGTH_SHORT).show()
        if (ok) pointerRefresh++
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PcCard(title = "鼠标") {
            PcDropdownRow(
                "鼠标控制模式",
                mouseMoveMode.toString(),
                listOf("0" to "点击", "1" to "拖动")
            ) {
                mouseMoveMode = it.toInt()
                MenuSetting.mouseMoveMode = mouseMoveMode
                persist(MenuSetting.Key.MOUSE_MOVE_MODE)
            }
            PcSliderRow(
                label = "鼠标灵敏度",
                value = mouseSensitivity,
                valueRange = 100..1000,
                suffix = "%"
            ) { v ->
                mouseSensitivity = v
                MenuSetting.mouseSensitivity = v / 100.0
                persist(MenuSetting.Key.MOUSE_SENSITIVITY)
            }
            PcSliderRow(
                label = "鼠标灵敏度（当指针可见时）",
                value = mouseSensitivityCursor,
                valueRange = 100..1000,
                suffix = "%"
            ) { v ->
                mouseSensitivityCursor = v
                MenuSetting.mouseSensitivityCursor = v / 100.0
                persist(MenuSetting.Key.MOUSE_SENSITIVITY_CURSOR)
            }
            PcSliderRow(
                label = "鼠标尺寸",
                value = mouseSize,
                valueRange = 0..30,
                suffix = "dp"
            ) { v ->
                mouseSize = v
                MenuSetting.mouseSize = v
                persist(MenuSetting.Key.MOUSE_SIZE)
            }
            PcSliderRow(
                label = "鼠标偏移 X",
                value = mouseOffsetX,
                valueRange = -30..30,
                suffix = "dp"
            ) { v ->
                mouseOffsetX = v
                MenuSetting.mouseOffsetX = v
                persist(MenuSetting.Key.MOUSE_OFFSET_X)
            }
            PcSliderRow(
                label = "鼠标偏移 Y",
                value = mouseOffsetY,
                valueRange = -30..30,
                suffix = "dp"
            ) { v ->
                mouseOffsetY = v
                MenuSetting.mouseOffsetY = v
                persist(MenuSetting.Key.MOUSE_OFFSET_Y)
            }
            PcSwitchRow(
                "实体鼠标控制",
                "使用外接鼠标的相对移动控制视角，关闭时由触屏模拟光标",
                physicalMouseMode
            ) { on ->
                physicalMouseMode = on
                MenuSetting.isPhysicalMouseMode = on
                persist(MenuSetting.Key.PHYSICAL_MOUSE_MODE)
            }
            PcSliderRow(
                label = "鼠标长按延迟",
                value = mouseLongPressDelay,
                valueRange = 100..1000,
                suffix = "ms"
            ) { v ->
                mouseLongPressDelay = v
                MenuSetting.mouseLongPressDelay = v
                persist(MenuSetting.Key.MOUSE_LONG_PRESS_DELAY)
            }
        }

        MousePointerCard(refreshed = pointerRefresh, onClickImport = { shape ->
            importShape = shape
            pointerImagePicker.launch("image/*")
        })

        PcCard(title = "手势") {
            PcSwitchRow(
                "禁用手势",
                "关闭所有触屏手势操作",
                disableGesture
            ) { on ->
                disableGesture = on
                MenuSetting.isDisableGesture = on
                persist(MenuSetting.Key.DISABLE_GESTURE)
            }
            PcSwitchRow(
                "禁用仿基岩版手势",
                "关闭仿基岩版的双击/长按等手势",
                disableBEGesture
            ) { on ->
                disableBEGesture = on
                MenuSetting.isDisableBEGesture = on
                persist(MenuSetting.Key.DISABLE_BE_GESTURE)
            }
            PcDropdownRow(
                "触控模式",
                gestureMode.toString(),
                listOf("0" to "建筑模式", "1" to "战斗模式")
            ) {
                gestureMode = it.toInt()
                MenuSetting.gestureMode = gestureMode
                persist(MenuSetting.Key.GESTURE_MODE)
            }
            PcSwitchRow(
                "禁用左半屏触控",
                "屏蔽屏幕左半区域的触摸输入",
                disableLeftTouch
            ) { on ->
                disableLeftTouch = on
                MenuSetting.isDisableLeftTouch = on
                persist(MenuSetting.Key.DISABLE_LEFT_TOUCH)
            }
            PcSwitchRow(
                "手势控制",
                "开启后点击/长按手势发送下方指定的鼠标按键动作",
                gestureControl
            ) { on ->
                gestureControl = on
                MenuSetting.isGestureControl = on
                persist(MenuSetting.Key.GESTURE_CONTROL)
            }
            PcDropdownRow(
                "单击动作",
                gestureTapAction.toString(),
                listOf("0" to "鼠标右键", "1" to "鼠标左键")
            ) {
                gestureTapAction = it.toInt()
                MenuSetting.gestureTapMouseAction = gestureTapAction
                persist(MenuSetting.Key.GESTURE_TAP_MOUSE_ACTION)
            }
            PcDropdownRow(
                "长按动作",
                gestureLongPressAction.toString(),
                listOf("0" to "鼠标右键", "1" to "鼠标左键")
            ) {
                gestureLongPressAction = it.toInt()
                MenuSetting.gestureLongPressMouseAction = gestureLongPressAction
                persist(MenuSetting.Key.GESTURE_LONG_PRESS_MOUSE_ACTION)
            }
            PcSliderRow(
                label = "手势长按延迟",
                value = gestureLongPressDelay,
                valueRange = 100..1000,
                suffix = "ms",
                enabled = gestureControl
            ) { v ->
                gestureLongPressDelay = v
                MenuSetting.gestureLongPressDelay = v
                persist(MenuSetting.Key.GESTURE_LONG_PRESS_DELAY)
            }
        }

        PcCard(title = "陀螺仪") {
            PcSwitchRow(
                "陀螺仪",
                "开启后转动设备即可控制视角",
                enableGyroscope
            ) { on ->
                enableGyroscope = on
                MenuSetting.isEnableGyroscope = on
                persist(MenuSetting.Key.ENABLE_GYROSCOPE)
            }
            PcSwitchRow(
                "反转陀螺仪",
                "反转陀螺仪的 X / Y 轴方向",
                invertGyroscope
            ) { on ->
                invertGyroscope = on
                MenuSetting.isInvertGyroscope = on
                persist(MenuSetting.Key.INVERT_GYROSCOPE)
                runCatching {
                    LauncherPreferences.PREF_GYRO_INVERT_X = on
                    LauncherPreferences.PREF_GYRO_INVERT_Y = on
                }
            }
            PcSliderRow(
                label = "陀螺仪灵敏度",
                value = gyroscopeSensitivity,
                valueRange = 0..100
            ) { v ->
                val clamped = v.coerceIn(0, 100)
                gyroscopeSensitivity = clamped
                MenuSetting.gyroscopeSensitivity = clamped
                persist(MenuSetting.Key.GYROSCOPE_SENSITIVITY)
                runCatching { LauncherPreferences.PREF_GYRO_SENSITIVITY = clamped * 0.5f }
            }
            PcSliderRow(
                label = "采样率",
                value = gyroscopeSampleRate,
                valueRange = 5..50,
                suffix = "ms"
            ) { v ->
                gyroscopeSampleRate = v
                MenuSetting.gyroscopeSampleRate = v
                persist(MenuSetting.Key.GYROSCOPE_SAMPLE_RATE)
                runCatching { LauncherPreferences.PREF_GYRO_SAMPLE_RATE = v }
            }
            PcSwitchRow(
                "平滑陀螺仪数据",
                "对陀螺仪读数取移动平均，减少抖动",
                gyroscopeSmoothing
            ) { on ->
                gyroscopeSmoothing = on
                MenuSetting.isGyroscopeSmoothing = on
                persist(MenuSetting.Key.GYROSCOPE_SMOOTHING)
                runCatching { LauncherPreferences.PREF_GYRO_SMOOTHING = on }
            }
            PcSliderRow(
                label = "平滑窗口",
                value = gyroscopeSmoothingWindow,
                valueRange = 2..10,
                enabled = gyroscopeSmoothing
            ) { v ->
                gyroscopeSmoothingWindow = v
                MenuSetting.gyroscopeSmoothingWindow = v
                persist(MenuSetting.Key.GYROSCOPE_SMOOTHING_WINDOW)
                runCatching { LauncherPreferences.PREF_GYRO_SMOOTHING_WINDOW = v }
            }
        }

        PcCard(title = "显示与菜单") {
            PcSwitchRow(
                "锁定菜单键",
                "锁定后菜单键不可拖动",
                lockMenuView
            ) { on ->
                lockMenuView = on
                MenuSetting.isLockMenuView = on
                persist(MenuSetting.Key.LOCK_MENU_VIEW)
            }
            PcSwitchRow(
                "隐藏菜单键",
                "隐藏游戏内菜单按键，按音量键唤出",
                hideMenuView
            ) { on ->
                hideMenuView = on
                MenuSetting.isHideMenuView = on
                persist(MenuSetting.Key.HIDE_MENU_VIEW)
            }
            PcSwitchRow(
                "显示内存",
                "在游戏内显示内存占用信息",
                showMemory
            ) { on ->
                showMemory = on
                MenuSetting.isShowMemory = on
                persist(MenuSetting.Key.SHOW_MEMORY)
            }
            PcSwitchRow(
                "显示日志",
                "在游戏内显示日志窗口",
                showLog
            ) { on ->
                showLog = on
                MenuSetting.isShowLog = on
                persist(MenuSetting.Key.SHOW_LOG)
            }
            PcSwitchRow(
                "自动显示日志",
                "启动游戏时自动展开日志窗口",
                autoShowLog
            ) { on ->
                autoShowLog = on
                MenuSetting.isAutoShowLog = on
                persist(MenuSetting.Key.AUTO_SHOW_LOG)
            }
            PcSwitchRow(
                "禁用软键盘自适应",
                "软键盘弹出时不自动调整控件布局",
                disableSoftKeyAdjust
            ) { on ->
                disableSoftKeyAdjust = on
                MenuSetting.isDisableSoftKeyAdjust = on
                persist(MenuSetting.Key.DISABLE_SOFT_KEY_ADJUST)
            }
            PcSwitchRow(
                "持续性能模式",
                "启用系统持续性能模式以稳定帧率",
                performanceMode
            ) { on ->
                performanceMode = on
                MenuSetting.isPerformanceMode = on
                persist(MenuSetting.Key.PERFORMANCE_MODE)
            }
            // 快捷栏（物品栏）判定方式：自动按 MC GUI 缩放算尺寸，自定义用下面两个百分比。
            // 旧版默认两个百分比都是 0，等于整层关闭 —— 反馈里「只能使用第一格物品」就是这个原因。
            PcDropdownRow(
                label = "物品栏判定方式",
                value = if (itemBarRule == MenuSetting.ITEM_BAR_RULE_CUSTOM) "custom" else "auto",
                options = listOf(
                    "auto" to "自动（跟随游戏 GUI 缩放）",
                    "custom" to "自定义尺寸"
                )
            ) { v ->
                val rule = if (v == "custom") MenuSetting.ITEM_BAR_RULE_CUSTOM
                else MenuSetting.ITEM_BAR_RULE_AUTO
                itemBarRule = rule
                MenuSetting.itemBarRule = rule
                persist(MenuSetting.Key.ITEM_BAR_RULE)
                com.verse.explorer.app.ui.game.HotbarOverlayView.requestFeedback()
            }
            if (itemBarRule == MenuSetting.ITEM_BAR_RULE_CUSTOM) {
            PcSliderRow(
                label = "物品栏缩放 - 宽度",
                value = itemBarWidth,
                valueRange = 0..100,
                suffix = "%"
            ) { v ->
                itemBarWidth = v
                MenuSetting.itemBarWidth = v
                persist(MenuSetting.Key.ITEM_BAR_WIDTH)
                com.verse.explorer.app.ui.game.HotbarOverlayView.requestFeedback()
            }
            PcSliderRow(
                label = "物品栏缩放 - 高度",
                value = itemBarHeight,
                valueRange = 0..100,
                suffix = "%"
            ) { v ->
                itemBarHeight = v
                MenuSetting.itemBarHeight = v
                persist(MenuSetting.Key.ITEM_BAR_HEIGHT)
                com.verse.explorer.app.ui.game.HotbarOverlayView.requestFeedback()
            }
            }
            PcSwitchRow(
                "双击交换主副手",
                "同一格连点两次发送 F，交换主手与副手物品",
                itemBarDoubleTap
            ) { on ->
                itemBarDoubleTap = on
                MenuSetting.itemBarDoubleTap = on
                persist(MenuSetting.Key.ITEM_BAR_DOUBLE_TAP)
            }
            PcSwitchRow(
                "长按丢弃",
                "按住某一格 ${MenuSetting.itemBarLongPressDelay}ms 发送 Q 丢弃该格物品",
                itemBarLongPress
            ) { on ->
                itemBarLongPress = on
                MenuSetting.itemBarLongPress = on
                persist(MenuSetting.Key.ITEM_BAR_LONG_PRESS)
            }
            Text(
                "自动模式按游戏 GUI 缩放计算 9 格尺寸，无需手动调；点按即切到该格、左右滑动跟手切格。" +
                    "自定义模式下两个百分比都为 0 表示关闭该触控层（不拦截自定义布局）",
                fontSize = 11.sp,
                color = VerseColors.PcTextMuted
            )
            PcSliderRow(
                label = "光标偏移",
                value = cursorOffset,
                valueRange = -150..150
            ) { v ->
                cursorOffset = v
                MenuSetting.cursorOffset = v.toDouble()
                persist(MenuSetting.Key.CURSOR_OFFSET)
            }
        }

        PcCard(title = "手柄") {
            // 手柄输入模式与 FCL/SDL 侧共用 "launcher" 偏好（gamepad_input_mode）：
            // SDL_DIRECT=手柄事件直通 SDL 游戏层；MAPPED=预留给控制布局映射
            var gamepadInputMode by remember {
                mutableStateOf(
                    context.getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE)
                        .getString("gamepad_input_mode", "MAPPED") ?: "MAPPED"
                )
            }
            PcDropdownRow(
                "手柄输入模式",
                gamepadInputMode,
                listOf(
                    "SDL_DIRECT" to "SDL 直通",
                    "MAPPED" to "控制布局映射（预留）"
                )
            ) {
                gamepadInputMode = it
                context.getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE)
                    .edit().putString("gamepad_input_mode", it).apply()
            }
            Text(
                "手柄事件由 SDL 游戏层直通处理；死区随游戏内控制面板同步。",
                fontSize = 11.sp,
                color = VerseColors.PcTextMuted
            )
            PcSliderRow(
                label = "手柄死区",
                value = gamepadDeadzone,
                valueRange = 0..100,
                suffix = "%"
            ) { v ->
                gamepadDeadzone = v
                MenuSetting.gamepadDeadzone = v / 100.0
                persist(MenuSetting.Key.GAMEPAD_DEADZONE)
            }
        }

        PcCard(title = "控制面板") {
            PcSwitchRow(
                "自动吸附",
                "控件边缘自动对齐屏幕与相邻控件",
                autoFit
            ) { on ->
                autoFit = on
                MenuSetting.isAutoFit = on
                persist(MenuSetting.Key.AUTO_FIT)
            }
            PcSliderRow(
                label = "自动吸附间距",
                value = autoFitDist,
                valueRange = 0..10,
                suffix = "dp",
                enabled = autoFit
            ) { v ->
                autoFitDist = v
                MenuSetting.autoFitDist = v
                persist(MenuSetting.Key.AUTO_FIT_DIST)
            }
        }
    }
}

/** 指针形状显示名 */
