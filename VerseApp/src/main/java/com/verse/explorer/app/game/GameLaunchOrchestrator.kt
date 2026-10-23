package com.verse.explorer.app.game

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.activity.ComponentActivity
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.domain.model.launch.GameLaunchParams
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.game.GameStatusOverlay
import com.verse.explorer.app.ui.game.GameInputHandler
import com.verse.explorer.core.java.JavaRuntimeManager
import com.verse.explorer.launcher.bridge.CallbackBridge
import com.verse.explorer.launcher.crash.CrashDetector
import com.verse.explorer.launcher.crash.CrashResult
import com.verse.explorer.launcher.fcl.auth.AuthInfo
import com.verse.explorer.launcher.fcl.game.DefaultGameRepository
import com.verse.explorer.launcher.fcl.game.JavaVersion
import com.verse.explorer.launcher.fcl.game.LaunchOptions
import com.verse.explorer.launcher.fcl.game.VerseJavaVersion
import com.verse.explorer.launcher.fcl.game.Version
import com.verse.explorer.launcher.fcl.launch.DefaultLauncher
import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.launcher.fcl.util.FCLPath
import com.verse.explorer.jvmlauncher.JvmLauncherAPI
import com.tungsten.fcl.game.sdl.SdlBridge
import com.tungsten.fcl.setting.MenuSetting
import com.tungsten.fclauncher.bridge.FCLBridge
import com.tungsten.fclauncher.bridge.FCLBridgeCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

