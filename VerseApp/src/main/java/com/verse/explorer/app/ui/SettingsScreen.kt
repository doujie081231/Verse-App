package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.PcDropdownRow
import com.verse.explorer.app.ui.components.PcSwitchRow
import com.verse.explorer.app.ui.components.PcTextInput
import com.verse.explorer.app.ui.components.PcTextInputRow
import com.verse.explorer.app.ui.components.pcTextFieldColors
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = SettingsRepository
    // 左侧子页索引：0=启动设置 1=个性化 2=其他
    var selectedPage by remember { mutableIntStateOf(0) }
    val pages = listOf("启动设置", "个性化", "其他")
    // 保留的原有状态变量
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
    var javaVersion by remember { mutableStateOf(settingsRepo.getJavaVersion()) }
    var language by remember { mutableStateOf(settingsRepo.getLanguage()) }
    var keepScreenOn by remember { mutableStateOf(settingsRepo.getKeepScreenOn()) }
    var showFps by remember { mutableStateOf(settingsRepo.getShowFps()) }
    var debugMode by remember { mutableStateOf(settingsRepo.getDebugMode()) }
    // 新增状态变量（对齐 PC 端结构）
    var windowTitle by remember { mutableStateOf(settingsRepo.getWindowTitle()) }
    var launcherVisibility by remember { mutableStateOf(settingsRepo.getLauncherVisibility()) }
    var showSnapshots by remember { mutableStateOf(false) }
    var themeColor by remember { mutableStateOf(settingsRepo.getThemeColor()) }
    var backgroundStyle by remember { mutableStateOf(settingsRepo.getBackgroundStyle()) }

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
            setWindowTitle(windowTitle)
            setLauncherVisibility(launcherVisibility)
            setThemeColor(themeColor)
        }
    }

    fun resetAll() {
        settingsRepo.resetToDefaults()
        maxMemory = 2048f; minMemory = 512f; gcType = "G1GC"
        customJvmArgs = ""; renderer = "gl4es"; launchMethod = "pojav"
        gameWidth = "854"; gameHeight = "480"; fullscreen = false
        downloadSource = "bmclapi"; javaVersion = "17"
        language = "简体中文"; keepScreenOn = true; showFps = false
        debugMode = false
        windowTitle = ""; launcherVisibility = "keep"
        showSnapshots = false; themeColor = "white"; backgroundStyle = "none"
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧边栏：160dp 宽，PcBgSecondary 背景
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(VerseColors.PcBgSecondary)
                .padding(top = 32.dp, start = 8.dp, end = 8.dp)
        ) {
            Text(
                "设置",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = VerseColors.PcTextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            pages.forEachIndexed { index, label ->
                val selected = selectedPage == index
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) VerseColors.PcAccent.copy(alpha = 0.1f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clickable { selectedPage = index }
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) VerseColors.PcAccent else VerseColors.PcTextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // 右侧内容区：PcBgPrimary 背景，卡片间间距 16dp
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(VerseColors.PcBgPrimary),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    pages[selectedPage],
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary
                )
            }

            when (selectedPage) {
                // 启动设置子页：合并原常规/视频/Java/启动器设置
                0 -> {
                    item {
                        PcCard(title = "启动选项") {
                            PcSwitchRow("显示快照版本", "在版本列表中显示快照版本", showSnapshots) { showSnapshots = it }
                            PcDropdownRow(
                                "启动器可见性",
                                launcherVisibility,
                                listOf(
                                    "keep" to "始终可见",
                                    "hide" to "启动后隐藏",
                                    "close" to "启动后关闭"
                                )
                            ) { launcherVisibility = it }
                            Spacer(Modifier.height(8.dp))
                            PcTextInputRow("游戏窗口标题", windowTitle, "Minecraft") { windowTitle = it }
                            PcSwitchRow("保持屏幕常亮", "游戏运行时保持屏幕常亮", keepScreenOn) { keepScreenOn = it }
                            PcSwitchRow("显示 FPS", "运行时显示帧率信息", showFps) { showFps = it }
                            PcSwitchRow("全屏启动", "启动游戏时自动全屏", fullscreen) { fullscreen = it }
                        }
                    }

                    item {
                        PcCard(title = "视频设置") {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    "游戏分辨率",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = VerseColors.PcTextSecondary
                                )
                                Spacer(Modifier.height(6.dp))
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
                                        modifier = Modifier.weight(1f),
                                        colors = pcTextFieldColors()
                                    )
                                    OutlinedTextField(
                                        value = gameHeight,
                                        onValueChange = { gameHeight = it },
                                        label = { Text("高度", fontSize = 12.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = pcTextFieldColors()
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val presets = listOf(
                                        "854x480" to "480p",
                                        "1280x720" to "720p",
                                        "1920x1080" to "1080p"
                                    )
                                    presets.forEach { (res, label) ->
                                        val w = res.split("x")[0]
                                        val h = res.split("x")[1]
                                        val active = gameWidth == w && gameHeight == h
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (active) VerseColors.PcAccent else VerseColors.PcBgTertiary,
                                            border = BorderStroke(
                                                1.dp,
                                                if (active) VerseColors.PcAccent else VerseColors.PcBorder
                                            ),
                                            modifier = Modifier.clickable {
                                                gameWidth = w; gameHeight = h
                                            }
                                        ) {
                                            Text(
                                                label,
                                                fontSize = 11.sp,
                                                color = if (active) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val rendererOptions = remember(context) {
                                RendererManager.availableRenderers(context).map { it.id to it.displayName }
                            }
                            LaunchedEffect(rendererOptions) {
                                if (rendererOptions.isNotEmpty() && rendererOptions.none { it.first == renderer }) {
                                    renderer = rendererOptions.first().first
                                }
                            }
                            PcDropdownRow(
                                "渲染器",
                                renderer,
                                rendererOptions
                            ) { renderer = it }
                        }
                    }

                    item {
                        PcCard(title = "Java 设置") {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    "内存分配",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = VerseColors.PcTextSecondary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "最大内存: ${maxMemory.toInt() / 1024}GB",
                                    fontSize = 11.sp,
                                    color = VerseColors.PcTextMuted
                                )
                                Slider(
                                    value = maxMemory,
                                    onValueChange = { maxMemory = it },
                                    valueRange = 2048f..12288f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = VerseColors.PcAccent,
                                        activeTrackColor = VerseColors.PcAccent,
                                        inactiveTrackColor = VerseColors.PcBgTertiary
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("2GB", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                    Text(
                                        "${maxMemory.toInt() / 1024}GB",
                                        fontSize = 13.sp,
                                        color = VerseColors.PcAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("12GB", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            PcDropdownRow(
                                "Java 版本",
                                javaVersion,
                                listOf(
                                    "8" to "Java 8",
                                    "17" to "Java 17",
                                    "21" to "Java 21"
                                )
                            ) { javaVersion = it }
                            PcDropdownRow(
                                "GC 类型",
                                gcType,
                                listOf(
                                    "G1GC" to "G1GC",
                                    "ZGC" to "ZGC",
                                    "ParallelGC" to "ParallelGC"
                                )
                            ) { gcType = it }
                            Spacer(Modifier.height(8.dp))
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "JVM 参数",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VerseColors.PcTextSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PcButton(
                                        text = "恢复默认",
                                        onClick = { customJvmArgs = "" },
                                        variant = PcButtonVariant.OUTLINE
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                PcTextInput(
                                    value = customJvmArgs,
                                    onValueChange = { customJvmArgs = it },
                                    placeholder = "-Xmx6G -Xms4G",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    item {
                        PcCard(title = "启动方式") {
                            PcDropdownRow(
                                "启动方式",
                                launchMethod,
                                listOf(
                                    "pojav" to "Pojav",
                                    "boat" to "Boat"
                                )
                            ) { launchMethod = it }
                            PcDropdownRow(
                                "下载源",
                                downloadSource,
                                listOf(
                                    "bmclapi" to "BMCLAPI",
                                    "official" to "官方源",
                                    "mojang" to "Mojang"
                                )
                            ) { downloadSource = it }
                        }
                    }
                }

                // 个性化子页
                1 -> {
                    item {
                        PcCard(title = "主题外观") {
                            Text(
                                "主题颜色",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = VerseColors.PcTextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ThemeColorOption(
                                    label = "黑色",
                                    color = VerseColors.PcTextPrimary,
                                    selected = themeColor == "black"
                                ) {
                                    themeColor = "black"
                                    VerseColors.applyTheme("black")
                                }
                                ThemeColorOption(
                                    label = "白色",
                                    color = VerseColors.PcBgPrimary,
                                    selected = themeColor == "white"
                                ) {
                                    themeColor = "white"
                                    VerseColors.applyTheme("white")
                                }
                            }
                        }
                    }

                    item {
                        PcCard(title = "背景设置") {
                            Text(
                                "背景风格",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = VerseColors.PcTextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BackgroundStyleOption("无背景", backgroundStyle == "none") {
                                    backgroundStyle = "none"
                                    settingsRepo.setBackgroundStyle("none")
                                }
                            }
                        }
                    }
                }

                // 其他子页
                2 -> {
                    item {
                        PcCard(title = "通用") {
                            PcDropdownRow(
                                "语言设置",
                                language,
                                listOf(
                                    "简体中文" to "简体中文",
                                    "English" to "English",
                                    "繁體中文" to "繁體中文"
                                )
                            ) { language = it }
                            PcSwitchRow("调试模式", "启用额外的调试信息和日志", debugMode) { debugMode = it }
                        }
                    }

                    item {
                        PcCard(title = "反馈与关于") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "反馈邮箱",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = VerseColors.PcTextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "support@versemc.com",
                                    fontSize = 13.sp,
                                    color = VerseColors.PcTextPrimary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "版本号",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = VerseColors.PcTextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("v1.0.0", fontSize = 13.sp, color = VerseColors.PcTextPrimary)
                            }
                            Spacer(Modifier.height(12.dp))
                            PcButton(
                                text = "恢复默认设置",
                                onClick = { resetAll() },
                                variant = PcButtonVariant.OUTLINE
                            )
                        }
                    }
                }
            }

            // 保存设置按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    PcButton(
                        text = "保存设置",
                        onClick = { saveAll() },
                        variant = PcButtonVariant.PRIMARY
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
