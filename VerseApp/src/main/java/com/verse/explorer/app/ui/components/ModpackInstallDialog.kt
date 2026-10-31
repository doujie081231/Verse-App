package com.verse.explorer.app.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.verse.explorer.core.install.modpack.ModpackTaskEvent
import com.verse.explorer.core.install.modpack.ModpackTaskKind
import com.tungsten.fclcore.task.FetchTask
import kotlin.math.sin
import kotlin.math.PI

// ===== 状态模型（UI 层归并任务事件 → 固定阶段行） =====

enum class ModpackInstallPhase { DOWNLOAD_FILE, INSTALLING, DONE, FAILED, CANCELLED }

enum class ModpackRowStatus { PENDING, RUNNING, DONE, FAILED }

/** 安装期固定阶段行 key：阶段集合在安装开始前固定，分母不再随事件动态增长 */
const val MODPACK_ROW_GAME = "game"
const val MODPACK_ROW_ASSETS = "assets"
const val MODPACK_ROW_LOADER = "loader"
const val MODPACK_ROW_UNPACK = "modpack"
const val MODPACK_ROW_MODS = "modpack_dl"

/**
 * 阶段行：n/total 计数（单阶段多文件）或任务进度（单任务阶段）二者其一驱动进度。
 */
data class ModpackStageRow(
    val key: String,
    val label: String,
    val status: ModpackRowStatus = ModpackRowStatus.PENDING,
    val progress: Float = 0f,
    val detail: String = "",
    val error: String? = null,
    /** 本阶段已完成文件数（计数事件驱动） */
    val countDone: Int = 0,
    /** 本阶段总文件数（阶段任务 properties 声明） */
    val countTotal: Int = 0
)

data class ModpackInstallUiState(
    val visible: Boolean = false,
    val title: String = "",
    /** 整合包作者提供的图标 URL（详情页 project.iconUrl），下载/安装时展示 */
    val iconUrl: String? = null,
    val phase: ModpackInstallPhase = ModpackInstallPhase.DOWNLOAD_FILE,
    val fileProgress: Float = 0f,
    val fileText: String = "",
    /** 整合包文件下载阶段的实时速度（字节/秒，来自下载中心条目上报） */
    val downloadSpeed: Long = 0,
    val rows: List<ModpackStageRow> = emptyList(),
    val error: String? = null
) {
    /**
     * 总进度：固定权重加权（阶段未开始记 0，完成记满）——
     * 下载整合包文件 0.10 / MC 本体 0.25 / 资源文件 0.10 / 加载器 0.10 /
     * 解压整合包 0.10 / 整合包模组 0.35。
     */
    val overall: Float
        get() = when (phase) {
            ModpackInstallPhase.DOWNLOAD_FILE -> fileProgress * 0.10f
            ModpackInstallPhase.INSTALLING -> {
                val byKey = rows.associateBy { it.key }
                0.10f + 0.25f * byKey[MODPACK_ROW_GAME].rowValue() +
                    0.10f * byKey[MODPACK_ROW_ASSETS].rowValue() +
                    0.10f * byKey[MODPACK_ROW_LOADER].rowValue() +
                    0.10f * byKey[MODPACK_ROW_UNPACK].rowValue() +
                    0.35f * byKey[MODPACK_ROW_MODS].rowValue()
            }
            ModpackInstallPhase.DONE -> 1f
            else -> 0f
        }

    private fun ModpackStageRow?.rowValue(): Float = when {
        this == null -> 0f
        status == ModpackRowStatus.DONE -> 1f
        status == ModpackRowStatus.PENDING -> 0f
        countTotal > 0 -> (countDone.toFloat() / countTotal).coerceIn(0f, 1f)
        else -> progress.coerceIn(0f, 1f)
    }
}

/** 安装期固定阶段表：进入安装阶段时构建一次，行集合不再变化 */
fun ModpackInstallUiState.withFixedStageRows(): ModpackInstallUiState = copy(
    phase = ModpackInstallPhase.INSTALLING,
    rows = listOf(
        ModpackStageRow(MODPACK_ROW_GAME, "安装 Minecraft 本体"),
        ModpackStageRow(MODPACK_ROW_ASSETS, "下载资源文件"),
        ModpackStageRow(MODPACK_ROW_LOADER, "安装加载器"),
        ModpackStageRow(MODPACK_ROW_UNPACK, "解压整合包文件"),
        ModpackStageRow(MODPACK_ROW_MODS, "下载整合包模组")
    )
)

/** 事件 key → 固定阶段行 key（task: 开头的单文件事件不占行，进度由计数事件表达） */
private fun bucketOf(eventKey: String): String? {
    if (!eventKey.startsWith("stage:")) return null
    val stage = eventKey.removePrefix("stage:")
    return when {
        stage.equals("fcl.modpack", ignoreCase = true) -> MODPACK_ROW_UNPACK
        stage.equals("fcl.modpack.download", ignoreCase = true) -> MODPACK_ROW_MODS
        stage.startsWith("fcl.install.game:", ignoreCase = true) -> MODPACK_ROW_GAME
        stage.equals("fcl.install.assets", ignoreCase = true) -> MODPACK_ROW_ASSETS
        stage.startsWith("fcl.install.", ignoreCase = true) -> MODPACK_ROW_LOADER
        else -> null
    }
}

