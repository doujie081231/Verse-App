package com.verse.explorer.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.documents.GameFolderProvider
import com.verse.explorer.app.ui.components.MarqueeText
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.cardColor
import com.verse.explorer.app.ui.theme.onCardColor
import com.verse.explorer.core.config.GameConfig
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 应用内目录浏览：游戏目录位于应用私有路径，外部文件管理器在 Android 11+ 无法读取，
 * 因此与安卓启动器一致，用应用自身的浏览窗口展示目录内容（只读、可进入子目录）。
 */
@Composable
internal fun FolderBrowserDialog(title: String, startDir: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(startDir) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(current) {
        entries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            current.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardColor(),
        titleContentColor = onCardColor(),
        shape = MaterialTheme.shapes.large,
        title = { Text("$title 文件夹", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = current.absolutePath,
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PcButton(
                        text = "上一级",
                        enabled = current.parentFile != null,
                        onClick = { current.parentFile?.let { current = it } },
                        variant = PcButtonVariant.OUTLINE
                    )
                    Spacer(Modifier.width(8.dp))
                    PcButton(
                        text = "用其他应用打开",
                        onClick = { openFolderWithOtherApp(context, current) },
                        variant = PcButtonVariant.OUTLINE
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${entries.size} 项", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                }
                HorizontalDivider(color = VerseColors.PcBorder, thickness = 1.dp)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(entries, key = { it.absolutePath }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .then(
                                    if (entry.isDirectory) Modifier.clickable { current = entry }
                                    else Modifier
                                )
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                                contentDescription = null,
                                tint = if (entry.isDirectory) VerseColors.PcAccent else VerseColors.PcTextMuted
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                modifier = Modifier.weight(1f),
                                text = entry.name,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!entry.isDirectory) {
                                Text(formatFileSize(entry.length()), fontSize = 11.sp, color = VerseColors.PcTextMuted)
                            }
                        }
                    }
                    if (entries.isEmpty()) {
                        item { Text("空目录", fontSize = 12.sp, color = VerseColors.PcTextMuted) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/**
 * 用其他应用打开目录：外部存储路径转成系统文档 URI，
 * 应用内部游戏目录则通过自身的文档提供器暴露，供文件管理器浏览；其余私有路径给出提示。
 */
internal fun openFolderWithOtherApp(context: Context, folder: File) {
    val target = folder.absoluteFile
    if (!target.isDirectory) {
        toast(context, "目录不存在")
        return
    }
    val uri = folderDocumentUri(context, target)
    if (uri == null) {
        toast(context, "该目录无法用其他应用打开")
        return
    }

    var intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        clipData = android.content.ClipData.newRawUri("folder", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    }

    // 系统文件管理器只识别文档提供器的根 URI，收到子目录文档 URI 会退回“下载”页；
    // 若当前只有系统文件管理器能处理，则改为打开游戏目录根，保证有可用落点
    val handlers = context.packageManager.queryIntentActivities(intent, 0)
    val onlySystemFiles = handlers.isNotEmpty() && handlers.all {
        it.activityInfo.packageName == "com.android.documentsui" ||
            it.activityInfo.packageName == "com.google.android.documentsui"
    }
    if (onlySystemFiles && uri.authority == GameFolderProvider.authority(context)) {
        intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                DocumentsContract.buildRootUri(
                    GameFolderProvider.authority(context),
                    GameFolderProvider.ROOT_ID
                ),
                "vnd.android.document/root"
            )
        }
    }

    val chooser = Intent.createChooser(intent, "用其他应用打开").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
        .onFailure { toast(context, "没有可打开该目录的应用") }
}

/** 目录 → 可交给外部应用的文档 URI：优先系统外部存储提供器，其次应用自身的游戏目录提供器 */
private fun folderDocumentUri(context: Context, folder: File): Uri? {
    val target = runCatching { folder.canonicalFile }.getOrNull() ?: return null
    val path = target.absolutePath
    val primaryRoot = Environment.getExternalStorageDirectory().absolutePath

    if (path == primaryRoot) {
        return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:")
    }
    if (path.startsWith("$primaryRoot/")) {
        return DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_AUTHORITY,
            "primary:" + path.removePrefix("$primaryRoot/")
        )
    }
    Regex("^/storage/([^/]+)/").find(path)?.let { match ->
        return DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_AUTHORITY,
            match.groupValues[1] + ":" + path.substring(match.range.last + 1)
        )
    }

    val gameDir = runCatching { GameConfig.getGameDir(context).canonicalFile }.getOrNull()
    if (gameDir != null &&
        (path == gameDir.absolutePath || path.startsWith(gameDir.absolutePath + File.separator))
    ) {
        return GameFolderProvider.documentUri(context, target)
    }
    return null
}

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

/** 目录条目行：图标 + 名称 + 大小 + 删除 */
@Composable
internal fun FolderEntryRow(entry: File, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory) VerseColors.PcAccent else VerseColors.PcTextMuted
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(text = entry.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = if (entry.isDirectory) "文件夹" else formatFileSize(entry.length()),
                    fontSize = 10.sp,
                    color = VerseColors.PcTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = VerseColors.PcOrange
                )
            }
        }
    }
}

/** 读取 SAF 所选文件的显示名 */
internal fun queryDisplayName(context: Context, uri: Uri): String {
    var name: String? = null
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index)
        }
    }
    return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported"
}

/** 解压 zip 到目标目录；条目路径越出目标目录时跳过 */
internal fun unzipInto(input: java.io.InputStream, targetDir: File) {
    targetDir.mkdirs()
    val canonicalRoot = targetDir.canonicalPath + File.separator
    ZipInputStream(input.buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val outFile = File(targetDir, entry.name)
            if (outFile.canonicalPath.startsWith(canonicalRoot)) {
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
}

/** 文件大小缩写 */
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "${bytes / (1024L * 1024 * 1024)} GB"
    bytes >= 1024L * 1024 -> "${bytes / (1024L * 1024)} MB"
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