class GameLaunchOrchestrator(
    private val activity: ComponentActivity,
    private val bridgeCallback: FCLBridgeCallback,
    private val nativesDeployer: NativesDeployer,
    private val logManager: GameLogManager,
    private val javaDetector: JavaRuntimeDetector,
    private val statusOverlay: GameStatusOverlay,
    private val inputHandler: GameInputHandler
) {
    companion object {
        private const val TAG = "GameLaunchOrchestrator"
        private const val RESTART_GAME_REQUEST_CODE = 0x5647
    }

    var javaHome: String? = null
        private set

    var fclBridge: FCLBridge? = null
        private set

    private var launchParams: GameLaunchParams? = null
    private var currentGameDir: File? = null
    private var currentVersionId: String? = null
    private var hasLaunched = false
    private var isLaunching = false
    // JVM 是否已启动过（bridge.execute 已调用）：用于保护 stopGame/release 中的 nativeSetInputReady(false)，
    private var jvmStarted = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val surfaceDeferred = CompletableDeferred<Surface>()
    private var surfaceReady: Boolean = false

    private val crashAnalyzer = LaunchCrashAnalyzer(activity)
    private val errorDialog = LaunchErrorDialog(activity, statusOverlay, logManager)
    private val restarter = LaunchRestarter(activity)

    fun configure(params: GameLaunchParams) {
        this.launchParams = params
        VerseLogger.info("=== configure 接收启动参数 ===", TAG)
        VerseLogger.info("  versionId=${params.versionId}", TAG)
        VerseLogger.info("  gameDir=${params.gameDirPath}", TAG)
        VerseLogger.info("  javaHome=${params.javaHome}, javaVersion=${params.javaVersion}", TAG)
        VerseLogger.info("  renderer=${params.renderer}, launchMethod=${params.launchMethod}", TAG)
        VerseLogger.info("  memory=${params.minMemory}~${params.maxMemory}M, gc=${params.gcType}", TAG)
        VerseLogger.info("  width=${params.gameWidth}, height=${params.gameHeight}", TAG)
        VerseLogger.info("  username=${params.username}, userType=${params.userType}, isMultiplayer=${params.isMultiplayer}", TAG)
        VerseLogger.info("  customJvmArgs=${params.customJvmArgs}", TAG)
    }

    fun startLaunch() {
        val params = launchParams ?: run {
            statusOverlay.setTitle("启动失败")
            statusOverlay.setMessage("未配置启动参数")
            showCrashDialog(
                title = "启动失败",
                message = "未配置启动参数，请重新进入启动页面。",
                details = "launchParams == null",
                suggestion = "返回上一页重新点击启动"
            )
            return
        }

        if (hasLaunched) {
            VerseLogger.warn("重复调用 startLaunch，已忽略", TAG)
            return
        }
        if (isLaunching) {
            VerseLogger.warn("正在启动中，忽略重复调用", TAG)
            return
        }

        hasLaunched = true
        isLaunching = true

        statusOverlay.show()
        statusOverlay.setTitle("正在启动游戏...")
        statusOverlay.setMessage("版本: ${params.versionId}")
        statusOverlay.setIndeterminate(true)

        scope.launch {
            try {
                launchGameInternal(params)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                VerseLogger.error("启动异常: ${e.message}", TAG)
                isLaunching = false
                if (isActivityAlive()) {
                    withContext(Dispatchers.Main) {
                        statusOverlay.setTitle("启动失败")
                        statusOverlay.setMessage(e.message ?: "未知错误")
                        showCrashDialog(
                            title = "游戏启动失败",
                            message = e.message ?: "未知错误",
                            details = e.stackTraceToString(),
                            suggestion = "检查 Java 运行时、版本完整性、原生库部署是否正常"
                        )
                    }
                }
            }
        }
    }

    fun handleGameExit(code: Int) {
        isLaunching = false
        VerseLogger.info("游戏退出，code=$code", TAG)

        scope.launch {
            try {
                if (isActivityAlive()) {
                    withContext(Dispatchers.Main) {
                        if (code == 0) {
                            statusOverlay.setTitle("游戏已退出")
                            statusOverlay.setMessage("退出码: $code")
                            // JVM 在同一进程内无法二次初始化：退出后结束进程，
                            // 下次启动由全新进程承载新的 JVM（与 ZL2 的退出策略一致）
                            activity.finishAffinity()
                            killProcessSoon()
                            return@withContext
                        }

                        statusOverlay.setTitle("游戏崩溃退出")
                        statusOverlay.setMessage("退出码: $code，正在分析崩溃原因...")
                        statusOverlay.setIndeterminate(true)
                    }
                }

                val crashReport = currentGameDir?.let { CrashDetector.getLatestCrashReport(it) }

                if (isActivityAlive()) {
                    withContext(Dispatchers.Main) {
                        showCrashReportPage(code)
                        VerseLogger.info("崩溃分析页已展示（crashReport=${crashReport != null}）")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                VerseLogger.error("handleGameExit failed: ${e.message}")
            }
        }
    }

    fun stopGame() {
        VerseLogger.info("stopGame 被调用", TAG)
        try {
            JvmLauncherAPI.nativeDestroyJVM()
        } catch (e: Exception) {
            VerseLogger.warn("nativeDestroyJVM 失败: ${e.message}", TAG)
        }
        try {
            fclBridge?.thread?.interrupt()
        } catch (e: Exception) {
            VerseLogger.warn("中断游戏线程失败: ${e.message}", TAG)
        }
        try {
            // 仅当 JVM 已启动过才复位输入：类从未初始化时调用 nativeSetInputReady 会触发
            if (jvmStarted) {
                CallbackBridge.nativeSetInputReady(false)
            }
        } catch (e: Exception) {
            VerseLogger.warn("nativeSetInputReady 失败: ${e.message}", TAG)
        }
        isLaunching = false
    }

    fun release() {
        try {
            stopGame()
        } catch (e: Throwable) {
            VerseLogger.error("stopGame failed: ${e.message}")
        }
        try {
            scope.cancel()
        } catch (e: Throwable) {
            VerseLogger.error("scope.cancel failed: ${e.message}")
        }
        try {
            fclBridge?.thread?.interrupt()
        } catch (e: Throwable) {
            VerseLogger.error("fclBridge interrupt failed: ${e.message}")
        }
        try {
            if (jvmStarted) {
                CallbackBridge.nativeSetInputReady(false)
            }
        } catch (e: Throwable) {
            VerseLogger.error("nativeSetInputReady failed: ${e.message}")
        }
        try {
            if (jvmStarted) {
                CallbackBridge.setFCLBridge(null)
            }
        } catch (e: Throwable) {
            VerseLogger.error("setFCLBridge failed: ${e.message}")
        }
        try {
            // SDL 渲染路径（MC 26.3+）：复位桥接状态，防止旧 Surface/旧初始化残留
            SdlBridge.reset()
        } catch (e: Throwable) {
            VerseLogger.error("SdlBridge.reset failed: ${e.message}")
        }
        try {
            VerseLogger.close()
        } catch (e: Throwable) {
            VerseLogger.error("VerseLogger.close failed: ${e.message}")
        }
    }

    fun onSurfaceCreated(surface: Surface, surfaceTexture: SurfaceTexture?) {
        surfaceTextureRef = surfaceTexture
        if (!surfaceReady) {
            surfaceReady = true
            surfaceDeferred.complete(surface)
            VerseLogger.info("Surface created and ready")
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        VerseLogger.info("Surface changed: ${width}x${height}")
        surfaceWidth = width
        surfaceHeight = height
        // onSurfaceTextureSizeChanged L172-173）：SDLSurface 初始化（mWidth=CallbackBridge.windowWidth）
        // 与 SdlBridge.prepareSurface 都依赖该字段，必须在本回调（先于 GameActivity
        // onSurfaceTextureAvailable L869 的 prepareSurface）内设置，否则 SDL/Vulkan surface
        // 尺寸恒 0 → 进世界渲染首个世界帧 abort（SIGABRT 退出码 6，OpenJDK exited with code : 6）。
        runCatching {
            val resolvedSize = MenuSetting.resolveRenderSize(width, height)
            org.lwjgl.glfw.CallbackBridge.windowWidth = resolvedSize.first
            org.lwjgl.glfw.CallbackBridge.windowHeight = resolvedSize.second
            if (FCLBridge.FORCE_RESOLUTION) {
                org.lwjgl.glfw.CallbackBridge.windowWidth = FCLBridge.FORCE_RESOLUTION_WIDTH
                org.lwjgl.glfw.CallbackBridge.windowHeight = FCLBridge.FORCE_RESOLUTION_HEIGHT
            }
        }.onFailure {
            VerseLogger.warn("预置 CallbackBridge.windowWidth/Height 失败: ${it.message}", TAG)
        }
    }

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceTextureRef: SurfaceTexture? = null

    fun onSurfaceDestroyed() {
        VerseLogger.info("Surface destroyed, stopping game")
        stopGame()
    }

    private fun isActivityAlive(): Boolean {
        return !activity.isFinishing && !activity.isDestroyed
    }

    private suspend fun launchGameInternal(params: GameLaunchParams) {
        val t0 = System.currentTimeMillis()
        val gameDir = File(params.gameDirPath)
        currentGameDir = gameDir
        currentVersionId = params.versionId
        VerseLogger.info("==========================================", TAG)
        VerseLogger.info("启动流程开始 (step-by-step)", TAG)
        VerseLogger.info("==========================================", TAG)

        // 1) 版本解析
        updateProgress("解析版本信息...", "版本: ${params.versionId}")
        val fclVersion = resolveFclVersion(params, gameDir)
        VerseLogger.info("[1/9 版本解析] ${fclVersion.id}, mainClass=${fclVersion.getMainClass()}, 耗时=${elapsed(t0)}ms", TAG)

        // 2) 原生库部署（静默执行，不在启动界面显示部署过程）
        val nativesDir = deployNatives(gameDir, params)
        VerseLogger.info("[2/9 原生库] 部署到 ${nativesDir.absolutePath}, 耗时=${elapsed(t0)}ms", TAG)

        nativesDeployer.deployFclRuntime(gameDir)
        VerseLogger.info("[3/9 运行时] 完成, 耗时=${elapsed(t0)}ms", TAG)

        // 4) Java 运行时检查
        updateProgress("检查 Java 运行时...", null)
        val resolvedJavaHome = resolveJavaHome(params, gameDir)
        javaHome = resolvedJavaHome
        logManager.javaHomeProvider = { javaHome }
        VerseLogger.info("[4/9 Java运行时] 选用 JRE=$resolvedJavaHome, 耗时=${elapsed(t0)}ms", TAG)

        updateProgress("初始化运行时路径...", null)
        FCLPath.loadPaths(activity, gameDir.absolutePath)
        VerseLogger.info("[5/9 运行时路径] FILES_DIR=${FCLPath.FILES_DIR}, 耗时=${elapsed(t0)}ms", TAG)

        // 6) 启动选项构建
        updateProgress("构建启动选项...", null)
        val authInfo = createAuthInfo(params)
        val javaVersion = VerseJavaVersion(params.javaVersion, resolvedJavaHome)
        val verSettings = com.verse.explorer.app.data.VersionSettingsRepository(activity)
            .getVersionSettings(params.versionId)
        val launchOptions = buildLaunchOptions(params, javaVersion, verSettings)
        val repository = DefaultGameRepository(gameDir)
        repository.setRunDirectoryIsolated(verSettings.usesGlobal || verSettings.isolateGameDir)
        repository.refreshVersions()
        injectOnboardingSkip(repository, params.versionId)
        injectGameLanguage(repository, fclVersion)
        injectGraphicsBackend(
            repository, fclVersion, params.renderer,
            if (!verSettings.usesGlobal) verSettings.graphicsBackend else null
        )
        dumpRenderOptions(repository, fclVersion, params)
        VerseLogger.info("[6/9 启动选项] renderer=${params.renderer}, options=${launchOptions.fullSummary()}, 耗时=${elapsed(t0)}ms", TAG)

        // 7) 等待 Surface（渲染窗口）
        updateProgress("等待渲染窗口...", null)
        VerseLogger.info("[7/9 待 Surface] 等待渲染 Surface...", TAG)
        withContext(Dispatchers.Main) {
            statusOverlay.setTitle("准备渲染窗口...")
        }
        val surface = awaitSurface()
        VerseLogger.info("[7/9 待 Surface] Surface=${surface}, 尺寸=${surfaceWidth}x${surfaceHeight}, 耗时=${elapsed(t0)}ms", TAG)

        // 8) JVM 启动（native 日志路径设到外部可访问目录）
        updateProgress("启动 Java 虚拟机...", "这可能需要几秒钟")
        val launcher = DefaultLauncher(activity, repository, fclVersion, authInfo, launchOptions)
        launcher.setNativesDirectory(nativesDir.absolutePath)
        // 诊断：JVM 的 stdout/stderr 在 Android 上默认丢弃（fd 指向 /dev/null），
        // 重定向到文件便于排查启动期错误（走外部存储，便于通过文件管理器/远程工具取回日志）。
        // 逐调用 try-catch：任何失败都必须落到 launch.log，杜绝被吞掉后再猜
        try {
            System.loadLibrary("versejvm")
            VerseLogger.info("[8/9 JVM启动] loadLibrary(versejvm) ok", TAG)
        } catch (t: Throwable) {
            VerseLogger.error("[8/9 JVM启动] loadLibrary(versejvm) 失败: $t", TAG)
        }
        // 诊断日志目录：提前计算（与下方 initializeHooks 无关，供 native 日志使用）
        val externalBase = activity.getExternalFilesDir(null)
        val diagDir = if (externalBase != null) File(File(externalBase, "VerseExplorerX"), "logs") else null
        if (diagDir != null) diagDir.mkdirs()
        // 执行 bytehook_init(BYTEHOOK_MODE_AUTOMATIC) → create_sdl_hooks（SDL_InitSubSystem
        // launcher 集成/窗口复用/EGL 兼容/Vulkan loader 一致性）+ create_sdl_dlopen_hooks
        // （dlsym 出口把 SDL 符号换成代理，LWJGL 经 dlopen+dlsym 解析的调用同样走代理）。
        // 注：自研 versesdl（hook SDL_GL_GetProcAddress/dlsym）已移除 —— 其 bytehook_init
        // 一次失败（MEMORY_ALLOCATION_FAILED）就把 bytehook 状态锁死，后续任何 init 均返回
        try {
            System.loadLibrary("pojavexec")
            FCLBridge.initializeHooks()
            VerseLogger.info("[8/9 JVM启动] FCLBridge.initializeHooks() ok（SDL hooks 已激活）", TAG)
        } catch (t: Throwable) {
            VerseLogger.error("[8/9 JVM启动] FCLBridge.initializeHooks() 失败: $t", TAG)
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
                activity.applicationInfo.nativeLibraryDir
            )
            try {
                System.loadLibrary("versesdl")
                VerseLogger.info("[8/9 JVM启动] loadLibrary(versesdl) ok（SDL GL 对齐 hooks 已激活）", TAG)
            } catch (t: Throwable) {
                VerseLogger.error("[8/9 JVM启动] loadLibrary(versesdl) 失败: $t", TAG)
            }
            // RenderPearl 的 Vulkan 后端（26.2+）要求 VK_EXT_vertex_attribute_divisor：
            // LWJGL/SDL 的 Vulkan 加载统一包装到 libvkshim.so（VULKAN_PTR）合成该扩展。
            // 仅 arm64-v8a 真机路径启用（模拟器 x86_64 / OpenGL 后端不受影响）。
            if (params.mcVersionMajor >= 26 &&
                android.os.Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" }) {
                // "1"=系统驱动；"0"=Turnip，versesdl 加载链经 liblinkerhook 把 loader 的驱动
                // 加载请求转向 DRIVER_PATH/libvulkan_freedreno.so（不适用设备探测失败自动回落）
                val vkDriverSystem = if (!verSettings.usesGlobal) verSettings.vkDriverSystem
                else SettingsRepository.getVkDriverSystem()
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "VULKAN_DRIVER_SYSTEM", if (vkDriverSystem) "1" else "0"
                )
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "DRIVER_PATH", activity.applicationInfo.nativeLibraryDir
                )
                // 补齐——divisor 合成或 fillModeNonSolid 降级模拟——时才启用包装层；
                // 无条件开启会让缺口不可补的设备（驱动 <1.2）在游戏内加载 Vulkan 句柄时崩溃）
                val shimEnabled = com.verse.explorer.app.device.VulkanCheckManager.needsVulkanShim
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "VKSHIM_ENABLE", if (shimEnabled) "1" else "0"
                )
                VerseLogger.info("[8/9 JVM启动] VKSHIM_ENABLE=${if (shimEnabled) "1" else "0"}（needsVulkanShim=$shimEnabled）", TAG)
                // VerseX 渲染器（设置页「VerseX 渲染器/Pipeline 缓存」）：M1 为 pipeline 对象级缓存，
                // 经环境变量传给 vkshim（默认开，VERSERX_PIPELINE_CACHE=0 关闭）
                val vsxPipelineCache = com.verse.explorer.app.data.SettingsRepository.getVersexPipelineCache()
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "VERSERX_PIPELINE_CACHE",
                    if (vsxPipelineCache) "1" else "0"
                )
                if (shimEnabled) {
                    try {
                        org.libsdl.app.SDL.nativeEnableVulkanShim()
                        VerseLogger.info("[8/9 JVM启动] vkshim Vulkan 包装层已启用（VULKAN_PTR 指向 libvkshim.so）", TAG)
                    } catch (t: Throwable) {
                        VerseLogger.error("[8/9 JVM启动] nativeEnableVulkanShim 失败: $t", TAG)
                    }
                } else {
                    VerseLogger.info("[8/9 JVM启动] vkshim 包装层未启用（设备缺口不可补或无缺口）", TAG)
                }
            }
            VerseLogger.info("[8/9 JVM启动] native 日志: ${(diagDir ?: File(gameDir, "logs")).absolutePath}", TAG)
        } catch (t: Throwable) {
            VerseLogger.error("[8/9 JVM启动] native 配置失败: $t", TAG)
        }
        VerseLogger.info("[8/9 JVM启动] 调用 DefaultLauncher.launch() ...", TAG)
        val bridge = withContext(Dispatchers.IO) {
            launcher.launch()
        }
        fclBridge = bridge

        isLaunching = false
        VerseLogger.info("[8/9 JVM启动] bridge 返回: $bridge (isValid=${bridge != null}), 总耗时=${System.currentTimeMillis() - t0}ms", TAG)

        // 9) JVM 启动完成，把 Surface 交给游戏渲染线程
        // 之前只缩 LaunchOptions 窗口尺寸、未缩 Surface 缓冲导致错位黑块。
        // 分辨率按屏幕尺寸的百分比缩放。
        val scaleFactor = MenuSetting.windowScale
        val resolvedSize = MenuSetting.resolveRenderSize(surfaceWidth, surfaceHeight)
        VerseLogger.info(
            "[9/9 渲染接入] windowScale=$scaleFactor, 窗口=${resolvedSize.first}x${resolvedSize.second}", TAG
        )
        bridge.setScaleFactor(scaleFactor)
        // 窗口缓冲尺寸：execute 后 pushEventWindow 需复用，提升到函数级
        var winWidth = 0
        var winHeight = 0
        val surfaceTexture = surfaceTextureRef
        if (surfaceTexture != null && surfaceWidth > 0 && surfaceHeight > 0) {
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
        VerseLogger.info("[9/9 渲染接入] bridge.execute(surface=$surface, callback) ...", TAG)
        jvmStarted = true
        // SDLSurface 初始化（mWidth=CallbackBridge.windowWidth）依赖该字段，缺失时窗口尺寸 0
        // 导致 SDL/Vulkan surface 尺寸 0，进世界渲染 abort（SIGABRT 退出码 6）。
        // 失败兜底忽略（execute 后 pushEventWindow 仍会经 sendUpdateWindowSize 更新）。
        runCatching {
            org.lwjgl.glfw.CallbackBridge.windowWidth = winWidth
            org.lwjgl.glfw.CallbackBridge.windowHeight = winHeight
        }.onFailure {
            VerseLogger.warn("[9/9 渲染接入] 预置 CallbackBridge.windowWidth/Height 失败: ${it.message}", TAG)
        }
        withContext(Dispatchers.Main) {
            bridge.execute(surface, bridgeCallback)
        }
        // 游戏 JVM 侧 org.lwjgl.glfw.CallbackBridge.sendUpdateWindowSize 更新 windowWidth/Height
        // 并触发 GLFW 窗口尺寸回调 → MC 按新尺寸重建渲染目标/swapchain。
        // 时序：必须在 execute 之后（JVM 已启动、CallbackBridge 已初始化），execute 前调用
        // 会触发该类过早静态初始化导致永久 failed（游戏无法启动）。
        // 缺失该推送时游戏窗口尺寸停留在初始化默认值，渲染目标与 Surface 缓冲尺寸不一致，
        // 进世界渲染首个世界帧（区块 command buffer 初始化）时驱动 abort（SIGABRT 退出码 6）。
        runCatching {
            bridge.pushEventWindow(winWidth, winHeight)
            VerseLogger.info("[9/9 渲染接入] pushEventWindow($winWidth x $winHeight) 已推送", TAG)
        }.onFailure {
            VerseLogger.warn("[9/9 渲染接入] pushEventWindow 失败: ${it.message}", TAG)
        }
        // Java 虚拟机已启动、游戏首帧尚未输出：把卡片切到「等待游戏画面」，允许玩家手动关闭
        // （黑屏期间始终有状态提示，首帧到达后 onGraphicOutput 会自动隐藏）
        withContext(Dispatchers.Main) {
            statusOverlay.setTitle("正在等待游戏画面出现")
            statusOverlay.setMessage("Java 虚拟机已启动，正在加载游戏画面...")
            statusOverlay.setIndeterminate(true)
            statusOverlay.setClosable(true)
        }
        VerseLogger.info("启动流程结束, 总耗时=${System.currentTimeMillis() - t0}ms", TAG)
    }

    private fun elapsed(t0: Long): Long = System.currentTimeMillis() - t0

    private fun resolveFclVersion(params: GameLaunchParams, gameDir: File): Version {
        val repository = DefaultGameRepository(gameDir)
        repository.refreshVersions()
        return repository.getVersion(params.versionId)
    }

    /**
     * 跳过 MC 首次启动欢迎引导（Welcome to Minecraft! 会阻止进入主菜单）：
     * 在版本目录 options.txt 写 onboardAccessibility:false。
     * 游戏退出时会重写 options.txt 丢弃该行，因此每次启动前都注入。
     */
    private fun injectOnboardingSkip(repository: DefaultGameRepository, versionId: String) {
        runCatching {
            val targets = listOf(
                File(repository.getRunDirectory(versionId), "options.txt"),
                File(File(File(repository.getBaseDirectory(), "versions"), versionId), "options.txt")
            )
            targets.distinct().forEach { optionsFile ->
                if (optionsFile.parentFile != null) optionsFile.parentFile.mkdirs()
                val content = if (optionsFile.exists() && optionsFile.isFile) optionsFile.readText() else ""
                if (!content.contains("onboardAccessibility:")) {
                    optionsFile.appendText("\nonboardAccessibility:false\n")
                }
            }
        }.onFailure {
            VerseLogger.warn("注入 onboardAccessibility 失败: ${it.message}", TAG)
        }
    }

    /**
     * 按版本发布日期选择 lang 值（1.1~1.10 用 zh_CN，其余支持语言版本用 zh_cn；
     * 1.0 及以下无语言选项直接跳过），写入版本目录 options.txt。
     * 用户要求强制中文：每次启动都会覆盖为 requiredLang（不保留存档中的旧语言选择）。
     */
    private fun injectGameLanguage(repository: DefaultGameRepository, version: Version) {
        if (!SettingsRepository.isAutoSetChinese()) {
            VerseLogger.info("自动设置中文已关闭，跳过语言设置", TAG)
            return
        }
        runCatching {
            val runDir = repository.getRunDirectory(version.id)
            var optionsFile = File(runDir, "options.txt")
            val yosbrFile = File(File(File(runDir, "config"), "yosbr"), "options.txt")
            if (!optionsFile.exists() && yosbrFile.exists()) {
                VerseLogger.info("使用 Yosbr Mod 中的 options.txt", TAG)
                optionsFile = yosbrFile
            }

            val releaseTime = version.releaseTime ?: version.time
            val mc1_1 = Instant.parse("2012-01-12T00:00:00Z")
            val mc1_11 = Instant.parse("2016-06-08T00:00:00Z")
            // 1.0 及以下版本无语言选项
            if (releaseTime != null && !releaseTime.isAfter(mc1_1)) {
                VerseLogger.info("1.0 及以下版本，无语言选项", TAG)
                return@runCatching
            }
            val requiredLang = if (releaseTime != null && releaseTime.isAfter(mc1_1) && !releaseTime.isAfter(mc1_11)) {
                "zh_CN"
            } else {
                "zh_cn"
            }

            var content = if (optionsFile.exists() && optionsFile.isFile) optionsFile.readText() else ""
            val langRegex = Regex("^lang:(.+)$", RegexOption.MULTILINE)
            val currentLang = langRegex.find(content)?.groupValues?.get(1)?.trim() ?: "none"

            // 强制中文：无论是否已有存档/语言选择，每次启动都写入 requiredLang
            if (currentLang != requiredLang) {
                content = if (content.isNotEmpty() && currentLang != "none") {
                    content.replace(langRegex, "lang:$requiredLang")
                } else if (content.isNotEmpty()) {
                    content + "\nlang:$requiredLang"
                } else {
                    "lang:$requiredLang\n"
                }
                VerseLogger.info("已将游戏语言设置为 $requiredLang（原 $currentLang）", TAG)
            } else {
                VerseLogger.info("当前语言已为 $requiredLang，无需修改", TAG)
            }

            optionsFile.parentFile?.mkdirs()
            optionsFile.writeText(content)
        }.onFailure {
            VerseLogger.warn("自动设置中文失败: ${it.message}", TAG)
        }
    }

    /**
     * 每次启动写 preferredGraphicsBackend：GL 渲染器（MobileGlues 等）强制 "opengl"
     * （MobileGlues 是 OpenGL 实现，Vulkan 后端下其窗口/EGL 初始化失败
     * "Failed to query window size in pixels: Invalid window"）；
     * 其余渲染器写 "default"（MC 26.2 据此自选后端）。
     * 注意：不整写旧版 options 模板——MC 26.2 的选项解析器对无引号旧格式行
     * （fov:0.0/clouds:true 等）报 "Skipping bad option"，可能引发启动期异常；
     * options.txt 首次由 MC 自行生成，这里只追加/更新 backend 键。
     */
    private fun injectGraphicsBackend(
        repository: DefaultGameRepository,
        version: Version,
        renderer: String,
        backendOverride: String? = null
    ) {
        runCatching {
            val runDir = repository.getRunDirectory(version.id)
            runDir.mkdirs()
            val optionsFile = File(runDir, "options.txt")
            if (!optionsFile.exists()) {
                optionsFile.writeText("")
            }

            var content = optionsFile.readText()

            // preferredGraphicsBackend：GL 渲染器强制 opengl（Vulkan 后端下 MobileGlues 的
            // 窗口/EGL 初始化失败 "Invalid window"）；其余走 default（MC 自选）。
            // 26.3 起选项类被模块化拆分，后端键名可能非 preferredGraphicsBackend，
            // 对 MobileGlues 额外写入一组候选键（MC 只读取它识别的那个）。
            // 值一律小写（default/opengl/vulkan），大写变体 MC 不识别会回落默认后端。
            val rendererConfig = RendererManager.getRendererById(activity, renderer)
            val isMobileGlues = rendererConfig != null &&
                (rendererConfig.glName.contains("mobileglues", ignoreCase = true) ||
                    rendererConfig.displayName.contains("MobileGlues", ignoreCase = true))
            if (isMobileGlues) {
                content = setOption(content, "preferredGraphicsBackend", "\"opengl\"")
                content = setOption(content, "graphicsApi", "\"opengl\"")
                content = setOption(content, "graphicsBackend", "\"opengl\"")
                content = setOption(content, "apiPreference", "\"opengl\"")
                content = setOption(content, "renderBackend", "\"opengl\"")
            } else {
                // default 由 MC 自选（26.3 探测到 Vulkan 强制 Vulkan），opengl/vulkan 显式指定。
                // 版本级独立设置优先；26.2+ 版本在全局仍是 default 时按实测适配为 vulkan
                // （G925 实测：default 时 MC 探测回落 GL 后端 glGetError mismatch 失败，
                // 显式 vulkan 才走 Krypton/系统驱动正常），用户显式选择的值不受影响
                val backend = backendOverride ?: run {
                    val global = SettingsRepository.getGraphicsBackend()
                    if (global == "default" &&
                        com.verse.explorer.app.device.VulkanCheckManager.hasVulkanBackend(version.id)
                    ) "vulkan" else global
                }
                content = setOption(content, "preferredGraphicsBackend", "\"$backend\"")
            }

            // 只写入渲染后端键；视距/平滑光照/mipmap/垂直同步等交给游戏内设置，
            // 每次启动强制覆盖会导致玩家在游戏里调好的画面设置下一局被重置。
            optionsFile.writeText(content)
            VerseLogger.info("已注入渲染后端: ${SettingsRepository.getGraphicsBackend()}", TAG)
        }.onFailure {
            VerseLogger.warn("注入 preferredGraphicsBackend 失败: ${it.message}", TAG)
        }
    }

    /**
     * options.txt 精确替换/追加单个键。value 为原始 options 行等号后的字面量（字符串需自带引号）。
     * 只改目标键，不整写模板，规避 MC 26.2 对旧格式行 "Skipping bad option" 的启动期异常。
     */
    private fun setOption(content: String, key: String, value: String): String {
        val regex = Regex("^$key:.*$", RegexOption.MULTILINE)
        val line = "${key}:$value"
        return if (regex.containsMatchIn(content)) {
            regex.replace(content, line)
        } else {
            (if (content.trimEnd().isEmpty()) content else content.trimEnd() + "\n") + line + "\n"
        }
    }

    /**
     * 输出 options.txt 中的渲染相关键，仅用于排查画面问题，不修改玩家的游戏内设置。
     */
    private fun dumpRenderOptions(repository: DefaultGameRepository, version: Version, params: GameLaunchParams) {
        runCatching {
            val runDir = repository.getRunDirectory(version.id)
            val optionsFile = File(runDir, "options.txt")
            if (!optionsFile.exists() || !optionsFile.isFile) {
                VerseLogger.warn("[RENDER-DIAG] options.txt 不存在于 ${optionsFile.absolutePath}", TAG)
                return@runCatching
            }
            val content = optionsFile.readText()
            VerseLogger.info("[RENDER-DIAG] ==== options.txt (${optionsFile.absolutePath}) 完整内容 ====", TAG)
            content.lineSequence().forEachIndexed { i, line ->
                if (line.isNotBlank()) VerseLogger.info("[RENDER-DIAG] options[${i}] = $line", TAG)
            }
            VerseLogger.info("[RENDER-DIAG] ==== 与水体/渲染相关的键（缺失即默认） ====", TAG)
            val keys = listOf(
                "preferredGraphicsBackend", "mipmapLevels", "renderDistance", "simulationDistance",
                "ao", "fancyGraphics", "graphicsPreset", "renderClouds", "particles", "gamma",
                "entityShadows", "maxFps", "fullscreen"
            )
            keys.forEach { key ->
                val m = Regex("(?m)^$key:(.*)$").find(content)
                val v = m?.groupValues?.get(1)?.trim() ?: "<缺失/默认>"
                VerseLogger.info("[RENDER-DIAG]   $key = $v", TAG)
            }
            VerseLogger.info("[RENDER-DIAG] 渲染: renderer=${params.renderer}, windowScale=${MenuSetting.windowScale}（窗口缩放实际尺寸见 [9/9 渲染接入] 行）", TAG)
        }.onFailure {
            VerseLogger.warn("[RENDER-DIAG] dump 渲染参数失败: ${it.message}", TAG)
        }
    }

    private suspend fun createAuthInfo(params: GameLaunchParams): AuthInfo {
        // 外置登录：把验证服务器返回的 textures 属性一并交给游戏（--userProperties），
        // 否则客户端拿不到角色皮肤，只能显示默认皮肤
        val userProperties = if (!params.authlibServerUrl.isNullOrEmpty()) {
            val textures = runCatching {
                VerseApplication.authlibAuthenticator
                    .fetchTexturesValue(params.authlibServerUrl, params.uuid)
            }.getOrNull()
            if (textures.isNullOrEmpty()) "{}" else """{"textures":["$textures"]}"""
        } else {
            "{}"
        }
        return AuthInfo(
            params.username,
            parseUuid(params.uuid),
            params.accessToken,
            params.userType,
            userProperties
        )
    }

    private fun parseUuid(raw: String): UUID {
        val compact = raw.replace("-", "")
        if (compact.length != 32) {
            return UUID.nameUUIDFromBytes(raw.toByteArray())
        }
        return UUID.fromString(
            compact.substring(0, 8) + "-" +
            compact.substring(8, 12) + "-" +
            compact.substring(12, 16) + "-" +
            compact.substring(16, 20) + "-" +
            compact.substring(20)
        )
    }

    private fun buildLaunchOptions(
        params: GameLaunchParams,
        javaVersion: JavaVersion,
        verSettings: com.verse.explorer.app.data.VersionSettingsRepository.VersionSettings
    ): LaunchOptions {
        // 强制分辨率（版本级设置）：直接用设定宽高，不按百分比规则换算。
        // setWidth((int)(screenWidth * scaleFactor)) —— 缩放三同步的另外两处：
        // execute 前 setScaleFactor + setDefaultBufferSize/pushEventWindow（见第 9 步）。
        val forceRes = !verSettings.usesGlobal && verSettings.forceResolution &&
            verSettings.width > 0 && verSettings.height > 0
        val resolvedSize = MenuSetting.resolveRenderSize(surfaceWidth, surfaceHeight)
        val effectiveWidth = if (forceRes) {
            verSettings.width
        } else if (surfaceWidth > 0) {
            resolvedSize.first
        } else {
            params.gameWidth
        }
        val effectiveHeight = if (forceRes) {
            verSettings.height
        } else if (surfaceHeight > 0) {
            resolvedSize.second
        } else {
            params.gameHeight
        }
        // 版本隔离：开启时运行目录指向版本文件夹（与 getRunDirectory 一致），
        // 关闭时回到游戏根目录
        val isolated = verSettings.usesGlobal || verSettings.isolateGameDir
        val runDir = if (isolated) {
            File(File(File(params.gameDirPath), "versions"), params.versionId).apply { mkdirs() }
        } else {
            File(params.gameDirPath).apply { mkdirs() }
        }
        val debugLog = if (!verSettings.usesGlobal) verSettings.debugLog else SettingsRepository.getDebugLog()
        return LaunchOptions.Builder()
            .setGameDir(runDir)
            .setJava(javaVersion)
            .setWidth(effectiveWidth)
            .setHeight(effectiveHeight)
            .setMaxMemory(params.maxMemory)
            .setMinMemory(params.minMemory)
            .setRenderer(params.renderer)
            .setUUid(params.uuid)
            .setJavaArguments(params.customJvmArgs.split(" ").filter { it.isNotBlank() })
            .setGameArguments(verSettings.minecraftArgs.split(" ").filter { it.isNotBlank() })
            .setServerIp(verSettings.serverIp)
            .setDebugLog(debugLog)
            .setAuthlibServerUrl(params.authlibServerUrl)
            .create()
    }

    private fun deployNatives(gameDir: File, params: GameLaunchParams): File {
        val externalNativesDir = File(gameDir, "natives")
        val librariesDir = File(gameDir, "libraries")
        val internalNativesDir = nativesDeployer.copyNativesToInternalStorage(externalNativesDir, params.javaHome, librariesDir)
        nativesDeployer.extractDefaultNatives(internalNativesDir)
        return internalNativesDir
    }

    private suspend fun resolveJavaHome(params: GameLaunchParams, gameDir: File): String {
        // 本次启动真正需要的 Java 大版本：参数未指定时按版本清单推断
        val requiredVersion = if (params.javaVersion > 0) params.javaVersion
            else javaDetector.getRequiredJavaVersion(params.versionId, gameDir)

        // 安装器自己的目标目录：files/jre/jre-<大版本>-<架构>
        val installTarget = File(
            JavaRuntimeManager.getJreDir(activity),
            "jre-$requiredVersion-${JavaRuntimeManager.getArchName()}"
        )

        val candidates = mutableListOf<String>()
        if (!params.javaHome.isNullOrEmpty()) candidates.add(params.javaHome)
        // 兜底运行时必须与游戏所需的大版本一致：findJavaHome() 返回的是「最高版本」，
        // 拿 Java 25 启动 1.12 会因 -Xbootclasspath/p 不再支持而直接退出
        findRuntimeForVersion(requiredVersion)?.let {
            if (it != params.javaHome) candidates.add(it)
        }
        candidates.add(installTarget.absolutePath)

        for (home in candidates) {
            val reason = jreInvalidReason(home)
            if (reason == null) return executableJre(home)
            VerseLogger.warn("JRE $home 不可用（$reason）", TAG)
        }

        // 没有可用运行时：交给安装器解包内置运行时。
        // 不删除候选目录：别的版本/别处安装的运行时可能仍在使用，安装器只清空自己的目标目录。
        val javaKey = "java$requiredVersion"
        try {
            JavaRuntimeManager.installJre(activity, javaKey) { progress ->
                VerseLogger.info("JRE 重装进度: ${progress.message}", TAG)
            }
        } catch (e: Exception) {
            VerseLogger.warn("JRE 重装失败: ${e.message}", TAG)
        }

        val reasonAfter = jreInvalidReason(installTarget.absolutePath)
        if (reasonAfter == null) {
            VerseLogger.info("JRE 重装后验证通过: ${installTarget.absolutePath}", TAG)
            return executableJre(installTarget.absolutePath)
        }
        // 安装器可能保留了旧目录或写到了其它位置，最后再按大版本扫一遍
        findRuntimeForVersion(requiredVersion)?.let { newHome ->
            if (jreInvalidReason(newHome) == null) {
                VerseLogger.info("JRE 重装后验证通过: $newHome", TAG)
                return executableJre(newHome)
            }
        }

        throw RuntimeException("未找到完整的 Java $requiredVersion 运行时（需包含 bin/java、lib/libjli.so、lib/libjvm.so、lib/libawt.so），请在设置中配置 JRE 路径")
    }

    /**
     * 校验 JRE 目录能否用于启动：返回 null 表示可用，否则返回缺失项。
     * JRE8 的核心运行库是 lib/rt.jar（安装时由 rt.jar.pack 还原），JDK9+ 是 lib/modules；
     * 与安装器的完整性判定保持一致，避免出现「安装器说已安装、启动又说找不到」的循环。
     */
    private fun jreInvalidReason(home: String): String? {
        val dir = File(home)
        if (!dir.exists()) return "目录不存在"
        if (!File(dir, "bin/java").exists()) return "缺少 bin/java"
        val libDir = File(dir, "lib")
        if (!libDir.isDirectory) return "缺少 lib/"
        if (!javaDetector.findFileInDir(libDir, "libjli.so")) return "缺少 libjli.so"
        if (!javaDetector.findFileInDir(libDir, "libjvm.so")) return "缺少 libjvm.so"
        if (!javaDetector.findFileInDir(libDir, "libawt.so")) return "缺少 libawt.so"
        if (!javaDetector.findFileInDir(libDir, "libfontmanager.so")) return "缺少 libfontmanager.so"
        val classesOk = javaDetector.findNonEmptyFileInDir(libDir, "modules") ||
            (javaDetector.findNonEmptyFileInDir(libDir, "rt.jar") && isRtJarUsable(libDir))
        if (!classesOk) return "核心运行库(modules/rt.jar)缺失或损坏"
        return null
    }

    private fun executableJre(home: String): String {
        ensureJreExecutable(File(home))
        return File(home).absolutePath
    }

    /**
     * 按所需 Java 大版本挑选已安装运行时。
     * 优先取 `jre-<版本>-<架构>` 目录（安装器产出），其余同版本运行时作为备选。
     */
    private fun findRuntimeForVersion(requiredVersion: Int): String? = runCatching {
        JavaRuntimeManager.getInstalledRuntimes(activity)
            .filter { it.version == requiredVersion }
            .sortedByDescending { it.path.name.startsWith("jre-$requiredVersion-") }
            .firstOrNull { javaDetector.isValidJreDir(it.path) }
            ?.path?.absolutePath
    }.getOrNull()

    /**
     * JRE8 核心库可用性：lib 目录下的 rt.jar 能读出 java/lang/Object.class 才算完好。
     * 局部损坏（中央目录正常、条目数据损坏）无法靠文件大小判断，用条目可读性兜底。
     */
    private fun isRtJarUsable(libDir: File): Boolean = runCatching {
        val rtJar = findFileInTree(libDir, "rt.jar") ?: return@runCatching false
        java.util.zip.ZipFile(rtJar).use { zip ->
            zip.getEntry("java/lang/Object.class") != null &&
                zip.getInputStream(zip.getEntry("java/lang/Object.class")).use { it.read() } >= 0
        }
    }.getOrDefault(false)

    private fun findFileInTree(dir: File, name: String): File? {
        if (!dir.exists()) return null
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name == name) return f
            if (f.isDirectory) findFileInTree(f, name)?.let { return it }
        }
        return null
    }

    private fun ensureJreExecutable(jreHome: File) {
        try {
            val binDir = File(jreHome, "bin")
            if (binDir.isDirectory) {
                binDir.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        f.setExecutable(true, false)
                        f.setReadable(true, false)
                    }
                }
            }
            val libDir = File(jreHome, "lib")
            if (libDir.isDirectory) {
                libDir.walkTopDown().forEach { f ->
                    if (f.isFile && f.name.endsWith(".so")) {
                        f.setExecutable(true, false)
                        f.setReadable(true, false)
                    }
                }
            }

            // JRE 自带的 libawt_xawt.so 依赖 X11 无法在 Android 加载，必须替换为我们编译的桩库
            // 即使文件已存在也要覆盖（可能是 JRE 自带版本或之前损坏的版本）
            val libawtFile = File(libDir, "libawt_xawt.so")
            libawtFile.delete()
            JavaRuntimeManager.extractLibFromApk(activity, "libawt_xawt.so", libawtFile)

            // 某些 JRE 将原生库存放在 lib/<arch>/ 子目录中，同步覆盖以覆盖所有加载路径
            val archSubdir = JavaRuntimeManager.detectArchSubdir(libDir)
            if (archSubdir != null) {
                val archAwtFile = File(libDir, "$archSubdir/libawt_xawt.so")
                archAwtFile.delete()
                JavaRuntimeManager.extractLibFromApk(activity, "libawt_xawt.so", archAwtFile)
            }

            VerseLogger.info("JRE 权限已修复: ${jreHome.absolutePath}", TAG)
        } catch (e: Exception) {
            VerseLogger.warn("JRE 权限修复失败: ${e.message}", TAG)
        }
    }

    private suspend fun awaitSurface(): Surface {
        return surfaceDeferred.await()
    }

    private fun analyzeCrash(): CrashResult {
        return crashAnalyzer.analyzeCrash(currentGameDir)
    }

    private fun showCrashReportPage(code: Int) {
        com.verse.explorer.app.ui.game.CrashReportActivity.show(activity, code, currentGameDir) { restartGame() }
    }

    private fun showCrashResultDialog(code: Int, result: CrashResult, crashReport: String?) {
        errorDialog.showCrashResultDialog(code, result, crashReport, isActivityAlive()) { restartGame() }
    }

    private fun showCrashDialog(title: String, message: String, details: String, suggestion: String) {
        errorDialog.showCrashDialog(title, message, details, suggestion, isActivityAlive())
    }

    private fun restartGame() {
        val params = launchParams ?: run {
            restarter.showMissingParamsToast()
            activity.finish()
            return
        }

        // 旧 JVM 已失效，不能在当前进程再建一个：挂起冷启动后结束进程
        val intent = restarter.buildRestartIntent(params)
        scheduleColdStart(activity, intent, RESTART_GAME_REQUEST_CODE)
        activity.finishAffinity()
        killProcessSoon()
    }

    private suspend fun updateProgress(title: String, message: String?) {
        withContext(Dispatchers.Main) {
            statusOverlay.setTitle(title)
            statusOverlay.setMessage(message)
        }
    }
}
