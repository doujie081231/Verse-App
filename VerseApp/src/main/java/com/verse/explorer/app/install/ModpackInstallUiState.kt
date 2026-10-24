package com.verse.explorer.app.install

import com.verse.explorer.core.install.modpack.ModpackTaskEvent
import com.verse.explorer.core.install.modpack.ModpackTaskKind

// 状态模型（UI 层归并任务事件 → 固定阶段行）

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
