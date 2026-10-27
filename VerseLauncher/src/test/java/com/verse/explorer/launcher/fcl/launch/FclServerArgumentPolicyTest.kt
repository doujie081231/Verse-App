package com.verse.explorer.launcher.fcl.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DefaultLauncher 服务器参数测试。
 *
 * 1.20 之前是 --server/--port，之后是 --quickPlayMultiplayer；
 * 地址非法时抛 IllegalArgumentException，由调用方记日志后继续启动。
 */
class FclServerArgumentPolicyTest {

    @Test
    fun legacyVersion_withPort_usesServerAndPort() {
        assertEquals(
            listOf("--server", "mc.example.com", "--port", "25566"),
            FclServerArgumentPolicy.resolve("1.12.2", "mc.example.com:25566")
        )
    }

    @Test
    fun legacyVersion_withoutPort_defaultsTo25565() {
        assertEquals(
            listOf("--server", "mc.example.com", "--port", "25565"),
            FclServerArgumentPolicy.resolve("1.12.2", "mc.example.com")
        )
    }

    @Test
    fun modernVersion_withoutPort_appends25565() {
        assertEquals(
            listOf("--quickPlayMultiplayer", "mc.example.com:25565"),
            FclServerArgumentPolicy.resolve("1.20.1", "mc.example.com")
        )
    }

    @Test
    fun modernVersion_withPort_keepsAddress() {
        assertEquals(
            listOf("--quickPlayMultiplayer", "mc.example.com:25566"),
            FclServerArgumentPolicy.resolve("1.21.1", "mc.example.com:25566")
        )
    }

    @Test
    fun missingGameVersion_returnsEmpty() {
        assertTrue(FclServerArgumentPolicy.resolve(null, "mc.example.com").isEmpty())
    }

    @Test
    fun invalidAddress_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            FclServerArgumentPolicy.resolve("1.21.1", "mc.example.com:")
        }
    }
}
