package com.verse.explorer.app.skin.cube.head

import com.verse.explorer.app.skin.cube.MainCube

/**
 * 头部立方体 - 继承自MainCube
 * 尺寸：8x8x8（Minecraft标准头部尺寸）
 * 位置：Y轴偏移12（位于身体上方）
 */
class Head(scale: Float) : MainCube(8.0f * scale, 8.0f * scale, 8.0f * scale, 0.0f * scale, 12.0f * scale, 0.0f * scale) {

    protected var headTexCoordinates = floatArrayOf(
        // Face 0: 前面 - 皮肤左上角第二行 (8-16像素)
        0.125f, 0.25f, 0.125f, 0.125f, 0.25f, 0.125f, 0.25f, 0.25f,
        // Face 1: 上面 - 皮肤左上角第一行 (0-8像素)
        0.125f, 0.125f, 0.125f, 0.0f, 0.25f, 0.0f, 0.25f, 0.125f,
        // Face 2: 下面 - 皮肤顶部中间区域
        0.25f, 0.125f, 0.25f, 0.0f, 0.375f, 0.0f, 0.375f, 0.125f,
        // Face 3: 右面 - 皮肤顶部右侧区域
        0.25f, 0.25f, 0.25f, 0.125f, 0.375f, 0.125f, 0.375f, 0.25f,
        // Face 4: 左面 - 皮肤顶部左侧区域
        0.0f, 0.25f, 0.0f, 0.125f, 0.125f, 0.125f, 0.125f, 0.25f,
        // Face 5: 后面 - 皮肤顶部中右区域
        0.375f, 0.25f, 0.375f, 0.125f, 0.5f, 0.125f, 0.5f, 0.25f
    )

    init {
        addTextures(this.headTexCoordinates)
    }
}