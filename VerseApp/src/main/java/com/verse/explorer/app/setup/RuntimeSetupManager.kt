package com.verse.explorer.app.setup

import android.content.Context
import android.os.Build
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipFile

object RuntimeSetupManager {

    private const val TAG = "RuntimeSetupManager"

    private const val GITHUB_RAW_BASE =
        "https://raw.githubusercontent.com/FCL-Team/FoldCraftLauncher/main/FCL/src/main/assets/app_runtime"
    private const val BMCLAPI_BASE =
        "https://bmclapi2.bangbang93.com/fcl"

    lateinit var RUNTIME_DIR: String
        private set
    lateinit var JAVA_PATH: String
        private set
    lateinit var JAVA_8_PATH: String
        private set
    lateinit var JAVA_17_PATH: String
        private set
    lateinit var JAVA_21_PATH: String
        private set
    lateinit var JNA_PATH: String
        private set
    lateinit var LWJGL_DIR: String
        private set
    lateinit var CACIOCAVALLO_8_DIR: String
        private set
    lateinit var CACIOCAVALLO_17_DIR: String
        private set

    fun loadPaths(context: Context) {
        RUNTIME_DIR = context.getDir("runtime", 0).absolutePath
        JAVA_PATH = "$RUNTIME_DIR/java"
        JAVA_8_PATH = "$JAVA_PATH/jre8"
        JAVA_17_PATH = "$JAVA_PATH/jre17"
        JAVA_21_PATH = "$JAVA_PATH/jre21"
        JNA_PATH = "$RUNTIME_DIR/jna"
        LWJGL_DIR = "$RUNTIME_DIR/lwjgl"
        CACIOCAVALLO_8_DIR = "$RUNTIME_DIR/caciocavallo"
        CACIOCAVALLO_17_DIR = "$RUNTIME_DIR/caciocavallo17"

        initDir(RUNTIME_DIR)
        initDir(JAVA_PATH)
    }

    private fun initDir(path: String): Boolean {
        val dir = File(path)
        return if (!dir.exists()) dir.mkdirs() else true
    }

    enum class RuntimeComponent(
        val displayName: String,
        val description: String,
        val remotePath: String,
        val isJava: Boolean = false,
        val isJna: Boolean = false,
        val isOptional: Boolean = false
    ) {
        LWJGL("LWJGL", "OpenGL 图形库", "lwjgl"),
        CACIO_8("CacioCavallo 8", "Java 8 AWT 桥接", "caciocavallo"),
        CACIO_17("CacioCavallo 17", "Java 17+ AWT 桥接", "caciocavallo17"),
        JAVA_8("Java 8", "Minecraft 1.7 - 1.16", "java/jre8", isJava = true),
        JAVA_17("Java 17", "Minecraft 1.18 - 1.20.4", "java/jre17", isJava = true),
        JAVA_21("Java 21", "Minecraft 1.20.5 - 1.21.x", "java/jre21", isJava = true),
        JAVA_25("Java 25", "Minecraft 1.22+", "java/jre25", isJava = true, isOptional = true),
        JNA("JNA", "Java 原生访问库", "jna", isJna = true)
    }

    fun getTargetDir(component: RuntimeComponent): String {
        return when (component) {
            RuntimeComponent.LWJGL -> LWJGL_DIR
            RuntimeComponent.CACIO_8 -> CACIOCAVALLO_8_DIR
            RuntimeComponent.CACIO_17 -> CACIOCAVALLO_17_DIR
            RuntimeComponent.JAVA_8 -> JAVA_8_PATH
            RuntimeComponent.JAVA_17 -> JAVA_17_PATH
            RuntimeComponent.JAVA_21 -> JAVA_21_PATH
            RuntimeComponent.JAVA_25 -> "$JAVA_PATH/jre25"
            RuntimeComponent.JNA -> JNA_PATH
        }
    }

    // ==================== 版本检测 ====================

