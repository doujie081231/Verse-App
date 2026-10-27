package com.verse.explorer.app.game

import java.io.File

/**
 * 启动诊断日志路径。
 *
 * 外部可访问目录优先（进程被杀后仍可取回），否则回退游戏目录下的 logs。
 */
internal object LaunchLogPolicy {

    fun diagDir(externalFilesDir: File?): File? =
        externalFilesDir?.let { File(File(it, "VerseExplorerX"), "logs") }

    fun baseLogDir(diagDir: File?, gameDir: File): File =
        diagDir ?: File(gameDir, "logs")

    data class NativeLogs(
        val native: File,
        val jvmOut: File,
        val glfw: File,
        val sdl: File
    )

    fun nativeLogs(baseDir: File): NativeLogs = NativeLogs(
        native = File(baseDir, "verse_native.log"),
        jvmOut = File(baseDir, "verse_jvm_out.log"),
        glfw = File(baseDir, "verse_glfw.log"),
        sdl = File(baseDir, "verse_sdl.log")
    )
}
