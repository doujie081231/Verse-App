package com.verse.explorer.launcher.renderer.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * SimpleFPEWrapper 兼容层的模组避让判定。
 *
 * ANGELICA / ACTINIUM 自带固定管线实现，和兼容层抢同一批 GL 入口，
 * 同时挂载会互相打架。这层判定只读文件名，所以能在 JVM 上直接测。
 */
class SfpewModGuardTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun modsDir(vararg names: String): File {
        val dir = tempFolder.newFolder("mods")
        names.forEach { File(dir, it).writeText("") }
        return dir
    }

    @Test
    fun `没有模组时不拦截`() {
        assertFalse(RendererConfigSupport.hasFpeEmulatorMods(modsDir()))
    }

    @Test
    fun `安装 ANGELICA 时拦截`() {
        assertTrue(RendererConfigSupport.hasFpeEmulatorMods(modsDir("angelica-1.0.0.jar")))
    }

    @Test
    fun `安装 ACTINIUM 时拦截`() {
        assertTrue(RendererConfigSupport.hasFpeEmulatorMods(modsDir("actinium-2.1.jar")))
    }

    @Test
    fun `大小写与前后缀不影响判定`() {
        assertTrue(RendererConfigSupport.hasFpeEmulatorMods(modsDir("Angelica-Release-1.2.jar")))
    }

    @Test
    fun `普通模组不拦截`() {
        assertFalse(
            RendererConfigSupport.hasFpeEmulatorMods(
                modsDir("sodium-fabric-0.6.9.jar", "iris-1.8.0.jar", "fabric-api-0.100.jar")
            )
        )
    }

    @Test
    fun `非 jar 文件不参与判定`() {
        assertFalse(RendererConfigSupport.hasFpeEmulatorMods(modsDir("angelica-1.0.0.jar.disabled")))
    }
}
