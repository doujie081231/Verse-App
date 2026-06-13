package com.verse.explorer.launcher.launch

import android.content.Context
import android.util.Log
import java.io.File

class JVMLoader(private val context: Context) {

    companion object {
        private const val TAG = "JVMLoader"
        private var libraryLoaded = false

        init {
            try {
                System.loadLibrary("pojavexec")
                libraryLoaded = true
                Log.d(TAG, "pojavexec native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "CRITICAL: Failed to load pojavexec native library!", e)
                libraryLoaded = false
            }
        }
    }

    private var exitCallback: JVMLoaderCallback? = null

    private fun logDirectoryContents(dir: File, prefix: String, maxDepth: Int = 3) {
        if (!dir.exists() || maxDepth <= 0) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                Log.d(TAG, "$prefix[D] ${file.name}/")
                logDirectoryContents(file, "$prefix  ", maxDepth - 1)
            } else {
                Log.d(TAG, "$prefix[F] ${file.name} (${file.length()} bytes)")
            }
        }
    }

    private fun findLibjliRecursively(dir: File, maxDepth: Int = 4): File? {
        if (!dir.exists() || maxDepth <= 0) return null
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name == "libjli.so") {
                return file
            }
            if (file.isDirectory) {
                val found = findLibjliRecursively(file, maxDepth - 1)
                if (found != null) return found
            }
        }
        return null
    }

    fun launchJVM(
        jrePath: String,
        args: Array<String>
    ): Int {
        if (!libraryLoaded) {
            Log.e(TAG, "Cannot launch JVM: native library not loaded!")
            return -1
        }

        Log.d(TAG, "=== Launching JVM via JLI_Launch ===")
        Log.d(TAG, "JRE: $jrePath")
        Log.d(TAG, "Total args: ${args.size}")

        val jreDir = File(jrePath)
        if (!jreDir.exists()) {
            Log.e(TAG, "JRE directory does not exist: $jrePath")
            return -1
        }

        Log.d(TAG, "=== JRE directory contents ===")
        logDirectoryContents(jreDir, "")

        val libjliPaths = listOf(
            File(jrePath, "lib/libjli.so"),
            File(jrePath, "lib/jli/libjli.so"),
            File(jrePath, "lib/aarch64/jli/libjli.so"),
            File(jrePath, "lib/amd64/jli/libjli.so"),
            File(jrePath, "lib/arm/jli/libjli.so"),
            File(jrePath, "lib/i386/jli/libjli.so")
        )
        val libjliFromPaths = libjliPaths.firstOrNull { it.exists() }
        val libjliRecursive = findLibjliRecursively(jreDir)
        val libjliExists = libjliFromPaths != null || libjliRecursive != null

        if (!libjliExists) {
            Log.e(TAG, "libjli.so not found in any expected path under: $jrePath")
            libjliPaths.forEach { Log.e(TAG, "  Checked: ${it.absolutePath} (exists=${it.exists()})") }
            Log.e(TAG, "Recursive search also found nothing!")
            return -1
        }

        val foundLibjli = libjliFromPaths ?: libjliRecursive
        Log.d(TAG, "Found libjli.so at: ${foundLibjli?.absolutePath}")

        val libjvmPaths = listOf(
            File(jrePath, "lib/server/libjvm.so"),
            File(jrePath, "lib/aarch64/server/libjvm.so"),
            File(jrePath, "lib/amd64/server/libjvm.so"),
            File(jrePath, "lib/arm/server/libjvm.so")
        )
        val libjvmExists = libjvmPaths.any { it.exists() }
        if (!libjvmExists) {
            Log.w(TAG, "libjvm.so not found in any expected path under: $jrePath")
            libjvmPaths.forEach { Log.w(TAG, "  Checked: ${it.absolutePath} (exists=${it.exists()})") }
        } else {
            val foundLibjvm = libjvmPaths.first { it.exists() }
            Log.d(TAG, "Found libjvm.so at: ${foundLibjvm.absolutePath}")
        }

        val javaBin = File(jrePath, "bin/java")
        val javaSh = File(jrePath, "bin/java.sh")
        Log.d(TAG, "bin/java exists: ${javaBin.exists()}, bin/java.sh exists: ${javaSh.exists()}")

        if (!javaBin.exists() && !javaSh.exists()) {
            Log.e(TAG, "No java executable found in $jrePath/bin/")
            return -1
        }

        return try {
            val result = nativeLaunchJVM(jrePath, args)
            if (result == 0) {
                Log.d(TAG, "JVM thread started successfully")
            } else {
                Log.e(TAG, "JVM launch failed with code: $result")
            }
            result
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "CRITICAL: nativeLaunchJVM method not found!", e)
            -1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch JVM", e)
            -1
        }
    }

    fun setExitCallback(callback: JVMLoaderCallback) {
        this.exitCallback = callback
        if (!libraryLoaded) return
        try {
            nativeSetExitCallback(callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set exit callback", e)
        }
    }

    fun destroyJVM() {
        if (!libraryLoaded) return
        try {
            nativeDestroyJVM()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy JVM", e)
        }
    }

    fun isJVMRunning(): Boolean {
        if (!libraryLoaded) return false
        return try {
            nativeIsJVMRunning()
        } catch (e: Exception) {
            false
        }
    }

    fun getExitCode(): Int {
        if (!libraryLoaded) return -1
        return try {
            nativeGetExitCode()
        } catch (e: Exception) {
            -1
        }
    }

    fun getPid(): Int {
        if (!libraryLoaded) return -1
        return try {
            nativeGetPid()
        } catch (e: Exception) {
            -1
        }
    }

    private external fun nativeLaunchJVM(
        jrePath: String,
        args: Array<String>
    ): Int

    private external fun nativeDestroyJVM()
    private external fun nativeIsJVMRunning(): Boolean
    private external fun nativeSetExitCallback(callback: JVMLoaderCallback)
    private external fun nativeGetExitCode(): Int
    private external fun nativeGetPid(): Int
}

interface JVMLoaderCallback {
    fun onExit(code: Int)
}
