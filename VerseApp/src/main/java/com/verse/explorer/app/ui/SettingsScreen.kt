package com.verse.explorer.app.ui

import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.verse.explorer.app.R
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.data.VersePersonalization
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
import com.tungsten.fcl.setting.MenuSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.colorselector.ColorSelector

@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = SettingsRepository
    // 控制栏设置（windowScale 渲染比例等）持久化到 menu_setting.json；init 幂等，先于 GameActivity 初始化
    MenuSetting.init(context.filesDir)
    // 左侧子页索引：0=启动设置 1=个性化 2=触控布局 3=其他
    var selectedPage by remember { mutableIntStateOf(0) }
    val pages = listOf("启动设置", "个性化", "触控布局", "其他")
    // 渲染分辨率比例（FCL windowScale，100% = 屏幕物理分辨率；调低降低渲染压力缓解卡顿）
    var renderScale by remember { mutableIntStateOf((MenuSetting.windowScale * 100).toInt().coerceIn(25, 300)) }
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
    var ignoreNotch by remember { mutableStateOf(DisplayNotch.ignoreNotch) }
    var downloadSource by remember { mutableStateOf(settingsRepo.getDownloadSource()) }
    var javaVersion by remember { mutableStateOf(settingsRepo.getJavaVersion()) }
    var language by remember { mutableStateOf(settingsRepo.getLanguage()) }
    var keepScreenOn by remember { mutableStateOf(settingsRepo.getKeepScreenOn()) }
    var showFps by remember { mutableStateOf(settingsRepo.getShowFps()) }
    var autoSetChinese by remember { mutableStateOf(settingsRepo.isAutoSetChinese()) }
    var debugMode by remember { mutableStateOf(settingsRepo.getDebugMode()) }
    // 新增状态变量（对齐 PC 端结构）
    var windowTitle by remember { mutableStateOf(settingsRepo.getWindowTitle()) }
    var launcherVisibility by remember { mutableStateOf(settingsRepo.getLauncherVisibility()) }
    var showSnapshots by remember { mutableStateOf(false) }
    // VerseX 渲染器：pipeline 缓存（M1）+ 整体开关（后续异步提交等接入）
    var versexRenderer by remember { mutableStateOf(settingsRepo.getVersexRenderer()) }
    var versexPipelineCache by remember { mutableStateOf(settingsRepo.getVersexPipelineCache()) }
    // 图形 API（default/opengl/vulkan，写 MC options.txt preferredGraphicsBackend）
    var graphicsBackend by remember { mutableStateOf(settingsRepo.getGraphicsBackend()) }
    // Vulkan 驱动器（true=系统驱动 / false=Turnip，UI 值转字符串驱动下拉）
    var vkDriver by remember { mutableStateOf(if (settingsRepo.getVkDriverSystem()) "system" else "turnip") }
    // 启动检查与日志（FCL 同名设置）
    var notCheckGame by remember { mutableStateOf(settingsRepo.getNotCheckGame()) }
    var debugLog by remember { mutableStateOf(settingsRepo.getDebugLog()) }
    var requestMicPermission by remember { mutableStateOf(settingsRepo.getRequestMicPermission()) }
    var themeColor by remember { mutableStateOf(settingsRepo.getThemeColor()) }
    // 个性化：自定义强调色取色器 + 背景透明度 + 导入回调
    var showColorPicker by remember { mutableStateOf(false) }
    var previousAccent by remember { mutableStateOf<Color?>(null) }
    var bgOpacity by remember { mutableIntStateOf(VersePersonalization.backgroundOpacity) }
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val appContext = context.applicationContext
            scope.launch(Dispatchers.IO) {
                val ok = VersePersonalization.importImage(appContext, uri)
                withContext(Dispatchers.Main) {
                    if (ok) bgOpacity = VersePersonalization.backgroundOpacity
                    Toast.makeText(appContext, if (ok) "背景图片已导入" else "背景图片导入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val appContext = context.applicationContext
            scope.launch(Dispatchers.IO) {
                val ok = VersePersonalization.importVideo(appContext, uri)
                withContext(Dispatchers.Main) {
                    if (ok) bgOpacity = VersePersonalization.backgroundOpacity
                    Toast.makeText(appContext, if (ok) "背景视频已导入" else "背景视频导入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
            setIgnoreNotch(ignoreNotch)
            setDownloadSource(downloadSource)
            setJavaVersion(javaVersion)
            setLanguage(language)
            setKeepScreenOn(keepScreenOn)
            setShowFps(showFps)
            setAutoSetChinese(autoSetChinese)
            setDebugMode(debugMode)
            setWindowTitle(windowTitle)
            setLauncherVisibility(launcherVisibility)
            setThemeColor(themeColor)
        }
        // 渲染分辨率比例（FCL windowScale）写入 menu_setting.json，启动链路三同步缩放生效
        MenuSetting.windowScale = renderScale.coerceIn(25, 300) / 100.0
        MenuSetting.notifyChange(MenuSetting.Key.WINDOW_SCALE)
    }

    fun resetAll() {
        settingsRepo.resetToDefaults()
        maxMemory = 2048f; minMemory = 512f; gcType = "G1GC"
        customJvmArgs = ""; renderer = "nggl4es"; launchMethod = "pojav"
        gameWidth = "854"; gameHeight = "480"; fullscreen = false
        ignoreNotch = false
        DisplayNotch.updateIgnoreNotch(false)
        downloadSource = "bmclapi"; javaVersion = "17"
        language = "简体中文"; keepScreenOn = true; showFps = false
        autoSetChinese = true
        debugMode = false
        windowTitle = ""; launcherVisibility = "keep"
        graphicsBackend = "default"
        vkDriver = if (settingsRepo.getVkDriverSystem()) "system" else "turnip"
        showSnapshots = false; themeColor = "white"
        // 同步重置个性化内存态（背景文件 + 强调色 + 透明度）
        VerseColors.customAccent = null
        SettingsRepository.setThemeAccent("")
        VersePersonalization.setStyle("none")
        VersePersonalization.setOpacity(95)
        VersePersonalization.clear(context, "image")
        VersePersonalization.clear(context, "video")
        bgOpacity = 95
    }

    // 根容器：左侧栏 + 内容区；顶层承载取色器全屏宿主（SideDialogView 从右侧滑入）
    Box(modifier = Modifier.fillMaxSize()) {
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
                            PcSwitchRow("VerseX 渲染器", "启用 VerseX 渲染优化层（pipeline 缓存 + 异步提交，显著提升帧率）", versexRenderer) { on ->
                                versexRenderer = on
                                settingsRepo.setVersexRenderer(on)
                            }
                            PcSwitchRow("VerseX Pipeline 缓存", "缓存已编译的渲染管线对象，消除世界加载/切场景编译卡顿", versexPipelineCache) { on ->
                                versexPipelineCache = on
                                settingsRepo.setVersexPipelineCache(on)
                            }
                            PcDropdownRow(
                                "图形 API",
                                graphicsBackend,
                                listOf(
                                    "default" to "自动（默认）",
                                    "opengl" to "OpenGL",
                                    "vulkan" to "Vulkan"
                                )
                            ) { graphicsBackend = it; settingsRepo.setGraphicsBackend(it) }
                            PcDropdownRow(
                                "Vulkan 驱动器",
                                vkDriver,
                                listOf(
                                    "system" to "系统驱动",
                                    "turnip" to "Turnip（Adreno 专用）"
                                )
                            ) {
                                vkDriver = it
                                settingsRepo.setVkDriverSystem(it == "system")
                            }
                            PcSwitchRow(
                                "不检查游戏完整性",
                                "跳过启动前的游戏文件完整性检查以加快启动",
                                notCheckGame
                            ) { notCheckGame = it; settingsRepo.setNotCheckGame(it) }
                            PcSwitchRow(
                                "输出调试日志",
                                "游戏内输出详细调试日志，便于排查问题",
                                debugLog
                            ) { debugLog = it; settingsRepo.setDebugLog(it) }
                            PcSwitchRow(
                                "请求麦克风权限",
                                "启动游戏时请求麦克风权限（用于语音聊天模组）",
                                requestMicPermission
                            ) { requestMicPermission = it; settingsRepo.setRequestMicPermission(it) }
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
                            PcSwitchRow("自动设置为中文", "启动游戏时自动将游戏语言设置为简体中文", autoSetChinese) { autoSetChinese = it }
                            PcSwitchRow("全屏启动", "启动游戏时自动全屏", fullscreen) { fullscreen = it }
                            PcSwitchRow("忽略刘海（全屏）", "忽略屏幕刘海区域全屏显示；关闭时游戏画面避让刘海缩小", ignoreNotch) { on ->
                                ignoreNotch = on
                                // 即时生效：内容容器让出/占回 displayCutout 区（MainActivity 响应式读取）
                                DisplayNotch.updateIgnoreNotch(on)
                            }
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
                                Spacer(Modifier.height(12.dp))
                                // 渲染分辨率比例（FCL windowScale：游戏渲染分辨率 = 屏幕 × 比例，三同步缩放，
                                // 调低降低渲染压力缓解卡顿；对应游戏内控制面板的「窗口比例」滑块）
                                Text(
                                    "渲染分辨率比例",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = VerseColors.PcTextSecondary
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        100 to "100%",
                                        85 to "85%",
                                        70 to "70%",
                                        55 to "55%"
                                    ).forEach { (v, label) ->
                                        val active = renderScale == v
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (active) VerseColors.PcAccent else VerseColors.PcBgTertiary,
                                            border = BorderStroke(
                                                1.dp,
                                                if (active) VerseColors.PcAccent else VerseColors.PcBorder
                                            ),
                                            modifier = Modifier.clickable { renderScale = v }
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
                                Spacer(Modifier.height(8.dp))
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
                            // Java 运行时管理入口（原侧边栏 Java 页，收进设置；FCL 全局管理中的 Java 管理）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate(Screen.Java.route) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Java 运行时管理",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = VerseColors.PcTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "安装 / 默认版本",
                                    fontSize = 11.sp,
                                    color = VerseColors.PcTextMuted
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = VerseColors.PcTextMuted
                                )
                            }
                            androidx.compose.material3.HorizontalDivider(color = VerseColors.PcBorder, thickness = 1.dp)
                            Spacer(Modifier.height(6.dp))
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
                                ThemeColorOption(
                                    label = "自定义",
                                    color = VerseColors.customAccent ?: VerseColors.PcAccent,
                                    selected = VerseColors.customAccent != null
                                ) {
                                    previousAccent = VerseColors.customAccent
                                    showColorPicker = true
                                }
                            }
                            // 已设置自定义强调色时提供恢复入口
                            if (VerseColors.customAccent != null) {
                                Spacer(Modifier.height(10.dp))
                                PcButton(
                                    text = "恢复默认强调色",
                                    onClick = {
                                        VerseColors.customAccent = null
                                        SettingsRepository.setThemeAccent("")
                                    },
                                    variant = PcButtonVariant.OUTLINE
                                )
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
                                BackgroundStyleOption(
                                    "无背景",
                                    VersePersonalization.backgroundStyle == "none"
                                ) {
                                    VersePersonalization.setStyle("none")
                                }
                                BackgroundStyleOption(
                                    "图片背景",
                                    VersePersonalization.backgroundStyle == "image"
                                ) {
                                    if (VersePersonalization.backgroundImagePath == null) {
                                        imagePicker.launch("image/*")
                                    } else {
                                        VersePersonalization.setStyle("image")
                                    }
                                }
                                BackgroundStyleOption(
                                    "视频背景",
                                    VersePersonalization.backgroundStyle == "video"
                                ) {
                                    if (VersePersonalization.backgroundVideoPath == null) {
                                        videoPicker.launch("video/*")
                                    } else {
                                        VersePersonalization.setStyle("video")
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PcButton(
                                    text = "导入图片",
                                    onClick = { imagePicker.launch("image/*") },
                                    variant = PcButtonVariant.OUTLINE,
                                    modifier = Modifier.weight(1f)
                                )
                                PcButton(
                                    text = "导入视频",
                                    onClick = { videoPicker.launch("video/*") },
                                    variant = PcButtonVariant.OUTLINE,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            // 背景透明度（0..100，实时生效并持久化）
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "背景透明度",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VerseColors.PcTextSecondary
                                    )
                                    Text(
                                        "${bgOpacity}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VerseColors.PcAccent
                                    )
                                }
                                Slider(
                                    value = bgOpacity.toFloat(),
                                    onValueChange = {
                                        bgOpacity = it.toInt()
                                        VersePersonalization.setOpacity(bgOpacity)
                                    },
                                    valueRange = 0f..100f,
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
                                    Text("0%", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                    Text("100%", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            PcButton(
                                text = "移除背景",
                                onClick = {
                                    VersePersonalization.clear(context, "image")
                                    VersePersonalization.clear(context, "video")
                                    bgOpacity = VersePersonalization.backgroundOpacity
                                },
                                variant = PcButtonVariant.OUTLINE
                            )
                        }
                    }
                }

                // 触控布局子页：本地布局管理 + 布局仓库下载（FCL ControllerManagePage/RepoPage 对应）
                2 -> {
                    item { ControlLayoutsContent() }
                }

                // 其他子页
                3 -> {
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

        // 顶层：Pojav HSV 取色器（打开时全屏 FrameLayout 宿主，从右滑入）
        if (showColorPicker) {
            ColorPickerOverlay(
                initialArgb = (VerseColors.customAccent ?: Color.White).toArgb(),
                onCancel = {
                    VerseColors.customAccent = previousAccent
                    showColorPicker = false
                },
                onConfirm = { argb ->
                    SettingsRepository.setThemeAccent(String.format("%08X", argb))
                    showColorPicker = false
                }
            )
        }
    }
}

/**
 * Pojav HSV 取色器集成层：全屏 AndroidView FrameLayout 作 SideDialogView 宿主，
 * 拖动实时预览（VerseColors.customAccent），确定/取消/返回均先滑出再关闭。
 */
@Composable
private fun ColorPickerOverlay(
    initialArgb: Int,
    onCancel: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val context = LocalContext.current
    var hostRef by remember { mutableStateOf<FrameLayout?>(null) }
    var selectorRef by remember { mutableStateOf<ColorSelector?>(null) }
    var latestArgb by remember { mutableIntStateOf(initialArgb) }

    fun close(picker: ColorSelector?) {
        picker?.disappear(true)
    }

    // 系统返回：等价取消（恢复原强调色）
    BackHandler {
        close(selectorRef)
        onCancel()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            FrameLayout(ctx).also { fl ->
                // SideDialogView 滑入动画依赖 currentDisplayMetrics，MainActivity 场景需先初始化
                Tools.init(ctx)
                hostRef = fl
            }
        }
    )

    LaunchedEffect(Unit) {
        val host = hostRef ?: return@LaunchedEffect
        val selector = ColorSelector(context, host, null).also { selectorRef = it }
        selector.setTitle(R.string.color_picker_title)
        selector.setAlphaEnabled(false)
        selector.setColorSelectionListener { argb ->
            latestArgb = argb
            VerseColors.customAccent = Color(argb)
        }
        selector.setStartButtonListener(R.string.color_picker_cancel) {
            close(selector)
            onCancel()
        }
        selector.setEndButtonListener(R.string.color_picker_confirm) {
            close(selector)
            onConfirm(latestArgb)
        }
        selector.show(true, initialArgb)
    }
}
