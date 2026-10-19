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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.PcCompactSearchField
import com.verse.explorer.app.ui.components.PcCompactFilterBar
import com.verse.explorer.app.ui.components.PcFilterSelect
import com.verse.explorer.app.ui.components.PcPagination
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.ResourceViewModel

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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                .padding(top = 8.dp)
        ) {

            // 搜索与筛选合并为单行；点开筛选时选项列表内联展开把底部顶下去
            // 筛选栏上移贴顶，start 缩进避开左上角悬浮导航钮，给列表腾出更多展示空间
            PcCompactFilterBar(
                selects = listOf(
                    PcFilterSelect(uiState.selectedLoader, loaders.map { it to it }) { viewModel.setSelectedLoader(it) },
                    PcFilterSelect(uiState.selectedVersion, versions.map { it to it }) { viewModel.setSelectedVersion(it) },
                    PcFilterSelect(uiState.selectedSource, listOf("" to "全部", "modrinth" to "Modrinth", "curseforge" to "CurseForge")) { viewModel.setSelectedSource(it) }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                PcCompactSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索整合包...",
                    onSearch = { viewModel.search(searchQuery) },
                    modifier = Modifier.weight(1.2f)
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
                    color = VerseColors.PcTextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = "已加载 ${results.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 11.sp
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
                            Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = VerseColors.PcTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.error ?: "加载失败",
                            color = VerseColors.PcTextSecondary,
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
                                containerColor = VerseColors.PcAccent.copy(alpha = 0.15f),
                                contentColor = VerseColors.PcAccent
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
                    RoseLoader(
                        modifier = Modifier.size(36.dp),
                        size = 36.dp,
                        color = VerseColors.PcAccent
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
                            tint = VerseColors.PcTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "没有找到整合包",
                            color = VerseColors.PcTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(results) { hit ->
                        ModpackCard(hit, onClick = { navController.navigate("modpack_detail/${hit.projectId}?source=${hit.source}") })
                    }
                    if (uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                RoseLoader(
                                    modifier = Modifier.size(24.dp),
                                    size = 24.dp,
                                    color = VerseColors.PcAccent
                                )
                            }
                        }
                    }
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
