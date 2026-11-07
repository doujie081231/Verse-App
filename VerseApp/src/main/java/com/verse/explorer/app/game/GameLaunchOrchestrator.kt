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
import com.verse.explorer.core.launch.GameSessionLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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
    private val lifecycle = GameSessionLifecycle()
    private var launchJob: Job? = null
    private val crashPageShown = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val surfaceDeferred = CompletableDeferred<Surface>()
    private var surfaceReady: Boolean = false

    private val crashAnalyzer = LaunchCrashAnalyzer(activity)
    private val errorDialog = LaunchErrorDialog(activity, statusOverlay, logManager)
    private val restarter = LaunchRestarter(activity)
    // options.txt 注入与 JRE 解析为独立组件：本类只保留 9 步启动编排与生命周期管理
    private val optionsInjector = OptionsInjector(activity)
    private val jreResolver = JreResolver(activity, javaDetector)
    private val nativeEnvironment = NativeLaunchEnvironment(activity)

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

        if (!lifecycle.beginLaunch()) {
            VerseLogger.warn("会话已开始或已停止，忽略启动请求", TAG)
            return
        }

        statusOverlay.show()
        statusOverlay.setTitle("正在启动游戏...")
        statusOverlay.setMessage("版本: ${params.versionId}")
        statusOverlay.setIndeterminate(true)

        launchJob = scope.launch {
            try {
                launchGameInternal(params)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                VerseLogger.error("启动异常: ${e.message}", TAG)
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
        VerseLogger.info("游戏退出，code=$code", TAG)

        // 退出码 6/-1 这类原生崩溃，latest.log 里只有一行被截断的堆栈摘要，
        // 真正定位靠 hs_err_pid*.log 的 Problematic frame。这里把关键几行抄进
        // 启动器自己的日志（随反馈一起上传），避免每次都只能看到"退出码 6"。
        if (code != 0) logNativeCrashFrame()

        // 崩溃时必须在当前调用栈里同步拉起崩溃分析页：
        // JVM 退出后 native 的 exit trap 立刻把 :game 进程带走，协程还没切到主线程
        // 进程就没了（真机日志停在“游戏退出，code=-1”，崩溃页从未出现）。
        // 崩溃页运行在独立 :crash 进程，这里只发出一次 startActivity 即可。
        if (code != 0 && crashPageShown.compareAndSet(false, true)) {
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

                if (code != 0 && isActivityAlive()) {
                    withContext(Dispatchers.Main) {
                        if (!crashPageShown.compareAndSet(false, true)) return@withContext
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
        val action = lifecycle.stop()
        if (action == GameSessionLifecycle.Stop.ALREADY_STOPPED) return
        launchJob?.cancel()
        surfaceDeferred.cancel()
        if (action == GameSessionLifecycle.Stop.BEFORE_JVM) return

        runCatching { JvmLauncherAPI.nativeDestroyJVM() }
            .onFailure { VerseLogger.warn("nativeDestroyJVM 失败: ${it.message}", TAG) }
        runCatching { fclBridge?.thread?.interrupt() }
            .onFailure { VerseLogger.warn("中断游戏线程失败: ${it.message}", TAG) }
        runCatching { CallbackBridge.nativeSetInputReady(false) }
            .onFailure { VerseLogger.warn("复位输入失败: ${it.message}", TAG) }
    }

    fun release() {
        if (!lifecycle.beginRelease()) return
        stopGame()
        scope.cancel()
        if (lifecycle.hasStartedJvm()) {
            runCatching { CallbackBridge.setFCLBridge(null) }
                .onFailure { VerseLogger.warn("清理 FCL 桥失败: ${it.message}", TAG) }
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
        nativeEnvironment.prepare(gameDir, nativesDir, fclVersion.id, params.mcVersionMajor, verSettings)
        VerseLogger.info("[8/9 JVM启动] 调用 DefaultLauncher.launch() ...", TAG)
        val bridge = withContext(Dispatchers.IO) {
            launcher.launch()
        }
        fclBridge = bridge

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
        // SDLSurface 初始化（mWidth=CallbackBridge.windowWidth）依赖该字段，缺失时窗口尺寸 0
        // 导致 SDL/Vulkan surface 尺寸 0，进世界渲染 abort（SIGABRT 退出码 6）。
        // 失败兜底忽略（execute 后 pushEventWindow 仍会经 sendUpdateWindowSize 更新）。
        runCatching {
            org.lwjgl.glfw.CallbackBridge.windowWidth = winWidth
            org.lwjgl.glfw.CallbackBridge.windowHeight = winHeight
        }.onFailure {
            VerseLogger.warn("[9/9 渲染接入] 预置 CallbackBridge.windowWidth/Height 失败: ${it.message}", TAG)
        }
        val started = withContext(Dispatchers.Main) {
            lifecycle.startJvm { bridge.execute(surface, bridgeCallback) }
        }
        if (!started) return
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
        val authlibServerUrl = params.authlibServerUrl
        val userProperties = if (!authlibServerUrl.isNullOrEmpty()) {
            val textures = runCatching {
                VerseApplication.authlibAuthenticator
                    .fetchTexturesValue(authlibServerUrl, params.uuid)
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
