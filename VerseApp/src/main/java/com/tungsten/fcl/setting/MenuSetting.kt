package com.tungsten.fcl.setting

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.verse.explorer.app.ui.VerseLogger
import java.io.File
import java.io.IOException

/**
 * 游戏内控制栏设置。
 *
 * 差异说明：旧实现用 Simple*Property 做双向绑定，本项目无 fakefx 依赖，
 * 改用 Kotlin 普通可变属性 + 变更监听器，序列化键名、默认值与旧版序列化格式完全一致，
 * 持久化到 <filesDir>/menu_setting.json（与旧版路径保持一致，保证配置兼容）。
 */
object MenuSetting {

    const val FILE_NAME = "menu_setting.json"

    /**
     * 按屏幕尺寸的百分比计算渲染宽高（px）。
     * 光标偏移补偿保留，用于可见光标场景的窗口宽度对齐。
     */
    fun resolveRenderSize(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        val factor = windowScale
        val width = ((screenWidth + cursorOffset) * factor).toInt().coerceAtLeast(1)
        val height = (screenHeight * factor).toInt().coerceAtLeast(1)
        return width to height
    }

    @Volatile
    private var initialized = false
    private var saveFile: File? = null

    private val listeners = mutableListOf<(MenuSettingChange) -> Unit>()

    /** 与旧版序列化格式完全一致的 Json 键 */
    object Key {
        const val AUTO_FIT = "autoFit"
        const val AUTO_FIT_DIST = "autoFitDist"
        const val LOCK_MENU_VIEW = "lockMenuView"
        const val HIDE_MENU_VIEW = "hideMenuView"
        const val SHOW_FPS = "showFps"
        const val SHOW_MEMORY = "showMemory"
        const val DISABLE_SOFT_KEY_ADJUST = "disableSoftKeyAdjust"
        const val SHOW_LOG = "showLog"
        const val AUTO_SHOW_LOG = "autoShowLog"
        const val PERFORMANCE_MODE = "performanceMode"
        const val MENU_POSITION_X = "menuPositionX"
        const val MENU_POSITION_Y = "menuPositionY"
        const val DISABLE_GESTURE = "disableGesture"
        const val DISABLE_BE_GESTURE = "disableBEGesture"
        const val GESTURE_MODE = "gestureMode"
        const val DISABLE_LEFT_TOUCH = "disableLeftTouch"
        const val ENABLE_GYROSCOPE = "enableGyroscope"
        const val INVERT_GYROSCOPE = "invertGyroscope"
        const val GYROSCOPE_SENSITIVITY = "gyroscopeSensitivity"
        const val GYROSCOPE_SAMPLE_RATE = "gyroscopeSampleRate"
        const val GYROSCOPE_SMOOTHING = "gyroscopeSmoothing"
        const val GYROSCOPE_SMOOTHING_WINDOW = "gyroscopeSmoothingWindow"
        const val GESTURE_CONTROL = "gestureControl"
        const val GESTURE_TAP_MOUSE_ACTION = "gestureTapMouseAction"
        const val GESTURE_LONG_PRESS_MOUSE_ACTION = "gestureLongPressMouseAction"
        const val GESTURE_LONG_PRESS_DELAY = "gestureLongPressDelay"
        const val MOUSE_LONG_PRESS_DELAY = "mouseLongPressDelay"
        const val MOUSE_MOVE_MODE = "mouseMoveMode"
        const val MOUSE_SENSITIVITY = "mouseSensitivity"
        const val MOUSE_SENSITIVITY_CURSOR = "mouseSensitivityCursor"
        const val MOUSE_SIZE = "mouseSize"
        const val MOUSE_OFFSET_X = "mouseOffsetX"
        const val MOUSE_OFFSET_Y = "mouseOffsetY"
        const val PHYSICAL_MOUSE_MODE = "physicalMouseMode"
        const val ITEM_BAR_WIDTH = "itemBarWidth"
        const val ITEM_BAR_HEIGHT = "itemBarHeight"
        const val WINDOW_SCALE = "windowScale"
        const val CURSOR_OFFSET = "cursorOffset"
        const val GAMEPAD_DEADZONE = "gamepadDeadzone"
    }

