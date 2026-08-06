package com.verse.explorer.launcher.bridge

import android.view.Surface
import com.tungsten.fclauncher.bridge.FCLBridge

object CallbackBridge {

    const val EVENT_TYPE_CHAR = 1000
    const val EVENT_TYPE_CHAR_MODS = 1001
    const val EVENT_TYPE_CURSOR_ENTER = 1002
    const val EVENT_TYPE_CURSOR_POS = 1003
    const val EVENT_TYPE_FRAMEBUFFER_SIZE = 1004
    const val EVENT_TYPE_KEY = 1005
    const val EVENT_TYPE_MOUSE_BUTTON = 1006
    const val EVENT_TYPE_SCROLL = 1007
    const val EVENT_TYPE_WINDOW_SIZE = 1008

    const val ANDROID_TYPE_GRAB_STATE = 0

    private var fclBridge: FCLBridge? = null
    private var inputReady = false
    private var useInputStackQueue = false

    private val pressedKeys = mutableSetOf<Int>()
    private var currentMods = 0

    private var windowWidth = FCLBridge.DEFAULT_WIDTH
    private var windowHeight = FCLBridge.DEFAULT_HEIGHT
    private var isGrabbing = false

    @JvmStatic
    fun setFCLBridge(bridge: FCLBridge?) {
        fclBridge = bridge
    }

    @JvmStatic
    fun setUseInputStackQueue(use: Boolean) {
        useInputStackQueue = use
        nativeSetUseInputStackQueue(use)
    }

    @JvmStatic
    fun setupBridgeWindow(surface: Surface) {
        nativeSetupBridgeWindow(surface)
    }

    @JvmStatic
    fun sendCursorPos(x: Double, y: Double) {
        if (!inputReady) return
        nativeSendData(false, EVENT_TYPE_CURSOR_POS, x.toInt(), y.toInt(), 0, 0)
    }

    @JvmStatic
    fun sendCursorDelta(dx: Double, dy: Double) {
        if (!inputReady) return
        nativeSendData(false, 1009, dx.toInt(), dy.toInt(), 0, 0)
    }

    @JvmStatic
    fun sendMouseButton(button: Int, pressed: Boolean) {
        if (!inputReady) return
        nativeSendData(false, EVENT_TYPE_MOUSE_BUTTON, button, if (pressed) 1 else 0, 0, 0)
    }

    @JvmStatic
    fun sendKeycode(keycode: Int, keyChar: Char, scancode: Int, mods: Int, pressed: Boolean) {
        if (!inputReady) return
        nativeSendData(false, EVENT_TYPE_KEY, keyChar.code, keycode, if (pressed) 1 else 0, mods)
    }

    @JvmStatic
    fun sendChar(keyChar: Char, mods: Int) {
        if (!inputReady) return
        nativeSendData(false, EVENT_TYPE_CHAR, keyChar.code, 0, 0, mods)
    }

    @JvmStatic
    fun sendScroll(xOffset: Double, yOffset: Double) {
        if (!inputReady) return
        nativeSendData(false, EVENT_TYPE_SCROLL, (xOffset * 1000).toInt(), (yOffset * 1000).toInt(), 0, 0)
    }

    @JvmStatic
    fun sendUpdateWindowSize(width: Int, height: Int) {
        windowWidth = width
        windowHeight = height
        if (!inputReady) return
        nativeSendData(false, EVENT_TYPE_WINDOW_SIZE, width, height, 0, 0)
    }

    @JvmStatic
    fun nativeSetWindowAttrib(attrib: Int, value: Int) {
        nativeSetWindowAttribNative(attrib, value)
    }

    @JvmStatic
    fun getCurrentMods(): Int = currentMods

    @JvmStatic
    fun setModifiers(keycode: Int, pressed: Boolean) {
        val modBit = when (keycode) {
            340 -> 1
            344 -> 1
            341 -> 2
            345 -> 2
            342 -> 4
            346 -> 4
            343 -> 8
            347 -> 8
            else -> 0
        }
        if (modBit != 0) {
            if (pressed) currentMods = currentMods or modBit
            else currentMods = currentMods and modBit.inv()
        }
    }

    @JvmStatic
    fun getFps(): Int {
        return try { nativeGetFps() } catch (_: Exception) { 0 }
    }

    private external fun nativeSetupBridgeWindow(surface: Surface)
    private external fun nativeSendData(isAndroid: Boolean, type: Int, i1: Int, i2: Int, i3: Int, i4: Int)
    private external fun nativeSetWindowAttribNative(attrib: Int, value: Int)
    private external fun nativeGetFps(): Int
    private external fun nativeSetUseInputStackQueue(use: Boolean)

    @JvmStatic
    fun nativeSetInputReady(ready: Boolean): Boolean {
        inputReady = ready
        if (!ready) {
            pressedKeys.clear()
            currentMods = 0
            isGrabbing = false
        }
        return true
    }

    @JvmStatic
    fun nativeSetGrabbing(grab: Boolean) {
        isGrabbing = grab
        fclBridge?.setCursorMode(if (grab) FCLBridge.CursorDisabled else FCLBridge.CursorEnabled)
    }

    @JvmStatic
    fun nativeClipboard(action: Int, copy: ByteArray?): String? {
        if (action == 1 && copy != null) {
            val text = String(copy, Charsets.UTF_8)
            fclBridge?.setPrimaryClipString(text)
            return null
        }
        return fclBridge?.getPrimaryClipString()
    }
}
