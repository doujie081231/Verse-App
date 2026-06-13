package com.verse.explorer.launcher.bridge

import android.content.Context
import android.view.MotionEvent
import java.io.File

data class TouchEvent(
    val action: Int,
    val x: Float,
    val y: Float,
    val pointerId: Int,
    val timestamp: Long
)

@Deprecated(
    message = "InputBridge native methods are not implemented. Use ZLBridge/CallbackBridge/glfw_hook instead.",
    replaceWith = ReplaceWith("ZLBridge")
)
class InputBridge(private val context: Context) {
    
    private var mouseEnabled = true
    private var mouseX = 0f
    private var mouseY = 0f
    private var mouseLeftPressed = false
    private var mouseRightPressed = false
    private var mouseMiddlePressed = false
    
    private val keyStates = mutableMapOf<Int, Boolean>()
    private val touchPoints = mutableMapOf<Int, TouchPoint>()
    
    data class TouchPoint(
        val id: Int,
        var x: Float,
        var y: Float,
        var startX: Float,
        var startY: Float,
        var lastX: Float,
        var lastY: Float,
        val isMouseEmulation: Boolean = false
    )
    
    fun processMotionEvent(event: MotionEvent): Boolean {
        if (!mouseEnabled) return false
        
        val action = event.actionMasked
        val pointerCount = event.pointerCount
        
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                
                touchPoints[pointerId] = TouchPoint(
                    id = pointerId,
                    x = x,
                    y = y,
                    startX = x,
                    startY = y,
                    lastX = x,
                    lastY = y
                )
                
                sendTouchEvent(TouchEvent(MotionEvent.ACTION_DOWN, x, y, pointerId, event.eventTime))
            }
            
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    
                    touchPoints[pointerId]?.let { point ->
                        point.lastX = point.x
                        point.lastY = point.y
                        point.x = x
                        point.y = y
                        
                        val dx = x - point.lastX
                        val dy = y - point.lastY
                        
                        sendMouseDelta(dx, dy)
                    }
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                
                touchPoints.remove(pointerId)
                
                sendTouchEvent(TouchEvent(MotionEvent.ACTION_UP, x, y, pointerId, event.eventTime))
            }
            
            MotionEvent.ACTION_CANCEL -> {
                touchPoints.clear()
                sendTouchEvent(TouchEvent(MotionEvent.ACTION_CANCEL, 0f, 0f, 0, event.eventTime))
            }
        }
        
        return true
    }
    
    fun sendKeyEvent(keyCode: Int, pressed: Boolean) {
        keyStates[keyCode] = pressed
        nativeSendKeyEvent(keyCode, pressed)
    }
    
    fun sendMouseEvent(button: Int, pressed: Boolean, x: Float, y: Float) {
        when (button) {
            0 -> mouseLeftPressed = pressed
            1 -> mouseRightPressed = pressed
            2 -> mouseMiddlePressed = pressed
        }
        nativeSendMouseEvent(button, pressed, x, y)
    }
    
    fun sendMouseDelta(dx: Float, dy: Float) {
        mouseX += dx
        mouseY += dy
        nativeSendMouseDelta(dx, dy)
    }
    
    fun sendTouchEvent(event: TouchEvent) {
        nativeSendTouchEvent(event.action, event.x, event.y, event.pointerId, event.timestamp)
    }
    
    fun setMouseEnabled(enabled: Boolean) {
        mouseEnabled = enabled
    }
    
    fun isKeyPressed(keyCode: Int): Boolean = keyStates[keyCode] ?: false
    
    fun getMousePosition(): Pair<Float, Float> = Pair(mouseX, mouseY)
    
    fun getMouseState(): Triple<Boolean, Boolean, Boolean> = Triple(mouseLeftPressed, mouseRightPressed, mouseMiddlePressed)
    
    fun clearAllInputs() {
        keyStates.clear()
        touchPoints.clear()
        mouseLeftPressed = false
        mouseRightPressed = false
        mouseMiddlePressed = false
    }
    
    private external fun nativeSendKeyEvent(keyCode: Int, pressed: Boolean)
    private external fun nativeSendMouseEvent(button: Int, pressed: Boolean, x: Float, y: Float)
    private external fun nativeSendMouseDelta(dx: Float, dy: Float)
    private external fun nativeSendTouchEvent(action: Int, x: Float, y: Float, pointerId: Int, timestamp: Long)
}
