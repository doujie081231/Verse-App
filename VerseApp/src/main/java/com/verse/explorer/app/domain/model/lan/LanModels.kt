package com.verse.explorer.app.domain.model.lan

data class LanRoomInfo(
    val code: String = "",
    val role: String = "host",
    val mode: String = "terracotta",
    val gamePort: Int = 25565,
    val relayPort: Int = 0,
    val publicIP: String? = null,
    val localIPs: List<String> = emptyList(),
    val peerCount: Int = 0,
    val peers: List<String> = emptyList(),
    val status: String = "waiting"
)

data class LanLogEntry(
    val time: String,
    val message: String,
    val type: String = "info"
)

enum class LanViewMode {
    MENU, TERRACOTTA, PORTMAP, ROOM_PANEL
}
