package com.verse.explorer.app.ui.game

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tungsten.fcl.setting.MenuSetting
import com.tungsten.fclauncher.bridge.FCLBridge
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.launcher.keycodes.LwjglGlfwKeycode
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.customcontrols.ControlData
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData
import net.kdt.pojavlaunch.customcontrols.buttons.ControlJoystick
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import java.io.File
import java.io.FileOutputStream

/**
 * - 左栏「控制」= menu_left.xml 全量行：当前布局方案、编辑模式、显示控件边缘、隐藏所有控件、
 *   自动吸附、自动吸附间距、当前控件组、控件组管理、添加按键、添加方向控制键、按键样式、方向控制键样式；
 * - 右栏「设置」= menu_right.xml 全量行：功能（锁定菜单键/隐藏菜单键/显示FPS/联机菜单/快捷输入/发送键值/
 *   禁用软键盘自适应/物品栏缩放/窗口比例/光标偏移）、手势、鼠标、手柄、陀螺仪、调试分区；
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class InGameControlOverlay(
    private val activity: GameActivity,
    private val onEsc: () -> Unit,
    private val onScreenshot: () -> Unit,
    private val onExitGame: () -> Unit,
    private val onCancelGameGesture: () -> Unit,
    private val onPhysicalMouseChanged: (Boolean) -> Unit = {}
) {

    // ===== Verse 暗色玻璃配色（对齐软件暗色主题：暗面板 + 亮文字 + 主题强调） =====
    private val fclBg = Color.argb(0xD9, 0x14, 0x14, 0x14)      // 面板底：#D9141414 近实暗玻璃
    private val verseRowBg = Color.argb(0x66, 0x2A, 0x2A, 0x2A) // 行卡片：半透明深灰
    private val fclText = Color.parseColor("#F0F0F0")           // 主文字（暗主题 primary）
    private val fclAccent = Color.parseColor("#E8D5D5")         // 强调（暖白，随软件玻璃调性）
    private val fclSecondary = Color.parseColor("#9E9E9E")      // 次级文字
    private val fclDivider = Color.argb(0x40, 0xFF, 0xFF, 0xFF) // 分隔线

    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).toInt()

    /** 设置行构建器（标签 + 控件行、开关行、下拉行、滑条行、按钮行） */
    private val rows = ControlRowBuilder(activity, fclAccent, verseRowBg, fclText)

    private var hintView: View? = null
    private var panelView: View? = null
    private var leftBar: View? = null
    private var rightBar: View? = null
    private var chevronView: ChevronSwipeView? = null
    private var hintAutoHide: Handler? = null

    private var hintVisible = false
    private var panelVisible = false

    private var fpsView: TextView? = null
    private var memView: TextView? = null
    private var infoContainer: LinearLayout? = null
    private var infoHandler: Handler? = null
    private var fpsEnabled = false
    private var memEnabled = false

    private var gamepadDisabled = false

    // ===================== 演示遮罩 =====================

    /** 创建演示遮罩视图（全屏半透明，覆盖在触控覆盖层之上） */
    fun createHintView(): View {
        val maskBg = Color.argb(110, 10, 10, 14)
        val cardColor = Color.argb(225, 26, 26, 38)
        val borderColor = Color.argb(60, 255, 255, 255)
        val textMain = Color.WHITE
        val textSub = Color.argb(200, 224, 224, 230)

        val root = FrameLayout(activity)
        root.setBackgroundColor(maskBg)
        root.setOnClickListener { dismissHint() }
        root.setOnTouchListener { _, ev -> handleOverlayViewTouch(ev) }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18f).toFloat()
                setColor(cardColor)
                setStroke(dp(1f), borderColor)
            }
            setPadding(dp(28f), dp(28f), dp(28f), dp(26f))
            layoutParams = FrameLayout.LayoutParams(
                dp(300f),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        root.addView(card)

        chevronView = ChevronSwipeView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(120f), dp(72f))
        }
        card.addView(chevronView)

        card.addView(TextView(activity).apply {
            text = "双指下滑唤起控制面板"
            setTextColor(textMain)
            textSize = 18f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10f) }
        })

        card.addView(TextView(activity).apply {
            text = "在屏幕任意位置用两根手指向下滑动\n即可唤起游戏控制面板"
            setTextColor(textSub)
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) }
        })

        card.addView(Button(activity).apply {
            text = "我知道了"
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10f).toFloat()
                setColor(Color.argb(255, 66, 66, 88))
            }
            setPadding(dp(36f), dp(10f), dp(36f), dp(10f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18f) }
            setOnClickListener { dismissHint() }
        })

        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        hintView = root
        return root
    }

    fun showHint() {
        val view = hintView ?: return
        view.visibility = View.VISIBLE
        view.bringToFront()
        hintAutoHide?.removeCallbacksAndMessages(null)
        hintAutoHide = Handler(Looper.getMainLooper())
        hintAutoHide?.postDelayed({ dismissHint() }, 8000)
        chevronView?.startSwipe()
        hintVisible = true
        VerseLogger.info("双指下滑演示遮罩已显示", "Control")
    }

    fun dismissHint() {
        hintAutoHide?.removeCallbacksAndMessages(null)
        val view = hintView ?: return
        if (view.visibility != View.VISIBLE) return
        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.duration = 200
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(a: Animation?) {}
            override fun onAnimationEnd(a: Animation?) {
                view.visibility = View.GONE
            }
            override fun onAnimationRepeat(a: Animation?) {}
        })
        view.startAnimation(fadeOut)
        chevronView?.stopSwipe()
        hintVisible = false
        VerseLogger.info("双指下滑演示遮罩已消失", "Control")
    }


    fun createPanelView(): View {
        val root = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 面板展示期间挡住下层游戏输入；点击面板外空白处收起
            setOnClickListener { hidePanel(true) }
        }
        leftBar = buildSideBar(Gravity.START, "控制", isLeft = true)
        rightBar = buildSideBar(Gravity.END, "设置", isLeft = false)
        root.addView(leftBar)
        root.addView(rightBar)
        panelView = root
        return root
    }

    private fun buildSideBar(barGravity: Int, title: String, isLeft: Boolean): View {
        val container = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(270f),
                ViewGroup.LayoutParams.MATCH_PARENT,
                barGravity
            ).apply {
                topMargin = dp(30f)
                bottomMargin = dp(30f)
                if (isLeft) marginStart = dp(30f) else marginEnd = dp(30f)
            }
        }

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(fclBg)
                setStroke(dp(1f), fclDivider)
            }
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            isClickable = true
            isFocusable = true
            setOnClickListener { }
            setOnTouchListener { _, ev -> handleOverlayViewTouch(ev) }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(panel)

        panel.addView(TextView(activity).apply {
            text = title
            setTextColor(fclText)
            textSize = 14f
            isSingleLine = true
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

        val scroll = android.widget.ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { topMargin = dp(10f) }
            isFillViewport = true
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scroll.addView(content)
        panel.addView(scroll)

        if (isLeft) {
            buildLeftContent(content)
        } else {
            buildRightContent(content)
        }
        return container
    }

    // ===================== 左栏「控制」（menu_left.xml 全量） =====================

    private fun layoutFileList(): List<String> {
        val dir = File(Tools.CTRLMAP_PATH)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?.sorted() ?: emptyList()
    }

    private fun buildLeftContent(content: LinearLayout) {
        val layouts = layoutFileList()
        content.addView(rows.sectionHeader("当前布局方案"))
        content.addView(rows.spinnerRow("当前布局方案", layouts, 0) { index ->
            val name = layouts.getOrNull(index) ?: return@spinnerRow
            runCatching {
                activity.controlLayout?.loadLayout(Tools.CTRLMAP_PATH + "/" + name + ".json")
            }.onFailure {
                Toast.makeText(activity, "加载布局失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        })
        content.addView(rows.buttonRow("保存布局", "保存") {
            activity.controlLayout?.openSaveDialog(null)
        })
        content.addView(rows.buttonRow("设为默认", "设默认") {
            activity.controlLayout?.openSetDefaultDialog()
        })

        content.addView(rows.switchRow("编辑模式", activity.controlLayout?.getModifiable() == true) { on ->
            activity.controlLayout?.setModifiable(on)
            Toast.makeText(
                activity,
                if (on) "编辑模式已开启：拖动控件调整位置，点按控件可编辑属性"
                else "编辑模式已关闭",
                Toast.LENGTH_SHORT
            ).show()
            VerseLogger.info("控制面板：编辑模式=$on", "Control")
        })
        content.addView(rows.switchRow(
            "显示控件边缘",
            activity.controlLayout?.getShowViewBoundaries() == true
        ) { on ->
            // 只控制描边显示，不能动编辑模式：否则关掉边缘会把编辑模式一起关掉（真机反馈「亮一下就灭」）
            activity.controlLayout?.setShowViewBoundaries(on)
            VerseLogger.info("控制面板：显示控件边缘=$on", "Control")
        })
        content.addView(rows.switchRow("隐藏所有控件", false) { on ->
            activity.controlLayout?.setControlVisible(!on)
            VerseLogger.info("控制面板：隐藏所有控件=$on", "Control")
        })
        // 通过 PREF_BUTTONSIZE 控制并在间距变化时重新加载布局生效）
        content.addView(rows.switchRow("自动吸附", MenuSetting.isAutoFit) { on ->
            MenuSetting.isAutoFit = on
            MenuSetting.notifyChange(MenuSetting.Key.AUTO_FIT)
            applyAutoFit()
        })
        content.addView(rows.seekbarRow("自动吸附间距", 0, 10, MenuSetting.autoFitDist, "dp") { v ->
            MenuSetting.autoFitDist = v
            MenuSetting.notifyChange(MenuSetting.Key.AUTO_FIT_DIST)
            if (MenuSetting.isAutoFit) applyAutoFit()
        })

        // 控件组（menu_left.xml edit_layout 区块）
        content.addView(rows.sectionHeader("控件组"))
        content.addView(rows.spinnerRow("当前控件组", layouts, 0) { index ->
            val name = layouts.getOrNull(index) ?: return@spinnerRow
            runCatching {
                activity.controlLayout?.loadLayout(Tools.CTRLMAP_PATH + "/" + name + ".json")
            }.onFailure {
                Toast.makeText(activity, "加载控件组失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        })
        content.addView(rows.buttonRow("控件组", "管理") {
            activity.controlLayout?.openLoadDialog()
        })
        content.addView(rows.buttonRow("添加按键", "添加") {
            addNewControl(ControlData())
        })
        content.addView(rows.buttonRow("添加方向控制键", "添加") {
            addNewControl(ControlJoystickData())
        })

        // 样式（menu_left.xml style 区块）
        content.addView(rows.sectionHeader("样式"))
        content.addView(rows.buttonRow("按键样式", "管理") {
            editLastControl(isJoystick = false)
        })
        content.addView(rows.buttonRow("方向控制键样式", "管理") {
            editLastControl(isJoystick = true)
        })
    }

    private fun addNewControl(data: ControlData) {
        val layout = activity.controlLayout ?: return
        if (data is ControlJoystickData) {
            layout.addJoystickButton(data)
        } else {
            layout.addControlButton(data)
        }
        layout.setModifiable(true)
        editLastControl(isJoystick = data is ControlJoystickData)
    }

    private fun editLastControl(isJoystick: Boolean) {
        val layout = activity.controlLayout ?: return
        val target = layout.getButtonChildren()
            .lastOrNull {
                val v = it.getControlView()
                if (isJoystick) v is ControlJoystick
                else v != null && v !is ControlJoystick
            }
            ?: run {
                Toast.makeText(activity, if (isJoystick) "没有方向控制键可编辑" else "没有按键可编辑", Toast.LENGTH_SHORT).show()
                return
            }
        layout.setModifiable(true)
        layout.editControlButton(target)
    }

    // ===================== 右栏「设置」（menu_right.xml 全量） =====================

    private fun buildRightContent(content: LinearLayout) {
        // ===== 功能 =====
        content.addView(rows.sectionHeader("功能"))
        content.addView(rows.switchRow("锁定菜单键", MenuSetting.isLockMenuView) { on ->
            MenuSetting.isLockMenuView = on
            MenuSetting.notifyChange(MenuSetting.Key.LOCK_MENU_VIEW)
        })
        content.addView(rows.switchRow("隐藏菜单键", MenuSetting.isHideMenuView) { on ->
            MenuSetting.isHideMenuView = on
            MenuSetting.notifyChange(MenuSetting.Key.HIDE_MENU_VIEW)
            activity.controlLayout?.setControlVisible(!on)
            if (on) {
                Toast.makeText(activity, "菜单按键已隐藏，按下音量键打开菜单。", Toast.LENGTH_LONG).show()
            }
        })
        content.addView(rows.switchRow("显示FPS", MenuSetting.isShowFps) { on ->
            MenuSetting.isShowFps = on
            MenuSetting.notifyChange(MenuSetting.Key.SHOW_FPS)
            setFpsVisible(on)
        })
        content.addView(rows.buttonRow("游戏菜单", "打开") { onEsc() })
        // 陶瓦联机（Verse 补充入口：直接唤出陶瓦联机对话框，未启用时提示）
        content.addView(rows.buttonRow("陶瓦联机", "打开") {
            hidePanel(false)
            activity.showMultiplayerEntry()
        })
        // 红石联机（Verse 补充入口：游戏内直接拉起/管理红石中转隧道，无需退出游戏）
        content.addView(rows.buttonRow("红石联机", "打开") {
            hidePanel(false)
            activity.showRedstoneDialog()
        })
        content.addView(rows.buttonRow("快捷输入", "管理") { activity.showQuickInput() })
        content.addView(rows.buttonRow("模拟键盘", "打开") { activity.showSimulatedKeyboard() })
        content.addView(rows.buttonRow("发送键值", "发送") { showSendKeyDialog() })
        content.addView(rows.switchRow("禁用软键盘自适应", MenuSetting.isDisableSoftKeyAdjust) { on ->
            MenuSetting.isDisableSoftKeyAdjust = on
            MenuSetting.notifyChange(MenuSetting.Key.DISABLE_SOFT_KEY_ADJUST)
        })
        // 快捷栏判定方式：自动（跟随游戏 GUI 缩放）/ 自定义尺寸（对齐 ZL2 的 HotbarRule）
        content.addView(rows.spinnerRow(
            "物品栏判定方式",
            listOf("自动（跟随 GUI 缩放）", "自定义尺寸"),
            if (MenuSetting.itemBarRule == MenuSetting.ITEM_BAR_RULE_CUSTOM) 1 else 0
        ) { index ->
            MenuSetting.itemBarRule = if (index == 1) MenuSetting.ITEM_BAR_RULE_CUSTOM
            else MenuSetting.ITEM_BAR_RULE_AUTO
            HotbarOverlayView.requestFeedback()
            MenuSetting.notifyChange(MenuSetting.Key.ITEM_BAR_RULE)
        })
        content.addView(rows.switchRow("双击交换主副手", MenuSetting.itemBarDoubleTap) { on ->
            MenuSetting.itemBarDoubleTap = on
            MenuSetting.notifyChange(MenuSetting.Key.ITEM_BAR_DOUBLE_TAP)
        })
        content.addView(rows.switchRow("长按丢弃（${MenuSetting.itemBarLongPressDelay}ms）", MenuSetting.itemBarLongPress) { on ->
            MenuSetting.itemBarLongPress = on
            MenuSetting.notifyChange(MenuSetting.Key.ITEM_BAR_LONG_PRESS)
        })
        content.addView(rows.seekbarRow("物品栏缩放 - 宽度", 0, 100, MenuSetting.itemBarWidth, "%") { v ->
            MenuSetting.itemBarWidth = v
            HotbarOverlayView.requestFeedback()
            MenuSetting.notifyChange(MenuSetting.Key.ITEM_BAR_WIDTH)
        })
        content.addView(rows.seekbarRow("物品栏缩放 - 高度", 0, 100, MenuSetting.itemBarHeight, "%") { v ->
            MenuSetting.itemBarHeight = v
            HotbarOverlayView.requestFeedback()
            MenuSetting.notifyChange(MenuSetting.Key.ITEM_BAR_HEIGHT)
        })
        content.addView(rows.seekbarRow("光标偏移", -150, 150, MenuSetting.cursorOffset.toInt(), "") { v ->
            MenuSetting.cursorOffset = v.toDouble()
            MenuSetting.notifyChange(MenuSetting.Key.CURSOR_OFFSET)
            refreshWindowsSize()
        })

        // ===== 分辨率 =====
        content.addView(rows.sectionHeader("分辨率"))
        content.addView(rows.seekbarRow(
            "分辨率比例", 25, 300,
            (MenuSetting.windowScale * 100).toInt().coerceIn(25, 300), "%"
        ) { v ->
            MenuSetting.windowScale = v / 100.0
            MenuSetting.notifyChange(MenuSetting.Key.WINDOW_SCALE)
            refreshWindowsSize()
        })

        // ===== 手势 =====
        content.addView(rows.sectionHeader("手势"))
        content.addView(rows.switchRow("禁用手势", MenuSetting.isDisableGesture) { on ->
            MenuSetting.isDisableGesture = on
            MenuSetting.notifyChange(MenuSetting.Key.DISABLE_GESTURE)
        })
        content.addView(rows.switchRow("禁用仿基岩版手势", MenuSetting.isDisableBEGesture) { on ->
            MenuSetting.isDisableBEGesture = on
            MenuSetting.notifyChange(MenuSetting.Key.DISABLE_BE_GESTURE)
        })
        content.addView(rows.spinnerRow(
            "触控模式",
            listOf("建筑模式", "战斗模式"),
            MenuSetting.gestureMode
        ) { index ->
            MenuSetting.gestureMode = index
            MenuSetting.notifyChange(MenuSetting.Key.GESTURE_MODE)
        })
        content.addView(rows.switchRow("禁用左半屏触控", MenuSetting.isDisableLeftTouch) { on ->
            MenuSetting.isDisableLeftTouch = on
            MenuSetting.notifyChange(MenuSetting.Key.DISABLE_LEFT_TOUCH)
        })

        // ===== 鼠标 =====
        content.addView(rows.sectionHeader("鼠标"))
        content.addView(rows.spinnerRow(
            "鼠标控制模式",
            listOf("点击", "拖动"),
            MenuSetting.mouseMoveMode
        ) { index ->
            MenuSetting.mouseMoveMode = index
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_MOVE_MODE)
        })
        content.addView(rows.seekbarRow("鼠标灵敏度", 100, 1000, (MenuSetting.mouseSensitivity * 100).toInt(), "%") { v ->
            MenuSetting.mouseSensitivity = v / 100.0
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_SENSITIVITY)
        })
        content.addView(rows.seekbarRow("鼠标灵敏度（当指针可见时）", 100, 1000, (MenuSetting.mouseSensitivityCursor * 100).toInt(), "%") { v ->
            MenuSetting.mouseSensitivityCursor = v / 100.0
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_SENSITIVITY_CURSOR)
        })
        content.addView(rows.seekbarRow("鼠标尺寸", 0, 30, MenuSetting.mouseSize, "dp") { v ->
            MenuSetting.mouseSize = v
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_SIZE)
            activity.applyCursorStyle()
        })
        content.addView(rows.seekbarRow("鼠标偏移X", -30, 30, MenuSetting.mouseOffsetX, "dp") { v ->
            MenuSetting.mouseOffsetX = v
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_OFFSET_X)
        })
        content.addView(rows.seekbarRow("鼠标偏移Y", -30, 30, MenuSetting.mouseOffsetY, "dp") { v ->
            MenuSetting.mouseOffsetY = v
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_OFFSET_Y)
        })
        content.addView(rows.switchRow("实体鼠标控制", MenuSetting.isPhysicalMouseMode) { on ->
            MenuSetting.isPhysicalMouseMode = on
            MenuSetting.notifyChange(MenuSetting.Key.PHYSICAL_MOUSE_MODE)
            // 切换后立即按当前抓取状态同步指针捕获（抓取态捕获、GUI 态释放）
            onPhysicalMouseChanged(on)
        })

        // ===== 手柄 =====
        content.addView(rows.sectionHeader("手柄"))
        content.addView(rows.switchRow("禁用手柄映射", gamepadDisabled) { on ->
            gamepadDisabled = on
        })
        content.addView(rows.buttonRow("重置映射", "重置") {
            Toast.makeText(activity, "重置映射（FCL 手柄栈未引入）", Toast.LENGTH_SHORT).show()
        })
        content.addView(rows.buttonRow("按键绑定", "打开") {
            Toast.makeText(activity, "按键绑定（FCL 手柄栈未引入）", Toast.LENGTH_SHORT).show()
        })
        content.addView(rows.seekbarRow("调整死区", 0, 100, (MenuSetting.gamepadDeadzone * 100).toInt(), "%") { v ->
            MenuSetting.gamepadDeadzone = v / 100.0
            MenuSetting.notifyChange(MenuSetting.Key.GAMEPAD_DEADZONE)
        })

        // ===== 陀螺仪 =====
        content.addView(rows.sectionHeader("陀螺仪"))
        content.addView(rows.switchRow("陀螺仪", MenuSetting.isEnableGyroscope) { on ->
            MenuSetting.isEnableGyroscope = on
            MenuSetting.notifyChange(MenuSetting.Key.ENABLE_GYROSCOPE)
            activity.gyroControl?.let {
                if (on) it.enable() else it.disable()
            }
            if (on) {
                Toast.makeText(activity, "陀螺仪已开启，点击画面锁定光标后生效", Toast.LENGTH_LONG).show()
            }
        })
        content.addView(rows.switchRow("反转陀螺仪", MenuSetting.isInvertGyroscope) { on ->
            MenuSetting.isInvertGyroscope = on
            MenuSetting.notifyChange(MenuSetting.Key.INVERT_GYROSCOPE)
            runCatching {
                LauncherPreferences.PREF_GYRO_INVERT_X = on
                LauncherPreferences.PREF_GYRO_INVERT_Y = on
            }.onFailure {
                VerseLogger.warn("写入 PREF_GYRO_INVERT 失败: ${it.message}", "Control")
            }
        })
        content.addView(
            rows.seekbarRow("陀螺仪灵敏度", 0, 100, MenuSetting.gyroscopeSensitivity.coerceIn(0, 100), "") { v ->
                val clamped = v.coerceIn(0, 100)
                MenuSetting.gyroscopeSensitivity = clamped
                MenuSetting.notifyChange(MenuSetting.Key.GYROSCOPE_SENSITIVITY)
                applyGyroSensitivity(clamped)
            }
        )

        // ===== 调试 =====
        content.addView(rows.sectionHeader("调试"))
        content.addView(rows.switchRow("显示内存", MenuSetting.isShowMemory) { on ->
            MenuSetting.isShowMemory = on
            MenuSetting.notifyChange(MenuSetting.Key.SHOW_MEMORY)
            setMemoryVisible(on)
        })
        content.addView(rows.switchRow("持续性能模式", MenuSetting.isPerformanceMode) { on ->
            MenuSetting.isPerformanceMode = on
            MenuSetting.notifyChange(MenuSetting.Key.PERFORMANCE_MODE)
            activity.window.setSustainedPerformanceMode(on)
        })
        content.addView(rows.switchRow("显示日志", MenuSetting.isShowLog) { on ->
            MenuSetting.isShowLog = on
            MenuSetting.notifyChange(MenuSetting.Key.SHOW_LOG)
            updateLogWindowVisibility()
        })
        content.addView(rows.switchRow("自动显示日志", MenuSetting.isAutoShowLog) { on ->
            MenuSetting.isAutoShowLog = on
            MenuSetting.notifyChange(MenuSetting.Key.AUTO_SHOW_LOG)
            updateLogWindowVisibility()
        })
        // 优雅拆解不可行，强制退出直接杀死整个进程（下次冷启动回到启动器主页）
        content.addView(rows.buttonRow("强制退出", "退出") {
            AlertDialog.Builder(activity)
                .setTitle("强制退出")
                .setMessage("你确定要强制退出吗？")
                .setCancelable(false)
                .setPositiveButton("确定") { _, _ ->
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                .setNegativeButton("取消", null)
                .show()
        })

        if (MenuSetting.isShowFps) setFpsVisible(true)
        if (MenuSetting.isShowMemory) setMemoryVisible(true)
        if (MenuSetting.isPerformanceMode) activity.window.setSustainedPerformanceMode(true)
        if (MenuSetting.isHideMenuView) activity.controlLayout?.setControlVisible(false)
        if (MenuSetting.isEnableGyroscope) applyGyroSensitivity(MenuSetting.gyroscopeSensitivity)
        if (MenuSetting.isInvertGyroscope) {
            runCatching {
                LauncherPreferences.PREF_GYRO_INVERT_X = true
                LauncherPreferences.PREF_GYRO_INVERT_Y = true
            }
        }
        updateLogWindowVisibility()
    }

    private fun applyAutoFit() {
        val layout = activity.controlLayout ?: return
        val name = layout.mLayoutFileName?.trimStart('.') ?: return
        if (name.isEmpty()) return
        runCatching {
            val scale = if (MenuSetting.isAutoFit) {
                (100 - MenuSetting.autoFitDist * 3).coerceIn(70, 100)
            } else {
                100
            }
            LauncherPreferences.PREF_BUTTONSIZE = scale.toFloat()
            layout.loadLayout(Tools.CTRLMAP_PATH + "/" + name + ".json")
        }.onFailure {
            Toast.makeText(activity, "自动吸附应用失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyGyroSensitivity(value: Int) {
        runCatching {
            LauncherPreferences.PREF_GYRO_SENSITIVITY = value * 0.5f
        }.onFailure {
            VerseLogger.warn("写入 PREF_GYRO_SENSITIVITY 失败: ${it.message}", "Control")
        }
    }

    private fun updateLogWindowVisibility() {
        val logWin = activity.logWindow ?: return
        logWin.setVisibility(MenuSetting.isShowLog || MenuSetting.isAutoShowLog)
    }

    /** 按当前分辨率规则刷新渲染缓冲与游戏窗口尺寸 */
    private fun refreshWindowsSize() {
        val bridge = activity.currentBridge ?: return
        val metrics = activity.resources.displayMetrics
        val resolved = MenuSetting.resolveRenderSize(metrics.widthPixels, metrics.heightPixels)
        var width = resolved.first
        var height = resolved.second
        if (FCLBridge.FORCE_RESOLUTION) {
            width = FCLBridge.FORCE_RESOLUTION_WIDTH
            height = FCLBridge.FORCE_RESOLUTION_HEIGHT
        }
        bridge.setScaleFactor(MenuSetting.windowScale)
        bridge.surfaceTexture?.setDefaultBufferSize(width, height)
        // pushEventWindow 依赖游戏 JVM 侧 org.lwjgl.glfw.CallbackBridge，可能未就绪，兜底捕获
        runCatching {
            bridge.pushEventWindow(width, height)
        }
        // SDL 渲染路径（MC 26.3+）同步原生窗口尺寸
        val sdlSurface = org.libsdl.app.SDLActivity.getSDLSurface()
        if (sdlSurface != null && com.tungsten.fcl.game.sdl.SdlBridge.sdlEnabled) {
            runCatching {
                sdlSurface.surfaceChanged()
                sdlSurface.nativeResize(width, height)
            }
        }
        VerseLogger.info(
            "分辨率已应用: ${width}x${height}", "Control"
        )
    }

    @SuppressLint("InflateParams")
    private fun showSendKeyDialog() {
        val keys = listOf(
            LwjglGlfwKeycode.GLFW_KEY_A to "A", LwjglGlfwKeycode.GLFW_KEY_B to "B",
            LwjglGlfwKeycode.GLFW_KEY_C to "C", LwjglGlfwKeycode.GLFW_KEY_D to "D",
            LwjglGlfwKeycode.GLFW_KEY_E to "E", LwjglGlfwKeycode.GLFW_KEY_F to "F",
            LwjglGlfwKeycode.GLFW_KEY_G to "G", LwjglGlfwKeycode.GLFW_KEY_H to "H",
            LwjglGlfwKeycode.GLFW_KEY_I to "I", LwjglGlfwKeycode.GLFW_KEY_J to "J",
            LwjglGlfwKeycode.GLFW_KEY_K to "K", LwjglGlfwKeycode.GLFW_KEY_L to "L",
            LwjglGlfwKeycode.GLFW_KEY_M to "M", LwjglGlfwKeycode.GLFW_KEY_N to "N",
            LwjglGlfwKeycode.GLFW_KEY_O to "O", LwjglGlfwKeycode.GLFW_KEY_P to "P",
            LwjglGlfwKeycode.GLFW_KEY_Q to "Q", LwjglGlfwKeycode.GLFW_KEY_R to "R",
            LwjglGlfwKeycode.GLFW_KEY_S to "S", LwjglGlfwKeycode.GLFW_KEY_T to "T",
            LwjglGlfwKeycode.GLFW_KEY_U to "U", LwjglGlfwKeycode.GLFW_KEY_V to "V",
            LwjglGlfwKeycode.GLFW_KEY_W to "W", LwjglGlfwKeycode.GLFW_KEY_X to "X",
            LwjglGlfwKeycode.GLFW_KEY_Y to "Y", LwjglGlfwKeycode.GLFW_KEY_Z to "Z",
            LwjglGlfwKeycode.GLFW_KEY_0 to "0", LwjglGlfwKeycode.GLFW_KEY_1 to "1",
            LwjglGlfwKeycode.GLFW_KEY_2 to "2", LwjglGlfwKeycode.GLFW_KEY_3 to "3",
            LwjglGlfwKeycode.GLFW_KEY_4 to "4", LwjglGlfwKeycode.GLFW_KEY_5 to "5",
            LwjglGlfwKeycode.GLFW_KEY_6 to "6", LwjglGlfwKeycode.GLFW_KEY_7 to "7",
            LwjglGlfwKeycode.GLFW_KEY_8 to "8", LwjglGlfwKeycode.GLFW_KEY_9 to "9",
            LwjglGlfwKeycode.GLFW_KEY_SPACE to "Space", LwjglGlfwKeycode.GLFW_KEY_ESCAPE to "Esc",
            LwjglGlfwKeycode.GLFW_KEY_ENTER to "Enter", LwjglGlfwKeycode.GLFW_KEY_TAB to "Tab",
            LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT to "Shift", LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL to "Ctrl",
            LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT to "Alt"
        )
        val checks = keys.map { CheckBox(activity).apply { text = it.second; textSize = 12f } }
        val grid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(12f), dp(20f), dp(4f))
        }
        checks.chunked(4).forEach { row ->
            val rowLayout = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { box ->
                rowLayout.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            grid.addView(rowLayout)
        }
        AlertDialog.Builder(activity)
            .setTitle("发送键值")
            .setView(grid)
            .setCancelable(true)
            .setPositiveButton("发送") { _, _ ->
                val selected = keys.filterIndexed { i, _ -> checks[i].isChecked }
                    .map { it.first }
                if (selected.isEmpty()) return@setPositiveButton
                val bridge = activity.currentBridge ?: return@setPositiveButton
                Thread {
                    selected.forEach { bridge.pushEventKey(it, 0, true) }
                    try {
                        Thread.sleep(50)
                    } catch (_: InterruptedException) {
                    }
                    selected.forEach { bridge.pushEventKey(it, 0, false) }
                }.start()
            }
            .setNegativeButton("取消", null)
            .show()
    }


    fun showPanel() {
        val view = panelView ?: return
        view.clearAnimation()
        view.visibility = View.VISIBLE
        view.bringToFront()
        leftBar?.let {
            it.clearAnimation()
            val slide = TranslateAnimation(-dp(300f).toFloat(), 0f, 0f, 0f)
            slide.duration = 220
            slide.interpolator = DecelerateInterpolator()
            it.startAnimation(slide)
        }
        rightBar?.let {
            it.clearAnimation()
            val slide = TranslateAnimation(dp(300f).toFloat(), 0f, 0f, 0f)
            slide.duration = 220
            slide.interpolator = DecelerateInterpolator()
            it.startAnimation(slide)
        }
        panelVisible = true
        VerseLogger.info("控制面板已唤起（双指下滑）", "Control")
    }

    fun hidePanel(animate: Boolean) {
        val view = panelView ?: return
        if (view.visibility != View.VISIBLE) return
        if (animate) {
            leftBar?.let {
                val slide = TranslateAnimation(0f, -dp(300f).toFloat(), 0f, 0f)
                slide.duration = 180
                it.startAnimation(slide)
            }
            rightBar?.let {
                val slide = TranslateAnimation(0f, dp(300f).toFloat(), 0f, 0f)
                slide.duration = 180
                it.startAnimation(slide)
            }
            view.postDelayed({ view.visibility = View.GONE }, 180)
        } else {
            view.visibility = View.GONE
        }
        panelVisible = false
        VerseLogger.info("控制面板已收起", "Control")
    }

    fun isHintVisible(): Boolean = hintVisible
    fun isPanelVisible(): Boolean = panelVisible
    fun isOverlayVisible(): Boolean = hintVisible || panelVisible

    // ===================== 双指下滑手势 =====================

    private val pointerStart = mutableMapOf<Int, Float>()
    private val pointerCurrent = mutableMapOf<Int, Float>()
    private var multiSwipeFired = false
    // 双指下滑灵敏度：110dp 较原三指的 140dp 更灵敏，但仍需明确下滑距离，避免双指普通操作误触
    private val swipeThreshold = 110f * density

    /**
     * 跟踪多指触摸。返回 true 表示本次事件中双指下滑已成立（手势已消费）。
     * 遮罩可见 / 面板可见 / 正常游戏态（GameActivity 分发前）三条路径共用，
     * 手势判定只依赖「两根手指整体下滑距离 ≥ 阈值」，不新增私有逻辑。
     */
    /** 双指下滑手势跟踪。skipPointerIds 为虚拟按钮指针（方向键/功能键）——
     *  玩家"左手按方向键 + 右手滑动转视角"是标准操作，按钮指针必须排除，
     *  否则一指定一指滑会被误判成双指下滑 → fireSwipe 弹出控制面板/打断滑动（视角抽搐）。 */
    fun trackMultiTouch(ev: MotionEvent, skipPointerIds: Set<Int> = emptySet()): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerStart.clear()
                pointerCurrent.clear()
                multiSwipeFired = false
                val id = ev.getPointerId(0)
                if (id !in skipPointerIds) {
                    pointerStart[id] = ev.getY(0)
                    pointerCurrent[id] = ev.getY(0)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                val id = ev.getPointerId(index)
                if (id !in skipPointerIds) {
                    pointerStart[id] = ev.getY(index)
                    pointerCurrent[id] = ev.getY(index)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i)
                    if (id !in skipPointerIds) {
                        pointerCurrent[id] = ev.getY(i)
                    }
                }
                if (pointerStart.size >= 2 && !multiSwipeFired) {
                    var sum = 0f
                    var n = 0
                    for ((id, start) in pointerStart) {
                        val current = pointerCurrent[id] ?: continue
                        sum += current - start
                        n++
                    }
                    if (n >= 2 && sum / n >= swipeThreshold) {
                        multiSwipeFired = true
                        return true
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val id = ev.getPointerId(ev.actionIndex)
                if (id !in skipPointerIds) {
                    pointerCurrent.remove(id)
                    pointerStart.remove(id)
                    if (pointerStart.isEmpty()) multiSwipeFired = false
                }
            }
        }
        return multiSwipeFired
    }

    /** 遮罩 / 面板自身可见时的触摸：跟踪多指并响应双指下滑 */
    private fun handleOverlayViewTouch(ev: MotionEvent): Boolean {
        val fired = trackMultiTouch(ev)
        if (fired) {
            fireSwipe()
            return true
        }
        return false
    }

    /** 双指下滑成立后的统一处理：复位游戏按键 → 遮罩/面板状态切换或唤起面板 */
    fun fireSwipe() {
        if (MenuSetting.isLockMenuView) {
            VerseLogger.info("菜单已锁定，忽略双指下滑手势", "Control")
            return
        }
        onCancelGameGesture()
        when {
            hintVisible -> {
                dismissHint()
                showPanel()
            }
            panelVisible -> hidePanel(true)
            else -> showPanel()
        }
    }


    private fun setFpsVisible(visible: Boolean) {
        fpsEnabled = visible
        if (visible) {
            ensureInfoOverlay()
            startInfoPolling()
        } else {
            fpsView?.visibility = View.GONE
            if (!memEnabled) stopInfoPolling()
        }
    }

    private fun setMemoryVisible(visible: Boolean) {
        memEnabled = visible
        if (visible) {
            ensureInfoOverlay()
            startInfoPolling()
        } else {
            memView?.visibility = View.GONE
            if (!fpsEnabled) stopInfoPolling()
        }
    }

    /** 启动 FPS / 内存轮询（每 500ms 刷新 FPS，每 1s 刷新内存） */
    private fun startInfoPolling() {
        infoHandler?.removeCallbacksAndMessages(null)
        infoHandler = Handler(Looper.getMainLooper())
        infoHandler?.post(object : Runnable {
            override fun run() {
                if (fpsEnabled) {
                    val fps = runCatching { com.verse.explorer.launcher.bridge.CallbackBridge.getFps() }
                        .getOrDefault(0)
                    fpsView?.text = "fps: $fps"
                    fpsView?.visibility = View.VISIBLE
                }
                if (memEnabled) {
                    val mi = android.app.ActivityManager.MemoryInfo()
                    activity.getSystemService(Context.ACTIVITY_SERVICE)?.let { am ->
                        (am as? android.app.ActivityManager)?.getMemoryInfo(mi)
                    }
                    val availMb = mi.availMem / 1024 / 1024
                    val totalMb = mi.totalMem / 1024 / 1024
                    memView?.text = "内存: $availMb / $totalMb MB"
                    memView?.visibility = View.VISIBLE
                }
                infoHandler?.postDelayed(this, 500)
            }
        })
    }

    /**
     * FPS/内存信息条：两项放入同一容器，整条可拖动，
     * 拖动位置按屏幕范围限制并持久化，避免遮挡视野后无法移开。
     */
    private fun ensureInfoOverlay() {
        if (infoContainer != null) return

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        fpsView = TextView(activity).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 14f
            paint.isFakeBoldText = true
            visibility = View.GONE
        }
        memView = TextView(activity).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 14f
            paint.isFakeBoldText = true
            visibility = View.GONE
        }
        container.addView(
            fpsView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            memView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4f).toInt() }
        )
        activity.addContentView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.TOP
            ).apply { topMargin = dp(80f).toInt() }
        )
        attachInfoDrag(container)
        infoContainer = container
    }

    /** 信息条拖动：按下后跟随手指移动，边界按当前屏幕尺寸限制，抬手保存位置 */
    private fun attachInfoDrag(view: View) {
        val prefs = activity.getSharedPreferences("game_hud", Context.MODE_PRIVATE)
        view.translationX = prefs.getFloat("info_offset_x", 0f)
        view.translationY = prefs.getFloat("info_offset_y", 0f)

        var lastRawX = 0f
        var lastRawY = 0f
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val metrics = v.resources.displayMetrics
                    // 初始位置为水平居中、顶部 80dp，可移动范围为屏幕内剩余空间
                    val maxX = ((metrics.widthPixels - v.width) / 2f).coerceAtLeast(0f)
                    val maxY =
                        (metrics.heightPixels - v.height - dp(80f)).coerceAtLeast(0).toFloat()
                    v.translationX =
                        (v.translationX + event.rawX - lastRawX).coerceIn(-maxX, maxX)
                    v.translationY =
                        (v.translationY + event.rawY - lastRawY).coerceIn(0f, maxY)
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit()
                        .putFloat("info_offset_x", v.translationX)
                        .putFloat("info_offset_y", v.translationY)
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    private fun stopInfoPolling() {
        infoHandler?.removeCallbacksAndMessages(null)
        infoHandler = null
    }

    // ===================== 视觉：双指下滑箭头动画 =====================

    private class ChevronSwipeView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = dp(5f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val animator = ValueAnimator().apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                offset = (it.animatedValue as Float) * step
                invalidate()
            }
        }
        private var offset = 0f

        private val step: Float
            get() = height / 4f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val chevronHeight = dp(14f)
            for (i in 0 until 2) {
                // 两个箭头依次向下流动（呼应双指下滑提示）
                var y = step * (i + 1) + offset
                if (y > h + chevronHeight) y -= h + chevronHeight
                if (y < -chevronHeight || y > h + chevronHeight) continue
                drawChevron(canvas, w / 2, y, chevronHeight)
            }
        }

        private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, size: Float) {
            canvas.drawLine(cx - size, cy - size * 0.55f, cx, cy + size * 0.45f, paint)
            canvas.drawLine(cx + size, cy - size * 0.55f, cx, cy + size * 0.45f, paint)
        }

        private fun dp(v: Float): Float = v * resources.displayMetrics.density

        fun startSwipe() {
            if (!animator.isStarted) {
                animator.setFloatValues(0f, 1f)
                animator.start()
            }
        }

        fun stopSwipe() {
            animator.cancel()
            offset = 0f
            invalidate()
        }
    }

    // ===================== 截图（复用现有 TextureView 位图） =====================

    companion object {
        /** 保存游戏画面截图到应用外部 Pictures 目录（无需额外权限） */
        @JvmStatic
        fun saveScreenshot(textureBitmap: Bitmap?, context: Context): String? {
            val bmp = textureBitmap ?: return null
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "verse_screenshot_${System.currentTimeMillis()}.png")
            return try {
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file.absolutePath
            } catch (e: Throwable) {
                VerseLogger.error("截图保存失败: ${e.message}", "Control")
                null
            }
        }
    }
}
