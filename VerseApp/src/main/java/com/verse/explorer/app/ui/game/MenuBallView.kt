package com.verse.explorer.app.ui.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.tungsten.fcl.setting.MenuSetting
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 游戏内菜单悬浮球，参考 ZL2 的 `FloatingBall` / `DraggableGameBall`：
 * - 图标就是启动器自己的应用图标（`packageManager.getApplicationIcon`）；
 * - 拖动超过 touchSlop 才算移动，松手一次性保存位置；未拖动则判定为点击 → 切换菜单；
 * - 位置始终钳制在父容器范围内（横竖屏切换后重新钳制）；
 * - 菜单打开时加一圈强调描边，对应 ZL2 的 ic_menu / ic_menu_open 状态切换；
 * - 不透明度对应 ZL2 的 menuBallOpacity。
 *
 * 触摸事件由 GameActivity 合成单指事件后送入，与游戏触控、双指下滑互不干扰。
 */
@SuppressLint("ViewConstructor")
class MenuBallView(
    context: Context,
    private val onBallClick: () -> Unit
) : View(context) {

    private val ballSize = (52f * resources.displayMetrics.density).roundToInt()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val appIcon: Drawable? = runCatching {
        context.packageManager.getApplicationIcon(context.packageName)
    }.getOrNull()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((255 * 0.30f).toInt(), 12, 12, 16)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = Color.argb(210, 232, 213, 213)
    }

    /** 菜单是否展开（绘制状态，对应 ZL2 的 opened 参数） */
    var menuOpened: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private var activePointerId = -1
    // 上游传入的是屏幕坐标（固定坐标系），球自身移动不会改变它
    private var downScreenX = 0f
    private var downScreenY = 0f
    private var downViewX = 0f
    private var downViewY = 0f
    private var dragging = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(ballSize, ballSize)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = cx - outlinePaint.strokeWidth / 2f
        canvas.drawCircle(cx, cy, radius, bgPaint)
        if (menuOpened) canvas.drawCircle(cx, cy, radius, outlinePaint)

        val icon = appIcon ?: return
        val iconSize = (ballSize * 0.62f).roundToInt()
        val left = ((width - iconSize) / 2f).roundToInt()
        val top = ((height - iconSize) / 2f).roundToInt()
        icon.setBounds(left, top, left + iconSize, top + iconSize)
        icon.draw(canvas)
    }

    /** ZL2：首次启动放顶部居中；已有位置则在容器尺寸变化后钳制回可视区域。 */
    fun applySavedPosition() {
        val container = parent as? View ?: return
        val maxX = (container.width - width).coerceAtLeast(0).toFloat()
        val maxY = (container.height - height).coerceAtLeast(0).toFloat()
        val savedX = MenuSetting.menuBallPosX
        x = if (savedX < 0f) maxX / 2f else savedX.coerceIn(0f, maxX)
        y = if (savedX < 0f) 16f * resources.displayMetrics.density
        else MenuSetting.menuBallPosY.coerceIn(0f, maxY)
    }

    /** ZL2 的 onSavePos：只在拖动结束、且抬起未被上层消费时保存。 */
    private fun savePosition() {
        MenuSetting.menuBallPosX = x
        MenuSetting.menuBallPosY = y
        MenuSetting.save()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                downScreenX = ev.x
                downScreenY = ev.y
                downViewX = x
                downViewY = y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId < 0) return true
                val dx = ev.x - downScreenX
                val dy = ev.y - downScreenY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                if (dragging) {
                    val container = parent as? View
                    val maxX = ((container?.width ?: 0) - width).coerceAtLeast(0).toFloat()
                    val maxY = ((container?.height ?: 0) - height).coerceAtLeast(0).toFloat()
                    x = (downViewX + dx).coerceIn(0f, maxX)
                    y = (downViewY + dy).coerceIn(0f, maxY)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) savePosition() else onBallClick()
                resetDrag()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                // 手势取消不触发点击（ZL2：drag 返回 false 时既不点击也不保存）
                resetDrag()
                return true
            }
        }
        return true
    }

    private fun resetDrag() {
        activePointerId = -1
        dragging = false
    }
}
