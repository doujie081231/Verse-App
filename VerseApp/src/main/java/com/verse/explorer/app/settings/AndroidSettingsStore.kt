package com.verse.explorer.app.settings

import android.app.Application
import com.verse.explorer.core.settings.*
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.app.data.MousePointerSetting
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.DisplayNotch
import com.verse.explorer.app.ui.theme.*
import com.tungsten.fcl.setting.MenuSetting

/** 保留既有 SharedPreferences 与控制设置格式的 Android 适配器。 */
internal class AndroidSettingsStore(private val application: Application) : SettingsStore {
    fun setThemeAccent(value: String) = SettingsRepository.setThemeAccent(value)
    override fun load(): SettingsUiState {
        MenuSetting.init(application.filesDir)
        MousePointerSetting.init(application.filesDir)
        return SettingsUiState(
            renderScale = (MenuSetting.windowScale * 100).toInt().coerceIn(25, 300),
            maxMemory = SettingsRepository.getMaxMemory().toFloat(),
            minMemory = SettingsRepository.getMinMemory().toFloat(),
            gcType = SettingsRepository.getGcType(),
            customJvmArgs = SettingsRepository.getCustomJvmArgs(),
            renderer = SettingsRepository.getRenderer(),
            launchMethod = SettingsRepository.getLaunchMethod(),
            gameWidth = SettingsRepository.getGameWidth().toString(),
            gameHeight = SettingsRepository.getGameHeight().toString(),
            fullscreen = SettingsRepository.getFullscreen(),
            ignoreNotch = DisplayNotch.ignoreNotch,
            downloadSource = SettingsRepository.getDownloadSource(),
            javaVersion = SettingsRepository.getJavaVersion(),
            language = SettingsRepository.getLanguage(),
            keepScreenOn = SettingsRepository.getKeepScreenOn(),
            showFps = SettingsRepository.getShowFps(),
            autoSetChinese = SettingsRepository.isAutoSetChinese(),
            debugMode = SettingsRepository.getDebugMode(),
            windowTitle = SettingsRepository.getWindowTitle(),
            launcherVisibility = SettingsRepository.getLauncherVisibility(),
            versexRenderer = SettingsRepository.getVersexRenderer(),
            versexPipelineCache = SettingsRepository.getVersexPipelineCache(),
            graphicsBackend = SettingsRepository.getGraphicsBackend(),
            vkDriver = if (SettingsRepository.getVkDriverSystem()) "system" else "turnip",
            notCheckGame = SettingsRepository.getNotCheckGame(),
            debugLog = SettingsRepository.getDebugLog(),
            requestMicPermission = SettingsRepository.getRequestMicPermission(),
            themeColor = SettingsRepository.getThemeColor(),
            bgOpacity = VersePersonalization.backgroundOpacity,
            bgBlur = VersePersonalization.backgroundBlur,
            bgBlurType = VersePersonalization.backgroundBlurType,
            bgVideoVolume = VersePersonalization.videoVolume
        )
    }

    override fun save(state: SettingsUiState) {
        with(state) {
            SettingsRepository.apply {
                setMaxMemory(maxMemory.toInt())
                setMinMemory(minMemory.toInt())
                setGcType(gcType)
                setCustomJvmArgs(customJvmArgs)
                setRenderer(renderer)
                setLaunchMethod(launchMethod)
                setGameWidth(gameWidth.toIntOrNull() ?: 854)
                setGameHeight(gameHeight.toIntOrNull() ?: 480)
                setFullscreen(fullscreen)
                setIgnoreNotch(ignoreNotch)
                setDownloadSource(downloadSource)
                setJavaVersion(javaVersion)
                setLanguage(language)
                setKeepScreenOn(keepScreenOn)
                setShowFps(showFps)
                setAutoSetChinese(autoSetChinese)
                setDebugMode(debugMode)
                setWindowTitle(windowTitle)
                setLauncherVisibility(launcherVisibility)
                setThemeColor(themeColor)
            }
            MenuSetting.windowScale = renderScale.coerceIn(25, 300) / 100.0
            MenuSetting.notifyChange(MenuSetting.Key.WINDOW_SCALE)
        }
    }

    override fun reset(): SettingsUiState {
        SettingsRepository.resetToDefaults()
        DisplayNotch.updateIgnoreNotch(false)
        VerseColors.customAccent = null
        VerseColors.applySchemeAccents(primary = null, secondary = null, tertiary = null)
        SettingsRepository.setThemeAccent("")
        VersePersonalization.setStyle("none")
        VersePersonalization.setOpacity(95)
        VersePersonalization.applyBackgroundBlur(0)
        VersePersonalization.applyBackgroundBlurType("background")
        VersePersonalization.applyVideoVolume(0)
        VersePersonalization.applyColorTheme(ColorThemeType.LIGHT.name)
        VersePersonalization.applyThemeCustomColor(0xFF0000FF.toInt())
        VersePersonalization.applyPaletteStyle("TonalSpot")
        VersePersonalization.applyDarkMode(DarkMode.FOLLOW_SYSTEM.name)
        VersePersonalization.applyActionMenuSide(ActionMenuSide.START.name)
        VersePersonalization.applyLauncherFullScreen(true)
        VersePersonalization.clear(application, "image")
        VersePersonalization.clear(application, "video")
        return load()
    }

    override fun persistAdvanced(field: AdvancedSetting, state: SettingsUiState) {
        when (field) {
            AdvancedSetting.PIPELINE_CACHE -> SettingsRepository.setVersexPipelineCache(state.versexPipelineCache)
            AdvancedSetting.GRAPHICS_BACKEND -> SettingsRepository.setGraphicsBackend(state.graphicsBackend)
            AdvancedSetting.VULKAN_DRIVER -> SettingsRepository.setVkDriverSystem(state.vkDriver == "system")
            AdvancedSetting.SKIP_GAME_CHECK -> SettingsRepository.setNotCheckGame(state.notCheckGame)
            AdvancedSetting.DEBUG_LOG -> SettingsRepository.setDebugLog(state.debugLog)
            AdvancedSetting.MICROPHONE -> SettingsRepository.setRequestMicPermission(state.requestMicPermission)
        }
    }
}
