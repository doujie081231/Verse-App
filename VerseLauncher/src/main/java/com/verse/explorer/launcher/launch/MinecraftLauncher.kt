package com.verse.explorer.launcher.launch

import android.content.Context
import android.util.Log
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.game.GameProfile
import com.verse.explorer.launcher.bridge.ZLBridge
import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.terracotta.TerracottaAndroidAPI
import java.io.File

data class LaunchConfiguration(
    val version: MinecraftVersion,
    val profile: GameProfile,
    val javaHome: File,
    val gameDir: File,
    val assetsDir: File,
    val librariesDir: File,
    val nativesDir: File,
    val width: Int = 854,
    val height: Int = 480,
    val maxMemory: Int = 2048,
    val minMemory: Int = 512,
    val fullscreen: Boolean = false,
    val demo: Boolean = false,
    val serverAddress: String? = null,
    val serverPort: Int? = null,
    val gcType: String = "G1GC",
    val customJvmArgs: String = "",
    val renderer: String = "zink",
    val javaVersion: Int = 17,
    val beGesture: Boolean = true,
    val debugLog: Boolean = false,
    val isVulkanDriverSystem: Boolean = false,
    val gameArguments: String = "",
    val javaArguments: List<String> = emptyList(),
    val accessToken: String = "0",
    val userType: String = "mojang"
)

data class LaunchProcess(
    val pid: Int,
    val process: Process? = null
)

class MinecraftLauncher(private val context: Context, private val zlBridge: ZLBridge? = null) {

    companion object {
        private const val TAG = "MinecraftLauncher"
    }

    private var currentProcess: LaunchProcess? = null
    private var launchCallback: LaunchCallback? = null

    private val environmentSetup by lazy { LaunchEnvironmentSetup(context) }
    private val classpathBuilder by lazy { ClasspathBuilder(context) }
    private val gameArgsParser by lazy { GameArgsParser(context) }
    private val jvmArgsBuilder by lazy {
        JvmArgsBuilder(context, environmentSetup, classpathBuilder, gameArgsParser)
    }

    fun setLaunchCallback(callback: LaunchCallback) {
        this.launchCallback = callback
    }

    fun launch(config: LaunchConfiguration, launchMethod: String = "pojav"): LaunchProcess? {
        if (currentProcess != null && isRunning()) {
            Log.w(TAG, "Game already running")
            return null
        }

        try {
            val t0 = System.currentTimeMillis()

            // V3.7: Set debug log path to app's private directory (no permissions needed)
            // This allows viewing logs directly in-app without adb
            val logPath = File(context.filesDir, "verse_debug.log").absolutePath
            TerracottaAndroidAPI.nativeSetLogPath(logPath)
            Log.i(TAG, "Debug log: $logPath")
            Log.i(TAG, "[launch] nativeSetLogPath done (${System.currentTimeMillis()-t0}ms)")

            // FCL-style: 在 launchJVM 之前设置渲染器相关的 env vars
            val rendererConfig = RendererManager.resolveRendererWithFallback(context, config.renderer)

            Log.i(TAG, "[launch] setupCommonEnvironment...")
            if (!environmentSetup.setupCommonEnvironment(config)) {
                Log.e(TAG, "setupCommonEnvironment failed")
                return null
            }
            Log.i(TAG, "[launch] setupCommonEnvironment done (${System.currentTimeMillis()-t0}ms)")

            Log.i(TAG, "[launch] setupRendererEnvironment...")
            if (!environmentSetup.setupRendererEnvironment(config, rendererConfig)) {
                Log.e(TAG, "setupRendererEnvironment failed")
                return null
            }
            Log.i(TAG, "[launch] setupRendererEnvironment done (${System.currentTimeMillis()-t0}ms)")

            Log.i(TAG, "[launch] buildFullArgs...")
            val args = jvmArgsBuilder.buildFullArgs(config, rendererConfig)
            Log.i(TAG, "[launch] buildFullArgs done: ${args.size} args (${System.currentTimeMillis()-t0}ms)")

            Log.d(TAG, "=== Launching Minecraft (FCL-style) ===")
            Log.d(TAG, "Method: $launchMethod")
            Log.d(TAG, "Java: ${config.javaHome.absolutePath}")
            Log.d(TAG, "Version: ${config.version.id}")
            Log.d(TAG, "Game dir: ${config.gameDir.absolutePath}")
            Log.d(TAG, "Natives: ${config.nativesDir.absolutePath}")
            Log.d(TAG, "Renderer: ${config.renderer}")
            Log.d(TAG, "Total args: ${args.size}")
            args.forEachIndexed { i, arg -> Log.d(TAG, "  argv[$i] = $arg") }

            Log.i(TAG, "[launch] Calling launchJVM ($launchMethod)...")
            val nativeResult = runGameLaunchThread(launchMethod, config, args, t0)

            if (nativeResult == 0) {
                Log.i(TAG, "JVM launched successfully (startup confirmed by native layer)")
                val pid = zlBridge?.getPid() ?: -1
                currentProcess = LaunchProcess(pid)
                return currentProcess
            }

            val errorMsg = when (nativeResult) {
                -1 -> "JVM启动参数错误或JRE路径不存在"
                -2 -> "JVM启动超时: JRE环境初始化失败 (检查Java安装和原生库)"
                -10 -> "无法加载libjli.so (JRE可能不完整)"
                -11 -> "无法找到JLI_Launch函数 (JRE版本不兼容)"
                else -> "JVM启动失败, 错误码: $nativeResult"
            }
            Log.e(TAG, errorMsg)
            return null

        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "原生库缺失 (UnsatisfiedLinkError): ${e.message}", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Minecraft启动异常: ${e.message}", e)
            return null
        }
    }

