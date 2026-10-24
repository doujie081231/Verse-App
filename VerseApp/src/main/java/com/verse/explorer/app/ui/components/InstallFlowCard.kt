package com.verse.explorer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.InstallState
import com.verse.explorer.core.install.InstallStage
import kotlin.math.sin

/** 阶段色：橙色 #f59e0b ↔ 绿色 #22c55e 循环 */
private val StageOrange = Color(0xFFF59E0B)
private val StageGreen = Color(0xFF22C55E)
/** 完成勾 / 完成文字 */
private val CheckGreen = Color(0xFF22C55E)
private val DoneTextGreen = Color(0xFF11AA66)
/** 连接点灰 */
private val ConnectorGray = Color(0xFF9CA3AF)
private const val DOTS = 5

private data class StageUi(val stage: InstallStage, val label: String, val color: Color)

private fun stageUiList(): List<StageUi> = listOf(
    StageUi(InstallStage.PREPARING, "准备安装", StageOrange),
    StageUi(InstallStage.DOWNLOADING_VERSION, "下载版本信息", StageGreen),
    StageUi(InstallStage.DOWNLOADING_CLIENT, "下载游戏客户端", StageOrange),
    StageUi(InstallStage.DOWNLOADING_LIBRARIES, "下载依赖库", StageGreen),
    StageUi(InstallStage.DOWNLOADING_ASSETS, "下载资源文件", StageOrange),
    StageUi(InstallStage.EXTRACTING_NATIVES, "解压原生库", StageGreen)
)

/** 各阶段总进度区间（与 GameInstaller progressCallback 对齐，用于阶段内局部进度条） */
private fun stageRange(stage: InstallStage): Pair<Float, Float> = when (stage) {
    InstallStage.PREPARING -> 0f to 0.02f
    InstallStage.DOWNLOADING_VERSION -> 0.02f to 0.05f
    InstallStage.DOWNLOADING_CLIENT -> 0.05f to 0.2f
    InstallStage.DOWNLOADING_LIBRARIES -> 0.2f to 0.68f
    InstallStage.DOWNLOADING_ASSETS -> 0.68f to 0.9f
    InstallStage.EXTRACTING_NATIVES -> 0.9f to 1f
    else -> 0f to 1f
}

/**
 * 安装流程卡片（弹窗内使用）。
 * 内容逐步出现：3×3 像素方块呼吸 → 等宽文字淡入 → 图标下展开 5 个灰色连接点；
 * 下一阶段出现后，当前阶段 3×3 缩小淡出、交叉淡入绿色完成勾，文字延迟变绿。
 * 活动阶段的文字下方有小波浪进度条，卡片底部是总安装进度条。
 */
