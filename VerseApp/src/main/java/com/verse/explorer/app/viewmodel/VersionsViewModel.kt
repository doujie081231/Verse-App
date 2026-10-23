package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.install.GameInstaller
import com.verse.explorer.core.install.InstallProgress
import com.verse.explorer.core.install.InstallStage
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import com.verse.explorer.core.version.VersionManifest
import com.verse.explorer.core.version.VersionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

data class VersionsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,
    val searchQuery: String = ""
)

data class InstallState(
    val versionId: String = "",
    /** 实际安装的版本目录名（命名弹窗自定义，默认等于 versionId） */
    val installName: String = "",
    val stage: InstallStage = InstallStage.PREPARING,
    val message: String = "",
    val progress: Float = 0f,
    val currentFile: String = "",
    val isInstalling: Boolean = false,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val error: String? = null,
    val speed: Long = 0L,
    val downloaded: Long = 0L,
    val totalBytes: Long = 0L
)

class VersionsViewModel(application: Application) : AndroidViewModel(application) {

    private val gameDir = GameConfig.getGameDir(application)
    private val versionManager = VersionManager(gameDir)
    private val gameInstaller = GameInstaller(application)

    private val _uiState = MutableStateFlow(VersionsUiState())
    val uiState: StateFlow<VersionsUiState> = _uiState.asStateFlow()

    private val _remoteVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val remoteVersions: StateFlow<List<MinecraftVersion>> = _remoteVersions.asStateFlow()

    private val _localVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val localVersions: StateFlow<List<MinecraftVersion>> = _localVersions.asStateFlow()

    private val _releaseVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val releaseVersions: StateFlow<List<MinecraftVersion>> = _releaseVersions.asStateFlow()

    private val _snapshotVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val snapshotVersions: StateFlow<List<MinecraftVersion>> = _snapshotVersions.asStateFlow()

    private val _specialVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val specialVersions: StateFlow<List<MinecraftVersion>> = _specialVersions.asStateFlow()

    private val _oldVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val oldVersions: StateFlow<List<MinecraftVersion>> = _oldVersions.asStateFlow()

    private val _downloadingVersions = MutableStateFlow<Set<String>>(emptySet())
    val downloadingVersions: StateFlow<Set<String>> = _downloadingVersions.asStateFlow()

    private var installJob: Job? = null

    /** 本次安装开始前基础原版目录（versions/<version.id>）是否已存在——决定取消/失败时是否清理它 */
    private var installBaseDirExisted = false

    private val _installState = MutableStateFlow(InstallState())
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    init {
        loadLocalVersions()
        fetchRemoteVersions()
    }

