package com.verse.explorer.launcher.renderer

import android.view.Surface
import android.util.Log

data class RendererConfig(
    val type: RendererType,
    val width: Int = 1280,
    val height: Int = 720
)

enum class RendererType(val displayName: String, val glLibName: String) {
    ZINK("Zink (Vulkan)", "libOSMesa_8.so"),
    VIRGL("VirGL (OpenGL)", "libOSMesa.so"),
    GL4ES("GL4ES (OpenGL 2)", "libgl4es_12.so"),
    MG("Mobile GL (Custom)", "libMGDex.so"),
    NGGL4ES("ng-GL4ES", "libgl4es_12.so"),
    VGPU("VGPU (OpenGL)", "libvgpu.so"),
    FREEDRENO("Freedreno (Turnip)", "libOSMesa_8.so")
}

class RendererManager {
    companion object {
        private const val TAG = "RendererManager"

        fun initializeRenderer(surface: Surface, config: RendererConfig) {
            Log.d(TAG, "Renderer ${config.type.displayName} initialized via environment variables")
        }

        fun cleanup() {
            Log.d(TAG, "Renderer cleanup complete")
        }

        fun isRendererAvailable(type: RendererType): Boolean {
            return true
        }

        fun getGlLibName(type: RendererType): String {
            return type.glLibName
        }
    }
}
