package com.verse.explorer.core.log

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 下载诊断日志：模组/版本/加载器/整合包等下载链路的关键事件
 * （开始、阶段、成功、失败与错误原因）统一追加写入 download.log，
 * 出现下载问题时可直接取该文件定位。
 *
 * 文件位置：
 * - 内部: filesDir/VerseExplorerX/logs/download.log
 * - 外部镜像: getExternalFilesDir/VerseExplorerX/logs/download.log（不 root 即可取回）
 *
 * 行格式：
 * [yyyy-MM-dd HH:mm:ss] [分类] 内容
 */
object DownloadLog {

    private var writer: PrintWriter? = null
    private var mirrorWriter: PrintWriter? = null
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /** 应用启动时挂载日志文件（幂等） */
    @Synchronized
    fun init(context: Context) {
        if (writer != null) return
        try {
            val app = context.applicationContext
            val internal = File(File(app.filesDir, "VerseExplorerX"), "logs").apply { mkdirs() }
            writer = PrintWriter(FileWriter(File(internal, "download.log"), true), true)
            val external = app.getExternalFilesDir(null)
            if (external != null) {
                val mirrorDir = File(File(external, "VerseExplorerX"), "logs").apply { mkdirs() }
                mirrorWriter = PrintWriter(FileWriter(File(mirrorDir, "download.log"), true), true)
            }
        } catch (e: Exception) {
            Log.e("DownloadLog", "init failed", e)
        }
    }

    /** 追加一行日志（内部 + 外部镜像同步写） */
    @Synchronized
    fun write(domain: String, message: String) {
        val line = "[${format.format(Date())}] [$domain] $message"
        Log.i("DownloadLog", line)
        try { writer?.println(line) } catch (_: Exception) {}
        try { mirrorWriter?.println(line) } catch (_: Exception) {}
    }

    /** 模组下载（资源详情页提交的 mod 下载） */
    fun mods(message: String) = write("mods", message)

    /** 版本/游戏文件下载（版本安装链路） */
    fun version(message: String) = write("version", message)

    /** 加载器下载（Forge/Fabric/NeoForge/OptiFine/Quilt 安装链路） */
    fun loader(message: String) = write("loader", message)

    /** 整合包下载与安装 */
    fun modpack(message: String) = write("modpack", message)
}

