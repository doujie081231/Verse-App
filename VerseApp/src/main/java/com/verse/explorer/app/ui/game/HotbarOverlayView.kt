package com.verse.explorer.app.ui.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.tungsten.fcl.setting.MenuSetting
import net.kdt.pojavlaunch.LwjglGlfwKeycode

/**
 * 游戏内快捷栏（物品栏）触控层：
 * 在快捷栏区域叠一块透明视图，点按 / 左右滑动即按位置发送 1~9 键切换槽位，
 * 同一槽位双击发送 F 交换主副手；
 * 手指在快捷栏上滑动时整段手势由本层独占，不再同时驱动视角。
 *
 * 判定方式对齐 ZL2 的 HotbarRule：
 *  - 自动（默认）：按 MC 的 GUI 缩放算 9 格尺寸（单格 = 缩放 × 20 px），开箱即用；
 *  - 自定义：用「设置 → 控制 → 物品栏缩放（宽度/高度）」的屏幕百分比，
 *    两项都为 0 时视为关闭该触控层（避免与自定义触控布局的按钮抢触摸）。
 * 手势同样对齐 ZL2：按下即切到该格、左右滑动跟手切格、同一格双击交换主副手（F）、
 * 长按一格丢弃（Q，未移动超过触摸阈值时才触发）。
 *
 * 触摸事件由 [com.verse.explorer.app.ui.game.GameActivity.dispatchTouchEvent] 手动路由：
 * 该 Activity 在游戏画面显示后不再走系统 View 分发，因此本视图只负责绘制与判定。
 */
class HotbarOverlayView(context: Context) : View(context) {

    companion object {

        /** 双击同一槽位的判定窗口（与 Android 的 doubleTapTimeout 同量级，ZL2 也用它） */
        private const val DOUBLE_TAP_MS = 300L

        private const val FEEDBACK_MS = 1500L

        /** 供控制面板滑条调用：让游戏内的快捷栏边框闪一下，确认尺寸已生效 */
        fun requestFeedback() {
            feedbackVersion++
        }

        @Volatile
        private var feedbackVersion = 0
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawRect = RectF()
    private var hotbarRect: HotbarGeometry.Rect? = null

    /** 本触控层是否启用：自动模式恒启用；自定义模式下两项百分比都为 0 时关闭 */
    val enabled: Boolean
        get() = if (MenuSetting.itemBarRule == MenuSetting.ITEM_BAR_RULE_CUSTOM) {
            MenuSetting.itemBarWidth > 0 || MenuSetting.itemBarHeight > 0
        } else {
            true
        }

    /** 当前手势里被快捷栏接管的指针 id */
    private val capturedIds = mutableSetOf<Int>()

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    /** 本次手势按下的位置，用于判断是否移动超过阈值（对齐 ZL2 的 touchSlop 判定） */
    private var downX = 0f
    private var downY = 0f

    /** 本次手势已触发长按（丢弃），后续不再切格 */
    private var longPressFired = false

    private val touchSlop: Int
        get() = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var lastSlot = -1
    private var lastTapTime = 0L
    private var lastTapSlot = -1
    private var feedbackUntil = 0L
    private var seenFeedbackVersion = feedbackVersion

    init {
        setWillNotDraw(false)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
        )
    }

    /** 按当前设置重算快捷栏矩形（屏幕坐标，与窗口坐标一致） */
    private fun refreshRect(widthPx: Int, heightPx: Int) {
        hotbarRect = if (MenuSetting.itemBarRule == MenuSetting.ITEM_BAR_RULE_CUSTOM) {
            HotbarGeometry.rect(
                screenWidth = widthPx,
                screenHeight = heightPx,
                widthPercent = MenuSetting.itemBarWidth,
                heightPercent = MenuSetting.itemBarHeight
            )
        } else {
            HotbarGeometry.autoRect(
                screenWidth = widthPx,
                screenHeight = heightPx,
                guiScaleOption = MenuSetting.mcGuiScale
            )
        }
        hotbarRect?.let {
            drawRect.set(it.left, it.top, it.right, it.bottom)
        } ?: drawRect.setEmpty()
    }

    /** 按 (x, y) 命中槽位，0..8；不在矩形内返回 -1 */
    private fun slotAt(x: Float, y: Float): Int {
        val rect = hotbarRect ?: return -1
        return HotbarGeometry.slotAt(rect, x, y)
    }

