package com.verse.explorer.core.launch

import com.verse.explorer.core.data.VersionSettingsRepository.VersionSettings
import org.junit.Assert.*
import org.junit.Test

class EffectiveLaunchSettingsTest {
    private val global = EffectiveLaunchSettings(2048, 512, "global", "-Dglobal=true")
    @Test fun globalModeIgnoresStoredVersionValues() {
        assertEquals(global, global.withVersion(VersionSettings(maxMemory = 4096, renderer = "plugin", javaArgs = "-Xmx1G")))
    }
    @Test fun independentModeUsesOverridesAndExplicitlyClearsJvmArgs() {
        assertEquals(EffectiveLaunchSettings(4096, 1024, "plugin", ""), global.withVersion(
            VersionSettings(usesGlobal = false, autoMemory = false, maxMemory = 4096, minMemory = 1024, renderer = "plugin")))
    }
    @Test fun autoMemoryAndBlankRendererKeepResolvedGlobalValues() {
        assertEquals(global.copy(jvmArgs = ""), global.withVersion(VersionSettings(usesGlobal = false, maxMemory = 4096)))
    }
}
