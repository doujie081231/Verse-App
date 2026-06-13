package com.verse.explorer.core.java

import android.content.Context
import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import android.util.Log
import java.io.*
import java.net.URL
import java.net.HttpURLConnection

private const val TAG = "JavaRuntimeManager"

data class JavaInstallProgress(
    val status: JavaInstallStatus,
    val progress: Int,
    val message: String,
    val currentFile: String = ""
)

enum class JavaInstallStatus {
    PENDING,
    CHECKING,
    DOWNLOADING,
    EXTRACTING,
    PATCHING,
    COMPLETED,
    FAILED
}

object JavaRuntimeManager {

    private val JRE_VERSIONS = mapOf(
        "java8" to 8,
        "java17" to 17,
        "java21" to 21,
        "java25" to 25,
        "8" to 8,
        "17" to 17,
        "21" to 21,
        "25" to 25
    )

    val BUNDLED_VERSIONS = listOf(8, 11, 17, 21, 25)

    private val JRE_DOWNLOAD_SOURCES = listOf(
        JREDownloadSource(
            name = "BMCLAPI",
            baseUrl = "https://bmclapi2.bangbang93.com/fcl/java",
            priority = 0
        ),
        JREDownloadSource(
            name = "FastMirror",
            baseUrl = "https://fastmirror.cn/api/fcl/java",
            priority = 1
        ),
        JREDownloadSource(
            name = "GitHub",
            baseUrl = "https://raw.githubusercontent.com/FCL-Team/FoldCraftLauncher/main/app_runtime/java",
            priority = 2
        )
    )

    data class JREDownloadSource(
        val name: String,
        val baseUrl: String,
        val priority: Int
    )