    /** 发送 1~9 键切换槽位 */
    private fun sendHotbarKey(slot: Int) {
        val key = LwjglGlfwKeycode.GLFW_KEY_1 + HotbarGeometry.keyOffset(slot)
        runCatching {
            org.lwjgl.glfw.CallbackBridge.sendKeyPress(key)
        }
    }

    private fun swapHands() {
        runCatching {
            org.lwjgl.glfw.CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_F.toInt())
        }
    }

    /** 发送丢弃键（Q），对应 ZL2 的 hotbarLongClick */
    private fun dropItem() {
        runCatching {
            org.lwjgl.glfw.CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_Q.toInt())
        }
    }

    /** 启动长按计时；移动超过阈值或抬手都会取消 */
    private fun startLongPress() {
        cancelHotbarLongPress()
        if (!MenuSetting.itemBarLongPress) return
        val delay = MenuSetting.itemBarLongPressDelay.coerceIn(100, 1000).toLong()
        val runnable = Runnable {
            longPressFired = true
            dropItem()
        }
        longPressRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    private fun cancelHotbarLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    /**
     * 手动路由的触摸入口。
     * @param skipIds 已被虚拟按钮接管的指针（按钮优先，快捷栏不抢）
     * @return 本层接管的指针 id 集合，调用方需把这些指针排除出游戏触控
     */
    fun handleTouch(event: MotionEvent, skipIds: Set<Int>): Set<Int> {
        if (!enabled || !org.lwjgl.glfw.CallbackBridge.isGrabbing()) {
            capturedIds.clear()
            return emptySet()
        }
        refreshRect(width, height)
        if (hotbarRect == null) return emptySet()

        // 抬起类事件处理完就要清空捕获，但本次抬手仍必须被排除出游戏触控，
        // 否则抓取态会把这次抬手当成点击 → 快捷栏点一下顺带在游戏里挥一下手。
        val entryIds = capturedIds.toSet()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                if (id !in skipIds && slotAt(event.getX(index), event.getY(index)) >= 0) {
                    capturedIds.add(id)
                    handleDown(event.getX(index), event.getY(index))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i0 = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                val dx = event.getX(i0) - downX
                val dy = event.getY(i0) - downY
                if (dx * dx + dy * dy > touchSlop * touchSlop) cancelHotbarLongPress()
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id !in capturedIds) continue
                    if (longPressFired) continue
                    val slot = slotAt(event.getX(i), event.getY(i))
                    if (slot >= 0 && slot != lastSlot) {
                        // 手指在快捷栏上左右滑动：滑到哪格切哪格
                        lastSlot = slot
                        sendHotbarKey(slot)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelHotbarLongPress()
                longPressFired = false
                capturedIds.clear()
                lastSlot = -1
            }
            MotionEvent.ACTION_POINTER_UP -> {
                capturedIds.remove(event.getPointerId(event.actionIndex))
                if (capturedIds.isEmpty()) lastSlot = -1
            }
        }
        return entryIds + capturedIds
    }

    private fun handleDown(x: Float, y: Float) {
        val slot = slotAt(x, y)
        if (slot < 0) return
        downX = x
        downY = y
        longPressFired = false
        lastSlot = slot
        sendHotbarKey(slot)
        startLongPress()
        val now = System.currentTimeMillis()
        if (MenuSetting.itemBarDoubleTap &&
            now - lastTapTime <= DOUBLE_TAP_MS && slot == lastTapSlot
        ) {
            swapHands()
            lastTapTime = 0L
            lastTapSlot = -1
        } else {
            lastTapTime = now
            lastTapSlot = slot
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 滑条被拖动时闪一下边框，让「缩放后没有任何效果」不再发生
        if (feedbackVersion != seenFeedbackVersion) {
            seenFeedbackVersion = feedbackVersion
            feedbackUntil = System.currentTimeMillis() + FEEDBACK_MS
            invalidate()
        }
        if (!enabled || hotbarRect == null || drawRect.isEmpty) return
        val now = System.currentTimeMillis()
        if (now > feedbackUntil) return
        paint.color = Color.argb(200, 255, 255, 255)
        canvas.drawRoundRect(drawRect, 8f, 8f, paint)
        invalidate()
    }
}
