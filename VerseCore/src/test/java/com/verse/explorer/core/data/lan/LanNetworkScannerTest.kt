package com.verse.explorer.core.data.lan

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LanNetworkScanner.getLocalIPs 的格式与去重断言。
 * 本机与 CI 环境网卡不同，只断言结果形态，不断言具体地址。
 */
class LanNetworkScannerTest {

    @Test
    fun getLocalIPs_returnsDistinctValidIPv4() {
        val ips = LanNetworkScanner.getLocalIPs()
        assertTrue("IP 列表应去重", ips.distinct().size == ips.size)
        ips.forEach { ip ->
            assertTrue("非法 IPv4: $ip", IPV4_REGEX.matches(ip))
        }
    }

    @Test
    fun getLocalIPs_neverThrows() {
        // 无网卡/仅回环的容器环境也应安静返回（可能为空列表）
        LanNetworkScanner.getLocalIPs()
    }

    companion object {
        private val IPV4_REGEX = Regex(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
        )
    }
}
