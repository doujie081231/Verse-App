package com.verse.explorer.launcher.runtime

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import com.verse.explorer.core.launch.GameRuntimeBackend
import com.verse.explorer.core.launch.GameRuntimeCallbacks
import com.verse.explorer.core.launch.LaunchRequest
import com.verse.explorer.jvmlauncher.JvmLauncherAPI
import com.verse.explorer.launcher.bridge.CallbackBridge
import com.verse.explorer.launcher.fcl.launch.DefaultLauncher
import com.tungsten.fcl.game.sdl.SdlBridge
import com.tungsten.fclauncher.bridge.FCLBridge
import com.tungsten.fclauncher.bridge.FCLBridgeCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidRenderTarget(val surface: Surface, val texture: SurfaceTexture?, @Volatile var width: Int, @Volatile var height: Int)

data class PreparedFclLaunch(
    val request: LaunchRequest,
    val launcher: DefaultLauncher,
    val gameDir: File,
    val nativesDir: File,
    val startedAtMillis: Long,
    val vkDriverSystem: Boolean,
    val vulkanShimEnabled: Boolean,
    val pipelineCacheEnabled: Boolean
)

interface RuntimeLog {
    fun info(message: String, tag: String)
    fun warn(message: String, tag: String)
    fun error(message: String, tag: String)
}

interface RenderSettings {
    val scale: Double
    fun resolveSize(width: Int, height: Int): Pair<Int, Int>
}

