package com.verse.explorer.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.components.MarqueeText
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.cardColor
import com.verse.explorer.app.ui.theme.onCardColor
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.world.BackupInfo
import com.verse.explorer.core.world.WorldInfo
import com.verse.explorer.core.world.WorldManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 存档管理页（版本设置 → 存档）。
 *
 * 与通用目录页（材质包/光影）不同，存档以「目录 + level.dat」为单位，需要备份、
 * 恢复、导出 zip、重命名等操作，因此单独成页。两类风险在这里做显式拦截：
 * 1. 恢复备份会覆盖当前存档 → 必弹二次确认；
 * 2. 导入同名存档时不覆盖，自动改名 `名字 (2)`（由 WorldManager 保证）。
 */
@Composable
internal fun WorldsTab(
    versionId: String,
    isolateGameDir: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var worlds by remember { mutableStateOf<List<WorldInfo>>(emptyList()) }
    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showBackups by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    var pendingDeleteWorld by remember { mutableStateOf<WorldInfo?>(null) }
    var pendingRename by remember { mutableStateOf<WorldInfo?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupInfo?>(null) }
    var pendingDeleteBackup by remember { mutableStateOf<BackupInfo?>(null) }
    var pendingExportWorld by remember { mutableStateOf<String?>(null) }

    val manager = remember(versionId, isolateGameDir) {
        val gameDir = GameConfig.getGameDir(context)
        val base = if (isolateGameDir) File(GameConfig.getVersionsDir(gameDir), versionId) else gameDir
        val saves = File(base, "saves")
        // 备份放在版本（或游戏根）目录下的独立文件夹，不污染 saves，避免被 MC 当成存档扫描
        val backupsDir = File(base, "verse-world-backups")
        WorldManager(saves, backupsDir)
    }

    fun reload() {
        reloadTick++
    }

    LaunchedEffect(manager, reloadTick) {
        loading = true
        val snapshot = withContext(Dispatchers.IO) {
            manager.getWorlds() to manager.getBackups()
        }
        worlds = snapshot.first
        backups = snapshot.second
        loading = false
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(context.cacheDir, "world-import-${System.currentTimeMillis()}.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("无法读取所选文件")
                    val importedName = manager.importWorld(temp)
                    temp.delete()
                    importedName
                }
            }
            busy = false
            result.onSuccess { name ->
                if (name != null) {
                    toast(context, "已导入存档：$name")
                    reload()
                } else {
                    toast(context, "导入失败：文件不是有效的存档压缩包")
                }
            }.onFailure {
                toast(context, "导入失败：${it.message ?: "未知错误"}")
            }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val worldName = pendingExportWorld
        pendingExportWorld = null
        if (uri == null || worldName == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(context.cacheDir, "$worldName-export.zip")
                    val zipped = manager.exportWorld(worldName, temp)
                    if (zipped) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            temp.inputStream().use { input -> input.copyTo(output) }
                        }
                        temp.delete()
                        true
                    } else {
                        temp.delete()
                        false
                    }
                }.getOrDefault(false)
            }
            busy = false
            toast(context, if (ok) "已导出：$worldName" else "导出失败")
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
                text = if (showBackups) "备份记录（${backups.size} 个）" else "存档（${worlds.size} 个）",
                style = MaterialTheme.typography.labelMedium,
                color = VerseColors.PcTextMuted
            )
            PcButton(
                text = if (showBackups) "返回存档" else "备份记录",
                onClick = { showBackups = !showBackups },
                variant = PcButtonVariant.OUTLINE
            )
            if (!showBackups) {
                PcButton(
                    text = "导入存档",
                    onClick = { importPicker.launch("*/*") },
                    variant = PcButtonVariant.OUTLINE
                )
            }
        }

        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                color = VerseColors.PcAccent
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RoseLoader(modifier = Modifier.size(28.dp), size = 28.dp, color = VerseColors.PcAccent)
            }

            showBackups && backups.isEmpty() -> EmptyHint("还没有备份。在存档列表里点「备份」即可创建。")

            !showBackups && worlds.isEmpty() -> EmptyHint("该版本还没有存档")

            showBackups -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(backups, key = { it.id }) { backup ->
                    BackupRow(
                        backup = backup,
                        onRestore = { pendingRestore = backup },
                        onDelete = { pendingDeleteBackup = backup }
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(worlds, key = { it.folderName }) { world ->
                    WorldRow(
                        world = world,
                        onBackup = {
                            scope.launch {
                                busy = true
                                val backup = withContext(Dispatchers.IO) { manager.createBackup(world.name) }
                                busy = false
                                toast(
                                    context,
                                    if (backup != null) "已备份：${world.name}" else "备份失败"
                                )
                                reload()
                            }
                        },
                        onExport = {
                            pendingExportWorld = world.name
                            exportPicker.launch("${world.name}.zip")
                        },
                        onRename = { pendingRename = world },
                        onDelete = { pendingDeleteWorld = world }
                    )
                }
            }
        }
    }

    pendingRename?.let { world ->
        var text by remember(world.folderName) { mutableStateOf(world.name) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            containerColor = cardColor(),
            titleContentColor = onCardColor(),
            shape = MaterialTheme.shapes.large,
            title = { Text("重命名存档", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("存档名") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() && text != world.name,
                    onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { manager.renameWorld(world.name, text.trim()) }
                            pendingRename = null
                            toast(context, if (ok) "已重命名为 ${text.trim()}" else "重命名失败：名字已存在或不可用")
                            reload()
                        }
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text("取消") }
            }
        )
    }

    pendingDeleteWorld?.let { world ->
        ConfirmDialog(
            title = "删除存档",
            message = "将永久删除「${world.name}」及其全部数据，该操作不可撤销。建议先备份。",
            confirmText = "删除",
            onConfirm = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { manager.deleteWorld(world.name) }
                    pendingDeleteWorld = null
                    toast(context, if (ok) "已删除 ${world.name}" else "删除失败")
                    reload()
                }
            },
            onDismiss = { pendingDeleteWorld = null }
        )
    }

    pendingRestore?.let { backup ->
        ConfirmDialog(
            title = "恢复备份",
            message = "将用 ${formatDateTime(backup.timestamp)} 的备份覆盖「${backup.worldName}」的当前进度，" +
                "当前进度会丢失。确定继续？",
            confirmText = "覆盖恢复",
            onConfirm = {
                scope.launch {
                    busy = true
                    val ok = withContext(Dispatchers.IO) { manager.restoreBackup(backup.id) }
                    busy = false
                    pendingRestore = null
                    toast(context, if (ok) "已恢复到 ${formatDateTime(backup.timestamp)}" else "恢复失败")
                    reload()
                }
            },
            onDismiss = { pendingRestore = null }
        )
    }

    pendingDeleteBackup?.let { backup ->
        ConfirmDialog(
            title = "删除备份",
            message = "删除 ${formatDateTime(backup.timestamp)} 的备份？存档本体不受影响。",
            confirmText = "删除",
            onConfirm = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { manager.deleteBackup(backup.id) }
                    pendingDeleteBackup = null
                    toast(context, if (ok) "已删除备份" else "删除失败")
                    reload()
                }
            },
            onDismiss = { pendingDeleteBackup = null }
        )
    }
}

