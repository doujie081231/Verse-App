package com.verse.explorer.core.mod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 模组元数据解析与冲突扫描测试。
 *
 * 覆盖安装期就该发现、但目前只能等游戏崩了才知道的两类问题：
 * 同一 modid 多版本共存、缺少必需前置。
 */
class ModConflictScannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun modsDir(): File = tmp.newFolder("mods")

    private fun makeJar(dir: File, name: String, entries: Map<String, String>): File {
        val jar = File(dir, name)
        ZipOutputStream(jar.outputStream()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return jar
    }

    private fun fabricJar(dir: File, fileName: String, id: String, version: String, depends: String = "{}"): File =
        makeJar(
            dir, fileName,
            mapOf(
                "fabric.mod.json" to """{"id":"$id","version":"$version","name":"$id 名称","depends":$depends}"""
            )
        )

    @Test
    fun readJar_parsesFabricMetadata() {
        val dir = modsDir()
        val jar = fabricJar(dir, "sodium-1.0.jar", "sodium", "1.0.2", """{"minecraft":">=1.20","fabricloader":">=0.15"}""")

        val mod = ModConflictScanner.readJar(jar)

        assertNotNull(mod)
        assertEquals("sodium", mod!!.modId)
        assertEquals("1.0.2", mod.version)
        assertEquals("fabric", mod.loader)
        assertEquals(2, mod.dependencies.size)
        assertTrue(mod.dependencies.all { it.mandatory })
    }

    @Test
    fun readJar_parsesForgeToml() {
        val dir = modsDir()
        val jar = makeJar(
            dir, "jei-forge.jar",
            mapOf(
                "META-INF/mods.toml" to """
                    modLoader="javafml"
                    loaderVersion="[47,)"

                    [[mods]]
                    modId="jei"
                    version="15.2.0"
                    displayName="Just Enough Items"

                    [[dependencies.jei]]
                    modId="minecraft"
                    mandatory=true
                    versionRange="[1.20,1.21)"
                """.trimIndent()
            )
        )

        val mod = ModConflictScanner.readJar(jar)

        assertNotNull(mod)
        assertEquals("jei", mod!!.modId)
        assertEquals("15.2.0", mod.version)
        assertEquals("Just Enough Items", mod.name)
        assertEquals("forge", mod.loader)
        assertEquals(1, mod.dependencies.size)
        assertEquals("minecraft", mod.dependencies[0].modId)
    }

    @Test
    fun scan_flagsDuplicateModId() {
        val dir = modsDir()
        fabricJar(dir, "sodium-1.0.jar", "sodium", "1.0")
        fabricJar(dir, "sodium-2.0.jar", "sodium", "2.0")

        val result = ModConflictScanner.scan(dir)

        val dup = result.conflicts.filter { it.kind == ModConflictScanner.Conflict.Kind.DUPLICATE_MOD_ID }
        assertEquals(1, dup.size)
        assertEquals("sodium", dup[0].modId)
        assertEquals(2, dup[0].files.size)
    }

    @Test
    fun scan_flagsMissingDependency_butIgnoresPlatformIds() {
        val dir = modsDir()
        // 依赖 minecraft（平台 id，不算缺）+ indium（真实缺失）
        fabricJar(
            dir, "sodium-extra.jar", "sodium-extra", "1.0",
            """{"minecraft":"*","indium":">=1.0"}"""
        )

        val result = ModConflictScanner.scan(dir)

        val missing = result.conflicts.filter { it.kind == ModConflictScanner.Conflict.Kind.MISSING_DEPENDENCY }
        assertEquals(1, missing.size)
        assertEquals("indium", missing[0].modId)
    }

    @Test
    fun scan_doesNotFlagPresentDependency() {
        val dir = modsDir()
        fabricJar(dir, "base.jar", "base-lib", "1.0")
        fabricJar(dir, "addon.jar", "addon", "1.0", """{"base-lib":">=1.0"}""")

        val result = ModConflictScanner.scan(dir)

        assertFalse(result.hasConflict)
        assertEquals(2, result.mods.size)
    }

    @Test
    fun scan_reportsUnreadableJar() {
        val dir = modsDir()
        makeJar(dir, "not-a-mod.jar", mapOf("readme.txt" to "hello"))

        val result = ModConflictScanner.scan(dir)

        assertTrue(result.conflicts.any { it.kind == ModConflictScanner.Conflict.Kind.UNREADABLE })
    }

    @Test
    fun scan_skipsDisabledJars() {
        val dir = modsDir()
        fabricJar(dir, "sodium.jar.disabled", "sodium", "1.0")

        val result = ModConflictScanner.scan(dir)

        assertTrue(result.mods.isEmpty())
        assertFalse(result.hasConflict)
    }

    @Test
    fun scan_emptyDirectory_isClean() {
        val result = ModConflictScanner.scan(modsDir())
        assertTrue(result.mods.isEmpty())
        assertFalse(result.hasConflict)
    }
}
