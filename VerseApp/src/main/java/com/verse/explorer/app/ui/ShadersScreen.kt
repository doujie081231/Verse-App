package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.LabelWithIcon
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.PcPagination
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.ResourceViewModel
import com.verse.explorer.core.resource.ModrinthSearchHit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadersScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val viewModel = remember { ResourceViewModel(context.applicationContext as android.app.Application, "shader") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(500)
        viewModel.search(searchQuery)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 0.dp)
        ) {
            PageHeader(title = "光影包管理", compact = true)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("搜索光影包...", color = VerseColors.PcTextMuted, fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = VerseColors.PcTextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Outlined.Clear,
                                    contentDescription = null,
                                    tint = VerseColors.PcTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerseColors.PcAccent,
                        unfocusedBorderColor = VerseColors.PcBorder,
                        focusedContainerColor = VerseColors.PcBgTertiary,
                        unfocusedContainerColor = VerseColors.PcBgTertiary,
                        focusedTextColor = VerseColors.PcTextPrimary,
                        unfocusedTextColor = VerseColors.PcTextPrimary,
                        cursorColor = VerseColors.PcAccent
                    )
                )

                GlassCard(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {},
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = VerseColors.PcTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val sources = listOf("全部" to "", "Modrinth" to "modrinth", "CurseForge" to "curseforge")
                sources.forEach { (label, value) ->
                    val isSelected = uiState.selectedSource == value
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) VerseColors.PcOrange.copy(alpha = 0.14f) else VerseColors.PcBgCard,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, VerseColors.PcOrange.copy(alpha = 0.35f)) else null,
                        modifier = Modifier.clickable { viewModel.setSelectedSource(value) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) VerseColors.PcOrange else VerseColors.PcTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "共 ${uiState.totalHits} 个光影包",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = "已加载 ${results.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (uiState.error != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = VerseColors.PcRed,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.error ?: "未知错误",
                            style = MaterialTheme.typography.bodySmall,
                            color = VerseColors.PcTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { viewModel.clearError(); viewModel.search(searchQuery) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = VerseColors.PcAccent.copy(alpha = 0.12f),
                                contentColor = VerseColors.PcAccent
                            )
                        ) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重试", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(results, key = { it.projectId }) { hit ->
                        ShaderCard(hit, onClick = { navController.navigate("shader_detail/${hit.projectId}?source=${hit.source}") })
                    }

                    if (uiState.isLoading && results.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = VerseColors.PcAccent
                                )
                            }
                        }
                    }

                    // PC 式分页（对齐 .pagination：上一页/页码/下一页）
                    item {
                        PcPagination(
                            currentPage = uiState.currentPage,
                            totalHits = uiState.totalHits,
                            pageSize = 15,
                            onPrev = { viewModel.goToPage(uiState.currentPage - 1) },
                            onNext = { viewModel.goToPage(uiState.currentPage + 1) }
                        )
                    }
                }

                if (uiState.isLoading && results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                                color = VerseColors.PcAccent
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "正在加载光影包...",
                                style = MaterialTheme.typography.bodySmall,
                                color = VerseColors.PcTextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShaderCard(hit: ModrinthSearchHit, onClick: () -> Unit = {}) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VerseColors.PcBgTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    // 始终显示占位图标（加载失败时透出来）
                    Icon(
                        imageVector = Icons.Filled.Flare,
                        contentDescription = null,
                        tint = VerseColors.PcPurple,
                        modifier = Modifier.size(22.dp)
                    )
                    // 有图标 URL 时叠加 AsyncImage 在上层
                    if (!hit.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = hit.iconUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = VerseColors.PcTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "作者: ${hit.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcTextMuted,
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = hit.description,
                style = MaterialTheme.typography.bodySmall,
                color = VerseColors.PcTextSecondary,
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabelWithIcon(
                        icon = Icons.Outlined.Download,
                        text = formatDownloadCount(hit.downloads),
                        color = VerseColors.PcTextSecondary
                    )
                }

                FilledTonalButton(
                    onClick = {},
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VerseColors.PcOrange.copy(alpha = 0.12f),
                        contentColor = VerseColors.PcOrange
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
