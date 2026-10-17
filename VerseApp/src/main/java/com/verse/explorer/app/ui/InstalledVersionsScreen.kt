package com.verse.explorer.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Home
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
import com.verse.explorer.app.ui.components.ModpackImportFlow
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.VersionsViewModel
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionType
import java.io.File

/**
 *
 * 从主页点击版本卡片进入。手机端为单一游戏文件夹，因此：
 *  - 移除「版本文件夹选择器」（folder-selector）
 *  - 移除「添加已有文件夹」（add-external-folder）
 *
 *  - 模组版本 / 基础版本 分组
 *  - 版本项：图标 + 名称 + 加载器 badge + meta(类型 · 日期)
 *  - 右侧：设置齿轮（进版本设置） + 删除按钮
 */
@Composable
fun InstalledVersionsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel = remember { VersionsViewModel(context.applicationContext as android.app.Application) }
    val localVersions by viewModel.localVersions.collectAsStateWithLifecycle()

    var currentLaunchVersionId by remember { mutableStateOf(settingsLastVersion() ?: "") }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    // 本地导入整合包：SAF 打开内部存储挑选 .zip/.mrpack 文件
    var importUri by remember { mutableStateOf<Uri?>(null) }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importUri = uri
    }

    val moddedVersions = localVersions.filter { isModdedVersion(it) }
    val vanillaVersions = localVersions.filterNot { isModdedVersion(it) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    onClick = {
                        importPicker.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.FileOpen,
                        contentDescription = "导入整合包",
                        tint = VerseColors.PcTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                                pinnedToHome = com.verse.explorer.app.ui.home.VersionCardManager.hasCard(version.id),
                                onSelect = {
                                    SettingsRepository.setCustomSetting("last_version", version.id)
                                    currentLaunchVersionId = version.id
                                    navController.navigateUp()
                                },
                                onSettings = {
                                    navController.navigate("version_settings/${version.id}")
                                },
                                onToggleHome = { pinned ->
                                    if (pinned) {
                                        com.verse.explorer.app.ui.home.VersionCardManager.addCard(context, version.id)
                                    } else {
                                        com.verse.explorer.app.ui.home.VersionCardManager.removeByVersionId(context, version.id)
                                    }
                                },
                                onDelete = { showDeleteConfirm = version.id }
                            )
                        }
                    }

                    if (vanillaVersions.isNotEmpty()) {
                        item {
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
                                pinnedToHome = com.verse.explorer.app.ui.home.VersionCardManager.hasCard(version.id),
                                onSelect = {
                                    SettingsRepository.setCustomSetting("last_version", version.id)
                                    currentLaunchVersionId = version.id
                                    navController.navigateUp()
                                },
                                onSettings = {
                                    navController.navigate("version_settings/${version.id}")
                                },
                                onToggleHome = { pinned ->
                                    if (pinned) {
                                        com.verse.explorer.app.ui.home.VersionCardManager.addCard(context, version.id)
                                    } else {
                                        com.verse.explorer.app.ui.home.VersionCardManager.removeByVersionId(context, version.id)
                                    }
                                },
                                onDelete = { showDeleteConfirm = version.id }
                            )
                        }
                    }
                }
            }
        }
    }

    ModpackImportFlow(
        importSource = importUri,
        onHandled = { importUri = null },
        onImported = { viewModel.loadLocalVersions() }
    )

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
 *  图标(52dp, 透明底) + 名称 + badge + meta + 右侧「齿轮 + 删除」
 *  选中版本：accent 边框 + 左上角 ✓ 角标
 */