    /**
     * 在一个独立游戏线程中调用 launchJVM，启动后把优先级提到最高。
     */
    private fun runGameLaunchThread(
        launchMethod: String,
        config: LaunchConfiguration,
        args: List<String>,
        t0: Long
    ): Int {
        val resultHolder = mutableListOf<Int>()
        val gameThread = Thread({
            try {
                val r = when {
                    launchMethod == "boat" -> {
                        Log.d(TAG, "Using Boat (JVMLoader) launch method")
                        val jvmLoader = JVMLoader(context)
                        jvmLoader.setExitCallback(object : JVMLoaderCallback {
                            override fun onExit(code: Int) {
                                currentProcess = null
                                launchCallback?.onGameExit(code)
                            }
                        })
                        jvmLoader.launchJVM(
                            jrePath = config.javaHome.absolutePath,
                            args = args.toTypedArray()
                        )
                    }
                    zlBridge != null -> {
                        Log.d(TAG, "Using Pojav (ZLBridge) launch method")
                        Log.i(TAG, "[launch] Calling zlBridge.launchJVM...")
                        val r = zlBridge.launchJVM(
                            jrePath = config.javaHome.absolutePath,
                            args = args.toTypedArray()
                        )
                        Log.i(TAG, "[launch] zlBridge.launchJVM returned: $r (${System.currentTimeMillis() - t0}ms)")
                        r
                    }
                    else -> {
                        Log.d(TAG, "Using fallback JVMLoader launch method")
                        val jvmLoader = JVMLoader(context)
                        jvmLoader.setExitCallback(object : JVMLoaderCallback {
                            override fun onExit(code: Int) {
                                currentProcess = null
                                launchCallback?.onGameExit(code)
                            }
                        })
                        jvmLoader.launchJVM(
                            jrePath = config.javaHome.absolutePath,
                            args = args.toTypedArray()
                        )
                    }
                }
                resultHolder.add(r)
            } catch (e: Throwable) {
                Log.e(TAG, "launchJVM thread failed: ${e.message}", e)
                resultHolder.add(-1)
            }
        }, "MinecraftGameThread")

        gameThread.start()
        gameThread.priority = Thread.MAX_PRIORITY
        gameThread.join()
        return resultHolder.firstOrNull() ?: -1
    }

    // ==================== 生命周期 ====================

    fun stop() {
        zlBridge?.destroyJVM()
        currentProcess = null
    }

    fun forceStop() {
        zlBridge?.destroyJVM()
        currentProcess = null
    }

    fun isRunning(): Boolean = zlBridge?.isJVMRunning() == true

    fun getPid(): Int? = currentProcess?.pid
}

interface LaunchCallback {
    fun onGameExit(code: Int)
}
