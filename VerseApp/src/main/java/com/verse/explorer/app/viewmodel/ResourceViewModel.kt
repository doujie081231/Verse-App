package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.app.data.ModTranslationMapper
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.core.resource.ModrinthSearchHit
import com.verse.explorer.core.resource.ResourceRepository
import com.verse.explorer.core.resource.ResourceSearchResult
import kotlinx.coroutines.Job
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
    // 「自动」= 按当前实例的版本与加载器筛选（仅模组页默认开启）；
    // 「全部」= 不筛选
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

    // 「自动」只在模组页有意义（有 ModFilterResolver 提供实例版本/加载器）；
    // 整合包/材质包/光影页没有解析器，默认保持「全部」，避免下拉显示一个选不中的值
    private val _uiState = MutableStateFlow(
        ResourceUiState(
            selectedLoader = if (projectType == "mod") "自动" else "全部",
            selectedVersion = if (projectType == "mod") "自动" else "全部"
        )
    )
    val uiState: StateFlow<ResourceUiState> = _uiState.asStateFlow()

    private val _results = MutableStateFlow<List<ModrinthSearchHit>>(emptyList())
    val results: StateFlow<List<ModrinthSearchHit>> = _results.asStateFlow()

    private val pageSize = 15

    /**
     * 「自动」筛选使用的实例数据（ModFilterResolver 结果）。
     *
     * 必须声明在 init 之前：Kotlin 按声明顺序初始化字段，init 里的 search() 用
     * viewModelScope（Main.immediate）会同步跑到第一个挂起点，若这两个字段声明在
     * init 之后，doSearch 读到的就是字段默认值 null —— loaders = null 一路传到
     * repository.search，首个请求必崩（用户现象：首屏「搜索失败」，点重试才正常）。
     * 这里再显式标成可空，任何初始化顺序下都不会把 null 带下去。
     */
    private var autoMcVersion: String? = null
    private var autoLoaders: List<String>? = emptyList()

    /** 当前进行中的搜索任务：页面初始化 + 自动筛选几乎同时触发，取消旧任务避免重复请求 */
    private var searchJob: Job? = null

    init {
        ModTranslationMapper.init(application)
        search()
    }

    fun search(query: String = _uiState.value.searchQuery) {
        // 只保留最后一次搜索：初始化搜索与「自动」筛选刷新会同时触发，
        // 两个并发请求会重复打 API（Modrinth 限流时还会拿到错误响应）
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, searchQuery = query, currentPage = 0)
            try {
                val res = doSearch(query, 0)
                _results.value = res.hits
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    totalHits = res.total
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 被新搜索取消：保持沉默，交给新任务更新状态
                throw e
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

    /**
     * 设置「自动」筛选的实例数据并刷新（按当前实例的 MC 版本 + 已装加载器过滤）。
     * 仅在用户未手动改动筛选时自动刷新，改过的保持用户选择。
     */
    fun setAutoFilter(mcVersion: String?, loaders: List<String>?) {
        val safeLoaders = loaders ?: emptyList()
        if (autoMcVersion == mcVersion && autoLoaders == safeLoaders) return
        autoMcVersion = mcVersion
        autoLoaders = safeLoaders
        val state = _uiState.value
        if (state.selectedLoader == "自动" || state.selectedVersion == "自动") {
            search(state.searchQuery)
        }
    }

    private suspend fun doSearch(query: String, offset: Int): ResourceSearchResult {
        val state = _uiState.value
        val loaders = when {
            state.selectedLoader == "全部" -> emptyList()
            state.selectedLoader == "自动" -> autoLoaders ?: emptyList()
            else -> listOf(state.selectedLoader.lowercase())
        }
        val gameVersions = when {
            state.selectedVersion == "全部" -> emptyList()
            state.selectedVersion == "自动" -> listOfNotNull(autoMcVersion?.takeIf { it.isNotBlank() })
            else -> listOf(state.selectedVersion)
        }

        // 中文查询先经本地翻译表转换为英文关键词（资源 API 不支持中文检索）；
        // 未命中时仍按原查询请求，保持行为可预期
        val effectiveQuery = run {
            val raw = query.trim()
            if (raw.isNotEmpty() && raw.any { it in '\u4e00'..'\u9fff' }) {
                ModTranslationMapper.searchZh(raw).firstOrNull() ?: raw
            } else {
                raw
            }
        }

        return repository.search(
            query = effectiveQuery,
            projectType = projectType,
            loaders = loaders,
            gameVersions = gameVersions,
            sort = if (effectiveQuery.isEmpty()) "downloads" else "relevance",
            limit = pageSize,
            offset = offset,
            source = state.selectedSource
        )
    }

    companion object {
        private val instances = HashMap<String, ResourceViewModel>()

        /**
         * 按资源类型共享实例：模组/整合包/材质包/光影各自保留搜索结果与筛选条件，
         * 页签切换后不再重新请求列表，切回即显示已有内容。
         */
        fun shared(application: Application, projectType: String): ResourceViewModel =
            synchronized(instances) {
                instances.getOrPut(projectType) { ResourceViewModel(application, projectType) }
            }
    }
}