@Composable
private fun InstalledVersionItem(
    version: MinecraftVersion,
    selected: Boolean,
    pinnedToHome: Boolean,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onToggleHome: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    // 整合包作者图标优先（安装整合包时写入 versions/<id>/modpack_icon.png）
    val packIconBitmap = remember(version.id) {
        try {
            val packIcon = File(
                GameConfig.getVersionsDir(GameConfig.getGameDir(context)),
                version.id
            ).resolve("modpack_icon.png")
            if (packIcon.exists() && packIcon.length() > 0) {
                android.graphics.BitmapFactory.decodeFile(packIcon.absolutePath)?.asImageBitmap()
            } else null
        } catch (e: Exception) { null }
    }
    // 加载器同时按版本 json 内容识别（loaderHint），覆盖整合包等名称不含加载器关键词的版本。
    val lower = version.id.lowercase()
    val hint = version.loaderHint?.lowercase()
    val loaderIconPath = hint?.let { h ->
        when (h) {
            "fabric" -> "icons/loader_icon_fabric.png"
            "quilt" -> "icons/loader_icon_quilt.png"
            "neoforge" -> "icons/loader_icon_neoforge.png"
            "forge" -> "icons/loader_icon_forge.png"
            "optifine" -> "icons/loader_icon_optifine.png"
            else -> null
        }
    } ?: when {
        lower.contains("fabric") -> "icons/loader_icon_fabric.png"
        lower.contains("quilt") -> "icons/loader_icon_quilt.png"
        lower.contains("neoforge") -> "icons/loader_icon_neoforge.png"
        lower.contains("forge") -> "icons/loader_icon_forge.png"
        lower.contains("optifine") -> "icons/loader_icon_optifine.png"
        else -> null
    }
    val iconAssetPath = loaderIconPath ?: when (version.type) {
        VersionType.SNAPSHOT -> "icons/version_icon_cobblestone.png"
        VersionType.OLD_ALPHA, VersionType.OLD_BETA -> "icons/version_icon_old.png"
        else -> "icons/version_icon_grass.png"
    }
    val assetBitmap = remember(iconAssetPath) {
        try {
            context.assets.open(iconAssetPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }
    val iconBitmap = packIconBitmap ?: assetBitmap

    val (badge, badgeColor) = loaderBadge(version)

    Box {
        if (selected) {
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

                // 主页固定（把版本作为主页卡片显示/移除）
                IconButton(
                    onClick = { onToggleHome(!pinnedToHome) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (pinnedToHome) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = if (pinnedToHome) "从主页移除" else "固定到主页",
                        tint = if (pinnedToHome) VerseColors.PcAccent else VerseColors.PcTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
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

/** 加载器 badge：优先取版本 json 识别的加载器（loaderHint），再退化为名称关键词判定。 */
private fun loaderBadge(version: MinecraftVersion): Pair<String, Color> {
    val hint = version.loaderHint?.lowercase()
    if (!hint.isNullOrBlank()) {
        return when (hint) {
            "fabric" -> "Fabric" to VerseColors.ForgeColor
            "quilt" -> "Quilt" to VerseColors.PcAccent
            "neoforge" -> "NeoForge" to VerseColors.PcOrange
            "forge" -> "Forge" to VerseColors.PcOrange
            "optifine" -> "OptiFine" to VerseColors.PcYellow
            else -> "原版" to VerseColors.PcTextSecondary
        }
    }
    val lower = version.id.lowercase()
    return when {
        lower.contains("modpack") || lower.contains("整合") -> "整合包" to VerseColors.PcGreen
        lower.contains("fabric") -> "Fabric" to VerseColors.ForgeColor
        lower.contains("neoforge") -> "NeoForge" to VerseColors.PcOrange
        lower.contains("forge") -> "Forge" to VerseColors.PcOrange
        lower.contains("optifine") -> "OptiFine" to VerseColors.PcYellow
        else -> "原版" to VerseColors.PcTextSecondary
    }
}

/** 是否为模组版本：版本 json 识别出加载器，或 id 命中加载器/整合包关键词 */
private fun isModdedVersion(version: MinecraftVersion): Boolean {
    if (!version.loaderHint.isNullOrBlank()) return true
    val lower = version.id.lowercase()
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