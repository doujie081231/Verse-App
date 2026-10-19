package com.verse.explorer.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.data.VersionMetaRepository
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.components.BackgroundCard
import com.verse.explorer.app.ui.components.DisabledAlpha
import com.verse.explorer.app.ui.components.EdgeDirection
import com.verse.explorer.app.ui.components.IconTextButton
import com.verse.explorer.app.ui.components.LittleTextLabel
import com.verse.explorer.app.ui.components.MarqueeText
import com.verse.explorer.app.ui.components.ModpackImportFlow
import com.verse.explorer.app.ui.components.ScalingLabel
import com.verse.explorer.app.ui.components.TextRailItem
import com.verse.explorer.app.ui.components.fadeEdge
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.cardColor
import com.verse.explorer.app.ui.theme.itemColor
import com.verse.explorer.app.ui.theme.onCardColor
import com.verse.explorer.app.ui.theme.onItemColor
import com.verse.explorer.app.viewmodel.VersionsViewModel
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionType
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 已安装版本管理页（1:1 复刻 ZL2 ui/screens/content/VersionsManageScreen.kt 的右侧版本列表栏）。
 *
 * 结构对位关系：
 *  - 顶部悬浮操作栏 = ZL2 VersionsLayout 顶部 Row（左侧 Surface 内「刷新 / 安装」，
 *    右侧 Surface 内横向滚动的版本分类 ALL / 原版 / 模组）
 *  - 列表条目 = ZL2 VersionItemLayout（Surface + RadioButton + 图标 + 名称/摘要/信息行 +
 *    置顶 / 设置 / 更多菜单）
 *  - 空列表 = ZL2 的居中 ScalingLabel
 *
 * 数据层沿用 Verse 现有实现（VersionsViewModel / VersionMetaRepository / VersionCardManager），
 * 未移植 ZL2 的多游戏路径菜单（Verse 手机端为单一游戏目录）。
 */

/** 版本分类（对位 ZL2 VersionCategory） */
enum class VersionCategory(val label: String) {
    /** 全部 */
    ALL("全部"),

    /** 原版 */
    VANILLA("原版"),

    /** 带有模组加载器 */
    MODLOADER("模组")
}

