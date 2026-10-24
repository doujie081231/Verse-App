package com.verse.explorer.app.skin.cube.leg

import com.verse.explorer.app.skin.cube.LimbCube

/**
 * 右腿立方体 - 继承自LimbCube
 * 尺寸：4x12x4（Minecraft标准腿部尺寸）
 * 位置：X轴偏移-2（身体右侧），Y轴偏移-10（身体下方）
 * 旋转：主旋转轴X轴，控制腿部摆动（行走动画）
 */
class RightLeg(scale: Float) : LimbCube(
    4.0f * scale, 12.0f * scale, 4.0f * scale, -2.0f * scale, -10.0f * scale, 0.0f * scale,
    1.0f, 0.0f, 0.0f,
    -1.5f, 30.0f, -30.0f,
    0.0f, 1.0f, 0.0f,
    0.5f, 30.0f, -30.0f
) {

    protected var rightLegTexCoordinates = floatArrayOf(
        0.0625f, 0.5f, 0.0625f, 0.40625f, 0.125f, 0.40625f, 0.125f, 0.5f,
        0.0625f, 0.40625f, 0.0625f, 0.3125f, 0.125f, 0.3125f, 0.125f, 0.40625f,
        0.0625f, 0.3125f, 0.0625f, 0.25f, 0.125f, 0.25f, 0.125f, 0.3125f,
        0.125f, 0.3125f, 0.125f, 0.25f, 0.1875f, 0.25f, 0.1875f, 0.3125f,
        0.125f, 0.5f, 0.125f, 0.40625f, 0.1875f, 0.40625f, 0.1875f, 0.5f,
        0.125f, 0.40625f, 0.125f, 0.3125f, 0.1875f, 0.3125f, 0.1875f, 0.40625f,
        0.0f, 0.5f, 0.0f, 0.40625f, 0.0625f, 0.40625f, 0.0625f, 0.5f,
        0.0f, 0.40625f, 0.0f, 0.3125f, 0.0625f, 0.3125f, 0.0625f, 0.40625f,
        0.1875f, 0.5f, 0.1875f, 0.40625f, 0.25f, 0.40625f, 0.25f, 0.5f,
        0.1875f, 0.40625f, 0.1875f, 0.3125f, 0.25f, 0.3125f, 0.25f, 0.40625f
    )

    init {
        addTextures(this.rightLegTexCoordinates)
    }
}