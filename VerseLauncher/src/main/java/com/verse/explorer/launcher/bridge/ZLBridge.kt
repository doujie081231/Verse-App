package com.verse.explorer.launcher.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Surface
import com.verse.explorer.core.java.JavaManager
import com.verse.explorer.jvmlauncher.JvmLauncherAPI
import com.verse.explorer.jvmlauncher.JvmExitCallback
import com.verse.explorer.launcher.keycodes.LwjglGlfwKeycode
import java.io.Serializable

class ZLBridge(private val context: Context) : Serializable, JvmExitCallback {

    companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720

        const val CursorEnabled = 1
        const val CursorDisabled = 0

        private const val TAG = "ZLBridge"
        private var lastLdLibraryPath: String? = null

        @JvmStatic
        var FORCE_RESOLUTION = false
        @JvmStatic
        var FORCE_RESOLUTION_WIDTH = 1280
        @JvmStatic
        var FORCE_RESOLUTION_HEIGHT = 720

        @JvmStatic
        fun getFps(): Int = CallbackBridge.getFps()
    }

    private var callback: ZLBridgeCallback? = null
    private var scaleFactor = 1.0
    private var gameDir: String? = null
    private var logPath: String? = null
    private var renderer: String = "opengles3"
    private var javaVersion: String = "17"
    private var surface: Surface? = null
    private var surfaceDestroyed = false
    private var handler: android.os.Handler? = null
    private var isGrabbing = false

    fun execute(surface: Surface, callback: ZLBridgeCallback) {
        this.handler = android.os.Handler(android.os.Looper.getMainLooper())
        this.callback = callback
        this.surface = surface
        this.surfaceDestroyed = false

        callback.onLog("Starting ZLBridge execution\n")

        val errorCode = JvmLauncherAPI.nativeRedirectStdio(logPath ?: "/dev/null")
        if (errorCode != 0) {
            callback.onLog("Warning: redirectStdio failed with code $errorCode\n")
        }

        if (surface.isValid) {
            JvmLauncherAPI.nativeSetupBridgeWindow(surface)
        }

        callback.onLog("Bridge window ready\n")
    }

    fun setupEnvironment(javaHome: String, gameDir: String, nativesDir: String, nativeLibDir: String) {
        val javaHomeFile = java.io.File(javaHome)
        val javaLibSubdir = JavaManager.getJavaLibSubdir(javaHomeFile)
        val javaLibDir = javaHome + javaLibSubdir
        val runtimeDir = context.getDir("runtime", 0).absolutePath
        val modRuntimeDir = context.getDir("runtime_mod", 0).absolutePath
        val jnaDir = "$runtimeDir/jna"

        val isJDK8 = javaHomeFile.listFiles()?.any { it.name == "jre" } == true
        val jvmLibSubdir = if (java.io.File("$javaLibDir/server/libjvm.so").exists()) "/server" else "/client"
        val jliLibDir = if (java.io.File("$javaLibDir/jli/libjli.so").exists()) "$javaLibDir/jli" else javaLibDir

        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        val libSuffix = if (is64Bit) "lib64" else "lib"

        val bridgePaths = mutableListOf(
            javaLibDir,
            jliLibDir,
            "$javaLibDir$jvmLibSubdir"
        )
        if (isJDK8) {
            bridgePaths.add("$javaHome/jre$javaLibSubdir$jvmLibSubdir")
            bridgePaths.add("$javaHome/jre$javaLibSubdir")
        }
        bridgePaths.addAll(listOf(
            "/system/$libSuffix",
            "/vendor/$libSuffix",
            "/vendor/$libSuffix/hw",
            "/system_ext/$libSuffix",
            jnaDir,
            modRuntimeDir,
            nativeLibDir,
            nativesDir
        ))

        val existingLdPath = lastLdLibraryPath ?: System.getenv("LD_LIBRARY_PATH") ?: ""
        val existingParts = if (existingLdPath.isNotEmpty()) existingLdPath.split(":").toMutableList() else mutableListOf()
        val newPaths = bridgePaths.filter { it.isNotEmpty() && it !in existingParts }
        val mergedPath = (existingParts + newPaths).joinToString(":")

        lastLdLibraryPath = mergedPath

        android.util.Log.d("ZLBridge", "LD_LIBRARY_PATH: $mergedPath")
        android.util.Log.d("ZLBridge", "Java lib subdir: $javaLibSubdir, javaLibDir: $javaLibDir")

        JvmLauncherAPI.nativeSetLdLibraryPath(mergedPath)
        JvmLauncherAPI.nativeSetenv("JAVA_HOME", javaHome)
        JvmLauncherAPI.nativeSetenv("HOME", gameDir)
        val tmpDir = javaHomeFile.parentFile?.parentFile
        val tmpPath = if (tmpDir != null) "${tmpDir.absolutePath}/cache" else "${gameDir ?: "/data/local/tmp"}/cache"
        JvmLauncherAPI.nativeSetenv("TMPDIR", tmpPath)
        JvmLauncherAPI.nativeSetenv("POJAV_NATIVEDIR", nativeLibDir)
        JvmLauncherAPI.nativeSetenv("FCL_NATIVEDIR", nativeLibDir)
        JvmLauncherAPI.nativeSetenv("PATH", "$javaHome/bin:${System.getenv("PATH") ?: ""}")
        JvmLauncherAPI.nativeSetenv("LD_LIBRARY_PATH", mergedPath)
        JvmLauncherAPI.nativeSetenv("FORCE_VSYNC", "false")
        JvmLauncherAPI.nativeSetenv("MOD_ANDROID_RUNTIME", modRuntimeDir)
        JvmLauncherAPI.nativeSetenv("FCL_VERSION_CODE", "1")

        android.util.Log.d("ZLBridge", "Environment setup complete (FCL-style), JAVA_HOME=$javaHome")
    }

    fun launchJVM(jrePath: String, args: Array<String>): Int {
        Log.i(TAG, "=== ZLBridge.launchJVM V2.6 (2026-05-15) ===")
        Log.i(TAG, "JRE path: $jrePath")
        Log.i(TAG, "Args count: ${args.size}")
        
        fixJrePermissionsAndConfig(jrePath)
        
        if (!gameDir.isNullOrEmpty()) {
            JvmLauncherAPI.nativeChdir(gameDir!!)
            Log.i(TAG, "chdir to: $gameDir")
        }
        
        val tmpDir = java.io.File(gameDir ?: "", "cache/tmp")
        tmpDir.mkdirs()
        
        JvmLauncherAPI.nativeSetupExitTrap(this)
        val result = JvmLauncherAPI.nativeLaunchJVM(jrePath, args)
        if (result != 0) {
            Log.e(TAG, "nativeLaunchJVM failed with code: $result")
            callback?.onLog("JVM launch failed with code: $result\n")
        }
        return result
    }
    
    private fun fixJrePermissionsAndConfig(jrePath: String) {
        val jreDir = java.io.File(jrePath)
        if (!jreDir.exists()) {
            Log.e(TAG, "JRE directory does not exist: $jrePath")
            return
        }
        
        Log.i(TAG, "Fixing JRE permissions for $jrePath")
        
        try {
            val binDir = java.io.File(jreDir, "bin")
            if (binDir.exists()) {
                binDir.walkTopDown().forEach { file ->
                    try {
                        file.setExecutable(true, false)
                        file.setReadable(true, false)
                    } catch (_: Exception) {}
                }
            }
            
            val libDir = java.io.File(jreDir, "lib")
            if (libDir.exists()) {
                libDir.walkTopDown().forEach { file ->
                    try {
                        if (file.isDirectory) {
                            file.setExecutable(true, false)
                        } else {
                            file.setReadable(true, false)
                            if (file.name.endsWith(".so")) {
                                file.setExecutable(true, false)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            
            val jvmCfg = java.io.File(jreDir, "lib/jvm.cfg")
            if (!jvmCfg.exists()) {
                Log.w(TAG, "jvm.cfg missing, creating: ${jvmCfg.absolutePath}")
                try {
                    jvmCfg.parentFile?.mkdirs()
                    jvmCfg.writeText("-server KNOWN\n-client IGNORE\n")
                    jvmCfg.setReadable(true, false)
                    Log.i(TAG, "Created jvm.cfg successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create jvm.cfg: ${e.message}")
                }
            } else {
                jvmCfg.setReadable(true, false)
                Log.i(TAG, "jvm.cfg exists at ${jvmCfg.absolutePath}")
            }
            
            val modules = java.io.File(jreDir, "lib/modules")
            if (modules.exists()) {
                modules.setReadable(true, false)
                Log.i(TAG, "lib/modules exists, size=${modules.length()}, readable=${modules.canRead()}")
            } else {
                Log.e(TAG, "lib/modules NOT FOUND at ${modules.absolutePath}")
            }
            
            val rtJar = java.io.File(jreDir, "lib/rt.jar")
            if (rtJar.exists()) {
                rtJar.setReadable(true, false)
                Log.i(TAG, "lib/rt.jar exists, size=${rtJar.length()}")
            }
            
            val release = java.io.File(jreDir, "release")
            if (release.exists()) {
                release.setReadable(true, false)
            }
            
            Log.i(TAG, "JRE permissions fixed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fix JRE permissions: ${e.message}")
        }
    }

    fun isJVMRunning(): Boolean = JvmLauncherAPI.nativeIsJVMRunning()
    fun destroyJVM() = JvmLauncherAPI.nativeDestroyJVM()
    fun getExitCode(): Int = JvmLauncherAPI.nativeGetExitCode()
    fun getPid(): Int = JvmLauncherAPI.nativeGetPid()

    fun release() {
        callback = null
        try {
            JvmLauncherAPI.nativeReleaseExitCallback()
        } catch (e: Throwable) {
            Log.e(TAG, "nativeReleaseExitCallback failed: ${e.message}")
        }
        try {
            JvmLauncherAPI.nativeDestroyJVM()
        } catch (e: Throwable) {
            Log.e(TAG, "nativeDestroyJVM failed: ${e.message}")
        }
    }

    override fun onExit(code: Int) {
        if (callback == null) return
        handler?.post {
            callback?.onLog("JVM exited with code: $code\n")
            callback?.onExit(code)
        }
    }

    fun pushEventMouseButton(button: Int, press: Boolean) {
        when (button) {
            LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_4 -> { if (press) pushEventScroll(0.0, 1.0) }
            LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_5 -> { if (press) pushEventScroll(0.0, -1.0) }
            else -> CallbackBridge.sendMouseButton(button, press)
        }
    }

    fun pushEventPointer(x: Int, y: Int) = CallbackBridge.sendCursorPos(x.toDouble(), y.toDouble())
    fun pushEventPointer(x: Float, y: Float) = CallbackBridge.sendCursorPos(x.toDouble(), y.toDouble())

    fun pushEventKey(keyCode: Int, keyChar: Int, press: Boolean) {
        CallbackBridge.setModifiers(keyCode, press)
        CallbackBridge.sendKeycode(keyCode, keyChar.toChar(), 0, CallbackBridge.getCurrentMods(), press)
    }

    fun pushEventChar(keyChar: Char) = CallbackBridge.sendChar(keyChar, 0)
    fun pushEventWindow(width: Int, height: Int) = CallbackBridge.sendUpdateWindowSize(width, height)
    private fun pushEventScroll(xOffset: Double, yOffset: Double) = CallbackBridge.sendScroll(xOffset, yOffset)

    fun setCursorMode(mode: Int) {
        isGrabbing = mode == CursorDisabled
        callback?.onCursorModeChange(mode)
    }

    fun isGrabbing(): Boolean = isGrabbing

    fun setPrimaryClipString(string: String) {
        handler?.post {
            try {
                val ctx = ZLBridgeContext.context ?: return@post
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Verse Clipboard", string))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set clipboard", e)
            }
        }
    }

    fun getPrimaryClipString(): String? {
        return try {
            val ctx = ZLBridgeContext.context ?: return null
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) return null
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) { null }
    }

    fun setScaleFactor(scale: Double) { this.scaleFactor = scale }
    fun getScaleFactor(): Double = scaleFactor
    fun setGameDir(gameDir: String?) { this.gameDir = gameDir }
    fun getGameDir(): String? = gameDir
    fun setRenderer(renderer: String) { this.renderer = renderer }
    fun getRenderer(): String = renderer
    fun setJava(javaVersion: String) { this.javaVersion = javaVersion }
    fun getJava(): String = javaVersion
    fun setLogPath(path: String) { this.logPath = path }
    fun getLogPath(): String = logPath ?: "/dev/null"
    fun setSurfaceDestroyed(destroyed: Boolean) { this.surfaceDestroyed = destroyed }
    fun isSurfaceDestroyed(): Boolean = surfaceDestroyed
    fun getCallback(): ZLBridgeCallback? = callback
    fun receiveLog(log: String) { callback?.onLog(log) }
}

interface ZLBridgeCallback {
    fun onLog(log: String)
    fun onExit(code: Int)
    fun onCursorModeChange(mode: Int)
    fun onGraphicOutput()
}

object ZLBridgeContext {
    @JvmStatic
    var context: Context? = null
}
