package com.verse.explorer.app.ui.game

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.MotionEvent
import android.util.Log
import com.verse.explorer.launcher.bridge.CallbackBridge
import com.verse.explorer.launcher.keycodes.LwjglGlfwKeycode
import com.tungsten.fclauncher.bridge.FCLBridge

@SuppressLint("ClickableViewAccessibility")
class GameInputHandler(private val bridgeProvider: () -> FCLBridge?) {

    private var isGrabbing = false
    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isLongPress = false
    private val LONG_PRESS_THRESHOLD = 500L
    private val MOVE_THRESHOLD = 30f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    fun handleTouchEvent(event: MotionEvent) {
        val bridge = bridgeProvider() ?: run {
            Log.d("VerseX", "[Input] handleTouchEvent: bridge is null")
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTime = System.currentTimeMillis()
                touchDownX = event.x
                touchDownY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isLongPress = false
                val x = event.x.toInt()
                val y = event.y.toInt()
                Log.d("VerseX", "[Input] DOWN ($x,$y) grabbing=$isGrabbing")
                // 先移动鼠标指针，等 1 帧让 MC GUI hover 状态确立后再按左键。
                // 与 Pojav TouchEventProcessor 的点击时序一致（cursor pos 先行，button 后行）。
                bridge.pushEventPointer(x, y)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_1, true)
                }, 50)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val px = event.getX(pointerIndex).toInt()
                val py = event.getY(pointerIndex).toInt()
                Log.d("VerseX", "[Input] PTR_DOWN ($px,$py)")
                bridge.pushEventPointer(px, py)
                bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_3, true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                if (isGrabbing && !isLongPress && System.currentTimeMillis() - touchDownTime > LONG_PRESS_THRESHOLD
                    && (dx * dx + dy * dy) < MOVE_THRESHOLD * MOVE_THRESHOLD) {
                    isLongPress = true
                    Log.d("VerseX", "[Input] LONG_PRESS -> RIGHT_DOWN")
                    bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_1, false)
                    bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_2, true)
                }
                if (isGrabbing) {
                    val deltaX = (event.x - lastTouchX).toInt()
                    val deltaY = (event.y - lastTouchY).toInt()
                    lastTouchX = event.x
                    lastTouchY = event.y
                    Log.d("VerseX", "[Input] MOVE grabbing d($deltaX,$deltaY)")
                    CallbackBridge.sendCursorDelta(deltaX.toDouble(), deltaY.toDouble())
                } else {
                    Log.d("VerseX", "[Input] MOVE (${event.x.toInt()},${event.y.toInt()})")
                    bridge.pushEventPointer(event.x.toInt(), event.y.toInt())
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isGrabbing) {
                    bridge.pushEventPointer(event.x.toInt(), event.y.toInt())
                }
                // 与 DOWN 的 50ms 延迟对齐，保证 LEFT_UP 在 LEFT_DOWN 之后到达（click 顺序）
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isLongPress) {
                        Log.d("VerseX", "[Input] UP after LONG_PRESS -> RIGHT_UP")
                        bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_2, false)
                    } else {
                        Log.d("VerseX", "[Input] UP -> LEFT_UP")
                        bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_1, false)
                    }
                    isLongPress = false
                }, 80)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val upIndex = event.actionIndex
                val upX = event.getX(upIndex).toInt()
                val upY = event.getY(upIndex).toInt()
                Log.d("VerseX", "[Input] PTR_UP ($upX,$upY)")
                bridge.pushEventPointer(upX, upY)
                bridge.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_3, false)
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

    fun setCursorMode(mode: Int) {
        isGrabbing = mode == FCLBridge.CursorDisabled
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