    fun getArchName(): String {
        return when (Build.SUPPORTED_ABIS[0]) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "aarch32"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "aarch64"
        }
    }

    fun getFclArchName(): String {
        return when (Build.SUPPORTED_ABIS[0]) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "arm64"
        }
    }

    fun isAndroidCompatibleJre(jreDir: File): Boolean {
        val releaseFile = File(jreDir, "release")
        if (releaseFile.exists()) {
            try {
                val content = releaseFile.readText()
                if (content.contains("pojav", ignoreCase = true) ||
                    content.contains("android", ignoreCase = true) ||
                    content.contains("bionic", ignoreCase = true)) {
                    return true
                }
            } catch (_: Exception) {}
        }

        if (File(jreDir, "bin/java.sh").exists()) return true

        val libjvmPaths = listOf(
            File(jreDir, "lib/aarch64/server/libjvm.so"),
            File(jreDir, "lib/arm/server/libjvm.so"),
            File(jreDir, "lib/amd64/server/libjvm.so"),
            File(jreDir, "lib/server/libjvm.so")
        )

        for (libjvm in libjvmPaths) {
            if (libjvm.exists()) {
                try {
                    val process = Runtime.getRuntime().exec(
                        arrayOf("readelf", "-d", libjvm.absolutePath)
                    )
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    if (output.contains("libc.so", ignoreCase = true) &&
                        !output.contains("ld-linux", ignoreCase = true)) {
                        return true
                    }
                } catch (_: Exception) {}

                if (libjvm.length() < 20_000_000) return true
            }
        }

        return false
    }

    fun getJreDir(context: Context): File {
        return File(context.filesDir, "jre")
    }

    fun getInstalledRuntimes(context: Context): List<JavaRuntime> {
        val jreDir = getJreDir(context)
        val manager = JavaManager(jreDir)
        val primaryRuntimes = manager.scanInstalledRuntimes()

        // 同时扫描 RuntimeSetupManager (FCL-style) 的 Java 目录
        val runtimeDir = context.getDir("runtime", 0)
        val javaDir = File(runtimeDir, "java")
        if (javaDir.exists() && javaDir.isDirectory) {
            val javaManager = JavaManager(javaDir)
            val runtimeRuntimes = javaManager.scanInstalledRuntimes()
            val existingPaths = primaryRuntimes.map { it.path.canonicalPath }.toSet()
            val merged = primaryRuntimes.toMutableList()
            runtimeRuntimes.forEach { rt ->
                if (rt.path.canonicalPath !in existingPaths) {
                    merged.add(rt)
                }
            }
            return merged
        }
        return primaryRuntimes
    }

    fun hasJava(context: Context, minVersion: Int = 17): Boolean {
        val runtimes = getInstalledRuntimes(context)
        return runtimes.any { it.version >= minVersion }
    }

    fun getJavaVersion(context: Context): Int? {
        val runtimes = getInstalledRuntimes(context)
        return runtimes.maxByOrNull { it.version }?.version
    }

    fun getJavaVersionString(context: Context): String {
        val version = getJavaVersion(context)
        return if (version != null) "Java $version" else "未安装"
    }

    fun getJavaRuntimeInfo(context: Context): JavaRuntimeInfo {
        val runtimes = getInstalledRuntimes(context)
        val best = runtimes.maxByOrNull { it.version }
        return if (best != null) {
            JavaRuntimeInfo(
                installed = true,
                version = best.version,
                versionName = "Java ${best.version}",
                path = best.path.absolutePath,
                arch = best.arch,
                vendor = best.vendor
            )
        } else {
            JavaRuntimeInfo(
                installed = false,
                version = 0,
                versionName = "未安装",
                path = "",
                arch = getArchName(),
                vendor = ""
            )
        }
    }

    suspend fun downloadJre(
        context: Context,
        javaVersion: String = "java17",
        onProgress: (JavaInstallProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        installJre(context, javaVersion, onProgress)
    }

    suspend fun installJre(
        context: Context,
        javaVersion: String = "java17",
        onProgress: (JavaInstallProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val arch = getArchName()
            val jreDir = getJreDir(context)
            if (!jreDir.exists()) jreDir.mkdirs()

            val versionNum = JRE_VERSIONS[javaVersion] ?: 17
            val targetDir = File(jreDir, "jre-$versionNum-$arch")

            if (targetDir.exists()) {
                val javaBin = File(targetDir, "bin/java")
                val javaSh = File(targetDir, "bin/java.sh")
                if ((javaBin.exists() || javaSh.exists()) && isAndroidCompatibleJre(targetDir)) {
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
                    if (!isAndroidCompatibleJre(oldDir)) {
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
            
            val downloadResult = downloadJreFromNetwork(context, javaVersion, arch, targetDir, onProgress)
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

    private fun extractFclJreFromAssets(
        context: Context,
        javaVersion: String,
        arch: String,
        targetDir: File,
        onProgress: (JavaInstallProgress) -> Unit
    ): Boolean {
        val versionNum = JRE_VERSIONS[javaVersion] ?: 17
        val fclArch = getFclArchName()

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

            extractTarXz(universalFile, extractDir) { current, total ->
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

                extractTarXz(archFile, extractDir) { current, total ->
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

            val jreRoot = findJreRootDir(extractDir)
            val moveSuccess = if (jreRoot != null && jreRoot != extractDir) {
                moveDirReliably(jreRoot, targetDir)
            } else {
                moveDirReliably(extractDir, targetDir)
            }

            if (!moveSuccess || !targetDir.exists()) {
                Log.e(TAG, "FCL JRE extraction: failed to move extracted files to target dir")
                tempDir.deleteRecursively()
                return false
            }

            tempDir.deleteRecursively()

            onProgress(JavaInstallProgress(
                JavaInstallStatus.PATCHING, 90,
                "正在修补Java运行时..."
            ))

            patchJava(context, targetDir)

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

            if (!verifyJreInstallation(targetDir)) {
                Log.e(TAG, "FCL JRE extraction: verification failed after extraction")
                targetDir.deleteRecursively()
                return false
            }

            setExecutableRecursive(File(targetDir, "bin"))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun extractPojavJreFromAssets(
        context: Context,
        javaVersion: String,
        arch: String,
        targetDir: File,
        onProgress: (JavaInstallProgress) -> Unit
    ): Boolean {
        val versionNum = JRE_VERSIONS[javaVersion] ?: 17

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

            extractPojavJreZip(tempZip, tempExtractDir, pojavArch, onProgress)
            tempZip.delete()

            if (targetDir.exists()) targetDir.deleteRecursively()

            val jreRoot = findJreRootDir(tempExtractDir)
            val moveSuccess = if (jreRoot != null) {
                moveDirReliably(jreRoot, targetDir)
            } else {
                val extractedDirs = tempExtractDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                if (extractedDirs.size == 1) {
                    moveDirReliably(extractedDirs[0], targetDir)
                } else {
                    moveDirReliably(tempExtractDir, targetDir)
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

            patchJava(context, targetDir)

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

            if (!verifyJreInstallation(targetDir)) {
                Log.e(TAG, "Pojav JRE extraction: verification failed after extraction")
                targetDir.deleteRecursively()
                return false
            }

            setExecutableRecursive(File(targetDir, "bin"))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Pojav JRE extraction failed: ${e.message}")
            false
        }
    }

    private fun extractPojavJreZip(
        zipFile: File,
        destDir: File,
        arch: String,
        onProgress: (JavaInstallProgress) -> Unit
    ) {
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val entryName = entry.name

                if (entryName.contains("bin-x86_64") && arch != "x86_64" ||
                    entryName.contains("bin-x86") && !entryName.contains("bin-x86_64") && arch != "x86" ||
                    entryName.contains("bin-arm") && !entryName.contains("bin-aarch64") && arch != "arm" ||
                    entryName.contains("bin-aarch64") && arch != "aarch64") {
                    if (entryName.contains("/bin-") || entryName.startsWith("bin-")) {
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }
                }

                val outputFile = File(destDir, entryName)
                if (!outputFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                    continue
                }

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().use { output -> zipIn.copyTo(output) }
                    if (entryName.contains("bin/") || entryName.endsWith(".so")) {
                        outputFile.setExecutable(true, false)
                    }
                    outputFile.setReadable(true, false)
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        val universalTar = findFileInDir(destDir, "universal.tar.xz")
        val archTar = findFileInDir(destDir, "bin-$arch.tar.xz")

        val tarExtractDir = File(destDir, "jre_extracted")
        tarExtractDir.mkdirs()

        if (universalTar != null) {
            extractTarXz(universalTar, tarExtractDir) { current, total ->
                val progress = if (total > 0) ((current * 100) / total).toInt() else 0
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压通用文件... ($current/$total)"
                ))
            }
            universalTar.delete()
        }

        if (archTar != null) {
            extractTarXz(archTar, tarExtractDir) { current, total ->
                val progress = if (total > 0) ((current * 100) / total).toInt() else 0
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压架构文件... ($current/$total)"
                ))
            }
            archTar.delete()
        }

        if (tarExtractDir.exists()) {
            val jreRoot = findJreRootDir(tarExtractDir)
            if (jreRoot != null && jreRoot != destDir) {
                jreRoot.listFiles()?.forEach { file ->
                    val target = File(destDir, file.name)
                    if (target.exists()) target.deleteRecursively()
                    file.renameTo(target)
                }
                tarExtractDir.deleteRecursively()
            }
        }

        val remainingTarFiles = mutableListOf<File>()
        findFilesByExtension(destDir, ".tar.xz", remainingTarFiles)
        for (tarFile in remainingTarFiles) {
            tarFile.delete()
        }
    }

    private fun moveDirReliably(src: File, dest: File): Boolean {
        if (src.absolutePath == dest.absolutePath) return true
        if (!src.exists()) {
            Log.e(TAG, "moveDirReliably: source does not exist: ${src.absolutePath}")
            return false
        }
        if (dest.exists()) {
            dest.deleteRecursively()
        }
        dest.parentFile?.mkdirs()
        val renamed = src.renameTo(dest)
        if (renamed && dest.exists()) {
            Log.d(TAG, "moveDirReliably: renameTo succeeded: ${src.absolutePath} -> ${dest.absolutePath}")
            return true
        }
        Log.w(TAG, "moveDirReliably: renameTo failed, falling back to copy: ${src.absolutePath} -> ${dest.absolutePath}")
        try {
            copyDirRecursive(src, dest)
            if (dest.exists()) {
                src.deleteRecursively()
                Log.d(TAG, "moveDirReliably: copy+delete succeeded")
                return true
            }
            Log.e(TAG, "moveDirReliably: copy completed but dest doesn't exist")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "moveDirReliably: copy failed: ${e.message}")
            return false
        }
    }

    private fun copyDirRecursive(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyDirRecursive(child, File(dest, child.name))
            }
        } else {
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.setExecutable(src.canExecute(), false)
            dest.setReadable(src.canRead(), false)
        }
    }

    private fun verifyJreInstallation(jreDir: File): Boolean {
        if (!jreDir.exists()) {
            Log.e(TAG, "verifyJreInstallation: directory does not exist: ${jreDir.absolutePath}")
            return false
        }
        val libDir = File(jreDir, "lib")
        if (!libDir.exists()) {
            val jreLibDir = File(jreDir, "jre/lib")
            if (!jreLibDir.exists()) {
                Log.e(TAG, "verifyJreInstallation: neither lib/ nor jre/lib/ exists")
                jreDir.listFiles()?.forEach { Log.e(TAG, "  Found: ${it.name} (dir=${it.isDirectory})") }
                return false
            }
        }

        val jvmCfg = File(jreDir, "lib/jvm.cfg")
        val jreJvmCfg = File(jreDir, "jre/lib/jvm.cfg")
        if (!jvmCfg.exists() && !jreJvmCfg.exists()) {
            Log.w(TAG, "verifyJreInstallation: jvm.cfg missing, creating it")
            try {
                val targetCfg = if (File(jreDir, "lib").exists()) jvmCfg else jreJvmCfg
                targetCfg.parentFile?.mkdirs()
                targetCfg.writeText("-server KNOWN\n-client IGNORE\n")
                Log.d(TAG, "Created jvm.cfg at ${targetCfg.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create jvm.cfg: ${e.message}")
            }
        }

        val modules = File(jreDir, "lib/modules")
        val rtJar = File(jreDir, "lib/rt.jar")
        val jreModules = File(jreDir, "jre/lib/modules")
        val jreRtJar = File(jreDir, "jre/lib/rt.jar")
        val hasBootClasspath = modules.exists() || rtJar.exists() || jreModules.exists() || jreRtJar.exists()
        if (!hasBootClasspath) {
            Log.e(TAG, "verifyJreInstallation: no boot classpath found (modules/rt.jar)")
            return false
        }

        val hasLibjvm = findFileRecursive(File(jreDir, "lib"), "libjvm.so") ||
            findFileRecursive(File(jreDir, "jre/lib"), "libjvm.so")
        if (!hasLibjvm) {
            Log.e(TAG, "verifyJreInstallation: libjvm.so not found anywhere under ${jreDir.absolutePath}")
            return false
        }
        val hasLibjli = findFileRecursive(File(jreDir, "lib"), "libjli.so") ||
            findFileRecursive(File(jreDir, "jre/lib"), "libjli.so")
        if (!hasLibjli) {
            Log.w(TAG, "verifyJreInstallation: libjli.so not found (not critical for JNI_CreateJavaVM)")
        }
        val binDir = File(jreDir, "bin")
        val jreBinDir = File(jreDir, "jre/bin")
        if (!binDir.exists() && !jreBinDir.exists()) {
            Log.e(TAG, "verifyJreInstallation: neither bin/ nor jre/bin/ exists")
            return false
        }
        val hasJava = File(binDir, "java").exists() || File(binDir, "java.sh").exists() ||
            File(jreBinDir, "java").exists() || File(jreBinDir, "java.sh").exists()
        if (!hasJava) {
            Log.e(TAG, "verifyJreInstallation: no java executable in bin/ or jre/bin/")
            return false
        }
        Log.d(TAG, "verifyJreInstallation: JRE at ${jreDir.absolutePath} is valid")
        return true
    }

    private fun findFileRecursive(dir: File, fileName: String): Boolean {
        if (!dir.exists()) return false
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name == fileName) return true
            if (file.isDirectory && findFileRecursive(file, fileName)) return true
        }
        return false
    }

    private fun findJreRootDir(dir: File): File? {
        if (File(dir, "bin/java").exists() || File(dir, "bin/java.sh").exists()) {
            return dir
        }
        if (File(dir, "lib/server/libjvm.so").exists() || File(dir, "lib/aarch64/server/libjvm.so").exists() || File(dir, "lib/libjli.so").exists()) {
            return dir
        }
        dir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
            val found = findJreRootDir(subDir)
            if (found != null) return found
        }
        return null
    }

    private fun findFilesByExtension(dir: File, extension: String, result: MutableList<File>) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                findFilesByExtension(file, extension, result)
            } else if (file.name.endsWith(extension)) {
                result.add(file)
            }
        }
    }

    private fun findFileInDir(dir: File, fileName: String): File? {
        if (!dir.exists()) return null
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findFileInDir(file, fileName)
                if (found != null) return found
            } else if (file.name == fileName) {
                return file
            }
        }
        return null
    }

    private fun patchJava(context: Context, javaPath: File) {
        if (!javaPath.exists()) return

        var libFolder = findLibFolder(javaPath)

        // FCL-style: JDK8 has jre/ prefix in its lib path
        val isJDK8 = isJDK8Layout(javaPath)
        if (isJDK8) {
            val jreLib = File(javaPath, "jre/$libFolder")
            if (jreLib.exists()) {
                libFolder = "jre/$libFolder"
            }
        }

        // Fix libfreetype.so symlink (FCL-style)
        val ftIn = File(javaPath, "$libFolder/libfreetype.so.6")
        val ftOut = File(javaPath, "$libFolder/libfreetype.so")
        if (ftIn.exists() && (!ftOut.exists() || ftIn.length() != ftOut.length())) {
            try { ftIn.renameTo(ftOut) } catch (_: Exception) {}
        }

        // For JDK8, also check libfreetype in the non-jre lib path
        if (isJDK8) {
            val altFtIn = File(javaPath, "${findLibFolder(javaPath)}/libfreetype.so")
            if (altFtIn.exists() && !ftOut.exists()) {
                try { altFtIn.renameTo(ftOut) } catch (_: Exception) {}
            }
        }

        // Replace libawt_xawt.so with the one from native libs (FCL-style)
        val libawtFile = File(javaPath, "$libFolder/libawt_xawt.so")
        if (!libawtFile.exists()) {
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            val sourceLib = File(nativeLibDir, "libawt_xawt.so")
            if (sourceLib.exists()) {
                try { sourceLib.copyTo(libawtFile, overwrite = true) } catch (_: Exception) {}
            }
        }

        setExecutableRecursive(File(javaPath, "bin"))
    }

    private fun isJDK8Layout(javaPath: File): Boolean {
        if (File(javaPath, "jre/lib").exists() || File(javaPath, "jre/bin").exists()) return true
        val releaseFile = File(javaPath, "release")
        if (releaseFile.exists()) {
            try {
                val content = releaseFile.readText()
                if (content.contains("JAVA_VERSION=\"1.8") || content.contains("JAVA_VERSION=\"8")) return true
            } catch (_: Exception) {}
        }
        return false
    }

    private fun findLibFolder(javaPath: File): String {
        val jreLib = File(javaPath, "lib")
        if (jreLib.exists() && jreLib.isDirectory) {
            return "lib"
        }
        val jreJreLib = File(javaPath, "jre/lib")
        if (jreJreLib.exists() && jreJreLib.isDirectory) {
            return "jre/lib"
        }
        return "lib"
    }

    private fun extractTarXz(tarXzFile: File, destDir: File, onProgress: (Int, Int) -> Unit) {
        destDir.mkdirs()
        var currentFile = 0

        // First pass: count entries for progress
        var totalFiles = 0
        try {
            FileInputStream(tarXzFile).use { fis ->
                XZCompressorInputStream(fis).use { xzIn ->
                    TarArchiveInputStream(xzIn).use { tarIn ->
                        var entry = tarIn.nextEntry
                        while (entry != null) { totalFiles++; entry = tarIn.nextEntry }
                    }
                }
            }
        } catch (_: Exception) {
            totalFiles = 1
        }

        // Second pass: extract with symlink support
        try {
            FileInputStream(tarXzFile).use { fis ->
                XZCompressorInputStream(fis).use { xzIn ->
                    TarArchiveInputStream(xzIn).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextEntry
                        while (entry != null) {
                            val e = entry!!
                            val destPath = File(destDir, e.name)

                            if (!destPath.canonicalPath.startsWith(destDir.canonicalPath)) {
                                entry = tarIn.nextEntry
                                continue
                            }

                            if (e.isSymbolicLink) {
                                destPath.parentFile?.mkdirs()
                                // FCL-style: use Os.symlink() instead of ln command
                                // Handle relative symlinks properly by resolving against destDir
                                try {
                                    val linkTarget = e.linkName
                                    if (!linkTarget.startsWith("/")) {
                                        // Relative symlink - create absolute target first, then relative symlink
                                        val resolvedTarget = File(destPath.parentFile, linkTarget).canonicalPath
                                        Os.symlink(resolvedTarget, destPath.absolutePath)
                                    } else {
                                        Os.symlink(linkTarget, destPath.absolutePath)
                                    }
                                } catch (symErr: Exception) {
                                    Log.w(TAG, "Symlink failed for ${e.name} -> ${e.linkName}: ${symErr.message}")
                                    // Fallback: create regular file with symlink path content
                                    try {
                                        destPath.writeText(e.linkName)
                                    } catch (_: Exception) {}
                                }
                            } else if (e.isDirectory) {
                                destPath.mkdirs()
                                try { destPath.setExecutable(true) } catch (_: Exception) {}
                            } else {
                                destPath.parentFile?.mkdirs()
                                // Only write if doesn't exist or size differs (FCL-style optimization)
                                if (!destPath.exists() || destPath.length() != e.size) {
                                    try { destPath.createNewFile() } catch (_: Exception) {}
                                    FileOutputStream(destPath).use { fos ->
                                        val buffer = ByteArray(8192)
                                        var len: Int
                                        while (tarIn.read(buffer).also { len = it } != -1) {
                                            fos.write(buffer, 0, len)
                                        }
                                        fos.flush()
                                    }
                                }
                                try {
                                    if ((e.mode and 0b1000000) != 0) destPath.setExecutable(true, false)
                                    destPath.setReadable(true, false)
                                } catch (_: Exception) {}
                            }

                            currentFile++
                            onProgress(currentFile, totalFiles)
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractTarXz failed: ${e.message}", e)
            throw e
        }
    }

    private fun setExecutableRecursive(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                setExecutableRecursive(file)
            } else {
                file.setExecutable(true, false)
            }
        }
    }

    fun getJavaExecutable(context: Context, javaVersion: String = "java17"): File? {
        val jreDir = getJreDir(context)
        val arch = getArchName()
        val versionNum = JRE_VERSIONS[javaVersion] ?: 17
        val runtimeDir = File(jreDir, "jre-$versionNum-$arch")

        if (runtimeDir.exists()) {
            return File(runtimeDir, "bin/java")
        }

        getInstalledRuntimes(context).firstOrNull()?.let {
            return File(it.path, "bin/java")
        }

        return null
    }

    fun deleteRuntime(context: Context, version: Int): Boolean {
        val jreDir = getJreDir(context)
        val arch = getArchName()
        val runtimeDir = File(jreDir, "jre-$version-$arch")
        if (runtimeDir.exists()) {
            return runtimeDir.deleteRecursively()
        }
        jreDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (dir.name.contains("$version") && !isAndroidCompatibleJre(dir)) {
                dir.deleteRecursively()
            }
        }
        return false
    }

    private fun downloadJreFromNetwork(
        context: Context,
        javaVersion: String,
        arch: String,
        targetDir: File,
        onProgress: (JavaInstallProgress) -> Unit
    ): Boolean {
        val versionNum = JRE_VERSIONS[javaVersion] ?: 17
        val fclArch = getFclArchName()
        
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
            
            extractTarXz(universalFile, extractDir) { current, total ->
                val progress = 50 + (if (total > 0) ((current * 20) / total).toInt() else 0)
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压通用文件... ($current/$total)"
                ))
            }
            
            extractTarXz(archFile, extractDir) { current, total ->
                val progress = 70 + (if (total > 0) ((current * 15) / total).toInt() else 0)
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压架构文件... ($current/$total)"
                ))
            }
            
            if (targetDir.exists()) targetDir.deleteRecursively()
            
            val jreRoot = findJreRootDir(extractDir)
            val moveSuccess = if (jreRoot != null && jreRoot != extractDir) {
                moveDirReliably(jreRoot, targetDir)
            } else {
                moveDirReliably(extractDir, targetDir)
            }
            
            if (!moveSuccess || !targetDir.exists()) {
                Log.e(TAG, "Failed to move extracted JRE to target directory")
                tempDir.deleteRecursively()
                return false
            }
            
            tempDir.deleteRecursively()
            
            onProgress(JavaInstallProgress(
                JavaInstallStatus.PATCHING, 90,
                "正在修补 Java 运行时..."
            ))
            
            patchJava(context, targetDir)
            
            val releaseFile = File(targetDir, "release")
            if (!releaseFile.exists()) {
                try {
                    releaseFile.writeText("JAVA_VERSION=\"$versionNum\"\n")
                } catch (_: Exception) {}
            }
            
            if (!verifyJreInstallation(targetDir)) {
                Log.e(TAG, "JRE verification failed after network download")
                targetDir.deleteRecursively()
                return false
            }
            
            setExecutableRecursive(File(targetDir, "bin"))
            Log.i(TAG, "JRE $versionNum installed successfully from network")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download JRE from network: ${e.message}")
            tempDir.deleteRecursively()
            return false
        }
    }
    
    private fun downloadFile(url: String, outputFile: File): Boolean {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "VerseExplorer/1.0")
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Download failed: ${connection.responseCode} for $url")
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

data class JavaRuntimeInfo(
    val installed: Boolean,
    val version: Int,
    val versionName: String,
    val path: String,
    val arch: String,
    val vendor: String
)

data class JavaDownloadProgress(
    val status: JavaDownloadStatus,
    val progress: Int,
    val message: String,
    val currentFile: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val sourceName: String = ""
)

enum class JavaDownloadStatus {
    PENDING,
    CHECKING,
    DOWNLOADING,
    EXTRACTING,
    PATCHING,
    COMPLETED,
    FAILED,
    SOURCE_SWITCHING
}
