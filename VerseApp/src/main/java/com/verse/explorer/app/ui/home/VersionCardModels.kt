package com.verse.explorer.app.ui.home

import com.verse.explorer.core.version.MinecraftVersion

/**
 * 主页版本卡片的记录与状态：
 * 记录持久化保存卡片对应的版本，状态在版本列表加载后重算。
 */

/** 单张版本卡片的持久化记录 */
data class VersionCardRecord(
    val cardId: String,
    val versionId: String
)

/** 卡片指向版本的可用性状态 */
sealed interface VersionCardStatus {
    /** 尚未完成首次核对 */
    data object Loading : VersionCardStatus

    /** 版本可用 */
    data class Available(val version: MinecraftVersion) : VersionCardStatus

    /** 版本已被删除或文件损坏 */
    data object Deleted : VersionCardStatus
}

/** 版本卡片的完整状态 */
data class VersionCardState(
    val record: VersionCardRecord,
    val status: VersionCardStatus
)
