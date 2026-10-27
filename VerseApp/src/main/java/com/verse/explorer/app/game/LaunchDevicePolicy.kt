package com.verse.explorer.app.game

/**
 * arm64/RenderPearl 启动环境决策。
 *
 * 把「哪些设备/版本需要 Vulkan 驱动与 vkshim」从启动编排里抽出来：
 * 只接收普通参数，不读 Build/SharedPreferences，便于在 JVM 上模拟 arm64 真机。
 */
internal object LaunchDevicePolicy {

    const val ABI_ARM64 = "arm64-v8a"

    data class VulkanEnvironment(
        val driverSystem: Boolean,
        val driverPath: String,
        val shimEnabled: Boolean,
        val pipelineCache: Boolean
    )

    /**
     * 26.x 及以上且设备 64 位 ABI 含 arm64-v8a 才注入 Vulkan 环境；
     * 其余情况返回 null（保持原内联条件的短路语义）。
     */
    fun vulkanEnvironment(
        mcVersionMajor: Int,
        supported64BitAbis: List<String>,
        vkDriverSystem: Boolean,
        needsVulkanShim: Boolean,
        pipelineCache: Boolean,
        nativeLibraryDir: String
    ): VulkanEnvironment? {
        if (mcVersionMajor < 26) return null
        if (ABI_ARM64 !in supported64BitAbis) return null
        return VulkanEnvironment(
            driverSystem = vkDriverSystem,
            driverPath = nativeLibraryDir,
            shimEnabled = needsVulkanShim,
            pipelineCache = pipelineCache
        )
    }
}
