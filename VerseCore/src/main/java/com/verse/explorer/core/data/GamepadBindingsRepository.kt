package com.verse.explorer.core.data

import android.content.Context
import android.view.KeyEvent

/**
 * 手柄按键映射：把手柄按键（Android KEYCODE_BUTTON_* 与十字键）映射为一组键盘按键。
 *
 * - 一个手柄按键可同时绑定多个目标键；
 * - 分「游戏内」「菜单内」两套表，各自独立持久化（SharedPreferences "launcher"）；
 * - 支持多套映射配置档案，档案之间互不影响；
 * - 未绑定的按键不下发给游戏，总开关关闭时整表停用。目标键使用 Android 按键码，下发时经键盘映射表转换为 GLFW 键。
 */
object GamepadBindingsRepository {

    private const val PREFS = "launcher"
    private const val KEY_ENABLED = "gamepad_control_enabled"
    private const val KEY_PROFILE = "gamepad_current_profile"
    private const val KEY_PROFILES = "gamepad_profiles"
    private const val BINDING_PREFIX = "gamepad_binding_"
    private const val SEPARATOR = "|"

    /** 旧版单键绑定的「未绑定」值，仅用于读取历史数据做迁移 */
    private const val LEGACY_UNBOUND = 0

    /** 默认配置档案名 */
    const val DEFAULT_PROFILE = "默认"

    /** 手柄输入模式：游戏内 / 菜单内 */
    enum class Mode(val prefSuffix: String) {
        IN_GAME("in_game"),
        MENU("menu")
    }

    /** 手柄按键：显示名、手柄按键码、默认绑定的目标键（空表示默认不绑定） */
    data class GamepadButton(val label: String, val keyCode: Int, val defaultTargets: List<Int>)

    val buttons: List<GamepadButton> = listOf(
        GamepadButton("A", KeyEvent.KEYCODE_BUTTON_A, listOf(KeyEvent.KEYCODE_SPACE)),
        GamepadButton("B", KeyEvent.KEYCODE_BUTTON_B, listOf(KeyEvent.KEYCODE_Q)),
        GamepadButton("X", KeyEvent.KEYCODE_BUTTON_X, listOf(KeyEvent.KEYCODE_E)),
        GamepadButton("Y", KeyEvent.KEYCODE_BUTTON_Y, listOf(KeyEvent.KEYCODE_F)),
        GamepadButton("LB", KeyEvent.KEYCODE_BUTTON_L1, emptyList()),
        GamepadButton("RB", KeyEvent.KEYCODE_BUTTON_R1, emptyList()),
        GamepadButton("LT", KeyEvent.KEYCODE_BUTTON_L2, emptyList()),
        GamepadButton("RT", KeyEvent.KEYCODE_BUTTON_R2, emptyList()),
        GamepadButton("LS", KeyEvent.KEYCODE_BUTTON_THUMBL, listOf(KeyEvent.KEYCODE_CTRL_LEFT)),
        GamepadButton("RS", KeyEvent.KEYCODE_BUTTON_THUMBR, listOf(KeyEvent.KEYCODE_SHIFT_LEFT)),
        GamepadButton("菜单", KeyEvent.KEYCODE_BUTTON_START, listOf(KeyEvent.KEYCODE_ESCAPE)),
        GamepadButton("视图", KeyEvent.KEYCODE_BUTTON_SELECT, listOf(KeyEvent.KEYCODE_TAB)),
        GamepadButton("↑", KeyEvent.KEYCODE_DPAD_UP, emptyList()),
        GamepadButton("↓", KeyEvent.KEYCODE_DPAD_DOWN, emptyList()),
        GamepadButton("←", KeyEvent.KEYCODE_DPAD_LEFT, emptyList()),
        GamepadButton("→", KeyEvent.KEYCODE_DPAD_RIGHT, emptyList())
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

    // ===== 手柄总开关 =====

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ===== 映射配置档案 =====

    fun profiles(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_PROFILES, null)
        val names = raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        return if (names.isEmpty()) listOf(DEFAULT_PROFILE) else names
    }

    fun currentProfile(context: Context): String {
        val name = prefs(context).getString(KEY_PROFILE, DEFAULT_PROFILE) ?: DEFAULT_PROFILE
        return if (profiles(context).contains(name)) name else DEFAULT_PROFILE
    }

    fun setCurrentProfile(context: Context, name: String) {
        if (!profiles(context).contains(name)) return
        prefs(context).edit().putString(KEY_PROFILE, name).apply()
    }

