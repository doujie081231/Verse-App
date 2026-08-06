package com.verse.explorer.core.java

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

private const val TAG = "JavaRuntimeManager"

internal object JreInstaller {

    suspend fun installJre(
        context: Context,
        javaVersion: String = "java17",
        onProgress: (JavaInstallProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val arch = JavaRuntimeManager.getArchName()
            val jreDir = JavaRuntimeManager.getJreDir(context)
            if (!jreDir.exists()) jreDir.mkdirs()

            val versionNum = JavaRuntimeManager.JRE_VERSIONS[javaVersion] ?: 17
            val targetDir = File(jreDir, "jre-$versionNum-$arch")

            if (targetDir.exists()) {
                val javaBin = File(targetDir, "bin/java")
                val javaSh = File(targetDir, "bin/java.sh")
                if ((javaBin.exists() || javaSh.exists()) && JreValidator.isAndroidCompatibleJre(targetDir)) {
                    onProgress(JavaInstallProgress(
                        JavaInstallStatus.COMPLETED, 100, "Java运行时已安装"
                    ))
                    return@withContext Result.success(targetDir)
                } else {
                    onProgress(JavaInstallProgress(
                        JavaInstallStatus.PENDING, 0,
                        "检测到不兼容的JRE，正在替换..."
                    ))
                    targetDir.deleteRecursively()
                }
            }

            val allJreDirs = jreDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            for (oldDir in allJreDirs) {
                val dirName = oldDir.name.lowercase()
                if (dirName.contains("jre$versionNum") || dirName.contains("java$versionNum") ||
                    dirName.contains("$versionNum")) {
                    if (!JreValidator.isAndroidCompatibleJre(oldDir)) {
                        onProgress(JavaInstallProgress(
                            JavaInstallStatus.PENDING, 0,
                            "正在替换不兼容的JRE: ${oldDir.name}..."
                        ))
                        oldDir.deleteRecursively()
                    }
                }
            }

            val fclResult = extractFclJreFromAssets(context, javaVersion, arch, targetDir, onProgress)
            if (fclResult) {
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.COMPLETED, 100,
                    "Java $versionNum 运行时安装完成！"
                ))
                return@withContext Result.success(targetDir)
            }

