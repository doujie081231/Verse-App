package com.verse.explorer.app.game

import com.verse.explorer.launcher.renderer.RendererManager
import com.verse.explorer.launcher.renderer.config.RendererConfigSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染器兼容策略测试：哪些渲染器算"转译层"（需要拦 Sodium 类模组），
 * 以及 MobileGlues 的默认配置必须与既有行为一致。
 */
class RendererCompatPolicyTest {

    @Test
    fun `gl4es 与 Krypton 属于转译层`() {
        assertTrue(RendererCompatCheck.isTranslationLayer(RendererManager.GL4ES))
        assertTrue(RendererCompatCheck.isTranslationLayer(RendererManager.NGGL4ES))
    }

    @Test
    fun `Mesa 系与 MobileGlues 不算转译层`() {
        assertFalse(RendererCompatCheck.isTranslationLayer(RendererManager.ZINK))
        assertFalse(RendererCompatCheck.isTranslationLayer(RendererManager.VIRGL))
        assertFalse(
            "MobileGlues 对外声明 OpenGL 4.0，能跑 Sodium，不应被拦截",
            RendererCompatCheck.isTranslationLayer(RendererManager.MOBILEGLUES)
        )
    }

    @Test
    fun `MobileGlues 有配置规范且默认沿用旧行为`() {
        val spec = RendererConfigSpecs.of(RendererManager.ID_MOBILEGLUES)
        assertNotNull(spec)
        assertEquals(3, spec!!.field("enableNoError")!!.defaultValue)
        assertEquals(true, spec.field("sfpewLayer")!!.defaultValue)
        assertTrue(spec.fields.isNotEmpty())
    }

    @Test
    fun `其它内置渲染器没有配置规范`() {
        assertNull(RendererConfigSpecs.of(RendererManager.ID_GL4ES))
        assertNull(RendererConfigSpecs.of(RendererManager.ID_ZINK))
    }
}
