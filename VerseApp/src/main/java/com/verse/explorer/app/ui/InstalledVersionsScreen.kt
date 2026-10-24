package com.verse.explorer.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.core.data.VersionMetaRepository
import com.verse.explorer.app.ui.components.BackgroundCard
import com.verse.explorer.app.ui.components.EdgeDirection
import com.verse.explorer.app.ui.components.IconTextButton
import com.verse.explorer.app.ui.components.MarqueeText
import com.verse.explorer.app.ui.components.ModpackImportFlow
import com.verse.explorer.app.ui.components.ScalingLabel
import com.verse.explorer.app.ui.components.fadeEdge
import com.verse.explorer.app.ui.theme.cardColor
import com.verse.explorer.app.ui.theme.onCardColor
import com.verse.explorer.app.viewmodel.VersionsViewModel
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import java.io.File
import java.util.zip.ZipOutputStream

/**
 * 已安装版本管理页。
 *
 * 结构：
 *  - 顶部悬浮操作栏 = 左侧「刷新 / 安装」，右侧横向滚动的版本分类 ALL / 原版 / 模组
 *  - 列表条目 = 版本图标 + 名称/摘要/信息行 + 置顶 / 设置 / 更多菜单
 *  - 空列表 = 居中 ScalingLabel
 *
 * 数据层沿用 Verse 现有实现（VersionsViewModel / VersionMetaRepository / VersionCardManager），
 * 手机端为单一游戏目录语义。
 */

/** 版本分类 */
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
    val viewModel = VersionsViewModel.shared(context.applicationContext as android.app.Application)
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
    // 应用内目录浏览（游戏目录在应用私有路径，外部文件管理器无权访问）
    var browserDir by remember { mutableStateOf<File?>(null) }

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
                    onOpen = { browserDir = GameConfig.getGameDir(context) }
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
                                    onOpenFolder = { sub ->
                                        val base = File(GameConfig.getVersionsDir(GameConfig.getGameDir(context)), version.id)
                                        browserDir = if (sub.isBlank()) base else File(base, sub)
                                    },
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

    browserDir?.let { dir ->
        FolderBrowserDialog(title = "文件夹", startDir = dir, onDismiss = { browserDir = null })
    }

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
                        val id = showDeleteConfirm!!
                        val removed = viewModel.deleteVersion(id)
                        if (removed) {
                            VersionMetaRepository.remove(context, id)
                            toast(context, "已删除 $id")
                        } else {
                            // 目录被占用时改名失败：不能假装删掉了，否则用户重下会撞「版本已存在」
                            toast(context, "删除失败：版本目录被占用，请关闭游戏后重试")
                        }
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
