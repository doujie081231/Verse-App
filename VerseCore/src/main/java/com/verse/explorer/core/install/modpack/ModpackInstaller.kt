package com.verse.explorer.core.install.modpack

import kotlinx.coroutines.CancellationException
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference

enum class ModpackTaskKind { RUNNING, DONE, FAILED }

/** 阶段进度；rowDone 表示整个阶段完成，而非其中某个并行文件完成。 */
data class ModpackTaskEvent(
    val key: String,
    val label: String,
    val name: String = "",
    val progress: Float = 0f,
    val message: String = "",
    val kind: ModpackTaskKind,
    val countDone: Int = -1,
    val countTotal: Int = -1,
    val rowDone: Boolean = false
)

data class ModpackInfo(val name: String)
class ModpackInstallCancelledException : Exception("整合包安装已取消")

interface ModpackInstallOperation {
    suspend fun install(gameDir: File, zipFile: File, name: String?, onEvent: (ModpackTaskEvent) -> Unit): Result<Unit>
    fun cancel()
}

interface ModpackInstallBackend {
    fun readModpack(zipFile: File, charset: Charset = Charsets.UTF_8): ModpackInfo
    fun newOperation(): ModpackInstallOperation
}

/** 一条安装流程的所有者；不同页面不共享取消标记和进度监听集合。 */
class ModpackInstaller(private val backend: ModpackInstallBackend) {
    private val active = AtomicReference<ModpackInstallOperation?>(null)

    fun readModpack(zipFile: File, charset: Charset = Charsets.UTF_8): ModpackInfo =
        backend.readModpack(zipFile, charset)

    suspend fun installModpack(
        gameDir: File,
        zipFile: File,
        name: String? = null,
        onEvent: (ModpackTaskEvent) -> Unit
    ): Result<Unit> {
        val operation = backend.newOperation()
        if (!active.compareAndSet(null, operation)) return Result.failure(IllegalStateException("安装任务已在运行"))
        return try {
            operation.install(gameDir, zipFile, name, onEvent).also { result ->
                val failure = result.exceptionOrNull()
                if (failure is CancellationException) throw failure
            }
        } catch (cancelled: CancellationException) {
            operation.cancel()
            throw cancelled
        } finally {
            active.compareAndSet(operation, null)
        }
    }

    fun cancel() {
        active.getAndSet(null)?.cancel()
    }
}
