package com.verse.explorer.core.data.java

import android.content.Context
import com.verse.explorer.core.java.JavaRuntime
import com.verse.explorer.core.java.JavaRuntimeManager

object JavaRuntimeRepository {

    fun loadInstalledRuntimes(context: Context): List<JavaRuntime> {
        return try {
            JavaRuntimeManager.getInstalledRuntimes(context)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
