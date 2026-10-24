package com.verse.explorer.app.skin.cube.body

import com.verse.explorer.app.skin.cube.MainCube

/**
 * 身体立方体 - 继承自MainCube
 * 尺寸：8x12x4（Minecraft标准身体尺寸）
 * 位置：Y轴偏移2（位于腿部上方）
 */
class Body(scale: Float) : MainCube(8.0f * scale, 12.0f * scale, 4.0f * scale, 0.0f * scale, 2.0f * scale, 0.0f * scale) {

    protected var bodyTexCoordinates = floatArrayOf(
        // Face 0: 前面 - 皮肤顶部中间区域
        0.3125f, 0.5f, 0.3125f, 0.3125f, 0.4375f, 0.3125f, 0.4375f, 0.5f,
        // Face 1: 上面 - 皮肤顶部区域
        0.3125f, 0.3125f, 0.3125f, 0.25f, 0.4375f, 0.25f, 0.4375f, 0.3125f,
        // Face 2: 下面 - 皮肤顶部中右区域
        0.4375f, 0.3125f, 0.4375f, 0.25f, 0.5625f, 0.25f, 0.5625f, 0.3125f,
        // Face 3: 右面 - 皮肤右侧区域
        0.4375f, 0.5f, 0.4375f, 0.3125f, 0.5f, 0.3125f, 0.5f, 0.5f,
        // Face 4: 左面 - 皮肤左侧区域
        0.25f, 0.5f, 0.25f, 0.3125f, 0.3125f, 0.3125f, 0.3125f, 0.5f,
        // Face 5: 后面 - 皮肤中右区域
        0.5f, 0.5f, 0.5f, 0.3125f, 0.625f, 0.3125f, 0.625f, 0.5f
    )

    init {
        addTextures(this.bodyTexCoordinates)
    }
}