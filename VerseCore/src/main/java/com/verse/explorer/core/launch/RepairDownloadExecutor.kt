package com.verse.explorer.core.launch

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import com.verse.explorer.core.util.HashUtils
import kotlinx.coroutines.ensureActive

/** 补全传输适配器，保留原来的 URLConnection 超时和重试参数。 */
internal class RepairDownloadExecutor {
    suspend fun downloadFileWithProgress(
        url: String,
        targetFile: File,
        expectedSha1: String?,
        expectedSize: Long,
        onProgress: (Float, String) -> Unit
    ) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        targetFile.parentFile?.mkdirs()

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 30000
            conn.readTimeout = 60000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${conn.responseCode}")
            }

            val totalSize = if (expectedSize > 0) expectedSize else conn.contentLengthLong
            var downloaded = 0L

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        if (totalSize > 0) {
                            val progress = downloaded.toFloat() / totalSize.toFloat()
                            val sizeStr = "${DependencyUtils.formatSize(downloaded)}/${DependencyUtils.formatSize(totalSize)}"
                            onProgress(progress, "下载中 $sizeStr")
                        }
                    }
                }
            }

            if (expectedSha1 != null) {
                val actualSha1 = HashUtils.sha1File(targetFile)
                if (actualSha1 != expectedSha1.lowercase()) {
                    targetFile.delete()
                    throw Exception("SHA1校验失败: ${targetFile.name}")
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun downloadFileWithRetry(
        url: String,
        targetFile: File,
        expectedSha1: String?,
        maxRetries: Int,
        onProgress: (Float, String) -> Unit
    ) {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                downloadFileWithProgress(url, targetFile, expectedSha1, 0L, onProgress)
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) kotlinx.coroutines.delay(1000L * attempt)
            }
        }
        throw lastException ?: Exception("Download failed after $maxRetries attempts")
    }

    suspend fun downloadFile(url: String, targetFile: File) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        targetFile.parentFile?.mkdirs()

        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${conn.responseCode}")
            }

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