    /**
     * 运行时资产包版本（与 FCL / Zalith 的做法一致：跟随「资产内容」而不是跟随「APK 装过没装过」）。
     *
     * APK 里 assets/app_runtime 目录的内容有任何变动（升级 lwjgl、换 JRE 构建、
     * 加新组件）时把这个值 +1，用户覆盖安装后就会重新走一次「安装前置环境」；
     * 只改 UI / 业务代码而不动运行时资产的版本不动它，覆盖安装后组件直接判定就绪，
     * 不会让用户白等一次几百 MB 的解压。
     *
     * 旧版本写入的标记是 "1"（固定值）或 APK 时间戳，迁移时自然不匹配，
     * 会触发一次重装，之后按本常量比较。
     */
    private const val RUNTIME_BUNDLE_VERSION = 2

    private fun bundleStamp(): String = "bundle:$RUNTIME_BUNDLE_VERSION"

    fun isLatest(context: Context, component: RuntimeComponent): Boolean {
        val targetDir = File(getTargetDir(component))
        if (!targetDir.exists()) return false

        // 运行时资产版本与安装时的记录不一致 → 需要重装
        val installedStamp = File(targetDir, "version").let {
            if (it.exists()) runCatching { it.readText().trim() }.getOrDefault("") else ""
        }
        if (installedStamp != bundleStamp()) return false

        val versionFile = File(targetDir, "version")
        if (!versionFile.exists()) {
            val files = targetDir.listFiles()
            if (files == null || files.isEmpty()) return false
            val meaningful = files.filter { it.name != "version" && it.length() > 0 }
            if (meaningful.isEmpty()) return false
        }

        if (component.isJava) {
            val hasLib = File(targetDir, "lib").isDirectory ||
                    File(targetDir, "jre/lib").isDirectory
            val hasBin = File(targetDir, "bin").isDirectory ||
                    File(targetDir, "jre/bin").isDirectory
            return hasLib && hasBin
        }

        if (component.isJna) {
            return File(targetDir, "jna.jar").exists() &&
                    File(targetDir, "jna.jar").length() > 1024
        }

        return true
    }

    // ==================== 安装逻辑（assets 优先，网络回退） ====================

    private fun assetPath(component: RuntimeComponent): String =
        "app_runtime/${component.remotePath}"

    fun installComponent(context: Context, component: RuntimeComponent) {
        val targetDir = getTargetDir(component)
        Log.i(TAG, "Installing ${component.displayName} -> $targetDir")

        val dir = File(targetDir)
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()

        try {
            copyAssets(context, assetPath(component), targetDir)
            Log.i(TAG, "Installed ${component.displayName} from assets")
        } catch (_: Exception) {
            Log.w(TAG, "No assets for ${component.displayName}, downloading...")
            downloadComponentFiles(component, targetDir)
        }

        File(targetDir, "version").writeText(bundleStamp())

        val meaningful = dir.listFiles()?.filter { it.name != "version" && it.length() > 0 }
        if (meaningful.isNullOrEmpty()) {
            throw RuntimeException("${component.displayName} 安装失败: 目录为空")
        }
        Log.i(TAG, "Installed ${component.displayName} (${meaningful.size} files)")
    }

    fun installJavaComponent(context: Context, component: RuntimeComponent) {
        val targetDir = getTargetDir(component)
        Log.i(TAG, "Installing Java ${component.displayName} -> $targetDir")

        val dir = File(targetDir)
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()

        val assetBase = assetPath(component)
        try {
            extractTarXzFromAssets(context, "$assetBase/universal.tar.xz", dir)
            val arch = getArchName()
            try {
                extractTarXzFromAssets(context, "$assetBase/bin-$arch.tar.xz", dir)
            } catch (_: Exception) {
                Log.w(TAG, "No arch-specific tar for ${component.displayName}")
            }
        } catch (_: Exception) {
            Log.w(TAG, "No assets for Java, downloading...")
            downloadJavaRuntime(component, targetDir)
        }

        patchJava(context, targetDir)

        val binDir = File(targetDir, "bin")
        if (binDir.exists()) {
            binDir.listFiles()?.forEach { it.setExecutable(true, false) }
        }

        File(targetDir, "version").writeText(bundleStamp())
        val versionNum = when (component) {
            RuntimeComponent.JAVA_8 -> "8"
            RuntimeComponent.JAVA_17 -> "17"
            RuntimeComponent.JAVA_21 -> "21"
            RuntimeComponent.JAVA_25 -> "25"
            else -> "17"
        }
        // 依赖它计算 lib 路径（archAsInt 对 null 会抛 NPE）。此处保持与 OpenJDK release
        // 文件格式一致；lib/<arch> 子目录不存在时 getJavaLibDir 回退到默认 /lib。
        val osArch = when (getArchName()) {
            "arm64" -> "aarch64"
            "arm" -> "arm"
            else -> getArchName()
        }
        File(targetDir, "release").writeText("JAVA_VERSION=\"$versionNum\"\nOS_NAME=\"android\"\nOS_ARCH=\"$osArch\"\n")

        Log.i(TAG, "Installed Java ${component.displayName}")
    }

