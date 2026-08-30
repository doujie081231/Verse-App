package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.VersionsViewModel
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionType

/**
 * 已安装版本管理列表（对齐 versepc2 `page-installed-versions` + `renderInstalledVersionsInto`）。
 *
 * 从主页点击版本卡片进入。手机端为单一游戏文件夹，因此：
 *  - 移除「版本文件夹选择器」（folder-selector）
 *  - 移除「添加已有文件夹」（add-external-folder）
 *
 * 保留 PC 端列表交互：
 *  - 模组版本 / 基础版本 分组
 *  - 版本项：图标 + 名称 + 加载器 badge + meta(类型 · 日期)
 *  - 右侧：设置齿轮（进版本设置） + 删除按钮
 *  - 选中态：✓ 角标 + accent 边框（对应 PC `.version-item.selected`）
 */
@Composable
fun InstalledVersionsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel = remember { VersionsViewModel(context.applicationContext as android.app.Application) }
    val localVersions by viewModel.localVersions.collectAsStateWithLifecycle()

    var currentLaunchVersionId by remember { mutableStateOf(settingsLastVersion() ?: "") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    // PC 端 split：模组版本优先显示，基础版本(原版)分组在下方
    val moddedVersions = localVersions.filter { isModdedVersion(it.id) }
    val vanillaVersions = localVersions.filterNot { isModdedVersion(it.id) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 对齐 PC .page-header：返回箭头 + 标题 + 刷新
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = VerseColors.PcTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "已安装版本",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.loadLocalVersions() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "刷新",
                        tint = VerseColors.PcTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (localVersions.isEmpty()) {
                // PC 空态：'该文件夹下暂无版本'
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无已安装版本，请前往「下载」页安装一个吧",
                        color = VerseColors.PcTextMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    if (moddedVersions.isNotEmpty()) {
                        items(moddedVersions) { version ->
                            InstalledVersionItem(
                                version = version,
                                selected = version.id == currentLaunchVersionId,
                                onSelect = {
                                    SettingsRepository.setCustomSetting("last_version", version.id)
                                    currentLaunchVersionId = version.id
                                    navController.navigateUp()
                                },
                                onSettings = {
                                    navController.navigate("version_settings/${version.id}")
                                },
                                onDelete = { showDeleteConfirm = version.id }
                            )
                        }
                    }

                    if (vanillaVersions.isNotEmpty()) {
                        item {
                            // PC：基础版本分组头（⚠ 原版分组）
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                                ) {
                                    Text("\u26A0 ", fontSize = 12.sp, color = VerseColors.PcTextMuted)
                                    Text(
                                        text = "基础版本 (${vanillaVersions.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VerseColors.PcTextMuted
                                    )
                                }
                            }
                        }
                        items(vanillaVersions) { version ->
                            InstalledVersionItem(
                                version = version,
                                selected = version.id == currentLaunchVersionId,
                                onSelect = {
                                    SettingsRepository.setCustomSetting("last_version", version.id)
                                    currentLaunchVersionId = version.id
                                    navController.navigateUp()
                                },
                                onSettings = {
                                    navController.navigate("version_settings/${version.id}")
                                },
                                onDelete = { showDeleteConfirm = version.id }
                            )
                        }
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

/**
 * 单个已安装版本项（对齐 PC `.version-item`）：
 *  图标(52dp, 透明底) + 名称 + badge + meta + 右侧「齿轮 + 删除」
 *  选中版本：accent 边框 + 左上角 ✓ 角标
 */
@Composable
private fun InstalledVersionItem(
    version: MinecraftVersion,
    selected: Boolean,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
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

    val (badge, badgeColor) = loaderBadge(version.id)

    Box {
        if (selected) {
            // ✓ 角标（对齐 PC .version-item.selected::before）
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp))
                    .background(VerseColors.PcAccent),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcAccentText)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelect
                ),
            shape = RoundedCornerShape(8.dp),
            color = if (selected) VerseColors.PcAccent.copy(alpha = 0.08f) else VerseColors.PcGlassCardBg,
            border = BorderStroke(
                1.dp,
                if (selected) VerseColors.PcAccent else VerseColors.PcBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标（52dp）
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))

                // 名称 + badge + meta
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = version.id,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = VerseColors.PcTextPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(3.dp), color = badgeColor.copy(alpha = 0.15f)) {
                            Text(
                                text = badge,
                                fontSize = 10.sp,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${versionTypeLabel(version)} · ${formatDate(version.releaseTime)}",
                        fontSize = 12.sp,
                        color = VerseColors.PcTextMuted
                    )
                }

                // 齿轮（版本设置）
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(VerseColors.PcBgTertiary)
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "版本设置",
                        tint = VerseColors.PcTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                // 删除
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = VerseColors.PcTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** 加载器 badge（对齐 PC renderInstalledVersionsInto badge 判定 + HomeScreen LoaderBadge） */
private fun loaderBadge(versionId: String): Pair<String, Color> {
    val lower = versionId.lowercase()
    return when {
        lower.contains("modpack") || lower.contains("整合") -> "整合包" to VerseColors.PcGreen
        lower.contains("fabric") -> "Fabric" to VerseColors.ForgeColor
        lower.contains("neoforge") -> "NeoForge" to VerseColors.PcOrange
        lower.contains("forge") -> "Forge" to VerseColors.PcOrange
        lower.contains("optifine") -> "OptiFine" to VerseColors.PcYellow
        else -> "原版" to VerseColors.PcTextSecondary
    }
}

/** 是否为模组版本（PC `.isFabric/isForge/isNeoForge/isOptiFine/isLiteLoader/isModpack` 的 id 近似判定） */
private fun isModdedVersion(versionId: String): Boolean {
    val lower = versionId.lowercase()
    return lower.contains("fabric") || lower.contains("forge") || lower.contains("neoforge") ||
        lower.contains("optifine") || lower.contains("liteloader") || lower.contains("modpack") || lower.contains("整合")
}

private fun versionTypeLabel(version: MinecraftVersion): String {
    return when (version.type) {
        VersionType.SNAPSHOT -> "快照"
        VersionType.OLD_ALPHA -> "Old Alpha"
        VersionType.OLD_BETA -> "Old Beta"
        else -> "正式版"
    }
}

private fun formatDate(dateStr: String): String {
    return if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
}

private fun settingsLastVersion(): String? = SettingsRepository.getCustomSetting("last_version")