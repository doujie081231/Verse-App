package com.verse.explorer.launcher.keycodes

import android.view.KeyEvent

/**
 * Maps Android KeyEvent keycodes to GLFW/LWJGL keycodes.
 */
object AndroidKeycodeMap {

    // Special keycodes for virtual controller
    const val KEY_UNKNOWN = -1
    const val KEY_ESC = 256
    const val KEY_RIGHTSHIFT = 344

    private val MAP = mapOf(
        KeyEvent.KEYCODE_UNKNOWN to LwjglGlfwKeycode.GLFW_KEY_UNKNOWN,
        KeyEvent.KEYCODE_SPACE to LwjglGlfwKeycode.GLFW_KEY_SPACE,
        KeyEvent.KEYCODE_APOSTROPHE to LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE,
        KeyEvent.KEYCODE_COMMA to LwjglGlfwKeycode.GLFW_KEY_COMMA,
        KeyEvent.KEYCODE_MINUS to LwjglGlfwKeycode.GLFW_KEY_MINUS,
        KeyEvent.KEYCODE_PERIOD to LwjglGlfwKeycode.GLFW_KEY_PERIOD,
        KeyEvent.KEYCODE_SLASH to LwjglGlfwKeycode.GLFW_KEY_SLASH,
        KeyEvent.KEYCODE_0 to LwjglGlfwKeycode.GLFW_KEY_0,
        KeyEvent.KEYCODE_1 to LwjglGlfwKeycode.GLFW_KEY_1,
        KeyEvent.KEYCODE_2 to LwjglGlfwKeycode.GLFW_KEY_2,
        KeyEvent.KEYCODE_3 to LwjglGlfwKeycode.GLFW_KEY_3,
        KeyEvent.KEYCODE_4 to LwjglGlfwKeycode.GLFW_KEY_4,
        KeyEvent.KEYCODE_5 to LwjglGlfwKeycode.GLFW_KEY_5,
        KeyEvent.KEYCODE_6 to LwjglGlfwKeycode.GLFW_KEY_6,
        KeyEvent.KEYCODE_7 to LwjglGlfwKeycode.GLFW_KEY_7,
        KeyEvent.KEYCODE_8 to LwjglGlfwKeycode.GLFW_KEY_8,
        KeyEvent.KEYCODE_9 to LwjglGlfwKeycode.GLFW_KEY_9,
        KeyEvent.KEYCODE_SEMICOLON to LwjglGlfwKeycode.GLFW_KEY_SEMICOLON,
        KeyEvent.KEYCODE_EQUALS to LwjglGlfwKeycode.GLFW_KEY_EQUAL,
        KeyEvent.KEYCODE_A to LwjglGlfwKeycode.GLFW_KEY_A,
        KeyEvent.KEYCODE_B to LwjglGlfwKeycode.GLFW_KEY_B,
        KeyEvent.KEYCODE_C to LwjglGlfwKeycode.GLFW_KEY_C,
        KeyEvent.KEYCODE_D to LwjglGlfwKeycode.GLFW_KEY_D,
        KeyEvent.KEYCODE_E to LwjglGlfwKeycode.GLFW_KEY_E,
        KeyEvent.KEYCODE_F to LwjglGlfwKeycode.GLFW_KEY_F,
        KeyEvent.KEYCODE_G to LwjglGlfwKeycode.GLFW_KEY_G,
        KeyEvent.KEYCODE_H to LwjglGlfwKeycode.GLFW_KEY_H,
        KeyEvent.KEYCODE_I to LwjglGlfwKeycode.GLFW_KEY_I,
        KeyEvent.KEYCODE_J to LwjglGlfwKeycode.GLFW_KEY_J,
        KeyEvent.KEYCODE_K to LwjglGlfwKeycode.GLFW_KEY_K,
        KeyEvent.KEYCODE_L to LwjglGlfwKeycode.GLFW_KEY_L,
        KeyEvent.KEYCODE_M to LwjglGlfwKeycode.GLFW_KEY_M,
        KeyEvent.KEYCODE_N to LwjglGlfwKeycode.GLFW_KEY_N,
        KeyEvent.KEYCODE_O to LwjglGlfwKeycode.GLFW_KEY_O,
        KeyEvent.KEYCODE_P to LwjglGlfwKeycode.GLFW_KEY_P,
        KeyEvent.KEYCODE_Q to LwjglGlfwKeycode.GLFW_KEY_Q,
        KeyEvent.KEYCODE_R to LwjglGlfwKeycode.GLFW_KEY_R,
        KeyEvent.KEYCODE_S to LwjglGlfwKeycode.GLFW_KEY_S,
        KeyEvent.KEYCODE_T to LwjglGlfwKeycode.GLFW_KEY_T,
        KeyEvent.KEYCODE_U to LwjglGlfwKeycode.GLFW_KEY_U,
        KeyEvent.KEYCODE_V to LwjglGlfwKeycode.GLFW_KEY_V,
        KeyEvent.KEYCODE_W to LwjglGlfwKeycode.GLFW_KEY_W,
        KeyEvent.KEYCODE_X to LwjglGlfwKeycode.GLFW_KEY_X,
        KeyEvent.KEYCODE_Y to LwjglGlfwKeycode.GLFW_KEY_Y,
        KeyEvent.KEYCODE_Z to LwjglGlfwKeycode.GLFW_KEY_Z,
        KeyEvent.KEYCODE_LEFT_BRACKET to LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET,
        KeyEvent.KEYCODE_BACKSLASH to LwjglGlfwKeycode.GLFW_KEY_BACKSLASH,
        KeyEvent.KEYCODE_RIGHT_BRACKET to LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET,
        KeyEvent.KEYCODE_GRAVE to LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT,
        KeyEvent.KEYCODE_ESCAPE to LwjglGlfwKeycode.GLFW_KEY_ESCAPE,
        KeyEvent.KEYCODE_ENTER to LwjglGlfwKeycode.GLFW_KEY_ENTER,
        KeyEvent.KEYCODE_TAB to LwjglGlfwKeycode.GLFW_KEY_TAB,
        KeyEvent.KEYCODE_DEL to LwjglGlfwKeycode.GLFW_KEY_BACKSPACE,
        KeyEvent.KEYCODE_INSERT to LwjglGlfwKeycode.GLFW_KEY_INSERT,
        KeyEvent.KEYCODE_FORWARD_DEL to LwjglGlfwKeycode.GLFW_KEY_DELETE,
        KeyEvent.KEYCODE_DPAD_RIGHT to LwjglGlfwKeycode.GLFW_KEY_RIGHT,
        KeyEvent.KEYCODE_DPAD_LEFT to LwjglGlfwKeycode.GLFW_KEY_LEFT,
        KeyEvent.KEYCODE_DPAD_DOWN to LwjglGlfwKeycode.GLFW_KEY_DOWN,
        KeyEvent.KEYCODE_DPAD_UP to LwjglGlfwKeycode.GLFW_KEY_UP,
        KeyEvent.KEYCODE_PAGE_UP to LwjglGlfwKeycode.GLFW_KEY_PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN to LwjglGlfwKeycode.GLFW_KEY_PAGE_DOWN,
        KeyEvent.KEYCODE_MOVE_HOME to LwjglGlfwKeycode.GLFW_KEY_HOME,
        KeyEvent.KEYCODE_MOVE_END to LwjglGlfwKeycode.GLFW_KEY_END,
        KeyEvent.KEYCODE_CAPS_LOCK to LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK,
        KeyEvent.KEYCODE_SCROLL_LOCK to LwjglGlfwKeycode.GLFW_KEY_SCROLL_LOCK,
        KeyEvent.KEYCODE_NUM_LOCK to LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK,
        KeyEvent.KEYCODE_F1 to LwjglGlfwKeycode.GLFW_KEY_F1,
        KeyEvent.KEYCODE_F2 to LwjglGlfwKeycode.GLFW_KEY_F2,
        KeyEvent.KEYCODE_F3 to LwjglGlfwKeycode.GLFW_KEY_F3,
        KeyEvent.KEYCODE_F4 to LwjglGlfwKeycode.GLFW_KEY_F4,
        KeyEvent.KEYCODE_F5 to LwjglGlfwKeycode.GLFW_KEY_F5,
        KeyEvent.KEYCODE_F6 to LwjglGlfwKeycode.GLFW_KEY_F6,
        KeyEvent.KEYCODE_F7 to LwjglGlfwKeycode.GLFW_KEY_F7,
        KeyEvent.KEYCODE_F8 to LwjglGlfwKeycode.GLFW_KEY_F8,
        KeyEvent.KEYCODE_F9 to LwjglGlfwKeycode.GLFW_KEY_F9,
        KeyEvent.KEYCODE_F10 to LwjglGlfwKeycode.GLFW_KEY_F10,
        KeyEvent.KEYCODE_F11 to LwjglGlfwKeycode.GLFW_KEY_F11,
        KeyEvent.KEYCODE_F12 to LwjglGlfwKeycode.GLFW_KEY_F12,
        KeyEvent.KEYCODE_NUMPAD_0 to LwjglGlfwKeycode.GLFW_KEY_KP_0,
        KeyEvent.KEYCODE_NUMPAD_1 to LwjglGlfwKeycode.GLFW_KEY_KP_1,
        KeyEvent.KEYCODE_NUMPAD_2 to LwjglGlfwKeycode.GLFW_KEY_KP_2,
        KeyEvent.KEYCODE_NUMPAD_3 to LwjglGlfwKeycode.GLFW_KEY_KP_3,
        KeyEvent.KEYCODE_NUMPAD_4 to LwjglGlfwKeycode.GLFW_KEY_KP_4,
        KeyEvent.KEYCODE_NUMPAD_5 to LwjglGlfwKeycode.GLFW_KEY_KP_5,
        KeyEvent.KEYCODE_NUMPAD_6 to LwjglGlfwKeycode.GLFW_KEY_KP_6,
        KeyEvent.KEYCODE_NUMPAD_7 to LwjglGlfwKeycode.GLFW_KEY_KP_7,
        KeyEvent.KEYCODE_NUMPAD_8 to LwjglGlfwKeycode.GLFW_KEY_KP_8,
        KeyEvent.KEYCODE_NUMPAD_9 to LwjglGlfwKeycode.GLFW_KEY_KP_9,
        KeyEvent.KEYCODE_NUMPAD_DOT to LwjglGlfwKeycode.GLFW_KEY_KP_DECIMAL,
        KeyEvent.KEYCODE_NUMPAD_DIVIDE to LwjglGlfwKeycode.GLFW_KEY_KP_DIVIDE,
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY to LwjglGlfwKeycode.GLFW_KEY_KP_MULTIPLY,
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT to LwjglGlfwKeycode.GLFW_KEY_KP_SUBTRACT,
        KeyEvent.KEYCODE_NUMPAD_ADD to LwjglGlfwKeycode.GLFW_KEY_KP_ADD,
        KeyEvent.KEYCODE_NUMPAD_ENTER to LwjglGlfwKeycode.GLFW_KEY_KP_ENTER,
        KeyEvent.KEYCODE_NUMPAD_EQUALS to LwjglGlfwKeycode.GLFW_KEY_KP_EQUAL,
        KeyEvent.KEYCODE_SHIFT_LEFT to LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT,
        KeyEvent.KEYCODE_CTRL_LEFT to LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL,
        KeyEvent.KEYCODE_ALT_LEFT to LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT,
        KeyEvent.KEYCODE_META_LEFT to LwjglGlfwKeycode.GLFW_KEY_LEFT_SUPER,
        KeyEvent.KEYCODE_SHIFT_RIGHT to LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT,
        KeyEvent.KEYCODE_CTRL_RIGHT to LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL,
        KeyEvent.KEYCODE_ALT_RIGHT to LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT,
        KeyEvent.KEYCODE_META_RIGHT to LwjglGlfwKeycode.GLFW_KEY_RIGHT_SUPER,
        KeyEvent.KEYCODE_MENU to LwjglGlfwKeycode.GLFW_KEY_MENU,
        KeyEvent.KEYCODE_SYSRQ to LwjglGlfwKeycode.GLFW_KEY_PRINT_SCREEN,
        KeyEvent.KEYCODE_BREAK to LwjglGlfwKeycode.GLFW_KEY_PAUSE,
    )

