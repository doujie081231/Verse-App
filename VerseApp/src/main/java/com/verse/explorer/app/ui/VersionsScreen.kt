package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
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
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.PcTabGroup
import com.verse.explorer.app.ui.components.navButtonAvoid
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
    val settingsRepo = com.verse.explorer.app.data.SettingsRepository
    val downloadSource = remember { settingsRepo.getDownloadSource() }
    val sourceLabel = com.verse.explorer.app.download.DownloadSourceManager.Source.entries.find { it.id == downloadSource }?.label ?: "BMCLAPI 镜像"

    val viewModel = remember { VersionsViewModel(context.applicationContext as android.app.Application) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val releaseVersions by viewModel.releaseVersions.collectAsStateWithLifecycle()
    val snapshotVersions by viewModel.snapshotVersions.collectAsStateWithLifecycle()
    val specialVersions by viewModel.specialVersions.collectAsStateWithLifecycle()
    val oldVersions by viewModel.oldVersions.collectAsStateWithLifecycle()
    val localVersions by viewModel.localVersions.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    val tabs = listOf("正式版", "快照版", "愚人节版", "远古版", "已安装")

    val displayVersions = when (selectedTab) {
        0 -> releaseVersions
        1 -> snapshotVersions
        2 -> specialVersions
        3 -> oldVersions
        4 -> localVersions
        else -> emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            // 紧凑单行控制条：下载源 + 数量 + 刷新，避免压缩版本列表可视高度
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .navButtonAvoid(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "源: $sourceLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isLoading) "加载中..." else "共 ${displayVersions.size} 个版本",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.fetchRemoteVersions(forceRefresh = true) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, "刷新", Modifier.size(16.dp), VerseColors.PcTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            PcTabGroup(
                tabs = tabs,
                selectedIndex = selectedTab,
                onSelected = { selectedTab = it },
                compact = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = VerseColors.PcRed.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Error, null, Modifier.size(16.dp), VerseColors.PcRed)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.error!!,
                            color = VerseColors.PcRed,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError(); viewModel.fetchRemoteVersions(forceRefresh = true) }) {
                            Text("重试", fontSize = 11.sp, color = VerseColors.PcAccent)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.isLoading && displayVersions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RoseLoader(
                            modifier = Modifier.size(32.dp),
                            size = 32.dp,
                            color = VerseColors.PcAccent
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("从 $sourceLabel 获取版本列表...", color = VerseColors.PcTextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(displayVersions) { version ->
                        VersionItem(
                            version = version,
                            isInInstalledTab = selectedTab == 4,
                            onClick = {
                                if (selectedTab == 4) {
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
            containerColor = VerseColors.PcBgCard,
            titleContentColor = VerseColors.PcTextPrimary,
            textContentColor = VerseColors.PcTextSecondary,
            shape = RoundedCornerShape(12.dp),
            title = { Text("删除版本", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除 Minecraft ${showDeleteConfirm} 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVersion(showDeleteConfirm!!)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = VerseColors.PcRed)
                ) {
                    Text("删除", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消", color = VerseColors.PcTextSecondary)
                }
            }
        )
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
        VersionType.SPECIAL -> VerseColors.PcOrange.copy(alpha = 0.15f)
        VersionType.SNAPSHOT -> VerseColors.PcOrange.copy(alpha = 0.15f)
        VersionType.OLD_ALPHA, VersionType.OLD_BETA -> VerseColors.PcPurple.copy(alpha = 0.15f)
        else -> VerseColors.PcGreen.copy(alpha = 0.15f)
    }
    val iconAssetPath = when (version.type) {
        VersionType.SPECIAL -> "icons/version_icon_cobblestone.png"
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

    val typeBadgeColor = when (version.type) {
        VersionType.SPECIAL -> VerseColors.PcOrange
        VersionType.SNAPSHOT -> VerseColors.PcOrange
        VersionType.OLD_ALPHA, VersionType.OLD_BETA -> VerseColors.PcPurple
        else -> VerseColors.PcGreen
    }
    val typeBadgeLabel = when (version.type) {
        VersionType.SPECIAL -> "愚人节"
        VersionType.SNAPSHOT -> "快照"
        VersionType.OLD_ALPHA -> "Old Alpha"
        VersionType.OLD_BETA -> "Old Beta"
        else -> "正式版"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = version.id,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = typeBadgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = typeBadgeLabel,
                            fontSize = 9.sp,
                            color = typeBadgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = versionMetaTail(version),
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            if (isInInstalledTab) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Delete, "删除", Modifier.size(16.dp), VerseColors.PcTextMuted)
                    }
                }
            } else {
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), VerseColors.PcTextMuted)
            }
        }
    }
}

private fun versionMetaTail(version: MinecraftVersion): String {
    return if (version.releaseTime.isNotEmpty()) formatDate(version.releaseTime) else ""
}

private fun formatDate(dateStr: String): String {
    return if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
}
