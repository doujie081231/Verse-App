package com.verse.explorer.app.ui.game

/**
 * 快捷栏触控层的纯几何计算（不依赖 Android，便于单测）：
 * 快捷栏固定 9 格、水平居中、贴屏幕底边；
 * 尺寸来自「物品栏缩放（宽度 / 高度）」百分比，0 表示不启用该触控层。
 */
object HotbarGeometry {

    /** 快捷栏槽位数（MC 固定 9 格） */
    const val SLOTS = 9

    /** 只设一项百分比时，另一项按快捷栏 9:2 比例推导 */
    private const val HEIGHT_RATIO_OF_WIDTH = 2f / SLOTS

    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        fun isEmpty(): Boolean = width <= 0f || height <= 0f
        fun contains(x: Float, y: Float): Boolean =
            x >= left && x <= right && y >= top && y <= bottom
    }

    /**
     * 计算快捷栏矩形。
     * @param widthPercent 宽度占屏宽百分比（0 表示未设置）
     * @param heightPercent 高度占屏高百分比（0 表示未设置）
     * @return null 表示该触控层未启用（两项都是 0）或屏幕尺寸无效
     */
    fun rect(
        screenWidth: Int,
        screenHeight: Int,
        widthPercent: Int,
        heightPercent: Int
    ): Rect? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val wPercent = widthPercent.coerceIn(0, 100)
        val hPercent = heightPercent.coerceIn(0, 100)
        if (wPercent == 0 && hPercent == 0) return null
        var w = if (wPercent > 0) screenWidth * wPercent / 100f else -1f
        var h = if (hPercent > 0) screenHeight * hPercent / 100f else -1f
        if (w > 0f && h <= 0f) h = w * HEIGHT_RATIO_OF_WIDTH
        if (h > 0f && w <= 0f) w = h / HEIGHT_RATIO_OF_WIDTH
        if (w <= 0f || h <= 0f) return null
        // 不超出屏幕：超高时按底边贴齐、超宽时水平居中
        if (w > screenWidth) w = screenWidth.toFloat()
        if (h > screenHeight) h = screenHeight.toFloat()
        val left = (screenWidth - w) / 2f
        return Rect(left, screenHeight - h, left + w, screenHeight.toFloat())
    }

    /** 命中槽位 0..8；点在矩形外返回 -1 */
    fun slotAt(rect: Rect, x: Float, y: Float): Int {
        if (rect.isEmpty() || !rect.contains(x, y)) return -1
        val slotWidth = rect.width / SLOTS
        if (slotWidth <= 0f) return -1
        return ((x - rect.left) / slotWidth).toInt().coerceIn(0, SLOTS - 1)
    }

    /** 槽位对应的 GLFW 数字键码偏移（1 号槽 → 0，9 号槽 → 8） */
    fun keyOffset(slot: Int): Int = slot.coerceIn(0, SLOTS - 1)

    // ===== 自动尺寸（对齐 ZL2 的 HotbarRule.Auto） =====

    /** 单格逻辑像素：MC 快捷栏每格 20×20 GUI 像素 */
    private const val SLOT_LOGICAL_PX = 20

    /** MC 自动 GUI 缩放的下限基准（320×240 逻辑分辨率） */
    private const val AUTO_BASE_WIDTH = 320
    private const val AUTO_BASE_HEIGHT = 240

    /**
     * 实际生效的 GUI 缩放（对齐 ZL2 `getMCGuiScale`）：
     * - options.txt 里 `guiScale:0`（自动）或未读到 → 用自动缩放 `min(w/320, h/240)`；
     * - 手设为 N → 取 `min(N, 自动缩放)`，与 MC 自身的上限行为一致。
     */
    fun effectiveGuiScale(guiScaleOption: Int, screenWidth: Int, screenHeight: Int): Int {
        val auto = minOf(screenWidth / AUTO_BASE_WIDTH, screenHeight / AUTO_BASE_HEIGHT)
            .coerceAtLeast(1)
        return if (guiScaleOption <= 0) auto else minOf(guiScaleOption, auto)
    }

    /**
     * 自动模式下的快捷栏矩形：9 格宽 × 1 格高，水平居中、贴屏幕底边。
     * 单格 = 生效缩放 × 20 像素（与 MC 自己画的快捷栏同尺寸）。
     */
    fun autoRect(
        screenWidth: Int,
        screenHeight: Int,
        guiScaleOption: Int
    ): Rect? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val scale = effectiveGuiScale(guiScaleOption, screenWidth, screenHeight)
        val slot = (scale * SLOT_LOGICAL_PX).toFloat()
        if (slot <= 0f) return null
        var w = slot * SLOTS
        var h = slot
        if (w > screenWidth) w = screenWidth.toFloat()
        if (h > screenHeight) h = screenHeight.toFloat()
        val left = (screenWidth - w) / 2f
        return Rect(left, screenHeight - h, left + w, screenHeight.toFloat())
    }
}
