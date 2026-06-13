package com.verse.explorer.controller.data

import kotlinx.serialization.Serializable

@Serializable
data class ButtonPosition(
    val x: Float = 0f,
    val y: Float = 0f
)

@Serializable
data class ButtonSize(
    val width: Float = 48f,
    val height: Float = 48f
)

@Serializable
enum class ButtonShape {
    RECTANGLE, CIRCLE, ROUNDED_RECTANGLE
}

@Serializable
enum class VisibilityType {
    ALWAYS, WHEN_GRABBING, WHEN_NOT_GRABBING
}

@Serializable
enum class HideLayerWhen {
    NEVER, GRABBING, NOT_GRABBING
}

@Serializable
data class ButtonStyle(
    val shape: ButtonShape = ButtonShape.ROUNDED_RECTANGLE,
    val backgroundColor: Long = 0x80333333,
    val pressedColor: Long = 0x80666666,
    val textColor: Long = 0xFFFFFFFF,
    val cornerRadius: Float = 8f,
    val strokeWidth: Float = 0f,
    val strokeColor: Long = 0x00FFFFFF,
    val opacity: Float = 0.7f
)

@Serializable
data class JoystickStyle(
    val outerRadius: Float = 60f,
    val innerRadius: Float = 24f,
    val outerColor: Long = 0x40333333,
    val innerColor: Long = 0x80666666,
    val opacity: Float = 0.7f,
    val deadZone: Float = 0.1f
)

@Serializable
data class TextData(
    val text: String = "",
    val fontSize: Float = 12f,
    val alignment: TextAlignment = TextAlignment.CENTER
)

@Serializable
enum class TextAlignment {
    START, CENTER, END
}

@Serializable
enum class Widget {
    BUTTON, JOYSTICK, TEXT, D_PAD
}

@Serializable
data class NormalData(
    val position: ButtonPosition = ButtonPosition(),
    val size: ButtonSize = ButtonSize(),
    val style: ButtonStyle = ButtonStyle(),
    val keycode: Int = 0,
    val label: String = "",
    val visibility: VisibilityType = VisibilityType.ALWAYS,
    val hideLayerWhen: HideLayerWhen = HideLayerWhen.NEVER
)

@Serializable
data class ControlLayer(
    val name: String = "",
    val buttons: List<NormalData> = emptyList(),
    val joysticks: List<JoystickData> = emptyList(),
    val texts: List<TextData> = emptyList(),
    val hideLayerWhen: HideLayerWhen = HideLayerWhen.NEVER
)

@Serializable
data class JoystickData(
    val position: ButtonPosition = ButtonPosition(),
    val style: JoystickStyle = JoystickStyle(),
    val xAxisKeycode: Int = 0,
    val yAxisKeycode: Int = 0,
    val visibility: VisibilityType = VisibilityType.ALWAYS
)

@Serializable
data class ControlLayout(
    val name: String = "Default",
    val version: Int = 1,
    val layers: List<ControlLayer> = emptyList(),
    val screenWidth: Int = 1280,
    val screenHeight: Int = 720
)
