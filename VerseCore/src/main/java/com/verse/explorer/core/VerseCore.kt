package com.verse.explorer.core

import android.content.Context
import com.verse.explorer.core.context.Contexts

object VerseCore {
    const val VERSION_NAME = "2.0.0"
    const val VERSION_CODE = 2
    const val LAUNCHER_NAME = "Verse Explorer X"

    var isInitialized = false
        private set

    fun initialize(context: Context) {
        if (isInitialized) return
        Contexts.initialize(context)
        isInitialized = true
    }
}
