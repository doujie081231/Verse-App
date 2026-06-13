package com.verse.explorer.controller.event

import com.verse.explorer.controller.data.NormalData

data class ClickEvent(
    val button: NormalData,
    val pressed: Boolean,
    val x: Float,
    val y: Float
)

interface EventHandler {
    fun onButtonClick(event: ClickEvent)
    fun onJoystickMove(id: Int, x: Float, y: Float)
    fun onJoystickRelease(id: Int)
}
