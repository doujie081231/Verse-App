package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 启动目录策略测试。
 *
 * 版本隔离语义 `usesGlobal || isolateGameDir` 同时被模组预检和启动参数使用；
 * 两处若不一致会出现「预检看 A 目录、启动读 B 目录」。
 */
class LaunchDirectoryPolicyTest {

    private val gameDir = File("/game")

    @Test
    fun usesGlobal_isIsolated() {
        assertTrue(LaunchDirectoryPolicy.isIsolated(usesGlobal = true, isolateGameDir = false))
    }

    @Test
    fun isolateGameDir_isIsolated() {
        assertTrue(LaunchDirectoryPolicy.isIsolated(usesGlobal = false, isolateGameDir = true))
    }

    @Test
    fun bothOff_isNotIsolated() {
        assertFalse(LaunchDirectoryPolicy.isIsolated(usesGlobal = false, isolateGameDir = false))
    }

    @Test
    fun isolatedRunDir_isUnderVersions() {
        assertEquals(
            File("/game/versions/1.21.1"),
            LaunchDirectoryPolicy.runDir(gameDir, "1.21.1", isolated = true)
        )
    }

    @Test
    fun sharedRunDir_isGameRoot() {
        assertEquals(gameDir, LaunchDirectoryPolicy.runDir(gameDir, "1.21.1", isolated = false))
    }

    @Test
    fun isolatedModsDir_isVersionMods() {
        assertEquals(
            File("/game/versions/1.21.1/mods"),
            LaunchDirectoryPolicy.modsDir(gameDir, "1.21.1", isolated = true)
        )
    }

    @Test
    fun sharedModsDir_isGameMods() {
        assertEquals(
            File("/game/mods"),
            LaunchDirectoryPolicy.modsDir(gameDir, "1.21.1", isolated = false)
        )
    }
}
