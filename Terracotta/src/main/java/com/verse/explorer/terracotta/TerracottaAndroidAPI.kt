package com.verse.explorer.terracotta

import android.content.Context

object TerracottaAndroidAPI {

    init {
        System.loadLibrary("terracotta")
    }

    @JvmStatic
    fun initialize(context: Context) {
    }

    @JvmStatic
    external fun nativeSetenv(key: String, value: String)

    @JvmStatic
    external fun nativeChdir(path: String): Int

    @JvmStatic
    external fun nativeDlopen(path: String): Long

    @JvmStatic
    external fun nativeRedirectStdio(path: String): Int

    @JvmStatic
    external fun nativeSetLdLibraryPath(path: String)

    @JvmStatic
    external fun nativeSetLogPath(path: String)

    @JvmStatic
    external fun nativeLaunchJVM(jrePath: String, args: Array<String>): Int

    @JvmStatic
    external fun nativeIsJVMRunning(): Boolean

    @JvmStatic
    external fun nativeDestroyJVM()

    @JvmStatic
    external fun nativeGetExitCode(): Int

    @JvmStatic
    external fun nativeGetPid(): Int

    @JvmStatic
    external fun nativeSetupExitTrap(callback: TerracottaExitCallback)

    @JvmStatic
    external fun nativeReleaseExitCallback()

    @JvmStatic
    external fun nativeSetupBridgeWindow(surface: android.view.Surface)

    @JvmStatic
    external fun nativeSendData(isAndroid: Boolean, type: Int, i1: Int, i2: Int, i3: Int, i4: Int)
}

interface TerracottaExitCallback {
    fun onExit(code: Int)
}
