package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.data.GamepadBindingsRepository
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 设置 → 手柄设置：手柄按键绑定。
 *
 * 「游戏内」用于游戏运行时，「菜单内」用于游戏内菜单/控制面板打开时；
 * 两套表独立保存，改动即时生效（无需保存按钮）。
 */
@Composable
fun GamepadSettingsContent() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(GamepadBindingsRepository.Mode.IN_GAME) }
    var refresh by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<GamepadBindingsRepository.GamepadButton?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeTab(text = "游戏内", selected = mode == GamepadBindingsRepository.Mode.IN_GAME) {
                mode = GamepadBindingsRepository.Mode.IN_GAME
            }
            ModeTab(text = "菜单内", selected = mode == GamepadBindingsRepository.Mode.MENU) {
                mode = GamepadBindingsRepository.Mode.MENU
            }
        }

        GamepadBindingsRepository.buttons.forEach { button ->
            val target = remember(refresh, mode, button.keyCode) {
                GamepadBindingsRepository.get(context, mode, button.keyCode)
            }
            GamepadBindingRow(
                buttonLabel = button.label,
                targetLabel = GamepadBindingsRepository.targetLabel(target),
                bound = target != GamepadBindingsRepository.UNBOUND,
                onPick = { picking = button },
                onReset = {
                    GamepadBindingsRepository.reset(context, mode, button.keyCode)
                    refresh++
                }
            )
        }

        Spacer(Modifier.height(2.dp))
        PcButton(
            text = "恢复默认绑定",
            onClick = {
                GamepadBindingsRepository.resetAll(context, mode)
                refresh++
            },
            variant = PcButtonVariant.OUTLINE,
            modifier = Modifier.fillMaxWidth()
        )
    }

    picking?.let { button ->
        KeyPickerDialog(
            buttonLabel = button.label,
            onDismiss = { picking = null },
            onPick = { keyCode ->
                GamepadBindingsRepository.set(context, mode, button.keyCode, keyCode)
                picking = null
                refresh++
            }
        )
    }
}

/** 页签：游戏内 / 菜单内 */
@Composable
private fun ModeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else VerseColors.PcBgCard,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else VerseColors.PcTextMuted,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

/** 单个手柄按键的绑定行 */
@Composable
private fun GamepadBindingRow(
    buttonLabel: String,
    targetLabel: String,
    bound: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPick)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(badgeColor(buttonLabel)),
                contentAlignment = Alignment.Center
            ) {
                Text(buttonLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Text("已绑定:", fontSize = 13.sp, color = VerseColors.PcTextMuted)
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (bound) VerseColors.PcAccent.copy(alpha = 0.18f) else VerseColors.PcBgTertiary
            ) {
                Text(
                    text = targetLabel,
                    fontSize = 12.sp,
                    color = if (bound) VerseColors.PcAccent else VerseColors.PcTextMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onReset) {
                Icon(Icons.Outlined.Refresh, "恢复默认绑定", Modifier.size(20.dp), VerseColors.PcTextSecondary)
            }
            IconButton(onClick = onPick) {
                Icon(Icons.Outlined.ChevronRight, "选择按键", Modifier.size(20.dp), VerseColors.PcTextSecondary)
            }
        }
    }
}

/** 按键选择对话框 */
@Composable
private fun KeyPickerDialog(
    buttonLabel: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VerseColors.PcBgSecondary,
        titleContentColor = VerseColors.PcTextPrimary,
        title = {
            Text("为「$buttonLabel」绑定按键", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyPickItem("未绑定", GamepadBindingsRepository.UNBOUND, onPick)
                GamepadBindingsRepository.bindableKeys.forEach { (name, keyCode) ->
                    KeyPickItem(name, keyCode, onPick)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun KeyPickItem(name: String, keyCode: Int, onPick: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgCard,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(keyCode) }
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            color = VerseColors.PcTextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** 手柄按键徽章配色（沿用 Xbox 手柄配色，其余为中性色） */
private fun badgeColor(label: String): Color = when (label) {
    "A" -> Color(0xFF4CAF50)
    "B" -> Color(0xFFE53935)
    "X" -> Color(0xFF1E88E5)
    "Y" -> Color(0xFFF9A825)
    else -> Color(0xFF6E6E6E)
}
