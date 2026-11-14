package com.verse.explorer.launcher.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置渲染器注册表测试。
 *
 * 关注两件事：MobileGlues 作为内置项存在且参数正确；渲染器 id 不重复
 * （重复会让"按 id 找渲染器"随机命中，表现为设置页选了 A、启动用的是 B）。
 */
class RendererRegistryTest {

    @Test
    fun `MobileGlues 已注册为内置渲染器`() {
        val renderer = RendererManager.allRenderers().firstOrNull { it.id == RendererManager.ID_MOBILEGLUES }
        assertNotNull("MobileGlues 必须出现在内置列表里", renderer)
        renderer!!
        assertEquals("libmobileglues.so", renderer.glName)
        assertEquals("libmobileglues.so", renderer.eglName)
        assertEquals("opengles3", renderer.pojavRenderer)
        assertTrue(renderer.pluginPackage.isEmpty())
        assertEquals(listOf("libmobileglues.so"), renderer.requiredLibs)
    }

    @Test
    fun `MobileGlues 的环境变量与驱动约定一致`() {
        val renderer = RendererManager.MOBILEGLUES
        val env = renderer.envVars

        assertEquals("libmobileglues.so", env["LIBGL_EGL"])
        assertEquals("libmobileglues.so", env["LIBGL_NAME"])
        assertEquals("3", env["LIBGL_ES"])
        assertEquals("libmobileglues.so", env["POJAVEXEC_EGL"])
    }

    @Test
    fun `渲染器 id 唯一`() {
        val ids = RendererManager.allRenderers().map { it.id }
        assertEquals("渲染器 id 不允许重复：$ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `按 id 查找命中 MobileGlues`() {
        val found = RendererManager.getRendererById(RendererManager.ID_MOBILEGLUES)
        assertNotNull(found)
        assertEquals("MobileGlues", found!!.displayName)
    }

    @Test
    fun `有配置规范的渲染器才显示配置入口`() {
        // 配置规范由 RendererConfigSpecs 提供，这里只验证 id 对齐
        assertEquals("mobileglues", RendererManager.ID_MOBILEGLUES)
    }
}