    fun fetchRemoteVersions(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val manifest = versionManager.fetchRemoteVersions(forceRefresh)
                _remoteVersions.value = manifest.versions

                val categorized = manifest.versions.map {
                    if (isAprilFoolsId(it.id)) it.copy(type = VersionType.SPECIAL) else it
                }
                _releaseVersions.value = categorized.filter {
                    it.type == VersionType.RELEASE
                }
                _snapshotVersions.value = categorized.filter {
                    it.type == VersionType.SNAPSHOT
                }
                _specialVersions.value = categorized.filter {
                    it.type == VersionType.SPECIAL
                }
                _oldVersions.value = categorized.filter {
                    it.type == VersionType.OLD_ALPHA || it.type == VersionType.OLD_BETA
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "获取版本列表失败: ${e.message}"
                )
            }
        }
    }

    private fun isAprilFoolsId(id: String): Boolean {
        return when (id) {
            "26.1.1", "26w14a", "25w14craftmine", "24w14potato",
            "23w13a_or_b", "22w13oneblockatatime", "20w14infinite",
            "3D Shareware v1.34", "15w14a", "1.RV-Pre1" -> true
            else -> false
        }
    }

    fun loadLocalVersions() {
        val local = versionManager.getLocalVersions()
        _localVersions.value = local
    }

    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun installVersion(
        version: MinecraftVersion,
        installName: String = version.id,
        loaderType: String? = null,
        loaderVersion: String? = null
    ) {
        gameInstaller.cancelled = false
        installJob = viewModelScope.launch {
            _downloadingVersions.value = _downloadingVersions.value + version.id
            _installState.value = InstallState(
                versionId = version.id,
                installName = installName,
                isInstalling = true
            )
            // 记录安装前基础目录是否已存在：失败/取消时只清理本次新建的基础目录，
            // 复用的既有基础版本不能删（否则其他实例的 inheritsFrom 悬空）。
            val baseDirExisted = File(GameConfig.getVersionsDir(gameDir), version.id).exists()
            installBaseDirExisted = baseDirExisted

            fun cleanupFailedInstall() {
                cleanupVersion(installName.ifBlank { version.id })
                if (!baseDirExisted) cleanupVersion(version.id)
            }

            try {
                val success = gameInstaller.installVersion(version, installName, loaderType, loaderVersion) { progress ->
                    // 取消/关闭后忽略后台残留进度回调，避免弹窗二次弹出（一次性取消）
                    if (installJob?.isActive == true) {
                        _installState.value = InstallState(
                            versionId = version.id,
                            installName = installName,
                            stage = progress.stage,
                            message = progress.message,
                            progress = progress.progress,
                            currentFile = progress.currentFile,
                            isInstalling = progress.stage != InstallStage.COMPLETED && progress.stage != InstallStage.FAILED,
                            isCompleted = progress.stage == InstallStage.COMPLETED,
                            isFailed = progress.stage == InstallStage.FAILED,
                            error = if (progress.stage == InstallStage.FAILED) progress.message else null,
                            speed = progress.speed,
                            downloaded = progress.downloaded,
                            totalBytes = progress.totalBytes
                        )
                    }
                }

                if (success) {
                    loadLocalVersions()
                    _installState.value = InstallState(
                        versionId = version.id,
                        installName = installName,
                        isInstalling = false,
                        isCompleted = true
                    )
                } else {
                    // GameInstaller 内部失败走 FAILED 进度返回 false（不抛异常），同样要清理
                    cleanupFailedInstall()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                cleanupFailedInstall()
                _installState.value = InstallState()
                throw e
            } catch (e: com.verse.explorer.core.install.GameInstaller.CancelledInstallException) {
                cleanupFailedInstall()
                _installState.value = InstallState(
                    versionId = version.id,
                    installName = installName,
                    isInstalling = false,
                    message = "已取消下载"
                )
            } catch (e: Exception) {
                _installState.value = InstallState(
                    versionId = version.id,
                    isInstalling = false,
                    isFailed = true,
                    error = e.message
                )
            } finally {
                _downloadingVersions.value = _downloadingVersions.value - version.id
            }
        }
    }

    fun cancelInstall() {
        gameInstaller.cancel()
        installJob?.cancel()
        installJob = null
        val currentState = _installState.value
        if (currentState.versionId.isNotEmpty()) {
            // 删除实际安装目录（命名弹窗改名后 ≠ versionId）；
            // 基础原版目录仅在本次安装新建时一并删除（复用的既有基础不能删）
            cleanupVersion(currentState.installName.ifBlank { currentState.versionId })
            if (!installBaseDirExisted) cleanupVersion(currentState.versionId)
            _installState.value = InstallState()
            _downloadingVersions.value = _downloadingVersions.value - currentState.versionId
        }
    }

    private fun cleanupVersion(versionId: String) {
        val versionDir = File(GameConfig.getVersionsDir(gameDir), versionId)
        if (versionDir.exists()) {
            versionDir.deleteRecursively()
        }
    }

    fun downloadVersion(version: MinecraftVersion, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _downloadingVersions.value = _downloadingVersions.value + version.id

            try {
                versionManager.downloadVersion(version)
                loadLocalVersions()
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            } finally {
                _downloadingVersions.value = _downloadingVersions.value - version.id
            }
        }
    }

    fun deleteVersion(versionId: String) {
        cleanupVersionChain(versionId)
        loadLocalVersions()
    }

    /**
     * PC端逻辑: 追踪 inheritsFrom 链，只删除没有其他子版本的父版本
     */
    private fun cleanupVersionChain(versionId: String) {
        val toDelete = mutableListOf(versionId)
        val versionsDir = GameConfig.getVersionsDir(gameDir)

        // 读取要删除版本的JSON，追踪inheritsFrom链
        val dvJsonPath = File(versionsDir, "$versionId/$versionId.json")
        if (dvJsonPath.exists()) {
            try {
                val gson = com.google.gson.Gson()
                val dvJson = gson.fromJson(dvJsonPath.readText(), com.google.gson.JsonObject::class.java)
                var currentId: String? = dvJson.get("inheritsFrom")?.asString
                while (currentId != null) {
                    if (toDelete.contains(currentId)) break
                    var hasOtherChildren = false
                    // 检查除toDelete外还有没有其他版本继承自currentId
                    try {
                        val allDirs = versionsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
                        for (d in allDirs) {
                            if (toDelete.contains(d.name)) continue
                            val dJsonFile = File(d, "${d.name}.json")
                            if (dJsonFile.exists()) {
                                try {
                                    val dJson = gson.fromJson(dJsonFile.readText(), com.google.gson.JsonObject::class.java)
                                    if (dJson.get("inheritsFrom")?.asString == currentId) {
                                        hasOtherChildren = true
                                        break
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                    if (hasOtherChildren) break
                    toDelete.add(currentId!!)
                    val pJsonPath = File(versionsDir, "$currentId/$currentId.json")
                    if (pJsonPath.exists()) {
                        try {
                            val pJson = gson.fromJson(pJsonPath.readText(), com.google.gson.JsonObject::class.java)
                            currentId = pJson.get("inheritsFrom")?.asString?.takeIf { it.isNotEmpty() }
                        } catch (_: Exception) { currentId = null }
                    } else { currentId = null }
                }
            } catch (_: Exception) {}
        }

        for (id in toDelete) {
            try {
                val dir = File(versionsDir, id)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            } catch (_: Exception) {}
        }
    }

    fun resetInstallState() {
        _installState.value = InstallState()
    }

    fun versionIdConflicts(name: String): Boolean {
        return versionManager.versionIdConflicts(name)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        @Volatile
        private var cached: VersionsViewModel? = null

        /**
         * 应用级共享实例：页签切换会销毁整页组合，
         * 若每次重建都新建状态容器，已加载的版本列表会丢失并重新联网加载，
         * 快速来回切换时表现为反复转圈、卡顿。共享同一实例后切回页面直接复用已有数据。
         */
        fun shared(application: Application): VersionsViewModel =
            cached ?: synchronized(this) {
                cached ?: VersionsViewModel(application).also { cached = it }
            }
    }
}
