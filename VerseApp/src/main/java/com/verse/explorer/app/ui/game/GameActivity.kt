package com.verse.explorer.app.ui.game

import android.app.AlertDialog
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.verse.explorer.app.data.version.VersionJsonResolver
import com.verse.explorer.app.domain.model.launch.GameLaunchParams
import com.verse.explorer.app.game.GameLaunchOrchestrator
import com.verse.explorer.app.game.GameLogManager
import com.verse.explorer.app.game.JavaRuntimeDetector
import com.verse.explorer.app.game.NativesDeployer
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.launcher.bridge.CallbackBridge
import com.tungsten.fclauncher.bridge.FCLBridgeCallback
import kotlinx.coroutines.CancellationException

class GameActivity : ComponentActivity(), FCLBridgeCallback, SurfaceHolder.Callback {

    private val logManager = GameLogManager(this)
    private val versionResolver = VersionJsonResolver { logManager.appendStatusLog(it) }
    private val nativesDeployer = NativesDeployer(this) { logManager.appendStatusLog(it) }
    private val statusOverlay = GameStatusOverlay(this, logManager)
    private val javaDetector = JavaRuntimeDetector(
        this, { logManager.appendStatusLog(it) }, statusOverlay, versionResolver
    )
    private val inputHandler = GameInputHandler { orchestrator.fclBridge }
    private lateinit var orchestrator: GameLaunchOrchestrator
    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val versionId = intent.getStringExtra("versionId")
            val gameDirPath = intent.getStringExtra("gameDir")
            val javaHome = intent.getStringExtra("javaHome")

            val javaVersion = intent.getIntExtra("javaVersion", 0)
            val maxMemory = intent.getIntExtra("maxMemory", 2048)
            val minMemory = intent.getIntExtra("minMemory", 512)
            val gcType = intent.getStringExtra("gcType") ?: "G1GC"
            val customJvmArgs = intent.getStringExtra("customJvmArgs") ?: ""
            val gameWidth = intent.getIntExtra("width", 854)
            val gameHeight = intent.getIntExtra("height", 480)
            val renderer = intent.getStringExtra("renderer") ?: "gl4es"
            val launchMethod = intent.getStringExtra("launchMethod") ?: "pojav"
            val username = intent.getStringExtra("username") ?: "Player"
            val uuid = intent.getStringExtra("uuid") ?: "00000000000000000000000000000000"
            val isMultiplayer = intent.getBooleanExtra("multiplayerMode", false)
            val accessToken = intent.getStringExtra("accessToken") ?: "0"
            val userType = intent.getStringExtra("userType") ?: "mojang"

            if (versionId == null || gameDirPath == null) {
                Toast.makeText(this, "启动参数错误", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setupImmersiveMode()

            surfaceView = SurfaceView(this).apply {
                holder.addCallback(this@GameActivity)
                setKeepScreenOn(true)
            }
            setContentView(surfaceView)

            val overlayView = statusOverlay.createOverlay()
            addContentView(overlayView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))

            logManager.initGameLog(gameDirPath, versionId, javaVersion, launchMethod)

            orchestrator = GameLaunchOrchestrator(
                activity = this,
                bridgeCallback = this,
                nativesDeployer = nativesDeployer,
                logManager = logManager,
                javaDetector = javaDetector,
                statusOverlay = statusOverlay,
                inputHandler = inputHandler
            )
            logManager.javaHomeProvider = { orchestrator.javaHome }

            orchestrator.configure(
                GameLaunchParams(
                    versionId = versionId,
                    gameDirPath = gameDirPath,
                    javaHome = javaHome,
                    javaVersion = javaVersion,
                    maxMemory = maxMemory,
                    minMemory = minMemory,
                    gcType = gcType,
                    customJvmArgs = customJvmArgs,
                    gameWidth = gameWidth,
                    gameHeight = gameHeight,
                    renderer = renderer,
                    launchMethod = launchMethod,
                    username = username,
                    uuid = uuid,
                    isMultiplayer = isMultiplayer,
                    accessToken = accessToken,
                    userType = userType
                )
            )

            orchestrator.startLaunch()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            VerseLogger.error("GameActivity onCreate failed: ${e.message}\n${e.stackTraceToString()}")
            if (!isFinishing && !isDestroyed) {
                AlertDialog.Builder(this)
                    .setTitle("启动失败")
                    .setMessage("${e.javaClass.simpleName}\n${e.message}")
                    .setCancelable(false)
                    .setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
                    .setOnDismissListener { finish() }
                    .show()
            }
        }
    }

    private fun setupImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
    }

    private fun stopGame() {
        if (::orchestrator.isInitialized) {
            orchestrator.stopGame()
        }
    }

    override fun onDestroy() {
        stopGame()
        try {
            if (::orchestrator.isInitialized) {
                orchestrator.release()
            }
        } catch (e: Throwable) {
            VerseLogger.error("orchestrator.release() failed: ${e.message}")
        }
        CallbackBridge.nativeSetInputReady(false)
        CallbackBridge.setFCLBridge(null)
        VerseLogger.close()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
    }

    override fun onLog(log: String) {
        VerseLogger.info(log.replace("\n", ""), "Minecraft")
    }

    override fun onExit(code: Int) {
        orchestrator.handleGameExit(code)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (inputHandler.onKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (inputHandler.onKeyUp(keyCode, event)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onCursorModeChange(mode: Int) {
        inputHandler.setCursorMode(mode)
    }

    override fun onHitResultTypeChange(type: Int) {
    }

    override fun onGraphicOutput() {
        statusOverlay.hide()
        CallbackBridge.nativeSetInputReady(true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        VerseLogger.info("Surface created")
        if (::orchestrator.isInitialized) {
            orchestrator.onSurfaceCreated(holder.surface)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        VerseLogger.info("Surface changed: ${width}x${height}")
        if (::orchestrator.isInitialized) {
            orchestrator.onSurfaceChanged(width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        VerseLogger.info("Surface destroyed")
        if (::orchestrator.isInitialized) {
            orchestrator.onSurfaceDestroyed()
        }
    }
}
