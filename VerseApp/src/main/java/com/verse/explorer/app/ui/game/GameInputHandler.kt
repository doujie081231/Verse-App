package com.verse.explorer.app.ui.game

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
    private val captureViewProvider: () -> android.view.View? = { null }
) {

    companion object {
        const val MOUSE_LEFT = 1000
        const val MOUSE_MIDDLE = 1001
        const val MOUSE_RIGHT = 1002
        const val MOUSE_SCROLL_UP = 1003
        const val MOUSE_SCROLL_DOWN = 1004
        private const val TAG = "GameInputHandler"
        private const val HOLD_DELAY = 400L
        private const val CLICK_DELAY = 33L
        private const val TAP_MAX_TIME = 100L
        private const val TAP_MAX_DIST = 10f
    }

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
            if (effectiveGestureMode() == GestureMode.BUILD) {
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

    /** 捕获同步：物理鼠标模式下抓取态用相对鼠标
     * （需指针捕获），GUI 态用屏幕绝对坐标（释放捕获）；非物理鼠标模式不捕获，
     * 由 GameActivity.onWindowFocusChanged 在挂载时按需请求。 */
    fun syncCapturedPointer() {
        val view = captureViewProvider()
        if (MenuSetting.isPhysicalMouseMode) {
            if (isGrabbing) {
                view?.requestFocus()
                view?.requestPointerCapture()
            } else {
                view?.releasePointerCapture()
                view?.clearFocus()
            }
        } else {
            view?.releasePointerCapture()
        }
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
        if (event.action == MotionEvent.ACTION_MOVE) {
            var tX = event.x
            var tY = event.y
            val historySize = event.historySize
            for (i in 0 until historySize) {
                tX += event.getHistoricalX(i)
                tY += event.getHistoricalY(i)
            }
            tX *= MenuSetting.mouseSensitivity.toFloat()
            tY *= MenuSetting.mouseSensitivity.toFloat()
            val deltaX = tX.toInt()
            val deltaY = tY.toInt()
            if (!isGrabbing) {
                val targetX = cursorX + deltaX * MenuSetting.mouseSensitivityCursor.toFloat()
                val targetY = cursorY + deltaY * MenuSetting.mouseSensitivityCursor.toFloat()
                setPointer(
                    targetX.coerceIn(0f, screenWidth.toFloat()),
                    targetY.coerceIn(0f, screenHeight.toFloat())
                )
            } else {
                setPointer(pointerX + deltaX, pointerY + deltaY)
            }
        }
        return handleExternalMouseEvent(event)
    }

    /** GameMenu.setOnGenericMotionListener 的移植：物理鼠标（physicalMouseMode）hover/按钮/滚轮 */
    fun handleGenericMotion(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) && MenuSetting.isPhysicalMouseMode) {
            if (!isGrabbing && event.action == MotionEvent.ACTION_HOVER_MOVE) {
                setPointer(event.rawX, event.rawY)
                return true
            }
            return handleExternalMouseEvent(event)
        }
        return false
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
            VerseLogger.debug("gLeftSkip x=${event.getX(mainIndex).toInt()} main=$mainIndex", "TouchDiag")
            return
        }
        initialX = pointerX
        initialY = pointerY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentPointerID = event.getPointerId(mainIndex)
                downX = event.getX(mainIndex)
                downY = event.getY(mainIndex)
                downTime = System.currentTimeMillis()
                lastGrabbedMoveTime = downTime
                hasMoved = false
                handler.postDelayed(holdRunnable, HOLD_DELAY)
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
                val deltaX = ((newDownX - downX) * MenuSetting.mouseSensitivity.toFloat() / scale).toInt()
                val deltaY = ((newDownY - downY) * MenuSetting.mouseSensitivity.toFloat() / scale).toInt()
                if (MenuSetting.isEnableGyroscope) {
                    pointerX = initialX + deltaX
                    pointerY = initialY + deltaY
                } else {
                    setPointer(initialX + deltaX, initialY + deltaY)
                }
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
                    VerseLogger.debug("gMove cur=$currentPointerID idx=$pointerIndex dX=${downX.toInt()} dY=${downY.toInt()} new=(${newDownX.toInt()},${newDownY.toInt()}) dlt=($deltaX,$deltaY) pX=${pointerX.toInt()} pY=${pointerY.toInt()} moved=$hasMoved sens=${MenuSetting.mouseSensitivity} scale=$scale", "TouchDiag")
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
                        if (effectiveGestureMode() == GestureMode.BUILD) {
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
        val glfwKey = androidKeyToGlfw(keyCode)
        if (glfwKey != LwjglGlfwKeycode.GLFW_KEY_UNKNOWN) {
            val keyChar = if (!isGrabbing) (event?.unicodeChar ?: 0) else 0
            bridge.pushEventKey(glfwKey, keyChar, true)
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
        val glfwKey = androidKeyToGlfw(keyCode)
        if (glfwKey != LwjglGlfwKeycode.GLFW_KEY_UNKNOWN) {
            bridge.pushEventKey(glfwKey, 0, false)
            return true
        }
        return false
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
