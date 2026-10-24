package com.tungsten.fclcore.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.os.postDelayed
import com.mio.data.Renderer
import com.tungsten.fcl.R
import com.tungsten.fclauncher.FCLConfig
import com.tungsten.fclauncher.FCLauncher
import com.tungsten.fclauncher.bridge.FCLBridgeCallback
import com.tungsten.fclcore.util.Logging
import com.tungsten.fclcore.util.io.FileUtils
import java.io.File
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.logging.Level

class ProcessService : Service() {
    companion object {
        const val PROCESS_SERVICE_PORT: Int = 29118

        // 处理器执行串行锁 + UDP 端口计数器。
        // 宿主适配：NeoForge/Forge/OptiFine 的处理器任务可能被 AsyncTaskExecutor
        // 并发调度（如 NeoForge 21.1.1 install_profile 中多个 MCP_DATA 任务），
        // 若多个 runJVMProcess 同时监听同一个固定端口，UDP 退出码报文只会到达其中一个，
        // 另一个 latch 将永久挂起。因此：(1) 所有处理器执行强制串行（持锁期间包含
        // latch.await，天然复现 FCL 原版顺序执行语义）；(2) 每个 runJVMProcess 使用
        // PROCESS_SERVICE_PORT 之后的独立端口，杜绝任何端口竞争。
        private val processorLock = Any()
        private val portCounter = java.util.concurrent.atomic.AtomicInteger(PROCESS_SERVICE_PORT + 1)

        @JvmStatic
        fun getProcessorLock(): Any = processorLock

        @JvmStatic
        fun nextSocketPort(): Int {
            val port = portCounter.getAndIncrement()
            if (portCounter.get() > 45000) portCounter.set(PROCESS_SERVICE_PORT + 1)
            return port
        }
    }

