package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.core.download.DownloadManager
import com.verse.explorer.core.download.DownloadProgress
import com.verse.explorer.core.game.GameProfile
import com.verse.explorer.core.game.GameProfileManager
import com.verse.explorer.core.java.JavaManager
import com.verse.explorer.core.java.JavaRuntime
import com.verse.explorer.core.mod.ModLoaderManager
import com.verse.explorer.core.mod.ModLoaderType
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import com.verse.explorer.launcher.GameLauncher
import com.verse.explorer.launcher.LaunchConfig
import com.verse.explorer.launcher.LaunchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class MainUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedVersion: MinecraftVersion? = null,
    val selectedProfile: GameProfile? = null,
    val isLaunching: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val gameDir = File(application.getExternalFilesDir(null), "minecraft")
    private val versionManager = VersionManager(gameDir)
    private val downloadManager = DownloadManager()
    private val profileManager = GameProfileManager(gameDir)
    private val javaManager = JavaManager(File(application.getExternalFilesDir(null), "jre"))
    private val modLoaderManager = ModLoaderManager()
    private val gameLauncher = GameLauncher()
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private val _versions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val versions: StateFlow<List<MinecraftVersion>> = _versions.asStateFlow()
    
    private val _localVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val localVersions: StateFlow<List<MinecraftVersion>> = _localVersions.asStateFlow()
    
    private val _profiles = MutableStateFlow<List<GameProfile>>(emptyList())
    val profiles: StateFlow<List<GameProfile>> = _profiles.asStateFlow()
    
    private val _javaRuntimes = MutableStateFlow<List<JavaRuntime>>(emptyList())
    val javaRuntimes: StateFlow<List<JavaRuntime>> = _javaRuntimes.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()
    
    init {
        loadLocalVersions()
        loadProfiles()
        scanJavaRuntimes()
    }
    
    fun fetchRemoteVersions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val manifest = versionManager.fetchRemoteVersions()
                _versions.value = manifest.versions
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "获取版本列表失败: ${e.message}"
                )
            }
        }
    }
    
    fun loadLocalVersions() {
        viewModelScope.launch {
            val local = versionManager.getLocalVersions()
            _localVersions.value = local
            
            if (local.isNotEmpty() && _uiState.value.selectedVersion == null) {
                selectVersion(local.first())
            }
        }
    }
    
    fun loadProfiles() {
        val loadedProfiles = profileManager.loadProfiles()
        _profiles.value = loadedProfiles
        
        if (loadedProfiles.isNotEmpty() && _uiState.value.selectedProfile == null) {
            selectProfile(loadedProfiles.first())
        }
    }
    
    fun scanJavaRuntimes() {
        val runtimes = javaManager.scanInstalledRuntimes()
        _javaRuntimes.value = runtimes
    }
    
    fun selectVersion(version: MinecraftVersion) {
        _uiState.value = _uiState.value.copy(selectedVersion = version)
    }
    
    fun selectProfile(profile: GameProfile) {
        _uiState.value = _uiState.value.copy(selectedProfile = profile)
    }
    
    fun downloadVersion(version: MinecraftVersion) {
        viewModelScope.launch {
            versionManager.downloadVersion(version) { current, total ->
                _downloadProgress.value = DownloadProgress(
                    taskId = version.id,
                    fileName = version.id,
                    bytesDownloaded = current.toLong(),
                    totalBytes = total.toLong(),
                    speed = 0,
                    progress = if (total > 0) current.toFloat() / total.toFloat() else 0f,
                    status = com.verse.explorer.core.download.DownloadStatus.DOWNLOADING
                )
            }
            
            _downloadProgress.value = null
            loadLocalVersions()
        }
    }
    
    fun createProfile(name: String, versionId: String) {
        val profile = profileManager.createProfile(name, versionId)
        _profiles.value = _profiles.value + profile
    }
    
    fun deleteProfile(profileId: String) {
        profileManager.deleteProfile(profileId)
        _profiles.value = _profiles.value.filter { it.id != profileId }
    }
    
    fun launchGame(): LaunchResult? {
        val version = _uiState.value.selectedVersion ?: return null
        val profile = _uiState.value.selectedProfile ?: return null
        val javaRuntime = javaManager.getBestRuntime() ?: return null
        
        _uiState.value = _uiState.value.copy(isLaunching = true)
        
        val config = LaunchConfig(
            profile = profile,
            version = version,
            javaRuntime = javaRuntime,
            gameDir = gameDir,
            assetsDir = File(gameDir, "assets"),
            librariesDir = File(gameDir, "libraries"),
            nativesDir = File(gameDir, "natives")
        )
        
        val result = gameLauncher.launch(config)
        
        if (!result.success) {
            _uiState.value = _uiState.value.copy(
                isLaunching = false,
                error = result.error
            )
        }
        
        return result
    }
    
    fun stopGame() {
        gameLauncher.stop()
        _uiState.value = _uiState.value.copy(isLaunching = false)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
