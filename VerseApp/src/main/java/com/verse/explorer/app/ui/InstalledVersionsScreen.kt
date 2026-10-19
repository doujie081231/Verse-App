package com.verse.explorer.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderZip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.data.VersionMetaRepository
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.components.DisabledAlpha
import com.verse.explorer.app.ui.components.EdgeDirection
import com.verse.explorer.app.ui.components.IconTextButton
import com.verse.explorer.app.ui.components.LittleTextLabel
import com.verse.explorer.app.ui.components.MarqueeText
import com.verse.explorer.app.ui.components.ScalingLabel
import com.verse.explorer.app.ui.components.TextRailItem
import com.verse.explorer.app.ui.components.fadeEdge
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.cardColor
import com.verse.explorer.app.ui.theme.onCardColor
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部返回栏（Verse 导航所需，视觉沿用 ZL2 的透明标题风格）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp),
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
                MarqueeText(
                    modifier = Modifier.weight(1f),
                    text = "版本管理",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
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

                    // 悬浮操作栏（对位 ZL2 VersionsLayout 顶部操作栏）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(1f)
                    ) {
                        val blur = VersePersonalization.backgroundBlur
                        val cardBg = cardColor()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = cardBg,
                                contentColor = onCardColor(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .backgroundGlass(blur, cardBg)
                                        .padding(all = 8.dp),
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
                                        text = "安装新版本"
                                    )
                                }
                            }

                            // 版本分类
                            Surface(
                                modifier = Modifier.weight(1f, fill = false),
                                color = cardBg,
                                contentColor = onCardColor(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                val scrollState = rememberScrollState()
                                Row(
                                    modifier = Modifier
                                        .backgroundGlass(blur, cardBg)
                                        .fadeEdge(
                                            state = scrollState,
                                            length = 32.dp,
                                            direction = EdgeDirection.Horizontal
                                        )
                                        .horizontalScroll(state = scrollState)
                                        .padding(all = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

private fun versionTypeLabel(version: MinecraftVersion): String {
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
private fun renameVersion(context: android.content.Context, oldId: String, newId: String) {
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
private fun zipDirectory(dir: File, rootDir: File, zip: ZipOutputStream) {
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

/**
 * 用系统文件管理器打开版本内目录：
 * 游戏目录位于共享存储时经 DocumentsContract 构造目录 URI 打开；
 * 私有目录或打开失败时回退为复制路径到剪贴板提示。
 */
private fun openVersionFolder(context: android.content.Context, versionId: String, subFolder: String) {
    val gameDir = GameConfig.getGameDir(context)
    val target = File(GameConfig.getVersionsDir(gameDir), versionId)
        .let { if (subFolder.isNotBlank()) File(it, subFolder) else it }
    if (!target.exists()) target.mkdirs()

    val fallback = {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("path", target.absolutePath)
        )
        android.widget.Toast.makeText(
            context, "未找到可用的文件管理器，路径已复制：${target.absolutePath}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    runCatching {
        val storagePath = target.absolutePath
        val prefix = "/storage/emulated/0/"
        require(storagePath.startsWith(prefix)) { "目录不在共享存储" }
        val documentId = "primary:${storagePath.removePrefix(prefix)}"
        val uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", documentId
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure { fallback() }
}
