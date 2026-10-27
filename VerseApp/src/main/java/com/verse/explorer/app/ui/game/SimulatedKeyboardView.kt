package com.verse.explorer.app.ui.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.verse.explorer.launcher.keycodes.LwjglGlfwKeycode as K

/**
 * 游戏内模拟键盘（纯 View 实现）：
 * 补全 F1-F12、符号、方向键等实体键盘才有的按键，按下/抬起分别发送 GLFW 按键事件，
 * 支持按住组合键（如 F3 + H）。
 *
 * 不使用 Compose：这里的宿主是普通 Dialog，Compose 在该容器里需要额外的
 * ViewTree 生命周期/ViewModel/状态保存所有者，缺失时组合阶段直接抛异常闪退。
 */
private data class KeySpec(val label: String, val code: Int = -1, val weight: Float = 1f)

private val KEY_ROWS: List<List<KeySpec>> = listOf(
    listOf(
        KeySpec("Esc", K.GLFW_KEY_ESCAPE, 1.2f),
        KeySpec("F1", K.GLFW_KEY_F1),
        KeySpec("F2", K.GLFW_KEY_F2),
        KeySpec("F3", K.GLFW_KEY_F3),
        KeySpec("F4", K.GLFW_KEY_F4),
        KeySpec("F5", K.GLFW_KEY_F5),
        KeySpec("F6", K.GLFW_KEY_F6),
        KeySpec("F7", K.GLFW_KEY_F7),
        KeySpec("F8", K.GLFW_KEY_F8),
        KeySpec("F9", K.GLFW_KEY_F9),
        KeySpec("F10", K.GLFW_KEY_F10),
        KeySpec("F11", K.GLFW_KEY_F11),
        KeySpec("F12", K.GLFW_KEY_F12)
    ),
    listOf(
        KeySpec("`", K.GLFW_KEY_GRAVE_ACCENT),
        KeySpec("1", K.GLFW_KEY_1),
        KeySpec("2", K.GLFW_KEY_2),
        KeySpec("3", K.GLFW_KEY_3),
        KeySpec("4", K.GLFW_KEY_4),
        KeySpec("5", K.GLFW_KEY_5),
        KeySpec("6", K.GLFW_KEY_6),
        KeySpec("7", K.GLFW_KEY_7),
        KeySpec("8", K.GLFW_KEY_8),
        KeySpec("9", K.GLFW_KEY_9),
        KeySpec("0", K.GLFW_KEY_0),
        KeySpec("-", K.GLFW_KEY_MINUS),
        KeySpec("=", K.GLFW_KEY_EQUAL),
        KeySpec("Backspace", K.GLFW_KEY_BACKSPACE, 2f)
    ),
    listOf(
        KeySpec("Tab", K.GLFW_KEY_TAB, 1.4f),
        KeySpec("Q", K.GLFW_KEY_Q),
        KeySpec("W", K.GLFW_KEY_W),
        KeySpec("E", K.GLFW_KEY_E),
        KeySpec("R", K.GLFW_KEY_R),
        KeySpec("T", K.GLFW_KEY_T),
        KeySpec("Y", K.GLFW_KEY_Y),
        KeySpec("U", K.GLFW_KEY_U),
        KeySpec("I", K.GLFW_KEY_I),
        KeySpec("O", K.GLFW_KEY_O),
        KeySpec("P", K.GLFW_KEY_P),
        KeySpec("[", K.GLFW_KEY_LEFT_BRACKET),
        KeySpec("]", K.GLFW_KEY_RIGHT_BRACKET),
        KeySpec("\\", K.GLFW_KEY_BACKSLASH, 1.4f)
    ),
    listOf(
        KeySpec("Caps", K.GLFW_KEY_CAPS_LOCK, 1.6f),
        KeySpec("A", K.GLFW_KEY_A),
        KeySpec("S", K.GLFW_KEY_S),
        KeySpec("D", K.GLFW_KEY_D),
        KeySpec("F", K.GLFW_KEY_F),
        KeySpec("G", K.GLFW_KEY_G),
        KeySpec("H", K.GLFW_KEY_H),
        KeySpec("J", K.GLFW_KEY_J),
        KeySpec("K", K.GLFW_KEY_K),
        KeySpec("L", K.GLFW_KEY_L),
        KeySpec(";", K.GLFW_KEY_SEMICOLON),
        KeySpec("'", K.GLFW_KEY_APOSTROPHE),
        KeySpec("Enter", K.GLFW_KEY_ENTER, 1.8f)
    ),
    listOf(
        KeySpec("Shift", K.GLFW_KEY_LEFT_SHIFT, 1.8f),
        KeySpec("Z", K.GLFW_KEY_Z),
        KeySpec("X", K.GLFW_KEY_X),
        KeySpec("C", K.GLFW_KEY_C),
        KeySpec("V", K.GLFW_KEY_V),
        KeySpec("B", K.GLFW_KEY_B),
        KeySpec("N", K.GLFW_KEY_N),
        KeySpec("M", K.GLFW_KEY_M),
        KeySpec(",", K.GLFW_KEY_COMMA),
        KeySpec(".", K.GLFW_KEY_PERIOD),
        KeySpec("/", K.GLFW_KEY_SLASH),
        KeySpec("Shift", K.GLFW_KEY_RIGHT_SHIFT, 1.8f)
    ),
    listOf(
        KeySpec("Ctrl", K.GLFW_KEY_LEFT_CONTROL, 1.3f),
        KeySpec("Alt", K.GLFW_KEY_LEFT_ALT, 1.3f),
        KeySpec("Space", K.GLFW_KEY_SPACE, 5f),
        KeySpec("←", K.GLFW_KEY_LEFT),
        KeySpec("↑", K.GLFW_KEY_UP),
        KeySpec("↓", K.GLFW_KEY_DOWN),
        KeySpec("→", K.GLFW_KEY_RIGHT)
    )
)

