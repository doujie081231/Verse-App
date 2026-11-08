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
    val userType: String,
    /** 外置登录（皮肤站）验证服务器 API 根；非外置账号为 null */
    val authlibServerUrl: String? = null,
    /**
     * 外置登录服务器的 metadata 原文（JSON）。启动时以 base64 传给
     * `-Dauthlibinjector.yggdrasil.prefetched`，避免 agent 在启动阶段再联网拉取；
     * 为空表示没取到，agent 会退回自己拉（与旧行为一致）。
     */
    val authlibPrefetchedMeta: String? = null,
    /**
     * 真实 Minecraft 版本号（如 "26.2"），由 [com.verse.explorer.app.game.McVersionResolver]
     * 从版本 json 的 clientVersion / inheritsFrom 解析得到。
     * 为空时退回用 versionId 解析——整合包实例的 id 是包名（"Fabulously Optimized"），
     * 解析不出主版本号，会让所有 26.x 门控失效。
     */
    val mcVersion: String? = null
) {
    /** Minecraft 主版本号（如 "26.3" → 26，"1.21.1" → 1），用于 RenderPearl/Vulkan 等按版本门控 */
    val mcVersionMajor: Int
        get() = com.verse.explorer.app.game.McVersionResolver.major(mcVersion ?: versionId)
}
