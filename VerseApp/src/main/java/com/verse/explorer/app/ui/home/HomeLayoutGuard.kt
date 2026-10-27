package com.verse.explorer.app.ui.home

/**
 * 主页固定功能卡片的布局校验（纯逻辑，便于单测）。
 *
 * 设计布局里「头像 → 当前版本 → 启动」是纵向三块，行区间必然互不重叠；
 * 一旦快照把三块排到同一排（真机反馈 1.2.3：主页所有 UI 排成一排，重启依旧），
 * 说明它来自首帧异常测量的坏布局，必须还原而不能继续持久化。
 */
object HomeLayoutGuard {

    /** 固定功能卡片的卡片 id（与 VerseHomeGrid 中的 id 一致） */
    val SYSTEM_CARD_IDS = listOf("system_account", "system_current_version", "system_launch")

    /** 卡片占用的行区间 */
    data class RowBand(val top: Int, val bottom: Int) {
        fun overlaps(other: RowBand): Boolean = top < other.bottom && other.top < bottom
    }

    /**
     * 三块固定卡片是否被排到互相重叠的行上。
     * @return false 表示卡片不全（尚未播种，不做判断）
     */
    fun systemRowsOverlap(bands: List<RowBand>): Boolean {
        if (bands.size < 2) return false
        for (i in bands.indices) {
            for (j in i + 1 until bands.size) {
                if (bands[i].overlaps(bands[j])) return true
            }
        }
        return false
    }
}
