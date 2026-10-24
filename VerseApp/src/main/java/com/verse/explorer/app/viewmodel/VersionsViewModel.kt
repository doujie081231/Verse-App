package com.verse.explorer.app.viewmodel
import com.verse.explorer.app.appContainer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import com.verse.explorer.core.version.VersionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VersionsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,
    val searchQuery: String = ""
)

class VersionsViewModel(application: Application) : AndroidViewModel(application) {

    private val versionManager get() = VersionManager(GameConfig.getGameDir(getApplication()))
    private val installs = application.appContainer.versionInstalls

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

    val downloadingVersions = installs.downloading
    val installState = installs.state

    init {
        loadLocalVersions()
        fetchRemoteVersions()
        viewModelScope.launch { installs.revision.collect { loadLocalVersions() } }
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
        installs.start(version, installName, loaderType, loaderVersion)
    }

    fun cancelInstall() = installs.cancel()

    /**
     * 删除一个版本，返回是否成功。
     *
     * 只删用户选中的那一个版本，**不再连带删除它继承的基础版本**：
     * base（如 26.3）本身是可独立游玩的版本，而且连带删掉后再安装加载器版本时
     * 会被重新创建并重新下载 40MB 客户端 jar —— 用户看到的现象就是
     * 「删掉的版本又回来了」，还白等一次下载。
     */
    fun deleteVersion(versionId: String): Boolean {
        val ok = versionManager.removeVersionFromDisk(versionId)
        loadLocalVersions()
        return ok
    }


    fun resetInstallState() {
        installs.reset()
    }

    fun versionIdConflicts(name: String): Boolean {
        return versionManager.versionIdConflicts(name)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

}
