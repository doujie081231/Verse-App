package com.tungsten.fclauncher.plugins

import com.mio.data.Renderer

object DriverPlugin {

    @JvmStatic
    fun getSelected(): Renderer {
        return Renderer.GLES
    }
}
