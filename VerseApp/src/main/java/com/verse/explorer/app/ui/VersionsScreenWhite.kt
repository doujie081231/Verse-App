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
fun VersionsScreenWhite(navController: NavHostController) {
    val ctx = LocalContext.current
    val settingsRepo = remember { com.verse.explorer.app.data.SettingsRepository(ctx) }
    val downloadSource = settingsRepo.getDownloadSource()
    val sourceLabel = com.verse.explorer.app.download.DownloadSourceManager.Source.entries.find { it.id == downloadSource }?.label ?: "BMCLAPI 镜像"

    val viewModel = remember { VersionsViewModel(ctx.applicationContext as android.app.Application) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val releaseVersions by viewModel.releaseVersions.collectAsStateWithLifecycle()
    val snapshotVersions by viewModel.snapshotVersions.collectAsStateWithLifecycle()
    val localVersions by viewModel.localVersions.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    val tabs = listOf("正式版", "快照", "已安装")
    val displayVersions = when (selectedTab) { 0 -> releaseVersions; 1 -> snapshotVersions; 2 -> localVersions; else -> emptyList() }

    // White theme colors
    val bg = VerseColors.WhiteBg
    val cardBg = VerseColors.WhiteBgCard
    val textPri = VerseColors.WhiteTextPrimary
    val textSec = VerseColors.WhiteTextSecondary
    val textMut = VerseColors.WhiteTextMuted
    val accent = VerseColors.WhiteAccent
    val accentBg = VerseColors.WhiteAccentSoft
    val border = VerseColors.WhiteBorder
    val danger = VerseColors.WhiteDanger

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("版本管理", color = textPri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                FilledTonalButton(onClick = { viewModel.fetchRemoteVersions() }, shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = accentBg, contentColor = accent)) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("刷新", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("下载源: $sourceLabel", color = textMut, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))

            // Tabs
            Surface(Modifier.fillMaxWidth().wrapContentHeight(), RoundedCornerShape(8.dp), color = VerseColors.WhiteBgSecondary) {
                Row(Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    tabs.forEachIndexed { idx, title ->
                        val sel = idx == selectedTab
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (sel) accent else Color.Transparent).clickable { selectedTab = idx }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(title, fontSize = 12.sp, color = if (sel) Color.White else textSec, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(if (uiState.isLoading) "加载中..." else "共 ${displayVersions.size} 个版本", color = textMut, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))

            if (uiState.error != null) {
                Surface(shape = RoundedCornerShape(8.dp), color = VerseColors.WhiteDangerLight) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Error, null, Modifier.size(16.dp), danger)
                        Spacer(Modifier.width(8.dp))
                        Text(uiState.error!!, color = danger, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearError(); viewModel.fetchRemoteVersions() }) { Text("重试", fontSize = 11.sp, color = accent) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (uiState.isLoading && displayVersions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(32.dp), accent, strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("从 $sourceLabel 获取版本列表...", color = textMut, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
                    items(displayVersions) { version ->
                        val iconBg = when (version.type) { VersionType.SNAPSHOT -> Color(0xFFFF9800).copy(alpha = 0.12f); VersionType.OLD_ALPHA, VersionType.OLD_BETA -> Color(0xFF9E9E9E).copy(alpha = 0.12f); else -> accent.copy(alpha = 0.08f) }
                        Surface(Modifier.fillMaxWidth().clickable {
                            if (selectedTab == 2) navController.navigate("version_settings/${version.id}")
                            else navController.navigate("version_detail/${version.id}?type=${version.type.name.lowercase()}&url=${java.net.URLEncoder.encode(version.url, "UTF-8")}")
                        }, RoundedCornerShape(8.dp), color = cardBg, border = androidx.compose.foundation.BorderStroke(1.dp, border)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                                    Icon(when (version.type) { VersionType.RELEASE -> Icons.Filled.Diamond; VersionType.SNAPSHOT -> Icons.Filled.NewReleases; else -> Icons.Filled.History }, null, Modifier.size(22.dp),
                                        tint = when (version.type) { VersionType.RELEASE -> accent; VersionType.SNAPSHOT -> Color(0xFFFF9800); else -> Color(0xFF9E9E9E) })
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(version.id, color = textPri, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(buildVersionMetaTextW(version), color = textSec, fontSize = 11.sp)
                                }
                                if (selectedTab == 2) {
                                    IconButton(onClick = { showDeleteConfirm = version.id }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, "删除", Modifier.size(16.dp), textMut) }
                                } else {
                                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), VerseColors.WhiteTextDim)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = null }, containerColor = VerseColors.WhiteBgCard,
            title = { Text("删除版本", fontWeight = FontWeight.Bold, color = textPri) },
            text = { Text("确定要删除 Minecraft ${showDeleteConfirm} 吗？此操作不可撤销。", color = textSec) },
            confirmButton = { TextButton(onClick = { viewModel.deleteVersion(showDeleteConfirm!!); showDeleteConfirm = null }, colors = ButtonDefaults.textButtonColors(contentColor = danger)) { Text("删除", fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消", color = textSec) } })
    }
}

private fun buildVersionMetaTextW(version: MinecraftVersion): String {
    val typeLabel = when (version.type) { VersionType.SNAPSHOT -> "快照"; VersionType.OLD_ALPHA -> "Old Alpha"; VersionType.OLD_BETA -> "Old Beta"; else -> "正式版" }
    val date = if (version.releaseTime.length >= 10) version.releaseTime.substring(0, 10) else version.releaseTime
    return "$typeLabel · $date"
}