@Composable
fun InstalledVersionsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel = remember { VersionsViewModel(context.applicationContext as android.app.Application) }
    val localVersions by viewModel.localVersions.collectAsStateWithLifecycle()

    var currentLaunchVersionId by remember { mutableStateOf(settingsLastVersion() ?: "") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var metaVersion by remember { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var exportTarget by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf(VersionCategory.ALL) }
    // 当前激活的游戏目录（null = 内置默认目录）
    var activeDirPath by remember { mutableStateOf(GameConfig.activeDirPath(context)) }
    var cleanupPlan by remember { mutableStateOf<CleanupPlan?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val id = exportTarget
        exportTarget = null
        if (uri == null || id == null) return@rememberLauncherForActivityResult
        val gameDir = GameConfig.getGameDir(context)
        val versionDir = File(GameConfig.getVersionsDir(gameDir), id)
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    zipDirectory(versionDir, versionDir, zip)
                }
            } ?: error("无法写入所选位置")
        }.onSuccess {
            android.widget.Toast.makeText(context, "已导出 $id", android.widget.Toast.LENGTH_SHORT).show()
        }.onFailure {
            android.widget.Toast.makeText(context, "导出失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 元数据缓存：置顶 / 摘要 / 自定义信息，metaVersion 自增触发刷新
    val metaMap = remember(localVersions, metaVersion) {
        localVersions.associate { it.id to VersionMetaRepository.get(context, it.id) }
    }

    // 本地导入整合包：SAF 打开内部存储挑选 .zip/.mrpack 文件
    var importUri by remember { mutableStateOf<Uri?>(null) }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importUri = uri
    }

    // 添加游戏目录：SAF 目录选择器。启动链按真实文件路径读写游戏文件，
    // 因此仅接受能还原成绝对路径的共享存储目录（内置默认目录始终可用）
    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = treeUriToPath(uri)
        when {
            path == null -> toast(context, "仅支持选择本机共享存储中的目录")
            !GameConfig.addGameDir(context, path) -> toast(context, "该目录不可用或已在列表中")
            else -> {
                activeDirPath = GameConfig.activeDirPath(context)
                viewModel.loadLocalVersions()
            }
        }
    }

    val allCount = localVersions.size
    val vanillaCount = remember(localVersions) { localVersions.count { !isModdedVersion(it) } }
    val modloaderCount = remember(localVersions) { localVersions.count { isModdedVersion(it) } }

    val displayedVersions = remember(localVersions, category, metaMap) {
        when (category) {
            VersionCategory.ALL -> localVersions
            VersionCategory.VANILLA -> localVersions.filterNot { isModdedVersion(it) }
            VersionCategory.MODLOADER -> localVersions.filter { isModdedVersion(it) }
        }.sortedWith(
            compareByDescending<MinecraftVersion> { metaMap[it.id]?.pinned == true }
                .thenBy { it.id.lowercase() }
        )
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
            IconButton(
                onClick = { navController.navigateUp() },
                modifier = Modifier.size(32.dp)
            ) {
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
                Icon(
                    Icons.Filled.Home,
                    contentDescription = "主页",
                    modifier = Modifier.size(20.dp)
                )
            }
            MarqueeText(
                modifier = Modifier.weight(1f),
                text = "版本列表",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ══ 左栏：当前游戏目录 / 添加游戏目录 / 清理游戏文件 ══
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
            ) {
                GameDirPill(
                    activePath = activeDirPath,
                    defaultPath = remember { GameConfig.getDefaultGameDir(context).absolutePath },
                    addedDirs = GameConfig.getAddedDirs(context),
                    onSwitch = { path ->
                        GameConfig.setActiveDirPath(context, path)
                        activeDirPath = GameConfig.activeDirPath(context)
                        viewModel.loadLocalVersions()
                    },
                    onRemove = { path ->
                        GameConfig.removeGameDir(context, path)
                        activeDirPath = GameConfig.activeDirPath(context)
                        viewModel.loadLocalVersions()
                    },
                    onOpen = { openFolderPath(context, GameConfig.getGameDir(context)) }
                )

                Spacer(modifier = Modifier.weight(1f))

                RailPillButton(text = "添加游戏目录") { dirPicker.launch(null) }
                Spacer(modifier = Modifier.height(10.dp))
                RailPillButton(text = "清理游戏文件") {
                    cleanupPlan = buildCleanupPlan(GameConfig.getGameDir(context))
                }
            }

            // ══ 右侧卡片：工具条 + 版本列表 ══
            BackgroundCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTextButton(
                        onClick = { viewModel.loadLocalVersions() },
                        imageVector = Icons.Outlined.Refresh,
                        text = "刷新"
                    )
                    IconTextButton(
                        onClick = {
                            importPicker.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        imageVector = Icons.Outlined.Download,
                        text = "导入整合包"
                    )

                    // 版本分类
                    val categoryScroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fadeEdge(
                                state = categoryScroll,
                                length = 32.dp,
                                direction = EdgeDirection.Horizontal
                            )
                            .horizontalScroll(state = categoryScroll),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VersionCategoryItem(
                            value = VersionCategory.ALL,
                            versionsCount = allCount,
                            selected = category == VersionCategory.ALL,
                            onClick = { category = VersionCategory.ALL }
                        )
                        VersionCategoryItem(
                            value = VersionCategory.VANILLA,
                            versionsCount = vanillaCount,
                            selected = category == VersionCategory.VANILLA,
                            onClick = { category = VersionCategory.VANILLA }
                        )
                        VersionCategoryItem(
                            value = VersionCategory.MODLOADER,
                            versionsCount = modloaderCount,
                            selected = category == VersionCategory.MODLOADER,
                            onClick = { category = VersionCategory.MODLOADER }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                val listState = rememberLazyListState()

                    if (displayedVersions.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .fadeEdge(state = listState, length = 32.dp),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = 12.dp,
                                end = 12.dp,
                                bottom = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            state = listState,
                        ) {
                            items(displayedVersions, key = { it.id }) { version ->
                                VersionItemLayout(
                                    modifier = Modifier.fillMaxWidth(),
                                    version = version,
                                    selected = version.id == currentLaunchVersionId,
                                    meta = metaMap[version.id] ?: VersionMetaRepository.VersionMeta(),
                                    pinnedToHome = com.verse.explorer.app.ui.home.VersionCardManager.hasCard(version.id),
                                    onSelected = {
                                        SettingsRepository.setCustomSetting("last_version", version.id)
                                        currentLaunchVersionId = version.id
                                    },
                                    onSettingsClick = {
                                        navController.navigate("version_settings/${version.id}")
                                    },
                                    onToggleHome = { pinned ->
                                        if (pinned) {
                                            com.verse.explorer.app.ui.home.VersionCardManager.addCard(context, version.id)
                                        } else {
                                            com.verse.explorer.app.ui.home.VersionCardManager.removeByVersionId(context, version.id)
                                        }
                                        // 记录变更后重算条目状态，「添加到主页卡片 / 从主页移除」文案即时切换
                                        metaVersion++
                                    },
                                    onRenameClick = {
                                        renameTarget = version.id
                                        renameText = version.id
                                    },
                                    onExportClick = {
                                        exportTarget = version.id
                                        exportLauncher.launch("${version.id}.zip")
                                    },
                                    onOpenFolder = { sub -> openVersionFolder(context, version.id, sub) },
                                    onTogglePin = { pinned ->
                                        VersionMetaRepository.setPinned(context, version.id, pinned)
                                        metaVersion++
                                    },
                                    onDeleteClick = { showDeleteConfirm = version.id }
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScalingLabel(
                                modifier = Modifier.align(Alignment.Center),
                                text = "暂无已安装版本"
                            )
                        }
                    }

                }
            }
        }
    }

    ModpackImportFlow(
        importSource = importUri,
        onHandled = { importUri = null },
        onImported = { viewModel.loadLocalVersions() }
    )

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = cardColor(),
            titleContentColor = onCardColor(),
            shape = MaterialTheme.shapes.large,
            title = { Text("删除版本", style = MaterialTheme.typography.titleLarge) },
            text = { Text("确定要删除 Minecraft ${showDeleteConfirm} 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVersion(showDeleteConfirm!!)
                        VersionMetaRepository.remove(context, showDeleteConfirm!!)
                        showDeleteConfirm = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }

    // 重命名版本：目录、版本 json 文件名与 json 内 id 字段、当前选中记录、元数据一并迁移
    renameTarget?.let { oldId ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
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
                        if (newId.isNotEmpty() && newId != oldId && runCatching {
                                renameVersion(context, oldId, newId)
                            }.isSuccess
                        ) {
                            if (settingsLastVersion() == oldId) {
                                SettingsRepository.setCustomSetting("last_version", newId)
                            }
                            if (currentLaunchVersionId == oldId) currentLaunchVersionId = newId
                            VersionMetaRepository.rename(context, oldId, newId)
                            viewModel.loadLocalVersions()
                        }
                        metaVersion++
                        renameTarget = null
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    // 清理游戏文件：只清理下载残留与缺少版本 json 的孤立版本目录，逐项列出后再删
    cleanupPlan?.let { plan ->
        val total = plan.residue.size + plan.orphanVersions.size
        AlertDialog(
            onDismissRequest = { cleanupPlan = null },
            containerColor = cardColor(),
            titleContentColor = onCardColor(),
            shape = MaterialTheme.shapes.large,
            title = { Text("清理游戏文件", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    if (total == 0) {
                        "没有可清理的文件"
                    } else {
                        buildString {
                            append("将删除 ${plan.residue.size} 项下载残留（.part / .tmp / 分块缓存）")
                            if (plan.orphanVersions.isNotEmpty()) {
                                append("，以及 ${plan.orphanVersions.size} 个缺少版本 json 的版本目录：")
                                append(plan.orphanVersions.take(8).joinToString("、") { it.name })
                            }
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = total > 0,
                    onClick = {
                        plan.residue.forEach { runCatching { it.deleteRecursively() } }
                        plan.orphanVersions.forEach { runCatching { it.deleteRecursively() } }
                        toast(context, "已清理 $total 项")
                        cleanupPlan = null
                        viewModel.loadLocalVersions()
                    }
                ) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { cleanupPlan = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 版本条目（1:1 对位 ZL2 VersionItemLayout）。
 *
 * RadioButton（选中态） + 版本图标 + 名称/摘要/信息行 + 置顶 + 设置 + 更多菜单。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VersionItemLayout(
    version: MinecraftVersion,
    selected: Boolean,
    meta: VersionMetaRepository.VersionMeta,
    pinnedToHome: Boolean,
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    color: Color = cardColor(influencedByBackground),
    contentColor: Color = onCardColor(),
    blur: Int = VersePersonalization.backgroundBlur,
    onSelected: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onToggleHome: (Boolean) -> Unit = {},
    onRenameClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onOpenFolder: (String) -> Unit = {},
    onTogglePin: (Boolean) -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    val scale = remember { Animatable(initialValue = 0.95f) }
    LaunchedEffect(Unit) {
        scale.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 200))
    }

    Surface(
        modifier = modifier.graphicsLayer(scaleY = scale.value, scaleX = scale.value),
        color = color,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        onClick = {
            if (selected) return@Surface
            onSelected()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = MaterialTheme.shapes.large)
                .backgroundGlass(blur, color, influencedByBackground)
                .padding(all = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = {
                    if (selected) return@RadioButton
                    onSelected()
                }
            )

            // 版本信息（对位 ZL2 CommonVersionInfoLayout）
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                VersionIcon(version = version, size = 34.dp)

                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = version.id,
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (meta.summary.isNotBlank()) {
                        MarqueeText(
                            text = meta.summary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    FlowRow(
                        modifier = Modifier.alpha(0.7f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val baseVersion = version.inheritsFrom
                        if (!baseVersion.isNullOrBlank()) {
                            Text(
                                text = baseVersion,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        val hint = version.loaderHint
                        if (!hint.isNullOrBlank()) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (baseVersion.isNullOrBlank() && hint.isNullOrBlank()) {
                            LittleTextLabel(
                                text = versionTypeLabel(version),
                                textStyle = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // 置顶（对位 ZL2：图钉图标旋转 45°）
            IconButton(
                onClick = { onTogglePin(!meta.pinned) }
            ) {
                Crossfade(targetState = meta.pinned) { pinned ->
                    Icon(
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(45.0f),
                        imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "置顶"
                    )
                }
            }

            // 版本设置
            IconButton(onClick = onSettingsClick) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "版本设置"
                )
            }

            // 更多操作
            Box {
                var menuExpanded by remember { mutableStateOf(false) }

                IconButton(onClick = { menuExpanded = !menuExpanded }) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "更多"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    shape = MaterialTheme.shapes.large,
                    shadowElevation = 3.dp,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp)) },
                        onClick = {
                            onRenameClick()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (pinnedToHome) "从主页移除" else "添加到主页卡片") },
                        leadingIcon = {
                            Icon(
                                if (pinnedToHome) Icons.Filled.Home else Icons.Outlined.AddBox,
                                null, Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            onToggleHome(!pinnedToHome)
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出 (.zip)") },
                        leadingIcon = { Icon(Icons.Outlined.FolderZip, null, Modifier.size(20.dp)) },
                        onClick = {
                            onExportClick()
                            menuExpanded = false
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("打开版本目录") },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp)) },
                        onClick = {
                            onOpenFolder("")
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("打开 mods") },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp)) },
                        onClick = {
                            onOpenFolder("mods")
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("打开 saves") },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp)) },
                        onClick = {
                            onOpenFolder("saves")
                            menuExpanded = false
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("删除版本", color = VerseColors.PcRed) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(20.dp), tint = VerseColors.PcRed) },
                        onClick = {
                            onDeleteClick()
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

/** 版本分类条目（对位 ZL2 VersionCategoryItem） */
@Composable
private fun VersionCategoryItem(
    modifier: Modifier = Modifier,
    value: VersionCategory,
    versionsCount: Int,
    selected: Boolean,
    shape: Shape = MaterialTheme.shapes.large,
    selectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    TextRailItem(
        modifier = modifier,
        text = {
            Text(text = value.label, style = style)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "($versionsCount)", style = style)
        },
        onClick = onClick,
        selected = selected,
        shape = shape,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
        enabled = enabled
    )
}

internal fun versionTypeLabel(version: MinecraftVersion): String {
    return when (version.type) {
        VersionType.SNAPSHOT -> "快照"
        VersionType.OLD_ALPHA -> "Old Alpha"
        VersionType.OLD_BETA -> "Old Beta"
        else -> "正式版"
    }
}

private fun settingsLastVersion(): String? = SettingsRepository.getCustomSetting("last_version")

/** 是否为模组版本：版本 json 识别出加载器，或 id 命中加载器/整合包关键词 */
private fun isModdedVersion(version: MinecraftVersion): Boolean {
    if (!version.loaderHint.isNullOrBlank()) return true
    val lower = version.id.lowercase()
    return lower.contains("fabric") || lower.contains("forge") || lower.contains("neoforge") ||
        lower.contains("optifine") || lower.contains("liteloader") || lower.contains("modpack") || lower.contains("整合")
}

/**
 * 重命名版本目录：目录与 <id>.json 改名，版本 json 内的 id 字段同步更新，
 * 目标名已存在或目录缺失时抛错由调用方提示。
 */
internal fun renameVersion(context: Context, oldId: String, newId: String) {
    val versionsDir = GameConfig.getVersionsDir(GameConfig.getGameDir(context))
    val oldDir = File(versionsDir, oldId)
    val newDir = File(versionsDir, newId)
    require(oldDir.isDirectory) { "版本目录不存在" }
    require(!newDir.exists()) { "目标名称已存在" }
    val invalidChars = Regex("""[\\/:*?"<>|]""")
    require(!invalidChars.containsMatchIn(newId)) { "名称含非法字符" }

    val oldJson = File(oldDir, "$oldId.json")
    if (oldJson.isFile) {
        val text = oldJson.readText()
        val updated = Regex("""("id"\s*:\s*")(${Regex.escape(oldId)})(")""")
            .replace(text) { m -> "${m.groupValues[1]}$newId${m.groupValues[3]}" }
        oldJson.writeText(updated)
        if (!oldJson.renameTo(File(oldDir, "$newId.json"))) {
            oldJson.writeText(text)
            error("版本 json 改名失败")
        }
    }
    if (!oldDir.renameTo(newDir)) {
        error("版本目录改名失败")
    }
}

/** 递归压缩目录（entry 名相对 rootDir） */
internal fun zipDirectory(dir: File, rootDir: File, zip: ZipOutputStream) {
    dir.listFiles()?.forEach { f ->
        if (f.isDirectory) {
            zipDirectory(f, rootDir, zip)
        } else {
            val entryName = f.relativeTo(rootDir).invariantSeparatorsPath
            zip.putNextEntry(ZipEntry(entryName))
            f.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

/** 左栏游戏目录胶囊：显示当前激活目录，⋯ 菜单切换 / 移除 / 打开 */
@Composable
private fun GameDirPill(
    activePath: String?,
    defaultPath: String,
    addedDirs: List<String>,
    onSwitch: (String?) -> Unit,
    onRemove: (String) -> Unit,
    onOpen: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val currentPath = activePath ?: defaultPath
    val title = if (activePath == null) "默认游戏目录" else File(currentPath).name.ifBlank { currentPath }
    val color = itemColor()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        contentColor = onItemColor(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .backgroundGlass(VersePersonalization.backgroundBlur, color)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(text = title, style = MaterialTheme.typography.labelLarge)
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = currentPath,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "游戏目录管理"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    shape = MaterialTheme.shapes.large,
                    shadowElevation = 3.dp,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    GameDirMenuItem(
                        title = "默认游戏目录",
                        path = defaultPath,
                        selected = activePath == null,
                        onClick = {
                            menuExpanded = false
                            onSwitch(null)
                        }
                    )
                    addedDirs.forEach { path ->
                        GameDirMenuItem(
                            title = File(path).name.ifBlank { path },
                            path = path,
                            selected = activePath == path,
                            onClick = {
                                menuExpanded = false
                                onSwitch(path)
                            },
                            onRemove = {
                                menuExpanded = false
                                onRemove(path)
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("打开游戏目录") },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp)) },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                }
            }
        }
    }
}

/** 游戏目录菜单条目：名称 + 路径，附加目录可单独移除 */
@Composable
private fun GameDirMenuItem(
    title: String,
    path: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge)
                Text(
                    modifier = Modifier.alpha(0.6f),
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, null, Modifier.size(20.dp)) }
        } else null,
        trailingIcon = if (onRemove == null) null else {
            {
                IconButton(onClick = onRemove) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "移除该目录"
                    )
                }
            }
        },
        onClick = onClick
    )
}

