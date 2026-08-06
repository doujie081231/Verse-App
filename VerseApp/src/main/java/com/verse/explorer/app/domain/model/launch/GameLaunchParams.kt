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
)
