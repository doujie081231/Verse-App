package com.verse.explorer.app.skin.cube.arm.normal

import com.verse.explorer.app.skin.cube.LimbCube

/**
 * 正常体型右臂外套/覆盖层立方体 - 继承自LimbCube
 * 尺寸：略大于右臂（4.24x12.71x4.24），形成外套效果
 * 位置：与右臂重合，渲染在右臂上方
 */
class RightArmOverlay(scale: Float) : LimbCube(
    4.2352943f * scale, 12.705883f * scale, 4.2352943f * scale, -6.0f * scale, 2.0f * scale, 0.0f * scale,
    1.0f, 0.0f, 0.0f,
    0.5f, 10.0f, -10.0f,
    0.0f, 1.0f, 0.0f,
    -0.333f, 20.0f, -20.0f
) {

    protected var rightArmOverlayTexCoordinates = floatArrayOf(
        0.6875f, 0.75f, 0.6875f, 0.65625f, 0.75f, 0.65625f, 0.75f, 0.75f,
        0.6875f, 0.65625f, 0.6875f, 0.5625f, 0.75f, 0.5625f, 0.75f, 0.65625f,
        0.6875f, 0.5625f, 0.6875f, 0.5f, 0.75f, 0.5f, 0.75f, 0.5625f,
        0.75f, 0.5625f, 0.75f, 0.5f, 0.8125f, 0.5f, 0.8125f, 0.5625f,
        0.75f, 0.75f, 0.75f, 0.65625f, 0.8125f, 0.65625f, 0.8125f, 0.75f,
        0.75f, 0.65625f, 0.75f, 0.5625f, 0.8125f, 0.5625f, 0.8125f, 0.65625f,
        0.625f, 0.75f, 0.625f, 0.65625f, 0.6875f, 0.65625f, 0.6875f, 0.75f,
        0.625f, 0.65625f, 0.625f, 0.5625f, 0.6875f, 0.5625f, 0.6875f, 0.65625f,
        0.8125f, 0.75f, 0.8125f, 0.65625f, 0.875f, 0.65625f, 0.875f, 0.75f,
        0.8125f, 0.65625f, 0.8125f, 0.5625f, 0.875f, 0.5625f, 0.875f, 0.65625f
    )

    init {
        addTextures(this.rightArmOverlayTexCoordinates)
    }
}