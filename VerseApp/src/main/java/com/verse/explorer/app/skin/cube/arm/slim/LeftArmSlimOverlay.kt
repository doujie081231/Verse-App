package com.verse.explorer.app.skin.cube.arm.slim

import com.verse.explorer.app.skin.cube.LimbCube

/**
 * 纤细体型左臂外套/覆盖层立方体 - 继承自LimbCube
 * 尺寸：略大于纤细左臂，形成外套效果
 * 位置：与纤细左臂重合，渲染在左臂上方
 */
class LeftArmSlimOverlay(scale: Float) : LimbCube(
    3.1764708f * scale, 12.705883f * scale, 4.2352943f * scale, 5.5f * scale, 2.0f * scale, 0.0f * scale,
    1.0f, 0.0f, 0.0f,
    -0.5f, 10.0f, -10.0f,
    0.0f, 0.0f, 1.0f,
    0.333f, 20.0f, -20.0f
) {

    protected var leftArmSlimOverlayTexCoordinates = floatArrayOf(
        0.8125f, 1.0f, 0.8125f, 0.90625f, 0.875f, 0.90625f, 0.875f, 1.0f,
        0.8125f, 0.90625f, 0.8125f, 0.8125f, 0.875f, 0.8125f, 0.875f, 0.90625f,
        0.8125f, 0.8125f, 0.8125f, 0.75f, 0.875f, 0.75f, 0.875f, 0.8125f,
        0.875f, 0.8125f, 0.875f, 0.75f, 0.9375f, 0.75f, 0.9375f, 0.8125f,
        0.875f, 1.0f, 0.875f, 0.90625f, 0.9375f, 0.90625f, 0.9375f, 1.0f,
        0.875f, 0.90625f, 0.875f, 0.8125f, 0.9375f, 0.8125f, 0.9375f, 0.90625f,
        0.75f, 1.0f, 0.75f, 0.90625f, 0.8125f, 0.90625f, 0.8125f, 1.0f,
        0.75f, 0.90625f, 0.75f, 0.8125f, 0.8125f, 0.8125f, 0.8125f, 0.90625f,
        0.9375f, 1.0f, 0.9375f, 0.90625f, 1.0f, 0.90625f, 1.0f, 1.0f,
        0.9375f, 0.90625f, 0.9375f, 0.8125f, 1.0f, 0.8125f, 1.0f, 0.90625f
    )

    init {
        addTextures(this.leftArmSlimOverlayTexCoordinates)
    }
}