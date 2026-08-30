package com.verse.explorer.app.skin.cube.head

import com.verse.explorer.app.skin.cube.MainCube

/**
 * 帽子立方体 - 继承自MainCube
 * 尺寸：9x9x9（比头部稍大，形成帽子效果）
 * 位置：与头部重合，渲染在头部上方
 */
class Hat(scale: Float) : MainCube(9.0f * scale, 9.0f * scale, 9.0f * scale, 0.0f * scale, 12.0f * scale, 0.0f * scale) {

    protected var hatTexCoordinates = floatArrayOf(
        // Face 0: 前面 - 皮肤右上区域第二行
        0.625f, 0.25f, 0.625f, 0.125f, 0.75f, 0.125f, 0.75f, 0.25f,
        // Face 1: 上面 - 皮肤右上区域第一行
        0.625f, 0.125f, 0.625f, 0.0f, 0.75f, 0.0f, 0.75f, 0.125f,
        // Face 2: 下面 - 皮肤顶部右中区域
        0.75f, 0.125f, 0.75f, 0.0f, 0.875f, 0.0f, 0.875f, 0.125f,
        // Face 3: 右面 - 皮肤顶部最右侧区域
        0.75f, 0.25f, 0.75f, 0.125f, 0.875f, 0.125f, 0.875f, 0.25f,
        // Face 4: 左面 - 皮肤顶部右侧区域
        0.5f, 0.25f, 0.5f, 0.125f, 0.625f, 0.125f, 0.625f, 0.25f,
        // Face 5: 后面 - 皮肤顶部最右侧边缘
        0.875f, 0.25f, 0.875f, 0.125f, 1.0f, 0.125f, 1.0f, 0.25f
    )

    init {
        addTextures(this.hatTexCoordinates)
    }
}