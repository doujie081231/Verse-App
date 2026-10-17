package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.core.resource.ModrinthSearchHit
import com.verse.explorer.core.resource.ResourceRepository
import com.verse.explorer.core.resource.ResourceSearchResult
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
    val selectedVersion: String = "全部",
    val selectedSource: String = ""  // "" = 全部, "modrinth", "curseforge"
)

class ResourceViewModel(
    application: Application,
    private val projectType: String
) : AndroidViewModel(application) {

    private val repository = ResourceRepository(
        application,
        useMirror = SettingsRepository.getDownloadSource() != "mojang"
    )

    private val _uiState = MutableStateFlow(ResourceUiState())
    val uiState: StateFlow<ResourceUiState> = _uiState.asStateFlow()

    private val _results = MutableStateFlow<List<ModrinthSearchHit>>(emptyList())
    val results: StateFlow<List<ModrinthSearchHit>> = _results.asStateFlow()

    private val pageSize = 15

    init {
        search()
    }

    fun search(query: String = _uiState.value.searchQuery) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, searchQuery = query, currentPage = 0)
            try {
                val res = doSearch(query, 0)
                _results.value = res.hits
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    totalHits = res.total
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun goToPage(page: Int) {
        val current = _uiState.value
        if (current.isLoading) return
        if (page < 0) return
        if (page * pageSize >= current.totalHits && current.totalHits > 0) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentPage = page)
            try {
                val res = doSearch(current.searchQuery, page * pageSize)
                _results.value = res.hits
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    totalHits = if (res.total > 0) res.total else current.totalHits
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message, currentPage = current.currentPage)
            }
        }
    }

    fun loadMore() {
        goToPage(_uiState.value.currentPage + 1)
    }

    fun setSelectedLoader(loader: String) {
        _uiState.value = _uiState.value.copy(selectedLoader = loader)
        search()
    }

    fun setSelectedVersion(version: String) {
        _uiState.value = _uiState.value.copy(selectedVersion = version)
        search()
    }

    fun setSelectedSource(source: String) {
        _uiState.value = _uiState.value.copy(selectedSource = source)
        search()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private suspend fun doSearch(query: String, offset: Int): ResourceSearchResult {
        val state = _uiState.value
        val loaders = if (state.selectedLoader != "全部") listOf(state.selectedLoader.lowercase()) else emptyList()
        val gameVersions = if (state.selectedVersion != "全部") listOf(state.selectedVersion) else emptyList()

        return repository.search(
            query = query,
            projectType = projectType,
            loaders = loaders,
            gameVersions = gameVersions,
            sort = if (query.isEmpty()) "downloads" else "relevance",
            limit = pageSize,
            offset = offset,
            source = state.selectedSource
        )
    }
}