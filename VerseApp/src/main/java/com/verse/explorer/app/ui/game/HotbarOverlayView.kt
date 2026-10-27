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
 * 尺寸取自「设置 → 控制 → 物品栏缩放（宽度/高度）」：
 *  - 0 表示关闭该触控层（避免与自定义触控布局的按钮抢触摸）；
 *  - 大于 0 表示占屏幕的百分比：宽度取屏宽百分比，高度取屏高百分比，
 *    只设一项时另一项按快捷栏 9:2 的比例推导，滑条调整后立即生效并闪一下边框作为反馈
 *    （手动改尺寸后短暂高亮一次，作为“已生效”的反馈）。
 *
 * 触摸事件由 [com.verse.explorer.app.ui.game.GameActivity.dispatchTouchEvent] 手动路由：
 * 该 Activity 在游戏画面显示后不再走系统 View 分发，因此本视图只负责绘制与判定。
 */
class HotbarOverlayView(context: Context) : View(context) {

    companion object {

        /** 双击同一槽位的判定窗口 */
        private const val DOUBLE_TAP_MS = 200L

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

    /** 本触控层是否启用（滑条都为 0 时不启用，保持自定义布局原样） */
    val enabled: Boolean
        get() = MenuSetting.itemBarWidth > 0 || MenuSetting.itemBarHeight > 0

    /** 当前手势里被快捷栏接管的指针 id */
    private val capturedIds = mutableSetOf<Int>()
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
        hotbarRect = HotbarGeometry.rect(
            screenWidth = widthPx,
            screenHeight = heightPx,
            widthPercent = MenuSetting.itemBarWidth,
            heightPercent = MenuSetting.itemBarHeight
        )
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
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id !in capturedIds) continue
                    val slot = slotAt(event.getX(i), event.getY(i))
                    if (slot >= 0 && slot != lastSlot) {
                        // 手指在快捷栏上左右滑动：滑到哪格切哪格
                        lastSlot = slot
                        sendHotbarKey(slot)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
        lastSlot = slot
        sendHotbarKey(slot)
        val now = System.currentTimeMillis()
        if (now - lastTapTime <= DOUBLE_TAP_MS && slot == lastTapSlot) {
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
