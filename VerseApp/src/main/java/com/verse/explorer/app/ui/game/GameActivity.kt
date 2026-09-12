package com.verse.explorer.app.ui.game

import android.app.AlertDialog
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import com.verse.explorer.app.R
import com.verse.explorer.app.data.version.VersionJsonResolver
import com.verse.explorer.app.domain.model.launch.GameLaunchParams
import com.verse.explorer.app.game.GameLaunchOrchestrator
import com.verse.explorer.app.game.GameLogManager
import com.verse.explorer.app.game.JavaRuntimeDetector
import com.verse.explorer.app.game.NativesDeployer
import com.verse.explorer.app.redstone.RedStoneTunnelDialog
import com.verse.explorer.app.redstone.RedStoneTunnelManager
import com.verse.explorer.app.terracotta.TerracottaController
import com.verse.explorer.app.terracotta.TerracottaMultiplayerDialog
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.launcher.bridge.CallbackBridge
import com.tungsten.fclauncher.bridge.FCLBridgeCallback
import kotlinx.coroutines.CancellationException
import net.kdt.pojavlaunch.MinecraftGLSurface
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.customcontrols.ControlLayout
import net.kdt.pojavlaunch.customcontrols.CustomControls
import net.kdt.pojavlaunch.customcontrols.mouse.Touchpad
import net.kdt.pojavlaunch.prefs.LauncherPreferences

class GameActivity : ComponentActivity(), FCLBridgeCallback, TextureView.SurfaceTextureListener {

    private val logManager = GameLogManager(this)
    private val versionResolver = VersionJsonResolver { logManager.appendStatusLog(it) }
    private val nativesDeployer = NativesDeployer(this) { logManager.appendStatusLog(it) }
    private val statusOverlay = GameStatusOverlay(this, logManager)
    private val javaDetector = JavaRuntimeDetector(
        this, { logManager.appendStatusLog(it) }, statusOverlay, versionResolver
    )
    private val inputHandler = GameInputHandler { orchestrator.fclBridge }
    private lateinit var orchestrator: GameLaunchOrchestrator
    private lateinit var textureView: TextureView
    private var surface: Surface? = null
    private var graphicOutputShown = false
    private var surfaceUpdateCount = 0

