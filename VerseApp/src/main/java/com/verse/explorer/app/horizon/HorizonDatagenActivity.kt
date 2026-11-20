package com.verse.explorer.app.horizon

import android.app.Activity
import android.os.Bundle
import com.verse.explorer.jvmlauncher.JvmLauncherAPI
import java.io.File

/**
 * 在独立进程（:datagen）里跑 Minecraft 官方数据生成器。
 *
 * 为什么必须独立进程：JVM 一个进程只能创建一次，游戏运行时也会建 JVM；两者共用会互相踩。
 * 为什么不用 exec：Android 10+ 禁止执行应用私有目录里的可执行文件（W^X），
 * 只能像启动器本身那样用 JNI（nativeLaunchJVM）在进程内创建 JVM。
 */
class HorizonDatagenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val workDir = File(intent.getStringExtra(EXTRA_WORK) ?: "")
        val jreHome = File(intent.getStringExtra(EXTRA_JRE) ?: "")
        val classpath = intent.getStringExtra(EXTRA_CP).orEmpty()
        val resultFile = File(workDir, "datagen.result")
        resultFile.delete()

        Thread {
            var code = -100
            try {
                val log = File(workDir, "datagen.log")
                JvmLauncherAPI.nativeChdir(workDir.absolutePath)
                JvmLauncherAPI.nativeRedirectStdio(log.absolutePath)
                JvmLauncherAPI.nativeSetLdLibraryPath(
                    listOf(
                        File(jreHome, "lib"),
                        File(jreHome, "lib/jli"),
                        File(jreHome, "lib/server")
                    ).joinToString(":") { it.absolutePath }
                )
                val args = arrayOf(
                    "-Xmx1536M",
                    "-Djava.io.tmpdir=${workDir.absolutePath}",
                    "-cp",
                    classpath,
                    "net.minecraft.data.Main",
                    "--reports",
                    "--output",
                    "out"
                )
                HorizonPrepLog.append("调用 nativeLaunchJVM，jre=${jreHome.absolutePath}")
                val launched = JvmLauncherAPI.nativeLaunchJVM(jreHome.absolutePath, args)
                if (launched < 0) {
                    code = launched
                    HorizonPrepLog.append("nativeLaunchJVM 返回 $launched")
                } else {
                    // JVM 在 native 线程里跑，轮询等待结束
                    val deadline = System.currentTimeMillis() + 20 * 60 * 1000
                    while (JvmLauncherAPI.nativeIsJVMRunning() && System.currentTimeMillis() < deadline) {
                        Thread.sleep(1000)
                    }
                    code = if (JvmLauncherAPI.nativeIsJVMRunning()) -102
                    else JvmLauncherAPI.nativeGetExitCode()
                }
                HorizonPrepLog.append("datagen 退出码=$code")
            } catch (t: Throwable) {
                code = -101
                HorizonPrepLog.append("datagen 异常: ${t.javaClass.simpleName}: ${t.message}")
            }
            runCatching { resultFile.writeText(code.toString()) }
            runOnUiThread { finish() }
        }.start()
    }

    companion object {
        const val EXTRA_WORK = "work"
        const val EXTRA_JRE = "jre"
        const val EXTRA_CP = "cp"
    }
}
