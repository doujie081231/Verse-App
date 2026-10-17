package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.LabelWithIcon
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.resource.ModrinthApi
import com.verse.explorer.core.resource.ModrinthProject
import com.verse.explorer.core.resource.ModrinthSearchHit
import com.verse.explorer.core.resource.ModrinthVersion

/**
 * 资源搜索结果卡片（对齐版本下载页列表行的密度：单行紧凑布局），
 * 图标 + 标题 + 徽章/元信息行 + 右侧箭头，不展示描述与分类标签行。
 */
@Composable
internal fun ResourceSearchCard(
    hit: ModrinthSearchHit,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标未加载/无图标时显示灰色底图
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcBgActive),
                contentAlignment = Alignment.Center
            ) {
                if (!hit.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = hit.iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    hit.loaders.firstOrNull()?.let { loader ->
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = VerseColors.PcAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = loader.replaceFirstChar { it.uppercase() },
                                fontSize = 9.sp,
                                color = VerseColors.PcAccent,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    hit.gameVersions.firstOrNull()?.let { version ->
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = VerseColors.PcAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = version,
                                fontSize = 9.sp,
                                color = VerseColors.PcAccent,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "${formatDownloadCount(hit.downloads)} 下载 · ${hit.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), VerseColors.PcTextMuted)
        }
    }
}

@Composable
internal fun ModpackCard(hit: ModrinthSearchHit, onClick: () -> Unit = {}) {
    ResourceSearchCard(hit, onClick = onClick)
}

@Composable
internal fun ResourceDetailDialog(
    hit: ModrinthSearchHit,
    projectType: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val useMirror = com.verse.explorer.app.data.SettingsRepository.getDownloadSource() != "mojang"

    var project by remember { mutableStateOf<ModrinthProject?>(null) }
    var versions by remember { mutableStateOf<List<ModrinthVersion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hit.projectId) {
        isLoading = true
        try {
            project = ModrinthApi.getProject(hit.projectId, useMirror)
            versions = ModrinthApi.getProjectVersions(hit.projectId, useMirror)
        } catch (e: Exception) {
            error = e.message
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VerseColors.PcBgCard,
        titleContentColor = VerseColors.PcTextPrimary,
        textContentColor = VerseColors.PcTextSecondary,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(VerseColors.PcAccent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (projectType) {
                            "modpack" -> Icons.Filled.Category
                            "resourcepack" -> Icons.Filled.Brush
                            "shader" -> Icons.Filled.AutoAwesome
                            else -> Icons.Filled.Extension
                        },
                        null,
                        Modifier.size(20.dp),
                        VerseColors.PcAccent
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(hit.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("作者: ${project?.author ?: hit.author}", fontSize = 11.sp, color = VerseColors.PcTextSecondary)
                }
            }
        },
        text = {
            Column {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        RoseLoader(
                            modifier = Modifier.size(24.dp),
                            size = 24.dp,
                            color = VerseColors.PcAccent
                        )
                    }
                } else if (error != null) {
                    Text("加载失败: $error", color = VerseColors.PcRed, fontSize = 13.sp)
                } else {
                    if (project != null) {
                        val p = project!!
                        if (!p.iconUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = p.iconUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        Text(p.description, fontSize = 13.sp, color = VerseColors.PcTextPrimary, lineHeight = 18.sp)
                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.gameVersions.take(4).forEach { v ->
                                Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.PcAccent.copy(alpha = 0.1f)) {
                                    Text(v, fontSize = 10.sp, color = VerseColors.PcAccent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            if (p.gameVersions.size > 4) {
                                Text("+${p.gameVersions.size - 4}", fontSize = 10.sp, color = VerseColors.PcTextMuted)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.loaders.forEach { l ->
                                Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.PcAccent.copy(alpha = 0.1f)) {
                                    Text(l, fontSize = 10.sp, color = VerseColors.PcAccent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text("下载量: ${formatDownloadCount(p.downloads)}", fontSize = 11.sp, color = VerseColors.PcTextSecondary)
                            Spacer(Modifier.width(16.dp))
                            Text("关注: ${formatDownloadCount(p.followers)}", fontSize = 11.sp, color = VerseColors.PcTextSecondary)
                        }
                    }

                    if (versions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = VerseColors.PcBorder)
                        Spacer(Modifier.height(8.dp))
                        Text("可用版本 (${versions.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.PcTextPrimary)
                        Spacer(Modifier.height(6.dp))

                        versions.take(5).forEach { version ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VerseColors.PcBgCard,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(version.versionNumber ?: "", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = VerseColors.PcTextPrimary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            (version.gameVersions ?: emptyList()).take(2).forEach { v ->
                                                Text(v, fontSize = 10.sp, color = VerseColors.PcTextMuted)
                                            }
                                            (version.loaders ?: emptyList()).take(2).forEach { l ->
                                                Text(l, fontSize = 10.sp, color = VerseColors.PcTextMuted)
                                            }
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = {},
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = VerseColors.PcAccent.copy(alpha = 0.12f),
                                            contentColor = VerseColors.PcAccent
                                        )
                                    ) {
                                        Icon(Icons.Outlined.Download, null, Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("安装", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VerseColors.PcAccent,
                    contentColor = VerseColors.PcAccentText
                )
            ) {
                Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
