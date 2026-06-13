package com.verse.explorer.launcher.controller

import android.content.Context
import android.view.MotionEvent
import android.view.View
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ControlButton(
    val id: String,
    val keyCode: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val label: String,
    val opacity: Float = 0.7f,
    val visible: Boolean = true,
    val toggleable: Boolean = false
)

@Serializable
data class ControlStick(
    val id: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val deadzone: Float = 0.1f,
    val opacity: Float = 0.7f
)

@Serializable
data class ControlLayout(
    val name: String,
    val buttons: List<ControlButton>,
    val sticks: List<ControlStick> = emptyList(),
    val mouseEnabled: Boolean = true,
    val mouseSensitivity: Float = 1.0f
)

class TouchController(private val context: Context) {
    
    private val layoutsDir = File(context.filesDir, "controller_layouts")
    private val json = Json { ignoreUnknownKeys = true }
    
    private var currentLayout: ControlLayout? = null
    private var buttonStates = mutableMapOf<String, Boolean>()
    private var stickStates = mutableMapOf<String, Pair<Float, Float>>()
    
    private var onButtonPressed: ((keyCode: Int, pressed: Boolean) -> Unit)? = null
    private var onStickMoved: ((id: String, x: Float, y: Float) -> Unit)? = null
    private var onMouseMoved: ((dx: Float, dy: Float) -> Unit)? = null
    
    init {
        if (!layoutsDir.exists()) {
            layoutsDir.mkdirs()
            createDefaultLayout()
        }
    }
    
    fun loadLayout(name: String): ControlLayout? {
        val file = File(layoutsDir, "$name.json")
        if (!file.exists()) return null
        
        return try {
            val layout = json.decodeFromString<ControlLayout>(file.readText())
            currentLayout = layout
            layout
        } catch (e: Exception) {
            println("Failed to load layout: ${e.message}")
            null
        }
    }
    
    fun saveLayout(layout: ControlLayout) {
        val file = File(layoutsDir, "${layout.name}.json")
        file.writeText(json.encodeToString(ControlLayout.serializer(), layout))
    }
    
    fun getLayouts(): List<String> {
        return layoutsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }
    
    fun deleteLayout(name: String) {
        val file = File(layoutsDir, "$name.json")
        if (file.exists()) file.delete()
    }
    
    fun getCurrentLayout(): ControlLayout? = currentLayout
    
    fun setButtonCallback(callback: (keyCode: Int, pressed: Boolean) -> Unit) {
        onButtonPressed = callback
    }
    
    fun setStickCallback(callback: (id: String, x: Float, y: Float) -> Unit) {
        onStickMoved = callback
    }
    
    fun setMouseCallback(callback: (dx: Float, dy: Float) -> Unit) {
        onMouseMoved = callback
    }
    
    fun onTouchEvent(event: MotionEvent, viewWidth: Int, viewHeight: Int): Boolean {
        val layout = currentLayout ?: return false
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                for (i in 0 until event.pointerCount) {
                    val x = event.getX(i)
                    val y = event.getY(i)
                    handleTouchDown(x, y, event.getPointerId(i), layout)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val x = event.getX(i)
                    val y = event.getY(i)
                    handleTouchMove(x, y, event.getPointerId(i), layout)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                handleTouchUp(pointerId, layout)
            }
            MotionEvent.ACTION_CANCEL -> {
                releaseAllButtons()
            }
        }
        
        return true
    }
    
    private fun handleTouchDown(x: Float, y: Float, pointerId: Int, layout: ControlLayout) {
        layout.buttons.forEach { button ->
            if (isPointInButton(x, y, button)) {
                buttonStates[button.id] = true
                onButtonPressed?.invoke(button.keyCode, true)
            }
        }
        
        layout.sticks.forEach { stick ->
            if (isPointInStick(x, y, stick)) {
                stickStates[stick.id] = Pair(0f, 0f)
            }
        }
    }
    
    private fun handleTouchMove(x: Float, y: Float, pointerId: Int, layout: ControlLayout) {
        layout.sticks.forEach { stick ->
            if (stickStates.containsKey(stick.id)) {
                val centerX = stick.x + stick.size / 2
                val centerY = stick.y + stick.size / 2
                
                var dx = (x - centerX) / (stick.size / 2)
                var dy = (y - centerY) / (stick.size / 2)
                
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance > 1f) {
                    dx /= distance
                    dy /= distance
                }
                
                if (kotlin.math.abs(dx) < stick.deadzone) dx = 0f
                if (kotlin.math.abs(dy) < stick.deadzone) dy = 0f
                
                stickStates[stick.id] = Pair(dx, dy)
                onStickMoved?.invoke(stick.id, dx, dy)
            }
        }
    }
    
    private fun handleTouchUp(pointerId: Int, layout: ControlLayout) {
        layout.buttons.forEach { button ->
            if (buttonStates[button.id] == true) {
                if (!button.toggleable) {
                    buttonStates[button.id] = false
                    onButtonPressed?.invoke(button.keyCode, false)
                }
            }
        }
        
        stickStates.clear()
        layout.sticks.forEach { stick ->
            onStickMoved?.invoke(stick.id, 0f, 0f)
        }
    }
    
    private fun releaseAllButtons() {
        currentLayout?.buttons?.forEach { button ->
            if (buttonStates[button.id] == true) {
                onButtonPressed?.invoke(button.keyCode, false)
            }
        }
        buttonStates.clear()
        stickStates.clear()
    }
    
    private fun isPointInButton(x: Float, y: Float, button: ControlButton): Boolean {
        return x >= button.x && x <= button.x + button.width &&
               y >= button.y && y <= button.y + button.height
    }
    
    private fun isPointInStick(x: Float, y: Float, stick: ControlStick): Boolean {
        val centerX = stick.x + stick.size / 2
        val centerY = stick.y + stick.size / 2
        val distance = kotlin.math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY))
        return distance <= stick.size / 2
    }
    
    private fun createDefaultLayout() {
        val defaultLayout = ControlLayout(
            name = "Default",
            buttons = listOf(
                ControlButton("forward", 87, 100f, 800f, 80f, 80f, "W"),
                ControlButton("left", 65, 20f, 880f, 80f, 80f, "A"),
                ControlButton("backward", 83, 100f, 880f, 80f, 80f, "S"),
                ControlButton("right", 68, 180f, 880f, 80f, 80f, "D"),
                ControlButton("jump", 32, 100f, 720f, 80f, 80f, "⬆"),
                ControlButton("sneak", 340, 20f, 800f, 80f, 80f, "⬇"),
                ControlButton("inventory", 69, 900f, 800f, 80f, 80f, "E"),
                ControlButton("chat", 84, 900f, 880f, 80f, 80f, "T"),
                ControlButton("attack", -100, 600f, 850f, 120f, 120f, "⚔"),
                ControlButton("use", -99, 750f, 850f, 120f, 120f, "🛡")
            ),
            sticks = listOf(
                ControlStick("move", 200f, 400f, 150f),
                ControlStick("look", 800f, 400f, 150f)
            )
        )
        
        saveLayout(defaultLayout)
        currentLayout = defaultLayout
    }
}