    /**
     * Convert an Android keycode to a GLFW/LWJGL keycode.
     * Returns KEY_UNKNOWN if no mapping exists.
     */
    @JvmStatic
    fun convertKeycode(androidKeycode: Int): Int {
        return MAP[androidKeycode] ?: KEY_UNKNOWN
    }
}

/**
 * Maps LWJGL keycodes to GLFW keycodes.
 * Used for the virtual controller button mapping.
 */
object LwjglKeycodeMap {

    private val MAP = mapOf(
        // WASD keys (Android keycodes used by VirtualController)
        87 to LwjglGlfwKeycode.GLFW_KEY_W,     // 'W'
        65 to LwjglGlfwKeycode.GLFW_KEY_A,     // 'A'
        83 to LwjglGlfwKeycode.GLFW_KEY_S,     // 'S'
        68 to LwjglGlfwKeycode.GLFW_KEY_D,     // 'D'
        32 to LwjglGlfwKeycode.GLFW_KEY_SPACE, // Space
        69 to LwjglGlfwKeycode.GLFW_KEY_E,     // 'E'
        84 to LwjglGlfwKeycode.GLFW_KEY_T,     // 'T'
        340 to LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT,
        256 to LwjglGlfwKeycode.GLFW_KEY_ESCAPE,
    )

    @JvmStatic
    fun convertKeycode(keycode: Int): Int {
        return MAP[keycode] ?: keycode
    }
}
