package com.verse.explorer.app.ui.game

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.verse.explorer.app.ui.VerseLogger
import java.io.File
import java.io.FileOutputStream

/**
 * 游戏内控制面板演示遮罩（样式参考 FCL 控制面板）：
 * - 不采用悬浮窗唤起控制面板，改为进入游戏画面后弹出演示遮罩，
 *   提示「三指下滑唤起控制面板」；
 * - 三指下滑手势（由 GameActivity 在分发触摸前检测）可唤起
 *   FCL 样式的底部控制面板（菜单/虚拟键/截图/退出）；
 * - 遮罩为演示性质：点击任意位置或「我知道了」、8 秒后自动消失，不阻塞游戏。
 *
 * 交互架构：遮罩/面板遮挡期间由视图自身消费触摸（按钮可正常点击）；
 * 仅在无遮挡的正常游戏态下，GameActivity 调用 [trackMultiTouch] 检测三指下滑。
 * 手势/按钮只复用现有输入链路（GameInputHandler / CallbackBridge），不自研逻辑。
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class InGameControlOverlay(
    private val activity: ComponentActivity,
    private val onEsc: () -> Unit,
    private val onToggleVirtualControls: () -> Unit,
    private val onScreenshot: () -> Unit,
    private val onExitGame: () -> Unit,
    private val onCancelGameGesture: () -> Unit
) {

    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).toInt()

    private var hintView: View? = null
    private var panelView: View? = null
    private var chevronView: ChevronSwipeView? = null
    private var hintAutoHide: android.os.Handler? = null

    private var hintVisible = false
    private var panelVisible = false

    // ===================== 演示遮罩 =====================

    /** 创建演示遮罩视图（全屏半透明，覆盖在触控覆盖层之上） */
    fun createHintView(): View {
        val maskBg = Color.argb(110, 10, 10, 14)
        val cardColor = Color.argb(225, 26, 26, 38)   // FCL 深色半透明圆角卡片
        val borderColor = Color.argb(60, 255, 255, 255)
        val textMain = Color.WHITE
        val textSub = Color.argb(200, 224, 224, 230)

        val root = FrameLayout(activity)
        root.setBackgroundColor(maskBg)
        // 遮罩可见时全部消费：点击任意空白处关闭；三指下滑则直接唤起控制面板（完整演示链路）
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
            text = "三指下滑唤起控制面板"
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
            text = "在屏幕任意位置用三根手指向下滑动\n即可唤起游戏控制面板"
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
        // 演示遮罩自动淡出，避免一直遮挡游戏
        hintAutoHide?.removeCallbacksAndMessages(null)
        hintAutoHide = android.os.Handler(android.os.Looper.getMainLooper())
        hintAutoHide?.postDelayed({ dismissHint() }, 8000)
        chevronView?.startSwipe()
        hintVisible = true
        VerseLogger.info("三指下滑演示遮罩已显示", "Control")
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
        VerseLogger.info("三指下滑演示遮罩已消失", "Control")
    }

    // ===================== FCL 样式控制面板 =====================

    /** 创建底部控制面板（FCL 风格：深色半透明圆角，按钮为「图标+文字」单元） */
    fun createPanelView(): View {
        val panelBg = Color.argb(215, 26, 26, 38)
        val cellBg = Color.argb(90, 255, 255, 255)
        val cellBgPressed = Color.argb(150, 255, 255, 255)
        val cellBorder = Color.argb(50, 255, 255, 255)
        val iconColor = Color.argb(235, 232, 232, 232)
        val labelColor = Color.argb(200, 200, 200, 210)

        val root = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // 面板展示期间挡住下层游戏输入；点击面板外空白处收起
            setOnClickListener { hidePanel(true) }
        }

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20f).toFloat()
                setColor(panelBg)
                setStroke(dp(1f), cellBorder)
            }
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            // 面板本身消费点击，避免点面板空白处把面板关掉；多指下滑手势仍响应
            setOnClickListener { }
            setOnTouchListener { _, ev -> handleOverlayViewTouch(ev) }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(24f) }
        }
        root.addView(panel)

        fun cell(glyph: String, label: String, onClick: () -> Unit): View {
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(14f).toFloat()
                    setColor(cellBg)
                    setStroke(dp(1f), cellBorder)
                }
                isClickable = true
                isFocusable = true
                setPadding(dp(10f), dp(12f), dp(10f), dp(10f))
                layoutParams = LinearLayout.LayoutParams(
                    dp(76f),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(8f) }
                setOnClickListener { onClick() }
                setOnTouchListener { v, ev ->
                    val bg = v.background as? GradientDrawable
                    if (bg != null) {
                        bg.setColor(if (ev.actionMasked == MotionEvent.ACTION_DOWN) cellBgPressed else cellBg)
                    }
                    false
                }
            }
            box.addView(TextView(activity).apply {
                text = glyph
                setTextColor(iconColor)
                textSize = 22f
                gravity = Gravity.CENTER
            })
            box.addView(TextView(activity).apply {
                text = label
                setTextColor(labelColor)
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6f) }
            })
            return box
        }

        panel.addView(cell("☰", "菜单", onEsc))
        panel.addView(cell("⌨", "虚拟键", onToggleVirtualControls))
        panel.addView(cell("✂", "截图", onScreenshot))
        panel.addView(cell("✕", "退出", onExitGame))

        panelView = root
        return root
    }

    fun showPanel() {
        val view = panelView ?: return
        view.clearAnimation()
        view.visibility = View.VISIBLE
        view.bringToFront()
        val slide = TranslateAnimation(0f, 0f, dp(120f).toFloat() * density, 0f)
        slide.duration = 220
        slide.interpolator = DecelerateInterpolator()
        view.startAnimation(slide)
        panelVisible = true
        VerseLogger.info("控制面板已唤起（三指下滑）", "Control")
    }

    fun hidePanel(animate: Boolean) {
        val view = panelView ?: return
        if (view.visibility != View.VISIBLE) return
        if (animate) {
            val slide = TranslateAnimation(0f, 0f, 0f, dp(120f).toFloat() * density)
            slide.duration = 180
            slide.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(a: Animation?) {}
                override fun onAnimationEnd(a: Animation?) {
                    view.visibility = View.GONE
                }
                override fun onAnimationRepeat(a: Animation?) {}
            })
            view.startAnimation(slide)
        } else {
            view.visibility = View.GONE
        }
        panelVisible = false
        VerseLogger.info("控制面板已收起", "Control")
    }

    fun isHintVisible(): Boolean = hintVisible
    fun isPanelVisible(): Boolean = panelVisible
    fun isOverlayVisible(): Boolean = hintVisible || panelVisible

    // ===================== 三指下滑手势 =====================

    /** 指针 id -> 按下时的 Y（用于计算三指下滑距离），与 FCL 多指手势同思路 */
    private val pointerStart = mutableMapOf<Int, Float>()
    private val pointerCurrent = mutableMapOf<Int, Float>()
    private var multiSwipeFired = false
    private val swipeThreshold = 140f * density

    /**
     * 跟踪多指触摸。返回 true 表示本次事件中三指下滑已成立（手势已消费）。
     * 遮罩可见 / 面板可见 / 正常游戏态（GameActivity 分发前）三条路径共用，
     * 手势判定只依赖「三根手指整体下滑距离 ≥ 阈值」，不新增私有逻辑。
     */
    fun trackMultiTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerStart.clear()
                pointerCurrent.clear()
                multiSwipeFired = false
                val id = ev.getPointerId(0)
                pointerStart[id] = ev.getY(0)
                pointerCurrent[id] = ev.getY(0)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                val id = ev.getPointerId(index)
                pointerStart[id] = ev.getY(index)
                pointerCurrent[id] = ev.getY(index)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    pointerCurrent[ev.getPointerId(i)] = ev.getY(i)
                }
                if (pointerStart.size >= 3 && !multiSwipeFired) {
                    var sum = 0f
                    var n = 0
                    for ((id, start) in pointerStart) {
                        val current = pointerCurrent[id] ?: continue
                        sum += current - start
                        n++
                    }
                    if (n >= 3 && sum / n >= swipeThreshold) {
                        multiSwipeFired = true
                        return true
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val id = ev.getPointerId(ev.actionIndex)
                pointerCurrent.remove(id)
                pointerStart.remove(id)
                if (pointerStart.isEmpty()) multiSwipeFired = false
            }
        }
        return multiSwipeFired
    }

    /** 遮罩 / 面板自身可见时的触摸：跟踪多指并响应三指下滑 */
    private fun handleOverlayViewTouch(ev: MotionEvent): Boolean {
        val fired = trackMultiTouch(ev)
        if (fired) {
            fireSwipe()
            return true
        }
        return false
    }

    /** 三指下滑成立后的统一处理：复位游戏按键 → 遮罩/面板状态切换或唤起面板 */
    fun fireSwipe() {
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

    // ===================== 视觉：三指下滑箭头动画 =====================

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
            for (i in 0 until 3) {
                // 三个箭头依次向下流动
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