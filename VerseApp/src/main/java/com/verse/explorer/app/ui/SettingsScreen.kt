package com.verse.explorer.app.ui

import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.verse.explorer.app.R
import com.verse.explorer.app.BuildConfig
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.data.MousePointerSetting
import com.verse.explorer.app.data.UpdateChecker
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.PcDropdownRow
import com.verse.explorer.app.ui.components.PcSwitchRow
import com.verse.explorer.app.ui.components.PcSliderRow
import com.verse.explorer.app.ui.components.PcTextInput
import com.verse.explorer.app.ui.components.PcTextInputRow
import com.verse.explorer.app.ui.components.UpdateDialog
import com.verse.explorer.app.ui.components.pcTextFieldColors
import com.verse.explorer.app.ui.theme.ActionMenuSide
import com.verse.explorer.app.ui.theme.ColorThemeType
import com.verse.explorer.app.ui.theme.DarkMode
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.paletteStyleLabel
import com.verse.explorer.app.ui.theme.paletteSwatch
import com.materialkolor.PaletteStyle
import com.tungsten.fcl.setting.MenuSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.colorselector.ColorSelector
import net.kdt.pojavlaunch.prefs.LauncherPreferences

@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = SettingsRepository
    // 控制栏设置（windowScale 渲染比例等）持久化到 menu_setting.json；init 幂等，先于 GameActivity 初始化
    MenuSetting.init(context.filesDir)
    // 鼠标指针自定义图片与热点的持久化目录（幂等）
    MousePointerSetting.init(context.filesDir)
    // 左侧子页索引：0=启动设置 1=个性化 2=触控布局 3=控制设置 4=关于（原"其他"页已并入关于）
    var selectedPage by remember { mutableIntStateOf(0) }
    val pages = listOf("启动设置", "个性化", "触控布局", "控制设置", "关于")
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
    var notCheckGame by remember { mutableStateOf(settingsRepo.getNotCheckGame()) }
    var debugLog by remember { mutableStateOf(settingsRepo.getDebugLog()) }
    var requestMicPermission by remember { mutableStateOf(settingsRepo.getRequestMicPermission()) }
    var themeColor by remember { mutableStateOf(settingsRepo.getThemeColor()) }
    // 个性化：自定义强调色取色器 + 背景透明度 + 导入回调
    var showColorPicker by remember { mutableStateOf(false) }
    var previousAccent by remember { mutableStateOf<Color?>(null) }
    var bgOpacity by remember { mutableIntStateOf(VersePersonalization.backgroundOpacity) }
    // 背景模糊半径（0..40 Dp）/ 模糊类型 / 视频背景音量（改即存，与背景透明度一致）
    var bgBlur by remember { mutableIntStateOf(VersePersonalization.backgroundBlur) }
    var bgBlurType by remember { mutableStateOf(VersePersonalization.backgroundBlurType) }
    var bgVideoVolume by remember { mutableIntStateOf(VersePersonalization.videoVolume) }
    // 版本更新检查（关于页手动触发）
    var checkingUpdate by remember { mutableStateOf(false) }
    var aboutUpdateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
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
        VerseColors.applySchemeAccents(primary = null, secondary = null, tertiary = null)
        SettingsRepository.setThemeAccent("")
        VersePersonalization.setStyle("none")
        VersePersonalization.setOpacity(95)
        VersePersonalization.applyBackgroundBlur(0)
        VersePersonalization.applyBackgroundBlurType("background")
        VersePersonalization.applyVideoVolume(0)
        // 主题与外观回默认：浅色黑白主题 / 跟随系统 / 菜单停靠左侧 / 全屏开
        VersePersonalization.applyColorTheme(ColorThemeType.LIGHT.name)
        VersePersonalization.applyThemeCustomColor(0xFF0000FF.toInt())
        VersePersonalization.applyPaletteStyle("TonalSpot")
        VersePersonalization.applyDarkMode(DarkMode.FOLLOW_SYSTEM.name)
        VersePersonalization.applyActionMenuSide(ActionMenuSide.START.name)
        VersePersonalization.applyLauncherFullScreen(true)
        VersePersonalization.clear(context, "image")
        VersePersonalization.clear(context, "video")
        bgOpacity = 95
        bgBlur = 0
        bgBlurType = "background"
        bgVideoVolume = 0
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
                            // VerseX 渲染器暂不可用：功能开发中，开关置灰禁用（保留设置存储）
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "VerseX 渲染器",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VerseColors.PcTextMuted
                                    )
                                    Text(
                                        "暂不可用",
                                        fontSize = 12.sp,
                                        color = VerseColors.PcTextMuted
                                    )
                                }
                                Switch(
                                    checked = versexRenderer,
                                    onCheckedChange = null,
                                    enabled = false,
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = VerseColors.PcAccent.copy(alpha = 0.4f),
                                        uncheckedTrackColor = VerseColors.PcBgTertiary
                                    )
                                )
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
                            // 颜色主题多选：深色/浅色（黑白主题，行为不变）+ 七套命名色板 + 动态取色 + 自定义取色
                            val selection = VersePersonalization.colorTheme
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ThemeColorOption(
                                        label = "深色",
                                        color = VerseColors.PcTextPrimary,
                                        selected = selection == ColorThemeType.DARK.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.DARK.name) }
                                    ThemeColorOption(
                                        label = "浅色",
                                        color = VerseColors.PcBgPrimary,
                                        selected = selection == ColorThemeType.LIGHT.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.LIGHT.name) }
                                    ThemeColorOption(
                                        label = "动态取色",
                                        color = VerseColors.PcAccent,
                                        selected = selection == ColorThemeType.DYNAMIC.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.DYNAMIC.name) }
                                    ThemeColorOption(
                                        label = "自定义取色",
                                        color = Color(VersePersonalization.themeCustomColor),
                                        selected = selection == ColorThemeType.CUSTOM.name
                                    ) {
                                        VersePersonalization.applyColorTheme(ColorThemeType.CUSTOM.name)
                                        previousAccent = VerseColors.customAccent
                                        showColorPicker = true
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ThemeColorOption(
                                        label = "烬火",
                                        color = paletteSwatch(ColorThemeType.EMBERMIRE),
                                        selected = selection == ColorThemeType.EMBERMIRE.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.EMBERMIRE.name) }
                                    ThemeColorOption(
                                        label = "绯玫",
                                        color = paletteSwatch(ColorThemeType.VELVET_ROSE),
                                        selected = selection == ColorThemeType.VELVET_ROSE.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.VELVET_ROSE.name) }
                                    ThemeColorOption(
                                        label = "雾波",
                                        color = paletteSwatch(ColorThemeType.MISTWAVE),
                                        selected = selection == ColorThemeType.MISTWAVE.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.MISTWAVE.name) }
                                    ThemeColorOption(
                                        label = "冰川",
                                        color = paletteSwatch(ColorThemeType.GLACIER),
                                        selected = selection == ColorThemeType.GLACIER.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.GLACIER.name) }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ThemeColorOption(
                                        label = "翠野",
                                        color = paletteSwatch(ColorThemeType.VERDANT_FIELD),
                                        selected = selection == ColorThemeType.VERDANT_FIELD.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.VERDANT_FIELD.name) }
                                    ThemeColorOption(
                                        label = "都市灰",
                                        color = paletteSwatch(ColorThemeType.URBAN_ASH),
                                        selected = selection == ColorThemeType.URBAN_ASH.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.URBAN_ASH.name) }
                                    ThemeColorOption(
                                        label = "翠晨",
                                        color = paletteSwatch(ColorThemeType.VERDANT_DAWN),
                                        selected = selection == ColorThemeType.VERDANT_DAWN.name
                                    ) { VersePersonalization.applyColorTheme(ColorThemeType.VERDANT_DAWN.name) }
                                }
                            }
                            // 自定义取色：配色风格（PaletteStyle）选择
                            if (selection == ColorThemeType.CUSTOM.name) {
                                Spacer(Modifier.height(10.dp))
                                PcDropdownRow(
                                    "配色风格",
                                    VersePersonalization.paletteStyle,
                                    PaletteStyle.entries.map { it.name to paletteStyleLabel(it) }
                                ) { VersePersonalization.applyPaletteStyle(it) }
                            }
                            // 黑白主题下保留原有自定义强调色行为（取色器覆盖黑白主题强调色）
                            if (selection == ColorThemeType.DARK.name || selection == ColorThemeType.LIGHT.name) {
                                Spacer(Modifier.height(10.dp))
                                PcButton(
                                    text = "自定义强调色",
                                    onClick = {
                                        previousAccent = VerseColors.customAccent
                                        showColorPicker = true
                                    },
                                    variant = PcButtonVariant.OUTLINE
                                )
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
                            Spacer(Modifier.height(12.dp))
                            // 主题明暗三态：深色 / 浅色 / 跟随系统
                            PcDropdownRow(
                                "主题明暗",
                                VersePersonalization.darkMode,
                                listOf(
                                    DarkMode.FOLLOW_SYSTEM.name to "跟随系统",
                                    DarkMode.ENABLE.name to "深色",
                                    DarkMode.DISABLE.name to "浅色"
                                )
                            ) { VersePersonalization.applyDarkMode(it) }
                        }
                    }

                    item {
                        PcCard(title = "界面效果") {
                            PcSwitchRow(
                                "侧边栏液态玻璃",
                                "悬浮导航按钮与展开面板的液态玻璃折射效果（Android 13+ 生效）",
                                VersePersonalization.sidebarGlass
                            ) { VersePersonalization.applySidebarGlass(it) }
                            // 主界面操作菜单停靠侧：START=左 / END=右
                            PcDropdownRow(
                                "操作菜单停靠侧",
                                VersePersonalization.actionMenuSide,
                                listOf(
                                    ActionMenuSide.END.name to "右侧",
                                    ActionMenuSide.START.name to "左侧"
                                )
                            ) { VersePersonalization.applyActionMenuSide(it) }
                            // 启动器部分屏幕全屏：默认开=沉浸式全屏并延伸到刘海区域，改动即时生效
                            PcSwitchRow(
                                "启动器全屏",
                                "沉浸式全屏显示并延伸到刘海区域；关闭后退出沉浸并避让刘海",
                                VersePersonalization.launcherFullScreen
                            ) { VersePersonalization.applyLauncherFullScreen(it) }
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
                            // 背景模糊半径（0..40 Dp，0=关闭），背景无效时禁用
                            val backgroundValid = VersePersonalization.backgroundValid
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "背景模糊",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VerseColors.PcTextSecondary
                                    )
                                    Text(
                                        "${bgBlur}Dp",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VerseColors.PcAccent
                                    )
                                }
                                Slider(
                                    value = bgBlur.toFloat(),
                                    onValueChange = {
                                        bgBlur = it.toInt()
                                        VersePersonalization.applyBackgroundBlur(bgBlur)
                                    },
                                    valueRange = 0f..40f,
                                    enabled = backgroundValid,
                                    colors = SliderDefaults.colors(
                                        thumbColor = VerseColors.PcAccent,
                                        activeTrackColor = VerseColors.PcAccent,
                                        inactiveTrackColor = VerseColors.PcBgTertiary,
                                        disabledThumbColor = VerseColors.PcTextMuted,
                                        disabledActiveTrackColor = VerseColors.PcTextMuted
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("0Dp", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                    Text("40Dp", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                }
                            }
                            // 模糊类型切换：背景模糊（整层模糊）/ 前景毛玻璃（卡片下模糊切片），背景无效时禁用
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    "模糊类型",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = VerseColors.PcTextSecondary
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PcButton(
                                        text = "背景模糊",
                                        onClick = {
                                            bgBlurType = "background"
                                            VersePersonalization.applyBackgroundBlurType("background")
                                        },
                                        variant = if (bgBlurType == "background") PcButtonVariant.PRIMARY else PcButtonVariant.SECONDARY,
                                        enabled = backgroundValid,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PcButton(
                                        text = "前景毛玻璃",
                                        onClick = {
                                            bgBlurType = "foreground"
                                            VersePersonalization.applyBackgroundBlurType("foreground")
                                        },
                                        variant = if (bgBlurType == "foreground") PcButtonVariant.PRIMARY else PcButtonVariant.SECONDARY,
                                        enabled = backgroundValid,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            // 视频背景音量（0..100），仅背景为视频时提供
                            if (VersePersonalization.backgroundStyle == "video") {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "视频音量",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = VerseColors.PcTextSecondary
                                        )
                                        Text(
                                            "${bgVideoVolume}%",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = VerseColors.PcAccent
                                        )
                                    }
                                    Slider(
                                        value = bgVideoVolume.toFloat(),
                                        onValueChange = {
                                            bgVideoVolume = it.toInt()
                                            VersePersonalization.applyVideoVolume(bgVideoVolume)
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

                2 -> {
                    item { ControlLayoutsContent() }
                }

                // 控制设置子页：鼠标 / 手势 / 陀螺仪 / 显示与菜单 / 手柄
                3 -> {
                    item { ControlSettingsContent(context) }
                }

                // 关于子页：通用（原"其他"页并入）+ 版本信息 + 致谢 + 开源协议
                4 -> {
                    item {
                        PcCard(title = "通用") {
                            PcSwitchRow("调试模式", "启用额外的调试信息和日志", debugMode) { debugMode = it }
                        }
                    }

                    item { AboutContent() }

                    item {
                        PcCard(title = "版本更新") {
                            Column {
                                Text(
                                    text = "当前版本 v${BuildConfig.VERSION_NAME}",
                                    fontSize = 12.sp,
                                    color = VerseColors.PcTextSecondary
                                )
                                Spacer(Modifier.height(10.dp))
                                PcButton(
                                    text = if (checkingUpdate) "检查中..." else "检查更新",
                                    onClick = {
                                        if (!checkingUpdate) {
                                            scope.launch {
                                                checkingUpdate = true
                                                val info = UpdateChecker.check()
                                                checkingUpdate = false
                                                if (info != null) {
                                                    aboutUpdateInfo = info
                                                } else {
                                                    Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !checkingUpdate,
                                    variant = PcButtonVariant.OUTLINE,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    item {
                        PcButton(
                            text = "恢复默认设置",
                            onClick = { resetAll() },
                            variant = PcButtonVariant.OUTLINE,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 保存设置按钮（关于页为纯展示信息，不提供保存操作；控制设置各项即时生效并持久化）
            if (selectedPage != 4) item {
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

        aboutUpdateInfo?.let { info ->
            UpdateDialog(
                versionName = "v${info.versionName}",
                releaseDate = info.releaseDate,
                items = info.changelog,
                onIgnore = { aboutUpdateInfo = null },
                onGoUpdate = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(info.downloadUrl)
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    aboutUpdateInfo = null
                }
            )
        }

        if (showColorPicker) {
            ColorPickerOverlay(
                // 自定义取色主题：初始值取自定义种子色；黑白主题：初始值取当前强调色
                initialArgb = when (VersePersonalization.colorTheme) {
                    ColorThemeType.CUSTOM.name -> VersePersonalization.themeCustomColor
                    else -> (VerseColors.customAccent ?: Color.White).toArgb()
                },
                onCancel = {
                    VerseColors.customAccent = previousAccent
                    showColorPicker = false
                },
                onConfirm = { argb ->
                    // 自定义取色主题：写入种子色（驱动 materialkolor 配色方案）；黑白主题：覆盖强调色
                    if (VersePersonalization.colorTheme == ColorThemeType.CUSTOM.name) {
                        VersePersonalization.applyThemeCustomColor(argb)
                    }
                    SettingsRepository.setThemeAccent(String.format("%08X", argb))
                    showColorPicker = false
                }
            )
        }
    }
}

/**
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
            // 拖动实时预览：自定义取色主题同步刷新种子色，黑白主题覆盖强调色
            if (VersePersonalization.colorTheme == ColorThemeType.CUSTOM.name) {
                VersePersonalization.applyThemeCustomColor(argb)
            }
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

/**
 * 控制设置页：鼠标 / 手势 / 陀螺仪 / 显示与菜单 / 手柄。
 * 所有控制项写入 MenuSetting（menu_setting.json），改动即时持久化，
 * 与游戏内控制面板共用同一份配置。
 */
@Composable
private fun ControlSettingsContent(context: android.content.Context) {
    // LauncherPreferences 承载陀螺仪等运行时项，写入前需初始化（幂等）
    LauncherPreferences.init(context)

    var mouseMoveMode by remember { mutableIntStateOf(MenuSetting.mouseMoveMode) }
    var mouseSensitivity by remember { mutableIntStateOf((MenuSetting.mouseSensitivity * 100).toInt()) }
    var mouseSensitivityCursor by remember { mutableIntStateOf((MenuSetting.mouseSensitivityCursor * 100).toInt()) }
    var mouseSize by remember { mutableIntStateOf(MenuSetting.mouseSize) }
    var mouseOffsetX by remember { mutableIntStateOf(MenuSetting.mouseOffsetX) }
    var mouseOffsetY by remember { mutableIntStateOf(MenuSetting.mouseOffsetY) }
    var physicalMouseMode by remember { mutableStateOf(MenuSetting.isPhysicalMouseMode) }

    var disableGesture by remember { mutableStateOf(MenuSetting.isDisableGesture) }
    var disableBEGesture by remember { mutableStateOf(MenuSetting.isDisableBEGesture) }
    var gestureMode by remember { mutableIntStateOf(MenuSetting.gestureMode) }
    var disableLeftTouch by remember { mutableStateOf(MenuSetting.isDisableLeftTouch) }

    var enableGyroscope by remember { mutableStateOf(MenuSetting.isEnableGyroscope) }
    var invertGyroscope by remember { mutableStateOf(MenuSetting.isInvertGyroscope) }
    var gyroscopeSensitivity by remember { mutableIntStateOf(MenuSetting.gyroscopeSensitivity) }
    var gyroscopeSampleRate by remember { mutableIntStateOf(MenuSetting.gyroscopeSampleRate) }
    var gyroscopeSmoothing by remember { mutableStateOf(MenuSetting.isGyroscopeSmoothing) }
    var gyroscopeSmoothingWindow by remember { mutableIntStateOf(MenuSetting.gyroscopeSmoothingWindow) }

    var gestureControl by remember { mutableStateOf(MenuSetting.isGestureControl) }
    var gestureTapAction by remember { mutableIntStateOf(MenuSetting.gestureTapMouseAction) }
    var gestureLongPressAction by remember { mutableIntStateOf(MenuSetting.gestureLongPressMouseAction) }
    var gestureLongPressDelay by remember { mutableIntStateOf(MenuSetting.gestureLongPressDelay) }
    var mouseLongPressDelay by remember { mutableIntStateOf(MenuSetting.mouseLongPressDelay) }

    var lockMenuView by remember { mutableStateOf(MenuSetting.isLockMenuView) }
    var hideMenuView by remember { mutableStateOf(MenuSetting.isHideMenuView) }
    var showMemory by remember { mutableStateOf(MenuSetting.isShowMemory) }
    var disableSoftKeyAdjust by remember { mutableStateOf(MenuSetting.isDisableSoftKeyAdjust) }
    var itemBarWidth by remember { mutableIntStateOf(MenuSetting.itemBarWidth) }
    var itemBarHeight by remember { mutableIntStateOf(MenuSetting.itemBarHeight) }
    var cursorOffset by remember { mutableIntStateOf(MenuSetting.cursorOffset.toInt()) }
    var performanceMode by remember { mutableStateOf(MenuSetting.isPerformanceMode) }
    var autoFit by remember { mutableStateOf(MenuSetting.isAutoFit) }
    var autoFitDist by remember { mutableIntStateOf(MenuSetting.autoFitDist) }
    var showLog by remember { mutableStateOf(MenuSetting.isShowLog) }
    var autoShowLog by remember { mutableStateOf(MenuSetting.isAutoShowLog) }

    var gamepadDeadzone by remember { mutableIntStateOf((MenuSetting.gamepadDeadzone * 100).toInt()) }

    fun persist(key: String) = MenuSetting.notifyChange(key)

    // 鼠标指针自定义图片：导入目标形状 + 刷新计数（导入/重置后刷新预览）
    var pointerRefresh by remember { mutableIntStateOf(0) }
    var importShape by remember { mutableStateOf<MousePointerSetting.Shape?>(null) }
    val pointerImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val shape = importShape
        importShape = null
        if (uri == null || shape == null) return@rememberLauncherForActivityResult
        val ok = MousePointerSetting.importImage(context, shape, uri)
        Toast.makeText(context, if (ok) "指针图片已导入" else "指针图片导入失败", Toast.LENGTH_SHORT).show()
        if (ok) pointerRefresh++
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PcCard(title = "鼠标") {
            PcDropdownRow(
                "鼠标控制模式",
                mouseMoveMode.toString(),
                listOf("0" to "点击", "1" to "拖动")
            ) {
                mouseMoveMode = it.toInt()
                MenuSetting.mouseMoveMode = mouseMoveMode
                persist(MenuSetting.Key.MOUSE_MOVE_MODE)
            }
            PcSliderRow(
                label = "鼠标灵敏度",
                value = mouseSensitivity,
                valueRange = 100..1000,
                suffix = "%"
            ) { v ->
                mouseSensitivity = v
                MenuSetting.mouseSensitivity = v / 100.0
                persist(MenuSetting.Key.MOUSE_SENSITIVITY)
            }
            PcSliderRow(
                label = "鼠标灵敏度（当指针可见时）",
                value = mouseSensitivityCursor,
                valueRange = 100..1000,
                suffix = "%"
            ) { v ->
                mouseSensitivityCursor = v
                MenuSetting.mouseSensitivityCursor = v / 100.0
                persist(MenuSetting.Key.MOUSE_SENSITIVITY_CURSOR)
            }
            PcSliderRow(
                label = "鼠标尺寸",
                value = mouseSize,
                valueRange = 0..30,
                suffix = "dp"
            ) { v ->
                mouseSize = v
                MenuSetting.mouseSize = v
                persist(MenuSetting.Key.MOUSE_SIZE)
            }
            PcSliderRow(
                label = "鼠标偏移 X",
                value = mouseOffsetX,
                valueRange = -30..30,
                suffix = "dp"
            ) { v ->
                mouseOffsetX = v
                MenuSetting.mouseOffsetX = v
                persist(MenuSetting.Key.MOUSE_OFFSET_X)
            }
            PcSliderRow(
                label = "鼠标偏移 Y",
                value = mouseOffsetY,
                valueRange = -30..30,
                suffix = "dp"
            ) { v ->
                mouseOffsetY = v
                MenuSetting.mouseOffsetY = v
                persist(MenuSetting.Key.MOUSE_OFFSET_Y)
            }
            PcSwitchRow(
                "实体鼠标控制",
                "使用外接鼠标的相对移动控制视角，关闭时由触屏模拟光标",
                physicalMouseMode
            ) { on ->
                physicalMouseMode = on
                MenuSetting.isPhysicalMouseMode = on
                persist(MenuSetting.Key.PHYSICAL_MOUSE_MODE)
            }
            PcSliderRow(
                label = "鼠标长按延迟",
                value = mouseLongPressDelay,
                valueRange = 100..1000,
                suffix = "ms"
            ) { v ->
                mouseLongPressDelay = v
                MenuSetting.mouseLongPressDelay = v
                persist(MenuSetting.Key.MOUSE_LONG_PRESS_DELAY)
            }
        }

        MousePointerCard(refreshed = pointerRefresh, onClickImport = { shape ->
            importShape = shape
            pointerImagePicker.launch("image/*")
        })

        PcCard(title = "手势") {
            PcSwitchRow(
                "禁用手势",
                "关闭所有触屏手势操作",
                disableGesture
            ) { on ->
                disableGesture = on
                MenuSetting.isDisableGesture = on
                persist(MenuSetting.Key.DISABLE_GESTURE)
            }
            PcSwitchRow(
                "禁用仿基岩版手势",
                "关闭仿基岩版的双击/长按等手势",
                disableBEGesture
            ) { on ->
                disableBEGesture = on
                MenuSetting.isDisableBEGesture = on
                persist(MenuSetting.Key.DISABLE_BE_GESTURE)
            }
            PcDropdownRow(
                "触控模式",
                gestureMode.toString(),
                listOf("0" to "建筑模式", "1" to "战斗模式")
            ) {
                gestureMode = it.toInt()
                MenuSetting.gestureMode = gestureMode
                persist(MenuSetting.Key.GESTURE_MODE)
            }
            PcSwitchRow(
                "禁用左半屏触控",
                "屏蔽屏幕左半区域的触摸输入",
                disableLeftTouch
            ) { on ->
                disableLeftTouch = on
                MenuSetting.isDisableLeftTouch = on
                persist(MenuSetting.Key.DISABLE_LEFT_TOUCH)
            }
            PcSwitchRow(
                "手势控制",
                "开启后点击/长按手势发送下方指定的鼠标按键动作",
                gestureControl
            ) { on ->
                gestureControl = on
                MenuSetting.isGestureControl = on
                persist(MenuSetting.Key.GESTURE_CONTROL)
            }
            PcDropdownRow(
                "单击动作",
                gestureTapAction.toString(),
                listOf("0" to "鼠标右键", "1" to "鼠标左键")
            ) {
                gestureTapAction = it.toInt()
                MenuSetting.gestureTapMouseAction = gestureTapAction
                persist(MenuSetting.Key.GESTURE_TAP_MOUSE_ACTION)
            }
            PcDropdownRow(
                "长按动作",
                gestureLongPressAction.toString(),
                listOf("0" to "鼠标右键", "1" to "鼠标左键")
            ) {
                gestureLongPressAction = it.toInt()
                MenuSetting.gestureLongPressMouseAction = gestureLongPressAction
                persist(MenuSetting.Key.GESTURE_LONG_PRESS_MOUSE_ACTION)
            }
            PcSliderRow(
                label = "手势长按延迟",
                value = gestureLongPressDelay,
                valueRange = 100..1000,
                suffix = "ms",
                enabled = gestureControl
            ) { v ->
                gestureLongPressDelay = v
                MenuSetting.gestureLongPressDelay = v
                persist(MenuSetting.Key.GESTURE_LONG_PRESS_DELAY)
            }
        }

        PcCard(title = "陀螺仪") {
            PcSwitchRow(
                "陀螺仪",
                "开启后转动设备即可控制视角",
                enableGyroscope
            ) { on ->
                enableGyroscope = on
                MenuSetting.isEnableGyroscope = on
                persist(MenuSetting.Key.ENABLE_GYROSCOPE)
            }
            PcSwitchRow(
                "反转陀螺仪",
                "反转陀螺仪的 X / Y 轴方向",
                invertGyroscope
            ) { on ->
                invertGyroscope = on
                MenuSetting.isInvertGyroscope = on
                persist(MenuSetting.Key.INVERT_GYROSCOPE)
                runCatching {
                    LauncherPreferences.PREF_GYRO_INVERT_X = on
                    LauncherPreferences.PREF_GYRO_INVERT_Y = on
                }
            }
            PcSliderRow(
                label = "陀螺仪灵敏度",
                value = gyroscopeSensitivity,
                valueRange = 0..1000
            ) { v ->
                gyroscopeSensitivity = v
                MenuSetting.gyroscopeSensitivity = v
                persist(MenuSetting.Key.GYROSCOPE_SENSITIVITY)
                runCatching { LauncherPreferences.PREF_GYRO_SENSITIVITY = v * 0.5f }
            }
            PcSliderRow(
                label = "采样率",
                value = gyroscopeSampleRate,
                valueRange = 5..50,
                suffix = "ms"
            ) { v ->
                gyroscopeSampleRate = v
                MenuSetting.gyroscopeSampleRate = v
                persist(MenuSetting.Key.GYROSCOPE_SAMPLE_RATE)
                runCatching { LauncherPreferences.PREF_GYRO_SAMPLE_RATE = v }
            }
            PcSwitchRow(
                "平滑陀螺仪数据",
                "对陀螺仪读数取移动平均，减少抖动",
                gyroscopeSmoothing
            ) { on ->
                gyroscopeSmoothing = on
                MenuSetting.isGyroscopeSmoothing = on
                persist(MenuSetting.Key.GYROSCOPE_SMOOTHING)
                runCatching { LauncherPreferences.PREF_GYRO_SMOOTHING = on }
            }
            PcSliderRow(
                label = "平滑窗口",
                value = gyroscopeSmoothingWindow,
                valueRange = 2..10,
                enabled = gyroscopeSmoothing
            ) { v ->
                gyroscopeSmoothingWindow = v
                MenuSetting.gyroscopeSmoothingWindow = v
                persist(MenuSetting.Key.GYROSCOPE_SMOOTHING_WINDOW)
                runCatching { LauncherPreferences.PREF_GYRO_SMOOTHING_WINDOW = v }
            }
        }

        PcCard(title = "显示与菜单") {
            PcSwitchRow(
                "锁定菜单键",
                "锁定后菜单键不可拖动",
                lockMenuView
            ) { on ->
                lockMenuView = on
                MenuSetting.isLockMenuView = on
                persist(MenuSetting.Key.LOCK_MENU_VIEW)
            }
            PcSwitchRow(
                "隐藏菜单键",
                "隐藏游戏内菜单按键，按音量键唤出",
                hideMenuView
            ) { on ->
                hideMenuView = on
                MenuSetting.isHideMenuView = on
                persist(MenuSetting.Key.HIDE_MENU_VIEW)
            }
            PcSwitchRow(
                "显示内存",
                "在游戏内显示内存占用信息",
                showMemory
            ) { on ->
                showMemory = on
                MenuSetting.isShowMemory = on
                persist(MenuSetting.Key.SHOW_MEMORY)
            }
            PcSwitchRow(
                "显示日志",
                "在游戏内显示日志窗口",
                showLog
            ) { on ->
                showLog = on
                MenuSetting.isShowLog = on
                persist(MenuSetting.Key.SHOW_LOG)
            }
            PcSwitchRow(
                "自动显示日志",
                "启动游戏时自动展开日志窗口",
                autoShowLog
            ) { on ->
                autoShowLog = on
                MenuSetting.isAutoShowLog = on
                persist(MenuSetting.Key.AUTO_SHOW_LOG)
            }
            PcSwitchRow(
                "禁用软键盘自适应",
                "软键盘弹出时不自动调整控件布局",
                disableSoftKeyAdjust
            ) { on ->
                disableSoftKeyAdjust = on
                MenuSetting.isDisableSoftKeyAdjust = on
                persist(MenuSetting.Key.DISABLE_SOFT_KEY_ADJUST)
            }
            PcSwitchRow(
                "持续性能模式",
                "启用系统持续性能模式以稳定帧率",
                performanceMode
            ) { on ->
                performanceMode = on
                MenuSetting.isPerformanceMode = on
                persist(MenuSetting.Key.PERFORMANCE_MODE)
            }
            PcSliderRow(
                label = "物品栏缩放 - 宽度",
                value = itemBarWidth,
                valueRange = 0..100,
                suffix = "%"
            ) { v ->
                itemBarWidth = v
                MenuSetting.itemBarWidth = v
                persist(MenuSetting.Key.ITEM_BAR_WIDTH)
            }
            PcSliderRow(
                label = "物品栏缩放 - 高度",
                value = itemBarHeight,
                valueRange = 0..100,
                suffix = "%"
            ) { v ->
                itemBarHeight = v
                MenuSetting.itemBarHeight = v
                persist(MenuSetting.Key.ITEM_BAR_HEIGHT)
            }
            PcSliderRow(
                label = "光标偏移",
                value = cursorOffset,
                valueRange = -150..150
            ) { v ->
                cursorOffset = v
                MenuSetting.cursorOffset = v.toDouble()
                persist(MenuSetting.Key.CURSOR_OFFSET)
            }
        }

        PcCard(title = "控制面板") {
            PcSwitchRow(
                "自动吸附",
                "控件边缘自动对齐屏幕与相邻控件",
                autoFit
            ) { on ->
                autoFit = on
                MenuSetting.isAutoFit = on
                persist(MenuSetting.Key.AUTO_FIT)
            }
            PcSliderRow(
                label = "自动吸附间距",
                value = autoFitDist,
                valueRange = 0..10,
                suffix = "dp",
                enabled = autoFit
            ) { v ->
                autoFitDist = v
                MenuSetting.autoFitDist = v
                persist(MenuSetting.Key.AUTO_FIT_DIST)
            }
            PcSliderRow(
                label = "手柄死区",
                value = gamepadDeadzone,
                valueRange = 0..100,
                suffix = "%"
            ) { v ->
                gamepadDeadzone = v
                MenuSetting.gamepadDeadzone = v / 100.0
                persist(MenuSetting.Key.GAMEPAD_DEADZONE)
            }
        }
    }
}

/** 指针形状显示名 */
private fun shapeLabel(shape: MousePointerSetting.Shape): String = when (shape) {
    MousePointerSetting.Shape.ARROW -> "箭头"
    MousePointerSetting.Shape.IBEAM -> "输入光标"
    MousePointerSetting.Shape.CROSSHAIR -> "十字"
    MousePointerSetting.Shape.HAND -> "手形"
    MousePointerSetting.Shape.RESIZE_EW -> "左右调整"
    MousePointerSetting.Shape.RESIZE_NS -> "上下调整"
    MousePointerSetting.Shape.RESIZE_ALL -> "全向移动"
    MousePointerSetting.Shape.NOT_ALLOWED -> "禁止"
}

/** 鼠标指针（各形状的自定义图片 / 热点）设置卡片 */
@Composable
private fun MousePointerCard(
    refreshed: Int,
    onClickImport: (MousePointerSetting.Shape) -> Unit
) {
    var localRefresh by remember { mutableIntStateOf(0) }
    var hotspotShape by remember { mutableStateOf<MousePointerSetting.Shape?>(null) }
    val tick = refreshed + localRefresh

    PcCard(title = "鼠标指针") {
        MousePointerSetting.Shape.entries.forEach { shape ->
            MousePointerRow(
                shape = shape,
                refreshKey = tick,
                onClickImport = onClickImport,
                onEditHotspot = { hotspotShape = shape },
                onReset = {
                    MousePointerSetting.resetImage(shape)
                    localRefresh++
                }
            )
        }
    }

    hotspotShape?.let { shape ->
        MouseHotspotDialog(shape = shape, onClose = { hotspotShape = null })
    }
}

@Composable
private fun MousePointerRow(
    shape: MousePointerSetting.Shape,
    refreshKey: Int,
    onClickImport: (MousePointerSetting.Shape) -> Unit,
    onEditHotspot: () -> Unit,
    onReset: () -> Unit
) {
    val hasImage = MousePointerSetting.hasImage(shape)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PointerPreview(shape = shape, refreshKey = refreshKey, sizeDp = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                shapeLabel(shape),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = VerseColors.PcTextPrimary
            )
            Text(
                if (hasImage) "已使用自定义图片" else "使用内置图片",
                fontSize = 11.sp,
                color = if (hasImage) VerseColors.PcAccent else VerseColors.PcTextMuted
            )
        }
        Text(
            "导入",
            fontSize = 12.sp,
            color = VerseColors.PcAccent,
            modifier = Modifier
                .clickable { onClickImport(shape) }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Text(
            "热点",
            fontSize = 12.sp,
            color = VerseColors.PcTextSecondary,
            modifier = Modifier
                .clickable { onEditHotspot() }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        if (hasImage) {
            Text(
                "重置",
                fontSize = 12.sp,
                color = VerseColors.PcRed,
                modifier = Modifier
                    .clickable { onReset() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

/** 指针图片预览：优先自定义图片，否则内置图片 */
@Composable
private fun PointerPreview(shape: MousePointerSetting.Shape, refreshKey: Int, sizeDp: Int) {
    val bitmap = remember(shape, refreshKey) { MousePointerSetting.loadBitmap(shape) }
    Box(modifier = Modifier.size(sizeDp.dp)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(shape.defaultRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 指针热点编辑对话框：预览图上以红点标记热点，两个滑块编辑百分比坐标 */
@Composable
private fun MouseHotspotDialog(shape: MousePointerSetting.Shape, onClose: () -> Unit) {
    val initial = remember(shape) { MousePointerSetting.hotspot(shape) }
    var x by remember { mutableIntStateOf(initial[0]) }
    var y by remember { mutableIntStateOf(initial[1]) }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                "${shapeLabel(shape)}·指针热点",
                color = VerseColors.PcTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    "热点是鼠标的实际作用点（点击/悬停命中位置），以图片宽高的百分比表示。",
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted
                )
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.size(120.dp)) {
                    val bitmap = remember(shape) { MousePointerSetting.loadBitmap(shape) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(shape.defaultRes),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width * x / 100f
                        val cy = size.height * y / 100f
                        drawCircle(Color(0xFFC9350F), radius = 5.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(
                            Color.White,
                            radius = 4.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
                PcSliderRow(
                    label = "热点 X",
                    value = x,
                    valueRange = 0..100,
                    suffix = "%",
                    onValueChange = { x = it }
                )
                PcSliderRow(
                    label = "热点 Y",
                    value = y,
                    valueRange = 0..100,
                    suffix = "%",
                    onValueChange = { y = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                MousePointerSetting.setHotspot(shape, x, y)
                onClose()
            }) { Text("保存", color = VerseColors.PcAccent) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("取消", color = VerseColors.PcTextSecondary) }
        },
        containerColor = VerseColors.PcBgSecondary
    )
}
