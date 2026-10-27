package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * native 日志路径策略测试。
 *
 * Android 10+ 不能写 /sdcard 根目录，日志必须落在应用专属外部目录；
 * 没外部目录时回退游戏目录，方便无 adb 时取日志。
 */
class LaunchLogPolicyTest {

    @Test
    fun noExternalDir_returnsNull() {
        assertNull(LaunchLogPolicy.diagDir(null))
    }

    @Test
    fun externalDir_usesVerseExplorerXLogs() {
        assertEquals(
            File("/storage/emulated/0/Android/data/pkg/files/VerseExplorerX/logs"),
            LaunchLogPolicy.diagDir(
                File("/storage/emulated/0/Android/data/pkg/files")
            )
        )
    }

    @Test
    fun noDiagDir_fallsBackToGameLogs() {
        assertEquals(
            File("/game/logs"),
            LaunchLogPolicy.baseLogDir(null, File("/game"))
        )
    }

    @Test
    fun nativeLogNames_areStable() {
        val logs = LaunchLogPolicy.nativeLogs(File("/logs"))
        assertEquals(File("/logs/verse_native.log"), logs.native)
        assertEquals(File("/logs/verse_jvm_out.log"), logs.jvmOut)
        assertEquals(File("/logs/verse_glfw.log"), logs.glfw)
        assertEquals(File("/logs/verse_sdl.log"), logs.sdl)
    }
}