    // ===== 属性（默认值与旧版一致） =====
    var isAutoFit: Boolean = true
    var autoFitDist: Int = 0
    var isLockMenuView: Boolean = false
    var isHideMenuView: Boolean = false
    var isShowFps: Boolean = false
    var isShowMemory: Boolean = false
    var isDisableSoftKeyAdjust: Boolean = false
    var isShowLog: Boolean = false
    var isAutoShowLog: Boolean = false
    var isPerformanceMode: Boolean = false
    var menuPositionX: Double = 0.5
    var menuPositionY: Double = 0.5
    var isDisableGesture: Boolean = false
    var isDisableBEGesture: Boolean = false
    var gestureMode: Int = 0          // GestureMode.BUILD
    var isDisableLeftTouch: Boolean = false
    var isEnableGyroscope: Boolean = false
    var isInvertGyroscope: Boolean = false
    var gyroscopeSensitivity: Int = 10
    var gyroscopeSampleRate: Int = 16        // 采样间隔（ms）
    var isGyroscopeSmoothing: Boolean = true
    var gyroscopeSmoothingWindow: Int = 4
    var isGestureControl: Boolean = false
    var gestureTapMouseAction: Int = 0        // GestureActionType.MOUSE_RIGHT
    var gestureLongPressMouseAction: Int = 1 // GestureActionType.MOUSE_LEFT
    var gestureLongPressDelay: Int = 300
    var mouseLongPressDelay: Int = 300
    var mouseMoveMode: Int = 0        // MouseMoveMode.CLICK
    var mouseSensitivity: Double = 1.0
    var mouseSensitivityCursor: Double = 2.0
    var mouseSize: Int = 15
    var mouseOffsetX: Int = 0
    var mouseOffsetY: Int = 0
    var isPhysicalMouseMode: Boolean = false
    var itemBarWidth: Int = 0
    var itemBarHeight: Int = 0
    var windowScale: Double = 1.0
    var cursorOffset: Double = 0.0
    var gamepadDeadzone: Double = 0.2

    /** 变更回调（参数为触发的键名，可能为 null=批量刷新） */
    fun addPropertyChangedListener(listener: (MenuSettingChange) -> Unit) {
        listeners.add(listener)
    }

