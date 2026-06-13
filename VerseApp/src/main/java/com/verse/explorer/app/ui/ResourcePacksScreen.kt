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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.verse.explorer.core.resource.ModrinthSearchHit

private val resolutionRegex = Regex("\\d+x", RegexOption.IGNORE_CASE)

private fun extractResolution(categories: List<String>): String {
    return categories.firstOrNull { resolutionRegex.matches(it) } ?: ""
}

private val iconGradients = listOf(
    listOf(VerseColors.McGreen, VerseColors.McGreen.copy(alpha = 0.6f)),
    listOf(VerseColors.McGreen, VerseColors.McGreen.copy(alpha = 0.6f)),
    listOf(VerseColors.Yellow, VerseColors.McGold.copy(alpha = 0.6f)),
    listOf(VerseColors.McWater, VerseColors.McWater.copy(alpha = 0.6f)),
    listOf(VerseColors.McRedstone, VerseColors.McGold.copy(alpha = 0.6f)),
    listOf(VerseColors.GradientPurpleStart, VerseColors.GradientPurpleEnd),
    listOf(VerseColors.McGreen, VerseColors.McGreen.copy(alpha = 0.6f)),
    listOf(VerseColors.McGold, VerseColors.Yellow.copy(alpha = 0.7f))
)

private fun gradientForId(id: String): List<Color> {
    val index = Math.abs(id.hashCode()) % iconGradients.size
    return iconGradients[index]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcePacksScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val viewModel = remember { ResourceViewModel(context.applicationContext as android.app.Application, "resourcepack") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    val versions = listOf("全部", "1.21", "1.20", "1.19", "1.18", "1.17", "1.16")
    val totalPages = if (uiState.totalHits > 0) (uiState.totalHits + 19) / 20 else 1

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
                        Text("搜索材质包...", color = VerseColors.McTextMuted, fontSize = 13.sp)
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
                        text = "游戏版本",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McTextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FilterRow(versions, uiState.selectedVersion) { viewModel.setSelectedVersion(it) }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "分辨率",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McTextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val resolutions = listOf("全部", "8x", "16x", "32x", "64x", "128x", "256x", "512x")
                    var selectedResolution by remember { mutableStateOf("全部") }
                    FilterRow(resolutions, selectedResolution) { selectedResolution = it }
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
                    text = "共 ${uiState.totalHits} 个材质包",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McTextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = "${uiState.currentPage + 1}/$totalPages",
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
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = VerseColors.McRedstone
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.error ?: "加载失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VerseColors.McTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = { viewModel.search(searchQuery) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = VerseColors.McGreen.copy(alpha = 0.12f),
                                contentColor = VerseColors.McGreen
                            )
                        ) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = VerseColors.McGreen,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "加载材质包中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VerseColors.McTextMuted
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
                        ResourcePackCard(hit, onClick = { navController.navigate("resourcepack_detail/${hit.projectId}") })
                    }
                    if (uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
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
                    if (uiState.error != null && results.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = uiState.error ?: "加载失败",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VerseColors.McRedstone,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { viewModel.loadMore() }) {
                                        Text("重试", color = VerseColors.McGreen, fontSize = 12.sp)
                                    }
                                }
                            }
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
                    enabled = uiState.currentPage > 0,
                    shape = RoundedCornerShape(8.dp, 0.dp, 0.dp, 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VerseColors.McCardBg,
                        contentColor = VerseColors.McTextSecondary,
                        disabledContainerColor = VerseColors.McCardBg,
                        disabledContentColor = VerseColors.McTextDim
                    )
                ) {
                    Icon(Icons.Outlined.ChevronLeft, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("上一页", fontSize = 12.sp)
                }

                Text(
                    text = " ${uiState.currentPage + 1} / $totalPages ",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                FilledTonalButton(
                    onClick = { viewModel.loadMore() },
                    enabled = uiState.currentPage + 1 < totalPages && !uiState.isLoading,
                    shape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VerseColors.McCardBg,
                        contentColor = VerseColors.McTextSecondary,
                        disabledContainerColor = VerseColors.McCardBg,
                        disabledContentColor = VerseColors.McTextDim
                    )
                ) {
                    Text("下一页", fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
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
private fun ResourcePackCard(hit: ModrinthSearchHit, onClick: () -> Unit = {}) {
    val resolution = extractResolution(hit.categories)
    val gameVersion = hit.gameVersions.firstOrNull() ?: ""
    val gradient = gradientForId(hit.projectId)

    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(gradient)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val iconUrl = hit.iconUrl
                if (iconUrl != null && iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = VerseColors.McTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (resolution.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.McWater.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = resolution,
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.McWater,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "作者: ${hit.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McTextSecondary,
                        fontSize = 11.sp
                    )
                    if (gameVersion.isNotEmpty()) {
                        Text(
                            text = "MC $gameVersion",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerseColors.McTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = hit.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VerseColors.McTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabelWithIcon(
                        icon = Icons.Outlined.Download,
                        text = formatDownloadCount(hit.downloads),
                        color = VerseColors.McTextSecondary
                    )
                }
            }

            FilledTonalButton(
                onClick = {},
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = VerseColors.McWater.copy(alpha = 0.12f),
                    contentColor = VerseColors.McWater
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
    }
}
