package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.verse.explorer.app.settings.AndroidSettingsStore
import com.verse.explorer.core.settings.SettingsEditor
import com.verse.explorer.core.settings.SettingsEvent
import com.verse.explorer.core.settings.SettingsUiState
import kotlin.reflect.KProperty

class SettingsBinding<T>(private val read: () -> T, private val edit: (T) -> Unit) {
    operator fun getValue(owner: Any?, property: KProperty<*>): T = read()
    operator fun setValue(owner: Any?, property: KProperty<*>, value: T) = edit(value)
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AndroidSettingsStore(application)
    private val editor = SettingsEditor(store)
    var uiState by mutableStateOf(editor.state.value)
        private set

    fun onEvent(event: SettingsEvent) {
        editor.onEvent(event)
        uiState = editor.state.value
    }

    fun setThemeAccent(value: String) = store.setThemeAccent(value)

    private fun <T> bind(read: (SettingsUiState) -> T, edit: (SettingsUiState, T) -> SettingsUiState) =
        SettingsBinding({ read(uiState) }) { value -> onEvent(SettingsEvent.Edit { edit(it, value) }) }

    val renderScaleBinding: SettingsBinding<Int> = bind({ it.renderScale }) { state, value -> state.copy(renderScale = value) }
    val maxMemoryBinding: SettingsBinding<Float> = bind({ it.maxMemory }) { state, value -> state.copy(maxMemory = value) }
    val minMemoryBinding: SettingsBinding<Float> = bind({ it.minMemory }) { state, value -> state.copy(minMemory = value) }
    val gcTypeBinding: SettingsBinding<String> = bind({ it.gcType }) { state, value -> state.copy(gcType = value) }
    val customJvmArgsBinding: SettingsBinding<String> = bind({ it.customJvmArgs }) { state, value -> state.copy(customJvmArgs = value) }
    val rendererBinding: SettingsBinding<String> = bind({ it.renderer }) { state, value -> state.copy(renderer = value) }
    val launchMethodBinding: SettingsBinding<String> = bind({ it.launchMethod }) { state, value -> state.copy(launchMethod = value) }
    val gameWidthBinding: SettingsBinding<String> = bind({ it.gameWidth }) { state, value -> state.copy(gameWidth = value) }
    val gameHeightBinding: SettingsBinding<String> = bind({ it.gameHeight }) { state, value -> state.copy(gameHeight = value) }
    val fullscreenBinding: SettingsBinding<Boolean> = bind({ it.fullscreen }) { state, value -> state.copy(fullscreen = value) }
    val ignoreNotchBinding: SettingsBinding<Boolean> = bind({ it.ignoreNotch }) { state, value -> state.copy(ignoreNotch = value) }
    val downloadSourceBinding: SettingsBinding<String> = bind({ it.downloadSource }) { state, value -> state.copy(downloadSource = value) }
    val javaVersionBinding: SettingsBinding<String> = bind({ it.javaVersion }) { state, value -> state.copy(javaVersion = value) }
    val languageBinding: SettingsBinding<String> = bind({ it.language }) { state, value -> state.copy(language = value) }
    val keepScreenOnBinding: SettingsBinding<Boolean> = bind({ it.keepScreenOn }) { state, value -> state.copy(keepScreenOn = value) }
    val showFpsBinding: SettingsBinding<Boolean> = bind({ it.showFps }) { state, value -> state.copy(showFps = value) }
    val autoSetChineseBinding: SettingsBinding<Boolean> = bind({ it.autoSetChinese }) { state, value -> state.copy(autoSetChinese = value) }
    val debugModeBinding: SettingsBinding<Boolean> = bind({ it.debugMode }) { state, value -> state.copy(debugMode = value) }
    val windowTitleBinding: SettingsBinding<String> = bind({ it.windowTitle }) { state, value -> state.copy(windowTitle = value) }
    val launcherVisibilityBinding: SettingsBinding<String> = bind({ it.launcherVisibility }) { state, value -> state.copy(launcherVisibility = value) }
    val versexRendererBinding: SettingsBinding<Boolean> = bind({ it.versexRenderer }) { state, value -> state.copy(versexRenderer = value) }
    val versexPipelineCacheBinding: SettingsBinding<Boolean> = bind({ it.versexPipelineCache }) { state, value -> state.copy(versexPipelineCache = value) }
    val graphicsBackendBinding: SettingsBinding<String> = bind({ it.graphicsBackend }) { state, value -> state.copy(graphicsBackend = value) }
    val vkDriverBinding: SettingsBinding<String> = bind({ it.vkDriver }) { state, value -> state.copy(vkDriver = value) }
    val notCheckGameBinding: SettingsBinding<Boolean> = bind({ it.notCheckGame }) { state, value -> state.copy(notCheckGame = value) }
    val debugLogBinding: SettingsBinding<Boolean> = bind({ it.debugLog }) { state, value -> state.copy(debugLog = value) }
    val requestMicPermissionBinding: SettingsBinding<Boolean> = bind({ it.requestMicPermission }) { state, value -> state.copy(requestMicPermission = value) }
    val themeColorBinding: SettingsBinding<String> = bind({ it.themeColor }) { state, value -> state.copy(themeColor = value) }
    val bgOpacityBinding: SettingsBinding<Int> = bind({ it.bgOpacity }) { state, value -> state.copy(bgOpacity = value) }
    val bgBlurBinding: SettingsBinding<Int> = bind({ it.bgBlur }) { state, value -> state.copy(bgBlur = value) }
    val bgBlurTypeBinding: SettingsBinding<String> = bind({ it.bgBlurType }) { state, value -> state.copy(bgBlurType = value) }
    val bgVideoVolumeBinding: SettingsBinding<Int> = bind({ it.bgVideoVolume }) { state, value -> state.copy(bgVideoVolume = value) }
}
