package com.verse.explorer.core.install

import com.verse.explorer.core.version.MinecraftVersion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class InstallState(
    val versionId: String = "",
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

interface VersionInstallOperation {
    suspend fun installVersion(version: MinecraftVersion, installName: String, loaderType: String?, loaderVersion: String?, progressCallback: (InstallProgress) -> Unit): Boolean
    fun cancel()
}

/** 应用拥有任务和结果；页面只订阅状态。重试必须等待前一个任务结束并完成清理。 */
class VersionInstallCoordinator(
    private val scope: CoroutineScope,
    private val snapshot: () -> InstallationSnapshot,
    private val factory: (InstallationSnapshot) -> VersionInstallOperation
) {
    private val mutableState = MutableStateFlow(InstallState())
    val state = mutableState.asStateFlow()
    private val mutableDownloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading = mutableDownloading.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private var job: Job? = null
    private var operation: VersionInstallOperation? = null

    @Synchronized fun start(version: MinecraftVersion, installName: String, loaderType: String?, loaderVersion: String?): Boolean {
        if (job != null) return false
        val config = snapshot()
        val plan = InstallationPlan.create(version.id, installName, loaderType, loaderVersion)
        val cleanup = InstallationCleanup(config.paths, plan)
        val lease = InstallationLease.acquire(listOf(config.paths.version(plan.installId), config.paths.version(plan.baseId)))
        if (lease == null) {
            mutableState.value = InstallState(version.id, plan.installId, isFailed = true, error = "版本目录正在被其他安装任务使用")
            return false
        }
        val installer = try { factory(config) } catch (failure: Throwable) { lease.close(); throw failure }
        operation = installer
        mutableState.value = InstallState(version.id, plan.installId, isInstalling = true)
        mutableDownloading.value = setOf(version.id)
        val task = scope.launch(start = CoroutineStart.LAZY) {
            var success = false
            val taskContext = currentCoroutineContext()
            try {
                success = installer.installVersion(version, plan.installId, loaderType, loaderVersion) { progress ->
                    if (taskContext.isActive) mutableState.value = InstallState(
                        versionId = version.id, installName = plan.installId, stage = progress.stage,
                        message = progress.message, progress = progress.progress, currentFile = progress.currentFile,
                        isInstalling = true, speed = progress.speed, downloaded = progress.downloaded, totalBytes = progress.totalBytes
                    )
                }
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(isInstalling = false, isCompleted = success, isFailed = !success,
                    stage = if (success) InstallStage.COMPLETED else InstallStage.FAILED,
                    error = if (success) null else it.message) }
            } catch (cancelled: CancellationException) {
                installer.cancel()
                mutableState.value = InstallState()
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(isInstalling = false, isFailed = true, stage = InstallStage.FAILED, error = failure.message) }
            } finally {
                if (!success) withContext(NonCancellable + Dispatchers.IO) { cleanup.afterFailure() }
            }
        }
        job = task
        task.invokeOnCompletion { failure ->
            synchronized(this) {
                if (job === task) {
                    lease.close()
                    if (failure is CancellationException) mutableState.value = InstallState()
                    mutableDownloading.value = emptySet()
                    operation = null
                    job = null
                    mutableRevision.update { it + 1 }
                }
            }
        }
        task.start()
        return true
    }

    @Synchronized fun cancel() { operation?.cancel(); job?.cancel() }
    @Synchronized fun reset() { if (job == null) mutableState.value = InstallState() }
}
