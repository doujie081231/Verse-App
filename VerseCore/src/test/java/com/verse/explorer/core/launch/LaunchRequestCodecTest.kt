package com.verse.explorer.core.launch

import org.junit.Assert.*
import org.junit.Test

class LaunchRequestCodecTest {
    @Test fun legacyQuickLaunchKeepsActivityDefaults() {
        val request = LaunchRequestCodec.decode(mapOf("versionId" to "1.21.1", "gameDir" to "/game")).getOrThrow()
        assertEquals(LaunchRequest("1.21.1", "/game"), request)
        assertEquals(0, request.javaVersion)
        assertEquals("gl4es", request.renderer)
        assertEquals(2048, request.maxMemory)
    }

    @Test fun normalAndColdRestartRoundTripEveryField() {
        val request = LaunchRequest("26.3", "/custom/game", "/jre21", 21, 4096, 1024,
            "ZGC", "-Dexample=true", 1920, 1080, "plugin:renderer", "pojav",
            "Example", "uuid", true, "private-token", "msa", "https://auth.example/api")
        assertEquals(request, LaunchRequestCodec.decode(LaunchRequestCodec.encode(request)).getOrThrow())
        assertEquals(26, request.mcVersionMajor)
        assertFalse(request.toString().contains("private-token"))
    }

    @Test fun missingRequiredFieldsFailWithoutInventingDirectoryOrVersion() {
        assertTrue(LaunchRequestCodec.decode(emptyMap()).isFailure)
        assertTrue(LaunchRequestCodec.decode(mapOf("versionId" to "1.21.1")).isFailure)
        assertTrue(LaunchRequestCodec.decode(mapOf("gameDir" to "/game")).isFailure)
    }

    @Test fun incompatibleOptionalExtraTypesUseLegacyDefaults() {
        val request = LaunchRequestCodec.decode(mapOf("versionId" to "1.20.1", "gameDir" to "/game",
            "javaVersion" to "17", "width" to "1920", "multiplayerMode" to "true")).getOrThrow()
        assertEquals(0, request.javaVersion)
        assertEquals(854, request.gameWidth)
        assertFalse(request.isMultiplayer)
    }

    @Test fun externalLoginAndMultiplayerMetadataArePreserved() {
        val request = LaunchRequest("1.20.1", "/game", userType = "mojang",
            authlibServerUrl = "https://auth.example/api", isMultiplayer = true)
        assertEquals(request, LaunchRequestCodec.decode(LaunchRequestCodec.encode(request) +
            mapOf("TERRACOTTA_PLAYER" to "Example", "multiplayerHost" to "host")).getOrThrow())
    }
}