/** 保持既有 FCL/JNI 初始化顺序；原生资源只由此后端持有。 */
class FclGameRuntimeBackend(
    context: Context,
    preparation: suspend (LaunchRequest) -> PreparedFclLaunch,
    callbacks: GameRuntimeCallbacks,
    private val logger: RuntimeLog,
    private val renderSettings: RenderSettings
) : GameRuntimeBackend<AndroidRenderTarget> {
    private val context = context.applicationContext
    private var preparation: (suspend (LaunchRequest) -> PreparedFclLaunch)? = preparation
    @Volatile private var callbacks: GameRuntimeCallbacks? = callbacks
    private var prepared: PreparedFclLaunch? = null
    @Volatile private var jvmStarted = false
    private var stopped = false
    private var released = false
    @Volatile var bridge: FCLBridge? = null
        private set
    private val bridgeCallbacks = object : FCLBridgeCallback {
        override fun onCursorModeChange(mode: Int) { this@FclGameRuntimeBackend.callbacks?.onCursorModeChange(mode) }
        override fun onHitResultTypeChange(type: Int) { this@FclGameRuntimeBackend.callbacks?.onHitResultTypeChange(type) }
        override fun onLog(log: String) { this@FclGameRuntimeBackend.callbacks?.onLog(log) }
        override fun onExit(code: Int) { this@FclGameRuntimeBackend.callbacks?.onExit(code) }
    }
    companion object { private const val TAG = "GameLaunchOrchestrator" }

    override suspend fun prepare(request: LaunchRequest) {
        val factory = checkNotNull(preparation) { "Game backend has been released" }
        val result = factory(request)
        synchronized(this) {
            check(!released) { "Game backend has been released" }
            prepared = result
        }
    }

    override suspend fun start(target: AndroidRenderTarget) {
        val configuration = checkNotNull(prepared) { "Game backend has not been prepared" }
        val launcher = configuration.launcher
        val gameDir = configuration.gameDir
        val nativesDir = configuration.nativesDir
        val t0 = configuration.startedAtMillis
        // 8) JVM 启动（native 日志路径设到外部可访问目录）
        // 诊断：JVM 的 stdout/stderr 在 Android 上默认丢弃（fd 指向 /dev/null），
        // 重定向到文件便于排查启动期错误（走外部存储，便于通过文件管理器/远程工具取回日志）。
        // 各初始化步骤分别记录异常，便于从 launch.log 定位具体失败阶段。
        try {
            System.loadLibrary("versejvm")
            logger.info("[8/9 JVM启动] loadLibrary(versejvm) ok", TAG)
        } catch (t: Throwable) {
            logger.error("[8/9 JVM启动] loadLibrary(versejvm) 失败: $t", TAG)
        }
        // 诊断日志目录：提前计算（与下方 initializeHooks 无关，供 native 日志使用）
        val externalBase = context.getExternalFilesDir(null)
        val diagDir = if (externalBase != null) File(File(externalBase, "VerseExplorerX"), "logs") else null
        if (diagDir != null) diagDir.mkdirs()
        // 执行 bytehook_init(BYTEHOOK_MODE_AUTOMATIC) → create_sdl_hooks（SDL_InitSubSystem
        // launcher 集成/窗口复用/EGL 兼容/Vulkan loader 一致性）+ create_sdl_dlopen_hooks
        // （dlsym 出口把 SDL 符号换成代理，LWJGL 经 dlopen+dlsym 解析的调用同样走代理）。
        // 通过 FCLBridge 统一初始化钩子，避免重复初始化 ByteHook 后无法继续安装钩子。
        try {
            System.loadLibrary("pojavexec")
            FCLBridge.initializeHooks()
            logger.info("[8/9 JVM启动] FCLBridge.initializeHooks() ok（SDL hooks 已激活）", TAG)
        } catch (t: Throwable) {
            logger.error("[8/9 JVM启动] FCLBridge.initializeHooks() 失败: $t", TAG)
        }
        try {
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv("VERSE_NATIVES_DIR", nativesDir.absolutePath)
            // native FLOGI 日志：外部可访问路径，Android 10+ 的 /sdcard 根目录不可写，
            // 必须用应用专属外部目录（getExternalFilesDir）。
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetLogPath(
                File(diagDir ?: File(gameDir, "logs"), "verse_native.log").absolutePath
            )
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeRedirectStdio(
                File(diagDir ?: File(gameDir, "logs"), "verse_jvm_out.log").absolutePath
            )
            // glfw_hook（EGL/窗口创建/渲染库加载）的文件日志：崩溃现场最后操作点
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                "VERSE_GLFW_LOG_PATH",
                File(diagDir ?: File(gameDir, "logs"), "verse_glfw.log").absolutePath
            )
            // MC 26.3 SDL 渲染路径：RenderPearl 校验 LWJGL 的 GL provider 与
            // SDL_GL_GetProcAddress 返回的 glGetError 是否同一实现。LWJGL 侧经
            // -Dorg.lwjgl.opengl.libname 加载 libng_gl4es.so，而 SDL3 Android 后端
            // 的 SDL_GL_GetProcAddress 走系统 eglGetProcAddress（Mali 驱动实现），
            // 两地址必然不一致 → glGetError mismatch。versesdl 在 dlsym 出口把
            // SDL_GL_GetProcAddress 的 gl* 函数重定向到 libng_gl4es.so。
            // versesdl 的 bytehook_init 返回 ALREADY_CALLED(4) 时继续装 hook（不锁死）。
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                "VERSE_SDL_LOG_PATH",
                File(diagDir ?: File(gameDir, "logs"), "verse_sdl.log").absolutePath
            )
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                "VERSE_APP_LIB_DIR",
                context.applicationInfo.nativeLibraryDir
            )
            try {
                System.loadLibrary("versesdl")
                logger.info("[8/9 JVM启动] loadLibrary(versesdl) ok（SDL GL 对齐 hooks 已激活）", TAG)
            } catch (t: Throwable) {
                logger.error("[8/9 JVM启动] loadLibrary(versesdl) 失败: $t", TAG)
            }
            // RenderPearl 的 Vulkan 后端（26.2+）要求 VK_EXT_vertex_attribute_divisor：
            // LWJGL/SDL 的 Vulkan 加载统一包装到 libvkshim.so（VULKAN_PTR）合成该扩展。
            // 仅 arm64-v8a 真机路径启用（模拟器 x86_64 / OpenGL 后端不受影响）。
            if (configuration.request.mcVersionMajor >= 26 &&
                android.os.Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" }) {
                // "1"=系统驱动；"0"=Turnip，versesdl 加载链经 liblinkerhook 把 loader 的驱动
                // 加载请求转向 DRIVER_PATH/libvulkan_freedreno.so（不适用设备探测失败自动回落）
                val vkDriverSystem = configuration.vkDriverSystem
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "VULKAN_DRIVER_SYSTEM", if (vkDriverSystem) "1" else "0"
                )
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "DRIVER_PATH", context.applicationInfo.nativeLibraryDir
                )
                // 补齐——divisor 合成或 fillModeNonSolid 降级模拟——时才启用包装层；
                // 无条件开启会让缺口不可补的设备（驱动 <1.2）在游戏内加载 Vulkan 句柄时崩溃）
                val shimEnabled = configuration.vulkanShimEnabled
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "VKSHIM_ENABLE", if (shimEnabled) "1" else "0"
                )
                logger.info("[8/9 JVM启动] VKSHIM_ENABLE=${if (shimEnabled) "1" else "0"}（needsVulkanShim=$shimEnabled）", TAG)
                // VerseX 渲染器（设置页「VerseX 渲染器/Pipeline 缓存」）：M1 为 pipeline 对象级缓存，
                // 经环境变量传给 vkshim（默认开，VERSERX_PIPELINE_CACHE=0 关闭）
                val vsxPipelineCache = configuration.pipelineCacheEnabled
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "VERSERX_PIPELINE_CACHE",
                    if (vsxPipelineCache) "1" else "0"
                )
                if (shimEnabled) {
                    try {
                        org.libsdl.app.SDL.nativeEnableVulkanShim()
                        logger.info("[8/9 JVM启动] vkshim Vulkan 包装层已启用（VULKAN_PTR 指向 libvkshim.so）", TAG)
                    } catch (t: Throwable) {
                        logger.error("[8/9 JVM启动] nativeEnableVulkanShim 失败: $t", TAG)
                    }
                } else {
                    logger.info("[8/9 JVM启动] vkshim 包装层未启用（设备缺口不可补或无缺口）", TAG)
                }
            }
            logger.info("[8/9 JVM启动] native 日志: ${(diagDir ?: File(gameDir, "logs")).absolutePath}", TAG)
        } catch (t: Throwable) {
            logger.error("[8/9 JVM启动] native 配置失败: $t", TAG)
        }
        logger.info("[8/9 JVM启动] 调用 DefaultLauncher.launch() ...", TAG)
        val bridge = withContext(Dispatchers.IO) {
            launcher.launch()
        }
        this.bridge = bridge

        logger.info("[8/9 JVM启动] bridge 返回: $bridge (isValid=${bridge != null}), 总耗时=${System.currentTimeMillis() - t0}ms", TAG)

        // 9) JVM 启动完成，把 Surface 交给游戏渲染线程
        // 之前只缩 LaunchOptions 窗口尺寸、未缩 Surface 缓冲导致错位黑块。
        // 分辨率按屏幕尺寸的百分比缩放。
        val scaleFactor = renderSettings.scale
        val resolvedSize = renderSettings.resolveSize(target.width, target.height)
        logger.info(
            "[9/9 渲染接入] windowScale=$scaleFactor, 窗口=${resolvedSize.first}x${resolvedSize.second}", TAG
        )
        bridge.setScaleFactor(scaleFactor)
        // 窗口缓冲尺寸：execute 后 pushEventWindow 需复用，提升到函数级
        var winWidth = 0
        var winHeight = 0
        val surfaceTexture = target.texture
        if (surfaceTexture != null && target.width > 0 && target.height > 0) {
            winWidth = resolvedSize.first
            winHeight = resolvedSize.second
            if (FCLBridge.FORCE_RESOLUTION) {
                winWidth = FCLBridge.FORCE_RESOLUTION_WIDTH
                winHeight = FCLBridge.FORCE_RESOLUTION_HEIGHT
            }
            // setDefaultBufferSize 是纯 Java SurfaceTexture API，缩放真正生效处（渲染缓冲按比例缩小）。
            // 注意：此处【不能】调用 bridge.pushEventWindow —— 它引用 org.lwjgl.glfw.CallbackBridge
            // （游戏 JVM 侧类），在 execute 启动 JVM 前触发其静态初始化会失败并把类标记为 failed，
            // 窗口尺寸已由 LaunchOptions(setWidth/Height) + setDefaultBufferSize 双重保证。
            surfaceTexture.setDefaultBufferSize(winWidth, winHeight)
            bridge.setSurfaceTexture(surfaceTexture)
        }
        logger.info("[9/9 渲染接入] bridge.execute(surface=${target.surface}, callback) ...", TAG)
        jvmStarted = true
        // SDLSurface 初始化（mWidth=CallbackBridge.windowWidth）依赖该字段，缺失时窗口尺寸 0
        // 导致 SDL/Vulkan surface 尺寸 0，进世界渲染 abort（SIGABRT 退出码 6）。
        // 失败兜底忽略（execute 后 pushEventWindow 仍会经 sendUpdateWindowSize 更新）。
        runCatching {
            org.lwjgl.glfw.CallbackBridge.windowWidth = winWidth
            org.lwjgl.glfw.CallbackBridge.windowHeight = winHeight
        }.onFailure {
            logger.warn("[9/9 渲染接入] 预置 CallbackBridge.windowWidth/Height 失败: ${it.message}", TAG)
        }
        withContext(Dispatchers.Main) {
            bridge.execute(target.surface, bridgeCallbacks)
        }
        // 游戏 JVM 侧 org.lwjgl.glfw.CallbackBridge.sendUpdateWindowSize 更新 windowWidth/Height
        // 并触发 GLFW 窗口尺寸回调 → MC 按新尺寸重建渲染目标/swapchain。
        // 时序：必须在 execute 之后（JVM 已启动、CallbackBridge 已初始化），execute 前调用
        // 会触发该类过早静态初始化导致永久 failed（游戏无法启动）。
        // 缺失该推送时游戏窗口尺寸停留在初始化默认值，渲染目标与 Surface 缓冲尺寸不一致，
        // 进世界渲染首个世界帧（区块 command buffer 初始化）时驱动 abort（SIGABRT 退出码 6）。
        runCatching {
            bridge.pushEventWindow(winWidth, winHeight)
            logger.info("[9/9 渲染接入] pushEventWindow($winWidth x $winHeight) 已推送", TAG)
        }.onFailure {
            logger.warn("[9/9 渲染接入] pushEventWindow 失败: ${it.message}", TAG)
        }
        logger.info("启动流程结束, 总耗时=${System.currentTimeMillis() - t0}ms", TAG)
    }


    fun onSurfaceChanged(width: Int, height: Int) {
        runCatching {
            val size = renderSettings.resolveSize(width, height)
            org.lwjgl.glfw.CallbackBridge.windowWidth = if (FCLBridge.FORCE_RESOLUTION) FCLBridge.FORCE_RESOLUTION_WIDTH else size.first
            org.lwjgl.glfw.CallbackBridge.windowHeight = if (FCLBridge.FORCE_RESOLUTION) FCLBridge.FORCE_RESOLUTION_HEIGHT else size.second
        }.onFailure { logger.warn("预置 CallbackBridge.windowWidth/Height 失败: ${it.message}", TAG) }
    }

    override fun stop() {
        synchronized(this) { if (stopped) return; stopped = true }
        if (!jvmStarted) return
        runCatching { JvmLauncherAPI.nativeDestroyJVM() }.onFailure { logger.warn("nativeDestroyJVM 失败: ${it.message}", TAG) }
        runCatching { bridge?.thread?.interrupt() }
        runCatching { CallbackBridge.nativeSetInputReady(false) }
    }

    override fun release() {
        synchronized(this) {
            if (released) return
            released = true
            callbacks = null
            preparation = null
            prepared = null
        }
        runCatching { if (jvmStarted) CallbackBridge.setFCLBridge(null) }
        runCatching { SdlBridge.reset() }
        bridge = null
    }
    private fun elapsed(start: Long): Long = System.currentTimeMillis() - start
}
