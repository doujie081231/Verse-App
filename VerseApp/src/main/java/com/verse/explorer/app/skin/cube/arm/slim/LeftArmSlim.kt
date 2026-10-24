package com.verse.explorer.app.skin.cube.arm.slim

import com.verse.explorer.app.skin.cube.LimbCube

/**
 * 纤细体型左臂立方体 - 继承自LimbCube
 * 尺寸：3x12x4（比正常体型手臂窄1像素）
 * 位置：X轴偏移5.5（比正常体型更靠近身体），Y轴偏移2
 * 旋转：主旋转轴X轴，控制手臂摆动
 */
class LeftArmSlim(scale: Float) : LimbCube(
    3.0f * scale, 12.0f * scale, 4.0f * scale, 5.5f * scale, 2.0f * scale, 0.0f * scale,
    1.0f, 0.0f, 0.0f,
    -0.5f, 10.0f, -10.0f,
    0.0f, 0.0f, 1.0f,
    0.333f, 20.0f, -20.0f
) {

    protected var leftArmSlimTexCoordinates = floatArrayOf(
        0.5625f, 1.0f, 0.5625f, 0.90625f, 0.609375f, 0.90625f, 0.609375f, 1.0f,
        0.5625f, 0.90625f, 0.5625f, 0.8125f, 0.609375f, 0.8125f, 0.609375f, 0.90625f,
        0.5625f, 0.8125f, 0.5625f, 0.75f, 0.609375f, 0.75f, 0.609375f, 0.8125f,
        0.609375f, 0.8125f, 0.609375f, 0.75f, 0.65625f, 0.75f, 0.65625f, 0.8125f,
        0.609375f, 1.0f, 0.609375f, 0.90625f, 0.671875f, 0.90625f, 0.671875f, 1.0f,
        0.609375f, 0.90625f, 0.609375f, 0.8125f, 0.671875f, 0.8125f, 0.671875f, 0.90625f,
        0.5f, 1.0f, 0.5f, 0.90625f, 0.5625f, 0.90625f, 0.5625f, 1.0f,
        0.5f, 0.90625f, 0.5f, 0.8125f, 0.5625f, 0.8125f, 0.5625f, 0.90625f,
        0.671875f, 1.0f, 0.671875f, 0.90625f, 0.71875f, 0.90625f, 0.71875f, 1.0f,
        0.671875f, 0.90625f, 0.671875f, 0.8125f, 0.71875f, 0.8125f, 0.71875f, 0.90625f
    )

    init {
        addTextures(this.leftArmSlimTexCoordinates)
    }
}