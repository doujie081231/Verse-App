package com.verse.explorer.core.launch

/** 不依赖 Android Bundle 的启动协议，供普通启动、快捷入口与冷重启共用。 */
object LaunchRequestCodec {
    fun encode(request: LaunchRequest): Map<String, Any?> = linkedMapOf(
        "versionId" to request.versionId,
        "gameDir" to request.gameDirPath,
        "javaHome" to request.javaHome,
        "javaVersion" to request.javaVersion,
        "maxMemory" to request.maxMemory,
        "minMemory" to request.minMemory,
        "gcType" to request.gcType,
        "customJvmArgs" to request.customJvmArgs,
        "width" to request.gameWidth,
        "height" to request.gameHeight,
        "renderer" to request.renderer,
        "launchMethod" to request.launchMethod,
        "username" to request.username,
        "uuid" to request.uuid,
        "multiplayerMode" to request.isMultiplayer,
        "accessToken" to request.accessToken,
        "userType" to request.userType,
        "authlibServerUrl" to request.authlibServerUrl
    )

    fun decode(values: Map<String, Any?>): Result<LaunchRequest> = runCatching {
        val versionId = requireNotNull(values["versionId"] as? String) { "缺少 versionId" }
        val gameDir = requireNotNull(values["gameDir"] as? String) { "缺少 gameDir" }
        LaunchRequest(
            versionId = versionId,
            gameDirPath = gameDir,
            javaHome = values["javaHome"] as? String,
            javaVersion = values["javaVersion"] as? Int ?: 0,
            maxMemory = values["maxMemory"] as? Int ?: 2048,
            minMemory = values["minMemory"] as? Int ?: 512,
            gcType = values["gcType"] as? String ?: "G1GC",
            customJvmArgs = values["customJvmArgs"] as? String ?: "",
            gameWidth = values["width"] as? Int ?: 854,
            gameHeight = values["height"] as? Int ?: 480,
            renderer = values["renderer"] as? String ?: "gl4es",
            launchMethod = values["launchMethod"] as? String ?: "pojav",
            username = values["username"] as? String ?: "Player",
            uuid = values["uuid"] as? String ?: "00000000000000000000000000000000",
            isMultiplayer = values["multiplayerMode"] as? Boolean ?: false,
            accessToken = values["accessToken"] as? String ?: "0",
            userType = values["userType"] as? String ?: "mojang",
            authlibServerUrl = values["authlibServerUrl"] as? String
        )
    }
}