@Composable
fun InstallFlowCard(
    state: InstallState,
    onCancel: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val stages = remember { stageUiList() }

    val activeIndex = remember(state.stage) {
        when (state.stage) {
            InstallStage.COMPLETED -> stages.size - 1
            InstallStage.FAILED -> stages.indexOfFirst { it.stage == state.stage }
                .takeIf { it >= 0 } ?: 0
            else -> stages.indexOfFirst { it.stage == state.stage }.coerceAtLeast(0)
        }
    }
    val allDone = state.isCompleted
    val failed = state.isFailed

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
            // 标题
            Text(
                if (failed) "安装失败" else if (allDone) "安装完成" else "正在安装",
                fontSize = 13.sp,
                color = if (failed) VerseColors.PcRed else VerseColors.PcTextMuted,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))

            // 逐步出现的阶段行：内容超高时在区域内滚动，保证底部取消按钮不被顶出
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                for (i in 0..activeIndex) {
                    val s = stages[i]
                    val done = allDone || i < activeIndex
                    val active = !failed && i == activeIndex && !allDone
                    StageRow(
                        label = s.label,
                        color = s.color,
                        done = done,
                        active = active,
                        localProgress = if (active) localStageProgress(state.stage, state.progress) else 0f,
                        subText = if (active && state.currentFile.isNotEmpty()) state.currentFile else "",
                        showDots = active && i < stages.size - 1,
                        speed = state.speed,
                        downloaded = state.downloaded,
                        totalBytes = state.totalBytes
                    )
                    if (i < activeIndex && i < stages.size - 1) {
                        Spacer(Modifier.height(6.dp))
                    }
                }

                if (failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error ?: state.message,
                        fontSize = 12.sp,
                        color = VerseColors.PcRed,
                        maxLines = 2
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 总安装进度条
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("总进度", fontSize = 11.sp, color = VerseColors.PcTextMuted, modifier = Modifier.padding(end = 8.dp))
                WaveProgressBar(
                    progress = state.progress,
                    color = VerseColors.PcAccent,
                    modifier = Modifier.weight(1f).height(8.dp)
                )
                Text(
                    " ${(state.progress * 100).toInt()}%",
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
                if (failed && onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Text("重试下载", color = VerseColors.PcAccent, fontSize = 13.sp)
                    }
                }
                if (!allDone) {
                    TextButton(onClick = onCancel) {
                        Text(if (failed) "关闭" else "取消下载", color = VerseColors.PcRed, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun localStageProgress(stage: InstallStage, total: Float): Float {
    val (lo, hi) = stageRange(stage)
    if (hi <= lo) return total.coerceIn(0f, 1f)
    return ((total - lo) / (hi - lo)).coerceIn(0f, 1f)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** 单个阶段行：3×3 图标（加载/完成勾）+ 等宽文字 + 活动期小波浪进度条 */
@Composable
private fun StageRow(
    label: String,
    color: Color,
    done: Boolean,
    active: Boolean,
    localProgress: Float,
    subText: String,
    showDots: Boolean,
    speed: Long = 0L,
    downloaded: Long = 0L,
    totalBytes: Long = 0L
) {
    val textColor by animateColorAsState(
        targetValue = when { done -> DoneTextGreen; active -> VerseColors.PcTextPrimary; else -> VerseColors.PcTextSecondary },
        animationSpec = tween(durationMillis = if (done) 600 else 350, delayMillis = if (done) 500 else 0),
        label = "stageTextColor"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (active || done) 1f else 0f,
        animationSpec = tween(400),
        label = "stageTextAlpha"
    )

    // 完成过渡：3×3 缩小淡出 + 同位置交叉淡入绿色完成勾
    val cubeAlpha = remember { Animatable(1f) }
    val checkAlpha = remember { Animatable(0f) }
    LaunchedEffect(done) {
        if (done) {
            checkAlpha.animateTo(0f, tween(1))
            cubeAlpha.animateTo(0f, tween(340, easing = EaseInOut))
            checkAlpha.animateTo(1f, tween(300, delayMillis = 160, easing = EaseInOut))
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 阶段图标（24dp 区域）：完成=勾交叉淡入，活动=3×3 呼吸
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (cubeAlpha.value > 0.01f) {
                    PixelCubeLoading(
                        color = color,
                        modifier = Modifier.graphicsLayer {
                            alpha = cubeAlpha.value
                            scaleX = 0.55f + 0.45f * cubeAlpha.value
                            scaleY = 0.55f + 0.45f * cubeAlpha.value
                        }
                    )
                }
                if (checkAlpha.value > 0.01f) {
                    DoneIcon(alpha = checkAlpha.value)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    modifier = Modifier.alpha(textAlpha)
                )
                if (active) {
                    Spacer(Modifier.height(6.dp))
                    WaveProgressBar(
                        progress = localProgress,
                        color = color,
                        modifier = Modifier.fillMaxWidth().height(5.dp)
                    )
                    if (subText.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subText,
                            fontSize = 10.sp,
                            color = VerseColors.PcTextMuted,
                            maxLines = 1
                        )
                    }
                    // 下载速度 + 已下载量
                    if (downloaded > 0 || speed > 0) {
                        Spacer(Modifier.height(3.dp))
                        val sizePart = "已下载 ${formatBytes(downloaded)}" +
                            if (totalBytes > 0) " / ${formatBytes(totalBytes)}" else ""
                        val speedPart = if (speed > 0) " · ${formatBytes(speed)}/s" else ""
                        Text(
                            sizePart + speedPart,
                            fontSize = 10.sp,
                            color = VerseColors.PcTextMuted,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 图标正下方展开的灰色连接点（仅活动阶段，下一阶段出现即消失停止）
        if (showDots) {
            Spacer(Modifier.height(6.dp))
            ConnectorDots(modifier = Modifier.padding(start = 14.dp))
        }
    }
}

/** 3×3 像素方块：9 个小方块相位错开的呼吸/流动，活动态带轻微光晕 */
@Composable
private fun PixelCubeLoading(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cubeBreath")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = androidx.compose.animation.core.LinearEasing),
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
                        // 每格相位错开 42°，形成不规则自然流动
                        val angle = (p + idx * 42f) * (kotlin.math.PI.toFloat() / 180f)
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

/** 绿色完成勾（无光晕） */
@Composable
private fun DoneIcon(alpha: Float) {
    Canvas(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        val stroke = 2.2.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.55f)
            lineTo(size.width * 0.40f, size.height * 0.80f)
            lineTo(size.width * 0.90f, size.height * 0.16f)
        }
        drawPath(
            path = path,
            color = CheckGreen,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/** 灰色竖向虚线连接（5 段，逐段展开 + 轻微向下流动；该行不再活动时随组件移除而停止） */
@Composable
private fun ConnectorDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dotsFlow")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "dotsPhase"
    )
    val p = phase.value
    val drift = (sin((p * 2f) * (kotlin.math.PI.toFloat() / 180f)) + 1f) / 2f * 1.5f

    Canvas(
        modifier = modifier.size(width = 6.dp, height = 46.dp)
    ) {
        val dotR = 1.5.dp.toPx()
        val spacing = (size.height - dotR * 2) / (DOTS)
        repeat(DOTS) { i ->
            val a = 0.18f + 0.82f * ((sin((p + i * 30f) * (kotlin.math.PI.toFloat() / 180f)) + 1f) / 2f)
            val y = dotR + i * spacing + drift * ((i % 3) + 1) * 0.6f
            drawCircle(
                color = ConnectorGray.copy(alpha = a),
                radius = dotR,
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, y)
            )
        }
    }
}

/** 波浪进度条：无灰底，仅进度填充 + 顶部正弦波浪 */
@Composable
private fun WaveProgressBar(
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
    Canvas(modifier = modifier.clip(RoundedCornerShape(999.dp))) {
        val w = size.width
        val h = size.height
        // 进度填充（无灰底，仅波浪虚线本身）
        val filled = (progress.coerceIn(0f, 1f)) * w
        // 波浪线（两个频率叠加，随时间右移）
        val t = phase.value
        if (filled > 0f) {
            val points = 40
            val waveH = h * 0.9f
            for (i in 0 until points) {
                val x = i * filled / points
                // 主波 + 副波
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