package com.verse.explorer.core.launch

import com.verse.explorer.core.data.VersionSettingsRepository.VersionSettings

/** 每次请求读取一次设置；独立配置中的空 JVM 参数是显式清空，不回退全局值。 */
data class EffectiveLaunchSettings(val maxMemory: Int, val minMemory: Int, val renderer: String, val jvmArgs: String) {
    fun withVersion(version: VersionSettings): EffectiveLaunchSettings {
        if (version.usesGlobal) return this
        return copy(
            maxMemory = if (!version.autoMemory && version.maxMemory > 0) version.maxMemory else maxMemory,
            minMemory = if (!version.autoMemory && version.minMemory > 0) version.minMemory else minMemory,
            renderer = version.renderer.ifBlank { renderer },
            jvmArgs = version.javaArgs
        )
    }
}
