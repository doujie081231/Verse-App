package com.mio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置渲染器表测试。
 *
 * MobileGlues 走的是内置分支（path 为空），`valueOf` 必须解析到它本身，
 * 不能回落到默认的 GLES —— 否则界面上选了 MobileGlues，实际用系统 GLES 跑，
 * 表现是"选了没效果"且日志里看不到任何渲染器报错。
 */
class RendererTest {

    @Test
    fun `mobileglues 解析为内置渲染器`() {
        val renderer = Renderer.valueOf(Renderer.ID_MOBILEGLUES)

        assertEquals("mobileglues", renderer.id)
        assertEquals("libmobileglues.so", renderer.glName)
        assertEquals("libmobileglues.so", renderer.eglName)
        assertTrue("内置渲染器不应有插件路径", renderer.path.isEmpty())
    }

    @Test
    fun `mobileglues 的环境声明与驱动约定一致`() {
        val env = Renderer.valueOf(Renderer.ID_MOBILEGLUES).pojavEnv.toSet()

        assertTrue(env.contains("LIBGL_ES=3"))
        assertTrue(env.contains("LIBGL_EGL=libmobileglues.so"))
        assertTrue(env.contains("LIBGL_NAME=libmobileglues.so"))
    }

    @Test
    fun `未知 id 仍回落到 GLES`() {
        assertEquals("opengles3", Renderer.valueOf("definitely-not-a-renderer").id)
    }

    @Test
    fun `内置渲染器列表完整且 id 唯一`() {
        val ids = Renderer.getRendererList().map { it.id }

        assertEquals("id 不允许重复：$ids", ids.size, ids.toSet().size)
        assertTrue(
            "缺少内置渲染器：$ids",
            ids.containsAll(
                listOf("opengles3", "gl4es", "nggl4es", "vgpu", "virgl", "zink", "freedreno", "mobileglues")
            )
        )
    }
}
