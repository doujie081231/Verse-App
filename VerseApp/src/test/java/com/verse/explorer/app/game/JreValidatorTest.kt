package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * JreValidator 的目录校验覆盖：用临时目录构造各缺失分支，
 * 另造最小 zip 验证 rt.jar 的条目可读性判定。
 */
class JreValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val nativeLibs = listOf("libjli.so", "libjvm.so", "libawt.so", "libfontmanager.so")

    private fun createCompleteJre(name: String = "jre"): File {
        val home = tempFolder.newFolder(name)
        File(home, "bin").mkdirs()
        File(home, "bin/java").createNewFile()
        val lib = File(home, "lib")
        lib.mkdirs()
        nativeLibs.forEach { File(lib, it).writeBytes(byteArrayOf(1)) }
        File(lib, "modules").writeBytes(ByteArray(64))
        return home
    }

    @Test
    fun completeJreIsValid() {
        assertNull(JreValidator.jreInvalidReason(createCompleteJre().absolutePath))
    }

    @Test
    fun missingDir() {
        val absent = File(tempFolder.root, "no-such-jre")
        assertEquals("目录不存在", JreValidator.jreInvalidReason(absent.absolutePath))
    }

    @Test
    fun missingJavaBinary() {
        val home = createCompleteJre()
        File(home, "bin/java").delete()
        assertEquals("缺少 bin/java", JreValidator.jreInvalidReason(home.absolutePath))
    }

    @Test
    fun missingLibjli() {
        val home = createCompleteJre()
        File(File(home, "lib"), "libjli.so").delete()
        assertEquals("缺少 libjli.so", JreValidator.jreInvalidReason(home.absolutePath))
    }

    @Test
    fun missingModules() {
        val home = createCompleteJre()
        File(File(home, "lib"), "modules").delete()
        assertEquals("核心运行库(modules/rt.jar)缺失或损坏", JreValidator.jreInvalidReason(home.absolutePath))
    }

    @Test
    fun emptyModulesFileIsInvalid() {
        val home = createCompleteJre()
        File(File(home, "lib"), "modules").writeBytes(ByteArray(0))
        assertEquals("核心运行库(modules/rt.jar)缺失或损坏", JreValidator.jreInvalidReason(home.absolutePath))
    }

    @Test
    fun rtJarWithObjectClassIsValid() {
        val home = tempFolder.newFolder("jre8")
        File(home, "bin").mkdirs()
        File(home, "bin/java").createNewFile()
        val lib = File(home, "lib")
        lib.mkdirs()
        nativeLibs.forEach { File(lib, it).writeBytes(byteArrayOf(1)) }
        ZipOutputStream(File(lib, "rt.jar").outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("java/lang/Object.class"))
            zos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
            zos.closeEntry()
        }
        assertNull(JreValidator.jreInvalidReason(home.absolutePath))
    }

    @Test
    fun rtJarWithoutObjectClassIsInvalid() {
        val home = tempFolder.newFolder("jre8-broken")
        File(home, "bin").mkdirs()
        File(home, "bin/java").createNewFile()
        val lib = File(home, "lib")
        lib.mkdirs()
        nativeLibs.forEach { File(lib, it).writeBytes(byteArrayOf(1)) }
        ZipOutputStream(File(lib, "rt.jar").outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zos.write("Manifest-Version: 1.0\n".toByteArray())
            zos.closeEntry()
        }
        assertEquals("核心运行库(modules/rt.jar)缺失或损坏", JreValidator.jreInvalidReason(home.absolutePath))
    }

    @Test
    fun findFileInTreeSearchesRecursively() {
        val root = tempFolder.newFolder("tree")
        val deep = File(root, "a/b/c").apply { mkdirs() }
        val target = File(deep, "modules").apply { writeBytes(ByteArray(8)) }
        assertEquals(target, JreValidator.findFileInTree(root, "modules"))
        assertNull(JreValidator.findFileInTree(root, "absent"))
    }

    @Test
    fun isRtJarUsableOnMissingFile() {
        assertFalse(JreValidator.isRtJarUsable(tempFolder.newFolder("empty-lib")))
    }

    @Test
    fun isRtJarUsableOnRealJar() {
        val lib = tempFolder.newFolder("lib-with-jar")
        ZipOutputStream(File(lib, "rt.jar").outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("java/lang/Object.class"))
            zos.write(byteArrayOf(1))
            zos.closeEntry()
        }
        assertTrue(JreValidator.isRtJarUsable(lib))
    }
}
