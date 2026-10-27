package com.verse.explorer.launcher.fcl.launch

import com.verse.explorer.launcher.fcl.util.ServerAddress
import com.verse.explorer.launcher.fcl.util.versioning.GameVersionNumber

/**
 * DefaultLauncher 服务器参数生成。
 *
 * 1.20 之前：--server <host> --port <port>；
 * 1.20 起：--quickPlayMultiplayer <address>（无端口时补 :25565）。
 */
object FclServerArgumentPolicy {

    @JvmStatic
    fun resolve(gameVersion: String?, address: String): List<String> {
        if (gameVersion == null) return emptyList()
        val parsed = ServerAddress.parse(address)
        return if (GameVersionNumber.compare(gameVersion, "1.20") < 0) {
            listOf(
                "--server",
                parsed.host,
                "--port",
                if (parsed.port >= 0) parsed.port.toString() else "25565"
            )
        } else {
            listOf(
                "--quickPlayMultiplayer",
                if (parsed.port < 0) "$address:25565" else address
            )
        }
    }
}
