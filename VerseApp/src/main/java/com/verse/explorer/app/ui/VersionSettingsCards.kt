package com.verse.explorer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.core.data.VersionMetaRepository
import com.verse.explorer.core.data.VersionSettingsRepository
import com.verse.explorer.app.ui.components.BackgroundCard
import com.verse.explorer.app.ui.components.CardTitleLayout
import com.verse.explorer.app.ui.components.DisabledAlpha
import com.verse.explorer.app.ui.components.IconTextButton
import com.verse.explorer.app.ui.components.MarqueeText
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.PcDropdownRow
import com.verse.explorer.app.ui.components.PcSwitchRow
import com.verse.explorer.app.ui.components.PcTextInputRow
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.pcTextFieldColors
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.cardColor
import com.verse.explorer.app.ui.theme.itemColor
import com.verse.explorer.app.ui.theme.onCardColor
import com.verse.explorer.app.ui.theme.onItemColor
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionType
import com.verse.explorer.launcher.renderer.RendererManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal typealias VS = VersionSettingsRepository.VersionSettings

internal enum class ManagePage(val label: String, val icon: ImageVector) {
    OVERVIEW("版本概览", Icons.Outlined.Widgets),
    CONFIG("版本配置", Icons.Outlined.Build),
    LOADER("安装加载器", Icons.Outlined.Sync),
    MODS("模组管理", Icons.Outlined.Extension),
    RESOURCEPACKS("材质包管理", Icons.Outlined.Palette),
    SHADERS("光影包管理", Icons.Outlined.Flare),
    SAVES("存档管理", Icons.Outlined.Inventory2)
}

/** 快捷操作文件夹：标签 → 版本目录下的子目录名 */
internal val QUICK_FOLDERS = listOf(
    "版本文件夹" to "",
    "存档文件夹" to "saves",
    "资源包文件夹" to "resourcepacks",
    "光影包文件夹" to "shaderpacks",
    "模组文件夹" to "mods",
    "屏幕截图文件夹" to "screenshots",
    "日志文件夹" to "logs",
    "崩溃报告文件夹" to "crash-reports"
)

