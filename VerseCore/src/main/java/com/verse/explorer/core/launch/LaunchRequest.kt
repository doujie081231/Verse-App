package com.verse.explorer.core.launch

/** 单次游戏启动的不可变输入；默认值与既有 Activity 启动协议保持一致。 */
data class LaunchRequest(
    val versionId: String,
    val gameDirPath: String,
    val javaHome: String? = null,
    val javaVersion: Int = 0,
    val maxMemory: Int = 2048,
    val minMemory: Int = 512,
    val gcType: String = "G1GC",
    val customJvmArgs: String = "",
    val gameWidth: Int = 854,
    val gameHeight: Int = 480,
    val renderer: String = "gl4es",
    val launchMethod: String = "pojav",
    val username: String = "Player",
    val uuid: String = "00000000000000000000000000000000",
    val isMultiplayer: Boolean = false,
    val accessToken: String = "0",
    val userType: String = "mojang",
    val authlibServerUrl: String? = null
) {
    val mcVersionMajor: Int
        get() = versionId.split('.').firstOrNull()?.toIntOrNull() ?: 0

    override fun toString(): String =
        "LaunchRequest(versionId=$versionId, javaVersion=$javaVersion, renderer=$renderer, " +
            "size=${gameWidth}x$gameHeight, multiplayer=$isMultiplayer)"
}
