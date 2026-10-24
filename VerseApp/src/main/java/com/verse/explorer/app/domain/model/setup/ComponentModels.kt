package com.verse.explorer.app.domain.model.setup

/** 组件状态数据类（供外部使用） */
data class ComponentState(
    val isReady: Boolean = false,
    val isInstalling: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)
