package com.verse.explorer.core.java

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "JavaRuntimeManager"

internal object JrePatcher {

    fun patchJava(context: Context, javaPath: File) {
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

        // Replace libawt_xawt.so with the one from APK (Android 11+ 无法访问 nativeLibraryDir)
        // FCL-style: 强制删除 JRE 自带的 libawt_xawt.so，从 APK 提取项目编译的桩库
        val libDir = File(javaPath, libFolder)
        val libawtFile = File(libDir, "libawt_xawt.so")
        libawtFile.delete()
        extractLibFromApk(context, "libawt_xawt.so", libawtFile)

        // 某些 JRE 将原生库存放在 lib/<arch>/ 子目录中，同步覆盖以覆盖所有加载路径
        val archSubdir = detectArchSubdir(libDir)
        if (archSubdir != null) {
            val archAwtFile = File(libDir, "$archSubdir/libawt_xawt.so")
            archAwtFile.delete()
            extractLibFromApk(context, "libawt_xawt.so", archAwtFile)
        }

        // JDK 24+ 将 java.security 从 lib/security/ 移到了 conf/security/
        // 确保两个位置都有 java.security 文件
        val confSecurityFile = File(javaPath, "conf/security/java.security")
        val libSecurityFile = File(javaPath, "lib/security/java.security")
        val libSecurityDir = File(javaPath, "lib/security")

        if (confSecurityFile.exists() && libSecurityDir.exists()) {
            // conf/ → lib/（某些 JVM 实现仍从 lib/ 加载）
            if (!libSecurityFile.exists()) {
                try {
                    confSecurityFile.copyTo(libSecurityFile)
                    Log.d(TAG, "Copied conf/security/java.security to lib/security/")
                    val confPolicy = File(javaPath, "conf/security/policy")
                    val libPolicy = File(libSecurityDir, "policy")
                    if (confPolicy.exists() && !libPolicy.exists()) {
                        confPolicy.copyRecursively(libPolicy)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy conf→lib java.security: ${e.message}")
                }
            }
        }

        if (libSecurityFile.exists()) {
            val confSecurityDir = File(javaPath, "conf/security")
            if (!confSecurityFile.exists() && confSecurityDir.exists()) {
                // lib/ → conf/（JDK 24+ 从 conf/security/ 加载）
                try {
                    libSecurityFile.copyTo(confSecurityFile)
                    Log.d(TAG, "Copied lib/security/java.security to conf/security/")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy lib→conf java.security: ${e.message}")
                }
            }
        }

        JreExtractor.setExecutableRecursive(File(javaPath, "bin"))
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

    fun detectArchSubdir(libDir: File): String? {
        val candidates = listOf("aarch64", "arm64-v8a", "amd64", "x86_64", "i386", "i486", "i586", "x86", "arm")
        for (candidate in candidates) {
            val dir = File(libDir, candidate)
            if (dir.exists() && dir.isDirectory) return candidate
        }
        return null
    }

    /**
     * 从 APK 的 lib/<abi>/ 目录提取指定 .so 文件到目标路径
     * Android 11+ 限制访问 nativeLibraryDir，所以直接从 ZipFile 读取
     */
    internal fun extractLibFromApk(context: Context, libName: String, destFile: File): Boolean {
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
                val beforeSize = destFile.length()
                destFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.setExecutable(true, false)
                destFile.setReadable(true, false)
                Log.i(TAG, "Extracted $libName from APK [$apkFile] entry [$entryName] -> ${destFile.absolutePath} (before=$beforeSize bytes, after=${destFile.length()} bytes)")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract $libName from APK: ${e.message}")
            false
        }
    }
}
