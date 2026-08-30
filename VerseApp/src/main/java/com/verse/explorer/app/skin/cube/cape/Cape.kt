package com.verse.explorer.app.skin.cube.cape

import com.verse.explorer.app.skin.cube.CapeCube

class Cape(scale: Float) : CapeCube(10.0f * scale, 16.0f * scale, scale, 0.0f * scale, 0.0f * scale, -1.75f * scale) {

    protected var capeTexCoordinates = floatArrayOf(
        0.1875f, 0.53125f, 0.1875f, 0.03125f, 0.34375f, 0.03125f, 0.34375f, 0.53125f,
        0.015625f, 0.03125f, 0.015625f, 0f, 0.171875f, 0f, 0.171875f, 0.03125f,
        0.171875f, 0.03125f, 0.171875f, 0f, 0.328125f, 0f, 0.328125f, 0.03125f,
        0f, 0.53125f, 0f, 0.03125f, 0.015625f, 0.03125f, 0.015625f, 0.53125f,
        0.171875f, 0.53125f, 0.171875f, 0.03125f, 0.1875f, 0.03125f, 0.1875f, 0.53125f,
        0.015625f, 0.53125f, 0.015625f, 0.03125f, 0.171875f, 0.03125f, 0.171875f, 0.53125f
    )

    init {
        addTextures(this.capeTexCoordinates)
    }
}