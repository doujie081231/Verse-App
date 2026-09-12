package com.verse.explorer.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.install.modpack.ModpackTaskEvent
import com.verse.explorer.core.install.modpack.ModpackTaskKind
import kotlin.math.sin
import kotlin.math.PI

// ===== 状态模型（UI 层归并 FCL 任务事件 → 阶段行） =====

enum class ModpackInstallPhase { DOWNLOAD_FILE, INSTALLING, DONE, FAILED, CANCELLED }

enum class ModpackRowStatus { RUNNING, DONE, FAILED }

data class ModpackStageRow(
    val key: String,
    val label: String,
    val status: ModpackRowStatus = ModpackRowStatus.RUNNING,
    val progress: Float = 0f,
    val detail: String = "",
    val error: String? = null,
    // 归并统计：该阶段行出现的任务 start 次数 / done 次数，二者相等视为该行完成
    val started: Int = 0,
    val done: Int = 0
)

data class ModpackInstallUiState(
    val visible: Boolean = false,
    val title: String = "",
    val phase: ModpackInstallPhase = ModpackInstallPhase.DOWNLOAD_FILE,
    val fileProgress: Float = 0f,
    val fileText: String = "",
    val rows: List<ModpackStageRow> = emptyList(),
    val error: String? = null
) {
    /** 总进度（整合包文件下载 0~0.15，FCL 任务阶段 0.15~1.0） */
    val overall: Float
        get() = when (phase) {
            ModpackInstallPhase.DOWNLOAD_FILE -> fileProgress * 0.15f
            ModpackInstallPhase.INSTALLING -> {
                val total = rows.size.coerceAtLeast(1)
                val doneW = rows.count { it.status == ModpackRowStatus.DONE }
                val actW = rows.sumOf {
                    if (it.status == ModpackRowStatus.RUNNING) it.progress.toDouble() else 0.0
                }.toFloat()
                0.15f + 0.85f * ((doneW + actW) / total).coerceIn(0f, 1f)
            }
            ModpackInstallPhase.DONE -> 1f
            else -> 0f
        }
}

/** 将单个 FCL 任务事件归并进弹窗状态（必须在主线程调用） */
fun ModpackInstallUiState.applyTaskEvent(ev: ModpackTaskEvent): ModpackInstallUiState {
    if (!visible || phase != ModpackInstallPhase.INSTALLING) return this

    val idx = rows.indexOfFirst { it.key == ev.key }
    val row = if (idx >= 0) rows[idx]
    else ModpackStageRow(
        key = ev.key,
        label = ev.label.ifBlank { ev.key },
        started = 0,
        done = 0
    )

    val started = if (ev.kind == ModpackTaskKind.RUNNING) row.started + 1 else row.started
    val done = if (ev.kind == ModpackTaskKind.DONE) row.done + 1 else row.done
    val failed = ev.kind == ModpackTaskKind.FAILED

    val status = when {
        failed -> ModpackRowStatus.FAILED
        started > 0 && done >= started -> ModpackRowStatus.DONE
        else -> ModpackRowStatus.RUNNING
    }
    val newRow = row.copy(
        label = ev.label.ifBlank { row.label },
        status = status,
        progress = if (ev.kind == ModpackTaskKind.RUNNING) ev.progress else row.progress,
        detail = if (ev.kind == ModpackTaskKind.RUNNING && ev.name.isNotBlank()) ev.name else row.detail,
        error = if (failed) ev.message.ifBlank { "任务失败" } else row.error,
        started = started,
        done = done
    )

    val newRows = if (idx >= 0) rows.toMutableList().apply { set(idx, newRow) }
    else rows + newRow
    return copy(rows = newRows)
}

// ===== 弹窗 UI（对齐版本下载的 InstallFlowCard 视觉：阶段行 + 总进度） =====

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
        color = VerseColors.PcBgCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 标题
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
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    userScrollEnabled = state.rows.size > 6,
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    items(state.rows) { row ->
                        ModpackStageRowView(
                            label = row.label,
                            status = row.status,
                            progress = row.progress,
                            detail = if (row.status == ModpackRowStatus.FAILED) row.error ?: row.detail else row.detail,
                            error = row.status == ModpackRowStatus.FAILED,
                            lastRow = row == state.rows.last()
                        )
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

            // 总进度条
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
        ModpackRowStatus.RUNNING -> StageOrange
        ModpackRowStatus.DONE -> StageGreen
        ModpackRowStatus.FAILED -> FailRed
    }
    val done = status == ModpackRowStatus.DONE
    val active = status == ModpackRowStatus.RUNNING

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

/** 3×3 像素方块呼吸（对齐 InstallFlowCard 的活动态图标） */
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

/** 波浪进度条（对齐 InstallFlowCard：无灰底，进度填充 + 顶部正弦波浪） */
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
        val filled = progress.coerceIn(0f, 1f) * w
        if (filled > 0f) {
            val points = 40
            val waveH = h * 0.9f
            for (i in 0 until points) {
                val x = i * filled / points
                val y = h / 2 + sin((x / 6f) + t * 0.12f) * waveH * 0.35f + sin((x / 3f) + t * 0.2f) * waveH * 0.18f
                val thickness = 1.4.dp.toPx()
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(x, y + thickness * 2),
                    strokeWidth = thickness,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}