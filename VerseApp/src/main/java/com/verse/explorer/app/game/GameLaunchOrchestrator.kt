package com.verse.explorer.app.game

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.activity.ComponentActivity
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.app.domain.model.launch.GameLaunchParams
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.game.GameStatusOverlay
import com.verse.explorer.app.ui.game.GameInputHandler
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

    /** 崩溃分析页是否已拉起：退出回调与协程两条路径只允许拉起一次 */
    @Volatile
    private var crashPageShown = false
    // JVM 是否已启动过（bridge.execute 已调用）：用于保护 stopGame/release 中的 nativeSetInputReady(false)，
    private var jvmStarted = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val surfaceDeferred = CompletableDeferred<Surface>()
    private var surfaceReady: Boolean = false

    private val crashAnalyzer = LaunchCrashAnalyzer(activity)
    private val errorDialog = LaunchErrorDialog(activity, statusOverlay, logManager)
    private val restarter = LaunchRestarter(activity)
    // options.txt 注入与 JRE 解析为独立组件：本类只保留 9 步启动编排与生命周期管理
    private val optionsInjector = OptionsInjector(activity)
    private val jreResolver = JreResolver(activity, javaDetector)

    fun configure(params: GameLaunchParams) {
        this.launchParams = params
        VerseLogger.info("=== configure 接收启动参数 ===", TAG)
        VerseLogger.info(
            "  versionId=${params.versionId}, mcVersion=${params.mcVersion ?: "<未解析>"}," +
                " mcVersionMajor=${params.mcVersionMajor}",
            TAG
        )
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
        crashPageShown = false

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

        // 退出码 6/-1 这类原生崩溃，latest.log 里只有一行被截断的堆栈摘要，
        // 真正定位靠 hs_err_pid*.log 的 Problematic frame。这里把关键几行抄进
        // 启动器自己的日志（随反馈一起上传），避免每次都只能看到"退出码 6"。
        if (code != 0) logNativeCrashFrame()

        // 崩溃时必须在当前调用栈里同步拉起崩溃分析页：
        // JVM 退出后 native 的 exit trap 立刻把 :game 进程带走，协程还没切到主线程
        // 进程就没了（真机日志停在“游戏退出，code=-1”，崩溃页从未出现）。
        // 崩溃页运行在独立 :crash 进程，这里只发出一次 startActivity 即可。
        if (code != 0 && !crashPageShown) {
            crashPageShown = true
            runCatching { showCrashReportPage(code) }
                .onSuccess { VerseLogger.info("崩溃分析页已拉起（同步，退出码 $code）") }
                .onFailure { VerseLogger.error("拉起崩溃分析页失败: ${it.message}") }
        }

        scope.launch {
            try {
                if (isActivityAlive()) {
                    withContext(Dispatchers.Main) {
                        if (code == 0) {
                            statusOverlay.setTitle("游戏已退出")
                            statusOverlay.setMessage("退出码: $code")
                            // 游戏运行在独立 :game 进程，结束后仅关闭游戏页并结束该进程，
                            // 启动器主界面保持在后台可继续使用
                            activity.finish()
                            killProcessSoon()
                            return@withContext
                        }

                        statusOverlay.setTitle("游戏崩溃退出")
                        statusOverlay.setMessage("退出码: $code，正在分析崩溃原因...")
                        statusOverlay.setIndeterminate(true)
                    }
                }

                val crashReport = currentGameDir?.let { CrashDetector.getLatestCrashReport(it) }

                if (isActivityAlive() && !crashPageShown) {
                    withContext(Dispatchers.Main) {
                        crashPageShown = true
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

    /** 把最新 hs_err_pid*.log 的致命帧/信号/命令行抄进启动器日志（随反馈上传） */
    private fun logNativeCrashFrame() {
        runCatching {
            val dir = File(activity.getExternalFilesDir(null), "VerseExplorerX/logs")
            val hs = dir.listFiles { f -> f.isFile && f.name.startsWith("hs_err_pid") }
                ?.maxByOrNull { it.lastModified() }
                ?: run {
                    VerseLogger.warn("未找到 hs_err_pid*.log（${dir.absolutePath}）", TAG)
                    return
                }
            // 只读开头一段：Problematic frame / siginfo 都在头部，避免整文件读进内存
            val head = StringBuilder()
            hs.bufferedReader().use { r ->
                var n = 0
                while (n < 220) {
                    val line = r.readLine() ?: break
                    head.append(line).append('\n')
                    n++
                }
            }
            val text = head.toString()
            val frame = Regex("(?m)^Problematic frame:////s*////n?////s*(.+)$").find(text)?.groupValues?.get(1)
            val sig = Regex("(?m)^siginfo:.*$").find(text)?.value
            val cmd = Regex("(?m)^Command Line:.*$").find(text)?.value
            VerseLogger.error(
                "hs_err(${hs.name}) ${sig ?: ""} | frame=${frame ?: "未解析到"}" +
                    (cmd?.let { " | ${it.take(150)}" } ?: ""),
                TAG
            )
        }.onFailure { VerseLogger.warn("读取 hs_err 失败: ${it.message}", TAG) }
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

        // 1.5) 模组兼容性预检：不兼容的模组（加载器不对 / MC 版本不符 / Java 不够）
        // 以前只能在游戏里静默失败或崩溃，用户无从得知原因（后台反馈「模组玩不了」）。
        // 这里提前到启动期给出条目式原因；版本设置里关掉「模组检查」可跳过。
        val verSettings = com.verse.explorer.core.data.VersionSettingsRepository(activity)
            .getVersionSettings(params.versionId)
        if (!verSettings.notCheckMod) {
            runModCompatibilityCheck(params, gameDir, verSettings)
        }

        // 2) 原生库部署（静默执行，不在启动界面显示部署过程）
        val nativesDir = deployNatives(gameDir, params)
        VerseLogger.info("[2/9 原生库] 部署到 ${nativesDir.absolutePath}, 耗时=${elapsed(t0)}ms", TAG)

        nativesDeployer.deployFclRuntime(gameDir)
        VerseLogger.info("[3/9 运行时] 完成, 耗时=${elapsed(t0)}ms", TAG)

        // 4) Java 运行时检查
        updateProgress("检查 Java 运行时...", null)
        val resolvedJavaHome = jreResolver.resolveJavaHome(params, gameDir)
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
        val launchOptions = buildLaunchOptions(params, javaVersion, verSettings)
        val repository = DefaultGameRepository(gameDir)
        repository.setRunDirectoryIsolated(verSettings.usesGlobal || verSettings.isolateGameDir)
        repository.refreshVersions()
        optionsInjector.injectOnboardingSkip(repository, params.versionId)
        optionsInjector.injectGameLanguage(repository, fclVersion)
        optionsInjector.injectGraphicsBackend(
            repository, fclVersion, params.renderer,
            if (!verSettings.usesGlobal) verSettings.graphicsBackend else null
        )
        optionsInjector.dumpRenderOptions(repository, fclVersion, params)
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
        val diagDir = LaunchLogPolicy.diagDir(activity.getExternalFilesDir(null))
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
            val logDir = LaunchLogPolicy.baseLogDir(diagDir, gameDir)
            val nativeLogs = LaunchLogPolicy.nativeLogs(logDir)
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetLogPath(
                nativeLogs.native.absolutePath
            )
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeRedirectStdio(
                nativeLogs.jvmOut.absolutePath
            )
            // glfw_hook（EGL/窗口创建/渲染库加载）的文件日志：崩溃现场最后操作点
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                "VERSE_GLFW_LOG_PATH",
                nativeLogs.glfw.absolutePath
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
                nativeLogs.sdl.absolutePath
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
            // 26.2+ 的版本初始化阶段会加载 Vulkan 查询设备能力，与渲染器是不是 OpenGL 无关，
            // 所以必须**无条件**按缓存评估一次设备能力，刷新 vkshim 门控
            // （VulkanCheckManager.needsVulkanShim）。以前只在 Vulkan 渲染器时评估，
            // MobileGlues/OpenGL 的设备永远拿到 shim=0，驱动缺 divisor/fillModeNonSolid 时
            // 就会拿到缺扩展的系统加载器 → 调用未实现入口 → SIGSEGV(pc=0)/退出码 6。
            // FCL 对 26.2+ 一律先做这一步能力评估（它还会据此拦截不支持 26.3 的机器）。
            runCatching {
                if (com.verse.explorer.app.device.VulkanCheckManager.hasVulkanBackend(fclVersion.id)) {
                    val useTurnip = !(if (!verSettings.usesGlobal) verSettings.vkDriverSystem
                    else SettingsRepository.getVkDriverSystem())
                    val driverPath = if (useTurnip) activity.applicationInfo.nativeLibraryDir else null
                    com.verse.explorer.app.device.VulkanCheckManager.ensureSupported(
                        activity, fclVersion.id, useTurnip, driverPath
                    )
                    VerseLogger.info(
                        "Vulkan 能力评估: shim=${com.verse.explorer.app.device.VulkanCheckManager.needsVulkanShim}" +
                            "（版本=${fclVersion.id}, turnip=$useTurnip）", TAG
                    )
                }
            }.onFailure { VerseLogger.warn("Vulkan 能力评估失败（仅影响 vkshim 门控）: ${it.message}", TAG) }

            // RenderPearl 的 Vulkan 后端（26.2+）要求 VK_EXT_vertex_attribute_divisor：
            // LWJGL/SDL 的 Vulkan 加载统一包装到 libvkshim.so（VULKAN_PTR）合成该扩展。
            // 仅 arm64-v8a 真机路径启用（模拟器 x86_64 / OpenGL 后端不受影响）。
            val vulkanEnv = LaunchDevicePolicy.vulkanEnvironment(
                mcVersionMajor = params.mcVersionMajor,
                supported64BitAbis = android.os.Build.SUPPORTED_64_BIT_ABIS.toList(),
                vkDriverSystem = if (!verSettings.usesGlobal) verSettings.vkDriverSystem
                else SettingsRepository.getVkDriverSystem(),
                needsVulkanShim = com.verse.explorer.app.device.VulkanCheckManager.needsVulkanShim,
                pipelineCache = SettingsRepository.getVersexPipelineCache(),
                nativeLibraryDir = activity.applicationInfo.nativeLibraryDir
            )
            if (vulkanEnv != null) {
                // "1"=系统驱动；"0"=Turnip，versesdl 加载链经 liblinkerhook 把 loader 的驱动
                // 加载请求转向 DRIVER_PATH/libvulkan_freedreno.so（不适用设备探测失败自动回落）
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "VULKAN_DRIVER_SYSTEM", if (vulkanEnv.driverSystem) "1" else "0"
                )
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "DRIVER_PATH", vulkanEnv.driverPath
                )
                // 补齐——divisor 合成或 fillModeNonSolid 降级模拟——时才启用包装层；
                // 无条件开启会让缺口不可补的设备（驱动 <1.2）在游戏内加载 Vulkan 句柄时崩溃）
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "VKSHIM_ENABLE", if (vulkanEnv.shimEnabled) "1" else "0"
                )
                VerseLogger.info(
                    "[8/9 JVM启动] VKSHIM_ENABLE=${if (vulkanEnv.shimEnabled) "1" else "0"}" +
                        "（needsVulkanShim=${vulkanEnv.shimEnabled}）",
                    TAG
                )
                // VerseX 渲染器（设置页「VerseX 渲染器/Pipeline 缓存」）：M1 为 pipeline 对象级缓存，
                // 经环境变量传给 vkshim（默认开，VERSERX_PIPELINE_CACHE=0 关闭）
                com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeSetenv(
                    "VERSERX_PIPELINE_CACHE",
                    if (vulkanEnv.pipelineCache) "1" else "0"
                )
                if (vulkanEnv.shimEnabled) {
                    try {
                        org.libsdl.app.SDL.nativeEnableVulkanShim()
                        VerseLogger.info("[8/9 JVM启动] vkshim Vulkan 包装层已启用（VULKAN_PTR 指向 libvkshim.so）", TAG)
                    } catch (t: Throwable) {
                        VerseLogger.error("[8/9 JVM启动] nativeEnableVulkanShim 失败: $t", TAG)
                    }
                } else {
                    VerseLogger.info("[8/9 JVM启动] vkshim 包装层未启用（设备缺口不可补或无缺口）", TAG)
                }
            } else {
                VerseLogger.info("[8/9 JVM启动] 非 arm64/26.x：跳过 Vulkan 环境注入", TAG)
            }
            VerseLogger.info("[8/9 JVM启动] native 日志: ${logDir.absolutePath}", TAG)
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
     * 启动前模组兼容性预检。
     *
     * mods 目录语义必须与 buildLaunchOptions 的运行目录一致：
     * `usesGlobal || isolateGameDir` → 版本隔离，mods 在 versions/<id>/mods。
     * 发现不兼容条目直接抛错停止启动（错误信息走启动失败弹窗），
     * 避免用户装了不生效/进游戏即崩还不知道原因。
     */
    private fun runModCompatibilityCheck(
        params: GameLaunchParams,
        gameDir: File,
        verSettings: com.verse.explorer.core.data.VersionSettingsRepository.VersionSettings
    ) {
        runCatching {
            val versionJsonFile = File(
                com.verse.explorer.core.config.GameConfig.getVersionsDir(gameDir),
                "${params.versionId}/${params.versionId}.json"
            )
            val versionJson = if (versionJsonFile.isFile) {
                runCatching {
                    com.google.gson.JsonParser.parseString(versionJsonFile.readText()).asJsonObject
                }.getOrNull()
            } else null
            val info = LoaderTypeResolver.resolve(versionJson, params.versionId)

            val isolated = LaunchDirectoryPolicy.isIsolated(
                verSettings.usesGlobal,
                verSettings.isolateGameDir
            )
            val modsDir = LaunchDirectoryPolicy.modsDir(
                File(params.gameDirPath),
                params.versionId,
                isolated
            )

            val report = com.verse.explorer.core.mod.ModCompatibilityChecker.check(
                modsDir, info.baseVersionId, info.loaderType, params.javaVersion
            )
            if (report.modCount > 0 && !report.hasIssues) {
                VerseLogger.info("[1.5/9 模组预检] ${report.modCount} 个模组全部兼容", TAG)
            }
            if (!report.hasIssues) return

            val shown = report.issues.take(3).joinToString("\n") {
                "• ${it.file}：${it.reason}"
            }
            val more = report.issues.size - minOf(3, report.issues.size)
            val detail = shown + if (more > 0) "\n• …等共 ${report.issues.size} 个问题" else ""
            VerseLogger.warn("模组兼容性提示（不阻断启动）:\n$detail", TAG)
        }.onFailure { e ->
            if (e is IllegalStateException) throw e
            // 预检自身异常不阻断启动，只记日志
            VerseLogger.warn("模组兼容性预检异常（已跳过）: ${e.message}", TAG)
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

    private fun parseUuid(raw: String): UUID = LaunchUuidPolicy.parse(raw)

    private fun buildLaunchOptions(
        params: GameLaunchParams,
        javaVersion: JavaVersion,
        verSettings: com.verse.explorer.core.data.VersionSettingsRepository.VersionSettings
    ): LaunchOptions {
        // 强制分辨率（版本级设置）：直接用设定宽高，不按百分比规则换算。
        // setWidth((int)(screenWidth * scaleFactor)) —— 缩放三同步的另外两处：
        // execute 前 setScaleFactor + setDefaultBufferSize/pushEventWindow（见第 9 步）。
        val resolvedSize = MenuSetting.resolveRenderSize(surfaceWidth, surfaceHeight)
        val (effectiveWidth, effectiveHeight) = LaunchSizePolicy.resolve(
            LaunchSizePolicy.Input(
                usesGlobal = verSettings.usesGlobal,
                forceResolution = verSettings.forceResolution,
                forceWidth = verSettings.width,
                forceHeight = verSettings.height,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                scaledWidth = resolvedSize.first,
                scaledHeight = resolvedSize.second,
                fallbackWidth = params.gameWidth,
                fallbackHeight = params.gameHeight
            )
        )
        // 版本隔离：开启时运行目录指向版本文件夹（与 getRunDirectory 一致），
        // 关闭时回到游戏根目录
        val isolated = LaunchDirectoryPolicy.isIsolated(
            verSettings.usesGlobal,
            verSettings.isolateGameDir
        )
        val runDir = LaunchDirectoryPolicy.runDir(
            File(params.gameDirPath),
            params.versionId,
            isolated
        ).apply { mkdirs() }
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
        activity.finish()
        killProcessSoon()
    }

    private suspend fun updateProgress(title: String, message: String?) {
        withContext(Dispatchers.Main) {
            statusOverlay.setTitle(title)
            statusOverlay.setMessage(message)
        }
    }
}
