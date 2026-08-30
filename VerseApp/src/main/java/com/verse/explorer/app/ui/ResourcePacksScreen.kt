package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.components.FilterRow
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.PcPagination
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.ResourceViewModel

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
    val totalPages = if (uiState.totalHits > 0) (uiState.totalHits + 14) / 15 else 1

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
            PageHeader(title = "材质包管理", compact = true)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("搜索材质包...", color = VerseColors.PcTextMuted, fontSize = 13.sp)
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
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.search("")
                            }) {
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
                        .clickable { viewModel.search(searchQuery) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = VerseColors.PcTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 对齐模组页：筛选去竖排 label，并排两行 FilterRow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterRow(versions, uiState.selectedVersion, onSelect = { viewModel.setSelectedVersion(it) }, modifier = Modifier.weight(1f))
                val resolutions = listOf("全部", "8x", "16x", "32x", "64x", "128x", "256x", "512x")
                var selectedResolution by remember { mutableStateOf("全部") }
                FilterRow(resolutions, selectedResolution, onSelect = { selectedResolution = it }, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                    color = VerseColors.PcTextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = "${uiState.currentPage + 1}/$totalPages",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                            tint = VerseColors.PcRed
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.error ?: "加载失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VerseColors.PcTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = { viewModel.search(searchQuery) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = VerseColors.PcAccent.copy(alpha = 0.12f),
                                contentColor = VerseColors.PcAccent
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
                            color = VerseColors.PcAccent,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "加载材质包中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VerseColors.PcTextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(results) { hit ->
                        ResourcePackCard(hit, onClick = { navController.navigate("resourcepack_detail/${hit.projectId}?source=${hit.source}") })
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
                                    color = VerseColors.PcAccent,
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
                                        color = VerseColors.PcRed,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { viewModel.goToPage(uiState.currentPage) }) {
                                        Text("重试", color = VerseColors.PcAccent, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    // 分页控件并入列表末尾，避免固定占据列表空间（对齐 PC .pagination：上一页/页码/下一页）
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
            }
        }
    }
}