    fun installJnaComponent(context: Context, component: RuntimeComponent) {
        val targetDir = getTargetDir(component)
        Log.i(TAG, "Installing JNA -> $targetDir")

        val dir = File(targetDir)
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()

        try {
            copyAssets(context, assetPath(component), targetDir)
        } catch (_: Exception) {
            Log.w(TAG, "No JNA assets, downloading...")
            val jnaJar = File(targetDir, "jna.jar")
            val downloaded = downloadFileWithMirror(
                "jna/jna.jar",
                "net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar",
                jnaJar
            )
            if (!downloaded) {
                throw RuntimeException("JNA 下载失败")
            }
        }

        File(targetDir, "version").writeText(bundleStamp())
        Log.i(TAG, "Installed JNA")
    }

    // ==================== 网络下载 ====================

    private fun downloadComponentFiles(component: RuntimeComponent, targetDir: String) {
        when (component) {
            RuntimeComponent.LWJGL -> {
                // 禁止网络回退：官方 lwjgl-3.3.1 core jar 不含 org.lwjgl.glfw 类，
                // 会导致 MC 1.19.4+/26.x 启动失败，比旧裁剪版更糟。
                throw RuntimeException("LWJGL 必须从内置资产安装，禁止网络回退替换")
            }
            RuntimeComponent.CACIO_8 -> {
                val files = listOf(
                    "cacio-androidnw-1.10-SNAPSHOT.jar",
                    "cacio-shared-1.10-SNAPSHOT.jar",
                    "ResConfHack.jar"
                )
                for (name in files) {
                    val ok = downloadFileWithMirror(
                        "caciocavallo/$name", "", File(targetDir, name)
                    )
                    if (!ok) Log.w(TAG, "Failed to download $name")
                }
            }
            RuntimeComponent.CACIO_17 -> {
                val files = listOf(
                    "cacio-agent.jar",
                    "cacio-shared-1.19.1-SNAPSHOT.jar",
                    "cacio-tta-1.19.1-SNAPSHOT.jar"
                )
                for (name in files) {
                    val ok = downloadFileWithMirror(
                        "caciocavallo17/$name", "", File(targetDir, name)
                    )
                    if (!ok) Log.w(TAG, "Failed to download $name")
                }
            }
            else -> throw RuntimeException("未知组件: ${component.displayName}")
        }
    }

    private fun downloadJavaRuntime(component: RuntimeComponent, targetDir: String) {
        val arch = getArchName()
        val dir = File(targetDir)

        val universalUrl = "${component.remotePath}/universal.tar.xz"
        val archUrl = "${component.remotePath}/bin-$arch.tar.xz"

        val universalFile = File(dir, "universal.tar.xz")
        val archFile = File(dir, "bin-$arch.tar.xz")

        Log.i(TAG, "Downloading Java ${component.displayName} universal...")
        val ok1 = downloadFileWithMirror(universalUrl, "", universalFile)
        if (!ok1) throw RuntimeException("${component.displayName} universal 下载失败")

        Log.i(TAG, "Downloading Java ${component.displayName} $arch...")
        val ok2 = downloadFileWithMirror(archUrl, "", archFile)
        if (!ok2) Log.w(TAG, "Arch download failed, continuing with universal only")

        Log.i(TAG, "Extracting Java ${component.displayName}...")
        extractTarXz(universalFile, dir)
        universalFile.delete()

        if (ok2 && archFile.exists()) {
            extractTarXz(archFile, dir)
            archFile.delete()
        }
    }

