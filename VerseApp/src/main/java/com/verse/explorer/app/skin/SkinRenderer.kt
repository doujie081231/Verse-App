package com.verse.explorer.app.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLU
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.widget.Toast

import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SkinRenderer(private val context: Context) : GLSurfaceView.Renderer {

    val skinModel = SkinModel()
    private var updateBitmapSkin = false
    private var slim = false
    private var skin: Bitmap? = null
    private var cape: Bitmap? = null
    private var modelTextureData: IntArray? = null

    init {
        // 默认动作：呼吸站姿（对齐 versepc2 默认 idle = IdleAnimation）
        this.skinModel.setAnimation(SkinModel.SkinAnimationType.BREATHING)
        this.updateBitmapSkin = false
        this.slim = false
        this.skin = loadDefaultSkin()
        this.cape = null
    }

    private fun loadDefaultSkin(): Bitmap? {
        try {
            val stream = context.assets.open("skins/alex.png")
            return stream.use { BitmapFactory.decodeStream(it) }
        } catch (ignore: Exception) {
            // fall back to steve.png when alex.png cannot be read
        }
        return try {
            val stream = context.assets.open("skins/steve.png")
            stream.use { BitmapFactory.decodeStream(it) }
        } catch (ignore: Exception) {
            null
        }
    }

    override fun onSurfaceCreated(gl10: GL10, eglConfig: EGLConfig?) {
        this.modelTextureData = loadTexture(this.skin!!, this.cape)
        gl10.glEnable(GL10.GL_BLEND)
        gl10.glCullFace(GL10.GL_FRONT)
        gl10.glShadeModel(GL10.GL_SMOOTH)
        gl10.glEnable(GL10.GL_RGBA)
        gl10.glEnable(GL10.GL_DEPTH_TEST)
        gl10.glEnable(GL10.GL_ALPHA_TEST)
        gl10.glAlphaFunc(GL10.GL_GREATER, 0.1f)
        gl10.glDepthFunc(GL10.GL_LEQUAL)
        gl10.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST)
        gl10.glClearDepthf(1.0f)
    }

    override fun onSurfaceChanged(gl10: GL10, i: Int, i1: Int) {
        gl10.glViewport(0, 0, i, i1)
        gl10.glMatrixMode(GL10.GL_PROJECTION)
        gl10.glLoadIdentity()
        // d2r(22.5f) == 3.1415927f * 22.5f / 180.0f (from ConvertUtils.d2r)
        val d2r = 3.1415927f * 22.5f / 180.0f
        val i2 = i1 / 2.0f / Math.tan(d2r.toDouble()).toFloat()
        GLU.gluPerspective(gl10, 45.0f, i.toFloat() / i1, 0.5f, Math.max(1500.0f, i2))
        gl10.glMatrixMode(GL10.GL_MODELVIEW)
        gl10.glLoadIdentity()
        GLU.gluLookAt(gl10, i / 2.0f, i1 / 2.0f, i2, i / 2.0f, i1 / 2.0f, 0.0f, 0.0f, 1.0f, 0.0f)
        gl10.glEnable(GL10.GL_COLOR_BUFFER_BIT)
    }

    override fun onDrawFrame(gl10: GL10) {
        if (this.updateBitmapSkin) {
            this.modelTextureData = loadTexture(this.skin!!, this.cape)
            this.updateBitmapSkin = false
        }
        gl10.glClear(16640)
        gl10.glEnable(GL10.GL_TEXTURE_2D)
        gl10.glLoadIdentity()
        gl10.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
        gl10.glTranslatef(0.0f, 0.0f, -60.0f)
        gl10.glPushMatrix()
        gl10.glBindTexture(GL10.GL_TEXTURE_2D, modelTextureData!![0])
        this.skinModel.drawBodyModel(gl10, slim)
        if (cape != null /*&& cape.getWidth() == 64 && cape.getHeight() == 32*/) {
            gl10.glBindTexture(GL10.GL_TEXTURE_2D, modelTextureData!![1])
            this.skinModel.drawCapeModel(gl10)
        }
        gl10.glPopMatrix()
        gl10.glLoadIdentity()
    }

    fun updateTexture(skin: Bitmap?, cape: Bitmap?) {
        postToUiThread(Runnable {
            try {
                val normalizedSkin = NormalizedSkin(skin!!)
                this.slim = normalizedSkin.isSlim()
                this.skin = if (normalizedSkin.isOldFormat()) normalizedSkin.getNormalizedTexture() else normalizedSkin.getOriginalTexture()
                this.cape = cape
                this.updateBitmapSkin = true
            } catch (e: InvalidSkinException) {
                e.printStackTrace()
                Toast.makeText(context, "Skin Renderer: " + e, Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Update the skin texture with an explicitly specified model.
     * The `slim` parameter overrides the auto-detection from the skin image.
     *
     * @param slim true for the slim (Alex) model, false for the classic (Steve) model
     */
    fun updateTexture(skin: Bitmap?, cape: Bitmap?, slim: Boolean) {
        postToUiThread(Runnable {
            try {
                val normalizedSkin = NormalizedSkin(skin!!)
                this.slim = slim
                this.skin = if (normalizedSkin.isOldFormat()) normalizedSkin.getNormalizedTexture() else normalizedSkin.getOriginalTexture()
                this.cape = cape
                this.updateBitmapSkin = true
            } catch (e: InvalidSkinException) {
                e.printStackTrace()
                Toast.makeText(context, "Skin Renderer: " + e, Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun setTexture(skin: Bitmap?, cape: Bitmap?) {
        updateTexture(skin, cape)
    }

    companion object {

        @JvmStatic
        fun loadTexture(skin: Bitmap, cape: Bitmap?): IntArray {
            val textures = IntArray(2)
            GLES20.glGenTextures(2, textures, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST.toFloat())
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, skin, 0)
            if (cape != null) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST.toFloat())
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, cape, 0)
            }
            if (textures[0] == 0 || (cape != null && textures[1] == 0)) {
                throw RuntimeException("Error loading texture.")
            }
            return textures
        }
    }
}

/**
 * 等价于原 Schedulers.androidUIThread().execute(...)：把任务派发到主线程执行。
 */
private fun postToUiThread(r: Runnable) {
    Handler(Looper.getMainLooper()).post(r)
}