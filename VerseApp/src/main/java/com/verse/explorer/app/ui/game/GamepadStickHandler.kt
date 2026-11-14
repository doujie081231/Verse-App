package com.verse.explorer.app.ui.game

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 手柄摇杆 → 键鼠映射。
 *
 * 背景：原先只有 `sGamepadDirectInput`（GLFW 直通）这一条路，而这个开关默认关闭，
 * 于是手柄摇杆在游戏里完全没有响应，玩家看到的就是「摇杆没法用 / 校准不了」。
 *
 * 这里补一条不依赖直通的软件映射：
 *  - 左摇杆 → W/A/S/D（带死区与归一化）
 *  - 右摇杆 → 视角增量（等价于鼠标相对移动）
 *
 * 死区处理参考现代启动器的通用做法：先按设备上报的 flat 范围取死区，
 * 再把「死区外」的区间重新拉伸到 0..1。不做这步的话，摇杆越过死区那一刻会突然跳一大格，
 * 手感上就是"推不动，一推就冲出去"，玩家会以为摇杆没校准。
 */
class GamepadStickHandler(private val input: GameInputHandler) {

    companion object {
        /** 摇杆死区下限（设备没上报 flat 时使用），0.15 ≈ 手游摇杆的常见手感 */
        private const val DEFAULT_DEADZONE = 0.15f

        /** 方向键触发阈值：超过它才按下 WASD，回落到它的一半才松开，避免临界抖动 */
        private const val MOVE_ON = 0.45f
        private const val MOVE_OFF = 0.22f

        /** 右摇杆灵敏度：一圈推满约等于鼠标移动这么多像素 */
        private const val LOOK_SENSITIVITY = 26f
    }

    private var keyW = false
    private var keyA = false
    private var keyS = false
    private var keyD = false

    /** 返回 true 表示这次事件已被摇杆消费 */
    fun handle(event: MotionEvent): Boolean {
        val src = event.source
        if ((src and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK &&
            (src and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
        ) {
            return false
        }
        if (event.action != MotionEvent.ACTION_MOVE) return false

        val (lx, ly) = normalized(event, MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
        val (rx, ry) = normalized(event, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)

        applyMove(lx, ly)

        if (abs(rx) > 0.01f || abs(ry) > 0.01f) {
            // 摇杆上推屏幕向上看（与鼠标移动方向一致：向下为正）
            input.sendMouseDelta(rx * LOOK_SENSITIVITY, ry * LOOK_SENSITIVITY)
        }

        return abs(lx) > 0.01f || abs(ly) > 0.01f || abs(rx) > 0.01f || abs(ry) > 0.01f
    }

    /** 手柄断开或退到后台时，别把 WASD 卡在按下状态 */
    fun releaseAll() {
        setKey(KeyEvent.KEYCODE_W, false)
        setKey(KeyEvent.KEYCODE_A, false)
        setKey(KeyEvent.KEYCODE_S, false)
        setKey(KeyEvent.KEYCODE_D, false)
    }

    /**
     * 取一根摇杆的 X/Y，扣掉死区并把剩余区间拉伸到 0..1。
     * 两个轴分开钳制，斜向推满时不会出现「一个轴先到顶、另一个轴还在半路」的割裂感。
     */
    private fun normalized(event: MotionEvent, axisX: Int, axisY: Int): Pair<Float, Float> {
        var x = event.getAxisValue(axisX)
        var y = event.getAxisValue(axisY)

        val deadzone = deadzoneOf(event, axisX)
        val magnitude = hypot(x, y)
        if (magnitude <= deadzone) return 0f to 0f

        // 把 (deadzone..1) 映射到 (0..1)，再做方向归一
        val scaled = ((magnitude - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
        x = (x / magnitude) * scaled
        y = (y / magnitude) * scaled
        return x.coerceIn(-1f, 1f) to y.coerceIn(-1f, 1f)
    }

    /** 设备若上报了 flat（轴回中范围），以它为准；很多蓝牙手柄这里给的是 0，退回默认死区 */
    private fun deadzoneOf(event: MotionEvent, axis: Int): Float {
        val range = event.device?.getMotionRange(axis)?.flat ?: 0f
        return if (range > 0.02f) range.coerceIn(0.02f, 0.5f) else DEFAULT_DEADZONE
    }

    private fun applyMove(x: Float, y: Float) {
        // 摇杆上推是负值 → W
        setKey(KeyEvent.KEYCODE_W, hysteresis(y, keyW, forward = true))
        setKey(KeyEvent.KEYCODE_S, hysteresis(y, keyS, forward = false))
        setKey(KeyEvent.KEYCODE_A, hysteresis(x, keyA, forward = false))
        setKey(KeyEvent.KEYCODE_D, hysteresis(x, keyD, forward = true))
    }

    /**
     * 迟滞判断：按下要过 MOVE_ON，松开要低于 MOVE_OFF。
     * forward = 该轴正方向（右/下）对应的键。
     */
    private fun hysteresis(value: Float, currentlyPressed: Boolean, forward: Boolean): Boolean {
        val v = if (forward) value else -value
        return if (currentlyPressed) v > MOVE_OFF else v > MOVE_ON
    }

    private fun setKey(keycode: Int, pressed: Boolean) {
        val current = when (keycode) {
            KeyEvent.KEYCODE_W -> keyW
            KeyEvent.KEYCODE_A -> keyA
            KeyEvent.KEYCODE_S -> keyS
            KeyEvent.KEYCODE_D -> keyD
            else -> return
        }
        if (current == pressed) return
        when (keycode) {
            KeyEvent.KEYCODE_W -> keyW = pressed
            KeyEvent.KEYCODE_A -> keyA = pressed
            KeyEvent.KEYCODE_S -> keyS = pressed
            KeyEvent.KEYCODE_D -> keyD = pressed
        }
        input.sendVirtualKey(keycode, pressed)
    }
}
