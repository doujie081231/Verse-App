package com.verse.explorer.app.skin.cube.arm.slim

import com.verse.explorer.app.skin.cube.LimbCube

/**
 * 纤细体型右臂立方体 - 继承自LimbCube
 * 尺寸：3x12x4（比正常体型手臂窄1像素）
 * 位置：X轴偏移-5.5（比正常体型更靠近身体），Y轴偏移2
 * 旋转：主旋转轴X轴，控制手臂摆动
 */
class RightArmSlim(scale: Float) : LimbCube(
    3.0f * scale, 12.0f * scale, 4.0f * scale, -5.5f * scale, 2.0f * scale, 0.0f * scale,
    1.0f, 0.0f, 0.0f,
    0.5f, 10.0f, -10.0f,
    0.0f, 0.0f, 1.0f,
    -0.333f, 20.0f, -20.0f
) {

    protected var rightArmSlimTexCoordinates = floatArrayOf(
        0.6875f, 0.5f, 0.6875f, 0.40625f, 0.734375f, 0.40625f, 0.734375f, 0.5f,
        0.6875f, 0.40625f, 0.6875f, 0.3125f, 0.734375f, 0.3125f, 0.734375f, 0.40625f,
        0.6875f, 0.3125f, 0.6875f, 0.25f, 0.734375f, 0.25f, 0.734375f, 0.3125f,
        0.734375f, 0.3125f, 0.734375f, 0.25f, 0.78125f, 0.25f, 0.78125f, 0.3125f,
        0.734375f, 0.5f, 0.734375f, 0.40625f, 0.796875f, 0.40625f, 0.796875f, 0.5f,
        0.734375f, 0.40625f, 0.734375f, 0.3125f, 0.796875f, 0.3125f, 0.796875f, 0.40625f,
        0.625f, 0.5f, 0.625f, 0.40625f, 0.6875f, 0.40625f, 0.6875f, 0.5f,
        0.625f, 0.40625f, 0.625f, 0.3125f, 0.6875f, 0.3125f, 0.6875f, 0.40625f,
        0.796875f, 0.5f, 0.796875f, 0.40625f, 0.84375f, 0.40625f, 0.84375f, 0.5f,
        0.796875f, 0.40625f, 0.796875f, 0.3125f, 0.84375f, 0.3125f, 0.84375f, 0.40625f
    )

    init {
        addTextures(this.rightArmSlimTexCoordinates)
    }
}