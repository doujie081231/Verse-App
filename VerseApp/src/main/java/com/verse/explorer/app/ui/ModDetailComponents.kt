package com.verse.explorer.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.VerseProgressBar
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.resource.McVersionGroups
import com.verse.explorer.core.resource.ResourceVersion
import com.verse.explorer.core.version.VersionType
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 前置模组紧凑行（与资源列表行同密度：单行 36dp 图标 + 13sp 标题 +
 * 徽章 + 下载量元信息 + 右侧箭头，不展示描述）。
 */
@Composable
internal fun DependencyRow(
    title: String,
    iconUrl: String?,
    badge: String,
    badgeColor: Color,
    meta: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcBgActive),
                contentAlignment = Alignment.Center
            ) {
                if (!iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = VerseColors.PcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = badgeColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            badge,
                            fontSize = 9.sp,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            meta,
                            fontSize = 11.sp,
                            color = VerseColors.PcTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), VerseColors.PcTextMuted)
        }
    }
}

@Composable
internal fun MdTabBtn(label: String, text: String, current: String, onSelect: (String) -> Unit) {
    val selected = current == label
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) VerseColors.PcAccent else VerseColors.PcBgTertiary,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onSelect(label) }
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
        )
    }
}

@Composable
internal fun MdVTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) VerseColors.PcAccent else VerseColors.PcBgTertiary,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun MdSidebarSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = VerseColors.PcTextMuted,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MdSidebarTags(tags: List<String>, emptyAsDash: Boolean) {
    if (tags.isEmpty()) {
        if (emptyAsDash) Text("—", fontSize = 12.sp, color = VerseColors.PcTextMuted)
        return
    }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tags.forEach { tag ->
            Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.PcBgTertiary) {
                Text(tag, fontSize = 10.sp, color = VerseColors.PcTextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
internal fun MdSidebarLinks(links: List<Pair<String, String>>) {
    if (links.isEmpty()) {
        Text("—", fontSize = 12.sp, color = VerseColors.PcTextMuted)
        return
    }
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        links.forEach { (label, url) ->
            Text(
                label,
                fontSize = 12.sp,
                color = VerseColors.PcAccent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "无可用浏览器", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
internal fun MdSidebarDetailRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, fontSize = 11.sp, color = VerseColors.PcTextMuted, modifier = Modifier.width(60.dp))
        Text(value, fontSize = 12.sp, color = VerseColors.PcTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


/**
 * 「适用版本」标签比较器：先数字版本（新的在前），再快照版，最后远古版/其他。
 * 对齐 PCL 用 VersionComparer 对分组名排序后的视觉顺序。
 */
internal fun versionTabComparator(): Comparator<String> = Comparator { a, b ->
    val ra = versionTabRank(a)
    val rb = versionTabRank(b)
    if (ra != rb) ra - rb else if (ra == 0) McVersionGroups.versionComparator().compare(a, b) else 0
}

private fun versionTabRank(name: String): Int = when (name) {
    McVersionGroups.SNAPSHOT -> 1
    McVersionGroups.ANCIENT -> 2
    McVersionGroups.OTHER -> 3
    else -> 0
}


internal fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}

internal fun mdFormatDate(dateStr: String): String = if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr

@Composable
internal fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = VerseColors.PcTextMuted)
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 11.sp, color = VerseColors.PcTextMuted)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun VersionsDetailCard(
    version: ResourceVersion,
    isSelected: Boolean,
    onClick: () -> Unit,
    selected: Boolean,
    downloading: Boolean,
    downloadProgress: Float,
    downloadText: String,
    downloadDone: Boolean,
    downloadError: String?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VerseColors.PcAccent.copy(alpha = 0.08f) else VerseColors.PcBgCard,
        border = BorderStroke(
            1.dp,
            if (selected) VerseColors.PcAccent.copy(alpha = 0.5f) else VerseColors.PcBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // 「等 N 个版本」展开状态：点开显示该文件支持的全部游戏版本
            var showAllGameVersions by remember { mutableStateOf(false) }
            // 与 PCL 一致：适用版本按从新到旧排序，只显示最新一个 + 其余数量，
            // 否则 API 原始顺序会给出「1.16.5、1.20.1」这种看起来毫无意义的组合
            val sortedGameVersions = remember(version.gameVersions) {
                McVersionGroups.sortDescending(version.gameVersions)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        version.versionNumber,
                        color = if (isSelected) VerseColors.PcAccent else VerseColors.PcTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sortedGameVersions.firstOrNull()?.let { newest ->
                            Surface(shape = RoundedCornerShape(3.dp), color = VerseColors.PcBgSecondary) {
                                Text(newest, color = VerseColors.PcTextSecondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                        if (sortedGameVersions.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = VerseColors.PcBgSecondary,
                                modifier = Modifier.clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { showAllGameVersions = !showAllGameVersions }
                            ) {
                                Text(
                                    if (showAllGameVersions) "收起" else "等 ${sortedGameVersions.size} 个版本",
                                    color = VerseColors.PcAccent,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        version.loaders.take(2).forEach { loader ->
                            Surface(shape = RoundedCornerShape(3.dp), color = VerseColors.PcOrange.copy(alpha = 0.1f)) {
                                Text(loader, color = VerseColors.PcOrange, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                        val primaryFile = version.primaryFile ?: version.files.firstOrNull()
                        if (primaryFile != null) {
                            Text(
                                formatFileSize(primaryFile.size),
                                color = VerseColors.PcTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                when {
                    isSelected && downloading -> {
                        RoseLoader(
                            modifier = Modifier.size(18.dp),
                            size = 18.dp,
                            color = VerseColors.PcAccent
                        )
                    }
                    isSelected && downloadDone -> {
                        Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = VerseColors.PcAccent)
                    }
                    isSelected && !downloading -> {
                        Button(
                            onClick = onDownload,
                            enabled = version.primaryFile?.url?.isNotBlank() != false,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VerseColors.PcAccent,
                                contentColor = VerseColors.PcAccentText
                            )
                        ) {
                            Icon(Icons.Filled.Download, null, Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("安装", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    else -> {
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), VerseColors.PcTextMuted)
                    }
                }
            }

            // 「等 N 个版本」展开：显示该文件支持的全部游戏版本，确认支持自己的版本后再点安装
            if (showAllGameVersions && sortedGameVersions.size > 1) {
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    sortedGameVersions.forEach { gv ->
                        Surface(shape = RoundedCornerShape(3.dp), color = VerseColors.PcBgSecondary) {
                            Text(
                                gv,
                                color = VerseColors.PcTextSecondary,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // 选中态的下载进度与结果反馈（紧凑行）
            when {
                isSelected && downloading -> {
                    Spacer(Modifier.height(6.dp))
                    VerseProgressBar(
                        progress = downloadProgress,
                        modifier = Modifier.fillMaxWidth(),
                        height = 6.dp,
                        color = VerseColors.PcAccent,
                        trackColor = VerseColors.PcBgSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(downloadText, color = VerseColors.PcTextMuted, fontSize = 11.sp)
                }
                isSelected && downloadError != null -> {
                    Spacer(Modifier.height(4.dp))
                    Text(downloadError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * 使用 Markwon（Android 标准 Markdown 库，内部基于 commonmark-java）在 TextView 中渲染，
 * 解析在后台线程执行，避免大 body 阻塞 UI。
 */
@Composable
internal fun MarkdownBody(
    body: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context).build()
    }
    val spanned by produceState<CharSequence>(initialValue = body, body) {
        value = try {
            withContext(Dispatchers.Default) { markwon.toMarkdown(body) }
        } catch (_: Exception) {
            body
        }
    }
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
            }
        },
        update = { tv ->
            tv.setTextColor(textColor.toArgb())
            // 链接色跟随主题强调色（TextView 默认链接色为蓝色，与主题不符）
            tv.setLinkTextColor(VerseColors.PcAccent.toArgb())
            tv.highlightColor = VerseColors.PcAccent.copy(alpha = 0.25f).toArgb()
            tv.setTextSize(13f) // sp
            tv.setLineSpacing(0f, 1.4f)
            tv.text = spanned
        },
        modifier = modifier
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / 1024 / 1024)} MB"
    }
}

internal fun formatSpeed(speed: Long): String {
    return when {
        speed < 1024 -> "$speed B/s"
        speed < 1024 * 1024 -> "${speed / 1024} KB/s"
        else -> "${"%.1f".format(speed.toDouble() / 1024 / 1024)} MB/s"
    }
}
