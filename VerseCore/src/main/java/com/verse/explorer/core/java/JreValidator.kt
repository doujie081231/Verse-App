package com.verse.explorer.core.java

import android.util.Log
import java.io.File

private const val TAG = "JavaRuntimeManager"

internal object JreValidator {

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

    fun verifyJreInstallation(jreDir: File): Boolean {
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

        val hasLibjvm = JreExtractor.findFileRecursive(File(jreDir, "lib"), "libjvm.so") ||
            JreExtractor.findFileRecursive(File(jreDir, "jre/lib"), "libjvm.so")
        if (!hasLibjvm) {
            Log.e(TAG, "verifyJreInstallation: libjvm.so not found anywhere under ${jreDir.absolutePath}")
            return false
        }
        val hasLibjli = JreExtractor.findFileRecursive(File(jreDir, "lib"), "libjli.so") ||
            JreExtractor.findFileRecursive(File(jreDir, "jre/lib"), "libjli.so")
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
}