            val pojavResult = extractPojavJreFromAssets(context, javaVersion, arch, targetDir, onProgress)
            if (pojavResult) {
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.COMPLETED, 100,
                    "Java $versionNum 运行时安装完成！"
                ))
                return@withContext Result.success(targetDir)
            }

            onProgress(JavaInstallProgress(
                JavaInstallStatus.DOWNLOADING, 0,
                "正在从网络下载 Java $versionNum..."
            ))

            val downloadResult = JreDownloader.downloadJreFromNetwork(context, javaVersion, arch, targetDir, onProgress)
            if (downloadResult) {
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.COMPLETED, 100,
                    "Java $versionNum 运行时安装完成！"
                ))
                return@withContext Result.success(targetDir)
            }

            onProgress(JavaInstallProgress(
                JavaInstallStatus.FAILED, 0,
                "安装失败: Java $versionNum 运行时无法获取"
            ))
            Result.failure(Exception("Java $versionNum 运行时无法获取"))

        } catch (e: Exception) {
            onProgress(JavaInstallProgress(
                JavaInstallStatus.FAILED, 0, "安装失败: ${e.message}"
            ))
            Result.failure(e)
        }
    }

    fun extractFclJreFromAssets(
        context: Context,
        javaVersion: String,
        arch: String,
        targetDir: File,
        onProgress: (JavaInstallProgress) -> Unit
    ): Boolean {
        val versionNum = JavaRuntimeManager.JRE_VERSIONS[javaVersion] ?: 17
        val fclArch = JavaRuntimeManager.getFclArchName()

        val universalPath = "app_runtime/java/jre$versionNum/universal.tar.xz"
        val archPath = "app_runtime/java/jre$versionNum/bin-$fclArch.tar.xz"

        val universalStream: InputStream?

        try {
            universalStream = context.assets.open(universalPath)
        } catch (_: Exception) {
            return false
        }

        return try {
            onProgress(JavaInstallProgress(
                JavaInstallStatus.EXTRACTING, 5,
                "正在解压 Java $versionNum 通用文件..."
            ))

            val tempDir = File(context.cacheDir, "fcl_jre_$versionNum")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            val extractDir = File(tempDir, "extracted")
            extractDir.mkdirs()

            val universalFile = File(tempDir, "universal.tar.xz")
            universalFile.outputStream().use { output -> universalStream.copyTo(output) }
            universalStream.close()

            if (universalFile.length() < 1024) {
                tempDir.deleteRecursively()
                return false
            }

            JreExtractor.extractTarXz(universalFile, extractDir) { current, total ->
                val progress = 5 + (if (total > 0) ((current * 40) / total).toInt() else 0)
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压通用文件... ($current/$total)"
                ))
            }
            universalFile.delete()

            var archStream: InputStream?
            try {
                archStream = context.assets.open(archPath)
            } catch (_: Exception) {
                archStream = null
            }

            if (archStream != null) {
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, 50,
                    "正在解压 Java $versionNum 架构文件..."
                ))

                val archFile = File(tempDir, "bin-$fclArch.tar.xz")
                archFile.outputStream().use { output -> archStream.copyTo(output) }
                archStream.close()

                if (archFile.length() < 1024) {
                    tempDir.deleteRecursively()
                    return false
                }

                JreExtractor.extractTarXz(archFile, extractDir) { current, total ->
                    val progress = 50 + (if (total > 0) ((current * 35) / total).toInt() else 0)
                    onProgress(JavaInstallProgress(
                        JavaInstallStatus.EXTRACTING, progress,
                        "正在解压架构文件... ($current/$total)"
                    ))
                }
                archFile.delete()
            } else {
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, 50,
                    "使用通用JRE归档 (无专用架构文件)..."
                ))
            }

            if (targetDir.exists()) targetDir.deleteRecursively()

            val jreRoot = JreExtractor.findJreRootDir(extractDir)
            val moveSuccess = if (jreRoot != null && jreRoot != extractDir) {
                JreExtractor.moveDirReliably(jreRoot, targetDir)
            } else {
                JreExtractor.moveDirReliably(extractDir, targetDir)
            }

            if (!moveSuccess || !targetDir.exists()) {
                Log.e(TAG, "FCL JRE extraction: failed to move extracted files to target dir")
                tempDir.deleteRecursively()
                return false
            }

            tempDir.deleteRecursively()

            // 合并嵌入式 JRE 子目录（如 jdk-25/）的内容到 targetDir
            // 当 universal.tar.xz 和 bin-*.tar.xz 的路径前缀不一致时，
            // universal 的内容会被提取到子目录中而丢失
            JreExtractor.mergeStrayJreSubdir(targetDir)

            onProgress(JavaInstallProgress(
                JavaInstallStatus.PATCHING, 90,
                "正在修补Java运行时..."
            ))

            JrePatcher.patchJava(context, targetDir)

            // FCL-style: write version file with version number from assets
            try {
                val versionAssetPath = "app_runtime/java/jre$versionNum/version"
                val versionStream = context.assets.open(versionAssetPath)
                val versionText = versionStream.bufferedReader().readText().trim()
                versionStream.close()
                val versionFile = File(targetDir, "version")
                versionFile.writeText(versionText)
                Log.d(TAG, "Wrote version file: $versionText for Java $versionNum")
            } catch (_: Exception) {
                // Fallback: write our own version number
                File(targetDir, "version").writeText(versionNum.toString())
            }

            // Write release file for compatibility detection
            val releaseFile = File(targetDir, "release")
            if (!releaseFile.exists()) {
                try {
                    releaseFile.writeText("JAVA_VERSION=\"$versionNum\"\n" +
                        "OS_NAME=\"android\"\n")
                } catch (_: Exception) {}
            } else {
                try {
                    val existing = releaseFile.readText()
                    if (!existing.contains("OS_NAME", ignoreCase = true)) {
                        releaseFile.writeText("$existing\n" +
                            "OS_NAME=\"android\"\n")
                    }
                } catch (_: Exception) {}
            }

            if (!JreValidator.verifyJreInstallation(targetDir)) {
                Log.e(TAG, "FCL JRE extraction: verification failed after extraction")
                targetDir.deleteRecursively()
                return false
            }

            JreExtractor.setExecutableRecursive(File(targetDir, "bin"))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun extractPojavJreFromAssets(
        context: Context,
        javaVersion: String,
        arch: String,
        targetDir: File,
        onProgress: (JavaInstallProgress) -> Unit
    ): Boolean {
        val versionNum = JavaRuntimeManager.JRE_VERSIONS[javaVersion] ?: 17

        val pojavArch = when (arch) {
            "aarch64" -> "aarch64"
            "aarch32" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "aarch64"
        }

        val assetsPaths = listOf(
            "app_runtime/java/jre$versionNum/jre$versionNum-pojav.zip",
            "app_runtime/java/jre$versionNum/pojav.zip"
        )

        var inputStream: InputStream? = null

        for (path in assetsPaths) {
            try {
                inputStream = context.assets.open(path)
                break
            } catch (_: Exception) {
                continue
            }
        }

        if (inputStream == null) {
            return false
        }

        return try {
            onProgress(JavaInstallProgress(
                JavaInstallStatus.EXTRACTING, 10,
                "正在从内置资源解压Android兼容JRE..."
            ))

            val tempZip = File(context.cacheDir, "pojav-jre$versionNum-assets.zip")
            tempZip.outputStream().use { output -> inputStream.copyTo(output) }
            inputStream.close()

            if (tempZip.length() < 1024) {
                tempZip.delete()
                return false
            }

            val tempExtractDir = File(context.cacheDir, "pojav_extract_$versionNum")
            if (tempExtractDir.exists()) tempExtractDir.deleteRecursively()
            tempExtractDir.mkdirs()

            JreExtractor.extractPojavJreZip(tempZip, tempExtractDir, pojavArch, onProgress)
            tempZip.delete()

            if (targetDir.exists()) targetDir.deleteRecursively()

            val jreRoot = JreExtractor.findJreRootDir(tempExtractDir)
            val moveSuccess = if (jreRoot != null) {
                JreExtractor.moveDirReliably(jreRoot, targetDir)
            } else {
                val extractedDirs = tempExtractDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                if (extractedDirs.size == 1) {
                    JreExtractor.moveDirReliably(extractedDirs[0], targetDir)
                } else {
                    JreExtractor.moveDirReliably(tempExtractDir, targetDir)
                }
            }

            if (!moveSuccess || !targetDir.exists()) {
                Log.e(TAG, "Pojav JRE extraction: failed to move extracted files to target dir")
            }

            val tempExtractDirCheck = File(context.cacheDir, "pojav_extract_$versionNum")
            if (tempExtractDirCheck.exists()) tempExtractDirCheck.deleteRecursively()

            onProgress(JavaInstallProgress(
                JavaInstallStatus.PATCHING, 90,
                "正在修补Java运行时..."
            ))

            JrePatcher.patchJava(context, targetDir)

            // Write version file (FCL-style)
            try {
                File(targetDir, "version").writeText(versionNum.toString())
            } catch (_: Exception) {}

            val releaseFile = File(targetDir, "release")
            if (!releaseFile.exists()) {
                try {
                    releaseFile.writeText("JAVA_VERSION=\"$versionNum\"\nOS_NAME=\"android\"\n")
                } catch (_: Exception) {}
            } else {
                try {
                    val existing = releaseFile.readText()
                    if (!existing.contains("OS_NAME", ignoreCase = true)) {
                        releaseFile.writeText("$existing\nOS_NAME=\"android\"\n")
                    }
                } catch (_: Exception) {}
            }

            if (!JreValidator.verifyJreInstallation(targetDir)) {
                Log.e(TAG, "Pojav JRE extraction: verification failed after extraction")
                targetDir.deleteRecursively()
                return false
            }

            JreExtractor.setExecutableRecursive(File(targetDir, "bin"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Pojav JRE extraction failed: ${e.message}")
            false
        }
    }
}
