package com.verse.explorer.core.java

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "JavaRuntimeManager"

internal data class JREDownloadSource(
    val name: String,
    val baseUrl: String,
    val priority: Int
)

internal object JreDownloader {

    private val JRE_DOWNLOAD_SOURCES = listOf(
        JREDownloadSource(
            name = "BMCLAPI",
            baseUrl = "https://bmclapi2.bangbang93.com/fcl/src/main/assets/app_runtime/java",
            priority = 0
        ),
        JREDownloadSource(
            name = "GitHub",
            baseUrl = "https://raw.githubusercontent.com/FCL-Team/FoldCraftLauncher/main/FCL/src/main/assets/app_runtime/java",
            priority = 1
        )
    )

    fun downloadJreFromNetwork(
        context: Context,
        javaVersion: String,
        arch: String,
        targetDir: File,
        onProgress: (JavaInstallProgress) -> Unit
    ): Boolean {
        val versionNum = JavaRuntimeManager.JRE_VERSIONS[javaVersion] ?: 17
        val fclArch = JavaRuntimeManager.getFclArchName()

        val tempDir = File(context.cacheDir, "jre_download_$versionNum")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        val extractDir = File(tempDir, "extracted")
        extractDir.mkdirs()

        try {
            val universalFile = File(tempDir, "universal.tar.xz")
            val archFile = File(tempDir, "bin-$fclArch.tar.xz")

            var downloadedUniversal = false
            var downloadedArch = false

            for (source in JRE_DOWNLOAD_SOURCES.sortedBy { it.priority }) {
                if (!downloadedUniversal) {
                    val universalUrl = "${source.baseUrl}/jre$versionNum/universal.tar.xz"
                    onProgress(JavaInstallProgress(
                        JavaInstallStatus.DOWNLOADING, 10,
                        "从 ${source.name} 下载 Java $versionNum 通用文件..."
                    ))

                    if (downloadFile(universalUrl, universalFile)) {
                        if (universalFile.length() > 10000) {
                            downloadedUniversal = true
                            Log.i(TAG, "Downloaded universal.tar.xz from ${source.name}")
                        }
                    }
                }

                if (!downloadedArch) {
                    val archUrl = "${source.baseUrl}/jre$versionNum/bin-$fclArch.tar.xz"
                    onProgress(JavaInstallProgress(
                        JavaInstallStatus.DOWNLOADING, 30,
                        "从 ${source.name} 下载 Java $versionNum 架构文件..."
                    ))

                    if (downloadFile(archUrl, archFile)) {
                        if (archFile.length() > 10000) {
                            downloadedArch = true
                            Log.i(TAG, "Downloaded bin-$fclArch.tar.xz from ${source.name}")
                        }
                    }
                }

                if (downloadedUniversal && downloadedArch) break
            }

            if (!downloadedUniversal || !downloadedArch) {
                Log.e(TAG, "Failed to download JRE files: universal=$downloadedUniversal, arch=$downloadedArch")
                tempDir.deleteRecursively()
                return false
            }

            onProgress(JavaInstallProgress(
                JavaInstallStatus.EXTRACTING, 50,
                "正在解压 Java $versionNum..."
            ))

            // 直接把两个 tar.xz 解压到目标目录（顶层即标准 JRE 结构），
            // 不再使用 findJreRootDir/moveDirReliably 等自研猜测逻辑。
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            JreExtractor.extractTarXz(universalFile, targetDir) { current, total ->
                val progress = 50 + (if (total > 0) ((current * 20) / total).toInt() else 0)
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压通用文件... ($current/$total)"
                ))
            }

            JreExtractor.extractTarXz(archFile, targetDir) { current, total ->
                val progress = 70 + (if (total > 0) ((current * 15) / total).toInt() else 0)
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压架构文件... ($current/$total)"
                ))
            }

            tempDir.deleteRecursively()

            onProgress(JavaInstallProgress(
                JavaInstallStatus.PATCHING, 90,
                "正在修补 Java 运行时..."
            ))

            JrePatcher.patchJava(context, targetDir)

            val releaseFile = File(targetDir, "release")
            if (!releaseFile.exists()) {
                try {
                    releaseFile.writeText("JAVA_VERSION=\"$versionNum\"\n")
                } catch (_: Exception) {}
            }

            if (!JreValidator.verifyJreInstallation(targetDir)) {
                Log.e(TAG, "JRE verification failed after network download")
                targetDir.deleteRecursively()
                return false
            }

            JreExtractor.setExecutableRecursive(File(targetDir, "bin"))
            Log.i(TAG, "JRE $versionNum installed successfully from network")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download JRE from network: ${e.message}")
            tempDir.deleteRecursively()
            return false
        }
    }

    fun downloadFile(url: String, outputFile: File): Boolean {
        return try {
            // GitHub 直连不可达网络下走 ghp.ci 代理（与全站 GitHub 下载约定一致）
            val targetUrl = com.verse.explorer.core.config.GameConfig.replaceGitHubUrl(url)
            val connection = URL(targetUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "VerseExplorer/1.0")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Download failed: ${connection.responseCode} for $targetUrl")
                connection.disconnect()
                return false
            }

            connection.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            connection.disconnect()
            outputFile.length() > 10000
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $url: ${e.message}")
            false
        }
    }
}
