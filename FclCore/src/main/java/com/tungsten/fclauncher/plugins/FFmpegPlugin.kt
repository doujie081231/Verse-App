package com.tungsten.fclauncher.plugins

import android.content.Context

object FFmpegPlugin {

    @JvmField
    var isAvailable = false

    @JvmField
    var libraryPath: String = ""

    @JvmStatic
    fun discover(context: Context) {
        // Simplified: FFmpeg plugin discovery skipped
        isAvailable = false
        libraryPath = ""
    }
}