/** 左栏药丸按钮 */
@Composable
private fun RailPillButton(text: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal fun toast(context: Context, text: String) {
    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
}

/** SAF 目录 URI → 共享存储绝对路径；非共享存储目录返回 null */
private fun treeUriToPath(uri: Uri): String? {
    if (uri.authority != "com.android.externalstorage.documents") return null
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val parts = docId.split(":")
    if (parts.size < 2) return null
    val volume = parts[0]
    val relative = parts[1]
    val base = if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$volume"
    return if (relative.isBlank()) base else "$base/$relative"
}

/** 共享存储绝对路径 → DocumentsContract 文档 id；非共享存储返回 null */
private fun storagePathToDocumentId(path: String): String? {
    if (path == "/storage/emulated/0") return "primary:"
    if (path.startsWith("/storage/emulated/0/")) return "primary:" + path.removePrefix("/storage/emulated/0/")
    val match = Regex("""^/storage/([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4})(?:/(.*))?$""").find(path) ?: return null
    return "${match.groupValues[1]}:${match.groupValues[2]}"
}

/** 清理计划：下载残留项 + 缺少版本 json 的孤立版本目录 */
private data class CleanupPlan(
    val residue: List<File>,
    val orphanVersions: List<File>
)

private val RESIDUE_SUFFIXES = listOf(".part", ".tmp", ".download")

private fun buildCleanupPlan(gameDir: File): CleanupPlan {
    val residue = mutableListOf<File>()
    listOf("modpacks", "mods", "resourcepacks", "shaderpacks", "versions")
        .forEach { collectResidue(File(gameDir, it), residue) }
    val versionsDir = GameConfig.getVersionsDir(gameDir)
    val orphan = versionsDir.listFiles { f -> f.isDirectory }
        ?.filterNot { File(it, "${it.name}.json").isFile }
        ?: emptyList()
    return CleanupPlan(residue, orphan)
}

/** 递归收集下载残留：分块缓存目录与 .part/.tmp/.download 半截文件（跳过 assets/libraries） */
private fun collectResidue(dir: File, out: MutableList<File>) {
    dir.listFiles()?.forEach { f ->
        when {
            f.isDirectory && f.name == ".download_chunks" -> out.add(f)
            f.isDirectory && f.name != "assets" && f.name != "libraries" -> collectResidue(f, out)
            f.isFile && RESIDUE_SUFFIXES.any { f.name.endsWith(it) } -> out.add(f)
        }
    }
}

/**
 * 打开目录：以「分享文件」的形式发起（分享面板让用户自选文件管理器接收目录 URI），
 * 与直接 ACTION_VIEW 相比不受固定打开方式限制；分享不可用时回退 ACTION_VIEW，
 * 仍不可用则把路径复制到剪贴板提示。
 */
internal fun openFolderPath(context: Context, target: File) {
    if (!target.exists()) target.mkdirs()

    val fallback = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("path", target.absolutePath))
        android.widget.Toast.makeText(
            context, "未找到可用的文件管理器，路径已复制：${target.absolutePath}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    val documentId = storagePathToDocumentId(target.absolutePath)
    if (documentId == null) {
        fallback()
        return
    }
    val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId)

    // 分享形式打开：接收方拿到目录 URI，用户可在分享面板里选文件管理器
    runCatching {
        val send = Intent(Intent.ACTION_SEND)
            .setType(DocumentsContract.Document.MIME_TYPE_DIR)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(send, "打开文件夹")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }.onFailure {
        // 分享面板不可用（无接收方/系统限制）时回退为直接打开目录
        runCatching {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(view)
        }.onFailure { fallback() }
    }
}

/**
 * 打开版本内目录：开启版本隔离时取版本目录下的子目录，否则取游戏目录下的对应子目录。
 */
internal fun openVersionFolder(
    context: Context,
    versionId: String,
    subFolder: String,
    isolate: Boolean = true
) {
    val gameDir = GameConfig.getGameDir(context)
    val base = if (isolate) File(GameConfig.getVersionsDir(gameDir), versionId) else gameDir
    val target = if (subFolder.isBlank()) base else File(base, subFolder)
    openFolderPath(context, target)
}
