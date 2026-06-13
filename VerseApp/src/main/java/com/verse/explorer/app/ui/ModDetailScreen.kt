package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.resource.ModrinthApi
import com.verse.explorer.core.resource.ModrinthProject
import com.verse.explorer.core.resource.ModrinthVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModDetailScreen(
    navController: NavHostController,
    projectId: String,
    projectType: String = "mod"
) {
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<ModrinthProject?>(null) }
    var versions by remember { mutableStateOf<List<ModrinthVersion>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedVersion by remember { mutableStateOf<ModrinthVersion?>(null) }
    var retryCount by remember { mutableStateOf(0) }

    val fetchData = suspend {
        try {
            val proj = withContext(Dispatchers.IO) { ModrinthApi.getProject(projectId, true) }
            project = proj
            VerseLogger.info("Project loaded: ${proj.title} (id=$projectId, type=$projectType)", "ModDetail")
            try {
                val vers = withContext(Dispatchers.IO) { ModrinthApi.getProjectVersions(projectId, true) }
                versions = vers
                selectedVersion = vers.firstOrNull()
                VerseLogger.info("Versions loaded: ${vers.size}", "ModDetail")
            } catch (e: Exception) {
                VerseLogger.warn("Failed to load versions: ${e.message}", "ModDetail")
            }
            isLoading = false
        } catch (e: Exception) {
            val msg = e.message ?: "未知错误"
            VerseLogger.error("Detail load failed: $msg (projectId=$projectId, type=$projectType, retry=$retryCount)", "ModDetail")
            error = "加载失败: $msg"
            isLoading = false
        }
    }

    LaunchedEffect(projectId, retryCount) {
        if (projectId.isBlank()) {
            error = "项目ID为空"
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        scope.launch { fetchData() }
    }

    val typeLabel = when (projectType) {
        "modpack" -> "整合包"
        "resourcepack" -> "资源包"
        "shader" -> "光影"
        else -> "模组"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.McBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            when {
                isLoading -> {
                    item {
                        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VerseColors.McGreen, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                error != null -> {
                    item {
                        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp), VerseColors.McTextMuted)
                                Text(error ?: "未知错误", color = VerseColors.McTextSecondary, fontSize = 14.sp)
                                OutlinedButton(onClick = { retryCount++ }, shape = RoundedCornerShape(8.dp)) {
                                    Text("重试", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                project != null -> {
                    val proj = project!!
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    try { navController.popBackStack() } catch (_: Exception) {}
                                }) {
                                    Icon(Icons.Filled.ArrowBack, "返回", tint = VerseColors.McTextPrimary)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(typeLabel + "详情", color = VerseColors.McTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = VerseColors.McCardBg,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    if (proj.iconUrl != null && proj.iconUrl!!.isNotBlank()) {
                                        AsyncImage(
                                            model = proj.iconUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Extension, null, Modifier.size(32.dp), VerseColors.McTextMuted)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        proj.title.ifEmpty { projectId },
                                        color = VerseColors.McTextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (proj.author.isNotBlank()) {
                                            Text(proj.author, color = VerseColors.McTextSecondary, fontSize = 13.sp)
                                        } else if (proj.team.isNotBlank()) {
                                            Text("Team: ${proj.team}", color = VerseColors.McTextSecondary, fontSize = 13.sp)
                                        }
                                        Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.McGreen.copy(alpha = 0.1f)) {
                                            Text(typeLabel, color = VerseColors.McGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        val dl = proj.downloads
                                        val dlStr = when {
                                            dl > 1_000_000 -> "${dl / 1_000_000}M"
                                            dl > 1_000 -> "${dl / 1_000}k"
                                            else -> "$dl"
                                        }
                                        StatChip(Icons.Filled.Download, dlStr)
                                        StatChip(Icons.Filled.Star, "${proj.followers}")
                                        StatChip(Icons.Filled.PhoneAndroid, "${proj.clientSide}/${proj.serverSide}")
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text("描述", color = VerseColors.McTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                proj.description,
                                color = VerseColors.McTextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )

                            if (proj.categories.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("分类", color = VerseColors.McTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    proj.categories.take(6).forEach { cat ->
                                        Surface(shape = RoundedCornerShape(6.dp), color = VerseColors.McGreen.copy(alpha = 0.08f)) {
                                            Text(cat, color = VerseColors.McGreen, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                        }
                                    }
                                }
                            }

                            if (proj.gameVersions.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("支持版本", color = VerseColors.McTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                    proj.gameVersions.take(8).forEach { ver ->
                                        Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.McCardBg) {
                                            Text(ver, color = VerseColors.McTextSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                }
                            }

                            if (proj.loaders.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("加载器", color = VerseColors.McTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                    proj.loaders.forEach { loader ->
                                        Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.McGold.copy(alpha = 0.08f)) {
                                            Text(loader, color = VerseColors.McGold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(20.dp))
                            Text("版本列表 (${versions.size})", color = VerseColors.McTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    items(versions) { version ->
                        val isSelected = selectedVersion?.id == version.id
                        VersionsDetailCard(
                            version = version,
                            isSelected = isSelected,
                            onClick = { selectedVersion = version },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = VerseColors.McTextMuted)
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 11.sp, color = VerseColors.McTextMuted)
    }
}

@Composable
private fun VersionsDetailCard(
    version: ModrinthVersion,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    version.versionNumber,
                    color = if (isSelected) VerseColors.McGreen else VerseColors.McTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val primaryFile = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
                if (primaryFile != null) {
                    Text(
                        formatFileSize(primaryFile.size),
                        color = VerseColors.McTextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                version.gameVersions.take(6).forEach { ver ->
                    Surface(shape = RoundedCornerShape(3.dp), color = VerseColors.McBg) {
                        Text(ver, color = VerseColors.McTextSecondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                version.loaders.take(3).forEach { loader ->
                    Surface(shape = RoundedCornerShape(3.dp), color = VerseColors.McGold.copy(alpha = 0.1f)) {
                        Text(loader, color = VerseColors.McGold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / 1024 / 1024)} MB"
    }
}