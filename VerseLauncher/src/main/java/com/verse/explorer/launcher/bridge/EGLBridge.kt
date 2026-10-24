package com.verse.explorer.launcher.bridge

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface

class EGLBridge(private val context: Context) {
    
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var eglConfig: EGLConfig? = null
    
    private var isInitialized = false
    private var width = 0
    private var height = 0
    
    companion object {
        private const val EGL_OPENGL_ES2_BIT = 4
        private const val EGL_OPENGL_ES3_BIT = 0x0040
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
    }
    
    fun initialize(surface: Surface, width: Int, height: Int): Boolean {
        if (isInitialized) return true
        
        this.width = width
        this.height = height
        
        try {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == null || eglDisplay == EGL14.EGL_NO_DISPLAY) {
                println("Failed to get EGL display")
                return false
            }
            
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                println("Failed to initialize EGL")
                return false
            }
            
            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 24,
                EGL14.EGL_STENCIL_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
            )
            
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                println("Failed to choose EGL config")
                return false
            }
            
            eglConfig = configs[0]
            
            val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
            
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
                println("Failed to create EGL context")
                return false
            }
            
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
            if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                println("Failed to create EGL surface")
                return false
            }
            
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                println("Failed to make EGL context current")
                return false
            }
            
            isInitialized = true
            println("EGL Bridge initialized successfully (${width}x${height})")
            return true
            
        } catch (e: Exception) {
            println("Error initializing EGL Bridge: ${e.message}")
            shutdown()
            return false
        }
    }
    
    fun shutdown() {
        if (!isInitialized) return
        
        try {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            
            eglSurface?.let {
                EGL14.eglDestroySurface(eglDisplay, it)
            }
            
            eglContext?.let {
                EGL14.eglDestroyContext(eglDisplay, it)
            }
            
            eglDisplay?.let {
                EGL14.eglTerminate(it)
            }
        } catch (e: Exception) {
            println("Error shutting down EGL Bridge: ${e.message}")
        }
        
        eglDisplay = null
        eglContext = null
        eglSurface = null
        eglConfig = null
        isInitialized = false
    }
    
    fun swapBuffers(): Boolean {
        if (!isInitialized) return false
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }
    
    fun makeCurrent(): Boolean {
        if (!isInitialized) return false
        return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }
    
    fun setVSync(enabled: Boolean) {
        if (!isInitialized) return
        EGL14.eglSwapInterval(eglDisplay, if (enabled) 1 else 0)
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    fun getSize(): Pair<Int, Int> = Pair(width, height)
}
