package com.verse.explorer.app.skin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent

class SkinViewer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var density = 0f
    private var previousX = 0f
    private var previousY = 0f
    private var renderer: SkinRenderer? = null
    private var priPointerId = 0
    private var secPointerId = 0
    private var initDistance = 0.0
    private var initScale = 0f

    init {
        init()
    }

    /** 按需渲染节拍器：空闲低速、交互/自动旋转时提速，避免连续渲染拖垮低端设备 */
    private val frameTicker = Handler(Looper.getMainLooper())
    private var tickerRunning = false
    private var lastTouchAt = 0L
    private var autoRotateOn = false

    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (!tickerRunning) return
            requestRender()
            frameTicker.postDelayed(this, currentIntervalMs())
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
            lastTouchAt = SystemClock.elapsedRealtime()
            if (event.pointerCount == 1) {
                val x = event.x
                val y = event.y
                if (event.action == MotionEvent.ACTION_MOVE && this.renderer != null) {
                    this.renderer!!.skinModel.setRotateStep((x - this.previousX) / this.density, (y - this.previousY) / this.density)
                }
                this.previousX = x
                this.previousY = y
            }
            if (event.pointerCount == 2) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        priPointerId = event.getPointerId(event.actionIndex)
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        secPointerId = event.getPointerId(event.actionIndex)
                        val deltaX = event.getX(event.findPointerIndex(priPointerId)) - event.getX(event.findPointerIndex(secPointerId))
                        val deltaY = event.getY(event.findPointerIndex(priPointerId)) - event.getY(event.findPointerIndex(secPointerId))
                        initDistance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())
                        initScale = renderer!!.skinModel.scale
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dX = event.getX(event.findPointerIndex(priPointerId)) - event.getX(event.findPointerIndex(secPointerId))
                        val dY = event.getY(event.findPointerIndex(priPointerId)) - event.getY(event.findPointerIndex(secPointerId))
                        val dist = Math.sqrt((dX * dX + dY * dY).toDouble())
                        val delta = dist - initDistance
                        if (initScale + (delta / (1 * Math.sqrt((getWidth() * getWidth() + getHeight() * getHeight()).toDouble()))) <= 2
                                && initScale + (delta / (1 * Math.sqrt((getWidth() * getWidth() + getHeight() * getHeight()).toDouble()))) >= 0.7) {
                            val scale = (initScale + (delta / (1 * Math.sqrt((getWidth() * getWidth() + getHeight() * getHeight()).toDouble())))).toFloat()
                            renderer!!.skinModel.scale = scale
                        }
                    }
                }
            }
            if (event.action == MotionEvent.ACTION_DOWN ||
                event.action == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
            ) {
                requestRender()
            }
        } catch (ignore: Throwable) {
        }
        return true
    }

    private fun init() {
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        getHolder().setFormat(PixelFormat.RGBA_8888)
        getHolder().setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
    }

    fun setRenderer(renderer: SkinRenderer, density: Float) {
        this.renderer = renderer
        this.density = density
        super.setRenderer(renderer)
        // 按需渲染 + 节拍器：替代连续渲染（连续模式在软渲染设备上会占满 CPU）
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
        startTicker()
    }

    /** 更新自动旋转开关（节拍器随之提速/降速） */
    fun setAutoRotate(on: Boolean) {
        this.autoRotateOn = on
    }

    private fun currentIntervalMs(): Long =
        if (autoRotateOn || SystemClock.elapsedRealtime() - lastTouchAt < TOUCH_GRACE_MS) {
            MOTION_INTERVAL_MS
        } else {
            IDLE_INTERVAL_MS
        }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        frameTicker.post(tickerRunnable)
    }

    private fun stopTicker() {
        tickerRunning = false
        frameTicker.removeCallbacks(tickerRunnable)
    }

    override fun onDetachedFromWindow() {
        stopTicker()
        super.onDetachedFromWindow()
    }

    companion object {
        /** 空闲（呼吸站姿）帧间隔：15fps */
        private const val IDLE_INTERVAL_MS = 66L

        /** 交互 / 自动旋转帧间隔：30fps */
        private const val MOTION_INTERVAL_MS = 33L

        /** 触摸后保持高帧率的时长 */
        private const val TOUCH_GRACE_MS = 400L
    }
}