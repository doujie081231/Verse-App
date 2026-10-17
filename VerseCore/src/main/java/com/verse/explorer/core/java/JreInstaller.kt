package com.verse.explorer.core.java

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

private const val TAG = "JavaRuntimeManager"

internal object JreInstaller {

    /**
     * JRE 安装互斥锁：VerseApplication.initAllJreRuntimes 的后台预装任务与
     * 启动检查/版本安装触发的 JRE 安装可能并发执行，两者会同时读写同一
     * jre-<version>-<arch> 目录（删除、解包写入）。若不串行化，
     * 一个协程删除目录时另一个协程正在写入，会触发 FileTreeWalk 竞态导致进程崩溃。
     * 全局互斥（而非按目录）足够且最稳妥：JRE 安装是低频操作，串行代价可忽略。
     */
    private val installMutex = Mutex()

    suspend fun installJre(
        context: Context,
        javaVersion: String = "java17",
        onProgress: (JavaInstallProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        installMutex.withLock {
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
                        deleteRecursivelySafe(targetDir)
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
                            deleteRecursivelySafe(oldDir)
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
    }

    /**
     *
     * assets 归档的顶层就是标准 JRE 根（bin/ lib/ conf/ legal/ release），
     * 之类的猜测逻辑——那些自研步骤正是反复安装时污染 JRE 目录的根源。
     */
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
        } catch (e: Exception) {
            Log.w(TAG, "FCL JRE $versionNum assets missing at $universalPath: ${e.message}")
            return false
        }

        return try {
            onProgress(JavaInstallProgress(
                JavaInstallStatus.EXTRACTING, 5,
                "正在解压 Java $versionNum 通用文件..."
            ))

            if (targetDir.exists()) deleteRecursivelySafe(targetDir)
            targetDir.mkdirs()

            uncompressTarXZ(universalStream, targetDir) { index, total ->
                val progress = 5 + (if (total > 0) ((index * 40) / total).toInt() else 0)
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压通用文件... ($index/$total)"
                ))
            }
            universalStream.close()

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
                uncompressTarXZ(archStream, targetDir) { index, total ->
                    val progress = 50 + (if (total > 0) ((index * 35) / total).toInt() else 0)
                    onProgress(JavaInstallProgress(
                        JavaInstallStatus.EXTRACTING, progress,
                        "正在解压架构文件... ($index/$total)"
                    ))
                }
                archStream.close()
            } else {
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, 50,
                    "使用通用JRE归档 (无专用架构文件)..."
                ))
            }

            onProgress(JavaInstallProgress(
                JavaInstallStatus.PATCHING, 90,
                "正在修补Java运行时..."
            ))

            JrePatcher.patchJava(context, targetDir)

            try {
                val versionAssetPath = "app_runtime/java/jre$versionNum/version"
                val versionStream = context.assets.open(versionAssetPath)
                val versionText = versionStream.bufferedReader().readText().trim()
                versionStream.close()
                File(targetDir, "version").writeText(versionText)
                Log.d(TAG, "Wrote version file: $versionText for Java $versionNum")
            } catch (_: Exception) {
                File(targetDir, "version").writeText(versionNum.toString())
            }

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
                Log.e(TAG, "FCL JRE extraction: verification failed after extraction")
                deleteRecursivelySafe(targetDir)
                return false
            }

            JreExtractor.setExecutableRecursive(File(targetDir, "bin"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "FCL JRE $versionNum extraction FAILED: ${e.message}", e)
            false
        }
    }

    /**
     * 安全删除目录：`File.deleteRecursively()` 在目标被并发修改 / 已变为非目录 /
     * 符号链接时抛 `AssertionError: rootDir must be verified to be directory beforehand`。
     * 多 JRE 并行初始化时多个协程可能同时清理同一目录，删除前校验 + 捕获所有异常
     */
    private fun deleteRecursivelySafe(dir: File) {
        try {
            if (dir.exists() && dir.isDirectory) {
                dir.deleteRecursively()
            } else {
                Log.w(TAG, "deleteRecursivelySafe skipped (not a directory): ${dir.absolutePath}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "deleteRecursivelySafe failed: ${dir.absolutePath} - ${t.message}")
        }
    }

    /**
     * 由 JreExtractor.extractTarXz 提供实现）。
     */
    private fun uncompressTarXZ(
        inputStream: InputStream,
        dest: File,
        onProgress: (Int, Int) -> Unit
    ) {
        JreExtractor.extractTarXz(inputStream, dest, onProgress)
    }

    /**
     */
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
            deleteRecursivelySafe(tempExtractDir)
            tempExtractDir.mkdirs()

            JreExtractor.extractPojavJreZip(tempZip, tempExtractDir, pojavArch, onProgress)
            tempZip.delete()

            if (targetDir.exists()) deleteRecursivelySafe(targetDir)
            targetDir.mkdirs()

            val universalTar = JreExtractor.findFileInDir(tempExtractDir, "universal.tar.xz")
            val archTar = JreExtractor.findFileInDir(tempExtractDir, "bin-$pojavArch.tar.xz")

            if (universalTar != null) {
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, 40,
                    "正在解压通用文件... Pojav"
                ))
                FileInputStream(universalTar).use { fis ->
                    uncompressTarXZ(fis, targetDir) { index, total ->
                        val progress = 40 + (if (total > 0) ((index * 30) / total).toInt() else 0)
                        onProgress(JavaInstallProgress(
                            JavaInstallStatus.EXTRACTING, progress,
                            "正在解压通用文件... ($index/$total)"
                        ))
                    }
                }
            }

            if (archTar != null) {
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, 70,
                    "正在解压架构文件... Pojav"
                ))
                FileInputStream(archTar).use { fis ->
                    uncompressTarXZ(fis, targetDir) { index, total ->
                        val progress = 70 + (if (total > 0) ((index * 15) / total).toInt() else 0)
                        onProgress(JavaInstallProgress(
                            JavaInstallStatus.EXTRACTING, progress,
                            "正在解压架构文件... ($index/$total)"
                        ))
                    }
                }
            }

            deleteRecursivelySafe(tempExtractDir)

            onProgress(JavaInstallProgress(
                JavaInstallStatus.PATCHING, 90,
                "正在修补Java运行时..."
            ))

            JrePatcher.patchJava(context, targetDir)

            File(targetDir, "version").writeText(versionNum.toString())

            val releaseFile = File(targetDir, "release")
            if (!releaseFile.exists()) {
                try {
                    releaseFile.writeText("JAVA_VERSION=\"$versionNum\"\nOS_NAME=\"android\"\n")
                } catch (_: Exception) {}
            }

            if (!JreValidator.verifyJreInstallation(targetDir)) {
                Log.e(TAG, "Pojav JRE extraction: verification failed after extraction")
                deleteRecursivelySafe(targetDir)
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