/** 将单个任务事件归并进弹窗状态（必须在主线程调用；行集合固定，未知 key 丢弃） */
fun ModpackInstallUiState.applyTaskEvent(ev: ModpackTaskEvent): ModpackInstallUiState {
    if (!visible || phase != ModpackInstallPhase.INSTALLING) return this
    val rowKey = bucketOf(ev.key) ?: return this

    val newRows = rows.map { row ->
        if (row.key != rowKey) return@map row
        var r = row
        if (ev.label.isNotBlank()) r = r.copy(label = ev.label)
        when {
            // 本阶段文件总数声明（n/total 的 total）。宿主任务的准备/查询期进度可能已把
            // 行进度虚推到 1，计数接管后必须按计数重算，否则总进度先冲 100% 再回跳
            ev.countTotal >= 0 ->
                r = r.copy(
                    countTotal = ev.countTotal,
                    status = if (r.status == ModpackRowStatus.PENDING) ModpackRowStatus.RUNNING else r.status,
                    progress = if (ev.countTotal > 0) (r.countDone.toFloat() / ev.countTotal).coerceIn(0f, 1f) else r.progress
                )
            // 单文件完成计数（n/total 的 n）
            ev.countDone >= 0 -> {
                r = r.copy(
                    countDone = maxOf(r.countDone, ev.countDone),
                    status = if (r.status == ModpackRowStatus.PENDING) ModpackRowStatus.RUNNING else r.status
                )
                if (r.countTotal > 0) {
                    r = r.copy(progress = (r.countDone.toFloat() / r.countTotal).coerceIn(0f, 1f))
                }
            }
            ev.kind == ModpackTaskKind.DONE && ev.rowDone ->
                // 阶段宿主任务结束：整行完成（单文件完成不算）
                r = r.copy(
                    status = ModpackRowStatus.DONE,
                    progress = 1f,
                    countDone = maxOf(r.countDone, r.countTotal)
                )
            ev.kind == ModpackTaskKind.FAILED ->
                r = r.copy(status = ModpackRowStatus.FAILED, error = ev.message.ifBlank { "任务失败" })
            ev.kind == ModpackTaskKind.RUNNING -> {
                if (r.status == ModpackRowStatus.PENDING) r = r.copy(status = ModpackRowStatus.RUNNING)
                // 单任务阶段：取单调最大进度；多文件阶段的进度由计数驱动，不绑定单文件
                if (r.countTotal <= 0 && ev.progress > r.progress) r = r.copy(progress = ev.progress)
                // 任务名仅取可读名称，内部类名（无业务含义）不落到行详情
                if (ev.name.isNotBlank() && !ev.name.startsWith("stage:") && !ev.name.contains('.')) {
                    r = r.copy(detail = ev.name)
                    // 「解析下载地址」是「下载整合包模组」的前半段：CurseForge 包要逐个模组
                    // 请求接口拿文件名/直链，这期间没有任何文件在下载。不单独标出来，
                    // 用户只能看到这一行一直转圈，误以为卡死在下载。
                    if (ev.name.startsWith("解析模组下载地址")) {
                        r = r.copy(label = "解析模组下载地址")
                    } else if (ev.name.startsWith("下载整合包模组")) {
                        r = r.copy(label = "下载整合包模组")
                    }
                }
            }
        }
        // 计数收齐 → 行完成（覆盖计数事件先于宿主结束事件到达的情况）
        if (r.status == ModpackRowStatus.RUNNING && r.countTotal > 0 && r.countDone >= r.countTotal) {
            r = r.copy(status = ModpackRowStatus.DONE, progress = 1f)
        }
        r
    }
    return copy(rows = newRows)
}

// ===== 弹窗 UI（阶段行 + 固定权重总进度 + 实时速度） =====

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
    onClose: () -> Unit,
    /** 右上角「最小化下载」：弹窗收起、任务转入下载小窗（仅下载/安装进行中显示） */
    onMinimize: (() -> Unit)? = null
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
                // 右上角「最小化下载」：任务不中断，转入下载小窗
                if (running && onMinimize != null) {
                    IconButton(onClick = onMinimize, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "最小化下载",
                            modifier = Modifier.size(20.dp),
                            tint = VerseColors.PcTextSecondary
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
                VerseProgressBar(
                    progress = state.overall,
                    modifier = Modifier.weight(1f),
                    height = 8.dp,
                    color = VerseColors.PcAccent,
                    trackColor = VerseColors.PcBgActive
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
                    VerseProgressBar(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        height = 5.dp,
                        color = color,
                        trackColor = color.copy(alpha = 0.14f)
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

