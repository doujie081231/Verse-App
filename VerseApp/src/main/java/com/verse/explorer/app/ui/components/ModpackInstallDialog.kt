package com.verse.explorer.app.ui.components

import com.verse.explorer.app.install.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.theme.VerseColors
import com.tungsten.fclcore.task.FetchTask
import kotlin.math.sin
import kotlin.math.PI

// 弹窗 UI（阶段行 + 固定权重总进度 + 实时速度）

private val StageOrange = Color(0xFFF59E0B)
private val StageGreen = Color(0xFF22C55E)
private val CheckGreen = Color(0xFF22C55E)
private val FailRed = Color(0xFFEF4444)
private val ConnectorGray = Color(0xFF9CA3AF)


@Composable
fun ModpackInstallDialog(
    state: ModpackInstallUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val failed = state.phase == ModpackInstallPhase.FAILED
    val done = state.phase == ModpackInstallPhase.DONE
    val cancelled = state.phase == ModpackInstallPhase.CANCELLED
    val running = state.phase == ModpackInstallPhase.DOWNLOAD_FILE || state.phase == ModpackInstallPhase.INSTALLING

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = VerseColors.PcBgCard,
        modifier = Modifier.heightIn(max = 480.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 标题 + 整合包图标 + 实时速度（下载任务每秒上报，B/s → KB/s → MB/s）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VerseColors.PcBgActive),
                    contentAlignment = Alignment.Center
                ) {
                    if (!state.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.iconUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            cancelled -> "已取消"
                            failed -> "安装失败"
                            done -> "安装完成"
                            state.phase == ModpackInstallPhase.DOWNLOAD_FILE -> "正在下载整合包"
                            else -> "正在安装整合包"
                        },
                        fontSize = 13.sp,
                        color = when {
                            failed || cancelled -> VerseColors.PcRed
                            done -> VerseColors.PcGreen
                            else -> VerseColors.PcTextMuted
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.title.isNotBlank() && running) {
                        Text(
                            state.title,
                            fontSize = 12.sp,
                            color = VerseColors.PcTextSecondary,
                            maxLines = 1
                        )
                    }
                }
                if (running) {
                    // 速度来源按阶段区分：整合包文件下载走下载中心条目上报（SmartDownloadClient），
                    // 安装阶段的 libraries/assets 走任务引擎的每秒速度事件
                    val speed = if (state.phase == ModpackInstallPhase.DOWNLOAD_FILE) {
                        state.downloadSpeed
                    } else {
                        rememberFetchSpeed()
                    }
                    if (speed > 0) {
                        Text(
                            formatSpeedText(speed),
                            fontSize = 11.sp,
                            color = VerseColors.PcAccent,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            if (state.phase == ModpackInstallPhase.DOWNLOAD_FILE) {
                // 整合包文件下载（下载弹窗第一阶段）
                ModpackStageRowView(
                    label = "下载整合包文件",
                    status = ModpackRowStatus.RUNNING,
                    progress = state.fileProgress,
                    detail = state.fileText,
                    lastRow = true
                )
            } else if (running || done || failed) {
                // 固定阶段行：内容超高时区域内滚动，保证底部总进度与操作按钮不被顶出
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    state.rows.forEachIndexed { index, row ->
                        val detailText = when {
                            row.status == ModpackRowStatus.FAILED -> row.error ?: row.detail
                            row.countTotal > 0 && row.status != ModpackRowStatus.PENDING ->
                                "${row.countDone} / ${row.countTotal} 个文件"
                            else -> row.detail
                        }
                        ModpackStageRowView(
                            label = row.label,
                            status = row.status,
                            progress = row.progress,
                            detail = detailText,
                            error = row.status == ModpackRowStatus.FAILED,
                            lastRow = index == state.rows.lastIndex
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            if (failed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.error ?: "整合包安装失败",
                    fontSize = 12.sp,
                    color = VerseColors.PcRed,
                    maxLines = 3
                )
            }

            Spacer(Modifier.height(16.dp))

            // 总进度条（固定权重加权，不再随阶段行增减跳动）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("总进度", fontSize = 11.sp, color = VerseColors.PcTextMuted, modifier = Modifier.padding(end = 8.dp))
                ModpackWaveBar(
                    progress = state.overall,
                    color = VerseColors.PcAccent,
                    modifier = Modifier.weight(1f).height(8.dp)
                )
                Text(
                    " ${(state.overall * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = VerseColors.PcAccent,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // 操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (failed) {
                    TextButton(onClick = onRetry) {
                        Text("重试安装", color = VerseColors.PcAccent, fontSize = 13.sp)
                    }
                    TextButton(onClick = onClose) {
                        Text("关闭", color = VerseColors.PcTextSecondary, fontSize = 13.sp)
                    }
                } else if (running) {
                    TextButton(onClick = onCancel) {
                        Text("取消下载", color = VerseColors.PcRed, fontSize = 13.sp)
                    }
                } else {
                    TextButton(onClick = onClose) {
                        Text("关闭", color = VerseColors.PcTextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * 订阅任务引擎的全局速度事件（每秒一次，聚合当前全部下载任务的字节速率），
 * 弹窗组合期注册、离开组合注销。仅安装阶段的任务（FetchTask 体系）会发出事件。
 */
@Composable
private fun rememberFetchSpeed(): Long {
    val speed = remember { mutableLongStateOf(0L) }
    DisposableEffect(Unit) {
        val handler = java.util.function.Consumer<FetchTask.SpeedEvent> { event ->
            speed.longValue = event.speed.toLong()
        }
        FetchTask.speedEvent
            .channel(FetchTask.SpeedEvent::class.java)
            .registerWeak(handler)
        onDispose {
            FetchTask.speedEvent
                .channel(FetchTask.SpeedEvent::class.java)
                .unregister(handler)
        }
    }
    return speed.longValue
}

/** "xx.x KB/s" 格式（B/s → KB/s → MB/s） */
private fun formatSpeedText(speed: Long): String {
    var value = speed.toFloat()
    var unit = "B/s"
    if (value > 1024) {
        value /= 1024
        unit = "KB/s"
    }
    if (value > 1024) {
        value /= 1024
        unit = "MB/s"
    }
    return String.format("%.1f %s", value, unit)
}

@Composable
private fun ModpackStageRowView(
    label: String,
    status: ModpackRowStatus,
    progress: Float,
    detail: String = "",
    error: Boolean = false,
    lastRow: Boolean = true
) {
    val color = when (status) {
        ModpackRowStatus.PENDING -> ConnectorGray
        ModpackRowStatus.RUNNING -> StageOrange
        ModpackRowStatus.DONE -> StageGreen
        ModpackRowStatus.FAILED -> FailRed
    }
    val done = status == ModpackRowStatus.DONE
    val active = status == ModpackRowStatus.RUNNING
    val pending = status == ModpackRowStatus.PENDING

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (active) {
                    MiniPixelCube(color = color, modifier = Modifier.graphicsLayer {
                        scaleX = 0.75f
                        scaleY = 0.75f
                    })
                } else if (done) {
                    MiniCheck()
                } else if (pending) {
                    // 未开始：灰色空心圆点
                    Canvas(
                        modifier = Modifier
                            .size(12.dp)
                            .alpha(0.6f)
                    ) {
                        drawCircle(
                            color = ConnectorGray,
                            radius = size.minDimension / 2,
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }
                } else {
                    MiniCross()
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        done -> Color(0xFF11AA66)
                        error -> FailRed
                        pending -> VerseColors.PcTextMuted
                        else -> VerseColors.PcTextPrimary
                    },
                    maxLines = 1
                )
                if (active) {
                    Spacer(Modifier.height(6.dp))
                    ModpackWaveBar(
                        progress = progress,
                        color = color,
                        modifier = Modifier.fillMaxWidth().height(5.dp)
                    )
                }
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        detail,
                        fontSize = 10.sp,
                        color = if (error) FailRed else VerseColors.PcTextMuted,
                        maxLines = 1
                    )
                }
            }
        }
        if (!lastRow) {
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 3×3 像素方块呼吸（活动阶段行图标） */
@Composable
private fun MiniPixelCube(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cubeBreath")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "cubePhase"
    )
    val p = phase.value
    Box(
        modifier = modifier
            .size(16.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(2.dp),
                ambientColor = color.copy(alpha = 0.3f),
                spotColor = color.copy(alpha = 0.3f),
                clip = false
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    repeat(3) { col ->
                        val idx = row * 3 + col
                        val angle = (p + idx * 42f) * (PI.toFloat() / 180f)
                        val alpha = 0.32f + 0.68f * ((sin(angle) + 1f) / 2f)
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(RoundedCornerShape(0.6.dp))
                                .background(color.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }
}

/** 完成勾 / 失败叉 */
@Composable
private fun MiniCheck() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = 2.2.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.55f)
            lineTo(size.width * 0.40f, size.height * 0.80f)
            lineTo(size.width * 0.90f, size.height * 0.16f)
        }
        drawPath(path = path, color = CheckGreen, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun MiniCross() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = 2.2.dp.toPx()
        val s = size.width
        val gap = size.width * 0.25f
        drawLine(FailRed, androidx.compose.ui.geometry.Offset(gap, gap), androidx.compose.ui.geometry.Offset(s - gap, s - gap), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(FailRed, androidx.compose.ui.geometry.Offset(s - gap, gap), androidx.compose.ui.geometry.Offset(gap, s - gap), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** 进度条：底轨 + 实心填充 + 填充区顶部正弦波浪（小进度也可辨识） */
@Composable
private fun ModpackWaveBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val phase = rememberInfiniteTransition(label = "wavePhase").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "wave"
    )
    val t = phase.value
    Canvas(modifier = modifier.clip(RoundedCornerShape(999.dp))) {
        val w = size.width
        val h = size.height
        val r = h / 2
        val corner = androidx.compose.ui.geometry.CornerRadius(r)
        // 底轨
        drawRoundRect(color = color.copy(alpha = 0.14f), cornerRadius = corner)
        val filled = progress.coerceIn(0f, 1f) * w
        if (filled > 0f) {
            // 实心填充（半透明底层 + 实色主体）
            drawRoundRect(
                color = color.copy(alpha = 0.35f),
                size = androidx.compose.ui.geometry.Size(filled, h),
                cornerRadius = corner
            )
            drawRoundRect(
                color = color,
                size = androidx.compose.ui.geometry.Size(filled, h * 0.55f),
                cornerRadius = corner
            )
            // 填充区内的流动波浪高光
            val points = 40
            val waveH = h * 0.9f
            for (i in 0 until points) {
                val x = i * filled / points
                val y = h / 2 + sin((x / 6f) + t * 0.12f) * waveH * 0.35f + sin((x / 3f) + t * 0.2f) * waveH * 0.18f
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(x, y + 1.2.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
