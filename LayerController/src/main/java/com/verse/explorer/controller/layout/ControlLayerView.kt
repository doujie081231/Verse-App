package com.verse.explorer.controller.layout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.verse.explorer.controller.data.*
import com.verse.explorer.controller.event.ClickEvent
import com.verse.explorer.controller.event.EventHandler

@Composable
fun ControlLayerView(
    layer: ControlLayer,
    isGrabbing: Boolean,
    eventHandler: EventHandler?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        layer.buttons.forEachIndexed { index, button ->
            val visible = when (button.visibility) {
                VisibilityType.ALWAYS -> true
                VisibilityType.WHEN_GRABBING -> isGrabbing
                VisibilityType.WHEN_NOT_GRABBING -> !isGrabbing
            }
            if (visible) {
                ControlButton(
                    data = button,
                    onPressed = { pressed ->
                        eventHandler?.onButtonClick(
                            ClickEvent(button, pressed, button.position.x, button.position.y)
                        )
                    }
                )
            }
        }

        layer.joysticks.forEachIndexed { index, joystick ->
            val visible = when (joystick.visibility) {
                VisibilityType.ALWAYS -> true
                VisibilityType.WHEN_GRABBING -> isGrabbing
                VisibilityType.WHEN_NOT_GRABBING -> !isGrabbing
            }
            if (visible) {
                JoystickView(
                    data = joystick,
                    onMove = { x, y -> eventHandler?.onJoystickMove(index, x, y) },
                    onRelease = { eventHandler?.onJoystickRelease(index) }
                )
            }
        }
    }
}

@Composable
fun ControlButton(
    data: NormalData,
    onPressed: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val style = data.style
    val bgColor = if (isPressed) Color(style.pressedColor) else Color(style.backgroundColor)
    val shape = when (style.shape) {
        ButtonShape.RECTANGLE -> null
        ButtonShape.CIRCLE -> CircleShape
        ButtonShape.ROUNDED_RECTANGLE -> RoundedCornerShape(style.cornerRadius.dp)
    }

    Box(
        modifier = modifier
            .offset(data.position.x.dp, data.position.y.dp)
            .size(data.size.width.dp, data.size.height.dp)
            .alpha(style.opacity)
            .then(
                if (shape != null) Modifier.clip(shape) else Modifier
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressed(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressed(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(bgColor)
        }
        if (data.label.isNotEmpty()) {
            Text(
                text = data.label,
                color = Color(style.textColor),
                fontSize = with(LocalDensity.current) { (data.size.height * 0.35f).toSp() }
            )
        }
    }
}

@Composable
fun JoystickView(
    data: JoystickData,
    onMove: (Float, Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val style = data.style
    val maxDistance = style.outerRadius - style.innerRadius

    Box(
        modifier = modifier
            .offset(data.position.x.dp, data.position.y.dp)
            .size((style.outerRadius * 2).dp)
            .alpha(style.opacity)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        onRelease()
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        onRelease()
                    }
                ) { _, dragAmount ->
                    dragOffset = Offset(
                        (dragOffset.x + dragAmount.x).coerceIn(-maxDistance, maxDistance),
                        (dragOffset.y + dragAmount.y).coerceIn(-maxDistance, maxDistance)
                    )
                    val nx = dragOffset.x / maxDistance
                    val ny = dragOffset.y / maxDistance
                    if (Math.abs(nx) > style.deadZone || Math.abs(ny) > style.deadZone) {
                        onMove(nx, ny)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(Color(style.outerColor))
            drawCircle(
                Color(style.innerColor),
                radius = style.innerRadius * density,
                center = Offset(
                    center.x + dragOffset.x * density,
                    center.y + dragOffset.y * density
                )
            )
        }
    }
}
