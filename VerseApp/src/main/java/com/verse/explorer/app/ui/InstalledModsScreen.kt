package com.verse.explorer.app.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.config.GameConfig
import com.tungsten.fclcore.game.DefaultGameRepository
import com.tungsten.fclcore.mod.LocalModFile
import com.tungsten.fclcore.mod.ModLoaderType
import com.tungsten.fclcore.mod.ModManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * 已安装模组管理页（版本设置 → 模组管理）。
 * - 扫描版本运行目录 mods/（含按 MC 版本命名的子目录），解析 mcmod.info / mods.toml /
 *   fabric.mod.json / quilt.mod.json / litemod.json 元数据
 * - 启用=去掉 .disabled 后缀，禁用=追加 .disabled 后缀（文件重命名实现）
 * - 旧版本文件以 .old 后缀备份，支持回滚
 * 卡片比例、大小、布局对齐版本下载页模组卡片。
 */

/** 运行目录按 Verse 版本隔离语义（usesGlobal || isolateGameDir → versions/<id>/）固定，
 *  不随版本 json 内的目录类型配置漂移 */
private class VerseRunGameRepository(base: File, private val runDir: File) :
    DefaultGameRepository(base) {
    override fun getRunDirectory(id: String): File = runDir
}

@Composable
fun InstalledModsScreen(
    versionId: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val manager = remember(versionId) {
        val gameDir = GameConfig.getGameDir(context)
        val settings = com.verse.explorer.app.data.VersionSettingsRepository(context)
            .getVersionSettings(versionId)
        val isolated = settings.usesGlobal || settings.isolateGameDir
        val runDir = if (isolated) File(File(gameDir, "versions"), versionId) else gameDir
        ModManager(VerseRunGameRepository(gameDir, runDir), versionId)
    }

    var mods by remember { mutableStateOf<List<LocalModFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<LocalModFile?>(null) }
    var infoMod by remember { mutableStateOf<LocalModFile?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        errorMsg = null
        try {
            mods = withContext(Dispatchers.IO) {
                manager.repository.refreshVersions()
                manager.refreshMods()
                manager.mods
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "模组列表加载失败"
        }
        loading = false
    }

    LaunchedEffect(versionId) { reload() }

    fun showToast(message: String) {
        toast = message
    }

    toast?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    // 从系统文件选择器导入模组 jar/zip（文件名校验、复制到 mods 目录走 ModManager.addMod）
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val activity = context as Activity
                    val name = withContext(Dispatchers.IO) { queryDisplayName(activity, uri) }
                    if (!ModManager.isFileNameMod(java.io.File(name).toPath())) {
                        showToast("仅支持 .jar / .zip / .litemod 模组文件")
                    } else {
                        withContext(Dispatchers.IO) { manager.addMod(activity, uri, name) }
                        showToast("已导入 $name")
                        // 元数据解析异常只提示不中断（坏 mod 文件不应带崩页面）
                        runCatching { reload() }.onFailure {
                            showToast("模组列表刷新失败：${it.message ?: "未知错误"}")
                        }
                    }
                } catch (e: Throwable) {
                    showToast("导入失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 页头：返回 + 标题 + 版本名（与版本设置页同布局）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = VerseColors.PcTextPrimary)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "模组管理",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerseColors.PcTextPrimary
                    )
                    Text(
                        text = versionId,
                        fontSize = 11.sp,
                        color = VerseColors.PcTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 工具行：计数 + 导入模组
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已安装 ${mods.size} 个模组",
                    fontSize = 12.sp,
                    color = VerseColors.PcTextMuted,
                    modifier = Modifier.weight(1f)
                )
                PcButton(
                    text = "导入模组",
                    onClick = { importPicker.launch("*/*") },
                    variant = PcButtonVariant.OUTLINE
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RoseLoader(
                            modifier = Modifier.size(32.dp),
                            size = 32.dp,
                            color = VerseColors.PcAccent
                        )
                    }
                }
                errorMsg != null && mods.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Outlined.Extension, null, Modifier.size(40.dp), VerseColors.PcTextMuted)
                            Text(errorMsg ?: "加载失败", fontSize = 13.sp, color = VerseColors.PcTextMuted)
                            OutlinedButton(
                                onClick = { scope.launch { reload() } },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VerseColors.PcAccent)
                            ) {
                                Text("重试", fontSize = 13.sp)
                            }
                        }
                    }
                }
                mods.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Extension, null, Modifier.size(40.dp), VerseColors.PcTextMuted)
                            Text("此版本尚未安装模组", fontSize = 13.sp, color = VerseColors.PcTextMuted)
                            Text(
                                "点击右上角「导入模组」，或前往资源页下载",
                                fontSize = 11.sp,
                                color = VerseColors.PcTextMuted
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(mods, key = { _, m -> m.fileName + "@" + m.file.fileName }) { _, mod ->
                            InstalledModCard(
                                mod = mod,
                                onToggle = { enabled ->
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { mod.setActive(enabled) }
                                            reload()
                                        } catch (e: Exception) {
                                            showToast("操作失败：${e.message ?: "未知错误"}")
                                        }
                                    }
                                },
                                onDelete = { pendingDelete = mod },
                                onClick = { infoMod = mod }
                            )
                        }
                    }
                }
            }
        }
    }

    // 删除确认
    pendingDelete?.let { mod ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = VerseColors.PcBgCard,
            titleContentColor = VerseColors.PcTextPrimary,
            textContentColor = VerseColors.PcTextSecondary,
            title = { Text("删除模组", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("确定删除「${(mod.name ?: mod.fileName)?.ifBlank { null } ?: "未知模组"}」吗？该文件将从模组目录中移除。", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { manager.removeMods(mod) }
                            showToast("已删除")
                            reload()
                        } catch (e: Exception) {
                            showToast("删除失败：${e.message ?: "未知错误"}")
                        }
                    }
                }) { Text("删除", color = VerseColors.PcOrange) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = VerseColors.PcTextSecondary)
                }
            }
        )
    }

    // 模组详情（元数据 + 旧版本回滚）
    infoMod?.let { mod ->
        AlertDialog(
            onDismissRequest = { infoMod = null },
            containerColor = VerseColors.PcBgCard,
            titleContentColor = VerseColors.PcTextPrimary,
            textContentColor = VerseColors.PcTextSecondary,
            title = {
                Column {
                    Text((mod.name ?: mod.fileName)?.ifBlank { null } ?: "未知模组", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(loaderLabel(mod.modLoaderType), fontSize = 11.sp, color = VerseColors.PcAccent)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Mod ID", mod.id ?: "")
                    val verInfo = mod.version ?: ""
                    if (verInfo.isNotBlank()) InfoRow("版本", verInfo)
                    val gameVerInfo = mod.gameVersion ?: ""
                    if (gameVerInfo.isNotBlank()) InfoRow("游戏版本", gameVerInfo)
                    val authorInfo = mod.authors ?: ""
                    if (authorInfo.isNotBlank()) InfoRow("作者", authorInfo)
                    val desc = mod.description.toString()
                    if (desc.isNotBlank()) InfoRow("简介", desc)
                    InfoRow("文件", mod.file.fileName.toString())
                    InfoRow("状态", if (mod.isActive) "已启用" else "已禁用")
                    val oldFiles = mod.mod.oldFiles.toList()
                    if (oldFiles.isNotEmpty()) {
                        HorizontalDivider(thickness = 1.dp, color = VerseColors.PcBorderSubtle)
                        Text("旧版本（${oldFiles.size}）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextPrimary)
                        oldFiles.forEach { old ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    old.file.fileName.toString(),
                                    fontSize = 11.sp,
                                    color = VerseColors.PcTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "回滚到此版本",
                                    fontSize = 11.sp,
                                    color = VerseColors.PcAccent,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { manager.rollback(mod, old) }
                                                    showToast("已回滚")
                                                    infoMod = null
                                                    reload()
                                                } catch (e: Exception) {
                                                    showToast("回滚失败：${e.message ?: "未知错误"}")
                                                }
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoMod = null }) {
                    Text("关闭", color = VerseColors.PcAccent)
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 12.sp,
            color = VerseColors.PcTextMuted,
            modifier = Modifier.width(72.dp)
        )
        Text(value, fontSize = 12.sp, color = VerseColors.PcTextPrimary, modifier = Modifier.weight(1f))
    }
}

/**
 * 已安装模组卡片：比例、大小、布局对齐版本下载页的模组卡片
 * （GlassCard + 36dp 图标 + 名称/加载器标签 + 版本·作者 + 简介），右侧追加
 * 启用开关与删除按钮。
 */
@Composable
private fun InstalledModCard(
    mod: LocalModFile,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModIconBox(
                modFile = mod.file,
                logoPath = mod.logoPath,
                loader = mod.modLoaderType
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 汉化名命中时以中文名展示，原名退为副行
                    val zhName = com.verse.explorer.app.data.ModTranslationMapper.translateFileName(
                        mod.file.fileName?.toString() ?: ""
                    )
                    Text(
                        text = zhName
                            ?: (mod.name ?: mod.fileName)?.ifBlank { null } ?: "未知模组",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mod.isActive) VerseColors.PcTextPrimary else VerseColors.PcTextMuted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = VerseColors.PcTextMuted.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = loaderLabel(mod.modLoaderType),
                            style = MaterialTheme.typography.labelSmall,
                            color = VerseColors.PcTextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cardVersion = mod.version ?: ""
                    if (cardVersion.isNotBlank()) {
                        Text(cardVersion, fontSize = 11.sp, color = VerseColors.PcTextMuted)
                        Spacer(Modifier.width(8.dp))
                        Text("·", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                        Spacer(Modifier.width(8.dp))
                    }
                    val cardAuthors = mod.authors ?: ""
                    if (cardAuthors.isNotBlank()) {
                        Text(cardAuthors, fontSize = 11.sp, color = VerseColors.PcTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (mod.isActive) "已启用" else "已禁用",
                        fontSize = 11.sp,
                        color = if (mod.isActive) VerseColors.PcTextMuted else VerseColors.PcOrange
                    )
                }
                val desc = mod.description.toString()
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        desc,
                        fontSize = 11.sp,
                        color = VerseColors.PcTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = mod.isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = VerseColors.PcAccent,
                    uncheckedThumbColor = VerseColors.PcTextSecondary,
                    uncheckedTrackColor = VerseColors.PcBgTertiary
                ),
                modifier = Modifier.scale(0.72f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = VerseColors.PcTextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/** 模组图标：优先读取模组包内 logoPath 指向的位图，未加载/失败时显示灰色底图 */
@Composable
private fun ModIconBox(
    modFile: java.nio.file.Path,
    logoPath: String?,
    loader: ModLoaderType?
) {
    var bitmap by remember(modFile.toString(), logoPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(modFile, logoPath) {
        if (!logoPath.isNullOrBlank()) {
            bitmap = withContext(Dispatchers.IO) { readModIcon(modFile, logoPath) }
        }
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(VerseColors.PcBgActive),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** 从模组压缩包内按元数据声明的路径解出图标位图（仅读，不解压整个包） */
private fun readModIcon(modFile: java.nio.file.Path, logoPath: String): ImageBitmap? {
    return runCatching {
        val cleanPath = logoPath.trim().removePrefix("/")
        if (cleanPath.isBlank()) return@runCatching null
        ZipFile(modFile.toFile()).use { zip ->
            val entry = zip.getEntry(cleanPath)
                ?: zip.entries().asSequence().firstOrNull {
                    it.name.equals(cleanPath, ignoreCase = true) || it.name.endsWith("/$cleanPath")
                }
                ?: return@runCatching null
            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }
    }.getOrNull()
}

// 整合包内元数据缺失的 mod（无 fabric.mod.json/mods.toml 等）会让 FCL 的
// LocalModFile 字段（Java 平台类型）返回 null，加载器/版本/作者一律按可空处理
private fun loaderLabel(type: ModLoaderType?): String = when (type) {
    ModLoaderType.FORGE -> "Forge"
    ModLoaderType.NEO_FORGED -> "NeoForge"
    ModLoaderType.FABRIC -> "Fabric"
    ModLoaderType.QUILT -> "Quilt"
    ModLoaderType.LITE_LOADER -> "LiteLoader"
    ModLoaderType.PACK -> "数据包"
    ModLoaderType.CLEANROOM -> "Cleanroom"
    ModLoaderType.UNKNOWN -> "未知"
    null -> "未知"
}

/** 读取内容 URI 的显示名（无 DISPLAY_NAME 时退回 lastPathSegment，保证有可落盘的文件名） */
private fun queryDisplayName(activity: Activity, uri: android.net.Uri): String {
    runCatching {
        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                if (!name.isNullOrBlank()) return name
            }
        }
    }
    return uri.lastPathSegment ?: "imported.jar"
}
