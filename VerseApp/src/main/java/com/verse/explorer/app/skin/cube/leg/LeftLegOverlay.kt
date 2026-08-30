package com.verse.explorer.app.skin.cube.leg

import com.verse.explorer.app.skin.cube.LimbCube

/**
 * 左腿外套/覆盖层立方体 - 继承自LimbCube
 * 尺寸：略大于左腿（4.24x12.71x4.24），形成靴子效果
 * 位置：与左腿重合，渲染在左腿上方
 */
class LeftLegOverlay(scale: Float) : LimbCube(
    4.2352943f * scale, 12.705883f * scale, 4.2352943f * scale, 2.0f * scale, -10.0f * scale, 0.0f * scale,
    1.0f, 0.0f, 0.0f,
    1.5f, 30.0f, -30.0f,
    0.0f, 1.0f, 0.0f,
    -0.5f, 30.0f, -30.0f
) {

    protected var leftLegOverlayTexCoordinates = floatArrayOf(
        0.0625f, 1.0f, 0.0625f, 0.90625f, 0.125f, 0.90625f, 0.125f, 1.0f,
        0.0625f, 0.90625f, 0.0625f, 0.8125f, 0.125f, 0.8125f, 0.125f, 0.90625f,
        0.0625f, 0.8125f, 0.0625f, 0.75f, 0.125f, 0.75f, 0.125f, 0.8125f,
        0.125f, 0.8125f, 0.125f, 0.75f, 0.1875f, 0.75f, 0.1875f, 0.8125f,
        0.125f, 1.0f, 0.125f, 0.90625f, 0.1875f, 0.90625f, 0.1875f, 1.0f,
        0.125f, 0.90625f, 0.125f, 0.8125f, 0.1875f, 0.8125f, 0.1875f, 0.90625f,
        0.0f, 1.0f, 0.0f, 0.90625f, 0.0625f, 0.90625f, 0.0625f, 1.0f,
        0.0f, 0.90625f, 0.0f, 0.8125f, 0.0625f, 0.8125f, 0.0625f, 0.90625f,
        0.1875f, 1.0f, 0.1875f, 0.90625f, 0.25f, 0.90625f, 0.25f, 1.0f,
        0.1875f, 0.90625f, 0.1875f, 0.8125f, 0.25f, 0.8125f, 0.25f, 0.90625f
    )

    init {
        addTextures(this.leftLegOverlayTexCoordinates)
    }
}