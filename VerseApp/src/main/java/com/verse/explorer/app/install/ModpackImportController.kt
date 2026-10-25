package com.verse.explorer.app.install

import android.content.Context
import android.net.Uri
import com.verse.explorer.app.download.DownloadCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ModpackImportState(val file: File? = null, val title: String = "", val error: String? = null)

/** 复制和格式识别由应用拥有，结果可被重建后的页面继续消费。 */
class ModpackImportController(context: Context, private val installer: ModpackInstallController) {
    private val application = context.applicationContext
    private val mutableState = MutableStateFlow(ModpackImportState())
    val state = mutableState.asStateFlow()

    @Synchronized fun submit(uri: Uri) {
        if (DownloadCenter.findByKey("modpack_import") != null) return
        discard()
        DownloadCenter.submit(key = "modpack_import", title = "导入整合包", task = { progress, cancelled ->
            val file = copyImportToCache(application, uri, progress, cancelled)
            if (file == null) {
                mutableState.value = ModpackImportState(error = "无法读取整合包文件")
                false
            } else {
                val title = runCatching { installer.readModpack(file).name.trim() }.getOrDefault("")
                if (title.isBlank() || cancelled()) {
                    file.delete()
                    mutableState.value = ModpackImportState(error = "无法识别的整合包格式")
                    false
                } else {
                    mutableState.value = ModpackImportState(file, title)
                    true
                }
            }
        })
    }

    fun consumed() { mutableState.value = ModpackImportState() }
    fun discard() { mutableState.value.file?.delete(); consumed() }
}

/** 把 content:// 或 file:// Uri 复制到缓存目录（保留扩展名，供 ModpackInstaller 按 zip 读取；带进度上报） */
private fun copyImportToCache(
    context: Context,
    uri: Uri,
    onProgress: (downloaded: Long, total: Long, speed: Long) -> Unit,
    isCancelled: () -> Boolean
): File? {
    var partial: File? = null
    return try {
        val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "import.zip"
        val ext = displayName.substringAfterLast('.', "zip").takeIf { it.isNotBlank() } ?: "zip"
        val dest = File(context.cacheDir, "import_modpack_${System.currentTimeMillis()}.$ext")
        partial = dest
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { ins ->
            val total = try { ins.available().toLong() } catch (_: Exception) { -1L }
            var copied = 0L
            var lastReport = 0L
            dest.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (ins.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled()) { dest.delete(); return null }
                    output.write(buffer, 0, bytesRead)
                    copied += bytesRead
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 300) {
                        onProgress(copied, total, 0)
                        lastReport = now
                    }
                }
                onProgress(copied, copied, 0)
            }
        }
        if (!dest.exists() || dest.length() == 0L) {
            dest.delete()
            return null
        }
        dest
    } catch (e: Exception) {
        partial?.delete()
        com.verse.explorer.app.ui.VerseLogger.warn("复制导入整合包失败: ${e.message}", "ModpackImport")
        null
    }
}