    private fun downloadFileWithMirror(
        fclPath: String,
        mavenPath: String,
        outputFile: File
    ): Boolean {
        val urls = mutableListOf<String>()
        if (fclPath.isNotEmpty()) {
            urls.add("$GITHUB_RAW_BASE/$fclPath")
            urls.add("$BMCLAPI_BASE/$fclPath")
        }
        if (mavenPath.isNotEmpty()) {
            urls.add("https://repo1.maven.org/maven2/$mavenPath")
        }

        for (url in urls) {
            if (downloadFile(url, outputFile)) return true
        }
        return false
    }

    private fun downloadFile(url: String, outputFile: File): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 120000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "VerseExplorer/1.0")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return false
            }

            conn.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                    }
                    output.flush()
                }
            }
            conn.disconnect()
            outputFile.length() > 100
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $url - ${e.message}")
            if (outputFile.exists()) outputFile.delete()
            false
        }
    }

    // ==================== tar.xz 解压 ====================

    private fun extractTarXzFromAssets(context: Context, assetPath: String, destDir: File) {
        destDir.mkdirs()
        context.assets.open(assetPath).use { fis ->
            extractTarXzStream(fis, destDir)
        }
    }

    private fun extractTarXz(file: File, destDir: File) {
        destDir.mkdirs()
        file.inputStream().use { fis ->
            extractTarXzStream(fis, destDir)
        }
    }

    private fun extractTarXzStream(inputStream: InputStream, destDir: File) {
        XZCompressorInputStream(inputStream).use { xzIn ->
            TarArchiveInputStream(xzIn).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val destPath = File(destDir, entry.name)

                    if (!destPath.canonicalPath.startsWith(destDir.canonicalPath)) {
                        entry = tarIn.nextEntry
                        continue
                    }

                    if (entry.isSymbolicLink) {
                        destPath.parentFile?.mkdirs()
                        try {
                            val linkTarget = entry.linkName.replace("..", destDir.absolutePath)
                            android.system.Os.symlink(linkTarget, destPath.absolutePath)
                        } catch (_: Exception) {}
                    } else if (entry.isDirectory) {
                        destPath.mkdirs()
                        try { destPath.setExecutable(true) } catch (_: Exception) {}
                    } else {
                        destPath.parentFile?.mkdirs()
                        FileOutputStream(destPath).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (tarIn.read(buffer).also { len = it } != -1) {
                                fos.write(buffer, 0, len)
                            }
                            fos.flush()
                        }
                        try {
                            if ((entry.mode and 0b1000000) != 0) destPath.setExecutable(true, false)
                            destPath.setReadable(true, false)
                        } catch (_: Exception) {}
                    }

                    entry = tarIn.nextEntry
                }
            }
        }
    }

    // ==================== Java 修补 ====================

    private fun patchJava(context: Context, javaPath: String) {
        val dest = File(javaPath)
        if (!dest.exists()) return

        val isJDK8 = File(dest, "jre/lib").exists() || File(dest, "jre/bin").exists()
        val baseLibFolder = if (isJDK8) "jre/lib" else "lib"

        val archSubdir = when {
            File(dest, "$baseLibFolder/aarch64").exists() -> "aarch64"
            File(dest, "$baseLibFolder/arm").exists() -> "arm"
            File(dest, "$baseLibFolder/x86_64").exists() -> "x86_64"
            else -> ""
        }
        val libFolder = if (archSubdir.isNotEmpty()) "$baseLibFolder/$archSubdir" else baseLibFolder

        val ftIn = File(dest, "$libFolder/libfreetype.so.6")
        val ftOut = File(dest, "$libFolder/libfreetype.so")
        if (ftIn.exists() && !ftOut.exists()) {
            try { ftIn.renameTo(ftOut) } catch (_: Exception) {}
        }

        val libawtFile = File(dest, "$libFolder/libawt_xawt.so")
        if (!libawtFile.exists()) {
            extractLibFromApk(context, "libawt_xawt.so", libawtFile)
        }

        val binDir = File(dest, "bin")
        if (binDir.exists()) {
            binDir.listFiles()?.forEach { it.setExecutable(true, false) }
        }
        val libDir = File(dest, libFolder)
        if (libDir.exists()) {
            libDir.listFiles()?.filter { it.name.endsWith(".so") }?.forEach {
                it.setExecutable(true, false)
            }
            File(libDir, "server").listFiles()?.filter { it.name.endsWith(".so") }?.forEach {
                it.setExecutable(true, false)
            }
            File(libDir, "jli").listFiles()?.filter { it.name.endsWith(".so") }?.forEach {
                it.setExecutable(true, false)
            }
        }
    }

    /**
     * 从 APK 的 lib/<abi>/ 目录提取指定 .so 文件到目标路径
     * Android 11+ 限制访问 nativeLibraryDir，所以直接从 ZipFile 读取
     */
    private fun extractLibFromApk(context: Context, libName: String, destFile: File): Boolean {
        return try {
            val apkFile = File(context.applicationInfo.sourceDir)
            if (!apkFile.exists()) {
                Log.w(TAG, "APK not found: ${apkFile.absolutePath}")
                return false
            }

            val targetAbi = Build.SUPPORTED_ABIS?.firstOrNull() ?: "arm64-v8a"
            val entryName = "lib/$targetAbi/$libName"

            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: run {
                    Log.w(TAG, "Entry not found in APK: $entryName")
                    return false
                }
                destFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.setExecutable(true, false)
                destFile.setReadable(true, false)
                Log.i(TAG, "Extracted $libName from APK -> ${destFile.absolutePath} (${destFile.length()} bytes)")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract $libName from APK: ${e.message}")
            false
        }
    }

    // ==================== Assets 复制（回退） ====================

    private fun copyAssets(context: Context, src: String, dest: String) {
        val fileNames = context.assets.list(src)
        if (fileNames == null || fileNames.isEmpty()) {
            val outFile = File(dest)
            outFile.parentFile?.mkdirs()
            context.assets.open(src).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        } else {
            for (fileName in fileNames) {
                copyAssets(context, "$src/$fileName", "$dest/$fileName")
            }
        }
    }

    // ==================== resolv.conf ====================

    fun createResolvConf(context: Context) {
        val resolvFile = File(JAVA_PATH, "resolv.conf")
        if (!resolvFile.exists()) {
            if (Locale.getDefault().displayName != Locale.CHINA.displayName) {
                resolvFile.writeText("nameserver 1.1.1.1\nnameserver 1.0.0.1\n")
            } else {
                resolvFile.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            }
        }
    }

    // ==================== 批量操作 ====================

    fun checkAllComponents(context: Context): Map<RuntimeComponent, Boolean> {
        val result = mutableMapOf<RuntimeComponent, Boolean>()
        for (component in RuntimeComponent.entries) {
            try {
                result[component] = isLatest(context, component)
            } catch (e: Exception) {
                Log.w(TAG, "Check failed: ${component.displayName}: ${e.message}")
                result[component] = false
            }
        }
        return result
    }

    fun installMissingComponents(
        context: Context,
        status: Map<RuntimeComponent, Boolean>,
        onProgress: (RuntimeComponent, String) -> Unit
    ) {
        for (component in RuntimeComponent.entries) {
            if (status[component] == true) continue
            onProgress(component, "正在安装...")
            try {
                when {
                    component.isJava -> installJavaComponent(context, component)
                    component.isJna -> installJnaComponent(context, component)
                    else -> installComponent(context, component)
                }
                onProgress(component, "安装完成")
            } catch (e: Exception) {
                Log.e(TAG, "Install failed: ${component.displayName}: ${e.message}")
                onProgress(component, "安装失败: ${e.message}")
            }
        }
        createResolvConf(context)
    }

    // ==================== 工具 ====================

    fun getArchName(): String {
        return when (android.os.Build.SUPPORTED_ABIS.firstOrNull()) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "arm64"
        }
    }
}