    /** 陶瓦联机：VPN 授权结果回传，与 FCL 的 startActivityForResult(RequestCodes.VPN) 对应 */
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        TerracottaController.handleVpnPrepareResult(this, result.resultCode)
    }

    /** 游戏内联机入口容器（陶瓦 + 红石按钮竖排，与 FCL 右菜单联机入口对应） */
    private var multiplayerEntryContainer: LinearLayout? = null

    /** 联机菜单打开期间吞掉触摸，避免透传给游戏输入 */
    private var multiplayerDialogOpen = false

    // ===== Pojav 触控模块迁移：游戏内虚拟按键/摇杆/触摸板覆盖层 =====
    private var touchOverlay: FrameLayout? = null
    private var controlLayout: net.kdt.pojavlaunch.customcontrols.ControlLayout? = null

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

            // 陶瓦联机：注入 VPN 授权 launcher（与 FCL 在联机菜单打开时请求 VPN 授权对应）
            TerracottaController.vpnPrepareLauncher = { _, intent ->
                vpnPermissionLauncher.launch(intent)
            }

            textureView = TextureView(this).apply {
                surfaceTextureListener = this@GameActivity
                setKeepScreenOn(true)
            }
            setContentView(textureView)

            val overlayView = statusOverlay.createOverlay()
            addContentView(overlayView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))

            setupMultiplayerEntry()

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

    /**
     * 游戏内联机入口：与 FCL 的 RightMenuAdapter 联机菜单项对应。
     * 陶瓦入口需在设置页启用多人联机；红石入口在设备带有 arm64 内核时显示。
     */
    private fun setupMultiplayerEntry() {
        val terracottaEnabled = getSharedPreferences("third_party", MODE_PRIVATE)
            .getBoolean("terracotta", false)
        val redstoneAvailable = RedStoneTunnelManager.isAvailable(this)
        if (!terracottaEnabled && !redstoneAvailable) return

        val density = resources.displayMetrics.density

        fun entryButton(text: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit): Button =
            Button(this).apply {
                this.text = text
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(VerseColors.PcAccentText.toArgb())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10 * density
                    setColor(VerseColors.PcBgSecondary.toArgb())
                    setStroke((1 * density).toInt(), tint.toArgb())
                }
                setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
                alpha = 0.75f
                setOnClickListener { onClick() }
            }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = (16 * density).toInt()
                marginEnd = (16 * density).toInt()
            }
            visibility = View.GONE
        }

        if (terracottaEnabled) {
            container.addView(entryButton("联机", VerseColors.PcBorder) {
                multiplayerDialogOpen = true
                val dialog = TerracottaMultiplayerDialog(this@GameActivity)
                dialog.onDismissed = { multiplayerDialogOpen = false }
                dialog.show()
            })
        }
        if (redstoneAvailable) {
            val redstoneBtn = entryButton("红石", VerseColors.PcGreen) {
                multiplayerDialogOpen = true
                val dialog = RedStoneTunnelDialog(this@GameActivity)
                dialog.onDismissed = { multiplayerDialogOpen = false }
                dialog.show()
            }
            (redstoneBtn.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.topMargin = (8 * density).toInt()
                redstoneBtn.layoutParams = params
            }
            container.addView(redstoneBtn)
        }

        multiplayerEntryContainer = container
        addContentView(container, container.layoutParams as ViewGroup.LayoutParams)
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
        // 与 FCL 的 JVMActivity.onDestroy 对应：退出游戏时复位陶瓦联机状态并关闭 VPN
        try {
            TerracottaController.setWaiting(this, true)
        } catch (e: Throwable) {
            VerseLogger.error("Terracotta setWaiting failed: ${e.message}")
        }
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (graphicOutputShown) {
            if (multiplayerDialogOpen) {
                // 联机菜单打开时不把触摸透传给游戏
                return true
            }
            if (isTouchOnMultiplayerButton(ev)) {
                // 让联机按钮正常接收点击
                return super.dispatchTouchEvent(ev)
            }
            // Pojav 触控模块：触摸命中虚拟按键/摇杆时交给覆盖层消费，
            // 其余区域直接转发给游戏（等价于 Pojav 中 MinecraftGLSurface 接住非按钮触摸）
            if (isTouchOnVirtualControl(ev)) {
                touchOverlay?.dispatchTouchEvent(ev)
                return true
            }
            VerseLogger.debug("TOUCH forward a=${ev.actionMasked} x=${ev.x.toInt()} y=${ev.y.toInt()}", "Input")
            inputHandler.handleTouchEvent(ev)
            return true
        }
        VerseLogger.debug("TOUCH skip a=${ev.actionMasked} (not graphic output yet)", "Input")
        return super.dispatchTouchEvent(ev)
    }

    /** 判断触摸点是否落在某个虚拟按键/摇杆上（Pojav ControlLayout 子控件区域内） */
    private fun isTouchOnVirtualControl(ev: MotionEvent): Boolean {
        val layout = controlLayout ?: return false
        return runCatching {
            layout.getButtonChildren().any { iface ->
                val v = iface.getControlView() ?: return@any false
                if (v.visibility != View.VISIBLE) return@any false
                ev.rawX >= v.x && ev.rawX <= v.x + v.width &&
                    ev.rawY >= v.y && ev.rawY <= v.y + v.height
            }
        }.getOrDefault(false)
    }

    private fun isTouchOnMultiplayerButton(ev: MotionEvent): Boolean {
        val container = multiplayerEntryContainer ?: return false
        if (container.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        container.getLocationOnScreen(location)
        return ev.rawX >= location[0] && ev.rawX <= location[0] + container.width &&
            ev.rawY >= location[1] && ev.rawY <= location[1] + container.height
    }

    override fun onLog(log: String) {
        VerseLogger.info(log.replace("\n", ""), "Minecraft")
        // 兜底：部分版本日志不含 atlas/blocks，但仍以 Surface 首帧渲染为准（见 onSurfaceTextureUpdated）。
        if (!graphicOutputShown && log.contains("atlas/blocks")) {
            graphicOutputShown = true
            runOnUiThread { onGraphicOutput() }
        }
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

    fun onGraphicOutput() {
        VerseLogger.debug("onGraphicOutput fired, surface=${textureView?.width}x${textureView?.height}", "Input")
        statusOverlay.hide()
        multiplayerEntryContainer?.visibility = View.VISIBLE
        // 窗口尺寸由 FCL execute->handleWindow->setupBridgeWindow 从 ANativeWindow 取得（全屏尺寸），
        // 此处无需重复推送；isInputReady 打开后再推送会触发 GLFW WindowSize 回调，可能干扰渲染循环。
        // textureView?.let { CallbackBridge.sendUpdateWindowSize(it.width, it.height) }
        CallbackBridge.nativeSetInputReady(true)
        setupTouchControls()
    }

    /**
     * Pojav 触控模块迁移：游戏画面就绪后挂载虚拟按键/摇杆/触摸板覆盖层。
     * 默认触控布局从 Pojav 默认按钮集生成（首次自动写入 Default.json），
     * 输入经 org.lwjgl.glfw.CallbackBridge 直达游戏 GLFW 窗口。
     */
    private fun setupTouchControls() {
        if (touchOverlay != null) return
        try {
            Tools.init(this)
            LauncherPreferences.init(this)

            val surface = MinecraftGLSurface(this)
            surface.id = R.id.main_game_render_view

            val layout = ControlLayout(this)
            layout.setModifiable(false)
            controlLayout = layout

            val jsonPath = Tools.CTRLMAP_PATH + "/Default.json"
            if (!java.io.File(jsonPath).exists()) {
                CustomControls(this).save(jsonPath)
            }
            layout.loadLayout(jsonPath)

            val touchpad = Touchpad(this, null)
            org.lwjgl.glfw.CallbackBridge.addGrabListener(touchpad)

            val overlay = FrameLayout(this)
            overlay.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            overlay.addView(touchpad, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            overlay.addView(layout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            this.addContentView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            touchOverlay = overlay

            layout.setControlVisible(true)
            VerseLogger.info("触控覆盖层已挂载: layout=${layout.mLayoutFileName}, buttons=${layout.getButtonChildren().size}", "Touch")
        } catch (t: Throwable) {
            VerseLogger.error("触控覆盖层挂载失败: ${t.message}", "Touch")
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        VerseLogger.info("Surface texture available: ${width}x${height}")
        val s = Surface(surfaceTexture)
        surface = s
        if (::orchestrator.isInitialized) {
            orchestrator.onSurfaceCreated(s)
            orchestrator.onSurfaceChanged(width, height)
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        VerseLogger.info("Surface texture size changed: ${width}x${height}")
        if (::orchestrator.isInitialized) {
            orchestrator.onSurfaceChanged(width, height)
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        VerseLogger.info("Surface texture destroyed")
        if (::orchestrator.isInitialized) {
            orchestrator.onSurfaceDestroyed()
        }
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // FCL 原版以 Surface 首帧渲染作为图形就绪信号（JVMActivity.onSurfaceTextureUpdated），
        // 此时 JVM 窗口已建立、开始出画面，才隐藏覆盖层并启用输入。
        // 跳过第一帧避免初始空帧误触发，从第二帧起判定。
        if (!graphicOutputShown) {
            surfaceUpdateCount++
            if (surfaceUpdateCount >= 2) {
                graphicOutputShown = true
                onGraphicOutput()
            }
        }
    }
}
