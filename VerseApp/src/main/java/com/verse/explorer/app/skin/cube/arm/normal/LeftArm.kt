package com.verse.explorer.app.skin.cube.arm.normal

import com.verse.explorer.app.skin.cube.LimbCube

/**
 * 正常体型左臂立方体 - 继承自LimbCube
 * 尺寸：4x12x4（Minecraft标准手臂尺寸）
 * 位置：X轴偏移6（身体左侧），Y轴偏移2（与身体底部对齐）
 * 旋转：主旋转轴X轴，控制手臂摆动（与右臂相反方向）
 */
class LeftArm(scale: Float) : LimbCube(
    4.0f * scale, 12.0f * scale, 4.0f * scale, 6.0f * scale, 2.0f * scale, 0.0f * scale,
    1.0f, 0.0f, 0.0f,
    -0.5f, 10.0f, -10.0f,
    0.0f, 1.0f, 0.0f,
    0.333f, 20.0f, -20.0f
) {

    protected var leftArmTexCoordinates = floatArrayOf(
        0.5625f, 1.0f, 0.5625f, 0.90625f, 0.625f, 0.90625f, 0.625f, 1.0f,
        0.5625f, 0.90625f, 0.5625f, 0.8125f, 0.625f, 0.8125f, 0.625f, 0.90625f,
        0.5625f, 0.8125f, 0.5625f, 0.75f, 0.625f, 0.75f, 0.625f, 0.8125f,
        0.625f, 0.8125f, 0.625f, 0.75f, 0.6875f, 0.75f, 0.6875f, 0.8125f,
        0.625f, 1.0f, 0.625f, 0.90625f, 0.6875f, 0.90625f, 0.6875f, 1.0f,
        0.625f, 0.90625f, 0.625f, 0.8125f, 0.6875f, 0.8125f, 0.6875f, 0.90625f,
        0.5f, 1.0f, 0.5f, 0.90625f, 0.5625f, 0.90625f, 0.5625f, 1.0f,
        0.5f, 0.90625f, 0.5f, 0.8125f, 0.5625f, 0.8125f, 0.5625f, 0.90625f,
        0.6875f, 1.0f, 0.6875f, 0.90625f, 0.75f, 0.90625f, 0.75f, 1.0f,
        0.6875f, 0.90625f, 0.6875f, 0.8125f, 0.75f, 0.8125f, 0.75f, 0.90625f
    )

    init {
        addTextures(this.leftArmTexCoordinates)
    }
}