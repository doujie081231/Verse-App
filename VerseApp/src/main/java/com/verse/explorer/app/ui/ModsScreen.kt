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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.PcCompactSearchField
import com.verse.explorer.app.ui.components.PcCompactFilterBar
import com.verse.explorer.app.ui.components.PcFilterSelect
import com.verse.explorer.app.ui.components.PcPagination
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.ResourceViewModel
import com.verse.explorer.core.resource.ModrinthApi
import com.verse.explorer.core.resource.ModrinthProject
import com.verse.explorer.core.resource.ModrinthSearchHit
import com.verse.explorer.core.resource.ModrinthVersion
import kotlinx.coroutines.launch

@Composable
fun ModsScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val viewModel = remember { ResourceViewModel(context.applicationContext as android.app.Application, "mod") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    // 收藏筛选（对齐 PC mod-favorites 页）
    var favOnly by remember { mutableStateOf(false) }
    val favHits = remember(favOnly) {
        com.verse.explorer.app.data.FavoritesRepository.list(context).map { f ->
            ModrinthSearchHit(
                projectId = f.id,
                title = f.title,
                description = f.description,
                downloads = f.downloads,
                iconUrl = f.iconUrl,
                source = f.source,
                loaders = f.loaders,
                gameVersions = f.gameVersions,
                author = f.source
            )
        }
    }

    val loaders = listOf("全部", "Fabric", "Forge", "NeoForge", "Quilt")
    val gameVersions = listOf("全部", "1.21", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.16.5")

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                .padding(top = 54.dp)
        ) {

            // 搜索与筛选合并为单行；点开筛选时选项列表内联展开把底部顶下去
            PcCompactFilterBar(
                selects = listOf(
                    PcFilterSelect(uiState.selectedLoader, loaders.map { it to it }) { viewModel.setSelectedLoader(it) },
                    PcFilterSelect(uiState.selectedVersion, gameVersions.map { it to it }) { viewModel.setSelectedVersion(it) },
                    PcFilterSelect(uiState.selectedSource, listOf("" to "全部", "modrinth" to "Modrinth", "curseforge" to "CurseForge")) { viewModel.setSelectedSource(it) }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                PcCompactSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索模组...",
                    onSearch = { viewModel.search(searchQuery) },
                    modifier = Modifier.weight(1.2f)
                )
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
                    text = if (favOnly) "我的收藏 (${favHits.size})" else "共 ${uiState.totalHits} 个模组",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextMuted,
                    fontSize = 11.sp
                )
                // 收藏筛选（对齐 PC mod-favorites 页的入口）
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (favOnly) VerseColors.PcOrange.copy(alpha = 0.16f) else VerseColors.PcBgCard,
                    border = if (favOnly) androidx.compose.foundation.BorderStroke(1.dp, VerseColors.PcOrange.copy(alpha = 0.35f)) else null,
                    modifier = Modifier.clickable { favOnly = !favOnly }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Icon(Icons.Filled.Favorite, null, Modifier.size(11.dp), if (favOnly) VerseColors.PcOrange else VerseColors.PcTextMuted)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (favOnly) "查看全部" else "收藏",
                            fontSize = 10.sp,
                            color = if (favOnly) VerseColors.PcOrange else VerseColors.PcTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val display = if (favOnly) favHits else results
            when {
                uiState.isLoading && results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = VerseColors.PcAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                uiState.error != null && results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.CloudOff, null, Modifier.size(40.dp), VerseColors.PcTextMuted)
                            Text(uiState.error ?: "加载失败", fontSize = 13.sp, color = VerseColors.PcTextMuted)
                            OutlinedButton(
                                onClick = { viewModel.search(searchQuery) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VerseColors.PcAccent)
                            ) {
                                Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重试", fontSize = 13.sp)
                            }
                        }
                    }
                }
                display.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (favOnly) Icons.Filled.Favorite else Icons.Outlined.SearchOff,
                                null,
                                Modifier.size(40.dp),
                                VerseColors.PcTextMuted
                            )
                            Text(
                                if (favOnly) "暂无收藏模组，去详情页点击" else "未找到模组",
                                fontSize = 13.sp,
                                color = VerseColors.PcTextMuted
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(display, key = { it.projectId }) { hit ->
                            ModListItem(
                                hit = hit,
                                onClick = { navController.navigate("mod_detail/${hit.projectId}?source=${hit.source}") }
                            )
                        }
                        if (uiState.isLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        color = VerseColors.PcAccent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        // 收藏筛选无分页；普通列表按 PC .pagination 翻页
                        if (!favOnly) {
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
    }
    }

@Composable
private fun ModListItem(
    hit: ModrinthSearchHit,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcBgTertiary),
                contentAlignment = Alignment.Center
            ) {
                // 始终显示占位文字（加载失败时透出来）
                Text(
                    text = (hit.loaders.firstOrNull()?.first()?.uppercase() ?: "?"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                // 有图标 URL 时叠加 AsyncImage 在上层（加载失败时透出底层占位）
                if (!hit.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = hit.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VerseColors.PcTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (hit.loaders.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.PcTextMuted.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = hit.loaders.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.PcTextSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hit.author, fontSize = 11.sp, color = VerseColors.PcTextMuted)
                    Spacer(Modifier.width(8.dp))
                    Text("·", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                    Spacer(Modifier.width(8.dp))
                    if (hit.gameVersions.isNotEmpty()) {
                        Text(hit.gameVersions.first(), fontSize = 11.sp, color = VerseColors.PcTextMuted)
                        Spacer(Modifier.width(8.dp))
                        Text("·", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(formatDownloadCount(hit.downloads), fontSize = 11.sp, color = VerseColors.PcTextMuted)
                }
                if (hit.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        hit.description,
                        fontSize = 11.sp,
                        color = VerseColors.PcTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

internal fun formatDownloadCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
