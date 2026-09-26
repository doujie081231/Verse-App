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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
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
 * 游戏内控制面板（样式与行项目全部对照 FCL 的 view_game_menu / menu_left / menu_right）：
 * - 双指下滑手势（由 GameActivity 在分发触摸前检测）唤起 FCL 样式的左右双侧边栏；
 * - 左栏「控制」= menu_left.xml 全量行：当前布局方案、编辑模式、显示控件边缘、隐藏所有控件、
 *   自动吸附、自动吸附间距、当前控件组、控件组管理、添加按键、添加方向控制键、按键样式、方向控制键样式；
 * - 右栏「设置」= menu_right.xml 全量行：功能（锁定菜单键/隐藏菜单键/显示FPS/联机菜单/快捷输入/发送键值/
 *   禁用软键盘自适应/物品栏缩放/窗口比例/光标偏移）、手势、鼠标、手柄、陀螺仪、调试分区；
 * - 设置项读写 MenuSetting（<filesDir>/menu_setting.json，键名与 FCL MenuSetting 一致）；
 * - 行为照搬 FCL GameMenu.java 对应接线：窗口缩放/光标偏移走 refreshWindowsSize（setScaleFactor +
 *   setDefaultBufferSize + pushEventWindow），陀螺仪接 Pojav GyroControl，快捷输入接 Pojav TouchCharInput，
 *   日志接 FCL LogWindow 移植，其余输入行为由 GameInputHandler（FCLInput/TouchPad 移植）读取。
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class InGameControlOverlay(
    private val activity: GameActivity,
    private val onEsc: () -> Unit,
    private val onScreenshot: () -> Unit,
    private val onExitGame: () -> Unit,
    private val onCancelGameGesture: () -> Unit
) {

    // ===== FCL 配色（colors.xml：ui_bg_color / primary_text / default_theme_color） =====
    private val fclBg = Color.argb(0x40, 0xF4, 0xF4, 0xF4)      // #40F4F4F4
    private val fclText = Color.parseColor("#0E0E0E")           // primary_text
    private val fclAccent = Color.parseColor("#7797CF")         // default_theme_color
    private val fclDivider = Color.parseColor("#A9A9A9")        // darker_gray

    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).toInt()

    private var hintView: View? = null
    private var panelView: View? = null
    private var leftBar: View? = null
    private var rightBar: View? = null
    private var chevronView: ChevronSwipeView? = null
    private var hintAutoHide: Handler? = null

    private var hintVisible = false
    private var panelVisible = false

    // 显示 FPS / 内存（FCL 右栏「显示FPS」/调试「显示内存」，menu_right.xml switch_show_fps / switch_show_memory）
    private var fpsView: TextView? = null
    private var memView: TextView? = null
    private var infoHandler: Handler? = null
    private var fpsEnabled = false
    private var memEnabled = false

    /** FCL 手柄「禁用手柄映射」：运行时字段（FCL GameMenu.gamepadDisabled，不入 menu_setting） */
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

        card.addView(TextView(activity).apply {
            text = "（演示遮罩 · 样式参考 FCL 控制面板）"
            setTextColor(Color.argb(140, 150, 150, 165))
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10f) }
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

    // ===================== FCL 样式控制面板（左右双侧边栏） =====================

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

    /** 构建单条 FCL 样式侧边栏（270dp 宽，上下留 30dp，贴左/右边缘） */
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
                cornerRadius = dp(8f).toFloat()
                setColor(fclBg)
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

        // 标题（FCL menu_left/menu_right：14sp 居中）
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

        // 内容区（ScrollView，FCL 对应结构）
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

    /** CTRLMAP 下的布局文件列表（对应 FCL 的 Controllers 控制器列表） */
    private fun layoutFileList(): List<String> {
        val dir = File(Tools.CTRLMAP_PATH)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?.sorted() ?: emptyList()
    }

    private fun buildLeftContent(content: LinearLayout) {
        // 当前布局方案（menu_left.xml current_controller，对应 FCL 的 Controllers 列表）
        val layouts = layoutFileList()
        content.addView(sectionHeader("当前布局方案"))
        content.addView(spinnerRow("当前布局方案", layouts, 0) { index ->
            val name = layouts.getOrNull(index) ?: return@spinnerRow
            runCatching {
                activity.controlLayout?.loadLayout(Tools.CTRLMAP_PATH + "/" + name + ".json")
            }.onFailure {
                Toast.makeText(activity, "加载布局失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // 编辑模式（FCL edit_mode → ViewManager 编辑；Pojav 等价 = ControlLayout.setModifiable）
        content.addView(switchRow("编辑模式", activity.controlLayout?.getModifiable() == true) { on ->
            activity.controlLayout?.setModifiable(on)
            VerseLogger.info("控制面板：编辑模式=$on", "Control")
        })
        // 显示控件边缘（FCL show_boundary；Pojav 等价 = 编辑模式下的句柄/边界视图）
        content.addView(switchRow("显示控件边缘", false) { on ->
            activity.controlLayout?.setModifiable(on)
            VerseLogger.info("控制面板：显示控件边缘=$on", "Control")
        })
        // 隐藏所有控件（FCL hideAllViews；Pojav 等价 = ControlLayout.setControlVisible）
        content.addView(switchRow("隐藏所有控件", false) { on ->
            activity.controlLayout?.setControlVisible(!on)
            VerseLogger.info("控制面板：隐藏所有控件=$on", "Control")
        })
        // 自动吸附（FCL TouchPad.autoFit；Pojav 等价 = 按屏幕宽度自适应按钮缩放，
        // 通过 PREF_BUTTONSIZE 控制并在间距变化时重新加载布局生效）
        content.addView(switchRow("自动吸附", MenuSetting.isAutoFit) { on ->
            MenuSetting.isAutoFit = on
            MenuSetting.notifyChange(MenuSetting.Key.AUTO_FIT)
            applyAutoFit()
        })
        // 自动吸附间距（FCL auto_fit_dist，0-10dp：间距越大按钮整体越小）
        content.addView(seekbarRow("自动吸附间距", 0, 10, MenuSetting.autoFitDist, "dp") { v ->
            MenuSetting.autoFitDist = v
            MenuSetting.notifyChange(MenuSetting.Key.AUTO_FIT_DIST)
            if (MenuSetting.isAutoFit) applyAutoFit()
        })

        // 控件组（menu_left.xml edit_layout 区块）
        content.addView(sectionHeader("控件组"))
        content.addView(spinnerRow("当前控件组", layouts, 0) { index ->
            val name = layouts.getOrNull(index) ?: return@spinnerRow
            runCatching {
                activity.controlLayout?.loadLayout(Tools.CTRLMAP_PATH + "/" + name + ".json")
            }.onFailure {
                Toast.makeText(activity, "加载控件组失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        })
        // 控件组管理（FCL manage_view_groups → ViewGroupDialog；Pojav 等价 = 布局加载/管理对话框）
        content.addView(buttonRow("控件组", "管理") {
            activity.controlLayout?.openLoadDialog()
        })
        // 添加按键（FCL add_button → EditViewDialog；Pojav 等价 = addControlButton + 编辑对话框）
        content.addView(buttonRow("添加按键", "添加") {
            addNewControl(ControlData())
        })
        // 添加方向控制键（FCL add_direction → EditViewDialog(ControlDirectionData)；Pojav 等价 = addJoystickButton）
        content.addView(buttonRow("添加方向控制键", "添加") {
            addNewControl(ControlJoystickData())
        })

        // 样式（menu_left.xml style 区块）
        content.addView(sectionHeader("样式"))
        // 按键样式（FCL manage_button_style → ButtonStyleDialog；Pojav 等价 = 编辑最后添加的按键外观）
        content.addView(buttonRow("按键样式", "管理") {
            editLastControl(isJoystick = false)
        })
        // 方向控制键样式（FCL manage_direction_style → DirectionStyleDialog；Pojav 等价 = 编辑方向键外观）
        content.addView(buttonRow("方向控制键样式", "管理") {
            editLastControl(isJoystick = true)
        })
    }

    /** 添加默认控件并进入编辑模式（对应 FCL EditViewDialog 的添加流程） */
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

    /** 打开 Pojav EditControlSideDialog 编辑最后一个（方向键）控件，等价 FCL 的样式管理对话框 */
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
        content.addView(sectionHeader("功能"))
        // 锁定菜单键（FCL lockMenuView：锁定后双指下滑不能唤起/收起菜单）
        content.addView(switchRow("锁定菜单键", MenuSetting.isLockMenuView) { on ->
            MenuSetting.isLockMenuView = on
            MenuSetting.notifyChange(MenuSetting.Key.LOCK_MENU_VIEW)
        })
        // 隐藏菜单键（FCL hideMenuView：隐藏全部虚拟控件 + 提示）
        content.addView(switchRow("隐藏菜单键", MenuSetting.isHideMenuView) { on ->
            MenuSetting.isHideMenuView = on
            MenuSetting.notifyChange(MenuSetting.Key.HIDE_MENU_VIEW)
            activity.controlLayout?.setControlVisible(!on)
            if (on) {
                Toast.makeText(activity, "菜单按键已隐藏，按下音量键打开菜单。", Toast.LENGTH_LONG).show()
            }
        })
        // 显示FPS（FCL switch_show_fps）
        content.addView(switchRow("显示FPS", MenuSetting.isShowFps) { on ->
            MenuSetting.isShowFps = on
            MenuSetting.notifyChange(MenuSetting.Key.SHOW_FPS)
            setFpsVisible(on)
        })
        // 游戏菜单（ESC；Verse 补充入口，对应 FCL MenuView 的暂停菜单键）
        content.addView(buttonRow("游戏菜单", "打开") { onEsc() })
        // 联机菜单（FCL open_multiplayer_menu）
        content.addView(buttonRow("联机菜单", "打开") { activity.showMultiplayerEntry() })
        // 快捷输入（FCL open_quick_input → TouchCharInput）
        content.addView(buttonRow("快捷输入", "管理") { activity.showQuickInput() })
        // 发送键值（FCL open_send_key → SelectKeycodeDialog）
        content.addView(buttonRow("发送键值", "发送") { showSendKeyDialog() })
        // 禁用软键盘自适应（FCL switch_soft_keyboard_adjust；SoftKeyboardHelper 未引入，仅持久化）
        content.addView(switchRow("禁用软键盘自适应", MenuSetting.isDisableSoftKeyAdjust) { on ->
            MenuSetting.isDisableSoftKeyAdjust = on
            MenuSetting.notifyChange(MenuSetting.Key.DISABLE_SOFT_KEY_ADJUST)
        })
        // 物品栏缩放（FCL item_bar_width/height；GameItemBar 未引入，仅持久化）
        content.addView(seekbarRow("物品栏缩放 - 宽度", 0, 100, MenuSetting.itemBarWidth, "%") { v ->
            MenuSetting.itemBarWidth = v
            MenuSetting.notifyChange(MenuSetting.Key.ITEM_BAR_WIDTH)
        })
        content.addView(seekbarRow("物品栏缩放 - 高度", 0, 100, MenuSetting.itemBarHeight, "%") { v ->
            MenuSetting.itemBarHeight = v
            MenuSetting.notifyChange(MenuSetting.Key.ITEM_BAR_HEIGHT)
        })
        // 窗口比例（FCL window_scale，1-300%，refreshWindowsSize）
        content.addView(seekbarRow("窗口比例", 1, 300, (MenuSetting.windowScale * 100).toInt(), "%") { v ->
            MenuSetting.windowScale = v / 100.0
            MenuSetting.notifyChange(MenuSetting.Key.WINDOW_SCALE)
            refreshWindowsSize(MenuSetting.windowScale)
        })
        // 光标偏移（FCL cursor_offset，-150~150，refreshWindowsSize）
        content.addView(seekbarRow("光标偏移", -150, 150, MenuSetting.cursorOffset.toInt(), "") { v ->
            MenuSetting.cursorOffset = v.toDouble()
            MenuSetting.notifyChange(MenuSetting.Key.CURSOR_OFFSET)
            refreshWindowsSize(MenuSetting.windowScale)
        })

        // ===== 手势 =====
        content.addView(sectionHeader("手势"))
        content.addView(switchRow("禁用手势", MenuSetting.isDisableGesture) { on ->
            MenuSetting.isDisableGesture = on
            MenuSetting.notifyChange(MenuSetting.Key.DISABLE_GESTURE)
        })
        content.addView(switchRow("禁用仿基岩版手势", MenuSetting.isDisableBEGesture) { on ->
            MenuSetting.isDisableBEGesture = on
            MenuSetting.notifyChange(MenuSetting.Key.DISABLE_BE_GESTURE)
        })
        // 触控模式（FCL gesture_mode_spinner：建筑模式/战斗模式）
        content.addView(spinnerRow(
            "触控模式",
            listOf("建筑模式", "战斗模式"),
            MenuSetting.gestureMode
        ) { index ->
            MenuSetting.gestureMode = index
            MenuSetting.notifyChange(MenuSetting.Key.GESTURE_MODE)
        })
        content.addView(switchRow("禁用左半屏触控", MenuSetting.isDisableLeftTouch) { on ->
            MenuSetting.isDisableLeftTouch = on
            MenuSetting.notifyChange(MenuSetting.Key.DISABLE_LEFT_TOUCH)
        })

        // ===== 鼠标 =====
        content.addView(sectionHeader("鼠标"))
        // 鼠标控制模式（FCL mouse_mode_spinner：点击/拖动）
        content.addView(spinnerRow(
            "鼠标控制模式",
            listOf("点击", "拖动"),
            MenuSetting.mouseMoveMode
        ) { index ->
            MenuSetting.mouseMoveMode = index
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_MOVE_MODE)
        })
        content.addView(seekbarRow("鼠标灵敏度", 100, 1000, (MenuSetting.mouseSensitivity * 100).toInt(), "%") { v ->
            MenuSetting.mouseSensitivity = v / 100.0
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_SENSITIVITY)
        })
        content.addView(seekbarRow("鼠标灵敏度（当指针可见时）", 100, 1000, (MenuSetting.mouseSensitivityCursor * 100).toInt(), "%") { v ->
            MenuSetting.mouseSensitivityCursor = v / 100.0
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_SENSITIVITY_CURSOR)
        })
        // 鼠标尺寸/偏移（FCL 作用于 CursorView；本实现为可见光标 dot，实时应用）
        content.addView(seekbarRow("鼠标尺寸", 0, 30, MenuSetting.mouseSize, "dp") { v ->
            MenuSetting.mouseSize = v
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_SIZE)
            activity.applyCursorStyle()
        })
        content.addView(seekbarRow("鼠标偏移X", -30, 30, MenuSetting.mouseOffsetX, "dp") { v ->
            MenuSetting.mouseOffsetX = v
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_OFFSET_X)
        })
        content.addView(seekbarRow("鼠标偏移Y", -30, 30, MenuSetting.mouseOffsetY, "dp") { v ->
            MenuSetting.mouseOffsetY = v
            MenuSetting.notifyChange(MenuSetting.Key.MOUSE_OFFSET_Y)
        })
        // 实体鼠标控制（FCL switch_physical_mouse_mode → GameMenu.setOnGenericMotionListener）
        content.addView(switchRow("实体鼠标控制", MenuSetting.isPhysicalMouseMode) { on ->
            MenuSetting.isPhysicalMouseMode = on
            MenuSetting.notifyChange(MenuSetting.Key.PHYSICAL_MOUSE_MODE)
            if (on) {
                activity.touchOverlay?.releasePointerCapture()
            } else {
                activity.touchOverlay?.requestPointerCapture()
            }
        })

        // ===== 手柄 =====
        content.addView(sectionHeader("手柄"))
        // 禁用手柄映射（FCL GameMenu.gamepadDisabled，运行时字段不入盘）
        content.addView(switchRow("禁用手柄映射", gamepadDisabled) { on ->
            gamepadDisabled = on
        })
        // 重置映射 / 按键绑定：FCL 依赖 fr.spse.gamepad_remapper + com.mio.touchcontroller，未引入
        content.addView(buttonRow("重置映射", "重置") {
            Toast.makeText(activity, "重置映射（FCL 手柄栈未引入）", Toast.LENGTH_SHORT).show()
        })
        content.addView(buttonRow("按键绑定", "打开") {
            Toast.makeText(activity, "按键绑定（FCL 手柄栈未引入）", Toast.LENGTH_SHORT).show()
        })
        content.addView(seekbarRow("调整死区", 0, 100, (MenuSetting.gamepadDeadzone * 100).toInt(), "%") { v ->
            MenuSetting.gamepadDeadzone = v / 100.0
            MenuSetting.notifyChange(MenuSetting.Key.GAMEPAD_DEADZONE)
        })

        // ===== 陀螺仪 =====
        content.addView(sectionHeader("陀螺仪"))
        // 陀螺仪（FCL switch_gyro → Gyroscope.enableSensor/disableSensor；接 Pojav GyroControl）
        content.addView(switchRow("陀螺仪", MenuSetting.isEnableGyroscope) { on ->
            MenuSetting.isEnableGyroscope = on
            MenuSetting.notifyChange(MenuSetting.Key.ENABLE_GYROSCOPE)
            activity.gyroControl?.let {
                if (on) it.enable() else it.disable()
            }
        })
        // 反转陀螺仪（FCL switch_gyro_invert；Pojav GyroControl 支持 PREF_GYRO_INVERT_X/Y）
        content.addView(switchRow("反转陀螺仪", MenuSetting.isInvertGyroscope) { on ->
            MenuSetting.isInvertGyroscope = on
            MenuSetting.notifyChange(MenuSetting.Key.INVERT_GYROSCOPE)
            runCatching {
                LauncherPreferences.PREF_GYRO_INVERT_X = on
                LauncherPreferences.PREF_GYRO_INVERT_Y = on
            }.onFailure {
                VerseLogger.warn("写入 PREF_GYRO_INVERT 失败: ${it.message}", "Control")
            }
        })
        // 陀螺仪灵敏度（FCL gyro_sensitivity，0-1000）
        content.addView(seekbarRow("陀螺仪灵敏度", 0, 1000, MenuSetting.gyroscopeSensitivity, "") { v ->
            MenuSetting.gyroscopeSensitivity = v
            MenuSetting.notifyChange(MenuSetting.Key.GYROSCOPE_SENSITIVITY)
            applyGyroSensitivity(v)
        })

        // ===== 调试 =====
        content.addView(sectionHeader("调试"))
        // 显示内存（FCL switch_show_memory）
        content.addView(switchRow("显示内存", MenuSetting.isShowMemory) { on ->
            MenuSetting.isShowMemory = on
            MenuSetting.notifyChange(MenuSetting.Key.SHOW_MEMORY)
            setMemoryVisible(on)
        })
        // 持续性能模式（FCL switch_performance → setSustainedPerformanceMode）
        content.addView(switchRow("持续性能模式", MenuSetting.isPerformanceMode) { on ->
            MenuSetting.isPerformanceMode = on
            MenuSetting.notifyChange(MenuSetting.Key.PERFORMANCE_MODE)
            activity.window.setSustainedPerformanceMode(on)
        })
        // 显示日志（FCL switch_show_log → LogWindow）
        content.addView(switchRow("显示日志", MenuSetting.isShowLog) { on ->
            MenuSetting.isShowLog = on
            MenuSetting.notifyChange(MenuSetting.Key.SHOW_LOG)
            updateLogWindowVisibility()
        })
        // 自动显示日志（FCL switch_auto_show_log）
        content.addView(switchRow("自动显示日志", MenuSetting.isAutoShowLog) { on ->
            MenuSetting.isAutoShowLog = on
            MenuSetting.notifyChange(MenuSetting.Key.AUTO_SHOW_LOG)
            updateLogWindowVisibility()
        })
        // 强制退出（FCL force_exit → 确认对话框后强退）
        content.addView(buttonRow("强制退出", "退出") {
            AlertDialog.Builder(activity)
                .setTitle("强制退出")
                .setMessage("你确定要强制退出吗？")
                .setCancelable(false)
                .setPositiveButton("确定") { _, _ -> onExitGame() }
                .setNegativeButton("取消", null)
                .show()
        })

        // 初始状态恢复（FCL GameMenu.setChecked 触发监听器的等价处理）
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
        if (MenuSetting.isAutoFit) applyAutoFit()
        updateLogWindowVisibility()
    }

    /** FCL 自动吸附的 Pojav 等价实现：按屏幕自适应调整按钮缩放（PREF_BUTTONSIZE）并重载布局 */
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

    /** FCL 陀螺仪灵敏度：写入 LauncherPreferences（Pojav GyroControl 运行时读取；
     *  FCL 默认 10 ↔ Pojav 默认 5.0，按 0.5 折算保持默认手感一致） */
    private fun applyGyroSensitivity(value: Int) {
        runCatching {
            LauncherPreferences.PREF_GYRO_SENSITIVITY = value * 0.5f
        }.onFailure {
            VerseLogger.warn("写入 PREF_GYRO_SENSITIVITY 失败: ${it.message}", "Control")
        }
    }

    /** 日志窗口可见性（FCL bindShowLog/bindAutoShowLog 的等价） */
    private fun updateLogWindowVisibility() {
        val logWin = activity.logWindow ?: return
        logWin.setVisibility(MenuSetting.isShowLog || MenuSetting.isAutoShowLog)
    }

    /** FCL GameMenu.refreshWindowsSize：按 scaleFactor + cursorOffset 重推窗口尺寸 */
    private fun refreshWindowsSize(factor: Double) {
        val bridge = activity.currentBridge ?: return
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val screenHeight = activity.resources.displayMetrics.heightPixels
        bridge.setScaleFactor(factor)
        var width = ((screenWidth + MenuSetting.cursorOffset) * factor).toInt()
        var height = (screenHeight * factor).toInt()
        if (FCLBridge.FORCE_RESOLUTION) {
            width = FCLBridge.FORCE_RESOLUTION_WIDTH
            height = FCLBridge.FORCE_RESOLUTION_HEIGHT
        }
        bridge.surfaceTexture?.setDefaultBufferSize(width, height)
        // pushEventWindow 依赖游戏 JVM 侧 org.lwjgl.glfw.CallbackBridge，可能未就绪，兜底捕获
        runCatching {
            bridge.pushEventWindow(width, height)
        }
    }

    /** 发送键值对话框：SelectKeycodeDialog 的功能等价（多选键码 → 按下 50ms → 释放，FCL onClick 流程） */
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
                // FCL GameMenu.onClick(sendKeycode)：按下全部 → 50ms → 释放全部
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

    // ===================== 通用行构建器 =====================

    /** 分区标题（FCL：11sp + 1dp 分隔线） */
    private fun sectionHeader(header: String): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5f) }
            addView(TextView(activity).apply {
                text = header
                setTextColor(fclText)
                textSize = 11f
                isSingleLine = true
            })
            addView(View(activity).apply {
                setBackgroundColor(fclDivider)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(1f)
                )
            })
        }
    }

    /** 对齐设置行（FCL：标签 weight=1 靠左 + 控件靠右，12sp） */
    private fun alignedRow(label: String, control: View): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5f), dp(0f), dp(5f), dp(0f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5f); bottomMargin = dp(5f) }
            addView(TextView(activity).apply {
                text = label
                setTextColor(fclText)
                textSize = 12f
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { gravity = Gravity.CENTER_VERTICAL }
            })
            addView(control)
        }
    }

    /** 开关行（对应 FCLSwitch：文本 12sp + 开关） */
    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = Switch(activity).apply {
            isChecked = checked
            textSize = 12f
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        return alignedRow(label, sw)
    }

    /** 下拉选择行（对应 FCLSpinner，FCL 的 FCLSpinner 等价） */
    private fun spinnerRow(
        label: String,
        items: List<String>,
        initialIndex: Int,
        onSelected: (Int) -> Unit
    ): View {
        val spinner = Spinner(activity).apply {
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, items).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            this.adapter = adapter
            if (initialIndex in items.indices) setSelection(initialIndex)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onSelected(position)
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        return alignedRow(label, spinner)
    }

    /** 滑块行（对应 FCLNumberSeekBar：标签行 + 数值 + 全宽滑块） */
    private fun seekbarRow(
        label: String,
        min: Int,
        max: Int,
        initial: Int,
        suffix: String,
        onChanged: (Int) -> Unit
    ): View {
        val valueText = TextView(activity).apply {
            setTextColor(fclText)
            textSize = 12f
            isSingleLine = true
            text = "$initial$suffix"
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f); bottomMargin = dp(3f) }
            addView(alignedRow(label, valueText))
            addView(SeekBar(activity).apply {
                this.min = min
                this.max = max
                progress = initial.coerceIn(min, max)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        valueText.text = "$progress$suffix"
                        if (fromUser) onChanged(progress)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            })
        }
        return container
    }

    /** 按钮行（对照 FCL menu_right：标签左 + 小按钮右，按钮圆角 8dp + 1dp 描边 + 主题色文字 12sp） */
    private fun buttonRow(label: String, buttonText: String, onClick: () -> Unit): View {
        val btn = TextView(activity).apply {
            text = buttonText
            setTextColor(fclAccent)
            textSize = 12f
            isSingleLine = true
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8f).toFloat()
                setStroke(dp(1f), fclAccent)
            }
            setOnClickListener { onClick() }
        }
        return alignedRow(label, btn)
    }

    fun showPanel() {
        val view = panelView ?: return
        view.clearAnimation()
        view.visibility = View.VISIBLE
        view.bringToFront()
        // FCL Drawer 式滑入：左栏从左、右栏从右
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

    /** 指针 id -> 按下时的 Y（用于计算双指下滑距离），与 FCL 多指手势同思路 */
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
        // FCL 锁定菜单键：锁定后手势不能唤起/收起菜单
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

    // ===================== 显示 FPS / 内存（FCL 显示FPS/显示内存） =====================

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

    /** 创建 FPS / 内存文本视图（挂到 Activity 内容顶层，与 FCL 屏幕中央显示一致） */
    private fun ensureInfoOverlay() {
        if (fpsView == null) {
            fpsView = TextView(activity).apply {
                setTextColor(Color.argb(200, 255, 255, 255))
                textSize = 14f
                paint.isFakeBoldText = true
                visibility = View.GONE
            }
            activity.addContentView(
                fpsView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL or Gravity.TOP
                ).apply { topMargin = dp(80f) }
            )
        }
        if (memView == null) {
            memView = TextView(activity).apply {
                setTextColor(Color.argb(200, 255, 255, 255))
                textSize = 14f
                paint.isFakeBoldText = true
                visibility = View.GONE
            }
            activity.addContentView(
                memView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL or Gravity.TOP
                ).apply { topMargin = dp(104f) }
            )
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
