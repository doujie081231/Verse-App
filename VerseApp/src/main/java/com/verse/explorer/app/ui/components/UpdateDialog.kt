package com.verse.explorer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.verse.explorer.app.data.UpdateDownloader
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 检测到新版本时的提示弹窗（参考 FCL UpdateDialog）：
 * 展示版本号、发布日期与更新内容；「立即更新」在启动器内下载 APK
 * （Gitee 主源 + GitHub 备源，进度/取消），完成后经 FileProvider 调起系统安装器；
 * 失败回退网盘/浏览器链接。
 */
@Composable
fun UpdateDialog(
    versionName: String,
    releaseDate: String,
    items: List<String>,
    part1Urls: List<String>,
    onIgnore: () -> Unit,
    onGoUpdate: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 下载状态：null = 未在下载；非 null = 进行中（downloaded/total/speed）
    var progress by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf(false) }

    fun installApk() {
        val apk = UpdateDownloader.apkFile(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun startDownload() {
        errorText = null
        progress = Triple(0L, -1L, 0L)
        scope.launch {
            try {
                UpdateDownloader.download(context, part1Urls) { d, t, s ->
                    progress = Triple(d, t, s)
                }
                progress = null
                installApk()
            } catch (e: CancellationException) {
                // 用户取消或协程取消：回到按钮态，不打扰
                progress = null
            } catch (e: Exception) {
                progress = null
                errorText = e.message ?: "下载失败"
            }
        }
    }

    Dialog(onDismissRequest = {
        // 下载中不允许点外部关闭（避免误触后台仍占网速）
        if (progress == null && !finishing) onIgnore()
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(VerseColors.PcBgActive)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(VerseColors.PcAccent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "检测到版本更新 $versionName",
                        color = VerseColors.PcTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (releaseDate.isNotBlank()) {
                        Text(
                            text = "发布日期：$releaseDate",
                            color = VerseColors.PcTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (items.isEmpty()) {
                    Text(
                        text = "本次更新包含若干问题修复与体验优化。",
                        color = VerseColors.PcTextSecondary,
                        fontSize = 13.sp
                    )
                } else {
                    items.forEachIndexed { index, item ->
                        Row {
                            Text(
                                text = "${index + 1}.",
                                color = VerseColors.PcAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item,
                                color = VerseColors.PcTextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                // 下载进度区（下载中显示）
                progress?.let { (downloaded, total, speed) ->
                    Spacer(modifier = Modifier.height(4.dp))
                    val ratio = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(VerseColors.PcBgTertiary)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(VerseColors.PcAccent)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("已下载 ${formatBytes(downloaded)}")
                            if (total > 0) append(" / ${formatBytes(total)}")
                            append(" · ${formatBytes(speed)}/s")
                        },
                        color = VerseColors.PcTextMuted,
                        fontSize = 11.sp
                    )
                }

                errorText?.let { err ->
                    Text(
                        text = "下载失败：$err",
                        color = Color(0xFFE57373),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val downloading = progress != null
            if (downloading) {
                // 下载中：只提供「取消」
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(VerseColors.PcBgTertiary)
                        .padding(vertical = 12.dp)
                        .clickable {
                            UpdateDownloader.cancel()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "取消下载",
                        color = VerseColors.PcTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VerseColors.PcBgTertiary)
                            .padding(vertical = 12.dp)
                            .clickable { onIgnore() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (errorText != null) "关闭" else "忽略",
                            color = VerseColors.PcTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (errorText != null) {
                        // 失败兜底：浏览器/网盘下载（与 FCL 一致）
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VerseColors.PcBgTertiary)
                                .padding(vertical = 12.dp)
                                .clickable { onGoUpdate() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "网盘下载",
                                color = VerseColors.PcTextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VerseColors.PcAccent)
                            .padding(vertical = 12.dp)
                            .clickable {
                                if (part1Urls.isNotEmpty()) {
                                    startDownload()
                                } else {
                                    // 无有效下载源：直接走网盘兜底
                                    onGoUpdate()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (errorText != null) "重试" else "立即更新",
                            color = if (isAccentLight()) Color.Black else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "未知"
    if (bytes < 1024) return "${bytes}B"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) String.format(Locale.US, "%.2fGB", mb / 1024)
    else String.format(Locale.US, "%.1fMB", mb)
}

private fun isAccentLight(): Boolean {
    val c = VerseColors.PcAccent
    return (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue) > 0.65
}
