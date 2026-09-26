package com.verse.explorer.launcher.bridge

import android.view.Surface
import org.lwjgl.glfw.CallbackBridge as GlfwCallbackBridge

/**
 * Verse UI 输入桥接层。所有输入事件最终都由 FCL 原版 org.lwjgl.glfw.CallbackBridge 的 JNI
 * 方法转发给 JVM 侧（libpojavexec.so），方法签名与 Verse 旧实现保持一致以最小化 UI 改动。
 */
object CallbackBridge {

    private var fclBridge: com.tungsten.fclauncher.bridge.FCLBridge? = null
    private var inputReady = false
    private var absoluteMouseX = 0f
    private var absoluteMouseY = 0f

    @JvmStatic
    fun setFCLBridge(bridge: com.tungsten.fclauncher.bridge.FCLBridge?) {
        fclBridge = bridge
        GlfwCallbackBridge.setFCLBridge(bridge)
    }

    @JvmStatic
    fun setUseInputStackQueue(use: Boolean) {
        GlfwCallbackBridge.nativeSetUseInputStackQueue(use)
    }

    @JvmStatic
    fun setupBridgeWindow(surface: Surface) {
        // 为兼容需求保留文件指针记录能力；FCL 原版通过类型安全的 surface 实例完成注入。
        GlfwCallbackBridge.setupBridgeWindow(surface)
    }

    @JvmStatic
    fun sendCursorPos(x: Double, y: Double) {
        absoluteMouseX = x.toFloat()
        absoluteMouseY = y.toFloat()
        GlfwCallbackBridge.sendCursorPos(absoluteMouseX, absoluteMouseY)
    }

    @JvmStatic
    fun sendCursorDelta(dx: Double, dy: Double) {
        absoluteMouseX += dx.toFloat()
        absoluteMouseY += dy.toFloat()
        GlfwCallbackBridge.sendCursorPos(absoluteMouseX, absoluteMouseY)
    }

    @JvmStatic
    fun sendMouseButton(button: Int, pressed: Boolean) {
        android.util.Log.d("VerseX", "[Bridge] sendMouseButton($button,$pressed)")
        GlfwCallbackBridge.sendMouseButton(button, pressed)
    }

    @JvmStatic
    fun sendKeycode(keycode: Int, keyChar: Char, scancode: Int, mods: Int, pressed: Boolean) {
        GlfwCallbackBridge.sendKeycode(keycode, keyChar, scancode, mods, pressed)
    }

    @JvmStatic
    fun sendChar(keyChar: Char, mods: Int) {
        GlfwCallbackBridge.sendChar(keyChar, mods)
    }

    @JvmStatic
    fun sendScroll(xOffset: Double, yOffset: Double) {
        GlfwCallbackBridge.sendScroll(xOffset, yOffset)
    }

    @JvmStatic
    fun sendUpdateWindowSize(width: Int, height: Int) {
        GlfwCallbackBridge.sendUpdateWindowSize(width, height)
    }

    @JvmStatic
    fun nativeSetWindowAttrib(attrib: Int, value: Int) {
        GlfwCallbackBridge.nativeSetWindowAttrib(attrib, value)
    }

    @JvmStatic
    fun getCurrentMods(): Int = GlfwCallbackBridge.getCurrentMods()

    @JvmStatic
    fun setModifiers(keycode: Int, pressed: Boolean) {
        GlfwCallbackBridge.setModifiers(keycode, pressed)
    }

    @JvmStatic
    fun getFps(): Int {
        return try {
            GlfwCallbackBridge.getFps()
        } catch (_: Throwable) {
            0
        }
    }

    @JvmStatic
    fun nativeSetInputReady(ready: Boolean): Boolean {
        // 转发到 FCL/verseexec 原生桥设置 isInputReady。
        // 若不设置，native 层 critical_send_* 会因 isInputReady=false 丢弃全部输入事件。
        inputReady = ready
        return try {
            GlfwCallbackBridge.nativeSetInputReady(ready)
        } catch (_: Throwable) {
            true
        }
    }

    @JvmStatic
    fun nativeSetGrabbing(grab: Boolean) {
        fclBridge?.setCursorMode(if (grab) com.tungsten.fclauncher.bridge.FCLBridge.CursorDisabled else com.tungsten.fclauncher.bridge.FCLBridge.CursorEnabled)
    }

    @JvmStatic
    fun nativeClipboard(action: Int, copy: ByteArray?): String? {
        val text = copy?.let { String(it, Charsets.UTF_8) }
        return GlfwCallbackBridge.accessAndroidClipboard(action, text)
    }
}