    fun containsProfile(context: Context, name: String): Boolean = profiles(context).contains(name)

    /** 新建配置档案；名称为空、含分隔符或已存在时返回 false */
    fun createProfile(context: Context, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains(',') || trimmed.contains(SEPARATOR)) return false
        val list = profiles(context).toMutableList()
        if (list.contains(trimmed)) return false
        list.add(trimmed)
        prefs(context).edit()
            .putString(KEY_PROFILES, list.joinToString(","))
            .putString(KEY_PROFILE, trimmed)
            .apply()
        return true
    }

    /** 删除配置档案，并清除该档案下的全部绑定；默认档案与最后一个档案不可删除 */
    fun deleteProfile(context: Context, name: String) {
        val list = profiles(context).toMutableList()
        if (list.size <= 1 || name == DEFAULT_PROFILE || !list.contains(name)) return
        list.remove(name)
        val editor = prefs(context).edit()
        prefs(context).all.keys
            .filter { it.startsWith(BINDING_PREFIX + name + SEPARATOR) }
            .forEach { editor.remove(it) }
        editor.putString(KEY_PROFILES, list.joinToString(","))
        if (currentProfile(context) == name) editor.putString(KEY_PROFILE, list.first())
        editor.apply()
    }

    // ===== 绑定读写 =====

    /** 取绑定目标集合；未设置过时回退默认值 */
    fun get(context: Context, mode: Mode, buttonKeyCode: Int): Set<Int> {
        val profile = currentProfile(context)
        val stored = prefs(context).getString(bindingKey(profile, mode, buttonKeyCode), null)
        if (stored == null && profile == DEFAULT_PROFILE) {
            // 读取旧版单键绑定，避免升级后已配置的手柄绑定被重置为默认
            val legacy = prefs(context).getInt(legacyKey(mode, buttonKeyCode), LEGACY_UNBOUND)
            if (legacy != LEGACY_UNBOUND) return setOf(legacy)
        }
        return if (stored == null) buttonDefault(buttonKeyCode).toSet() else parseSet(stored)
    }

    /** 写入绑定目标集合（空集合表示解绑） */
    fun set(context: Context, mode: Mode, buttonKeyCode: Int, targets: Set<Int>) {
        prefs(context).edit()
            .putString(bindingKey(currentProfile(context), mode, buttonKeyCode), targets.sorted().joinToString(","))
            .apply()
    }

    /** 在现有绑定上增删一个目标键（用于多键绑定的勾选/取消） */
    fun toggle(context: Context, mode: Mode, buttonKeyCode: Int, targetKeyCode: Int) {
        val targets = get(context, mode, buttonKeyCode).toMutableSet()
        if (!targets.add(targetKeyCode)) targets.remove(targetKeyCode)
        set(context, mode, buttonKeyCode, targets)
    }

    /** 恢复单个按键的默认绑定 */
    fun reset(context: Context, mode: Mode, buttonKeyCode: Int) {
        prefs(context).edit().remove(bindingKey(currentProfile(context), mode, buttonKeyCode)).apply()
    }

    /** 恢复当前档案下该模式全部按键的默认绑定 */
    fun resetAll(context: Context, mode: Mode) {
        val editor = prefs(context).edit()
        val prefix = BINDING_PREFIX + currentProfile(context) + SEPARATOR + mode.prefSuffix + SEPARATOR
        prefs(context).all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    /** 绑定目标的显示名；不可识别时显示「未知」 */
    fun targetLabel(targetKeyCode: Int): String =
        bindableKeys.firstOrNull { it.second == targetKeyCode }?.first ?: "未知"

    private fun buttonDefault(buttonKeyCode: Int): List<Int> =
        buttons.firstOrNull { it.keyCode == buttonKeyCode }?.defaultTargets ?: emptyList()

    private fun bindingKey(profile: String, mode: Mode, buttonKeyCode: Int) =
        "$BINDING_PREFIX$profile$SEPARATOR${mode.prefSuffix}$SEPARATOR$buttonKeyCode"

    /** 旧版单键绑定的键名（无档案前缀），仅用于迁移读取 */
    private fun legacyKey(mode: Mode, buttonKeyCode: Int) =
        "$BINDING_PREFIX${mode.prefSuffix}_$buttonKeyCode"

    private fun parseSet(raw: String): Set<Int> =
        raw.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
