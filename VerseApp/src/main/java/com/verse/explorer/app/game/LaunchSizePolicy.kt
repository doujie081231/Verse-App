package com.verse.explorer.app.game

/**
 * 启动宽高选择策略。
 *
 * 只做纯计算，百分比缩放本身仍由 MenuSetting.resolveRenderSize 负责；
 * 这里保持原启动编排的逐项判断：强制宽高优先，其次 Surface 缩放，最后参数回退。
 */
internal object LaunchSizePolicy {

    data class Input(
        val usesGlobal: Boolean,
        val forceResolution: Boolean,
        val forceWidth: Int,
        val forceHeight: Int,
        val surfaceWidth: Int,
        val surfaceHeight: Int,
        val scaledWidth: Int,
        val scaledHeight: Int,
        val fallbackWidth: Int,
        val fallbackHeight: Int
    )

    fun resolve(input: Input): Pair<Int, Int> {
        val forceActive = !input.usesGlobal &&
            input.forceResolution &&
            input.forceWidth > 0 &&
            input.forceHeight > 0
        val width = when {
            forceActive -> input.forceWidth
            input.surfaceWidth > 0 -> input.scaledWidth
            else -> input.fallbackWidth
        }
        val height = when {
            forceActive -> input.forceHeight
            input.surfaceHeight > 0 -> input.scaledHeight
            else -> input.fallbackHeight
        }
        return width to height
    }
}
