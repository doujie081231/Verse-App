package com.verse.explorer.app.ui.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** 主页固定卡片的共享状态与操作回调（账户、当前版本、启动入口） */
data class HomeHubState(
    val account: com.verse.explorer.core.account.Account? = null,
    val currentVersion: com.verse.explorer.core.version.MinecraftVersion? = null,
    val currentVersionId: String? = null,
    val launching: Boolean = false,
    val onGoAccounts: () -> Unit = {},
    val onOpenVersions: () -> Unit = {},
    val onLaunch: () -> Unit = {}
)

val LocalHomeHub = staticCompositionLocalOf { HomeHubState() }
