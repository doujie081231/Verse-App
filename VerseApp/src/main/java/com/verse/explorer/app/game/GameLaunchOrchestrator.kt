package com.verse.explorer.app.game

import android.view.Surface
import androidx.activity.ComponentActivity
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
        VerseLogger.info("configure: version=${params.versionId}, java=${params.javaVersion}, renderer=${params.renderer}", TAG)
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

        updateProgress("解析版本信息...", "版本: ${params.versionId}")
        val fclVersion = resolveFclVersion(params, gameDir)
        VerseLogger.info("FCL 版本解析完成: ${fclVersion.id}, mainClass=${fclVersion.getMainClass()}", TAG)

        updateProgress("部署原生库...", null)
        val nativesDir = deployNatives(gameDir, params)
        VerseLogger.info("原生库部署完成: ${nativesDir.absolutePath}", TAG)

        updateProgress("部署 FCL 运行时...", null)
        nativesDeployer.deployFclRuntime(gameDir)
        VerseLogger.info("FCL 运行时部署完成", TAG)

        updateProgress("检查 Java 运行时...", null)
        val resolvedJavaHome = resolveJavaHome(params, gameDir)
        javaHome = resolvedJavaHome
        logManager.javaHomeProvider = { javaHome }
        VerseLogger.info("JRE 路径: $resolvedJavaHome", TAG)

        updateProgress("初始化 FCL 路径...", null)
        FCLPath.loadPaths(activity, gameDir.absolutePath)
        VerseLogger.info("FCLPath 初始化完成: ${FCLPath.FILES_DIR}", TAG)

        updateProgress("构建启动选项...", null)
        val authInfo = createAuthInfo(params)
        val javaVersion = VerseJavaVersion(params.javaVersion, resolvedJavaHome)
        val launchOptions = buildLaunchOptions(params, javaVersion)
        val repository = DefaultGameRepository(gameDir)
        repository.refreshVersions()

        VerseLogger.info("环境准备完成，等待 Surface ready...")
        withContext(Dispatchers.Main) {
            statusOverlay.setTitle("准备渲染窗口...")
        }
        val surface = awaitSurface()

        updateProgress("启动 Java 虚拟机...", "这可能需要几秒钟")
        val launcher = DefaultLauncher(activity, repository, fclVersion, authInfo, launchOptions)
        launcher.setNativesDirectory(nativesDir.absolutePath)
        // 诊断：JVM 的 stdout/stderr 在 Android 上默认丢弃（fd 指向 /dev/null），
        // 重定向到文件便于排查启动期错误（复用既有 native 能力，非新增逻辑）。
        runCatching {
            System.loadLibrary("versejvm")
            com.verse.explorer.jvmlauncher.JvmLauncherAPI.nativeRedirectStdio(
                File(activity.filesDir, "verse_jvm_out.log").absolutePath
            )
        }
        val bridge = withContext(Dispatchers.IO) {
            launcher.launch()
        }
        fclBridge = bridge

        isLaunching = false
        VerseLogger.info("JVM 启动成功，耗时=${System.currentTimeMillis() - t0}ms", TAG)

        withContext(Dispatchers.Main) {
            bridge.execute(surface, bridgeCallback)
        }
    }

    private fun resolveFclVersion(params: GameLaunchParams, gameDir: File): Version {
        val repository = DefaultGameRepository(gameDir)
        repository.refreshVersions()
        return repository.getVersion(params.versionId)
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
