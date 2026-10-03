package com.verse.explorer.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 资源安装的版本选择弹窗
 *
 * 与 ZL2 的 DownloadDialog 对应：以复选框卡片列出已安装的游戏版本
 * （多选，默认选中当前版本），确认后资源安装到所选版本的对应文件夹。
 */
@Composable
fun VersionPickerDialog(
    versions: List<VerseVersionItem>,
    defaultSelectedId: String?,
    onDismiss: () -> Unit,
    onConfirm: (List<VerseVersionItem>) -> Unit
) {
    val selected = remember(versions) {
        mutableStateListOf<String>().apply {
            versions.firstOrNull { it.id == defaultSelectedId }?.let { add(it.id) }
        }
    }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VerseColors.PcBgCard,
        titleContentColor = VerseColors.PcTextPrimary,
        textContentColor = VerseColors.PcTextSecondary,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text("为哪些游戏版本安装？", fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(versions, key = { it.id }) { version ->
                    val checked = version.id in selected
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) selected.remove(version.id) else selected.add(version.id)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (checked) {
                            VerseColors.PcAccent.copy(alpha = 0.10f)
                        } else {
                            VerseColors.PcBgTertiary
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selected.add(version.id) else selected.remove(version.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = VerseColors.PcAccent,
                                    checkmarkColor = VerseColors.PcAccentText
                                )
                            )
                            // 各版本对应图标（与版本列表一致：正式版草方块 / 快照与特殊圆石 / 旧版）
                            if (version.iconAsset.isNotBlank() || version.iconBg != null) {
                                val iconBitmap = remember(version.iconAsset) {
                                    try {
                                        if (version.iconAsset.isBlank()) {
                                            null
                                        } else {
                                            context.assets.open(version.iconAsset).use { stream ->
                                                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                                            }
                                        }
                                    } catch (e: Exception) { null }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(version.iconBg ?: VerseColors.PcBgTertiary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (iconBitmap != null) {
                                        Image(
                                            bitmap = iconBitmap,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    version.id,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VerseColors.PcTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (version.typeLabel.isNotBlank()) {
                                    Text(
                                        version.typeLabel,
                                        fontSize = 11.sp,
                                        color = VerseColors.PcTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "安装",
                color = if (selected.isEmpty()) VerseColors.PcTextMuted else VerseColors.PcAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(enabled = selected.isNotEmpty()) {
                        onConfirm(versions.filter { it.id in selected })
                    }
            )
        },
        dismissButton = {
            Text(
                "取消",
                color = VerseColors.PcTextSecondary,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    )
}

/** 版本选择条目 */
data class VerseVersionItem(
    val id: String,
    val typeLabel: String = "",
    /** 版本图标资源路径（assets 内），如 icons/version_icon_grass.png */
    val iconAsset: String = "",
    /** 图标底色（按版本类型着色，null 用默认底色） */
    val iconBg: Color? = null
)

/** 无已安装版本时的警告（与 ZL2 的 download_assets_no_installed_versions 对应） */
@Composable
fun NoInstalledVersionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VerseColors.PcBgCard,
        titleContentColor = VerseColors.PcTextPrimary,
        textContentColor = VerseColors.PcTextSecondary,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text("警告", fontWeight = FontWeight.Bold)
        },
        text = {
            Text("当前未安装可用的游戏版本，无法决定该资源的目标下载位置，请先安装游戏版本哦！")
        },
        confirmButton = {
            Text(
                "我知道了",
                color = VerseColors.PcAccent,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    )
}
