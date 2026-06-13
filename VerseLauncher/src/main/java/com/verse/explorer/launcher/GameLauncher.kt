package com.verse.explorer.launcher

import android.util.Log
import com.verse.explorer.core.game.GameProfile
import com.verse.explorer.core.java.JavaRuntime
import com.verse.explorer.core.version.MinecraftVersion
import java.io.File

data class LaunchConfig(
    val profile: GameProfile,
    val version: MinecraftVersion,
    val javaRuntime: JavaRuntime,
    val gameDir: File,
    val assetsDir: File,
    val librariesDir: File,
    val nativesDir: File,
    val width: Int = 854,
    val height: Int = 480,
    val maxMemory: Int = 2048,
    val minMemory: Int = 512,
    val fullscreen: Boolean = false
)

data class LaunchResult(
    val success: Boolean,
    val processId: Int = 0,
    val error: String? = null
)

@Deprecated(
    message = "Legacy code using ProcessBuilder, incompatible with Android. Use GameLaunchController → MinecraftLauncher → JVMLoader instead.",
    replaceWith = ReplaceWith("GameLaunchController")
)
class GameLauncher {
    
    fun launch(config: LaunchConfig): LaunchResult {
        Log.e("GameLauncher", "GameLauncher is deprecated and must not be used. Use GameLaunchController → MinecraftLauncher → JVMLoader instead.")
        return LaunchResult(success = false, error = "GameLauncher is deprecated. Use GameLaunchController instead.")
    }

    fun stop() {}

    fun forceStop() {}

    fun isRunning(): Boolean = false

    fun getOutput(): String? = null
}