/** 构建模拟键盘视图：整屏半透明遮罩 + 居中深色面板，点面板外空白关闭 */
@SuppressLint("ClickableViewAccessibility")
fun createSimulatedKeyboardView(
    context: Context,
    onKey: (Int, Boolean) -> Unit,
    onClose: () -> Unit
): View {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val keyColor = Color.argb(0xFF, 0x3A, 0x3A, 0x3A)
    val keyPressedColor = Color.argb(0xFF, 0x55, 0x55, 0x55)

    fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    val root = FrameLayout(context).apply {
        setBackgroundColor(Color.argb(0x99, 0x00, 0x00, 0x00))
        isClickable = true
        setOnClickListener { onClose() }
    }

    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = rounded(Color.argb(0xEE, 0x1B, 0x1B, 0x1B), 16)
        // 面板自身吃掉点击，避免"点按键把面板关掉"
        isClickable = true
    }

    // 标题行：标题 + 组合键提示 + 关闭
    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(8))
    }
    header.addView(TextView(context).apply {
        text = "模拟键盘"
        setTextColor(Color.WHITE)
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    })
    header.addView(TextView(context).apply {
        text = "  按住可组合（如 F3 + H）"
        setTextColor(Color.rgb(0x9C, 0xA3, 0xAF))
        textSize = 11f
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    header.addView(TextView(context).apply {
        text = "关闭"
        setTextColor(Color.rgb(0xE5, 0xE7, 0xEB))
        textSize = 14f
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setOnClickListener { onClose() }
    })
    panel.addView(header)

    KEY_ROWS.forEach { row ->
        val rowView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val rowParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2); bottomMargin = dp(2) }
        panel.addView(rowView, rowParams)

        row.forEach { spec ->
            val keyView = TextView(context).apply {
                text = spec.label
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 1
                background = rounded(keyColor, 8)
                isClickable = true
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (spec.code < 0) return@setOnTouchListener true
                            view.background = rounded(keyPressedColor, 8)
                            onKey(spec.code, true)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (spec.code < 0) return@setOnTouchListener true
                            view.background = rounded(keyColor, 8)
                            onKey(spec.code, false)
                            true
                        }
                        else -> false
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(0, dp(44), spec.weight)
            lp.marginStart = dp(2)
            lp.marginEnd = dp(2)
            rowView.addView(keyView, lp)
        }
    }

    val panelParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.CENTER
    ).apply {
        marginStart = dp(8)
        marginEnd = dp(8)
    }
    root.addView(panel, panelParams)
    return root
}