@Composable
private fun WorldRow(
    world: WorldInfo,
    onBackup: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorldIcon(iconPath = world.iconPath)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(text = world.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = "${formatBytes(world.size)} · ${formatDateTime(world.lastPlayed)}",
                    fontSize = 10.sp,
                    color = VerseColors.PcTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RowAction("备份", onBackup)
            RowAction("导出", onExport)
            RowAction("改名", onRename)
            IconButton(onClick = onDelete) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除存档",
                    tint = VerseColors.PcOrange
                )
            }
        }
    }
}

@Composable
private fun BackupRow(
    backup: BackupInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = VerseColors.PcTextMuted
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(text = backup.worldName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = "${formatDateTime(backup.timestamp)} · ${formatBytes(backup.size)}",
                    fontSize = 10.sp,
                    color = VerseColors.PcTextMuted
                )
            }
            RowAction("恢复", onRestore)
            IconButton(onClick = onDelete) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除备份",
                    tint = VerseColors.PcOrange
                )
            }
        }
    }
}

@Composable
private fun RowAction(text: String, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        text = text,
        fontSize = 12.sp,
        color = VerseColors.PcAccent
    )
}

@Composable
private fun WorldIcon(iconPath: String?) {
    val bitmap = remember(iconPath) {
        iconPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(38.dp))
        } else {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = VerseColors.PcTextMuted
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = VerseColors.PcTextMuted)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardColor(),
        titleContentColor = onCardColor(),
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, fontSize = 13.sp, color = VerseColors.PcTextMuted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

private fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
