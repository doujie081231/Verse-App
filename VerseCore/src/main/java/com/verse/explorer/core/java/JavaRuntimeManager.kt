package com.verse.explorer.core.java

import android.content.Context
import android.os.Build
import java.io.File

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

    // 与 FCL 原版一致：只带 jre8 / jre17 / jre21 / jre25。
    // FCL 的 app_runtime（Pojav 运行时）不含 jre11，其归档在官方仓库为 404 占位，
    // 强行安装只会产生下载失败日志。处理器 fallback 链为 21→17，也不依赖 11。
    internal val JRE_VERSIONS = mapOf(
        "java8" to 8,
        "java17" to 17,
        "java21" to 21,
        "java25" to 25,
        "8" to 8,
        "17" to 17,
        "21" to 21,
        "25" to 25
    )

    val BUNDLED_VERSIONS = listOf(8, 17, 21, 25)

    /**
     * 获取与应用实际运行 ABI 匹配的 JRE 架构名。
     *
     * 关键点：当应用只打包 arm64-v8a 时，即使在 x86_64 设备上通过翻译层运行，
     * 进程实际仍是 arm64，必须安装 aarch64 JRE 才能加载 libjvm.so 等原生库。
     */
    fun getArchName(): String {
        return when (getAppActualAbi()) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "aarch32"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "aarch64"
        }
    }

    fun getFclArchName(): String {
        return when (getAppActualAbi()) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "arm64"
        }
    }

    /**
     * 获取应用进程实际运行的 ABI。
     * APK 同时打包 arm64-v8a 和 x86_64，系统会根据设备首选 ABI 自动选择。
     * 使用 Build.SUPPORTED_ABIS[0] 获取实际运行 ABI，
     * 避免 x86_64 模拟器上通过 arm 翻译层运行时安装错误架构的 JRE。
     */
    private fun getAppActualAbi(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        val packagedAbis = setOf("arm64-v8a", "x86_64")
        // 优先返回设备首选 ABI（前提是 APK 打包了该 ABI）
        supportedAbis.firstOrNull { it in packagedAbis }?.let { return it }
        return "arm64-v8a"
    }

    fun isAndroidCompatibleJre(jreDir: File): Boolean =
        JreValidator.isAndroidCompatibleJre(jreDir)

    fun getJreDir(context: Context): File {
        return File(context.filesDir, "jre")
    }

    /**
     * 导入本地 Java 运行时压缩包（zip）。
     * 解压到 jre 目录下以 [displayName] 命名的子目录；若压缩包带有单一顶层目录
     * 则内容上提一层，保证 bin/java 位于导入根（scanInstalledRuntimes 只扫一层）。
     * 解压后立即扫描验证，识别失败（缺 bin/java 或读不到版本）则回滚删除并抛异常。
     */
    fun importJava(context: Context, input: java.io.InputStream, displayName: String): JavaRuntime {
        val safeName = displayName.trim()
            .removeSuffix(".zip").removeSuffix(".ZIP")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "imported-java" }
        val targetDir = File(getJreDir(context), safeName)
        val cacheZip = File(context.cacheDir, "import_java_${System.currentTimeMillis()}.zip")

        try {
            cacheZip.outputStream().use { output -> input.copyTo(output) }
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            java.util.zip.ZipFile(cacheZip).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val out = File(targetDir, entry.name)
                    // 防 zip slip：规范化后必须仍在目标目录内
                    if (!out.canonicalPath.startsWith(targetDir.canonicalPath + File.separator) &&
                        out.canonicalPath != targetDir.canonicalPath
                    ) continue
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
                    }
                }
            }

            // 单一顶层目录（bin 或 jre 在其下）则上提一层
            val children = targetDir.listFiles()?.toList() ?: emptyList()
            val dirs = children.filter { it.isDirectory }
            if (dirs.size == 1 && children.size == 1) {
                val inner = dirs.first()
                val hasJava = File(inner, "bin/java").exists() || File(inner, "jre/bin/java").exists()
                if (hasJava) {
                    inner.listFiles()?.forEach { it.renameTo(File(targetDir, it.name)) }
                    inner.delete()
                }
            }

            val runtime = JavaManager(targetDir).scanInstalledRuntimes()
                .firstOrNull { it.path.absolutePath.startsWith(targetDir.absolutePath) }
                ?: throw Exception("无法识别导入的 Java 运行时（缺少 bin/java 或 release 文件）")
            return runtime
        } finally {
            cacheZip.delete()
        }
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
    ): Result<File> = JreInstaller.installJre(context, javaVersion, onProgress)

    suspend fun installJre(
        context: Context,
        javaVersion: String = "java17",
        onProgress: (JavaInstallProgress) -> Unit
    ): Result<File> = JreInstaller.installJre(context, javaVersion, onProgress)

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

    /**
     * 从 APK 的 lib/<abi>/ 目录提取指定 .so 文件到目标路径。
     * Android 11+ 限制访问 nativeLibraryDir，所以直接从 ZipFile 读取。
     */
    fun extractLibFromApk(context: Context, libName: String, destFile: File): Boolean =
        JrePatcher.extractLibFromApk(context, libName, destFile)

    /**
     * 检测 JRE lib 目录下的架构子目录（如 aarch64、arm64-v8a 等）。
     */
    fun detectArchSubdir(libDir: File): String? = JrePatcher.detectArchSubdir(libDir)
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
