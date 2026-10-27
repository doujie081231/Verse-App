package com.verse.explorer.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * arm64/26.x Vulkan 启动环境决策测试。
 *
 * 覆盖原 GameLaunchOrchestrator 内联条件：
 * `mcVersionMajor >= 26 && Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" }`。
 * 参数化 ABI 后可在普通 JVM 上模拟 arm64 真机，不需要设备。
 */
class LaunchDevicePolicyTest {

    private fun env(
        major: Int = 26,
        abis: List<String> = listOf("arm64-v8a"),
        driverSystem: Boolean = true,
        shim: Boolean = true,
        pipelineCache: Boolean = true,
        nativeDir: String = "/data/app/lib"
    ) = LaunchDevicePolicy.vulkanEnvironment(
        mcVersionMajor = major,
        supported64BitAbis = abis,
        vkDriverSystem = driverSystem,
        needsVulkanShim = shim,
        pipelineCache = pipelineCache,
        nativeLibraryDir = nativeDir
    )

    @Test
    fun arm64And26x_producesEnvironment() {
        val result = env()
        assertNotNull(result)
        assertTrue(result!!.driverSystem)
        assertTrue(result.shimEnabled)
        assertTrue(result.pipelineCache)
    }

    @Test
    fun x86_64_returnsNull() {
        assertNull(env(abis = listOf("x86_64")))
    }

    @Test
    fun legacyVersion_returnsNull() {
        assertNull(env(major = 1))
        assertNull(env(major = 25))
    }

    @Test
    fun shimAndPipelineFlags_preserved() {
        val result = env(shim = false, pipelineCache = false)
        assertNotNull(result)
        assertFalse(result!!.shimEnabled)
        assertFalse(result.pipelineCache)
    }

    @Test
    fun driverPathAndSystemFlag_preserved() {
        val result = env(driverSystem = false, nativeDir = "/data/user/0/com.verse.explorer/lib/arm64")
        assertEquals("/data/user/0/com.verse.explorer/lib/arm64", result!!.driverPath)
        assertFalse(result.driverSystem)
    }
}
