package com.verse.explorer.app.game

import android.view.Surface
import androidx.activity.ComponentActivity
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
import com.verse.explorer.launcher.fcl.util.FCLPath
import com.verse.explorer.jvmlauncher.JvmLauncherAPI
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
                            activity.finish()
                            return@withContext
                        }

                        statusOverlay.setTitle("游戏崩溃退出")
                        statusOverlay.setMessage("退出码: $code，正在分析崩溃原因...")
                        statusOverlay.setIndeterminate(true)
                    }
                }

                val crashResult = analyzeCrash()
                val crashReport = currentGameDir?.let { CrashDetector.getLatestCrashReport(it) }

                if (isActivityAlive()) {
                    withContext(Dispatchers.Main) {
                        showCrashResultDialog(code, crashResult, crashReport)
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
            CallbackBridge.nativeSetInputReady(false)
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
            CallbackBridge.nativeSetInputReady(false)
        } catch (e: Throwable) {
            VerseLogger.error("nativeSetInputReady failed: ${e.message}")
        }
        try {
            CallbackBridge.setFCLBridge(null)
        } catch (e: Throwable) {
            VerseLogger.error("setFCLBridge failed: ${e.message}")
        }
        try {
            VerseLogger.close()
        } catch (e: Throwable) {
            VerseLogger.error("VerseLogger.close failed: ${e.message}")
        }
    }

    fun onSurfaceCreated(surface: Surface) {
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
    }

    private var surfaceWidth = 0
    private var surfaceHeight = 0

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

        // 2) 原生库部署
        updateProgress("部署原生库...", null)
        val nativesDir = deployNatives(gameDir, params)
        VerseLogger.info("[2/9 原生库] 部署到 ${nativesDir.absolutePath}, 耗时=${elapsed(t0)}ms", TAG)

        // 3) FCL 运行时
        updateProgress("部署 FCL 运行时...", null)
        nativesDeployer.deployFclRuntime(gameDir)
        VerseLogger.info("[3/9 FCL运行时] 完成, 耗时=${elapsed(t0)}ms", TAG)

        // 4) Java 运行时检查
        updateProgress("检查 Java 运行时...", null)
        val resolvedJavaHome = resolveJavaHome(params, gameDir)
        javaHome = resolvedJavaHome
        logManager.javaHomeProvider = { javaHome }
        VerseLogger.info("[4/9 Java运行时] 选用 JRE=$resolvedJavaHome, 耗时=${elapsed(t0)}ms", TAG)

        // 5) FCLPath 初始化
        updateProgress("初始化 FCL 路径...", null)
        FCLPath.loadPaths(activity, gameDir.absolutePath)
        VerseLogger.info("[5/9 FCLPath] FILES_DIR=${FCLPath.FILES_DIR}, 耗时=${elapsed(t0)}ms", TAG)

        // 6) 启动选项构建
        updateProgress("构建启动选项...", null)
        val authInfo = createAuthInfo(params)
        val javaVersion = VerseJavaVersion(params.javaVersion, resolvedJavaHome)
        val launchOptions = buildLaunchOptions(params, javaVersion)
        val repository = DefaultGameRepository(gameDir)
        repository.refreshVersions()
        injectOnboardingSkip(repository, params.versionId)
        injectGameLanguage(repository, fclVersion)
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
        runCatching {
            System.loadLibrary("versejvm")
            // native FLOGI 日志：外部可访问路径，Android 10+ 的 /sdcard 根目录不可写，
            // 必须用应用专属外部目录（getExternalFilesDir）。
            val externalBase = activity.getExternalFilesDir(null)
            val diagDir = if (externalBase != null) File(File(externalBase, "VerseExplorerX"), "logs") else null
            if (diagDir != null) diagDir.mkdirs()
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
            VerseLogger.info("[8/9 JVM启动] native 日志: ${(diagDir ?: File(gameDir, "logs")).absolutePath}", TAG)
        }
        VerseLogger.info("[8/9 JVM启动] 调用 DefaultLauncher.launch() ...", TAG)
        val bridge = withContext(Dispatchers.IO) {
            launcher.launch()
        }
        fclBridge = bridge

        isLaunching = false
        VerseLogger.info("[8/9 JVM启动] bridge 返回: $bridge (isValid=${bridge != null}), 总耗时=${System.currentTimeMillis() - t0}ms", TAG)

        // 9) JVM 启动完成，把 Surface 交给游戏渲染线程
        VerseLogger.info("[9/9 渲染接入] bridge.execute(surface=$surface, callback) ...", TAG)
        withContext(Dispatchers.Main) {
            bridge.execute(surface, bridgeCallback)
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
     * 游戏启动自动设置为中文（对照 versepc2 server.js setGameLanguage）：
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

    private fun createAuthInfo(params: GameLaunchParams): AuthInfo {
        return AuthInfo(
            params.username,
            parseUuid(params.uuid),
            params.accessToken,
            params.userType,
            "{}"
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

    private fun buildLaunchOptions(params: GameLaunchParams, javaVersion: JavaVersion): LaunchOptions {
        val effectiveWidth = if (surfaceWidth > 0) surfaceWidth else params.gameWidth
        val effectiveHeight = if (surfaceHeight > 0) surfaceHeight else params.gameHeight
        // ZL2 式版本隔离：-Duser.home 与运行目录指向版本文件夹（与 getRunDirectory 一致）
        val runDir = File(File(File(params.gameDirPath), "versions"), params.versionId).apply { mkdirs() }
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
        val candidates = mutableListOf<String>()
        if (!params.javaHome.isNullOrEmpty()) candidates.add(params.javaHome)
        javaDetector.findJavaHome()?.let { candidates.add(it) }

        var reinstalledJre = false
        var reinstalledPath: String? = null

        for (home in candidates) {
            val file = File(home)
            if (!file.exists() || !File(file, "bin/java").exists()) continue
            val libDir = File(file, "lib")
            val libjliOk = javaDetector.findFileInDir(libDir, "libjli.so")
            val libjvmOk = javaDetector.findFileInDir(libDir, "libjvm.so")
            if (!libjliOk) {
                VerseLogger.warn("JRE $home 缺少 libjli.so", TAG)
                continue
            }
            if (!libjvmOk) {
                VerseLogger.warn("JRE $home 缺少 libjvm.so", TAG)
                continue
            }

            // 检查关键 AWT 库 — 缺失说明 JRE 安装不完整，需要重装
            val libawtOk = javaDetector.findFileInDir(libDir, "libawt.so")
            val libfontmanagerOk = javaDetector.findFileInDir(libDir, "libfontmanager.so")
            if (!libawtOk || !libfontmanagerOk) {
                if (reinstalledJre && reinstalledPath == home) {
                    VerseLogger.warn("JRE 重装后仍缺少 AWT 库，放弃", TAG)
                    continue
                }
                VerseLogger.warn("JRE $home 缺少 AWT 库 (libawt.so=$libawtOk, libfontmanager.so=$libfontmanagerOk)，触发重装", TAG)
                file.deleteRecursively()

                // 触发 JRE 重新安装（同步等待完成）
                val javaKey = "java${params.javaVersion}"
                try {
                    JavaRuntimeManager.installJre(activity, javaKey) { progress ->
                        VerseLogger.info("JRE 重装进度: ${progress.message}", TAG)
                    }
                    reinstalledJre = true
                    reinstalledPath = home
                } catch (e: Exception) {
                    VerseLogger.warn("JRE 重装失败: ${e.message}", TAG)
                }
                continue
            }

            ensureJreExecutable(file)
            return file.absolutePath
        }

        // JRE 重装后可能路径变了，重新扫描一次
        if (reinstalledJre) {
            javaDetector.findJavaHome()?.let { newHome ->
                val file = File(newHome)
                if (file.exists() && File(file, "bin/java").exists()) {
                    val libDir = File(file, "lib")
                    if (javaDetector.findFileInDir(libDir, "libawt.so") &&
                        javaDetector.findFileInDir(libDir, "libfontmanager.so")) {
                        ensureJreExecutable(file)
                        VerseLogger.info("JRE 重装后验证通过: $newHome", TAG)
                        return newHome
                    }
                }
            }
        }

        val requiredVersion = if (params.javaVersion > 0) params.javaVersion
            else javaDetector.getRequiredJavaVersion(params.versionId, gameDir)
        throw RuntimeException("未找到完整的 Java $requiredVersion 运行时（需包含 bin/java、lib/libjli.so、lib/libjvm.so），请在设置中配置 JRE 路径")
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

            // 强制覆盖 libawt_xawt.so — FCL-style
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

        hasLaunched = false
        isLaunching = false
        fclBridge = null
        CallbackBridge.setFCLBridge(null)

        val intent = restarter.buildRestartIntent(params)
        activity.finish()
        activity.startActivity(intent)
    }

    private suspend fun updateProgress(title: String, message: String?) {
        withContext(Dispatchers.Main) {
            statusOverlay.setTitle(title)
            statusOverlay.setMessage(message)
        }
    }
}
