package com.verse.explorer.app.domain.model.launch

data class GameLaunchParams(
    val versionId: String,
    val gameDirPath: String,
    val javaHome: String?,
    val javaVersion: Int,
    val maxMemory: Int,
    val minMemory: Int,
    val gcType: String,
    val customJvmArgs: String,
    val gameWidth: Int,
    val gameHeight: Int,
    val renderer: String,
    val launchMethod: String,
    val username: String,
    val uuid: String,
    val isMultiplayer: Boolean,
    val accessToken: String,
    val userType: String
) {
    /** Minecraft 主版本号（如 "26.3" → 26，"1.21.1" → 1），用于 RenderPearl/Vulkan 等按版本门控 */
    val mcVersionMajor: Int
        get() = versionId.split('.').firstOrNull()?.toIntOrNull() ?: 0
}
