package com.verse.explorer.cardgrid.model

import kotlin.math.roundToInt

/** 网格的最小列数 */
const val MIN_GRID_COLUMNS = 4

/** 单元格边长的设计目标值（dp） */
const val DEFAULT_TARGET_CELL_SIZE = 20f

/**
 * 网格几何信息。
 * [columns] 列数恒为偶数，保证卡片可严格对齐半宽等对称分割；
 * [cellSize] 为单个正方形单元格的边长（dp），
 * 网格首尾两端与容器边缘严格对齐。
 */
data class GridGeometry(
    val columns: Int,
    val cellSize: Float
)

/**
 * 依据容器宽度计算网格：
 * 以 [targetCellSize] 为目标细分出偶数列，宽度余数平摊进每个单元格，
 * 使单元格边长保持在目标值附近、网格边缘与容器边缘对齐。
 */
fun computeGridGeometry(
    widthDp: Float,
    targetCellSize: Float = DEFAULT_TARGET_CELL_SIZE
): GridGeometry {
    if (widthDp <= 0f) return GridGeometry(MIN_GRID_COLUMNS, targetCellSize)
    val evenColumns = (widthDp / targetCellSize / 2f)
        .roundToInt()
        .coerceAtLeast(MIN_GRID_COLUMNS / 2) * 2
    return GridGeometry(columns = evenColumns, cellSize = widthDp / evenColumns)
}