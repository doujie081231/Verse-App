package com.verse.explorer.app.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VulkanCheckManager.hasVulkanBackend 的版本门禁断言：
 * 26.2 起有 Vulkan 后端；快照/pre/rc 归一化为目标正式版本后判断。
 *
 * 注意：比较走 FclCore 的 GameVersionNumber，其静态初始化从 classpath 读取
 * /assets/game/versions.txt（生产环境来自 APK assets）。该资源已镜像到
 * VerseApp/src/test/resources/assets/game/versions.txt，删除会导致本测试类
 * 全部用例以 NoClassDefFoundError 失败。
 */
class VulkanCheckManagerHasVulkanBackendTest {

    @Test
    fun nullVersion_isFalse() {
        assertFalse(VulkanCheckManager.hasVulkanBackend(null))
    }

    @Test
    fun minVersion_isTrue() {
        assertTrue(VulkanCheckManager.hasVulkanBackend("26.2"))
    }

    @Test
    fun aboveMin_isTrue() {
        assertTrue(VulkanCheckManager.hasVulkanBackend("26.3"))
    }

    @Test
    fun belowMin_isFalse() {
        assertFalse(VulkanCheckManager.hasVulkanBackend("1.21.4"))
    }

    @Test
    fun prereleaseOfMin_isTrue() {
        // 26.x 预发布命名带横线（见 assets/game/versions.txt：26.2-pre-1/26.2-rc-1）：
        // "26.2-pre-1" 归一化为 "26.2"
        assertTrue(VulkanCheckManager.hasVulkanBackend("26.2-pre-1"))
    }

    @Test
    fun prereleaseBelowMin_isFalse() {
        // "26.1-rc-1" 归一化为 "26.1"，低于门槛
        assertFalse(VulkanCheckManager.hasVulkanBackend("26.1-rc-1"))
    }

    @Test
    fun snapshotAboveMin_isTrue() {
        // "26.3-snapshot-5" 归一化为 "26.3"
        assertTrue(VulkanCheckManager.hasVulkanBackend("26.3-snapshot-5"))
    }
}
