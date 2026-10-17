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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.LabelWithIcon
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.PcCompactSearchField
import com.verse.explorer.app.ui.components.PcCompactFilterBar
import com.verse.explorer.app.ui.components.PcFilterSelect
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
                .padding(horizontal = 10.dp)
                .padding(top = 8.dp)
        ) {

            // 搜索与筛选合并为单行；点开筛选时选项列表内联展开把底部顶下去
            // 筛选栏上移贴顶，start 缩进避开左上角悬浮导航钮，给列表腾出更多展示空间
            PcCompactFilterBar(
                selects = listOf(
                    PcFilterSelect(uiState.selectedSource, listOf("" to "全部", "modrinth" to "Modrinth", "curseforge" to "CurseForge")) { viewModel.setSelectedSource(it) }
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 56.dp)
            ) {
                PcCompactSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索光影包...",
                    onSearch = { viewModel.search(searchQuery) },
                    modifier = Modifier.weight(1.2f)
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

            Spacer(modifier = Modifier.height(4.dp))

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
                    fontSize = 11.sp
                )
                Text(
                    text = "已加载 ${results.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
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

                if (uiState.isLoading && results.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RoseLoader(
                                modifier = Modifier.size(36.dp),
                                size = 36.dp,
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
    ResourceSearchCard(hit, onClick = onClick)
}