    private var exitPort = PROCESS_SERVICE_PORT

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, buildNotification())
        val command = intent.extras!!.getStringArray("command")
        val java = intent.extras!!.getInt("java")
        exitPort = intent.extras!!.getInt("port", PROCESS_SERVICE_PORT)
        // 处理器 JVM 兼容参数（aarch64）：
        // 与游戏启动处理器（InstallerProcessService 阶段）一致的 aarch64 兼容参数——
        // 1) -XX:TieredStopAtLevel=1：规避 aarch64 C2 JIT bug；
        // 2) -XX:-UseCompressedOops / -XX:-UseCompressedClassPointers：规避 HotSpot aarch64
        //    "Field too big for insn"（assembler_aarch64.hpp guarantee）的压缩引用编码超限问题。
        val fullCommand = arrayOf(
            "-XX:TieredStopAtLevel=1",
            "-XX:-UseCompressedOops",
            "-XX:-UseCompressedClassPointers",
            // 宿主适配：处理器 JVM 崩溃（SIGABRT=退出码 6）时 hs_err 默认写到
            // 内部 cwd（cache/fclauncher，adb 不可读），无法定位根因。
            // 重定向到外部可读目录，崩溃后可直接取回分析。
            "-XX:ErrorFile=/storage/emulated/0/Android/data/com.verse.explorer/files/installer_hs_err_pid%p.log"
        ) + command!!
        val jre = "jre-$java-${com.tungsten.fclauncher.utils.FCLPath.JRE_ARCH}"
        // 诊断汇总：每次处理器执行把命令/退出码/异常追加到外部可读汇总文件
        val diagFile = File(
            Environment.getExternalStorageDirectory(),
            "FCL/log/processor_summary.log"
        )
        fun appendDiag(line: String) {
            try {
                diagFile.parentFile?.mkdirs()
                FileUtils.writeTextWithAppendMode(diagFile, line + "\n")
            } catch (_: Throwable) {
            }
        }
        // 宿主适配（诊断/加固）：处理器 JVM 直接以 java 二进制 + ProcessBuilder 启动，
        // 复现桌面 FCL 原版跑 installertools 处理器的方式；stdout+stderr 合并落盘到
        // 外部可读文件，避免 pojava fc 管道的 stderr 丢失导致退出码 6 无任何日志。
        // JRE 二进制完整性先校验（bin/java + lib/libjli.so 存在），失败回退 FCL API installer。
        val javaExec = File(
            File(
                com.tungsten.fclauncher.utils.FCLPath.JAVA_PATH,
                jre
            ),
            "bin/java"
        )
        val jreRoot = File(
            com.tungsten.fclauncher.utils.FCLPath.JAVA_PATH,
            jre
        )
        val jreLib = File(File(jreRoot, "lib"), "libjli.so")
        if (javaExec.isFile && (jreLib.isFile || jreLib.exists())) {
            try {
                // Android 应用进程 exec(2) 需要可执行位；JRE 解压后可能未设置，
                // 递归对 bin/ 与 lib/ 下文件设置可执行位（与游戏启动 ensureJreExecutable 一致）
                jreRoot.walkTopDown().forEach { f ->
                    if (f.isFile && (f.name == "java" || f.extension == "so" || f.name == "javac")) {
                        f.setExecutable(true, false)
                        f.setReadable(true, false)
                    }
                }
                val logFile = File(
                    Environment.getExternalStorageDirectory(),
                    "FCL/log/processor_${System.currentTimeMillis()}.log"
                )
                logFile.parentFile?.mkdirs()
                appendDiag("== exec path: java=$java jre=$jre cmd=${fullCommand.joinToString(" ")}")
                val proc = ProcessBuilder(
                    listOf(javaExec.absolutePath) + fullCommand.toList()
                )
                    .redirectErrorStream(true)
                    .redirectOutput(logFile)
                    .start()
                val exit = proc.waitFor()
                appendDiag("   exit=$exit log=${logFile.absolutePath}")
                sendCode(exit)
                return START_NOT_STICKY
            } catch (e: Throwable) {
                Logging.LOG.log(Level.WARNING, "ProcessBuilder java 启动失败(" + e.javaClass.simpleName + ": " + e.message + ")，回退 FCLauncher")
                appendDiag("!! exec failed: " + e.javaClass.simpleName + ": " + e.message)
            }
        } else {
            appendDiag("!! exec skipped: javaExec=$javaExec exists=${javaExec.isFile}, jreLib=$jreLib exists=${jreLib.exists()}")
        }
        val config = FCLConfig(
            applicationContext,
            Environment.getExternalStorageDirectory().absolutePath + "/FCL/log",
            com.tungsten.fclauncher.utils.FCLPath.JAVA_PATH + "/" + jre,
            applicationContext.cacheDir.toString() + "/fclauncher",
            Renderer(
                Renderer.ID_GL4ES,
                "GL4ES",
                "",
                "libEGL.so",
                "libgl4es_114.so",
                emptyList()
            ),
            fullCommand
        )
        startProcess(config)
        return START_NOT_STICKY
    }

    private var firstLog = true

    fun startProcess(config: FCLConfig) {
        val bridge = FCLauncher.launchAPIInstaller(config)
        val callback: FCLBridgeCallback = object : FCLBridgeCallback {
            override fun onCursorModeChange(mode: Int) {
                // Ignore
            }

            override fun onHitResultTypeChange(type: Int) {
                // Ignore
            }

            override fun onLog(log: String?) {
                try {
                    if (firstLog) {
                        FileUtils.writeText(File(bridge.logPath), log)
                        firstLog = false
                    } else {
                        FileUtils.writeTextWithAppendMode(File(bridge.logPath), log)
                    }
                } catch (e: IOException) {
                    Logging.LOG.log(Level.WARNING, "Can't log game log to target file", e.message)
                }
            }

            override fun onExit(code: Int) {
                try {
                    val df = File(Environment.getExternalStorageDirectory(), "FCL/log/processor_summary.log")
                    df.parentFile?.mkdirs()
                    FileUtils.writeTextWithAppendMode(df, "   [fcl path] exit=$code\n")
                } catch (_: Throwable) {
                }
                sendCode(code)
            }
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(1000) {
            bridge.execute(null, callback)
        }
    }

    private fun sendCode(code: Int) {
        try {
            val socket = DatagramSocket()
            socket.connect(InetSocketAddress("127.0.0.1", exitPort))
            val data = (code.toString() + "").toByteArray()
            val packet = DatagramPacket(data, data.size)
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "fcl_process",
            "FCL Process",
            NotificationManager.IMPORTANCE_HIGH
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "fcl_process")
            .setContentTitle(getString(R.string.notification_title))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
}
