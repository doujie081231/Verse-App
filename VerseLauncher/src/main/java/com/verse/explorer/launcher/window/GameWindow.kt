package com.verse.explorer.launcher.window

import android.content.Context
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.verse.explorer.launcher.bridge.EGLBridge
import com.verse.explorer.launcher.bridge.InputBridge
import com.verse.explorer.launcher.bridge.LWJGLBridge
import com.verse.explorer.launcher.controller.TouchController
import com.verse.explorer.launcher.renderer.RendererConfig
import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.launcher.renderer.RendererType

class GameWindow(
    private val context: Context,
    private val surfaceView: SurfaceView
) : SurfaceHolder.Callback {
    
    private var eglBridge: EGLBridge? = null
    private var inputBridge: InputBridge? = null
    private var touchController: TouchController? = null
    
    private var isRunning = false
    private var width = 0
    private var height = 0
    
    private var onReady: (() -> Unit)? = null
    private var onResize: ((Int, Int) -> Unit)? = null
    private var onRender: (() -> Unit)? = null
    
    init {
        surfaceView.holder.addCallback(this)
        surfaceView.setKeepScreenOn(true)
        
        eglBridge = EGLBridge(context)
        inputBridge = InputBridge(context)
        touchController = TouchController(context)
    }
    
    fun setOnReadyListener(listener: () -> Unit) {
        onReady = listener
    }
    
    fun setOnResizeListener(listener: (Int, Int) -> Unit) {
        onResize = listener
    }
    
    fun setOnRenderListener(listener: () -> Unit) {
        onRender = listener
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        println("Surface created")
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        this.width = width
        this.height = height
        
        println("Surface changed: ${width}x${height}")
        
        if (!isRunning) {
            initialize(holder.surface, width, height)
            isRunning = true
            onReady?.invoke()
        } else {
            onResize?.invoke(width, height)
        }
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        println("Surface destroyed")
        isRunning = false
        shutdown()
    }
    
    private fun initialize(surface: Surface, width: Int, height: Int): Boolean {
        val config = RendererConfig(
            type = RendererType.GL4ES,
            width = width,
            height = height
        )
        
        RendererManager.initializeRenderer(surface, config)
        
        val eglSuccess = eglBridge?.initialize(surface, width, height) ?: false
        if (!eglSuccess) {
            println("Failed to initialize EGL")
            return false
        }
        
        LWJGLBridge.initialize(surface, width, height)
        
        setupTouchController()
        
        return true
    }
    
    private fun setupTouchController() {
        touchController?.loadLayout("Default")
        
        touchController?.setButtonCallback { keyCode, pressed ->
            inputBridge?.sendKeyEvent(keyCode, pressed)
        }
        
        touchController?.setStickCallback { id, x, y ->
            when (id) {
                "look" -> {
                    val sensitivity = 2.0f
                    inputBridge?.sendMouseDelta(x * sensitivity, y * sensitivity)
                }
            }
        }
        
        touchController?.setMouseCallback { dx, dy ->
            inputBridge?.sendMouseDelta(dx, dy)
        }
    }
    
    fun shutdown() {
        LWJGLBridge.shutdown()
        eglBridge?.shutdown()
        RendererManager.cleanup()
    }
    
    fun swapBuffers() {
        eglBridge?.swapBuffers()
    }
    
    fun makeCurrent(): Boolean {
        return eglBridge?.makeCurrent() ?: false
    }
    
    fun handleTouchEvent(event: MotionEvent) {
        touchController?.onTouchEvent(event, width, height)
    }
    
    fun getWidth(): Int = width
    fun getHeight(): Int = height
    fun isRunning(): Boolean = isRunning
    
    fun getTouchController(): TouchController? = touchController
    fun getInputBridge(): InputBridge? = inputBridge
}
