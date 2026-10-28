package com.verse.explorer.app.ui.game

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.verse.explorer.core.data.GamepadBindingsRepository
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.launcher.bridge.CallbackBridge
import com.verse.explorer.launcher.keycodes.LwjglGlfwKeycode
import com.tungsten.fcl.control.GestureMode
import com.tungsten.fcl.control.MouseMoveMode
import com.tungsten.fcl.setting.MenuSetting
import com.tungsten.fclauncher.bridge.FCLBridge

/**
 * 游戏输入处理：CursorDisabled=抓取态 / CursorEnabled=GUI 态 两条触摸分支，400ms 长按 BUILD/FIGHT 手势，
 * CLICK/SLIDE 鼠标移动模式，物理鼠标 external mouse 事件，左半屏禁用触控。
 * 不新增私有逻辑；设置项从 MenuSetting（menu_setting.json）读取。
 */
@SuppressLint("ClickableViewAccessibility")
class GameInputHandler(
    private val bridgeProvider: () -> FCLBridge?,
    private val context: Context? = null,
    private val hitResultTypeProvider: () -> Int = { FCLBridge.HIT_RESULT_TYPE_UNKNOWN },
    private val captureViewProvider: () -> android.view.View? = { null },
    /** 游戏内菜单/控制面板是否可见：据此选用「菜单内」而非「游戏内」的手柄绑定表 */
    private val menuVisibleProvider: () -> Boolean = { false }
) {

    companion object {
        const val MOUSE_LEFT = 1000
        const val MOUSE_MIDDLE = 1001
        const val MOUSE_RIGHT = 1002
        const val MOUSE_SCROLL_UP = 1003
        const val MOUSE_SCROLL_DOWN = 1004
        private const val TAG = "GameInputHandler"
        private const val CLICK_DELAY = 33L
        private const val TAP_MAX_TIME = 100L
        private const val TAP_MAX_DIST = 10f
        // 手势动作枚举：0=鼠标右键 1=鼠标左键
        private const val ACTION_MOUSE_RIGHT = 0
    }

    /** 长按触发时长：手势控制开启时用「手势长按延迟」，否则用「鼠标长按延迟」 */
    private fun longPressDelay(): Long =
        (if (MenuSetting.isGestureControl) MenuSetting.gestureLongPressDelay
        else MenuSetting.mouseLongPressDelay).toLong()

    private fun gestureMouseKey(action: Int): Int =
        if (action == ACTION_MOUSE_RIGHT) MOUSE_RIGHT else MOUSE_LEFT

    private val handler = Handler(Looper.getMainLooper())

    private var isGrabbing = false

    internal var cursorX = 0f
    internal var cursorY = 0f
    private var pointerX = 0f
    private var pointerY = 0f
    private var pointerId: String? = null

    internal var cursorMoveListener: ((Float, Float) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var initialX = 0f
    private var initialY = 0f
    private var currentPointerID = -1
    private var lastPointerCount = 0
    private var shouldBeDown = false
    private var cancelMouseLeft = false
    private var cancelMouseRight = false

    private val holdRunnable = Runnable {
        VerseLogger.debug("hold fire! gesture=${effectiveGestureMode()} grab=$isGrabbing cancelL=$cancelMouseLeft cancelR=$cancelMouseRight", "TouchDiag")
        if (!MenuSetting.isDisableGesture) {
            if (MenuSetting.isGestureControl) {
                // 手势控制：长按发送「手势长按动作」对应的鼠标按键
                val key = gestureMouseKey(MenuSetting.gestureLongPressMouseAction)
                sendKeyEvent(key, true)
                if (key == MOUSE_LEFT) cancelMouseLeft = true else cancelMouseRight = true
            } else if (effectiveGestureMode() == GestureMode.BUILD) {
                sendKeyEvent(MOUSE_LEFT, true)
                cancelMouseLeft = true
                cancelMouseRight = false
            } else {
                sendKeyEvent(MOUSE_RIGHT, true)
                cancelMouseRight = true
                cancelMouseLeft = false
            }
        }
    }

    // 本次触摸是否发生过滑动（位移超过 TAP_MAX_DIST）。滑动转视角的手指在抬起时
    // 绝不应被判定为 tap/长按点击（否则转视角同时触发挖掘/攻击 → 视角跳转抽搐）。
    // 手动分流（按钮指针跳过）后事件时序错乱，tap 判定会误触发，需用滑动标志兜底。
    private var hasMoved = false

    // 触摸诊断日志节流：DOWN/UP/状态切换全量打印；MOVE 按窗口采样，避免高频刷输入线程
    private var lastMoveDiagTime = 0L
    private var lastMoveDeltaDiagTime = 0L
    // 上次抓取态 MOVE 处理时间：按钮遮挡期间（指针全落按钮上 toInput=false）滑动位移不被跟踪，
    // 重新进入时基线 downX/downY 过期 → 一次巨 delta → 视角跳屏。超时强制重置基线。
    private var lastGrabbedMoveTime = 0L

    private val clickDownRunnable = Runnable { sendKeyEvent(MOUSE_LEFT, true) }
    private val clickUpRunnable = Runnable { sendKeyEvent(MOUSE_LEFT, false) }

    private val screenWidth: Int
        get() = context?.resources?.displayMetrics?.widthPixels ?: 854
    private val screenHeight: Int
        get() = context?.resources?.displayMetrics?.heightPixels ?: 480

    fun isGrabbing(): Boolean = isGrabbing

    /** 进入 GUI 态时把逻辑光标复位到屏幕中心（MC 默认鼠标在窗口中心，逻辑位置需与其对齐） */
    fun centerCursor() {
        setPointer(screenWidth / 2f, screenHeight / 2f)
    }

    fun setCursorMode(mode: Int) {
        isGrabbing = mode == FCLBridge.CursorDisabled
        syncCapturedPointer()
    }

    /** 捕获同步：有实体鼠标时启用指针捕获（相对移动并隐藏系统光标），
     *  触屏设备不捕获，避免触摸事件被以相对形式送进捕获监听。 */
    fun syncCapturedPointer() {
        val view = captureViewProvider()
        if (MenuSetting.isPhysicalMouseMode || hasPhysicalMouse()) {
            view?.requestFocus()
            view?.requestPointerCapture()
        } else {
            view?.releasePointerCapture()
            view?.clearFocus()
        }
    }

    /** 当前是否连接了实体鼠标（与 ZL2 PhysicalMouseChecker 一致：按 SOURCE_MOUSE 判定） */
    private fun hasPhysicalMouse(): Boolean =
        InputDevice.getDeviceIds().any { id ->
            InputDevice.getDevice(id)?.let {
                it.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
            } == true
        }

    private fun effectiveGestureMode(): GestureMode {
        if (MenuSetting.isDisableBEGesture) {
            return GestureMode.getById(MenuSetting.gestureMode)
        }
        val bridge = bridgeProvider()
        return if (bridge != null) {
            when (hitResultTypeProvider()) {
                FCLBridge.HIT_RESULT_TYPE_UNKNOWN -> GestureMode.getById(MenuSetting.gestureMode)
                FCLBridge.HIT_RESULT_TYPE_BLOCK -> GestureMode.BUILD
                else -> GestureMode.FIGHT
            }
        } else {
            GestureMode.getById(MenuSetting.gestureMode)
        }
    }

    // GUI 态屏幕坐标 → 渲染 buffer 坐标的精确映射（刘海避让/窗口比例/强制分辨率场景），
    var pointerMapper: ((Float, Float) -> Pair<Int, Int>?)? = null

    private fun setPointer(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        if (!isGrabbing) {
            cursorX = x
            cursorY = y
            cursorMoveListener?.invoke(x, y)
        }
        val bridge = bridgeProvider() ?: return
        val scale = bridge.getScaleFactor().toFloat()
        val bx: Int
        val by: Int
        if (!isGrabbing && pointerMapper != null) {
            val mapped = pointerMapper!!.invoke(x, y)
            bx = mapped?.first ?: (x * scale).toInt()
            by = mapped?.second ?: (y * scale).toInt()
        } else {
            bx = (x * scale).toInt()
            by = (y * scale).toInt()
        }
        // 光标对齐诊断：屏幕坐标 / 发给游戏的 buffer 坐标（定位可见光标与实际鼠标偏移）
        val now = System.currentTimeMillis()
        if (now - lastCursorDiagTime > 300) {
            lastCursorDiagTime = now
            VerseLogger.debug(
                "cursor screen=(${x.toInt()},${y.toInt()}) buffer=($bx,$by) grab=$isGrabbing",
                "CursorDiag"
            )
        }
        bridge.pushEventPointer(bx, by)
    }

    private var lastCursorDiagTime = 0L

    /** 模拟键盘：直接向游戏窗口发送 GLFW 按键（按住/抬起） */
    fun sendVirtualKey(keycode: Int, press: Boolean) = sendKeyEvent(keycode, press)

    private fun sendKeyEvent(keycode: Int, press: Boolean) {
        val bridge = bridgeProvider() ?: return
        when (keycode) {
            MOUSE_LEFT -> bridge.pushEventMouseButton(FCLBridge.Button1, press)
            MOUSE_MIDDLE -> bridge.pushEventMouseButton(FCLBridge.Button3, press)
            MOUSE_RIGHT -> bridge.pushEventMouseButton(FCLBridge.Button2, press)
            MOUSE_SCROLL_UP -> if (press) bridge.pushEventMouseButton(FCLBridge.Button4, true)
            MOUSE_SCROLL_DOWN -> if (press) bridge.pushEventMouseButton(FCLBridge.Button5, true)
            else -> bridge.pushEventKey(keycode, 0, press)
        }
    }

    fun handleExternalMouseEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val press = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
                when (event.actionButton) {
                    MotionEvent.BUTTON_PRIMARY -> sendKeyEvent(MOUSE_LEFT, press)
                    MotionEvent.BUTTON_SECONDARY -> sendKeyEvent(MOUSE_RIGHT, press)
                    MotionEvent.BUTTON_TERTIARY -> sendKeyEvent(MOUSE_MIDDLE, press)
                }
            }
            // 只有 DOWN/UP 事件的鼠标：用 buttonState 与上次状态差分补报按键
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                val buttons = event.buttonState
                applyButtonDiff(lastMouseButtonState xor buttons, buttons)
                lastMouseButtonState = buttons
            }
            MotionEvent.ACTION_SCROLL -> {
                val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                for (i in 0 until kotlin.math.abs(vScroll.toInt())) {
                    sendKeyEvent(if (vScroll > 0) MOUSE_SCROLL_UP else MOUSE_SCROLL_DOWN, true)
                }
            }
        }
        return true
    }

    fun handleCapturedPointer(event: MotionEvent): Boolean {
        // 捕获态下 x/y 本身就是相对增量；部分驱动还会同时给出相对轴，优先取相对轴
        if (event.actionMasked == MotionEvent.ACTION_MOVE ||
            event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
        ) {
            val rel = relativeAxes(event)
            if (rel != null) {
                applyMouseDelta(rel.first, rel.second)
            } else {
                var tX = event.x
                var tY = event.y
                for (i in 0 until event.historySize) {
                    tX += event.getHistoricalX(i)
                    tY += event.getHistoricalY(i)
                }
                applyMouseDelta(tX, tY)
            }
        }
        return handleExternalMouseEvent(event)
    }

    private fun applyButtonDiff(changed: Int, buttons: Int) {
        fun dispatch(button: Int) {
            if (changed and button != 0) {
                val pressed = buttons and button != 0
                when (button) {
                    MotionEvent.BUTTON_PRIMARY -> sendKeyEvent(MOUSE_LEFT, pressed)
                    MotionEvent.BUTTON_SECONDARY -> sendKeyEvent(MOUSE_RIGHT, pressed)
                    MotionEvent.BUTTON_TERTIARY -> sendKeyEvent(MOUSE_MIDDLE, pressed)
                }
            }
        }
        dispatch(MotionEvent.BUTTON_PRIMARY)
        dispatch(MotionEvent.BUTTON_SECONDARY)
        dispatch(MotionEvent.BUTTON_TERTIARY)
    }

    /** 物理鼠标按键位掩码（按 buttonState 差分上报左/右/中键） */
    private var lastMouseButtonState = 0

    /**
     * 物理鼠标事件：不依赖「实体鼠标控制」开关，始终按 buttonState 差分上报左/右/中键，
     * 并处理移动（hover/拖动）与滚轮。
     */
    /**
     * 鼠标来源判定：普通物理鼠标是 [InputDevice.SOURCE_MOUSE]，
     * 指针捕获/部分外设上报的是 [InputDevice.SOURCE_MOUSE_RELATIVE]。
     * 两者都必须按鼠标处理，否则相对位移类鼠标的移动会被当成触摸丢掉（视角不转）。
     */
    fun isMouseEvent(event: MotionEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)

    fun handlePhysicalMouseEvent(event: MotionEvent): Boolean {
        if (!isMouseEvent(event)) return false

        val buttons = event.buttonState
        val changed = lastMouseButtonState xor buttons
        applyButtonDiff(changed, buttons)
        lastMouseButtonState = buttons

        when (event.actionMasked) {
            // 未建立指针捕获时的实体鼠标移动（含未按键的 hover、按住拖动）：
            // 这类事件不会进捕获监听，必须在这里按位移处理，否则进游戏后鼠标完全不动
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> handlePhysicalMouseMove(event)
            MotionEvent.ACTION_SCROLL -> {
                val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                for (i in 0 until kotlin.math.abs(vScroll.toInt())) {
                    sendKeyEvent(if (vScroll > 0) MOUSE_SCROLL_UP else MOUSE_SCROLL_DOWN, true)
                }
            }
        }
        return true
    }

    /** 上次处理过的实体鼠标位置（无相对轴时用相邻位置差算位移） */
    private var lastPhysicalMouseX = Float.NaN
    private var lastPhysicalMouseY = Float.NaN

    /** 读取事件自带的相对位移（含历史采样点）；无相对轴返回 null */
    private fun relativeAxes(event: MotionEvent): Pair<Float, Float>? {
        var dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        var dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        for (i in 0 until event.historySize) {
            dx += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i)
            dy += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i)
        }
        return if (dx != 0f || dy != 0f) dx to dy else null
    }

    /**
     * 实体鼠标位移：
     *  - 相对轴 AXIS_RELATIVE_X/Y 有值时直接作为本次增量（指针捕获/多数外置鼠标驱动都是这种）；
     *  - 没有相对轴时退回绝对坐标：GUI 态直接定位光标，抓取态与上一位置求差得到增量。
     */
    private fun handlePhysicalMouseMove(event: MotionEvent) {
        val rel = relativeAxes(event)
        if (rel != null) {
            lastPhysicalMouseX = Float.NaN
            lastPhysicalMouseY = Float.NaN
            applyMouseDelta(rel.first, rel.second)
            return
        }
        val x = event.x
        val y = event.y
        if (!isGrabbing) {
            // GUI 态：绝对坐标直接定位光标
            setPointer(x.coerceIn(0f, screenWidth.toFloat()), y.coerceIn(0f, screenHeight.toFloat()))
            lastPhysicalMouseX = x
            lastPhysicalMouseY = y
            return
        }
        if (lastPhysicalMouseX.isNaN()) {
            lastPhysicalMouseX = x
            lastPhysicalMouseY = y
            return
        }
        val dx = x - lastPhysicalMouseX
        val dy = y - lastPhysicalMouseY
        lastPhysicalMouseX = x
        lastPhysicalMouseY = y
        applyMouseDelta(dx, dy)
    }

    /**
     * 把原始位移交给游戏：
     *  - GUI 态移动逻辑光标（按 GUI 灵敏度）；
     *  - 抓取态按增量送进游戏（避免覆盖陀螺仪已累加的光标位移）。
     */
    private fun applyMouseDelta(rawDx: Float, rawDy: Float) {
        val sensitivity = MenuSetting.mouseSensitivity.toFloat()
        val dx = rawDx * sensitivity
        val dy = rawDy * sensitivity
        if (!isGrabbing) {
            val targetX = cursorX + dx * MenuSetting.mouseSensitivityCursor.toFloat()
            val targetY = cursorY + dy * MenuSetting.mouseSensitivityCursor.toFloat()
            setPointer(
                targetX.coerceIn(0f, screenWidth.toFloat()),
                targetY.coerceIn(0f, screenHeight.toFloat())
            )
        } else {
            val scale = bridgeProvider()?.getScaleFactor()?.toFloat() ?: 1f
            org.lwjgl.glfw.CallbackBridge.sendCursorDelta(dx * scale, dy * scale)
            pointerX += dx
            pointerY += dy
        }
    }

    /** GameMenu.setOnGenericMotionListener 的移植：物理鼠标 hover/按钮/滚轮 */
    fun handleGenericMotion(event: MotionEvent): Boolean {
        return handlePhysicalMouseEvent(event)
    }

    fun handleTouchEvent(event: MotionEvent, skipPointerIds: Set<Int> = emptySet()) {
        val bridge = bridgeProvider() ?: run {
            Log.d("VerseX", "[Input] handleTouchEvent: bridge is null")
            return
        }
        // 多指支持：主指针 = 第一个不在虚拟按钮上的指针（按钮指针由覆盖层按钮消费），
        // 这样"左手按方向键 + 右手空白区转视角"可同时工作
        val mainIndex = (0 until event.pointerCount).firstOrNull { event.getPointerId(it) !in skipPointerIds } ?: 0
        diagEvent(event, skipPointerIds, mainIndex)
        if (isGrabbing) {
            handleGrabbedTouch(event, mainIndex)
        } else {
            handleGuiTouch(event, mainIndex)
        }
        if (isGrabbing) {
            lastPointerCount = event.pointerCount
        }
    }

    /**
     * 让下一帧 MOVE 重新取基线、不产生位移。
     * 手指完全落在虚拟按钮 / 快捷栏上时游戏触控收不到事件，此时不清基线，
     * 手指离开按钮的瞬间会按「跨越整段时间的位移」算 delta → 视角猛跳（画面像闪了一下）。
     */
    fun invalidateGrabBaseline() {
        if (isGrabbing) shouldBeDown = false
    }

    /** 触摸诊断日志（仅 logcat，MOVE 采样节流防刷输入线程） */
    private fun diagEvent(event: MotionEvent, skipIds: Set<Int>, mainIndex: Int) {
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_POINTER_DOWN -> "P_DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_POINTER_UP -> "P_UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> "A${event.actionMasked}"
        }
        val now = System.currentTimeMillis()
        val isStateChange = action != "MOVE"
        if (!isStateChange && now - lastMoveDiagTime < 120) return
        lastMoveDiagTime = now
        val ptrs = StringBuilder()
        for (i in 0 until event.pointerCount) {
            if (i > 0) ptrs.append(" ")
            ptrs.append("p$i(id=${event.getPointerId(i)},${event.getX(i).toInt()},${event.getY(i).toInt()})")
        }
        VerseLogger.debug("in: ev=$action cnt=${event.pointerCount} main=$mainIndex skip=$skipIds grab=$isGrabbing | $ptrs", "TouchDiag")
    }

    private fun handleGuiTouch(event: MotionEvent, mainIndex: Int) {
        val bridge = bridgeProvider() ?: return
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.action == MotionEvent.ACTION_MOVE) {
                setPointer(event.rawX, event.rawY)
            }
            // 防止被外接鼠标触发触摸板
            return
        }
        if (MenuSetting.mouseMoveMode == MouseMoveMode.CLICK.id) {
            setPointer(event.getX(mainIndex), event.getY(mainIndex))
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> handler.postDelayed(clickDownRunnable, CLICK_DELAY)
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    // 否则快速点按（DOWN→UP < 33ms）时按下事件被取消，鼠标左键从未按下 → 点击无效。
                    handler.postDelayed(clickUpRunnable, CLICK_DELAY)
                }
            }
        } else {
            // MOVE 相对移动光标（initial + delta × GUI 灵敏度），UP 仅 tap（≤100ms 且 ≤10px）点击
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.getX(mainIndex)
                    downY = event.getY(mainIndex)
                    downTime = System.currentTimeMillis()
                    initialX = cursorX
                    initialY = cursorY
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.getX(mainIndex) - downX) * MenuSetting.mouseSensitivityCursor.toFloat()
                    val deltaY = (event.getY(mainIndex) - downY) * MenuSetting.mouseSensitivityCursor.toFloat()
                    val targetX = (initialX + deltaX).coerceIn(0f, screenWidth.toFloat())
                    val targetY = (initialY + deltaY).coerceIn(0f, screenHeight.toFloat())
                    setPointer(targetX, targetY)
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    if (System.currentTimeMillis() - downTime <= TAP_MAX_TIME
                        && kotlin.math.abs(event.getX(mainIndex) - downX) <= TAP_MAX_DIST
                        && kotlin.math.abs(event.getY(mainIndex) - downY) <= TAP_MAX_DIST
                    ) {
                        sendKeyEvent(MOUSE_LEFT, true)
                        sendKeyEvent(MOUSE_LEFT, false)
                    }
                    pointerId = null
                }
            }
        }
    }

    private fun handleGrabbedTouch(event: MotionEvent, mainIndex: Int) {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        if (MenuSetting.isDisableLeftTouch && event.getX(mainIndex) <= screenWidth / 2f) {
            // 屏蔽期间基线必须跟着手指走：之前只 return 不更新基线，手指从按钮区回到右半屏的
            // 第一帧会算出「整段跨屏位移」，视角一次猛跳（真机反馈的滑到按键就闪一下）。
            downX = event.getX(mainIndex)
            downY = event.getY(mainIndex)
            lastGrabbedMoveTime = System.currentTimeMillis()
            VerseLogger.debug("gLeftSkip x=${event.getX(mainIndex).toInt()} main=$mainIndex", "TouchDiag")
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentPointerID = event.getPointerId(mainIndex)
                downX = event.getX(mainIndex)
                downY = event.getY(mainIndex)
                downTime = System.currentTimeMillis()
                lastGrabbedMoveTime = downTime
                hasMoved = false
                handler.postDelayed(holdRunnable, longPressDelay())
                VerseLogger.debug("gDown cur=$currentPointerID dX=${downX.toInt()} dY=${downY.toInt()} t=$downTime holdScheduled", "TouchDiag")
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerCount = event.pointerCount
                val pointerIndex = event.findPointerIndex(currentPointerID)
                val now = System.currentTimeMillis()
                // 按钮遮挡守卫：指针全落按钮期间（toInput=false）滑动位移未被跟踪，
                // 重新进入时基线已过期 → 强制重置，避免一次巨 delta 视角跳屏
                val staleBaseline = now - lastGrabbedMoveTime > 150
                if (pointerIndex == -1 || lastPointerCount != pointerCount || !shouldBeDown || staleBaseline) {
                    VerseLogger.debug("gReset cur=$currentPointerID mainId=${event.getPointerId(mainIndex)} lastN=$lastPointerCount cnt=$pointerCount sb=$shouldBeDown idx=$pointerIndex stale=$staleBaseline", "TouchDiag")
                    shouldBeDown = true
                    currentPointerID = event.getPointerId(mainIndex)
                    downX = event.getX(mainIndex)
                    downY = event.getY(mainIndex)
                    lastGrabbedMoveTime = now
                    return
                }
                lastGrabbedMoveTime = now
                val newDownX = event.getX(pointerIndex)
                val newDownY = event.getY(pointerIndex)
                val scale = bridgeProvider()?.getScaleFactor()?.toFloat() ?: 1f
                val sensitivity = MenuSetting.mouseSensitivity.toFloat()
                val moveDx = (newDownX - downX) * sensitivity
                val moveDy = (newDownY - downY) * sensitivity
                // 抓取态一律用相对增量下发：陀螺仪在后台持续累加鼠标位置，
                // 若这里按「按下时坐标 + 手指位移」下发绝对坐标，会把陀螺仪位移覆盖掉 → 视角乱跳。
                org.lwjgl.glfw.CallbackBridge.sendCursorDelta(moveDx, moveDy)
                // 内部坐标同步（切回非抓取态时作为光标基准）
                pointerX += moveDx / scale
                pointerY += moveDy / scale
                if (kotlin.math.abs(newDownX - downX) > TAP_MAX_DIST
                    || kotlin.math.abs(newDownY - downY) > TAP_MAX_DIST
                ) {
                    // 发生滑动：标记 hasMoved 并彻底取消长按 —— 滑动转视角绝不应触发 BUILD/FIGHT 点击。
                    //   Verse 手动分流下误判率高，滑动过即取消更符合"滑动=转视角"预期。）
                    hasMoved = true
                }
                handler.removeCallbacks(holdRunnable)
                downX = newDownX
                downY = newDownY
                // delta/基线采样日志（独立节流，与 diagEvent 概要错开）
                if (now - lastMoveDeltaDiagTime >= 120) {
                    lastMoveDeltaDiagTime = now
                    VerseLogger.debug("gMove cur=$currentPointerID idx=$pointerIndex dX=${downX.toInt()} dY=${downY.toInt()} new=(${newDownX.toInt()},${newDownY.toInt()}) dlt=(${moveDx.toInt()},${moveDy.toInt()}) pX=${pointerX.toInt()} pY=${pointerY.toInt()} moved=$hasMoved sens=${MenuSetting.mouseSensitivity} scale=$scale", "TouchDiag")
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 新指针按下：旧基线（downX/downY/currentPointerID）必然失效，
                // 强制 next MOVE 走重置分支重新校准，避免旧基线导致巨 delta 视角跳屏
                shouldBeDown = false
                lastGrabbedMoveTime = 0L
                VerseLogger.debug("gPtrDown ev=${event.actionMasked} cnt=${event.pointerCount} main=$mainIndex mainId=${event.getPointerId(mainIndex)} forcedReset", "TouchDiag")
            }
            MotionEvent.ACTION_POINTER_UP -> {
                VerseLogger.debug("gIgnore ev=${event.actionMasked} cnt=${event.pointerCount} main=$mainIndex mainId=${event.getPointerId(mainIndex)}", "TouchDiag")
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                val isTap = !hasMoved
                    && System.currentTimeMillis() - downTime <= TAP_MAX_TIME
                    && kotlin.math.abs(event.getX(mainIndex) - downX) <= TAP_MAX_DIST
                    && kotlin.math.abs(event.getY(mainIndex) - downY) <= TAP_MAX_DIST
                VerseLogger.debug("gUp moved=$hasMoved dt=${System.currentTimeMillis() - downTime} tap=$isTap cancelL=$cancelMouseLeft cancelR=$cancelMouseRight", "TouchDiag")
                pointerId = null
                shouldBeDown = false
                currentPointerID = -1
                handler.removeCallbacks(holdRunnable)
                if (cancelMouseLeft) {
                    sendKeyEvent(MOUSE_LEFT, false)
                }
                if (cancelMouseRight) {
                    sendKeyEvent(MOUSE_RIGHT, false)
                }
                cancelMouseLeft = false
                cancelMouseRight = false
                // 仅未滑动过的手指才判定 tap：滑动转视角的手指抬起不算点击（否则视角跳转）。
                if (isTap) {
                    if (!MenuSetting.isDisableGesture) {
                        if (MenuSetting.isGestureControl) {
                            // 手势控制：单击发送「手势单击动作」对应的鼠标按键
                            val key = gestureMouseKey(MenuSetting.gestureTapMouseAction)
                            sendKeyEvent(key, true)
                            sendKeyEvent(key, false)
                        } else if (effectiveGestureMode() == GestureMode.BUILD) {
                            sendKeyEvent(MOUSE_RIGHT, true)
                            sendKeyEvent(MOUSE_RIGHT, false)
                        } else {
                            sendKeyEvent(MOUSE_LEFT, true)
                            sendKeyEvent(MOUSE_LEFT, false)
                        }
                    }
                }
            }
        }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val bridge = bridgeProvider() ?: return false
        if (isMouseSource(event)) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                sendKeyEvent(MOUSE_RIGHT, true)
                return true
            }
        }
        val targets = resolveTargetKeys(keyCode)
        if (targets.isNotEmpty()) {
            val keyChar = if (!isGrabbing) (event?.unicodeChar ?: 0) else 0
            targets.forEachIndexed { index, glfwKey ->
                bridge.pushEventKey(glfwKey, if (index == 0) keyChar else 0, true)
            }
            return true
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val bridge = bridgeProvider() ?: return false
        if (isMouseSource(event)) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                sendKeyEvent(MOUSE_RIGHT, false)
                return true
            }
        }
        val targets = resolveTargetKeys(keyCode)
        targets.forEach { glfwKey ->
            bridge.pushEventKey(glfwKey, 0, false)
        }
        return targets.isNotEmpty()
    }

    /**
     * 按键解析：键盘按键走原有键盘映射；手柄按键（键盘映射无法识别）再按
     * 「游戏内 / 菜单内」绑定表二次映射，返回该手柄按键绑定的全部目标键；
     * 未绑定、目标键不可映射或手柄总开关关闭时返回空数组。
     */
    private fun resolveTargetKeys(keyCode: Int): IntArray {
        val direct = androidKeyToGlfw(keyCode)
        if (direct != LwjglGlfwKeycode.GLFW_KEY_UNKNOWN) return intArrayOf(direct)
        val ctx = context ?: return IntArray(0)
        if (!GamepadBindingsRepository.isEnabled(ctx)) return IntArray(0)
        val mode = if (menuVisibleProvider()) {
            GamepadBindingsRepository.Mode.MENU
        } else {
            GamepadBindingsRepository.Mode.IN_GAME
        }
        return GamepadBindingsRepository.get(ctx, mode, keyCode)
            .map { androidKeyToGlfw(it) }
            .filter { it != LwjglGlfwKeycode.GLFW_KEY_UNKNOWN }
            .toIntArray()
    }

    private fun isMouseSource(event: KeyEvent?): Boolean {
        if (event?.device == null) return false
        val source = event.source
        return (source and InputDevice.SOURCE_MOUSE_RELATIVE == InputDevice.SOURCE_MOUSE_RELATIVE) ||
            (source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE)
    }

    /**
     * 手势被上层拦截（如双指下滑唤起控制面板）时复位当前输入状态，
     * 释放已下发的鼠标按键，避免游戏残留长按/右击状态。
     */
    fun cancelGesture() {
        handler.removeCallbacks(holdRunnable)
        handler.removeCallbacks(clickDownRunnable)
        handler.removeCallbacks(clickUpRunnable)
        runCatching {
            sendKeyEvent(MOUSE_LEFT, false)
            sendKeyEvent(MOUSE_RIGHT, false)
            sendKeyEvent(MOUSE_MIDDLE, false)
        }
        cancelMouseLeft = false
        cancelMouseRight = false
        shouldBeDown = false
        currentPointerID = -1
        pointerId = null
        downTime = 0L
    }

    fun androidKeyToGlfw(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> LwjglGlfwKeycode.GLFW_KEY_SPACE
            KeyEvent.KEYCODE_A -> LwjglGlfwKeycode.GLFW_KEY_A
            KeyEvent.KEYCODE_B -> LwjglGlfwKeycode.GLFW_KEY_B
            KeyEvent.KEYCODE_C -> LwjglGlfwKeycode.GLFW_KEY_C
            KeyEvent.KEYCODE_D -> LwjglGlfwKeycode.GLFW_KEY_D
            KeyEvent.KEYCODE_E -> LwjglGlfwKeycode.GLFW_KEY_E
            KeyEvent.KEYCODE_F -> LwjglGlfwKeycode.GLFW_KEY_F
            KeyEvent.KEYCODE_G -> LwjglGlfwKeycode.GLFW_KEY_G
            KeyEvent.KEYCODE_H -> LwjglGlfwKeycode.GLFW_KEY_H
            KeyEvent.KEYCODE_I -> LwjglGlfwKeycode.GLFW_KEY_I
            KeyEvent.KEYCODE_J -> LwjglGlfwKeycode.GLFW_KEY_J
            KeyEvent.KEYCODE_K -> LwjglGlfwKeycode.GLFW_KEY_K
            KeyEvent.KEYCODE_L -> LwjglGlfwKeycode.GLFW_KEY_L
            KeyEvent.KEYCODE_M -> LwjglGlfwKeycode.GLFW_KEY_M
            KeyEvent.KEYCODE_N -> LwjglGlfwKeycode.GLFW_KEY_N
            KeyEvent.KEYCODE_O -> LwjglGlfwKeycode.GLFW_KEY_O
            KeyEvent.KEYCODE_P -> LwjglGlfwKeycode.GLFW_KEY_P
            KeyEvent.KEYCODE_Q -> LwjglGlfwKeycode.GLFW_KEY_Q
            KeyEvent.KEYCODE_R -> LwjglGlfwKeycode.GLFW_KEY_R
            KeyEvent.KEYCODE_S -> LwjglGlfwKeycode.GLFW_KEY_S
            KeyEvent.KEYCODE_T -> LwjglGlfwKeycode.GLFW_KEY_T
            KeyEvent.KEYCODE_U -> LwjglGlfwKeycode.GLFW_KEY_U
            KeyEvent.KEYCODE_V -> LwjglGlfwKeycode.GLFW_KEY_V
            KeyEvent.KEYCODE_W -> LwjglGlfwKeycode.GLFW_KEY_W
            KeyEvent.KEYCODE_X -> LwjglGlfwKeycode.GLFW_KEY_X
            KeyEvent.KEYCODE_Y -> LwjglGlfwKeycode.GLFW_KEY_Y
            KeyEvent.KEYCODE_Z -> LwjglGlfwKeycode.GLFW_KEY_Z
            KeyEvent.KEYCODE_0 -> LwjglGlfwKeycode.GLFW_KEY_0
            KeyEvent.KEYCODE_1 -> LwjglGlfwKeycode.GLFW_KEY_1
            KeyEvent.KEYCODE_2 -> LwjglGlfwKeycode.GLFW_KEY_2
            KeyEvent.KEYCODE_3 -> LwjglGlfwKeycode.GLFW_KEY_3
            KeyEvent.KEYCODE_4 -> LwjglGlfwKeycode.GLFW_KEY_4
            KeyEvent.KEYCODE_5 -> LwjglGlfwKeycode.GLFW_KEY_5
            KeyEvent.KEYCODE_6 -> LwjglGlfwKeycode.GLFW_KEY_6
            KeyEvent.KEYCODE_7 -> LwjglGlfwKeycode.GLFW_KEY_7
            KeyEvent.KEYCODE_8 -> LwjglGlfwKeycode.GLFW_KEY_8
            KeyEvent.KEYCODE_9 -> LwjglGlfwKeycode.GLFW_KEY_9
            KeyEvent.KEYCODE_ENTER -> LwjglGlfwKeycode.GLFW_KEY_ENTER
            KeyEvent.KEYCODE_TAB -> LwjglGlfwKeycode.GLFW_KEY_TAB
            KeyEvent.KEYCODE_DEL -> LwjglGlfwKeycode.GLFW_KEY_BACKSPACE
            KeyEvent.KEYCODE_ESCAPE -> LwjglGlfwKeycode.GLFW_KEY_ESCAPE
            KeyEvent.KEYCODE_SHIFT_LEFT -> LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT
            KeyEvent.KEYCODE_SHIFT_RIGHT -> LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT
            KeyEvent.KEYCODE_CTRL_LEFT -> LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL
            KeyEvent.KEYCODE_CTRL_RIGHT -> LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL
            KeyEvent.KEYCODE_ALT_LEFT -> LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT
            KeyEvent.KEYCODE_ALT_RIGHT -> LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT
            KeyEvent.KEYCODE_CAPS_LOCK -> LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK
            KeyEvent.KEYCODE_NUM_LOCK -> LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK
            KeyEvent.KEYCODE_SCROLL_LOCK -> LwjglGlfwKeycode.GLFW_KEY_SCROLL_LOCK
            KeyEvent.KEYCODE_F1 -> LwjglGlfwKeycode.GLFW_KEY_F1
            KeyEvent.KEYCODE_F2 -> LwjglGlfwKeycode.GLFW_KEY_F2
            KeyEvent.KEYCODE_F3 -> LwjglGlfwKeycode.GLFW_KEY_F3
            KeyEvent.KEYCODE_F4 -> LwjglGlfwKeycode.GLFW_KEY_F4
            KeyEvent.KEYCODE_F5 -> LwjglGlfwKeycode.GLFW_KEY_F5
            KeyEvent.KEYCODE_F6 -> LwjglGlfwKeycode.GLFW_KEY_F6
            KeyEvent.KEYCODE_F7 -> LwjglGlfwKeycode.GLFW_KEY_F7
            KeyEvent.KEYCODE_F8 -> LwjglGlfwKeycode.GLFW_KEY_F8
            KeyEvent.KEYCODE_F9 -> LwjglGlfwKeycode.GLFW_KEY_F9
            KeyEvent.KEYCODE_F10 -> LwjglGlfwKeycode.GLFW_KEY_F10
            KeyEvent.KEYCODE_F11 -> LwjglGlfwKeycode.GLFW_KEY_F11
            KeyEvent.KEYCODE_F12 -> LwjglGlfwKeycode.GLFW_KEY_F12
            KeyEvent.KEYCODE_DPAD_UP -> LwjglGlfwKeycode.GLFW_KEY_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> LwjglGlfwKeycode.GLFW_KEY_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> LwjglGlfwKeycode.GLFW_KEY_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> LwjglGlfwKeycode.GLFW_KEY_RIGHT
            KeyEvent.KEYCODE_INSERT -> LwjglGlfwKeycode.GLFW_KEY_INSERT
            KeyEvent.KEYCODE_FORWARD_DEL -> LwjglGlfwKeycode.GLFW_KEY_DELETE
            KeyEvent.KEYCODE_MOVE_HOME -> LwjglGlfwKeycode.GLFW_KEY_HOME
            KeyEvent.KEYCODE_MOVE_END -> LwjglGlfwKeycode.GLFW_KEY_END
            KeyEvent.KEYCODE_PAGE_UP -> LwjglGlfwKeycode.GLFW_KEY_PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> LwjglGlfwKeycode.GLFW_KEY_PAGE_DOWN
            KeyEvent.KEYCODE_APOSTROPHE -> LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE
            KeyEvent.KEYCODE_COMMA -> LwjglGlfwKeycode.GLFW_KEY_COMMA
            KeyEvent.KEYCODE_MINUS -> LwjglGlfwKeycode.GLFW_KEY_MINUS
            KeyEvent.KEYCODE_PERIOD -> LwjglGlfwKeycode.GLFW_KEY_PERIOD
            KeyEvent.KEYCODE_SLASH -> LwjglGlfwKeycode.GLFW_KEY_SLASH
            KeyEvent.KEYCODE_SEMICOLON -> LwjglGlfwKeycode.GLFW_KEY_SEMICOLON
            KeyEvent.KEYCODE_EQUALS -> LwjglGlfwKeycode.GLFW_KEY_EQUAL
            KeyEvent.KEYCODE_LEFT_BRACKET -> LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET
            KeyEvent.KEYCODE_RIGHT_BRACKET -> LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET
            KeyEvent.KEYCODE_BACKSLASH -> LwjglGlfwKeycode.GLFW_KEY_BACKSLASH
            KeyEvent.KEYCODE_GRAVE -> LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT
            else -> LwjglGlfwKeycode.GLFW_KEY_UNKNOWN
        }
    }
}
