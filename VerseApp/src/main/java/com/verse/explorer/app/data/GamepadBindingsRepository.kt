package com.verse.explorer.app.data

import android.content.Context
import android.view.KeyEvent

/**
 * 手柄按键映射：把手柄按键（Android KEYCODE_BUTTON_* 与十字键）映射为键盘按键。
 *
 * 分「游戏内」与「菜单内」两套表，各自独立持久化（SharedPreferences "launcher"）；
 * 未绑定的按键不下发给游戏。目标键使用 Android 按键码，下发时经键盘映射表转换为 GLFW 键。
 */
object GamepadBindingsRepository {

    /** 未绑定 */
    const val UNBOUND = 0

    private const val PREFS = "launcher"
    private const val KEY_PREFIX = "gamepad_binding_"

    /** 手柄输入模式：游戏内 / 菜单内 */
    enum class Mode(val prefSuffix: String) {
        IN_GAME("in_game"),
        MENU("menu")
    }

    /** 手柄按键：显示名、手柄按键码、默认绑定（Android 按键码，UNBOUND 表示默认不绑定） */
    data class GamepadButton(val label: String, val keyCode: Int, val defaultTarget: Int)

    val buttons: List<GamepadButton> = listOf(
        GamepadButton("A", KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_SPACE),
        GamepadButton("B", KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_Q),
        GamepadButton("X", KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_E),
        GamepadButton("Y", KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_F),
        GamepadButton("LB", KeyEvent.KEYCODE_BUTTON_L1, UNBOUND),
        GamepadButton("RB", KeyEvent.KEYCODE_BUTTON_R1, UNBOUND),
        GamepadButton("LT", KeyEvent.KEYCODE_BUTTON_L2, UNBOUND),
        GamepadButton("RT", KeyEvent.KEYCODE_BUTTON_R2, UNBOUND),
        GamepadButton("LS", KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_CTRL_LEFT),
        GamepadButton("RS", KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_SHIFT_LEFT),
        GamepadButton("菜单", KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ESCAPE),
        GamepadButton("视图", KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_TAB),
        GamepadButton("↑", KeyEvent.KEYCODE_DPAD_UP, UNBOUND),
        GamepadButton("↓", KeyEvent.KEYCODE_DPAD_DOWN, UNBOUND),
        GamepadButton("←", KeyEvent.KEYCODE_DPAD_LEFT, UNBOUND),
        GamepadButton("→", KeyEvent.KEYCODE_DPAD_RIGHT, UNBOUND)
    )

    /** 可绑定的目标键：显示名 + Android 按键码 */
    val bindableKeys: List<Pair<String, Int>> = listOf(
        "Space" to KeyEvent.KEYCODE_SPACE,
        "Left Shift" to KeyEvent.KEYCODE_SHIFT_LEFT,
        "Left Ctrl" to KeyEvent.KEYCODE_CTRL_LEFT,
        "Left Alt" to KeyEvent.KEYCODE_ALT_LEFT,
        "Tab" to KeyEvent.KEYCODE_TAB,
        "Esc" to KeyEvent.KEYCODE_ESCAPE,
        "Enter" to KeyEvent.KEYCODE_ENTER,
        "Backspace" to KeyEvent.KEYCODE_DEL,
        "F1" to KeyEvent.KEYCODE_F1,
        "F2" to KeyEvent.KEYCODE_F2,
        "F3" to KeyEvent.KEYCODE_F3,
        "F4" to KeyEvent.KEYCODE_F4,
        "F5" to KeyEvent.KEYCODE_F5,
        "F11" to KeyEvent.KEYCODE_F11,
        "1" to KeyEvent.KEYCODE_1,
        "2" to KeyEvent.KEYCODE_2,
        "3" to KeyEvent.KEYCODE_3,
        "4" to KeyEvent.KEYCODE_4,
        "5" to KeyEvent.KEYCODE_5,
        "6" to KeyEvent.KEYCODE_6,
        "7" to KeyEvent.KEYCODE_7,
        "8" to KeyEvent.KEYCODE_8,
        "9" to KeyEvent.KEYCODE_9,
        "E" to KeyEvent.KEYCODE_E,
        "F" to KeyEvent.KEYCODE_F,
        "Q" to KeyEvent.KEYCODE_Q,
        "R" to KeyEvent.KEYCODE_R,
        "T" to KeyEvent.KEYCODE_T,
        "W" to KeyEvent.KEYCODE_W,
        "A" to KeyEvent.KEYCODE_A,
        "S" to KeyEvent.KEYCODE_S,
        "D" to KeyEvent.KEYCODE_D,
        "Z" to KeyEvent.KEYCODE_Z,
        "X" to KeyEvent.KEYCODE_X,
        "C" to KeyEvent.KEYCODE_C,
        "V" to KeyEvent.KEYCODE_V,
        "↑" to KeyEvent.KEYCODE_DPAD_UP,
        "↓" to KeyEvent.KEYCODE_DPAD_DOWN,
        "←" to KeyEvent.KEYCODE_DPAD_LEFT,
        "→" to KeyEvent.KEYCODE_DPAD_RIGHT
    )

    /** 取绑定目标；未设置时回退默认值 */
    fun get(context: Context, mode: Mode, buttonKeyCode: Int): Int {
        val fallback = buttonDefault(buttonKeyCode)
        return prefs(context).getInt(prefKey(mode, buttonKeyCode), fallback)
    }

    /** 写入绑定目标（UNBOUND 表示解绑） */
    fun set(context: Context, mode: Mode, buttonKeyCode: Int, targetKeyCode: Int) {
        prefs(context).edit().putInt(prefKey(mode, buttonKeyCode), targetKeyCode).apply()
    }

    /** 恢复单个按键的默认绑定 */
    fun reset(context: Context, mode: Mode, buttonKeyCode: Int) {
        prefs(context).edit().remove(prefKey(mode, buttonKeyCode)).apply()
    }

    /** 恢复该模式全部按键的默认绑定 */
    fun resetAll(context: Context, mode: Mode) {
        val editor = prefs(context).edit()
        buttons.forEach { editor.remove(prefKey(mode, it.keyCode)) }
        editor.apply()
    }

    /** 绑定目标的显示名；未绑定显示「未绑定」 */
    fun targetLabel(targetKeyCode: Int): String =
        bindableKeys.firstOrNull { it.second == targetKeyCode }?.first ?: "未绑定"

    private fun buttonDefault(buttonKeyCode: Int): Int =
        buttons.firstOrNull { it.keyCode == buttonKeyCode }?.defaultTarget ?: UNBOUND

    private fun prefKey(mode: Mode, buttonKeyCode: Int) = "$KEY_PREFIX${mode.prefSuffix}_$buttonKeyCode"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
