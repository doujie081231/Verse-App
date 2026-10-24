package com.verse.explorer.app.install

import com.verse.explorer.core.install.modpack.ModpackInstaller
import com.verse.explorer.core.install.modpack.ModpackInstallCancelledException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** 每个资源/导入入口一个任务所有者；任务和状态随应用存续，不捕获 Composable 或 Activity。 */
class ModpackInstallController(private val scope: CoroutineScope, private val installer: ModpackInstaller) {
    private val mutableState = MutableStateFlow(ModpackInstallUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun readModpack(file: File) = installer.readModpack(file)
    fun requiresNewName(gameDir: File, name: String) = installer.requiresNewName(gameDir, name)
    fun downloadFailed(title: String, error: String?) {
        mutableState.value = ModpackInstallUiState(visible = true, title = title,
            phase = if (error == "已取消") ModpackInstallPhase.CANCELLED else ModpackInstallPhase.FAILED,
            error = error ?: "整合包下载失败")
    }

    @Synchronized fun start(gameDir: File, file: File, name: String?, title: String, iconUrl: String? = null): Boolean {
        if (job != null) return false
        mutableState.value = ModpackInstallUiState(visible = true, title = title, iconUrl = iconUrl).withFixedStageRows()
        val task = scope.launch(start = CoroutineStart.LAZY) {
            val taskContext = currentCoroutineContext()
            try {
                val result = installer.installModpack(gameDir, file, name) { event ->
                    if (taskContext.isActive) mutableState.update { it.applyTaskEvent(event) }
                }
                taskContext.ensureActive()
                val failure = result.exceptionOrNull()
                mutableState.update { it.copy(
                    phase = when (failure) {
                        null -> ModpackInstallPhase.DONE
                        is ModpackInstallCancelledException, is CancellationException -> ModpackInstallPhase.CANCELLED
                        else -> ModpackInstallPhase.FAILED
                    }, error = failure?.message
                ) }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(phase = ModpackInstallPhase.CANCELLED) }
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(phase = ModpackInstallPhase.FAILED, error = failure.message) }
            }
        }
        job = task
        task.invokeOnCompletion { failure ->
            // 也覆盖尚未调度就被取消的任务；安装器返回后才能删除介质。
            file.delete()
            synchronized(this) {
                if (job === task) {
                    if (failure is CancellationException) mutableState.update { it.copy(phase = ModpackInstallPhase.CANCELLED) }
                    job = null
                }
            }
        }
        task.start()
        return true
    }

    @Synchronized fun cancel() { installer.cancel(); job?.cancel() }
    @Synchronized fun dismiss() { if (job == null) mutableState.update { it.copy(visible = false) } }
}
