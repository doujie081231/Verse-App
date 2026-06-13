package com.verse.explorer.launcher.bridge

import android.content.Context
import android.view.Surface

object LWJGLBridge {
    
    private var isInitialized = false
    private var currentSurface: Surface? = null
    
    init {
        try {
            System.loadLibrary("lwjgl_bridge")
            println("LWJGL Bridge loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            println("LWJGL Bridge library not found, using fallback")
        }
    }
    
    fun initialize(surface: Surface, width: Int, height: Int): Boolean {
        if (isInitialized) return true
        
        currentSurface = surface
        
        return try {
            nativeInitialize(surface, width, height)
            isInitialized = true
            true
        } catch (e: Exception) {
            println("Failed to initialize LWJGL Bridge: ${e.message}")
            false
        }
    }
    
    fun shutdown() {
        if (!isInitialized) return
        
        try {
            nativeShutdown()
        } catch (e: Exception) {
            println("Error shutting down LWJGL Bridge: ${e.message}")
        }
        
        isInitialized = false
        currentSurface = null
    }
    
    fun swapBuffers() {
        if (!isInitialized) return
        try {
            nativeSwapBuffers()
        } catch (e: Exception) {
        }
    }
    
    fun pollEvents() {
        if (!isInitialized) return
        try {
            nativePollEvents()
        } catch (e: Exception) {
        }
    }
    
    fun setWindowTitle(title: String) {
        if (!isInitialized) return
        try {
            nativeSetWindowTitle(title)
        } catch (e: Exception) {
        }
    }
    
    fun getWindowSize(): Pair<Int, Int> {
        if (!isInitialized) return Pair(0, 0)
        return try {
            val size = nativeGetWindowSize()
            Pair(size[0], size[1])
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    private external fun nativeInitialize(surface: Surface, width: Int, height: Int)
    private external fun nativeShutdown()
    private external fun nativeSwapBuffers()
    private external fun nativePollEvents()
    private external fun nativeSetWindowTitle(title: String)
    private external fun nativeGetWindowSize(): IntArray
}
