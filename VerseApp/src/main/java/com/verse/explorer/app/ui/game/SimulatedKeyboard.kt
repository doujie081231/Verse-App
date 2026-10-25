package com.verse.explorer.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.launcher.keycodes.LwjglGlfwKeycode as K

/**
 * 游戏内模拟键盘（布局与 ZL2 模拟键盘一致）：
 * 补全 F1-F12、符号、方向键等实体键盘才有的按键，
 * 按下/抬起分别向游戏窗口发送 GLFW 按键事件，支持按住组合键（如 F3+H）。
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

@Composable
fun SimulatedKeyboard(onKey: (Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .background(Color(0xEE1B1B1B), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("模拟键盘", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("按住可组合（如 F3 + H）", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "关闭",
                    color = Color(0xFFE5E7EB),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            KEY_ROWS.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { spec -> VirtualKey(spec, onKey) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.VirtualKey(spec: KeySpec, onKey: (Int, Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .weight(spec.weight)
            .height(44.dp)
            .background(Color(0xFF3A3A3A), RoundedCornerShape(8.dp))
            .pointerInput(spec.code) {
                detectTapGestures(
                    onPress = {
                        onKey(spec.code, true)
                        tryAwaitRelease()
                        onKey(spec.code, false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            spec.label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
