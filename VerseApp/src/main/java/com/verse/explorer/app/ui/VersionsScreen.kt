package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.VersionsViewModel
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val settingsRepo = remember { com.verse.explorer.app.data.SettingsRepository(context) }
    val downloadSource = remember { settingsRepo.getDownloadSource() }
    val sourceLabel = com.verse.explorer.app.download.DownloadSourceManager.Source.entries.find { it.id == downloadSource }?.label ?: "BMCLAPI 镜像"

    val viewModel = remember { VersionsViewModel(context.applicationContext as android.app.Application) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val releaseVersions by viewModel.releaseVersions.collectAsStateWithLifecycle()
    val snapshotVersions by viewModel.snapshotVersions.collectAsStateWithLifecycle()
    val localVersions by viewModel.localVersions.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    val tabs = listOf("正式版", "快照", "已安装")

    val displayVersions = when (selectedTab) {
        0 -> releaseVersions
        1 -> snapshotVersions
        2 -> localVersions
        else -> emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.McBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "版本管理",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VerseColors.McTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.fetchRemoteVersions() },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = VerseColors.McCardBg,
                            contentColor = VerseColors.McGreen
                        )
                    ) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刷新", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "下载源: $sourceLabel",
                style = MaterialTheme.typography.labelSmall,
                color = VerseColors.McTextMuted,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            TabGroup(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (uiState.isLoading) "加载中..." else "共 ${displayVersions.size} 个版本",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McTextMuted,
                    fontSize = 11.sp
                )
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VerseColors.McRedstone.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Error, null, Modifier.size(16.dp), VerseColors.McRedstone)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.error!!,
                            color = VerseColors.McRedstone,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError(); viewModel.fetchRemoteVersions() }) {
                            Text("重试", fontSize = 11.sp, color = VerseColors.McGreen)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (uiState.isLoading && displayVersions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = VerseColors.McGreen,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("从 $sourceLabel 获取版本列表...", color = VerseColors.McTextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(displayVersions) { version ->
                        VersionItem(
                            version = version,
                            isInInstalledTab = selectedTab == 2,
                            onClick = {
                                if (selectedTab == 2) {
                                    navController.navigate("version_settings/${version.id}")
                                } else {
                                    navController.navigate("version_detail/${version.id}?type=${version.type.name.lowercase()}&url=${java.net.URLEncoder.encode(version.url, "UTF-8")}")
                                }
                            },
                            onDelete = { showDeleteConfirm = version.id }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = VerseColors.McOverlayContentBg,
            titleContentColor = VerseColors.McTextPrimary,
            textContentColor = VerseColors.McTextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = { Text("删除版本", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除 Minecraft ${showDeleteConfirm} 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVersion(showDeleteConfirm!!)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = VerseColors.McRedstone)
                ) {
                    Text("删除", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消", color = VerseColors.McTextSecondary)
                }
            }
        )
    }
}

@Composable
internal fun TabGroup(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.McCardBgLight,
        modifier = Modifier.fillMaxWidth().wrapContentHeight()
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) VerseColors.McGreen else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) Color.White else VerseColors.McTextSecondary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionItem(
    version: MinecraftVersion,
    isInInstalledTab: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val iconBg = when (version.type) {
        VersionType.SNAPSHOT -> Color(0xFFFF9800).copy(alpha = 0.15f)
        VersionType.OLD_ALPHA, VersionType.OLD_BETA -> Color(0xFF9E9E9E).copy(alpha = 0.15f)
        else -> if (isInInstalledTab) VerseColors.McGreen.copy(alpha = 0.12f) else Color(0xFF4CAF50).copy(alpha = 0.12f)
    }
    val iconAssetPath = when (version.type) {
        VersionType.SNAPSHOT -> "icons/version_icon_cobblestone.png"
        VersionType.OLD_ALPHA, VersionType.OLD_BETA -> "icons/version_icon_old.png"
        else -> "icons/version_icon_grass.png"
    }
    val iconBitmap = remember(iconAssetPath) {
        try {
            context.assets.open(iconAssetPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.McCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, VerseColors.McCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = version.id,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.McTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = buildVersionMetaText(version),
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McTextSecondary,
                    fontSize = 11.sp
                )
            }

            if (isInInstalledTab) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Delete, "删除", Modifier.size(16.dp), VerseColors.McTextMuted)
                    }
                }
            } else {
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), VerseColors.McTextDim)
            }
        }
    }
}

private fun buildVersionMetaText(version: MinecraftVersion): String {
    val parts = mutableListOf<String>()
    val typeLabel = when (version.type) {
        VersionType.SNAPSHOT -> "快照"
        VersionType.OLD_ALPHA -> "Old Alpha"
        VersionType.OLD_BETA -> "Old Beta"
        else -> "正式版"
    }
    parts.add(typeLabel)
    if (version.releaseTime.isNotEmpty()) {
        parts.add(formatDate(version.releaseTime))
    }
    return parts.joinToString(" · ")
}

private fun formatDate(dateStr: String): String {
    return if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
}
