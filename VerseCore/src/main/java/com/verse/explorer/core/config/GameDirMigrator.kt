package com.verse.explorer.core.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 游戏目录迁移：把现有目录整份复制到新位置，供用户把数据从应用私有目录搬到
 * 文件管理器可见的 `Android/data/<包名>/files/VerseExplorerX`。
 *
 * 几条硬约束：
 *  - **不删除源目录**：复制完成后由用户自行确认删除，任何中途失败都不会丢数据；
 *  - 目标已存在同名同大小文件时跳过，支持中断后重跑（手机迁移几十 GB 经常被打断）；
 *  - 连续进度回调带节流，避免几十万次小文件回调把主线程刷爆。
 */
object GameDirMigrator {

    data class Progress(
        val copiedFiles: Int,
        val totalFiles: Int,
        val copiedBytes: Long,
        val totalBytes: Long,
        val currentName: String
    ) {
        val percent: Int
            get() = when {
                totalBytes > 0 -> ((copiedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                totalFiles > 0 -> (copiedFiles * 100 / totalFiles).coerceIn(0, 100)
                else -> 0
            }
    }

    data class Result(val copiedFiles: Int, val copiedBytes: Long, val skippedFiles: Int)

    suspend fun migrate(
        from: File,
        to: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {}
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            require(from.isDirectory) { "当前目录不存在：${from.absolutePath}" }
            if (runCatching { from.canonicalPath == to.canonicalPath }.getOrDefault(false)) {
                return@runCatching Result(0, 0L, 0)
            }
            to.mkdirs()

            var totalFiles = 0
            var totalBytes = 0L
            from.walkTopDown().forEach { file ->
                if (file.isFile) {
                    totalFiles++
                    totalBytes += file.length()
                }
            }

            var copiedFiles = 0
            var copiedBytes = 0L
            var skipped = 0
            var lastEmit = 0L

            from.walkTopDown().forEach { src ->
                if (isCancelled()) throw IllegalStateException("迁移已取消")
                val relative = src.relativeTo(from).path
                if (src.isDirectory) {
                    File(to, relative).mkdirs()
                    return@forEach
                }
                val dest = File(to, relative)
                dest.parentFile?.mkdirs()
                if (dest.isFile && dest.length() == src.length()) {
                    skipped++
                } else {
                    src.copyTo(dest, overwrite = true)
                    copiedFiles++
                    copiedBytes += src.length()
                }
                val now = System.currentTimeMillis()
                if (now - lastEmit >= 120) {
                    lastEmit = now
                    onProgress(Progress(copiedFiles, totalFiles, copiedBytes, totalBytes, src.name))
                }
            }

            onProgress(Progress(copiedFiles, totalFiles, copiedBytes, totalBytes, ""))
            Result(copiedFiles, copiedBytes, skipped)
        }
    }
}
