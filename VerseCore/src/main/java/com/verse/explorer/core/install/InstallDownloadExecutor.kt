package com.verse.explorer.core.install

import java.io.File
import okhttp3.OkHttpClient
import kotlinx.coroutines.ensureActive

/** 安装文件传输；客户端和超时由调用方提供，镜像选择仍由原有策略负责。 */
internal class InstallDownloadExecutor(
    private val httpClient: OkHttpClient,
    private val fastHttpClient: OkHttpClient,
    private val checkCancelled: () -> Unit
) {
    suspend fun downloadFileWithRetry(
        url: String,
        targetFile: File,
        expectedSha1: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> },
        maxRetries: Int = 3
    ) {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                downloadFileWithProgress(url, targetFile, expectedSha1, onProgress)
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: GameInstaller.CancelledInstallException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) kotlinx.coroutines.delay(1000L * attempt)
            }
        }
        throw lastException ?: Exception("Download failed after $maxRetries attempts")
    }

    suspend fun downloadFileWithProgress(
        url: String,
        targetFile: File,
        expectedSha1: String?,
        onProgress: (Float, String) -> Unit
    ) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        checkCancelled()
        targetFile.parentFile?.mkdirs()

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        val request = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "VerseExplorerX/1.0")
            .header("Accept", "*/*")
            .build()

        var totalSize = -1L
        var downloaded = 0L

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")

                val body = response.body ?: throw Exception("Empty body: $url")
                totalSize = body.contentLength()

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            checkCancelled()
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalSize > 0) {
                                val progress = downloaded.toFloat() / totalSize.toFloat()
                                val sizeStr = InstallFileUtils.formatSize(downloaded) + "/" + InstallFileUtils.formatSize(totalSize)
                                onProgress(progress, "下载中 $sizeStr")
                            }
                        }
                    }
                }
            }

            if (totalSize >= 0 && downloaded != totalSize) {
                tempFile.delete()
                throw Exception("文件大小不匹配: 已下载 $downloaded, 期望 $totalSize")
            }

            if (expectedSha1 != null) {
                val actualSha1 = InstallFileUtils.calculateSHA1(tempFile)
                if (actualSha1.lowercase() != expectedSha1.lowercase()) {
                    tempFile.delete()
                    throw Exception("SHA1 校验失败 ${targetFile.name}: 期望=$expectedSha1, 实际=$actualSha1")
                }
            }

            targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                // renameTo 可能因跨挂载点失败，用拷贝回退
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            // 下载失败时删除临时文件，避免残留
            tempFile.delete()
            throw e
        }
    }

    suspend fun downloadFile(url: String, targetFile: File) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        checkCancelled()
        targetFile.parentFile?.mkdirs()

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        val request = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "VerseExplorerX/1.0")
            .header("Accept", "*/*")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")
                val body = response.body ?: throw Exception("Empty body: $url")
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            checkCancelled()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        } }
                }
            }
            targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    suspend fun downloadAssetFile(
        url: String,
        targetFile: File,
        onBytes: (Long, Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        checkCancelled()
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val request = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "VerseExplorerX/1.0")
            .header("Accept", "*/*")
            .build()
        try {
            fastHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")
                val body = response.body ?: throw Exception("Empty body: $url")
                val totalBytes = body.contentLength().coerceAtLeast(-1L)
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded = 0L
                        var lastReport = System.currentTimeMillis()
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (isCancelled()) throw GameInstaller.CancelledInstallException()
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            checkCancelled()
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 300) {
                                onBytes(downloaded, totalBytes)
                                lastReport = now
                            }
                        }
                        onBytes(downloaded, totalBytes)
                    }
                }
            }
            targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

}
