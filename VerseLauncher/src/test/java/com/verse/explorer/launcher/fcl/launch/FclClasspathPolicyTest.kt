package com.verse.explorer.launcher.fcl.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * DefaultLauncher 类路径替换测试。
 *
 * 官方 LWJGL/JNA jar 会加载 glibc native 或与 patched native 版本不匹配，
 * 必须在启动前换成本地套装；本地 jar 必须排在最前，否则官方 jar 先被加载。
 */
class FclClasspathPolicyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun localJar(name: String): File {
        val file = File(tempFolder.root, "lwjgl/$name")
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(16))
        return file
    }

    @Test
    fun replaceJna_swapsOfficialJar() {
        val local = localJar("jna.jar")
        val classpath = linkedSetOf(
            "/libs/net/java/dev/jna/jna/5.12.1/jna-5.12.1.jar",
            "/libs/guava.jar"
        )
        FclClasspathPolicy.replaceJna(classpath, local)
        assertEquals(
            listOf(local.absolutePath, "/libs/guava.jar"),
            classpath.toList()
        )
    }

    @Test
    fun replaceJna_missingLocalKeepsOfficial() {
        val classpath = linkedSetOf("/libs/net/java/dev/jna/jna/5.12.1/jna-5.12.1.jar")
        FclClasspathPolicy.replaceJna(
            classpath,
            File(tempFolder.root, "missing.jar")
        )
        assertEquals(
            listOf("/libs/net/java/dev/jna/jna/5.12.1/jna-5.12.1.jar"),
            classpath.toList()
        )
    }

    @Test
    fun replaceLwjgl_prependsLocalAndDropsOfficial() {
        val glfw = localJar("lwjgl-glfw.jar")
        val core = localJar("lwjgl.jar")
        val classpath = linkedSetOf(
            "/libs/org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar",
            "/libs/jinput/jinput-2.0.9.jar",
            "/libs/lwjgl_util.jar",
            "/libs/gson.jar"
        )
        FclClasspathPolicy.replaceLwjgl(
            classpath,
            listOf(glfw, core),
            modernModuleLoader = false
        )
        assertEquals(
            listOf(glfw.absolutePath, core.absolutePath, "/libs/gson.jar"),
            classpath.toList()
        )
    }

    @Test
    fun replaceLwjgl_modernModuleLoader_dropsLwjglx() {
        val lwjglx = localJar("lwjgl-lwjglx.jar")
        val core = localJar("lwjgl.jar")
        val classpath = linkedSetOf("/libs/gson.jar")
        FclClasspathPolicy.replaceLwjgl(
            classpath,
            listOf(lwjglx, core),
            modernModuleLoader = true
        )
        assertFalse(classpath.contains(lwjglx.absolutePath))
        assertTrue(classpath.contains(core.absolutePath))
    }

    @Test
    fun replaceLwjgl_legacy_keepsLwjglx() {
        val lwjglx = localJar("lwjgl-lwjglx.jar")
        val classpath = linkedSetOf("/libs/gson.jar")
        FclClasspathPolicy.replaceLwjgl(
            classpath,
            listOf(lwjglx),
            modernModuleLoader = false
        )
        assertTrue(classpath.contains(lwjglx.absolutePath))
    }

    @Test
    fun replaceLwjgl_noLocalJars_keepsClasspath() {
        val classpath = linkedSetOf("/libs/org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar")
        FclClasspathPolicy.replaceLwjgl(
            classpath,
            emptyList(),
            modernModuleLoader = true
        )
        assertEquals(
            listOf("/libs/org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1.jar"),
            classpath.toList()
        )
    }
}
