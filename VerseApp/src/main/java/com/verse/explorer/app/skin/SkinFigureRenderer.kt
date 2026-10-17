package com.verse.explorer.app.skin

import android.graphics.Bitmap
import android.opengl.GLUtils
import android.opengl.GLU
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.opengles.GL10

/**
 * 皮肤全身正视图像渲染器：用 EGL Pbuffer 离屏渲染（与账户详情页 SkinViewer
 * 相同的 GL10 固定管线模型与相机参数），把一张皮肤渲染成正面全身图，供
 * 皮肤/披风选择卡片做预览。结果按 (纹理标识 + 模型 + 披风) 缓存在内存。
 */
object SkinFigureRenderer {

    /** 缓存上限（账户皮肤库通常几张到十几张） */
    private const val CACHE_LIMIT = 24

    private val cache =LinkedHashMap<String, Bitmap>()

    private const val OUT_W = 180
    private const val OUT_H = 252

    /**
     * 渲染一张皮肤的正面全身图。
     *
     * @param skin 完整皮肤位图（内部做旧格式归一化与 slim 判定）
     * @param cape 披风位图（可为 null，不渲染披风）
     * @param key  缓存键（纹理标识，如文件路径或 URL）；命中直接返回
     */
    @Synchronized
    fun renderFigure(skin: Bitmap, cape: Bitmap?, key: String): Bitmap? {
        cache[key]?.let { return it }
        val figure = renderOffscreen(skin, cape) ?: return null
        if (cache.size >= CACHE_LIMIT) {
            val it = cache.entries.iterator()
            it.next(); it.remove()
        }
        cache[key] = figure
        return figure
    }

    /** 皮肤变化后清除缓存（上传/删除皮肤、披风切换时调用） */
    @Synchronized
    fun clearCache() {
        cache.clear()
    }

    /** EGL Pbuffer 离屏渲染主体；失败返回 null（调用方回退到头部裁剪预览） */
    private fun renderOffscreen(skinBitmap: Bitmap, capeBitmap: Bitmap?): Bitmap? {
        val normalized = try {
            NormalizedSkin(skinBitmap)
        } catch (_: Exception) {
            return null
        }
        val slim = normalized.isSlim()
        val skinTex = if (normalized.isOldFormat()) normalized.getNormalizedTexture() else normalized.getOriginalTexture()

        val egl = EGLContext.getEGL() as EGL10
        val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        if (display == EGL10.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!egl.eglInitialize(display, version)) return null

        val attribs = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES_BIT
            EGL10.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!egl.eglChooseConfig(display, attribs, configs, 1, numConfigs) || numConfigs[0] == 0) {
            return null
        }
        val pbufferAttribs = intArrayOf(
            EGL10.EGL_WIDTH, OUT_W,
            EGL10.EGL_HEIGHT, OUT_H,
            EGL10.EGL_NONE
        )
        val surface = egl.eglCreatePbufferSurface(display, configs[0], pbufferAttribs)
        if (surface == EGL10.EGL_NO_SURFACE) {
            return null
        }
        // ES1 上下文（EGL_CONTEXT_CLIENT_VERSION=1）
        val contextAttribs = intArrayOf(0x3098, 1, EGL10.EGL_NONE)
        val context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, contextAttribs)
        if (context == EGL10.EGL_NO_CONTEXT) {
            egl.eglDestroySurface(display, surface)
            return null
        }
        if (!egl.eglMakeCurrent(display, surface, surface, context)) {
            egl.eglDestroyContext(display, context)
            egl.eglDestroySurface(display, surface)
            return null
        }

        val figure: Bitmap? = try {
            val gl = egl.eglGetCurrentContext().gl as GL10
            draw(gl, skinTex, capeBitmap, slim)
            readPixels(gl, OUT_W, OUT_H)
        } catch (_: Throwable) {
            null
        } finally {
            // display 为进程共享（GLSurfaceView 同用），只销毁自建的 surface/context，不终止 display
            egl.eglMakeCurrent(
                display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT
            )
            egl.eglDestroyContext(display, context)
            egl.eglDestroySurface(display, surface)
        }
        return figure
    }

    /** 绘制流程与 SkinRenderer 的 surface 回调一致（清除色透明，输出带 alpha） */
    private fun draw(gl: GL10, skin: Bitmap, cape: Bitmap?, slim: Boolean) {
        val textures = IntArray(2)
        gl.glGenTextures(2, textures, 0)
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0])
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST.toFloat())
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST.toFloat())
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, skin, 0)
        if (cape != null) {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[1])
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST.toFloat())
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST.toFloat())
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, cape, 0)
        }

        gl.glEnable(GL10.GL_BLEND)
        gl.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA)
        gl.glCullFace(GL10.GL_FRONT)
        gl.glShadeModel(GL10.GL_SMOOTH)
        gl.glEnable(GL10.GL_DEPTH_TEST)
        gl.glEnable(GL10.GL_ALPHA_TEST)
        gl.glAlphaFunc(GL10.GL_GREATER, 0.1f)
        gl.glDepthFunc(GL10.GL_LEQUAL)
        gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST)
        gl.glClearDepthf(1.0f)

        gl.glViewport(0, 0, OUT_W, OUT_H)
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glLoadIdentity()
        GLU.gluPerspective(gl, 45.0f, OUT_W.toFloat() / OUT_H, 0.5f, 1500f)
        gl.glMatrixMode(GL10.GL_MODELVIEW)

        gl.glClearColor(0f, 0f, 0f, 0f)
        gl.glClear(16640)
        gl.glEnable(GL10.GL_TEXTURE_2D)
        gl.glLoadIdentity()
        gl.glColor4f(1f, 1f, 1f, 1f)
        gl.glTranslatef(0f, 0f, -60f)
        val model = SkinModel()
        gl.glPushMatrix()
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0])
        model.drawBodyModel(gl, slim)
        if (cape != null) {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[1])
            model.drawCapeModel(gl)
        }
        gl.glPopMatrix()
        gl.glLoadIdentity()
    }

    /** 读取帧缓冲为带透明通道的位图（GL 坐标自下而上，需垂直翻转） */
    private fun readPixels(gl: GL10, width: Int, height: Int): Bitmap? {
        val buf = java.nio.ByteBuffer.allocateDirect(width * height * 4)
        buf.order(java.nio.ByteOrder.nativeOrder())
        gl.glReadPixels(0, 0, width, height, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, buf)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = (y * width + x) * 4
                val r = buf.get(i).toInt() and 0xFF
                val g = buf.get(i + 1).toInt() and 0xFF
                val b = buf.get(i + 2).toInt() and 0xFF
                val a = buf.get(i + 3).toInt() and 0xFF
                // 输出按预乘 alpha 语义还原（GL_ONE_MINUS_SRC_ALPHA 混合写入的是非预乘颜色）
                pixels[(height - 1 - y) * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
