package com.verse.explorer.app.ui

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
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.LabelWithIcon
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.resource.ModrinthApi
import com.verse.explorer.core.resource.ModrinthProject
import com.verse.explorer.core.resource.ModrinthSearchHit
import com.verse.explorer.core.resource.ModrinthVersion

@Composable
internal fun ModpackCard(hit: ModrinthSearchHit, onClick: () -> Unit = {}) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VerseColors.PcBgTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    // 始终显示占位图标（加载失败时透出来）
                    Icon(
                        imageVector = Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    // 有图标 URL 时叠加 AsyncImage 在上层
                    val iconUrl = hit.iconUrl
                    if (iconUrl != null && iconUrl.isNotEmpty()) {
                        AsyncImage(
                            model = iconUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = VerseColors.PcTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "作者: ${hit.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = hit.description,
                style = MaterialTheme.typography.bodySmall,
                color = VerseColors.PcTextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabelWithIcon(
                        icon = Icons.Outlined.Download,
                        text = formatDownloadCount(hit.downloads),
                        color = VerseColors.PcTextSecondary
                    )

                    hit.loaders.firstOrNull()?.let { loader ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.PcAccent.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = loader.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.PcAccent,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    hit.gameVersions.firstOrNull()?.let { version ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.PcAccent.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = version,
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.PcAccent,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = {},
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VerseColors.PcAccent.copy(alpha = 0.15f),
                        contentColor = VerseColors.PcAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "安装",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }

            if (hit.categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hit.categories.take(4).forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.PcTextMuted.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.PcTextMuted,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
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
                        CircularProgressIndicator(Modifier.size(24.dp), VerseColors.PcAccent, strokeWidth = 2.dp)
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
                colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcAccent)
            ) {
                Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
