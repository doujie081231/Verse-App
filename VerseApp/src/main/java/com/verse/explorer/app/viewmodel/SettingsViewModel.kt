package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import com.verse.explorer.core.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository

    var gameDir by mutableStateOf(settingsRepo.getGameDir())
        private set
    var maxMemory by mutableStateOf(settingsRepo.getMaxMemory())
        private set
    var minMemory by mutableStateOf(settingsRepo.getMinMemory())
        private set
    var rendererType by mutableStateOf(settingsRepo.getRenderer())
        private set
    var showVirtualMouse by mutableStateOf(settingsRepo.getShowVirtualMouse())
        private set
    var virtualMouseOpacity by mutableStateOf(settingsRepo.getVirtualKeyOpacity())
        private set
    var theme by mutableStateOf(settingsRepo.getTheme())
        private set
    var language by mutableStateOf(settingsRepo.getLanguage())
        private set
    var keepScreenOn by mutableStateOf(settingsRepo.getKeepScreenOn())
        private set
    var showFps by mutableStateOf(settingsRepo.getShowFps())
        private set
    var forceLandscape by mutableStateOf(settingsRepo.getForceLandscape())
        private set

    var downloadSource by mutableStateOf(settingsRepo.getDownloadSource())
        private set
    var versionSource by mutableStateOf(settingsRepo.getVersionSource())
        private set
    var maxThreads by mutableStateOf(settingsRepo.getMaxThreads())
        private set
    var speedLimit by mutableStateOf(settingsRepo.getSpeedLimit())
        private set
    var targetFolder by mutableStateOf(settingsRepo.getTargetFolder())
        private set
    var sslVerify by mutableStateOf(settingsRepo.getVerifySsl())
        private set

    var communitySource by mutableStateOf(settingsRepo.getCommunitySource())
        private set
    var fileNameFormat by mutableStateOf(settingsRepo.getFileNameFormat())
        private set
    var modStyle by mutableStateOf(settingsRepo.getModStyle())
        private set
    var ignoreQuilt by mutableStateOf(settingsRepo.getIgnoreQuilt())
        private set

    var showIcons by mutableStateOf(settingsRepo.getShowIcons())
        private set
    var iconStyle by mutableStateOf(settingsRepo.getIconStyle())
        private set
    var autoUpdate by mutableStateOf(settingsRepo.getAutoUpdate())
        private set
    var backgroundMusic by mutableStateOf(settingsRepo.getBackgroundMusic())
        private set
    var soundEffects by mutableStateOf(settingsRepo.getSoundEffects())
        private set
    var vibration by mutableStateOf(settingsRepo.getVibration())
        private set
    var notifications by mutableStateOf(settingsRepo.getNotifications())
        private set
    var logLevel by mutableStateOf(settingsRepo.getLogLevel())
        private set
    var debugMode by mutableStateOf(settingsRepo.getDebugMode())
        private set

    fun updateGameDir(dir: String) {
        gameDir = dir
        settingsRepo.setGameDir(dir)
    }

    fun updateMaxMemory(memory: Int) {
        maxMemory = memory
        settingsRepo.setMaxMemory(memory)
    }

    fun updateMinMemory(memory: Int) {
        minMemory = memory
        settingsRepo.setMinMemory(memory)
    }

    fun updateRendererType(type: String) {
        rendererType = type
        settingsRepo.setRenderer(type)
    }

    fun updateShowVirtualMouse(show: Boolean) {
        showVirtualMouse = show
        settingsRepo.setShowVirtualMouse(show)
    }

    fun updateVirtualMouseOpacity(opacity: Float) {
        virtualMouseOpacity = opacity
        settingsRepo.setVirtualKeyOpacity(opacity)
    }

    fun updateTheme(newTheme: String) {
        theme = newTheme
        settingsRepo.setTheme(newTheme)
    }

    fun updateLanguage(lang: String) {
        language = lang
        settingsRepo.setLanguage(lang)
    }

    fun updateKeepScreenOn(keep: Boolean) {
        keepScreenOn = keep
        settingsRepo.setKeepScreenOn(keep)
    }

    fun updateShowFps(show: Boolean) {
        showFps = show
        settingsRepo.setShowFps(show)
    }

    fun updateForceLandscape(force: Boolean) {
        forceLandscape = force
        settingsRepo.setForceLandscape(force)
    }

    fun updateDownloadSource(source: String) {
        downloadSource = source
        settingsRepo.setDownloadSource(source)
    }

    fun updateVersionSource(source: String) {
        versionSource = source
        settingsRepo.setVersionSource(source)
    }

    fun updateMaxThreads(threads: Int) {
        maxThreads = threads
        settingsRepo.setMaxThreads(threads)
    }

    fun updateSpeedLimit(limit: Int) {
        speedLimit = limit
        settingsRepo.setSpeedLimit(limit)
    }

    fun updateTargetFolder(folder: String) {
        targetFolder = folder
        settingsRepo.setTargetFolder(folder)
    }

    fun updateSslVerify(verify: Boolean) {
        sslVerify = verify
        settingsRepo.setVerifySsl(verify)
    }

    fun updateCommunitySource(source: String) {
        communitySource = source
        settingsRepo.setCommunitySource(source)
    }

    fun updateFileNameFormat(format: String) {
        fileNameFormat = format
        settingsRepo.setFileNameFormat(format)
    }

    fun updateModStyle(style: String) {
        modStyle = style
        settingsRepo.setModStyle(style)
    }

    fun updateIgnoreQuilt(ignore: Boolean) {
        ignoreQuilt = ignore
        settingsRepo.setIgnoreQuilt(ignore)
    }

    fun updateShowIcons(show: Boolean) {
        showIcons = show
        settingsRepo.setShowIcons(show)
    }

    fun updateIconStyle(style: String) {
        iconStyle = style
        settingsRepo.setIconStyle(style)
    }

    fun updateAutoUpdate(auto: Boolean) {
        autoUpdate = auto
        settingsRepo.setAutoUpdate(auto)
    }

    fun updateBackgroundMusic(enabled: Boolean) {
        backgroundMusic = enabled
        settingsRepo.setBackgroundMusic(enabled)
    }

    fun updateSoundEffects(enabled: Boolean) {
        soundEffects = enabled
        settingsRepo.setSoundEffects(enabled)
    }

    fun updateVibration(enabled: Boolean) {
        vibration = enabled
        settingsRepo.setVibration(enabled)
    }

    fun updateNotifications(enabled: Boolean) {
        notifications = enabled
        settingsRepo.setNotifications(enabled)
    }

    fun updateLogLevel(level: String) {
        logLevel = level
        settingsRepo.setLogLevel(level)
    }

    fun updateDebugMode(debug: Boolean) {
        debugMode = debug
        settingsRepo.setDebugMode(debug)
    }

    fun resetAll() {
        gameDir = "/storage/emulated/0/VerseExplorerX"
        maxMemory = 2048
        minMemory = 512
        rendererType = "gl4es"
        showVirtualMouse = true
        virtualMouseOpacity = 0.7f
        theme = "深色"
        language = "简体中文"
        keepScreenOn = true
        showFps = false
        forceLandscape = true
        downloadSource = "优先使用官方源，在加载缓慢时切换镜像源"
        versionSource = "优先使用官方源，在加载缓慢时切换镜像源"
        maxThreads = 32
        speedLimit = 0
        targetFolder = ""
        sslVerify = false
        communitySource = "尽量使用官方源"
        fileNameFormat = "[机械动力] create-1.21.1-6.0.4"
        modStyle = "标题显示译名，详情显示文件名"
        ignoreQuilt = true
        showIcons = true
        iconStyle = "彩色图标"
        autoUpdate = true
        backgroundMusic = true
        soundEffects = true
        vibration = true
        notifications = true
        logLevel = "INFO"
        debugMode = false
        settingsRepo.resetToDefaults()
    }
}
