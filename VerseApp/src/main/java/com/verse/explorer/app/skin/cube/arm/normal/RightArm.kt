package com.verse.explorer.app.skin.cube.arm.normal

import com.verse.explorer.app.skin.cube.LimbCube

/**
 * 正常体型右臂立方体 - 继承自LimbCube
 * 尺寸：4x12x4（Minecraft标准手臂尺寸）
 * 位置：X轴偏移-6（身体右侧），Y轴偏移2（与身体底部对齐）
 * 旋转：主旋转轴X轴，控制手臂摆动
 */
class RightArm(scale: Float) : LimbCube(
    4.0f * scale, 12.0f * scale, 4.0f * scale, -6.0f * scale, 2.0f * scale, 0.0f * scale,
    1.0f, 0.0f, 0.0f,
    0.5f, 10.0f, -10.0f,
    0.0f, 0.0f, 1.0f,
    -0.333f, 20.0f, -20.0f
) {

    protected var rightArmTexCoordinates = floatArrayOf(
        0.6875f, 0.5f, 0.6875f, 0.40625f, 0.75f, 0.40625f, 0.75f, 0.5f,
        0.6875f, 0.40625f, 0.6875f, 0.3125f, 0.75f, 0.3125f, 0.75f, 0.40625f,
        0.6875f, 0.3125f, 0.6875f, 0.25f, 0.75f, 0.25f, 0.75f, 0.3125f,
        0.75f, 0.3125f, 0.75f, 0.25f, 0.8125f, 0.25f, 0.8125f, 0.3125f,
        0.75f, 0.5f, 0.75f, 0.40625f, 0.8125f, 0.40625f, 0.8125f, 0.5f,
        0.75f, 0.40625f, 0.75f, 0.3125f, 0.8125f, 0.3125f, 0.8125f, 0.40625f,
        0.625f, 0.5f, 0.625f, 0.40625f, 0.6875f, 0.40625f, 0.6875f, 0.5f,
        0.625f, 0.40625f, 0.625f, 0.3125f, 0.6875f, 0.3125f, 0.6875f, 0.40625f,
        0.8125f, 0.5f, 0.8125f, 0.40625f, 0.875f, 0.40625f, 0.875f, 0.5f,
        0.8125f, 0.40625f, 0.8125f, 0.3125f, 0.875f, 0.3125f, 0.875f, 0.40625f
    )

    init {
        addTextures(this.rightArmTexCoordinates)
    }
}