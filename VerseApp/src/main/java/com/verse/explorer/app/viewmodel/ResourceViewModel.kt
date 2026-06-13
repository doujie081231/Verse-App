package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.core.resource.ModrinthApi
import com.verse.explorer.core.resource.ModrinthSearchHit
import com.verse.explorer.core.resource.ModrinthSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResourceUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val currentPage: Int = 0,
    val totalHits: Int = 0,
    val selectedLoader: String = "全部",
    val selectedVersion: String = "全部"
)

class ResourceViewModel(
    application: Application,
    private val projectType: String
) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val useMirror = settingsRepo.getDownloadSource() != "mojang"

    private val _uiState = MutableStateFlow(ResourceUiState())
    val uiState: StateFlow<ResourceUiState> = _uiState.asStateFlow()

    private val _results = MutableStateFlow<List<ModrinthSearchHit>>(emptyList())
    val results: StateFlow<List<ModrinthSearchHit>> = _results.asStateFlow()

    private val pageSize = 20

    init {
        search()
    }

    fun search(query: String = _uiState.value.searchQuery) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, searchQuery = query, currentPage = 0)
            try {
                val result = doSearch(query, 0)
                _results.value = result.hits
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    totalHits = result.totalHits,
                    currentPage = 0
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading) return
        val nextOffset = (current.currentPage + 1) * pageSize
        if (nextOffset >= current.totalHits) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = doSearch(current.searchQuery, nextOffset)
                _results.value = _results.value + result.hits
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    totalHits = result.totalHits,
                    currentPage = current.currentPage + 1
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun setSelectedLoader(loader: String) {
        _uiState.value = _uiState.value.copy(selectedLoader = loader)
        search()
    }

    fun setSelectedVersion(version: String) {
        _uiState.value = _uiState.value.copy(selectedVersion = version)
        search()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private suspend fun doSearch(query: String, offset: Int): ModrinthSearchResult {
        val state = _uiState.value
        val loaders = if (state.selectedLoader != "全部") listOf(state.selectedLoader.lowercase()) else emptyList()
        val gameVersions = if (state.selectedVersion != "全部") listOf(state.selectedVersion) else emptyList()

        return ModrinthApi.searchProjects(
            query = query,
            projectType = projectType,
            loaders = loaders,
            gameVersions = gameVersions,
            sort = if (query.isEmpty()) "downloads" else "relevance",
            limit = pageSize,
            offset = offset,
            useMirror = useMirror
        )
    }
}