/** 左侧子页导航条目（图标在上、文字在下） */
@Composable
internal fun SubPageRailItem(
    page: ManagePage,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = page.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = page.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 版本概览：版本头行 + 版本管理 + 快捷操作 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VersionOverviewContent(
    versionId: String,
    version: MinecraftVersion?,
    meta: VersionMetaRepository.VersionMeta,
    iconVersion: Int,
    isolateGameDir: Boolean,
    onEditIcon: () -> Unit,
    onEditDescription: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val displayVersion = version ?: MinecraftVersion(id = versionId, type = VersionType.RELEASE)
    var browserDir by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 版本头行
        BackgroundCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VersionIcon(version = displayVersion, size = 38.dp, iconKey = iconVersion)
                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(text = versionId, style = MaterialTheme.typography.labelLarge)
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = versionSubtitle(displayVersion, versionId),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                IconTextButton(
                    onClick = onEditIcon,
                    imageVector = Icons.Outlined.Image,
                    text = "自定义图标"
                )
            }
        }

        // 版本管理
        BackgroundCard(modifier = Modifier.fillMaxWidth()) {
            CardTitleLayout {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    text = "版本管理",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionPill(text = "编辑描述", onClick = onEditDescription)
                ActionPill(text = "重命名版本", onClick = onRename)
                ActionPill(text = "导出整合包", onClick = onExport)
                ActionPill(text = "运行日志", enabled = false) {}
                ActionPill(text = "删除版本", onClick = onDelete)
            }
        }

        // 快捷操作
        BackgroundCard(modifier = Modifier.fillMaxWidth()) {
            CardTitleLayout {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    text = "快捷操作",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_FOLDERS.forEach { (label, sub) ->
                    ActionPill(text = label) {
                        val gameDir = GameConfig.getGameDir(context)
                        val base = if (isolateGameDir) {
                            File(GameConfig.getVersionsDir(gameDir), versionId)
                        } else {
                            gameDir
                        }
                        browserDir = if (sub.isBlank()) base else File(base, sub)
                    }
                }
                ActionPill(text = "用其他应用打开") {
                    val gameDir = GameConfig.getGameDir(context)
                    val base = if (isolateGameDir) {
                        File(GameConfig.getVersionsDir(gameDir), versionId)
                    } else {
                        gameDir
                    }
                    openFolderWithOtherApp(context, base)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    browserDir?.let { dir ->
        FolderBrowserDialog(title = "版本文件夹", startDir = dir, onDismiss = { browserDir = null })
    }
}

/** 版本副标题：版本类型 + 基础版本 + 加载器 */
internal fun versionSubtitle(version: MinecraftVersion, versionId: String): String {
    val base = version.inheritsFrom.orEmpty()
    val hint = version.loaderHint.orEmpty()
    return listOf(
        versionTypeLabel(version),
        if (base.isNotBlank() && base != versionId) "基于 $base" else null,
        hint.takeIf { it.isNotBlank() }
    ).filterNotNull().joinToString(" · ")
}

/** 药丸操作按钮 */
@Composable
internal fun ActionPill(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier.alpha(if (enabled) 1f else DisabledAlpha),
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = itemColor(),
        contentColor = onItemColor()
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** 版本配置：该版本的独立启动设置 */
@Composable
internal fun VersionConfigContent(
    settings: VS,
    javaOptions: List<Pair<String, String>>,
    controllerNames: List<String>,
    onUpdate: ((VS) -> VS) -> Unit,
    onSpecialize: () -> Unit,
    onGlobalize: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PcCard(title = "独立设置") {
            PcSwitchRow(
                "启用版本独立设置",
                if (settings.usesGlobal) "当前跟随全局启动设置" else "以下设置仅对本版本生效",
                !settings.usesGlobal
            ) { enable ->
                if (enable) onSpecialize() else onGlobalize()
            }
            if (settings.usesGlobal) {
                Spacer(Modifier.height(4.dp))
                GlobalSummary()
            }
        }

        if (!settings.usesGlobal) {
            GroupTitle("版本设置")
            PcCard {
                PcSwitchRow(
                    "版本隔离",
                    "该版本使用独立运行目录（saves/mods 等随版本存放）",
                    settings.isolateGameDir
                ) { onUpdate { s -> s.copy(isolateGameDir = it) } }
                PcSwitchRow(
                    "不检查游戏完整性",
                    "跳过启动前的游戏文件完整性检查以加快启动",
                    settings.notCheckGame
                ) { onUpdate { s -> s.copy(notCheckGame = it) } }
                PcSwitchRow(
                    "不检查模组",
                    "启动时不对模组文件做完整性校验",
                    settings.notCheckMod
                ) { onUpdate { s -> s.copy(notCheckMod = it) } }
            }
            PcCard {
                // 渲染器候选 = 跟随全局 + 内置 + 已安装插件；已选插件被卸载时保留原值便于提示
                val rendererChoices = remember(settings.renderer) {
                    val base = listOf("" to "跟随全局设置") +
                        RendererManager.availableRenderers(context).map { cfg ->
                            val label = if (cfg.pluginPackage.isNotEmpty()) {
                                "${cfg.displayName}（插件）"
                            } else cfg.displayName
                            cfg.id to label
                        }
                    if (settings.renderer.isNotEmpty() && base.none { it.first == settings.renderer }) {
                        base + (settings.renderer to settings.renderer)
                    } else base
                }
                PcDropdownRow(
                    "渲染器",
                    settings.renderer,
                    rendererChoices
                ) { onUpdate { s -> s.copy(renderer = it) } }
                PcDropdownRow(
                    "Vulkan 驱动器",
                    if (settings.vkDriverSystem) "system" else "turnip",
                    listOf(
                        "system" to "系统驱动",
                        "turnip" to "Turnip（Adreno 专用）"
                    )
                ) { onUpdate { s -> s.copy(vkDriverSystem = it == "system") } }
                PcDropdownRow(
                    "图形 API",
                    settings.graphicsBackend,
                    listOf(
                        "default" to "自动（默认）",
                        "opengl" to "OpenGL",
                        "vulkan" to "Vulkan"
                    )
                ) { onUpdate { s -> s.copy(graphicsBackend = it) } }
            }
            if (controllerNames.isNotEmpty() || settings.forceResolution) {
                PcCard {
                    if (controllerNames.isNotEmpty()) {
                        PcDropdownRow(
                            "触控布局",
                            settings.controller,
                            listOf("" to "默认布局") + controllerNames.map { it to it }
                        ) { onUpdate { s -> s.copy(controller = it) } }
                    }
                    PcSwitchRow(
                        "强制分辨率",
                        "使用固定窗口尺寸启动，不跟随窗口比例缩放",
                        settings.forceResolution
                    ) { onUpdate { s -> s.copy(forceResolution = it) } }
                    if (settings.forceResolution) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = if (settings.width > 0) settings.width.toString() else "",
                                onValueChange = { v ->
                                    onUpdate { s -> s.copy(width = v.filter { c -> c.isDigit() }.take(5).toIntOrNull() ?: 0) }
                                },
                                label = { Text("宽度", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = pcTextFieldColors()
                            )
                            OutlinedTextField(
                                value = if (settings.height > 0) settings.height.toString() else "",
                                onValueChange = { v ->
                                    onUpdate { s -> s.copy(height = v.filter { c -> c.isDigit() }.take(5).toIntOrNull() ?: 0) }
                                },
                                label = { Text("高度", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = pcTextFieldColors()
                            )
                        }
                    }
                }
            }

            GroupTitle("游戏设置")
            PcCard {
                PcDropdownRow(
                    "Java 运行时",
                    settings.java,
                    listOf(VersionSettingsRepository.JAVA_AUTO to "自动选择") + javaOptions
                ) { onUpdate { s -> s.copy(java = it) } }
                PcSwitchRow(
                    "不检查 JVM",
                    "跳过启动前对定向 Java 运行时的完整性校验",
                    settings.notCheckJVM
                ) { onUpdate { s -> s.copy(notCheckJVM = it) } }
                PcSwitchRow(
                    "自动分配内存",
                    "按启动时的实时可用内存自动计算",
                    settings.autoMemory
                ) { onUpdate { s -> s.copy(autoMemory = it) } }
                if (!settings.autoMemory) {
                    MemoryField("最大内存 (MB)", settings.maxMemory) {
                        onUpdate { s -> s.copy(maxMemory = it) }
                    }
                    MemoryField("最小内存 (MB)", settings.minMemory) {
                        onUpdate { s -> s.copy(minMemory = it) }
                    }
                }
            }
            PcCard {
                PcTextInputRow(
                    "JVM 参数（空格分隔）",
                    settings.javaArgs,
                    "-Xmx1024m ..."
                ) { onUpdate { s -> s.copy(javaArgs = it) } }
                PcTextInputRow(
                    "游戏参数（空格分隔）",
                    settings.minecraftArgs,
                    "--width 1280 ..."
                ) { onUpdate { s -> s.copy(minecraftArgs = it) } }
                PcTextInputRow(
                    "启动后进入服务器",
                    settings.serverIp,
                    "ip 或 ip:端口"
                ) { onUpdate { s -> s.copy(serverIp = it) } }
            }

            GroupTitle("其他")
            PcCard {
                PcSwitchRow(
                    "输出调试日志",
                    "游戏内输出详细调试日志，便于排查问题",
                    settings.debugLog
                ) { onUpdate { s -> s.copy(debugLog = it) } }
            }

            GroupTitle("操作")
            PcCard {
                PcSwitchRow(
                    "恢复跟随全局",
                    "清除本版本的全部独立设置，回到全局启动设置",
                    false,
                    onToggle = { _ -> onGlobalize() }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** 设置分组标题（主题色小字，统领下方卡片组） */
@Composable
internal fun GroupTitle(text: String) {
    Text(
        text = text,
        color = VerseColors.PcAccent,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
internal fun MemoryField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value > 0) value.toString() else "") }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = VerseColors.PcTextSecondary
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { c -> c.isDigit() }.take(5)
                onChange(text.toIntOrNull() ?: 0)
            },
            placeholder = { Text("自动", fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = pcTextFieldColors()
        )
    }
}

@Composable
internal fun GlobalSummary() {
    val backend = when (SettingsRepository.getGraphicsBackend()) {
        "opengl" -> "OpenGL"
        "vulkan" -> "Vulkan"
        else -> "自动"
    }
    SummaryLine("图形 API", backend)
    SummaryLine("渲染器", SettingsRepository.getRenderer())
    val mem = SettingsRepository.getMaxMemory()
    SummaryLine("内存", if (mem > 0) "${mem} MB 上限（自动分配）" else "自动分配")
    SummaryLine("调试日志", if (SettingsRepository.getDebugLog()) "开" else "关")
}

@Composable
internal fun SummaryLine(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, fontSize = 12.sp, color = VerseColors.PcTextMuted)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = VerseColors.PcTextSecondary)
    }
}

/**
 * 版本资源目录子页：列出并管理该版本的一类资源目录（材质包 / 光影包 / 存档）。
 * 未开版本隔离时取游戏根目录下的同名目录，与启动时实际使用的目录一致。
 */
@Composable
internal fun VersionFolderTab(
    versionId: String,
    subDir: String,
    isolateGameDir: Boolean,
    title: String,
    importLabel: String,
    /** 存档以目录为单位：导入 zip 时解压成子目录 */
    importAsFolder: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var showFolderBrowser by remember { mutableStateOf(false) }

    val dir = remember(versionId, subDir, isolateGameDir) {
        val gameDir = GameConfig.getGameDir(context)
        val base = if (isolateGameDir) File(GameConfig.getVersionsDir(gameDir), versionId) else gameDir
        File(base, subDir).apply { mkdirs() }
    }

    LaunchedEffect(dir, reloadTick) {
        loading = true
        entries = withContext(Dispatchers.IO) {
            dir.listFiles()
                ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                ?: emptyList()
        }
        loading = false
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) { queryDisplayName(context, uri) }
            runCatching {
                withContext(Dispatchers.IO) {
                    if (importAsFolder && name.endsWith(".zip", ignoreCase = true)) {
                        val target = File(dir, name.dropLast(4))
                        context.contentResolver.openInputStream(uri)?.use { unzipInto(it, target) }
                            ?: error("无法读取所选文件")
                    } else {
                        File(dir, name).outputStream().use { out ->
                            context.contentResolver.openInputStream(uri)?.use { it.copyTo(out) }
                                ?: error("无法读取所选文件")
                        }
                    }
                }
            }.onSuccess {
                toast(context, "已导入 $name")
            }.onFailure {
                toast(context, "导入失败：${it.message ?: "未知错误"}")
            }
            reloadTick++
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "$title（${entries.size} 项）",
                style = MaterialTheme.typography.labelMedium,
                color = VerseColors.PcTextMuted
            )
            PcButton(
                text = "打开文件夹",
                // 游戏目录在 Android/data 或应用内部目录下，系统/第三方文件管理器无权访问，
                // 这里直接用应用内浏览窗口展示目录内容（与其他安卓启动器一致）
                onClick = { showFolderBrowser = true },
                variant = PcButtonVariant.OUTLINE
            )
            PcButton(
                text = importLabel,
                onClick = { importPicker.launch("*/*") },
                variant = PcButtonVariant.OUTLINE
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RoseLoader(modifier = Modifier.size(28.dp), size = 28.dp, color = VerseColors.PcAccent)
            }

            entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("该目录下暂无$title", fontSize = 13.sp, color = VerseColors.PcTextMuted)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(entries, key = { it.absolutePath }) { entry ->
                    FolderEntryRow(entry = entry, onDelete = { pendingDelete = entry })
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = cardColor(),
            titleContentColor = onCardColor(),
            shape = MaterialTheme.shapes.large,
            title = { Text("删除$title", style = MaterialTheme.typography.titleLarge) },
            text = { Text("确定要删除「${target.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { target.deleteRecursively() }
                        pendingDelete = null
                        reloadTick++
                    }
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    if (showFolderBrowser) {
        FolderBrowserDialog(title = title, startDir = dir, onDismiss = { showFolderBrowser = false })
    }
}
