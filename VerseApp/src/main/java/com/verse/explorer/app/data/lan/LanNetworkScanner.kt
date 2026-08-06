package com.verse.explorer.app.data.lan

import java.net.Inet4Address
import java.net.NetworkInterface

object LanNetworkScanner {

    fun getLocalIPs(): List<String> {
        val ips = mutableListOf<String>()
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                if (!iface.isLoopback && iface.isUp) {
                    iface.inetAddresses.toList().forEach { addr ->
                        if (addr is Inet4Address) {
                            ips.add(addr.hostAddress ?: "")
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ips.distinct()
    }
}
