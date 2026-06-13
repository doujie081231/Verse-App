package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.ResourceViewModel
import com.verse.explorer.core.resource.ModrinthApi
import com.verse.explorer.core.resource.ModrinthProject
import com.verse.explorer.core.resource.ModrinthSearchHit
import com.verse.explorer.core.resource.ModrinthVersion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpacksScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val viewModel = remember { ResourceViewModel(context.applicationContext as android.app.Application, "modpack") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    val loaders = listOf("全部", "Fabric", "Forge", "NeoForge", "Quilt", "Vanilla")
    val versions = listOf("全部", "1.21", "1.20", "1.19", "1.18", "1.17", "1.16")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.McBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("搜索整合包...", color = VerseColors.McTextMuted, fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = VerseColors.McTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.search("")
                            }) {
                                Icon(
                                    Icons.Outlined.Clear,
                                    contentDescription = null,
                                    tint = VerseColors.McTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerseColors.McGreen.copy(alpha = 0.3f),
                        unfocusedBorderColor = VerseColors.McCardBorder,
                        focusedContainerColor = VerseColors.McCardBg,
                        unfocusedContainerColor = VerseColors.McCardBg,
                        focusedTextColor = VerseColors.McTextPrimary,
                        unfocusedTextColor = VerseColors.McTextPrimary,
                        cursorColor = VerseColors.McGreen
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { viewModel.search(searchQuery) }
                    )
                )

                GlassCard(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { viewModel.search(searchQuery) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = VerseColors.McTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "加载器",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McTextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FilterRow(loaders, uiState.selectedLoader) { viewModel.setSelectedLoader(it) }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "游戏版本",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McTextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FilterRow(versions, uiState.selectedVersion) { viewModel.setSelectedVersion(it) }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "共 ${uiState.totalHits} 个整合包",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McTextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = "已加载 ${results.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McTextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.error != null && results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = VerseColors.McTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.error ?: "加载失败",
                            color = VerseColors.McTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = {
                                viewModel.clearError()
                                viewModel.search(searchQuery)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = VerseColors.McGreen.copy(alpha = 0.15f),
                                contentColor = VerseColors.McGreen
                            )
                        ) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重试", fontSize = 13.sp)
                        }
                    }
                }
            } else if (uiState.isLoading && results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = VerseColors.McGreen,
                        strokeWidth = 3.dp
                    )
                }
            } else if (results.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = null,
                            tint = VerseColors.McTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "没有找到整合包",
                            color = VerseColors.McTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(results) { hit ->
                        ModpackCard(hit, onClick = { navController.navigate("modpack_detail/${hit.projectId}") })
                    }
                    if (uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = VerseColors.McGreen,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.search(searchQuery) },
                        shape = RoundedCornerShape(8.dp, 0.dp, 0.dp, 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = VerseColors.McCardBg,
                            contentColor = VerseColors.McTextSecondary
                        )
                    ) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刷新", fontSize = 12.sp)
                    }

                    Text(
                        text = " ${results.size} / ${uiState.totalHits} ",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    FilledTonalButton(
                        onClick = { viewModel.loadMore() },
                        enabled = results.size < uiState.totalHits && !uiState.isLoading,
                        shape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = VerseColors.McCardBg,
                            contentColor = VerseColors.McTextSecondary,
                            disabledContainerColor = VerseColors.McCardBg,
                            disabledContentColor = VerseColors.McTextDim
                        )
                    ) {
                        Text("加载更多", fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(options.size) { index ->
            val option = options[index]
            val isSelected = selected == option
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) VerseColors.McGreen.copy(alpha = 0.12f) else VerseColors.McCardBg,
                modifier = Modifier.clickable { onSelect(option) }
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) VerseColors.McGreen else VerseColors.McTextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ModpackCard(hit: ModrinthSearchHit, onClick: () -> Unit = {}) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(VerseColors.McGreen, VerseColors.McGreen.copy(alpha = 0.6f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val iconUrl = hit.iconUrl
                    if (iconUrl != null && iconUrl.isNotEmpty()) {
                        AsyncImage(
                            model = iconUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Inventory2,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = VerseColors.McTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "作者: ${hit.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = hit.description,
                style = MaterialTheme.typography.bodySmall,
                color = VerseColors.McTextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

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
                        color = VerseColors.McTextSecondary
                    )

                    hit.loaders.firstOrNull()?.let { loader ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.McGreen.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = loader.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.McGreen,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    hit.gameVersions.firstOrNull()?.let { version ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.McGreen.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = version,
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.McGreen,
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
                        containerColor = VerseColors.McGreen.copy(alpha = 0.15f),
                        contentColor = VerseColors.McGreen
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hit.categories.take(4).forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.McTextDim.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.McTextMuted,
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
private fun LabelWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 11.sp
        )
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
    val useMirror = com.verse.explorer.app.data.SettingsRepository(context).getDownloadSource() != "mojang"

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
        containerColor = VerseColors.McOverlayContentBg,
        titleContentColor = VerseColors.McTextPrimary,
        textContentColor = VerseColors.McTextSecondary,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(VerseColors.McGreen.copy(alpha = 0.1f)),
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
                        VerseColors.McGreen
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(hit.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("作者: ${project?.author ?: hit.author}", fontSize = 11.sp, color = VerseColors.McTextSecondary)
                }
            }
        },
        text = {
            Column {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), VerseColors.McGreen, strokeWidth = 2.dp)
                    }
                } else if (error != null) {
                    Text("加载失败: $error", color = VerseColors.McRedstone, fontSize = 13.sp)
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

                        Text(p.description, fontSize = 13.sp, color = VerseColors.McTextPrimary, lineHeight = 18.sp)
                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.gameVersions.take(4).forEach { v ->
                                Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.McGreen.copy(alpha = 0.1f)) {
                                    Text(v, fontSize = 10.sp, color = VerseColors.McGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            if (p.gameVersions.size > 4) {
                                Text("+${p.gameVersions.size - 4}", fontSize = 10.sp, color = VerseColors.McTextMuted)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.loaders.forEach { l ->
                                Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.McGreen.copy(alpha = 0.1f)) {
                                    Text(l, fontSize = 10.sp, color = VerseColors.McGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text("下载量: ${formatDownloadCount(p.downloads)}", fontSize = 11.sp, color = VerseColors.McTextSecondary)
                            Spacer(Modifier.width(16.dp))
                            Text("关注: ${formatDownloadCount(p.followers)}", fontSize = 11.sp, color = VerseColors.McTextSecondary)
                        }
                    }

                    if (versions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = VerseColors.McCardBorder)
                        Spacer(Modifier.height(8.dp))
                        Text("可用版本 (${versions.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.McTextPrimary)
                        Spacer(Modifier.height(6.dp))

                        versions.take(5).forEach { version ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = VerseColors.McCardBg,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(version.versionNumber, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = VerseColors.McTextPrimary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            version.gameVersions.take(2).forEach { v ->
                                                Text(v, fontSize = 10.sp, color = VerseColors.McTextMuted)
                                            }
                                            version.loaders.take(2).forEach { l ->
                                                Text(l, fontSize = 10.sp, color = VerseColors.McTextMuted)
                                            }
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = {},
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = VerseColors.McGreen.copy(alpha = 0.12f),
                                            contentColor = VerseColors.McGreen
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
                colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McGreen)
            ) {
                Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
