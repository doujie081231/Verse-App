package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.SettingsRepository

@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("常规", "视频", "Java 设置", "启动器设置")

    var maxMemory by remember { mutableFloatStateOf(settingsRepo.getMaxMemory().toFloat()) }
    var minMemory by remember { mutableFloatStateOf(settingsRepo.getMinMemory().toFloat()) }
    var gcType by remember { mutableStateOf(settingsRepo.getGcType()) }
    var customJvmArgs by remember { mutableStateOf(settingsRepo.getCustomJvmArgs()) }
    var renderer by remember { mutableStateOf(settingsRepo.getRenderer()) }
    var launchMethod by remember { mutableStateOf(settingsRepo.getLaunchMethod()) }
    var gameWidth by remember { mutableStateOf(settingsRepo.getGameWidth().toString()) }
    var gameHeight by remember { mutableStateOf(settingsRepo.getGameHeight().toString()) }
    var fullscreen by remember { mutableStateOf(settingsRepo.getFullscreen()) }
    var downloadSource by remember { mutableStateOf(settingsRepo.getDownloadSource()) }
    var autoSelectJava by remember { mutableStateOf(true) }
    var javaVersion by remember { mutableStateOf(settingsRepo.getJavaVersion()) }
    var language by remember { mutableStateOf(settingsRepo.getLanguage()) }
    var keepScreenOn by remember { mutableStateOf(settingsRepo.getKeepScreenOn()) }
    var showFps by remember { mutableStateOf(settingsRepo.getShowFps()) }
    var debugMode by remember { mutableStateOf(settingsRepo.getDebugMode()) }

    fun saveAll() {
        settingsRepo.apply {
            setMaxMemory(maxMemory.toInt())
            setMinMemory(minMemory.toInt())
            setGcType(gcType)
            setCustomJvmArgs(customJvmArgs)
            setRenderer(renderer)
            setLaunchMethod(launchMethod)
            setGameWidth(gameWidth.toIntOrNull() ?: 854)
            setGameHeight(gameHeight.toIntOrNull() ?: 480)
            setFullscreen(fullscreen)
            setDownloadSource(downloadSource)
            setJavaVersion(javaVersion)
            setLanguage(language)
            setKeepScreenOn(keepScreenOn)
            setShowFps(showFps)
            setDebugMode(debugMode)
        }
    }

    fun resetAll() {
        settingsRepo.resetToDefaults()
        maxMemory = 2048f; minMemory = 512f; gcType = "G1GC"
        customJvmArgs = ""; renderer = "zink"; launchMethod = "pojav"
        gameWidth = "854"; gameHeight = "480"; fullscreen = false
        downloadSource = "bmclapi"; javaVersion = "17"
        language = "简体中文"; keepScreenOn = true; showFps = false
        debugMode = false
    }

    val bg = Color(0xFFFAFAFA)
    val cardBg = Color(0xFFFFFFFF)
    val textPri = Color(0xFF1A1A2E)
    val textSec = Color(0xFF4A4A5E)
    val textMuted = Color(0xFF787890)
    val border = Color(0xFFE2E5EA)
    val accent = Color(0xFF5D8C3E)
    val sidebarBg = Color(0xFFF0F2F5)
    val sidebarActive = Color(0xFFE8F5E0)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(sidebarBg)
                .padding(top = 32.dp, start = 8.dp)
        ) {
            Text(
                "Minecraft Java 版启动器",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textPri,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            tabs.forEachIndexed { index, label ->
                val selected = selectedTab == index
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) sidebarActive else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clickable { selectedTab = index }
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) accent else textSec,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item { Text("常规设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPri) }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("启动器选项", textSec)
                            SwitchRow("显示快照", "在版本列表中显示快照版本", true, textPri, textMuted, accent)
                            SwitchRow("自动下载更新", "启动时自动检查并下载更新", true, textPri, textMuted, accent)
                            SwitchRow("保持屏幕常亮", "游戏运行时保持屏幕常亮", keepScreenOn, textPri, textMuted, accent) { keepScreenOn = it }
                            SwitchRow("显示 FPS", "运行时显示帧率信息", showFps, textPri, textMuted, accent) { showFps = it }
                        }
                    }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("通用设置", textSec)
                            DropdownRow("语言设置", language, listOf("简体中文" to "简体中文", "English" to "English", "繁體中文" to "繁體中文"), textPri, textSec, textMuted, border, accent) { language = it }
                            SwitchRow("全屏启动", "启动游戏时自动全屏", fullscreen, textPri, textMuted, accent) { fullscreen = it }
                        }
                    }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("高级设置", textSec)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("JVM 参数", fontSize = 14.sp, color = textPri)
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = customJvmArgs,
                                        onValueChange = { customJvmArgs = it },
                                        placeholder = { Text("-Xmx6G -Xms4G", fontSize = 13.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accent.copy(alpha = 0.4f),
                                            unfocusedBorderColor = border,
                                            focusedContainerColor = Color(0xFFF8F9FB),
                                            unfocusedContainerColor = Color(0xFFF8F9FB),
                                            focusedTextColor = textPri,
                                            unfocusedTextColor = textPri,
                                            cursorColor = accent,
                                            focusedPlaceholderColor = textMuted,
                                            unfocusedPlaceholderColor = textMuted
                                        )
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                OutlinedButton(
                                    onClick = { resetAll() },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, accent),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("恢复默认设置", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item { Text("视频设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPri) }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("游戏分辨率", textSec)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = gameWidth,
                                    onValueChange = { gameWidth = it },
                                    label = { Text("宽度", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accent.copy(alpha = 0.4f),
                                        unfocusedBorderColor = border,
                                        focusedContainerColor = Color(0xFFF8F9FB),
                                        unfocusedContainerColor = Color(0xFFF8F9FB),
                                        focusedTextColor = textPri,
                                        unfocusedTextColor = textPri,
                                        cursorColor = accent,
                                        focusedLabelColor = textMuted,
                                        unfocusedLabelColor = textMuted
                                    )
                                )
                                OutlinedTextField(
                                    value = gameHeight,
                                    onValueChange = { gameHeight = it },
                                    label = { Text("高度", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accent.copy(alpha = 0.4f),
                                        unfocusedBorderColor = border,
                                        focusedContainerColor = Color(0xFFF8F9FB),
                                        unfocusedContainerColor = Color(0xFFF8F9FB),
                                        focusedTextColor = textPri,
                                        unfocusedTextColor = textPri,
                                        cursorColor = accent,
                                        focusedLabelColor = textMuted,
                                        unfocusedLabelColor = textMuted
                                    )
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            val presets = listOf("854x480" to "480p", "1280x720" to "720p", "1920x1080" to "1080p (全高清)")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                presets.forEach { (res, label) ->
                                    val w = res.split("x")[0]
                                    val h = res.split("x")[1]
                                    val active = gameWidth == w && gameHeight == h
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (active) accent else Color(0xFFF0F2F5),
                                        modifier = Modifier.clickable { gameWidth = w; gameHeight = h }
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 11.sp,
                                            color = if (active) Color.White else textSec,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("渲染器", textSec)
                            DropdownRow(
                                "OpenGL 渲染", renderer,
                                listOf("zink" to "Zink (OpenGL over Vulkan)", "gl4es" to "GL4ES (OpenGL ES)", "virgl" to "VirGL (虚拟 GPU)"),
                                textPri, textSec, textMuted, border, accent
                            ) { renderer = it }
                            Spacer(Modifier.height(8.dp))
                            SwitchRow("全屏启动", "启动游戏时自动全屏", fullscreen, textPri, textMuted, accent) { fullscreen = it }
                        }
                    }
                }

                2 -> {
                    item { Text("Java 设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPri) }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("内存分配", textSec)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "最大内存: ${maxMemory.toInt() / 1024}GB",
                                fontSize = 13.sp,
                                color = textPri,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = maxMemory,
                                onValueChange = { maxMemory = it },
                                valueRange = 512f..16384f,
                                steps = 0,
                                colors = SliderDefaults.colors(
                                    thumbColor = accent,
                                    activeTrackColor = accent,
                                    inactiveTrackColor = border
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("2GB", fontSize = 10.sp, color = textMuted)
                                Text("${maxMemory.toInt() / 1024}GB", fontSize = 12.sp, color = accent, fontWeight = FontWeight.SemiBold)
                                Text("12GB", fontSize = 10.sp, color = textMuted)
                            }

                            Spacer(Modifier.height(16.dp))
                            SectionLabel("Java 运行环境", textSec)
                            DropdownRow(
                                "Java 版本", javaVersion,
                                listOf("8" to "Java 8", "11" to "Java 11", "17" to "Java 17", "21" to "Java 21", "25" to "Java 25"),
                                textPri, textSec, textMuted, border, accent
                            ) { javaVersion = it }
                        }
                    }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("垃圾回收", textSec)
                            DropdownRow(
                                "GC 类型", gcType,
                                listOf("G1GC" to "G1GC", "ShenandoahGC" to "ShenandoahGC", "ZGC" to "ZGC", "SerialGC" to "SerialGC", "ParallelGC" to "ParallelGC"),
                                textPri, textSec, textMuted, border, accent
                            ) { gcType = it }
                        }
                    }
                }

                3 -> {
                    item { Text("启动器设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPri) }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("启动选项", textSec)
                            DropdownRow(
                                "启动方式", launchMethod,
                                listOf("pojav" to "PojavLauncher 后端", "boat" to "Boat 后端"),
                                textPri, textSec, textMuted, border, accent
                            ) { launchMethod = it }
                        }
                    }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("下载设置", textSec)
                            DropdownRow(
                                "下载源", downloadSource,
                                listOf("auto" to "自动选择", "bmclapi" to "BMCLAPI 镜像", "mojang" to "Mojang 官方"),
                                textPri, textSec, textMuted, border, accent
                            ) { downloadSource = it }
                        }
                    }

                    item {
                        SectionCard(cardBg, border) {
                            SectionLabel("开发者", textSec)
                            SwitchRow("调试模式", "启用额外的调试信息和日志", debugMode, textPri, textMuted, accent) { debugMode = it }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { saveAll() },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, accent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("保存设置", fontSize = 13.sp)
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SectionCard(cardBg: Color, border: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardBg,
        border = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    textPri: Color,
    textMuted: Color,
    accent: Color,
    onToggle: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = textPri)
            if (description.isNotEmpty()) {
                Text(description, fontSize = 11.sp, color = textMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle?.invoke(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color(0xFFBDBDBD),
                uncheckedTrackColor = Color(0xFFE0E0E0)
            )
        )
    }
}

@Composable
private fun DropdownRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    textPri: Color,
    textSec: Color,
    textMuted: Color,
    border: Color,
    accent: Color,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.find { it.first == value }?.second ?: value

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 14.sp, color = textPri)
        Spacer(Modifier.height(6.dp))
        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8F9FB),
                border = BorderStroke(1.dp, border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(displayText, fontSize = 13.sp, color = textPri, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp), tint = textMuted)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                fontSize = 13.sp,
                                color = if (key == value) accent else textPri,
                                fontWeight = if (key == value) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = { onSelect(key); expanded = false }
                    )
                }
            }
        }
    }
}
