package com.verse.explorer.app.skin.cube.leg

import com.verse.explorer.app.skin.cube.LimbCube

/**
 * 右腿外套/覆盖层立方体 - 继承自LimbCube
 * 尺寸：略大于右腿（4.24x12.71x4.24），形成靴子效果
 * 位置：与右腿重合，渲染在右腿上方
 */
class RightLegOverlay(scale: Float) : LimbCube(
    4.2352943f * scale, 12.705883f * scale, 4.2352943f * scale, -2.0f * scale, -10.0f * scale, 0.0f * scale,
    1.0f, 0.0f, 0.0f,
    -1.5f, 30.0f, -30.0f,
    0.0f, 1.0f, 0.0f,
    0.5f, 30.0f, -30.0f
) {

    protected var rightLegOverlayTexCoordinates = floatArrayOf(
        0.0625f, 0.75f, 0.0625f, 0.65625f, 0.125f, 0.65625f, 0.125f, 0.75f,
        0.0625f, 0.65625f, 0.0625f, 0.5625f, 0.125f, 0.5625f, 0.125f, 0.65625f,
        0.0625f, 0.5625f, 0.0625f, 0.5f, 0.125f, 0.5f, 0.125f, 0.5625f,
        0.125f, 0.5625f, 0.125f, 0.5f, 0.1875f, 0.5f, 0.1875f, 0.5625f,
        0.125f, 0.75f, 0.125f, 0.65625f, 0.1875f, 0.65625f, 0.1875f, 0.75f,
        0.125f, 0.65625f, 0.125f, 0.5625f, 0.1875f, 0.5625f, 0.1875f, 0.65625f,
        0.0f, 0.75f, 0.0f, 0.65625f, 0.0625f, 0.65625f, 0.0625f, 0.75f,
        0.0f, 0.65625f, 0.0f, 0.5625f, 0.0625f, 0.5625f, 0.0625f, 0.65625f,
        0.1875f, 0.75f, 0.1875f, 0.65625f, 0.25f, 0.65625f, 0.25f, 0.75f,
        0.1875f, 0.65625f, 0.1875f, 0.5625f, 0.25f, 0.5625f, 0.25f, 0.65625f
    )

    init {
        addTextures(this.rightLegOverlayTexCoordinates)
    }
}