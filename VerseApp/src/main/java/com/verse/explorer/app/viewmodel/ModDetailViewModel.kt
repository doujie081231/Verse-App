package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.core.resource.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ModDetailUiState(
    val project: ResourceDetail? = null,
    val versions: List<ResourceVersion> = emptyList(),
    val versionsError: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val dependencies: Map<String, ResourceDetail> = emptyMap()
)

/** 页面只发送加载事件和展示状态；加载工作受 ViewModelStore 生命周期管理。 */
class ModDetailViewModel(application: Application, private val projectId: String, private val source: String) : AndroidViewModel(application) {
    private val repository = ResourceRepository(application)
    private val mutableState = MutableStateFlow(ModDetailUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    init { reload() }

    fun reload() {
        job?.cancel()
        mutableState.value = ModDetailUiState()
        job = viewModelScope.launch {
            if (projectId.isBlank()) {
                mutableState.value = ModDetailUiState(isLoading = false, error = "项目ID为空")
                return@launch
            }
            try {
                val project = repository.getDetail(projectId, source)
                mutableState.update { it.copy(project = project) }
                val versions = try {
                    repository.getVersions(projectId, source)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    mutableState.update { it.copy(versionsError = "版本列表加载失败: ${failure.message}") }
                    emptyList()
                }
                mutableState.update { it.copy(versions = versions, isLoading = false) }
                val details = mutableMapOf<String, ResourceDetail>()
                versions.flatMap { it.dependencies }.distinctBy { it.projectId }.filter { it.projectId.isNotBlank() }.forEach { dependency ->
                    try { details[dependency.projectId] = repository.getDetail(dependency.projectId, dependency.source) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* 可选依赖说明加载失败不阻断详情。 */ }
                }
                mutableState.update { it.copy(dependencies = details) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(isLoading = false, error = "加载失败: ${failure.message ?: "未知错误"}") }
            }
        }
    }
}
