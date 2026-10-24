package com.verse.explorer.app.ui

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.app.data.MousePointerSetting
import com.verse.explorer.app.data.UpdateChecker
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.launcher.renderer.RendererPluginManager
import java.io.File
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.navButtonAvoid
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
    // 子页索引与 pages 列表的顺序保持一致。
    var selectedPage by remember { mutableIntStateOf(0) }
    val pages = listOf("启动设置", "个性化", "触控布局", "控制设置", "手柄设置", "关于")
    var renderScale by remember { mutableIntStateOf((MenuSetting.windowScale * 100).toInt().coerceIn(25, 300)) }
    // 启动设置状态。
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

    // 渲染器插件：安装/卸载后回到页面时重扫，保证列表即时更新
    var pluginRevision by remember { mutableIntStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) pluginRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val pluginApkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rawName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                    }
                    val name = rawName?.takeIf { it.endsWith(".apk", ignoreCase = true) } ?: "renderer_plugin.apk"
                    val dir = File(context.cacheDir, "plugin_import").apply { mkdirs() }
                    val file = File(dir, name)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("无法读取文件")
                    file
                }
            }
            result.onSuccess { file ->
                runCatching {
                    val apkUri = androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }.onFailure {
                    Toast.makeText(context, "无法启动安装程序：${it.message}", Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                Toast.makeText(context, "读取插件 APK 失败：${it.message}", Toast.LENGTH_LONG).show()
            }
            pluginRevision++
        }
    }

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
        // 左侧边栏：160dp 宽，PcBgSecondary 背景；子页较多时纵向可滚动，避免被挤出屏幕
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .background(VerseColors.PcBgSecondary)
                .verticalScroll(rememberScrollState())
                .padding(top = 32.dp, start = 8.dp, end = 8.dp)
        ) {
            Text(
                "设置",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = VerseColors.PcTextPrimary,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navButtonAvoid()
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
                            // 渲染器列表 = 内置（库存在）+ 已安装插件；安装/卸载插件后按 revision 重扫
                            val rendererOptions by produceState(
                                initialValue = emptyList<Pair<String, String>>(),
                                context, pluginRevision
                            ) {
                                value = withContext(Dispatchers.IO) {
                                    RendererPluginManager.load(context, force = true)
                                    RendererManager.availableRenderers(context)
                                }.map { cfg ->
                                    val label = if (cfg.pluginPackage.isNotEmpty()) {
                                        "${cfg.displayName}（插件）"
                                    } else cfg.displayName
                                    cfg.id to label
                                }
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
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pluginApkPicker.launch(arrayOf("application/vnd.android.package-archive"))
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "导入渲染器插件",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VerseColors.PcTextPrimary
                                    )
                                    Text(
                                        "选择已下载的插件 APK 安装，安装完成后自动识别并出现在上方列表",
                                        fontSize = 11.sp,
                                        color = VerseColors.PcTextSecondary
                                    )
                                }
                                Text("导入", fontSize = 12.sp, color = VerseColors.PcAccent)
                            }
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
                            PcSwitchRow(
                                "侧边栏按钮显示功能字",
                                "在导航按钮图标下方显示功能名（关闭后仅显示图标）",
                                VersePersonalization.sidebarLabels
                            ) { VersePersonalization.applySidebarLabels(it) }
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

                // 手柄设置子页：手柄按键绑定（游戏内 / 菜单内）
                4 -> {
                    item { GamepadSettingsContent() }
                }

                // 关于子页包含通用设置、版本信息、致谢和开源协议。
                5 -> {
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

            // 保存设置按钮（关于页为纯展示信息，不提供保存操作；控制设置/手柄设置各项即时生效并持久化）
            if (selectedPage != 5) item {
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