    fun removePropertyChangedListener(listener: (MenuSettingChange) -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChange(key: String? = null) {
        val change = MenuSettingChange(key)
        listeners.forEach { it(change) }
        save()
    }

    /** 加载逻辑：存在则读取，损坏则删除重建默认 */
    fun init(filesDir: File) {
        if (initialized) return
        initialized = true
        saveFile = File(filesDir, FILE_NAME)
        val file = saveFile ?: return
        if (!file.exists()) {
            save()
            return
        }
        try {
            val json = file.readText()
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            applyJson(obj)
            VerseLogger.info("MenuSetting 已从 ${file.absolutePath} 加载", "MenuSetting")
        } catch (e: Throwable) {
            VerseLogger.warn("menu_setting.json 损坏，使用默认设置: ${e.message}", "MenuSetting")
            file.delete()
        }
    }

    private fun applyJson(obj: JsonObject) {
        isAutoFit = obj[Key.AUTO_FIT]?.asBoolean ?: true
        autoFitDist = obj[Key.AUTO_FIT_DIST]?.asInt ?: 0
        isLockMenuView = obj[Key.LOCK_MENU_VIEW]?.asBoolean ?: false
        isHideMenuView = obj[Key.HIDE_MENU_VIEW]?.asBoolean ?: false
        isShowFps = obj[Key.SHOW_FPS]?.asBoolean ?: false
        isShowMemory = obj[Key.SHOW_MEMORY]?.asBoolean ?: false
        isDisableSoftKeyAdjust = obj[Key.DISABLE_SOFT_KEY_ADJUST]?.asBoolean ?: false
        isShowLog = obj[Key.SHOW_LOG]?.asBoolean ?: false
        isAutoShowLog = obj[Key.AUTO_SHOW_LOG]?.asBoolean ?: false
        isPerformanceMode = obj[Key.PERFORMANCE_MODE]?.asBoolean ?: false
        menuPositionX = obj[Key.MENU_POSITION_X]?.asDouble ?: 0.5
        menuPositionY = obj[Key.MENU_POSITION_Y]?.asDouble ?: 0.5
        isDisableGesture = obj[Key.DISABLE_GESTURE]?.asBoolean ?: false
        isDisableBEGesture = obj[Key.DISABLE_BE_GESTURE]?.asBoolean ?: false
        gestureMode = obj[Key.GESTURE_MODE]?.asInt ?: 0
        isDisableLeftTouch = obj[Key.DISABLE_LEFT_TOUCH]?.asBoolean ?: false
        isEnableGyroscope = obj[Key.ENABLE_GYROSCOPE]?.asBoolean ?: false
        isInvertGyroscope = obj[Key.INVERT_GYROSCOPE]?.asBoolean ?: false
        // 灵敏度上限收敛到 100：旧配置里存过 1000 等越界值，换算后转速高到不可用
        gyroscopeSensitivity = (obj[Key.GYROSCOPE_SENSITIVITY]?.asInt ?: 10).coerceIn(0, 100)
        gyroscopeSampleRate = obj[Key.GYROSCOPE_SAMPLE_RATE]?.asInt ?: 16
        isGyroscopeSmoothing = obj[Key.GYROSCOPE_SMOOTHING]?.asBoolean ?: true
        gyroscopeSmoothingWindow = obj[Key.GYROSCOPE_SMOOTHING_WINDOW]?.asInt ?: 4
        isGestureControl = obj[Key.GESTURE_CONTROL]?.asBoolean ?: false
        gestureTapMouseAction = obj[Key.GESTURE_TAP_MOUSE_ACTION]?.asInt ?: 0
        gestureLongPressMouseAction = obj[Key.GESTURE_LONG_PRESS_MOUSE_ACTION]?.asInt ?: 1
        gestureLongPressDelay = obj[Key.GESTURE_LONG_PRESS_DELAY]?.asInt ?: 300
        mouseLongPressDelay = obj[Key.MOUSE_LONG_PRESS_DELAY]?.asInt ?: 300
        mouseMoveMode = obj[Key.MOUSE_MOVE_MODE]?.asInt ?: 0
        mouseSensitivity = obj[Key.MOUSE_SENSITIVITY]?.asDouble ?: 1.0
        mouseSensitivityCursor = obj[Key.MOUSE_SENSITIVITY_CURSOR]?.asDouble ?: 2.0
        mouseSize = obj[Key.MOUSE_SIZE]?.asInt ?: 15
        mouseOffsetX = obj[Key.MOUSE_OFFSET_X]?.asInt ?: 0
        mouseOffsetY = obj[Key.MOUSE_OFFSET_Y]?.asInt ?: 0
        isPhysicalMouseMode = obj[Key.PHYSICAL_MOUSE_MODE]?.asBoolean ?: false
        itemBarWidth = obj[Key.ITEM_BAR_WIDTH]?.asInt ?: 0
        itemBarHeight = obj[Key.ITEM_BAR_HEIGHT]?.asInt ?: 0
        windowScale = obj[Key.WINDOW_SCALE]?.asDouble ?: 1.0
        cursorOffset = obj[Key.CURSOR_OFFSET]?.asDouble ?: 0.0
        gamepadDeadzone = obj[Key.GAMEPAD_DEADZONE]?.asDouble ?: 0.2
    }

    fun save() {
        val file = saveFile ?: return
        try {
            val content = GsonBuilder().setPrettyPrinting().create().toJson(serialize())
            file.writeText(content)
        } catch (e: IOException) {
            VerseLogger.warn("保存 menu_setting.json 失败: ${e.message}", "MenuSetting")
        }
    }

    private fun serialize(): JsonObject {
        return JsonObject().apply {
            addProperty(Key.AUTO_FIT, isAutoFit)
            addProperty(Key.AUTO_FIT_DIST, autoFitDist)
            addProperty(Key.LOCK_MENU_VIEW, isLockMenuView)
            addProperty(Key.HIDE_MENU_VIEW, isHideMenuView)
            addProperty(Key.SHOW_FPS, isShowFps)
            addProperty(Key.SHOW_MEMORY, isShowMemory)
            addProperty(Key.DISABLE_SOFT_KEY_ADJUST, isDisableSoftKeyAdjust)
            addProperty(Key.SHOW_LOG, isShowLog)
            addProperty(Key.AUTO_SHOW_LOG, isAutoShowLog)
            addProperty(Key.PERFORMANCE_MODE, isPerformanceMode)
            addProperty(Key.MENU_POSITION_X, menuPositionX)
            addProperty(Key.MENU_POSITION_Y, menuPositionY)
            addProperty(Key.DISABLE_GESTURE, isDisableGesture)
            addProperty(Key.DISABLE_BE_GESTURE, isDisableBEGesture)
            addProperty(Key.GESTURE_MODE, gestureMode)
            addProperty(Key.DISABLE_LEFT_TOUCH, isDisableLeftTouch)
            addProperty(Key.ENABLE_GYROSCOPE, isEnableGyroscope)
            addProperty(Key.INVERT_GYROSCOPE, isInvertGyroscope)
            addProperty(Key.GYROSCOPE_SENSITIVITY, gyroscopeSensitivity)
            addProperty(Key.GYROSCOPE_SAMPLE_RATE, gyroscopeSampleRate)
            addProperty(Key.GYROSCOPE_SMOOTHING, isGyroscopeSmoothing)
            addProperty(Key.GYROSCOPE_SMOOTHING_WINDOW, gyroscopeSmoothingWindow)
            addProperty(Key.GESTURE_CONTROL, isGestureControl)
            addProperty(Key.GESTURE_TAP_MOUSE_ACTION, gestureTapMouseAction)
            addProperty(Key.GESTURE_LONG_PRESS_MOUSE_ACTION, gestureLongPressMouseAction)
            addProperty(Key.GESTURE_LONG_PRESS_DELAY, gestureLongPressDelay)
            addProperty(Key.MOUSE_LONG_PRESS_DELAY, mouseLongPressDelay)
            addProperty(Key.MOUSE_MOVE_MODE, mouseMoveMode)
            addProperty(Key.MOUSE_SENSITIVITY, mouseSensitivity)
            addProperty(Key.MOUSE_SENSITIVITY_CURSOR, mouseSensitivityCursor)
            addProperty(Key.MOUSE_SIZE, mouseSize)
            addProperty(Key.MOUSE_OFFSET_X, mouseOffsetX)
            addProperty(Key.MOUSE_OFFSET_Y, mouseOffsetY)
            addProperty(Key.PHYSICAL_MOUSE_MODE, isPhysicalMouseMode)
            addProperty(Key.ITEM_BAR_WIDTH, itemBarWidth)
            addProperty(Key.ITEM_BAR_HEIGHT, itemBarHeight)
            addProperty(Key.WINDOW_SCALE, windowScale)
            addProperty(Key.CURSOR_OFFSET, cursorOffset)
            addProperty(Key.GAMEPAD_DEADZONE, gamepadDeadzone)
        }
    }
}

/** 设置变更事件（key 为发生变更的键名，null 表示整体刷新） */
data class MenuSettingChange(val key: String?)
