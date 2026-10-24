package com.verse.explorer.app.game

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本 JSON 的「基础版本 / 已装加载器」识别测试。
 *
 * 这段逻辑决定给已装实例加加载器时的父版本：判断错了会拼出
 * `1.20.1-forge-47.2.0-forge-47.3.0` 这种层层套娃的版本目录。
 */
class LoaderTypeResolverTest {

    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    @Test
    fun vanilla_noLoader_andSelfIsBase() {
        val info = LoaderTypeResolver.resolve(
            json("""{"id":"1.20.1","mainClass":"net.minecraft.client.main.Main"}"""),
            "1.20.1"
        )
        assertEquals("1.20.1", info.baseVersionId)
        assertNull(info.loaderType)
    }

    @Test
    fun forge_inheritsFromIsBase() {
        val info = LoaderTypeResolver.resolve(
            json(
                """{"id":"1.20.1-forge-47.2.0","inheritsFrom":"1.20.1",
                   "mainClass":"cpw.mods.bootstraplauncher.BootstrapLauncher"}"""
            ),
            "1.20.1-forge-47.2.0"
        )
        assertEquals("1.20.1", info.baseVersionId)
        assertEquals(LoaderTypeResolver.FORGE, info.loaderType)
    }

    @Test
    fun neoforge_notMisdetectedAsForge() {
        // neoforge 主类里同样含 "forge"，必须先匹配 neoforge
        val info = LoaderTypeResolver.resolve(
            json(
                """{"id":"1.21.1-neoforge-21.1.0","inheritsFrom":"1.21.1",
                   "mainClass":"cpw.mods.bootstraplauncher.BootstrapLauncher"}"""
            ),
            "1.21.1-neoforge-21.1.0"
        )
        assertEquals(LoaderTypeResolver.NEOFORGE, info.loaderType)
        assertEquals("1.21.1", info.baseVersionId)
    }

    @Test
    fun fabric_detectedByMainClass() {
        val info = LoaderTypeResolver.resolve(
            json(
                """{"id":"1.20.1-fabric-0.15.0","inheritsFrom":"1.20.1",
                   "mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient"}"""
            ),
            "1.20.1-fabric-0.15.0"
        )
        assertEquals(LoaderTypeResolver.FABRIC, info.loaderType)
    }

    @Test
    fun quilt_detectedAndInstallable() {
        val info = LoaderTypeResolver.resolve(null, "1.20.1-quilt-0.24.0")
        assertEquals(LoaderTypeResolver.QUILT, info.loaderType)
        assertTrue(info.loaderType!! in LoaderTypeResolver.INSTALLABLE)
    }

    @Test
    fun optifine_detectedFromId() {
        val info = LoaderTypeResolver.detectLoader("", "1.20.1-OptiFine_HD_U_I6")
        assertEquals(LoaderTypeResolver.OPTIFINE, info)
    }

    @Test
    fun missingJson_fallsBackToIdSuffix() {
        val info = LoaderTypeResolver.resolve(null, "1.20.1-forge-47.2.0")
        assertEquals("1.20.1-forge-47.2.0", info.baseVersionId)
        assertEquals(LoaderTypeResolver.FORGE, info.loaderType)
    }

    @Test
    fun targetVersionId_matchesInstallerNaming() {
        assertEquals(
            "1.20.1-forge-47.2.0",
            LoaderTypeResolver.targetVersionId("1.20.1", "forge", "47.2.0")
        )
    }

    @Test
    fun installableSet_coversAllSupportedLoaders() {
        assertTrue(LoaderTypeResolver.FORGE in LoaderTypeResolver.INSTALLABLE)
        assertTrue(LoaderTypeResolver.NEOFORGE in LoaderTypeResolver.INSTALLABLE)
        assertTrue(LoaderTypeResolver.FABRIC in LoaderTypeResolver.INSTALLABLE)
        assertTrue(LoaderTypeResolver.QUILT in LoaderTypeResolver.INSTALLABLE)
        assertTrue(LoaderTypeResolver.OPTIFINE in LoaderTypeResolver.INSTALLABLE)
    }

    @Test
    fun displayName_coversAll() {
        assertEquals("原版", LoaderTypeResolver.displayName(null))
        assertEquals("Forge", LoaderTypeResolver.displayName(LoaderTypeResolver.FORGE))
        assertEquals("NeoForge", LoaderTypeResolver.displayName(LoaderTypeResolver.NEOFORGE))
    }
}
