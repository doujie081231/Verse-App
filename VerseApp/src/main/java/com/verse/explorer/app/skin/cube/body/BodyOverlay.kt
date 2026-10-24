package com.verse.explorer.app.skin.cube.body

import com.verse.explorer.app.skin.cube.MainCube

class BodyOverlay(scale: Float) : MainCube(8.470589f * scale, 12.705883f * scale, 4.2352943f * scale, 0.0f * scale, 2.0f * scale, 0.0f * scale) {

    protected var jacketTexCoordinates = floatArrayOf(
        0.3125f, 0.75f, 0.3125f, 0.5625f, 0.4375f, 0.5625f, 0.4375f, 0.75f,
        0.3125f, 0.5625f, 0.3125f, 0.5f, 0.4375f, 0.5f, 0.4375f, 0.5625f,
        0.4375f, 0.5625f, 0.4375f, 0.5f, 0.5625f, 0.5f, 0.5625f, 0.5625f,
        0.4375f, 0.75f, 0.4375f, 0.5625f, 0.5f, 0.5625f, 0.5f, 0.75f,
        0.25f, 0.75f, 0.25f, 0.5625f, 0.3125f, 0.5625f, 0.3125f, 0.75f,
        0.5f, 0.75f, 0.5f, 0.5625f, 0.625f, 0.5625f, 0.625f, 0.75f
    )

    init {
        addTextures(this.jacketTexCoordinates)
    }
}