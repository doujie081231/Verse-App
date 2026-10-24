package com.verse.explorer.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val renderScale: Int = 100,
    val maxMemory: Float = 2048f,
    val minMemory: Float = 512f,
    val gcType: String = "G1GC",
    val customJvmArgs: String = "",
    val renderer: String = "nggl4es",
    val launchMethod: String = "pojav",
    val gameWidth: String = "854",
    val gameHeight: String = "480",
    val fullscreen: Boolean = false,
    val ignoreNotch: Boolean = false,
    val downloadSource: String = "bmclapi",
    val javaVersion: String = "17",
    val language: String = "简体中文",
    val keepScreenOn: Boolean = true,
    val showFps: Boolean = false,
    val autoSetChinese: Boolean = true,
    val debugMode: Boolean = false,
    val windowTitle: String = "",
    val launcherVisibility: String = "keep",
    val versexRenderer: Boolean = false,
    val versexPipelineCache: Boolean = true,
    val graphicsBackend: String = "default",
    val vkDriver: String = "system",
    val notCheckGame: Boolean = false,
    val debugLog: Boolean = false,
    val requestMicPermission: Boolean = false,
    val themeColor: String = "white",
    val bgOpacity: Int = 95,
    val bgBlur: Int = 0,
    val bgBlurType: String = "background",
    val bgVideoVolume: Int = 0
)

enum class AdvancedSetting { PIPELINE_CACHE, GRAPHICS_BACKEND, VULKAN_DRIVER, SKIP_GAME_CHECK, DEBUG_LOG, MICROPHONE }

interface SettingsStore {
    fun load(): SettingsUiState
    fun save(state: SettingsUiState)
    fun reset(): SettingsUiState
    fun persistAdvanced(field: AdvancedSetting, state: SettingsUiState)
}

sealed interface SettingsEvent {
    data class Edit(val transform: (SettingsUiState) -> SettingsUiState) : SettingsEvent
    data object Save : SettingsEvent
    data object Reset : SettingsEvent
    data class PersistAdvanced(val field: AdvancedSetting) : SettingsEvent
}

/** 草稿和持久化分离：保留基本设置点击保存、高级开关即时保存的原有行为。 */
class SettingsEditor(private val store: SettingsStore) {
    private val mutableState = MutableStateFlow(store.load())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.Edit -> mutableState.value = event.transform(mutableState.value)
            SettingsEvent.Save -> store.save(mutableState.value)
            SettingsEvent.Reset -> mutableState.value = store.reset()
            is SettingsEvent.PersistAdvanced -> store.persistAdvanced(event.field, mutableState.value)
        }
    }
}
