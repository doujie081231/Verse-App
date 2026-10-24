package com.verse.explorer.app.game

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.activity.ComponentActivity
import com.verse.explorer.app.game.compat.FclLaunchPreparation
import com.verse.explorer.app.game.compat.FclRenderSettings
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.game.GameInputHandler
import com.verse.explorer.app.ui.game.GameStatusOverlay
import com.verse.explorer.core.launch.GameRuntimeCallbacks
import com.verse.explorer.core.launch.GameSession
import com.verse.explorer.core.launch.GameSessionState
import com.verse.explorer.core.launch.LaunchRequest
import com.verse.explorer.core.launch.SessionPhase
import com.verse.explorer.launcher.runtime.AndroidRenderTarget
import com.verse.explorer.launcher.runtime.FclGameRuntimeBackend
import com.verse.explorer.launcher.runtime.RuntimeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Android 界面协调层；会话规则由 Core 管理，FCL/JNI 操作由运行后端管理。 */
class GameLaunchOrchestrator(
    private val activity: ComponentActivity,
    bridgeCallback: GameRuntimeCallbacks,
    nativesDeployer: NativesDeployer,
    private val logManager: GameLogManager,
    javaDetector: JavaRuntimeDetector,
    private val statusOverlay: GameStatusOverlay,
    @Suppress("UNUSED_PARAMETER") inputHandler: GameInputHandler
) {
    companion object {
        private const val TAG = "GameLaunchOrchestrator"
        private const val RESTART_GAME_REQUEST_CODE = 0x5647
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var launchParams: LaunchRequest? = null
    private var renderTarget: AndroidRenderTarget? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val errorDialog = LaunchErrorDialog(activity, statusOverlay, logManager)
    private val restarter = LaunchRestarter(activity)
    private val preparation = FclLaunchPreparation(activity, nativesDeployer, logManager, javaDetector,
        { surfaceWidth to surfaceHeight }, ::updateProgress)
    private val backend = FclGameRuntimeBackend(activity, preparation::prepare, bridgeCallback, GameRuntimeLogger, FclRenderSettings)
    private val session = GameSession(backend, scope)
    val state: StateFlow<GameSessionState> get() = session.state
    val javaHome: String? get() = preparation.javaHome
    val fclBridge get() = backend.bridge

    init {
        scope.launch {
            state.collect { current ->
                withContext(Dispatchers.Main) {
                    if (!isActivityAlive()) return@withContext
                    when (current.phase) {
                        SessionPhase.WAITING_FOR_SURFACE -> statusOverlay.setTitle("准备渲染窗口...")
                        SessionPhase.STARTING -> updateProgress("启动 Java 虚拟机...", "这可能需要几秒钟")
                        SessionPhase.RUNNING -> {
                            statusOverlay.setTitle("正在等待游戏画面出现")
                            statusOverlay.setMessage("Java 虚拟机已启动，正在加载游戏画面...")
                            statusOverlay.setIndeterminate(true)
                            statusOverlay.setClosable(true)
                        }
                        SessionPhase.FAILED -> {
                            val failure = current.failure ?: return@withContext
                            VerseLogger.error("启动异常: ${failure.message}", TAG)
                            statusOverlay.setTitle("启动失败")
                            statusOverlay.setMessage(failure.message ?: "未知错误")
                            errorDialog.showCrashDialog("游戏启动失败", failure.message ?: "未知错误",
                                failure.stackTraceToString(), "检查 Java 运行时、版本完整性、原生库部署是否正常", true)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    fun configure(params: LaunchRequest) {
        check(state.value.phase == SessionPhase.IDLE) { "不能重新配置已启动的游戏会话" }
        launchParams = params
        VerseLogger.info("配置游戏启动: $params", TAG)
    }

    fun startLaunch() {
        val params = launchParams
        if (params == null) {
            statusOverlay.setTitle("启动失败")
            statusOverlay.setMessage("未配置启动参数")
            errorDialog.showCrashDialog("启动失败", "未配置启动参数，请重新进入启动页面。",
                "launchParams == null", "返回上一页重新点击启动", isActivityAlive())
            return
        }
        if (!session.start(params)) {
            VerseLogger.warn("重复调用 startLaunch，已忽略", TAG)
            return
        }
        statusOverlay.show()
        statusOverlay.setTitle("正在启动游戏...")
        statusOverlay.setMessage("版本: ${params.versionId}")
        statusOverlay.setIndeterminate(true)
    }

    fun handleGameExit(code: Int) {
        if (!session.onExit(code)) return
        VerseLogger.info("游戏退出，code=$code", TAG)
        scope.launch(Dispatchers.Main) {
            if (!isActivityAlive()) return@launch
            if (code == 0) {
                statusOverlay.setTitle("游戏已退出")
                statusOverlay.setMessage("退出码: $code")
                activity.finishAffinity()
                killProcessSoon()
            } else {
                statusOverlay.setTitle("游戏崩溃退出")
                statusOverlay.setMessage("退出码: $code，正在分析崩溃原因...")
                statusOverlay.setIndeterminate(true)
                com.verse.explorer.app.ui.game.CrashReportActivity.show(activity, code,
                    launchParams?.gameDirPath?.let(::File)) { restartGame() }
            }
        }
    }

    fun stopGame() = session.stop()

    fun release() {
        try { session.release() } finally {
            scope.cancel()
            VerseLogger.close()
        }
    }

    fun onSurfaceCreated(surface: Surface, surfaceTexture: SurfaceTexture?) {
        if (renderTarget != null) return
        val target = AndroidRenderTarget(surface, surfaceTexture, surfaceWidth, surfaceHeight)
        renderTarget = target
        session.onSurfaceCreated(target)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        renderTarget?.let { it.width = width; it.height = height }
        backend.onSurfaceChanged(width, height)
    }

    fun onSurfaceDestroyed() = session.onSurfaceDestroyed()

    private fun isActivityAlive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    private fun restartGame() {
        val params = launchParams ?: run { restarter.showMissingParamsToast(); activity.finish(); return }
        scheduleColdStart(activity, restarter.buildRestartIntent(params), RESTART_GAME_REQUEST_CODE)
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

private object GameRuntimeLogger : RuntimeLog {
    override fun info(message: String, tag: String) = VerseLogger.info(message, tag)
    override fun warn(message: String, tag: String) = VerseLogger.warn(message, tag)
    override fun error(message: String, tag: String) = VerseLogger.error(message, tag)
}
