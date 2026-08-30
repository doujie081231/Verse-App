package com.verse.explorer.app.skin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        try {
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
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
    }
}