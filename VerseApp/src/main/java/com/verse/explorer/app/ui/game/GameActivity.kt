package com.verse.explorer.app.ui.game

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import com.verse.explorer.app.R
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.data.version.VersionJsonResolver
import com.verse.explorer.app.domain.model.launch.GameLaunchParams
import com.verse.explorer.app.game.GameLaunchOrchestrator
import com.verse.explorer.app.game.GameLogManager
import com.verse.explorer.app.game.JavaRuntimeDetector
import com.verse.explorer.app.ui.DisplayNotch
import com.verse.explorer.app.game.NativesDeployer
import com.verse.explorer.app.redstone.RedStoneTunnelManager
import com.verse.explorer.app.terracotta.TerracottaController
import com.verse.explorer.app.terracotta.TerracottaMultiplayerDialog
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.launcher.bridge.CallbackBridge
import com.tungsten.fcl.control.view.LogWindow
import com.tungsten.fcl.game.sdl.SdlBridge
import com.tungsten.fcl.setting.MenuSetting
import com.tungsten.fclauncher.bridge.FCLBridge
import com.tungsten.fclauncher.bridge.FCLBridgeCallback
import kotlinx.coroutines.CancellationException
import net.kdt.pojavlaunch.MinecraftGLSurface
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.customcontrols.ControlLayout
import net.kdt.pojavlaunch.customcontrols.CustomControls
import net.kdt.pojavlaunch.customcontrols.keyboard.LwjglCharSender
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput
import net.kdt.pojavlaunch.customcontrols.mouse.GyroControl
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
    private val inputHandler = GameInputHandler(
        { orchestrator.fclBridge },
        this,
        { hitResultType },
        captureViewProvider = { touchOverlay }
    )
    private lateinit var orchestrator: GameLaunchOrchestrator
    private lateinit var textureView: TextureView
    private var surface: Surface? = null
    private var graphicOutputShown = false
    // 输入是否已安全启用：仅由真实首帧信号（onSurfaceTextureUpdated 第2帧 / atlas-blocks 日志）置位。
    // 首次调用 CallbackBridge.nativeSetInputReady 会触发 org.lwjgl.glfw.CallbackBridge 类初始化
    // （loadLibrary libpojavexec.so → JNI_OnLoad），必须在游戏 JVM 已 dlopen pojavexec 之后执行，
    // 否则 UI 线程在 <clinit> 中再 FindClass 会触发 ART AssertNoPendingException 崩溃（见 2.1.17 崩堆栈）。
    // 6 秒兜底只挂载 UI、不置位本标志，因此不会在 JVM 未就绪时触碰 JNI。
    private var inputReady = false
    private var surfaceUpdateCount = 0

    /** FCL GameMenu.hitResultType：触控按下时由 refreshHitResultType 刷新，供 BUILD/FIGHT 手势判定 */
    private var hitResultType = FCLBridge.HIT_RESULT_TYPE_UNKNOWN

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
    var touchOverlay: FrameLayout? = null
    var controlLayout: ControlLayout? = null

    /** FCL 右栏「陀螺仪」接入的陀螺仪控制器（Pojav GyroControl，已移植） */
    var gyroControl: GyroControl? = null

    /** FCL 右栏「快捷输入」接入的字符输入框（Pojav TouchCharInput，已移植） */
    var touchCharInput: TouchCharInput? = null

    /** FCL 右栏「显示日志/自动显示日志」接入的日志窗口（FCL LogWindow 移植） */
    var logWindow: LogWindow? = null

    /** FCL 右栏「鼠标尺寸/偏移」接入的可见光标（FCL CursorView 的简化移植） */
    var cursorView: TextView? = null

    /** 应用鼠标尺寸/偏移到可见光标（FCL initCursorView 的对应，由控制面板滑块调用） */
    fun applyCursorStyle() {
        val view = cursorView ?: return
        val size = (MenuSetting.mouseSize * resources.displayMetrics.density).toInt()
        view.layoutParams = FrameLayout.LayoutParams(
            if (size > 0) size else 1,
            if (size > 0) size else 1
        )
        view.textSize = (MenuSetting.mouseSize * 0.75f).coerceAtLeast(8f)
    }

    /** 当前 FCLBridge（供控制面板的窗口缩放/光标偏移等使用） */
    val currentBridge: FCLBridge?
        get() = if (::orchestrator.isInitialized) orchestrator.fclBridge else null

    /** 双指下滑演示遮罩 + FCL 样式控制面板（不用悬浮窗唤起） */
    private var inGameControl: InGameControlOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // FCL LauncherSettingPage：语音聊天模组需要麦克风，启动时按设置请求 RECORD_AUDIO
            if (SettingsRepository.getRequestMicPermission() &&
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001)
            }

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

            VerseLogger.info("GameActivity.onCreate: intent 参数", "GameActivity")
            VerseLogger.info("  versionId=$versionId, gameDir=$gameDirPath", "GameActivity")
            VerseLogger.info("  javaHome=$javaHome, javaVersion=$javaVersion", "GameActivity")
            VerseLogger.info("  renderer=$renderer, launchMethod=$launchMethod", "GameActivity")
            VerseLogger.info("  memory=$minMemory~$maxMemory M, gc=$gcType", "GameActivity")
            VerseLogger.info("  window=$gameWidth x $gameHeight, multiplayer=$isMultiplayer", "GameActivity")
            VerseLogger.info("  username=$username, userType=$userType", "GameActivity")
            VerseLogger.info("  customJvmArgs=$customJvmArgs", "GameActivity")

            if (versionId == null || gameDirPath == null) {
                Toast.makeText(this, "启动参数错误", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setupImmersiveMode()

            // FCL 控制栏设置：加载 <filesDir>/menu_setting.json（与 FCL FILES_DIR 对齐）
            MenuSetting.init(getFilesDir())

            // 陶瓦联机：注入 VPN 授权 launcher（与 FCL 在联机菜单打开时请求 VPN 授权对应）
            TerracottaController.vpnPrepareLauncher = { _, intent ->
                vpnPermissionLauncher.launch(intent)
            }

            textureView = TextureView(this).apply {
                surfaceTextureListener = this@GameActivity
                setKeepScreenOn(true)
            }
            // 刘海避让：关闭「忽略刘海（全屏）」时游戏容器让出 displayCutout 区
            // （横屏左侧打孔，TextureView 由 FrameLayout 容器 padding 压缩，Surface 同步缩小）
            val gameRoot = FrameLayout(this)
            if (!SettingsRepository.getIgnoreNotch()) {
                gameRoot.setPadding(DisplayNotch.cutoutLeft(this), 0, 0, 0)
            }
            gameRoot.addView(textureView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            setContentView(gameRoot)

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
            // 画面就绪信号兜底：Surface 首帧信号（onSurfaceTextureUpdated）若未触发，
            // 6 秒后仅隐藏加载遮罩，等待真实首帧信号再走完整 onGraphicOutput()。
            // 不能在此调用 onGraphicOutput/setupTouchControls/setupInGameControlOverlay：
            // 它们引用 org.lwjgl.glfw.CallbackBridge（physicalWidth/addGrabListener/nativeSetInputReady），
            // 若游戏 JVM 尚未 dlopen pojavexec，UI 线程首次触发该类 <clinit> → JNI_OnLoad 内 FindClass
            // → ART AssertNoPendingException 崩溃（2.1.17 崩堆栈：onCreate$lambda$9→onGraphicOutput→nativeSetInputReady）。
            // 同时不置 graphicOutputShown：dispatchTouchEvent 在 false 时不向游戏转发触摸（安全）。
            // JVM 出画面后 onSurfaceTextureUpdated 首帧自会走完整 onGraphicOutput()。
            Handler(Looper.getMainLooper()).postDelayed({
                if (!graphicOutputShown && !isFinishing && !isDestroyed) {
                    VerseLogger.warn("画面就绪信号超时（6s），仅隐藏加载遮罩（输入/按钮待真实首帧信号）", "GameActivity")
                    statusOverlay.hide()
                    multiplayerEntryContainer?.visibility = View.VISIBLE
                }
            }, 6000)
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
     * SDL 通过 JNI 调用此方法显示错误对话框（MC 26.3+ RenderPearl 初始化失败时
     * 调 SDL_ShowSimpleMessageBox）。方法名/签名由 libSDL3.so 硬编码解析，
     * 缺失会 NoSuchMethodError 崩溃；实现照搬 FCL org.libsdl.app.SDLActivity。
     */
    fun messageboxShowMessageBox(
        flags: Int,
        title: String?,
        message: String?,
        buttonFlags: IntArray?,
        buttonIds: IntArray?,
        buttonTexts: Array<String>?,
        colors: IntArray?
    ): Int {
        val ids = buttonIds ?: intArrayOf(0)
        val texts = buttonTexts ?: arrayOf("OK")
        if (ids.isEmpty()) return -1
        val selection = intArrayOf(-1)
        val latch = CountDownLatch(1)
        runOnUiThread {
            try {
                val builder = AlertDialog.Builder(this).setCancelable(false)
                if (title != null) builder.setTitle(title)
                val extraButtons = ArrayList<Button>()
                if (ids.size > 3) {
                    val customView = LinearLayout(this)
                    customView.orientation = LinearLayout.VERTICAL
                    if (message != null) {
                        val messageScroll = ScrollView(this)
                        val messageView = TextView(this)
                        messageView.text = message
                        messageScroll.addView(messageView)
                        customView.addView(messageScroll)
                    }
                    for (i in 3 until ids.size) {
                        val button = Button(this)
                        button.text = texts[i]
                        customView.addView(button)
                        extraButtons.add(button)
                    }
                    builder.setView(customView)
                } else if (message != null) {
                    builder.setMessage(message)
                }
                val slotCount = minOf(ids.size, 3)
                for (i in 0 until slotCount) {
                    val id = ids[i]
                    val listener = DialogInterface.OnClickListener { dialog, _ ->
                        selection[0] = id
                        dialog.dismiss()
                    }
                    if (i == slotCount - 1) {
                        builder.setPositiveButton(texts[i], listener)
                    } else if (i == 0 && slotCount == 3) {
                        builder.setNeutralButton(texts[i], listener)
                    } else {
                        builder.setNegativeButton(texts[i], listener)
                    }
                }
                val dialog = builder.create()
                dialog.setOnDismissListener { latch.countDown() }
                for (i in 3 until ids.size) {
                    val id = ids[i]
                    extraButtons[i - 3].setOnClickListener {
                        selection[0] = id
                        dialog.dismiss()
                    }
                }
                dialog.show()
            } catch (e: Throwable) {
                Log.e("GameActivity", "Failed to show SDL message box", e)
                latch.countDown()
            }
        }
        try {
            latch.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return selection[0]
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
        // 【已按要求移除红石按钮】

        multiplayerEntryContainer = container
        addContentView(container, container.layoutParams as ViewGroup.LayoutParams)
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
    }

    /** FCL GameMenu.onPause：抓取态退后台时释放鼠标（发一对 ESC），避免键位卡死 */
    override fun onPause() {
        super.onPause()
        if (inputReady && inputHandler.isGrabbing()) {
            inputHandler.onKeyDown(KeyEvent.KEYCODE_ESCAPE, null)
            inputHandler.onKeyUp(KeyEvent.KEYCODE_ESCAPE, null)
        }
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
        // 仅当输入已安全启用过才复位：类从未初始化时调用会触发首次 loadLibrary（见 inputReady 注释）。
        if (inputReady) {
            CallbackBridge.nativeSetInputReady(false)
            CallbackBridge.setFCLBridge(null)
        }
        VerseLogger.close()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupImmersiveMode()
        // FCL FCLInput.initExternalController：非实体鼠标模式时请求指针捕获（相对移动）
        if (hasFocus && !MenuSetting.isPhysicalMouseMode) {
            touchOverlay?.requestPointerCapture()
        }
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
            if (isTouchOnQuickInput(ev)) {
                // 快捷输入框可见时让 EditText 正常接收焦点与键盘
                return super.dispatchTouchEvent(ev)
            }
            // 双指下滑演示遮罩 / 控制面板可见：事件交给覆盖层视图处理（按钮可点击、手势可响应）
            if (inGameControl?.isOverlayVisible() == true) {
                return super.dispatchTouchEvent(ev)
            }
            // ===== Pojav/FCL 触控分流（手动，事件流完整） =====
            // 手机触屏走普通 touch 事件（pointer capture 不影响触摸源）：
            //   · 虚拟按钮（方向键/功能键）→ 手动转发 touchOverlay（ControlLayout/ControlButton 消费）；
            //   · 空白区 → GameInputHandler（FCL FCLInput 移植），含 hasMoved：滑动过的
            //     手指抬起绝不判 tap（修复滑动误触点击 → 视角跳转，2.1.22）。
            //   · 双指下滑检测必须排除按钮指针：左手按键 + 右手滑动不会误触手势（2.1.21）。
            val gestureSkipIds = virtualControlPointerIndexes(ev)
            if (inGameControl?.trackMultiTouch(ev, gestureSkipIds) == true) {
                VerseLogger.debug("TOUCH consumed by 2-finger swipe", "Control")
                inGameControl?.fireSwipe()
                return true
            }
            // 编辑模式下完整交给 ControlLayout（Pojav 编辑器语义：编辑态由 ControlLayout 接管，
            // 按钮拖动/对话框交互依赖 Android 原生触摸捕获——DOWN 命中的按钮持续接收
            // MOVE/UP，手动坐标分流会因 MOVE 移出按钮区域而丢事件，导致拖动失效）。
            if (controlLayout?.getModifiable() == true) {
                touchOverlay?.dispatchTouchEvent(ev)
                return true
            }
            val buttonIndexes = virtualControlPointerIndexes(ev)
            if (buttonIndexes.isNotEmpty()) {
                touchOverlay?.dispatchTouchEvent(ev)
            }
            val skipIds = mutableSetOf<Int>()
            for (i in buttonIndexes) skipIds.add(ev.getPointerId(i))
            diagDispatch(ev, buttonIndexes, skipIds)
            if (skipIds.size < ev.pointerCount) {
                if (inputHandler.isGrabbing() && MenuSetting.isDisableLeftTouch) {
                    val mainIndex = (0 until ev.pointerCount)
                        .firstOrNull { ev.getPointerId(it) !in skipIds } ?: 0
                    if (ev.getX(mainIndex) <= resources.displayMetrics.widthPixels / 2f) {
                        return true
                    }
                }
                inputHandler.handleTouchEvent(ev, skipIds)
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    /** 分流诊断日志节流（MOVE 采样，仅 logcat） */
    private var lastDispatchDiagTime = 0L

    /** 分流诊断：按钮跳过 / 是否进入 inputHandler / 左触控拦截（定位滑动跳屏根因） */
    private fun diagDispatch(ev: MotionEvent, btnIdx: Set<Int>, skip: Set<Int>) {
        val now = System.currentTimeMillis()
        val isMove = ev.actionMasked == MotionEvent.ACTION_MOVE
        if (isMove && now - lastDispatchDiagTime < 150) return
        lastDispatchDiagTime = now
        val grabbing = inputHandler.isGrabbing()
        val disableLeft = MenuSetting.isDisableLeftTouch
        val toInput = skip.size < ev.pointerCount
        val mainIdx = (0 until ev.pointerCount).firstOrNull { ev.getPointerId(it) !in skip } ?: -1
        val mainX = if (mainIdx >= 0) ev.getX(mainIdx).toInt() else -1
        val leftSkip = toInput && grabbing && disableLeft && mainX <= resources.displayMetrics.widthPixels / 2
        VerseLogger.debug("disp btn=$btnIdx skip=$skip cnt=${ev.pointerCount} toInput=$toInput grab=$grabbing leftDisable=$disableLeft mainX=$mainX leftSkip=$leftSkip", "TouchDiag")
    }

    /** 返回落在虚拟按钮上的指针 index 集合（逐指针判定，支持多指） */
    private fun virtualControlPointerIndexes(ev: MotionEvent): Set<Int> {
        val layout = controlLayout ?: return emptySet()
        val buttons = runCatching { layout.getButtonChildren() }.getOrNull() ?: return emptySet()
        val hit = mutableSetOf<Int>()
        for (i in 0 until ev.pointerCount) {
            val x = ev.getRawX(i)
            val y = ev.getRawY(i)
            for (iface in buttons) {
                val v = iface.getControlView() ?: continue
                if (v.visibility != View.VISIBLE) continue
                if (x >= v.x && x <= v.x + v.width && y >= v.y && y <= v.y + v.height) {
                    hit.add(i)
                    break
                }
            }
        }
        return hit
    }

    private fun isTouchOnMultiplayerButton(ev: MotionEvent): Boolean {
        val container = multiplayerEntryContainer ?: return false
        if (container.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        container.getLocationOnScreen(location)
        return ev.rawX >= location[0] && ev.rawX <= location[0] + container.width &&
            ev.rawY >= location[1] && ev.rawY <= location[1] + container.height
    }

    /** 快捷输入框可见时的命中判断：让 EditText 接收焦点（对应 FCL 的 input_scanner） */
    private fun isTouchOnQuickInput(ev: MotionEvent): Boolean {
        val input = touchCharInput ?: return false
        if (input.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        input.getLocationOnScreen(location)
        return ev.rawX >= location[0] && ev.rawX <= location[0] + input.width &&
            ev.rawY >= location[1] && ev.rawY <= location[1] + input.height
    }

    /** FCL 右栏「联机菜单」按钮：展示游戏内联机入口（陶瓦/红石） */
    fun showMultiplayerEntry() {
        multiplayerEntryContainer?.let {
            it.visibility = View.VISIBLE
            it.bringToFront()
        }
    }

    /** FCL 右栏「快捷输入」按钮：唤出 Pojav TouchCharInput 与软键盘 */
    fun showQuickInput() {
        touchCharInput?.let {
            it.visibility = View.VISIBLE
            it.bringToFront()
            it.requestFocus()
            it.switchKeyboardState()
        }
    }

    override fun onLog(log: String) {
        VerseLogger.info(log.replace("\n", ""), "Minecraft")
        // FCL GameMenu.onLog：日志同时写入游戏内日志窗口（显示日志/自动显示日志开关控制）
        logWindow?.appendLog(log)
        // 兜底：部分版本日志不含 atlas/blocks，但仍以 Surface 首帧渲染为准（见 onSurfaceTextureUpdated）。
        // 输入启用仅由真实信号置位（inputReady），避免在 JVM 未 dlopen pojavexec 时初始化 CallbackBridge JNI。
        if (!inputReady && log.contains("atlas/blocks")) {
            inputReady = true
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

    /** 外接键鼠兜底：物理鼠标 hover/按钮/滚轮在 Activity 层统一接入，避免被视图拦截
     * （对应 FCL JVMActivity.dispatchGenericMotionEvent 转 FCLInput） */
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (inputHandler.handleGenericMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onCursorModeChange(mode: Int) {
        inputHandler.setCursorMode(mode)
        // FCL GameMenu.onCursorModeChange：GUI 态显示光标，抓取态隐藏
        cursorView?.visibility = if (mode == FCLBridge.CursorEnabled) View.VISIBLE else View.GONE
    }

    override fun onHitResultTypeChange(type: Int) {
        // FCL GameMenu.onHitResultTypeChange：保存命中结果供 BUILD/FIGHT 手势判定
        hitResultType = type
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
        setupInGameControlOverlay()
        // FCL GameMenu.setup：logWindow 初始可见 = showLog || autoShowLog；图形输出后 autoShowLog 自动隐藏
        logWindow?.let {
            it.setVisibility(MenuSetting.isShowLog || MenuSetting.isAutoShowLog)
            if (!MenuSetting.isShowLog && MenuSetting.isAutoShowLog) {
                it.setVisibility(false)
            }
        }
    }

    /**
     * 游戏画面就绪后挂载「双指下滑」演示遮罩与 FCL 样式控制面板。
     * 不同于 FCL 的侧边悬浮窗按钮，Verse 用演示遮罩提示手势唤起控制面板。
     */
    private fun setupInGameControlOverlay() {
        if (inGameControl != null) return
        val overlay = InGameControlOverlay(
            activity = this,
            onEsc = {
                inputHandler.onKeyDown(KeyEvent.KEYCODE_ESCAPE, null)
                inputHandler.onKeyUp(KeyEvent.KEYCODE_ESCAPE, null)
            },
            onScreenshot = {
                val bmp = textureView?.bitmap
                val path = InGameControlOverlay.saveScreenshot(bmp, this)
                Toast.makeText(
                    this,
                    if (path != null) "截图已保存: $path" else "截图失败",
                    Toast.LENGTH_LONG
                ).show()
            },
            onExitGame = {
                VerseLogger.info("从控制面板触发退出游戏", "Control")
                stopGame()
                finish()
            },
            onCancelGameGesture = {
                inputHandler.cancelGesture()
            },
            onPhysicalMouseChanged = {
                inputHandler.syncCapturedPointer()
            }
        )
        val hint = overlay.createHintView()
        val panel = overlay.createPanelView()
        addContentView(hint, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addContentView(panel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        hint.visibility = View.GONE
        panel.visibility = View.GONE
        inGameControl = overlay
        // 演示遮罩：游戏画面出现后自动弹出双指下滑提示（git 恢复）
        overlay.showHint()
        // ===== FCL MenuView 悬浮入口对应 =====
        // 控制面板可经右侧竖胶囊悬浮入口唤出（记忆规范：竖着的整个胶囊容器内放圆形图标按钮）。
        // 【已按要求移除菜单按钮，控制面板仅通过双指下滑手势唤出】
        VerseLogger.info("控制面板已挂载（双指下滑唤出）", "Control")
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
            // 照搬 Pojav Tools.updateWindowSize：触控布局坐标变量 ${bottom}/${screen_height}/${margin}
            // 依赖 org.lwjgl.glfw.CallbackBridge.physicalWidth/Height（ControlData.getControlLayoutVariables）。
            // 未设置时 bottom = 0 - height 为负，方向键（▲◀▶▼）被定位到屏幕外不可见。
            org.lwjgl.glfw.CallbackBridge.physicalHeight = resources.displayMetrics.heightPixels
            org.lwjgl.glfw.CallbackBridge.physicalWidth = resources.displayMetrics.widthPixels

            val surface = MinecraftGLSurface(this)
            surface.id = R.id.main_game_render_view

            val layout = ControlLayout(this)
            layout.setModifiable(false)
            controlLayout = layout

            val jsonPath = Tools.CTRLMAP_PATH + "/Default.json"
            val defaultFile = java.io.File(jsonPath)
            // Pojav 整套触控按钮布局迁移：优先从 APK assets 部署官方 default.json
            // （来源 .pojav-src/app_pojavlauncher/src/main/assets/default.json，含 Keyboard/GUI/PRI/SEC/
            // Mouse/Debug/Chat/Tab/3rd/▲◀▶▼/Inv/◇/⬛ 共 16 键）。一次性标记，之后保留用户自定义布局。
            val pojavMark = java.io.File(Tools.CTRLMAP_PATH, ".pojav_default_v1")
            if (!defaultFile.exists() || !pojavMark.exists()) {
                runCatching {
                    assets.open("game/default_controls.json").use { input ->
                        defaultFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    pojavMark.writeText("pojav-default-v1")
                    VerseLogger.info("已部署 Pojav 官方触控布局（16 键）到 $jsonPath", "Touch")
                }.onFailure {
                    VerseLogger.warn("部署 Pojav 官方布局失败，回退 CustomControls 生成: ${it.message}", "Touch")
                    if (!defaultFile.exists()) {
                        CustomControls(this).save(jsonPath)
                    }
                }
            }
            // 默认控制布局与 Pojav 一致：优先 PREF_DEFAULTCTRL_PATH（控制面板「设为默认」写入），
            // 未设置时回退到部署的官方 Default.json；
            // 版本级设置（FCL VersionSetting.controller）：该版本指定了控制布局时优先加载
            val versionLayout = com.verse.explorer.app.data.VersionSettingsRepository(this)
                .getVersionSettings(intent.getStringExtra("versionId") ?: "")
                .takeIf { !it.usesGlobal && it.controller.isNotBlank() }
                ?.let { java.io.File(Tools.CTRLMAP_PATH, it.controller + ".json") }
                ?.takeIf { it.exists() }
            val loadPath = versionLayout?.absolutePath
                ?: LauncherPreferences.PREF_DEFAULTCTRL_PATH
                ?: jsonPath
            layout.loadLayout(loadPath)

            // ===== Pojav/FCL 触控架构 =====
            // Pojav Touchpad view：虚拟鼠标 + GrabListener（isGrabbing 状态同步）。
            // 触摸分发：手机触屏走普通 touch 事件（Activity dispatchTouchEvent 手动分流，
            // 按钮→ControlLayout，空白→GameInputHandler=FCL FCLInput 移植）；
            // 滑动转视角由指针捕获（captured pointer，isGrabbing 时）驱动。
            val touchpad = Touchpad(this, null)
            org.lwjgl.glfw.CallbackBridge.addGrabListener(touchpad)

            // FCL 右栏「陀螺仪」：Pojav GyroControl（已移植），默认按 menu_setting 启用
            val gyro = GyroControl(this)
            gyroControl = gyro
            if (MenuSetting.isEnableGyroscope) {
                gyro.enable()
            }

            // FCL 右栏「快捷输入」：Pojav TouchCharInput（已移植）+ LwjglCharSender
            val charInput = TouchCharInput(this).apply {
                setCharacterSender(LwjglCharSender())
                visibility = View.GONE
            }
            touchCharInput = charInput

            // FCL 右栏「显示日志/自动显示日志」：FCL LogWindow 移植
            val logWin = LogWindow(this)
            logWindow = logWin

            // FCL 右栏「鼠标尺寸/偏移」：可见光标（FCL CursorView 简化移植：GUI 态显示圆点）
            val cursor = TextView(this).apply {
                text = "●"
                setTextColor(Color.WHITE)
                setShadowLayer(4f, 0f, 0f, Color.BLACK)
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            cursorView = cursor
            applyCursorStyle()

            val density = resources.displayMetrics.density

            val overlay = FrameLayout(this)
            overlay.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            overlay.addView(touchpad, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            overlay.addView(layout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            overlay.addView(logWin, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (200 * density).toInt(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = (40 * density).toInt() })
            overlay.addView(charInput, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = (16 * density).toInt() })
            overlay.addView(cursor, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            this.addContentView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            touchOverlay = overlay

            // 指针捕获（相对移动）接 inputHandler；hover/按钮/滚轮由 Activity 层
            // dispatchGenericMotionEvent 统一分发，避免双份处理
            overlay.setOnCapturedPointerListener { _, ev -> inputHandler.handleCapturedPointer(ev) }

            // FCL CursorView 跟随：GUI 态下指针移动时更新可见光标位置（含偏移）
            inputHandler.cursorMoveListener = { x, y ->
                cursorView?.let {
                    it.x = x + MenuSetting.mouseOffsetX * density
                    it.y = y + MenuSetting.mouseOffsetY * density
                }
            }

            layout.setControlVisible(true)
            // 挂载后按当前物理鼠标模式与抓取状态同步一次指针捕获（JVM 早于 UI 就绪的抓取态）
            inputHandler.syncCapturedPointer()
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
            orchestrator.onSurfaceCreated(s, surfaceTexture)
            orchestrator.onSurfaceChanged(width, height)
        }
        // MC 26.3+ 走 SDL 渲染路径：Surface 就绪即注册到 SdlBridge，
        // 供游戏 JVM 内 notifyLauncher(ACTION_INIT_LAUNCHER_INTEGRATION) 完成 SDL3 绑定。
        // layout 参数必须传 TextureView 的父容器（对齐 FCL JVMActivity 的
        // (ViewGroup) textureView.getParent()）：SDLActivity.externalInitialize 把它
        // 存为 mLayout，游戏内启用相对鼠标时 SDLControllerManager 对 mLayout 调
        // requestPointerCapture()，传 null 会在进世界时 NPE → JVM abort
        SdlBridge.prepareSurface(this, s, textureView.parent as ViewGroup)
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        VerseLogger.info("Surface texture size changed: ${width}x${height}")
        if (::orchestrator.isInitialized) {
            orchestrator.onSurfaceChanged(width, height)
            // 照搬 FCL JVMActivity.onSurfaceTextureSizeChanged（L141-150）：
            // 尺寸变化时同步 setDefaultBufferSize + pushEventWindow，保证窗口缩放不产生错位黑块
            val scaleFactor = MenuSetting.windowScale
            var winWidth = ((width + MenuSetting.cursorOffset) * scaleFactor).toInt()
            var winHeight = (height * scaleFactor).toInt()
            if (FCLBridge.FORCE_RESOLUTION) {
                winWidth = FCLBridge.FORCE_RESOLUTION_WIDTH
                winHeight = FCLBridge.FORCE_RESOLUTION_HEIGHT
            }
            val bridge = orchestrator.fclBridge
            if (bridge != null) {
                surfaceTexture.setDefaultBufferSize(winWidth, winHeight)
                // pushEventWindow 依赖游戏 JVM 侧 org.lwjgl.glfw.CallbackBridge；只在游戏已运行
                // （execute 完成、类已成功初始化）后推送，execute 前调用会污染类状态导致启动失败。
                if (graphicOutputShown) {
                    runCatching {
                        bridge.pushEventWindow(winWidth, winHeight)
                    }
                }
            }
            // SDL 渲染路径同步窗口尺寸（MC 26.3+）；surfaceChanged 内部按 SdlBridge 启用态自行跳过
            val sdlSurface = org.libsdl.app.SDLActivity.getSDLSurface()
            if (sdlSurface != null && SdlBridge.sdlEnabled) {
                runCatching {
                    sdlSurface.surfaceChanged()
                    sdlSurface.nativeResize(winWidth, winHeight)
                }
            }
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        VerseLogger.info("Surface texture destroyed")
        if (::orchestrator.isInitialized) {
            orchestrator.onSurfaceDestroyed()
        }
        SdlBridge.unregisterSurface(surface)
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // FCL 原版以 Surface 首帧渲染作为图形就绪信号（JVMActivity.onSurfaceTextureUpdated），
        // 此时 JVM 窗口已建立、开始出画面，才隐藏覆盖层并启用输入。
        // 跳过第一帧避免初始空帧误触发，从第二帧起判定。
        // 仅当 inputReady 未置位时处理：6 秒兜底只挂 UI 不置位，真实首帧到达后仍会补发输入启用。
        if (!inputReady) {
            surfaceUpdateCount++
            if (surfaceUpdateCount >= 2) {
                inputReady = true
                graphicOutputShown = true
                onGraphicOutput()
            }
        }
    }
}
