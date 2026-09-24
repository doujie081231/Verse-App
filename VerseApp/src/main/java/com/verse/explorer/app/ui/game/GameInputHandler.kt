package com.verse.explorer.app.ui.game

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.verse.explorer.launcher.bridge.CallbackBridge
import com.verse.explorer.launcher.keycodes.LwjglGlfwKeycode
import com.tungsten.fcl.control.GestureMode
import com.tungsten.fcl.control.MouseMoveMode
import com.tungsten.fcl.setting.MenuSetting
import com.tungsten.fclauncher.bridge.FCLBridge

/**
 * 游戏输入处理：行为全部照搬 FCL 的 FCLInput.java + control/view/TouchPad.java
 * （CursorDisabled=抓取态 / CursorEnabled=GUI 态 两条触摸分支，400ms 长按 BUILD/FIGHT 手势，
 * CLICK/SLIDE 鼠标移动模式，物理鼠标 external mouse 事件，左半屏禁用触控）。
 * 不新增私有逻辑；设置项从 MenuSetting（menu_setting.json）读取。
 */
@SuppressLint("ClickableViewAccessibility")
class GameInputHandler(
    private val bridgeProvider: () -> FCLBridge?,
    private val context: Context? = null,
    private val hitResultTypeProvider: () -> Int = { FCLBridge.HIT_RESULT_TYPE_UNKNOWN }
) {

    // ===== FCLInput 常量（MOUSE_MAP 与 FCLInput.java 一致） =====
    companion object {
        const val MOUSE_LEFT = 1000
        const val MOUSE_MIDDLE = 1001
        const val MOUSE_RIGHT = 1002
        const val MOUSE_SCROLL_UP = 1003
        const val MOUSE_SCROLL_DOWN = 1004
        private const val TAG = "GameInputHandler"
        // 与 FCL TouchPad 的长按阈值/点击判定一致
        private const val HOLD_DELAY = 400L
        private const val CLICK_DELAY = 33L
        private const val TAP_MAX_TIME = 100L
        private const val TAP_MAX_DIST = 10f
    }

    private val handler = Handler(Looper.getMainLooper())

    private var isGrabbing = false

    // ===== 光标/指针状态（FCL GameMenu.cursorX/cursorY + pointerX/pointerY 的对应） =====
    internal var cursorX = 0f
    internal var cursorY = 0f
    private var pointerX = 0f
    private var pointerY = 0f
    private var pointerId: String? = null

    /** GUI 态光标移动回调（FCL CursorView.setX/setY 的对应，由 GameActivity 接可见光标） */
    internal var cursorMoveListener: ((Float, Float) -> Unit)? = null

    // ===== FCL TouchPad 抓取态状态 =====
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

    private val clickDownRunnable = Runnable { sendKeyEvent(MOUSE_LEFT, true) }
    private val clickUpRunnable = Runnable { sendKeyEvent(MOUSE_LEFT, false) }

    private val screenWidth: Int
        get() = context?.resources?.displayMetrics?.widthPixels ?: 854
    private val screenHeight: Int
        get() = context?.resources?.displayMetrics?.heightPixels ?: 480

    fun isGrabbing(): Boolean = isGrabbing

    fun setCursorMode(mode: Int) {
        isGrabbing = mode == FCLBridge.CursorDisabled
    }

    /** FCL TouchPad.getGestureMode()：BE 手势禁用时用设置值，否则按命中结果自动切换 BUILD/FIGHT */
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

    // ===== FCLInput.setPointer：推送指针时按 scaleFactor 缩放（与 FCLInput.java 一致） =====
    private fun setPointer(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        // FCL：光标坐标仅在 CursorEnabled（GUI 态）更新，抓取态只维护指针坐标
        if (!isGrabbing) {
            cursorX = x
            cursorY = y
            cursorMoveListener?.invoke(x, y)
        }
        val bridge = bridgeProvider() ?: return
        val scale = bridge.getScaleFactor().toFloat()
        bridge.pushEventPointer((x * scale).toInt(), (y * scale).toInt())
    }

    /** FCLInput.sendKeyEvent：鼠标键走 pushEventMouseButton（含滚轮映射），其余走 pushEventKey */
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

    /** FCLInput.handleExternalMouseEvent：外接鼠标按钮/滚轮事件 */
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

    /** FCLInput.handleMouse：抓取态下捕获的相对鼠标移动（指针捕获模式），含灵敏度 */
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

    fun handleTouchEvent(event: MotionEvent) {
        val bridge = bridgeProvider() ?: run {
            Log.d("VerseX", "[Input] handleTouchEvent: bridge is null")
            return
        }
        if (isGrabbing) {
            handleGrabbedTouch(event)
        } else {
            handleGuiTouch(event)
        }
        if (isGrabbing) {
            lastPointerCount = event.pointerCount
        }
    }

    // ===== FCL TouchPad CursorEnabled（GUI 态）分支 =====
    private fun handleGuiTouch(event: MotionEvent) {
        val bridge = bridgeProvider() ?: return
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (event.action == MotionEvent.ACTION_MOVE) {
                setPointer(event.rawX, event.rawY)
            }
            // 防止被外接鼠标触发触摸板
            return
        }
        if (MenuSetting.mouseMoveMode == MouseMoveMode.CLICK.id) {
            setPointer(event.x, event.y)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> handler.postDelayed(clickDownRunnable, CLICK_DELAY)
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(clickDownRunnable)
                    handler.postDelayed(clickUpRunnable, CLICK_DELAY)
                }
            }
        } else {
            // MouseMoveMode.SLIDE
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = System.currentTimeMillis()
                    initialX = cursorX
                    initialY = cursorY
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.x - downX) * MenuSetting.mouseSensitivityCursor.toFloat()
                    val deltaY = (event.y - downY) * MenuSetting.mouseSensitivityCursor.toFloat()
                    val targetX = (initialX + deltaX).coerceIn(0f, screenWidth.toFloat())
                    val targetY = (initialY + deltaY).coerceIn(0f, screenHeight.toFloat())
                    setPointer(targetX, targetY)
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    if (System.currentTimeMillis() - downTime <= TAP_MAX_TIME
                        && kotlin.math.abs(event.x - downX) <= TAP_MAX_DIST
                        && kotlin.math.abs(event.y - downY) <= TAP_MAX_DIST
                    ) {
                        sendKeyEvent(MOUSE_LEFT, true)
                        sendKeyEvent(MOUSE_LEFT, false)
                    }
                    pointerId = null
                }
            }
        }
    }

    // ===== FCL TouchPad CursorDisabled（抓取/游戏内）分支 =====
    private fun handleGrabbedTouch(event: MotionEvent) {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        if (MenuSetting.isDisableLeftTouch && event.x <= screenWidth / 2f) {
            return
        }
        initialX = pointerX
        initialY = pointerY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentPointerID = event.getPointerId(0)
                bridgeProvider()?.refreshHitResultType()
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                handler.postDelayed(holdRunnable, HOLD_DELAY)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerCount = event.pointerCount
                val pointerIndex = event.findPointerIndex(currentPointerID)
                if (pointerIndex == -1 || lastPointerCount != pointerCount || !shouldBeDown) {
                    shouldBeDown = true
                    currentPointerID = event.getPointerId(0)
                    downX = event.x
                    downY = event.y
                    return
                }
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
                if ((kotlin.math.abs(deltaX) > 1 || kotlin.math.abs(deltaY) > 1)
                    && System.currentTimeMillis() - downTime < HOLD_DELAY
                ) {
                    handler.removeCallbacks(holdRunnable)
                }
                downX = newDownX
                downY = newDownY
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
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
                if (System.currentTimeMillis() - downTime <= TAP_MAX_TIME
                    && kotlin.math.abs(event.x - downX) <= TAP_MAX_DIST
                    && kotlin.math.abs(event.y - downY) <= TAP_MAX_DIST
                ) {
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
        val glfwKey = androidKeyToGlfw(keyCode)
        if (glfwKey != LwjglGlfwKeycode.GLFW_KEY_UNKNOWN) {
            bridge.pushEventKey(glfwKey, event?.unicodeChar ?: 0, true)
            return true
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val bridge = bridgeProvider() ?: return false
        val glfwKey = androidKeyToGlfw(keyCode)
        if (glfwKey != LwjglGlfwKeycode.GLFW_KEY_UNKNOWN) {
            bridge.pushEventKey(glfwKey, event?.unicodeChar ?: 0, false)
            return true
        }
        return false
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
