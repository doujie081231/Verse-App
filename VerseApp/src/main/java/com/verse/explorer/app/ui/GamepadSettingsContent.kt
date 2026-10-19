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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.data.GamepadBindingsRepository
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.PcSwitchRow
import com.verse.explorer.app.ui.components.PcTextInput
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 设置 → 手柄设置：手柄按键映射。
 *
 * 「游戏内」用于游戏运行时，「菜单内」用于游戏内菜单/控制面板打开时；两套表独立保存。
 * 一个手柄按键可同时绑定多个目标键；改动即时生效（无需保存按钮）。映射配置档案之间互不影响。
 */
@Composable
fun GamepadSettingsContent() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(GamepadBindingsRepository.Mode.IN_GAME) }
    var refresh by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<GamepadBindingsRepository.GamepadButton?>(null) }
    var enabled by remember { mutableStateOf(GamepadBindingsRepository.isEnabled(context)) }
    var profile by remember(refresh) { mutableStateOf(GamepadBindingsRepository.currentProfile(context)) }
    var showSwitch by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PcCard(title = "手柄控制") {
            PcSwitchRow(
                label = "手柄控制",
                description = "开启后手柄按键按下方映射表下发到游戏",
                checked = enabled
            ) { on ->
                enabled = on
                GamepadBindingsRepository.setEnabled(context, on)
            }
        }

        PcCard(title = "映射配置档案") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("当前档案", fontSize = 13.sp, color = VerseColors.PcTextMuted)
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = VerseColors.PcBgTertiary
                    ) {
                        Text(
                            profile,
                            fontSize = 12.sp,
                            color = VerseColors.PcTextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    PcButton(
                        text = "切换",
                        onClick = { showSwitch = true },
                        variant = PcButtonVariant.OUTLINE
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PcButton(
                        text = "新建档案",
                        onClick = { showCreate = true },
                        variant = PcButtonVariant.SECONDARY
                    )
                    PcButton(
                        text = "删除档案",
                        onClick = {
                            GamepadBindingsRepository.deleteProfile(context, profile)
                            profile = GamepadBindingsRepository.currentProfile(context)
                            refresh++
                        },
                        variant = PcButtonVariant.DANGER,
                        enabled = profile != GamepadBindingsRepository.DEFAULT_PROFILE
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeTab(text = "游戏内", selected = mode == GamepadBindingsRepository.Mode.IN_GAME) {
                mode = GamepadBindingsRepository.Mode.IN_GAME
            }
            ModeTab(text = "菜单内", selected = mode == GamepadBindingsRepository.Mode.MENU) {
                mode = GamepadBindingsRepository.Mode.MENU
            }
        }

        Column(
            modifier = Modifier.alpha(if (enabled) 1f else 0.45f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GamepadBindingsRepository.buttons.forEach { button ->
                val targets = remember(refresh, mode, profile, button.keyCode) {
                    GamepadBindingsRepository.get(context, mode, button.keyCode)
                }
                GamepadBindingRow(
                    buttonLabel = button.label,
                    targetLabels = targets.map { GamepadBindingsRepository.targetLabel(it) },
                    bound = targets.isNotEmpty(),
                    enabled = enabled,
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
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showSwitch) {
        ProfileSwitchDialog(
            profiles = GamepadBindingsRepository.profiles(context),
            current = profile,
            onDismiss = { showSwitch = false },
            onSelect = { name ->
                GamepadBindingsRepository.setCurrentProfile(context, name)
                profile = GamepadBindingsRepository.currentProfile(context)
                refresh++
                showSwitch = false
            }
        )
    }

    if (showCreate) {
        ProfileCreateDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                val ok = GamepadBindingsRepository.createProfile(context, name)
                if (ok) {
                    profile = GamepadBindingsRepository.currentProfile(context)
                    refresh++
                    showCreate = false
                }
                ok
            }
        )
    }

    picking?.let { button ->
        KeyPickerDialog(
            buttonLabel = button.label,
            selected = GamepadBindingsRepository.get(context, mode, button.keyCode),
            onDismiss = { picking = null },
            onToggle = { keyCode ->
                GamepadBindingsRepository.toggle(context, mode, button.keyCode, keyCode)
                refresh++
            },
            onClear = {
                GamepadBindingsRepository.set(context, mode, button.keyCode, emptySet())
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
    targetLabels: List<String>,
    bound: Boolean,
    enabled: Boolean,
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
                .clickable(enabled = enabled, onClick = onPick)
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
                    text = if (bound) targetLabels.joinToString(" · ") else "未绑定",
                    fontSize = 12.sp,
                    color = if (bound) VerseColors.PcAccent else VerseColors.PcTextMuted,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onReset, enabled = enabled) {
                Icon(Icons.Outlined.Refresh, "恢复默认绑定", Modifier.size(20.dp), VerseColors.PcTextSecondary)
            }
            IconButton(onClick = onPick, enabled = enabled) {
                Icon(Icons.Outlined.ChevronRight, "选择按键", Modifier.size(20.dp), VerseColors.PcTextSecondary)
            }
        }
    }
}

/** 按键选择对话框：可同时勾选多个目标键，改动即时生效 */
@Composable
private fun KeyPickerDialog(
    buttonLabel: String,
    selected: Set<Int>,
    onDismiss: () -> Unit,
    onToggle: (Int) -> Unit,
    onClear: () -> Unit
) {
    val chosen = remember { mutableStateListOf<Int>().also { it.addAll(selected) } }

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
                KeyPickItem("清空绑定", selected = false, enabled = true) {
                    chosen.clear()
                    onClear()
                }
                GamepadBindingsRepository.bindableKeys.forEach { (name, keyCode) ->
                    KeyPickItem(name, selected = chosen.contains(keyCode), enabled = true) {
                        if (!chosen.remove(keyCode)) chosen.add(keyCode)
                        onToggle(keyCode)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
private fun KeyPickItem(name: String, selected: Boolean, enabled: Boolean, onPick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgCard,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onPick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontSize = 13.sp,
                color = VerseColors.PcTextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp), VerseColors.PcAccent)
            }
        }
    }
}

/** 切换映射配置档案 */
@Composable
private fun ProfileSwitchDialog(
    profiles: List<String>,
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VerseColors.PcBgSecondary,
        titleContentColor = VerseColors.PcTextPrimary,
        title = { Text("选择映射配置档案", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                profiles.forEach { name ->
                    KeyPickItem(name, selected = name == current, enabled = true) { onSelect(name) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 新建映射配置档案 */
@Composable
private fun ProfileCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Boolean
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VerseColors.PcBgSecondary,
        titleContentColor = VerseColors.PcTextPrimary,
        title = { Text("新建映射配置档案", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                PcTextInput(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    placeholder = "档案名称",
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, fontSize = 12.sp, color = VerseColors.PcRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().isEmpty()) {
                    error = "名称不能为空"
                } else if (!onCreate(name)) {
                    error = "名称已存在或含非法字符"
                }
            }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 手柄按键徽章配色（沿用 Xbox 手柄配色，其余为中性色） */
private fun badgeColor(label: String): Color = when (label) {
    "A" -> Color(0xFF4CAF50)
    "B" -> Color(0xFFE53935)
    "X" -> Color(0xFF1E88E5)
    "Y" -> Color(0xFFF9A825)
    else -> Color(0xFF6E6E6E)
}
