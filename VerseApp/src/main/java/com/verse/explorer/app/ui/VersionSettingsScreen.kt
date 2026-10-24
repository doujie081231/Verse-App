package com.verse.explorer.app.ui

import com.verse.explorer.app.viewmodel.activityViewModel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.core.data.VersionMetaRepository
import com.verse.explorer.core.data.VersionSettingsRepository
import com.verse.explorer.app.documents.GameFolderProvider
import com.verse.explorer.app.ui.components.BackgroundCard
import com.verse.explorer.app.ui.components.CardTitleLayout
import com.verse.explorer.app.ui.components.DisabledAlpha
import com.verse.explorer.app.ui.components.IconTextButton
import com.verse.explorer.app.ui.components.MarqueeText
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.launcher.renderer.RendererManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


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
    val viewModel = activityViewModel(VersionsViewModel::class.java)
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
                modifier = Modifier
                    .width(104.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
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
                listOf(ManagePage.MODS, ManagePage.RESOURCEPACKS, ManagePage.SHADERS, ManagePage.SAVES)
                    .forEach { sub ->
                        SubPageRailItem(sub, page == sub) { page = sub }
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

                    ManagePage.MODS -> InstalledModsScreen(
                        versionId = versionId,
                        navController = navController,
                        embedded = true
                    )

                    ManagePage.RESOURCEPACKS -> VersionFolderTab(
                        versionId = versionId,
                        subDir = "resourcepacks",
                        isolateGameDir = settings.isolateGameDir,
                        title = "材质包",
                        importLabel = "导入材质包"
                    )

                    ManagePage.SHADERS -> VersionFolderTab(
                        versionId = versionId,
                        subDir = "shaderpacks",
                        isolateGameDir = settings.isolateGameDir,
                        title = "光影包",
                        importLabel = "导入光影包"
                    )

                    ManagePage.SAVES -> WorldsTab(
                        versionId = versionId,
                        isolateGameDir = settings.isolateGameDir
                    )

                    ManagePage.LOADER -> LoaderInstallTab(versionId = versionId)
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
