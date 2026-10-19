package com.verse.explorer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.data.VersionMetaRepository
import com.verse.explorer.app.data.VersionSettingsRepository
import com.verse.explorer.app.ui.components.BackgroundCard
import com.verse.explorer.app.ui.components.CardTitleLayout
import com.verse.explorer.app.ui.components.DisabledAlpha
import com.verse.explorer.app.ui.components.IconTextButton
import com.verse.explorer.app.ui.components.MarqueeText
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
import com.verse.explorer.app.viewmodel.VersionsViewModel
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.java.JavaRuntimeManager
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipOutputStream

private typealias VS = VersionSettingsRepository.VersionSettings

/**
 * 管理版本页：左侧子页导航（版本概览 / 版本配置 / 安装加载器 / 模组管理），
 * 右侧内容随子页切换。
 *
 * 版本概览 = 版本头行 + 版本管理（编辑描述/重命名/导出/运行日志/删除）+ 快捷操作（各类文件夹）；
 * 版本配置 = 该版本的独立启动设置（JVM/游戏参数、图形 API、隔离、调试日志等，未开启独立设置时只读展示全局值）。
 */
@Composable
fun VersionSettingsScreen(
    versionId: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val repo = remember { VersionSettingsRepository(context) }
    val viewModel = remember { VersionsViewModel(context.applicationContext as android.app.Application) }
    val localVersions by viewModel.localVersions.collectAsStateWithLifecycle()

    var page by remember { mutableStateOf(ManagePage.OVERVIEW) }
    var settings by remember { mutableStateOf(VS()) }
    var loaded by remember { mutableStateOf(false) }
    var javaOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var controllerNames by remember { mutableStateOf<List<String>>(emptyList()) }

    // 版本概览相关状态
    var meta by remember(versionId) { mutableStateOf(VersionMetaRepository.get(context, versionId)) }
    var iconVersion by remember { mutableIntStateOf(0) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<String?>(null) }

    val version = remember(localVersions, versionId) { localVersions.find { it.id == versionId } }

    LaunchedEffect(versionId) {
        settings = repo.getVersionSettings(versionId)
        loaded = true
        withContext(Dispatchers.IO) {
            javaOptions = JavaRuntimeManager.getInstalledRuntimes(context).map {
                it.name to "${it.name}（Java ${it.version}）"
            }
            controllerNames = try {
                val dir = File(net.kdt.pojavlaunch.Tools.CTRLMAP_PATH)
                dir.listFiles { f -> f.isFile && f.extension == "json" }
                    ?.map { it.nameWithoutExtension }
                    ?.filter { it != "Default" }
                    ?.sorted()
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun update(transform: (VS) -> VS) {
        val next = transform(settings)
        settings = next
        repo.setVersionSettings(versionId, next)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val id = exportTarget
        exportTarget = null
        if (uri == null || id == null) return@rememberLauncherForActivityResult
        val versionDir = File(GameConfig.getVersionsDir(GameConfig.getGameDir(context)), id)
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out.buffered()).use { zip -> zipDirectory(versionDir, versionDir, zip) }
            } ?: error("无法写入所选位置")
        }.onSuccess {
            android.widget.Toast.makeText(context, "已导出 $id", android.widget.Toast.LENGTH_SHORT).show()
        }.onFailure {
            android.widget.Toast.makeText(context, "导出失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 自定义图标：写入 versions/<id>/modpack_icon.png，版本图标随之更新
    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val dir = File(GameConfig.getVersionsDir(GameConfig.getGameDir(context)), versionId)
            dir.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                File(dir, "modpack_icon.png").outputStream().use { input.copyTo(it) }
            } ?: error("无法读取所选图片")
        }.onSuccess {
            iconVersion++
        }.onFailure {
            android.widget.Toast.makeText(context, "设置图标失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：返回 / 主页 / 标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { navController.navigateUp() }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { navigateToTab(navController, Screen.Home.route) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Home, contentDescription = "主页", modifier = Modifier.size(20.dp))
            }
            MarqueeText(
                modifier = Modifier.weight(1f),
                text = "管理版本 - ${page.label}",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ══ 左栏：子页导航 ══
            Column(
                modifier = Modifier.width(104.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SubPageRailItem(ManagePage.OVERVIEW, page == ManagePage.OVERVIEW) {
                    page = ManagePage.OVERVIEW
                }
                SubPageRailItem(ManagePage.CONFIG, page == ManagePage.CONFIG) {
                    page = ManagePage.CONFIG
                }
                SubPageRailItem(ManagePage.LOADER, selected = false) {
                    // 跳现有版本详情页的加载器安装，传入底层原版版本号
                    val base = version?.inheritsFrom?.takeIf { it.isNotBlank() } ?: versionId
                    navController.navigate("version_detail/$base?type=release&url=")
                }
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    HorizontalDivider(modifier = Modifier.width(44.dp))
                }
                SubPageRailItem(ManagePage.MODS, selected = false) {
                    navController.navigate("installed_mods/$versionId")
                }
            }

            // ══ 右侧内容 ══
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (!loaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RoseLoader(modifier = Modifier.size(28.dp), size = 28.dp, color = VerseColors.PcAccent)
                    }
                } else when (page) {
                    ManagePage.OVERVIEW -> VersionOverviewContent(
                        versionId = versionId,
                        version = version,
                        meta = meta,
                        iconVersion = iconVersion,
                        isolateGameDir = settings.isolateGameDir,
                        onEditIcon = { iconPicker.launch("image/*") },
                        onEditDescription = { showDescriptionDialog = true },
                        onRename = {
                            renameText = versionId
                            showRenameDialog = true
                        },
                        onExport = {
                            exportTarget = versionId
                            exportLauncher.launch("$versionId.zip")
                        },
                        onDelete = { showDeleteConfirm = true }
                    )

                    ManagePage.CONFIG -> VersionConfigContent(
                        settings = settings,
                        javaOptions = javaOptions,
                        controllerNames = controllerNames,
                        onUpdate = ::update,
                        onSpecialize = {
                            settings = repo.specialize(versionId)
                        },
                        onGlobalize = {
                            repo.globalize(versionId)
                            settings = repo.getVersionSettings(versionId)
                        }
                    )

                    ManagePage.LOADER, ManagePage.MODS -> Unit
                }
            }
        }
    }

    // 编辑描述：版本摘要与自定义信息，仅展示用，不受独立设置开关影响
    if (showDescriptionDialog) {
        var summaryText by remember(meta.summary) { mutableStateOf(meta.summary) }
        var infoText by remember(meta.customInfo) { mutableStateOf(meta.customInfo) }
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            containerColor = cardColor(),
            titleContentColor = onCardColor(),
            shape = MaterialTheme.shapes.large,
            title = { Text("编辑描述", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = summaryText,
                        onValueChange = { summaryText = it.take(120) },
                        label = { Text("版本摘要") },
                        placeholder = { Text("一句话描述，显示在版本列表", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = pcTextFieldColors()
                    )
                    OutlinedTextField(
                        value = infoText,
                        onValueChange = { infoText = it.take(500) },
                        label = { Text("自定义信息") },
                        placeholder = { Text("版本备注", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = pcTextFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        VersionMetaRepository.setSummary(context, versionId, summaryText)
                        VersionMetaRepository.setCustomInfo(context, versionId, infoText)
                        meta = VersionMetaRepository.get(context, versionId)
                        showDescriptionDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showDescriptionDialog = false }) { Text("取消") }
            }
        )
    }

    // 重命名版本：目录、版本 json 文件名与 json 内 id 字段、当前选中记录、元数据一并迁移
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = cardColor(),
            titleContentColor = onCardColor(),
            shape = MaterialTheme.shapes.large,
            title = { Text("重命名版本", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { v -> renameText = v.filter { c -> !c.isWhitespace() }.take(64) },
                    label = { Text("新名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newId = renameText.trim()
                        val ok = newId.isNotEmpty() && newId != versionId &&
                            runCatching { renameVersion(context, versionId, newId) }.isSuccess
                        showRenameDialog = false
                        if (ok) {
                            if (SettingsRepository.getCustomSetting("last_version") == versionId) {
                                SettingsRepository.setCustomSetting("last_version", newId)
                            }
                            VersionMetaRepository.rename(context, versionId, newId)
                            navController.navigateUp()
                        } else {
                            android.widget.Toast.makeText(
                                context, "重命名失败：名称不可用", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = cardColor(),
            titleContentColor = onCardColor(),
            shape = MaterialTheme.shapes.large,
            title = { Text("删除版本", style = MaterialTheme.typography.titleLarge) },
            text = { Text("确定要删除 Minecraft $versionId 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVersion(versionId)
                        VersionMetaRepository.remove(context, versionId)
                        showDeleteConfirm = false
                        navController.navigateUp()
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

/** 管理版本页左侧子页 */
private enum class ManagePage(val label: String, val icon: ImageVector) {
    OVERVIEW("版本概览", Icons.Outlined.Widgets),
    CONFIG("版本配置", Icons.Outlined.Build),
    LOADER("安装加载器", Icons.Outlined.Sync),
    MODS("模组管理", Icons.Outlined.Extension)
}

/** 快捷操作文件夹：标签 → 版本目录下的子目录名 */
private val QUICK_FOLDERS = listOf(
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
private fun SubPageRailItem(
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
private fun VersionOverviewContent(
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
                        openVersionFolder(context, versionId, sub, isolateGameDir)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** 版本副标题：版本类型 + 基础版本 + 加载器 */
private fun versionSubtitle(version: MinecraftVersion, versionId: String): String {
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
private fun ActionPill(
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
private fun VersionConfigContent(
    settings: VS,
    javaOptions: List<Pair<String, String>>,
    controllerNames: List<String>,
    onUpdate: ((VS) -> VS) -> Unit,
    onSpecialize: () -> Unit,
    onGlobalize: () -> Unit
) {
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
                PcDropdownRow(
                    "渲染器",
                    settings.renderer,
                    listOf(
                        "" to "跟随全局设置",
                        "nggl4es" to "nggl4es（Krypton）",
                        "gl4es" to "gl4es 1.1.4",
                        "mobileglues" to "MobileGlues"
                    )
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
private fun GroupTitle(text: String) {
    Text(
        text = text,
        color = VerseColors.PcAccent,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun MemoryField(label: String, value: Int, onChange: (Int) -> Unit) {
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
private fun GlobalSummary() {
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
private fun SummaryLine(key: String, value: String) {
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
