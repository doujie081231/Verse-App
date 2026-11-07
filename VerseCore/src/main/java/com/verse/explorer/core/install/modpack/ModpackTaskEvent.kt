package com.verse.explorer.core.install.modpack

/** 阶段行状态 */
enum class ModpackTaskKind { RUNNING, DONE, FAILED }

/**
 *
 *                 供 UI 将事件归并到对应阶段行
 * @param name     当前任务名 / 正在下载的文件名
 * @param progress 当前任务进度 0..1
 * @param message  附加信息（任务 message，通常为空）
 * @param kind     任务状态
 * @param countDone 计数行已完成文件数（计数事件携带，-1 表示非计数事件）：
 *                  单阶段多文件时 UI 按 n/total 计数展示行进度，不再绑定单文件进度
 * @param countTotal 计数行总文件数（任务 properties["total"] 事件携带，-1 表示非计数事件）
 * @param rowDone  是否为阶段行整体完成（非单文件任务结束时置 true，
 *                 避免同阶段并行文件个别完成就把整行标成已完成）
 */
data class ModpackTaskEvent(
    val key: String,
    val label: String,
    val name: String = "",
    val progress: Float = 0f,
    val message: String = "",
    val kind: ModpackTaskKind,
    val countDone: Int = -1,
    val countTotal: Int = -1,
    val rowDone: Boolean = false
)

class ModpackInstallCancelledException : Exception("整合包安装已取消")
