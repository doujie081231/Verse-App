package com.verse.explorer.core.java

import java.io.File

data class JavaRuntime(
    val name: String,
    val version: Int,
    val path: File,
    val arch: String,
    val vendor: String
)

enum class JavaVersion(val majorVersion: Int, val displayName: String) {
    JAVA_8(8, "Java 8"),
    JAVA_17(17, "Java 17"),
    JAVA_21(21, "Java 21"),
    JAVA_25(25, "Java 25");

    companion object {
        fun fromMajorVersion(major: Int): JavaVersion? {
            return entries.find { it.majorVersion == major }
        }

        fun getRecommended(mcVersion: String): JavaVersion {
            val snapshotYear = extractSnapshotVersion(mcVersion)
            if (snapshotYear != null) {
                return when {
                    snapshotYear >= 25 -> JAVA_21
                    snapshotYear >= 24 -> JAVA_21
                    snapshotYear >= 23 -> JAVA_17
                    else -> JAVA_8
                }
            }

            val parts = mcVersion.split(".")
            if (parts.size < 2) {
                return try {
                    val ver = mcVersion.toInt()
                    when {
                        ver >= 25 -> JAVA_21
                        ver >= 21 -> JAVA_21
                        ver >= 17 -> JAVA_17
                        ver >= 11 -> JAVA_17
                        else -> JAVA_8
                    }
                } catch (_: Exception) { JAVA_17 }
            }

            val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: return JAVA_17
            val patchStr = parts.getOrNull(2) ?: "0"
            val patch = patchStr.split("-").first().toIntOrNull() ?: 0

            val isModded = mcVersion.contains("forge", ignoreCase = true) ||
                           mcVersion.contains("fabric", ignoreCase = true) ||
                           mcVersion.contains("quilt", ignoreCase = true) ||
                           mcVersion.contains("neoforged", ignoreCase = true)

            return when {
                mcVersion.startsWith("1.7") || mcVersion.startsWith("1.8") || mcVersion.startsWith("1.9") -> JAVA_8
                mcVersion.startsWith("1.10") || mcVersion.startsWith("1.11") || mcVersion.startsWith("1.12") -> JAVA_8
                mcVersion.startsWith("1.13") || mcVersion.startsWith("1.14") || mcVersion.startsWith("1.15") -> JAVA_8
                mcVersion.startsWith("1.16") -> when {
                    isModded && patch >= 5 -> JAVA_17
                    isModded -> JAVA_8
                    patch >= 5 -> JAVA_17
                    else -> JAVA_8
                }
                mcVersion.startsWith("1.17") -> JAVA_17
                mcVersion.startsWith("1.18") || mcVersion.startsWith("1.19") -> JAVA_17
                mcVersion.startsWith("1.20") -> when {
                    patch >= 5 -> JAVA_21
                    else -> JAVA_17
                }
                mcVersion.startsWith("1.21") -> JAVA_21
                mcVersion.startsWith("1.22") -> JAVA_21
                mcVersion.startsWith("1.23") || mcVersion.startsWith("1.24") || mcVersion.startsWith("1.25") -> JAVA_21
                mcVersion.startsWith("1.26") || mcVersion.startsWith("1.27") || mcVersion.startsWith("1.28") -> JAVA_21
                mcVersion.startsWith("1.29") || mcVersion.startsWith("1.30") -> JAVA_21
                mcVersion.startsWith("2") -> JAVA_21
                major >= 22 -> JAVA_21
                major >= 18 -> JAVA_21
                major >= 13 -> JAVA_17
                else -> when {
                    minor >= 22 -> JAVA_21
                    minor >= 20 -> JAVA_21
                    minor >= 17 -> JAVA_17
                    minor >= 11 -> JAVA_17
                    else -> JAVA_8
                }
            }
        }

        private fun extractSnapshotVersion(mcVersion: String): Int? {
            val regex = Regex("""^(\d{2})w\d{2}[a-z]$""")
            val match = regex.find(mcVersion) ?: return null
            return match.groupValues[1].toIntOrNull()
        }
    }
}

class JavaManager(private val runtimeDir: File) {

    companion object {
        fun getJavaLibSubdir(javaHome: File): String {
            val releaseFile = File(javaHome, "release")
            if (!releaseFile.exists()) {
                val libDir = File(javaHome, "lib")
                if (libDir.exists()) {
                    for (candidate in listOf("aarch64", "arm64-v8a", "amd64", "x86_64", "i386", "i486", "i586", "x86")) {
                        if (File(libDir, candidate).isDirectory) return "/lib/$candidate"
                    }
                }
                return "/lib"
            }
            try {
                val props = releaseFile.readLines()
                    .filter { it.contains("=") }
                    .map { it.split("=", limit = 2) }
                    .associate { it[0] to it[1].trim('"', ' ') }
                val osArch = props["OS_ARCH"] ?: return "/lib"
                var arch = osArch
                if (arch == "x86") arch = "i386/i486/i586"
                for (candidate in arch.split("/")) {
                    val candidateDir = File(javaHome, "lib/$candidate")
                    if (candidateDir.exists() && candidateDir.isDirectory) return "/lib/$candidate"
                }
            } catch (_: Exception) {}
            return "/lib"
        }
    }

    private val installedRuntimes = mutableListOf<JavaRuntime>()
    
    fun scanInstalledRuntimes(): List<JavaRuntime> {
        installedRuntimes.clear()
        
        if (!runtimeDir.exists()) {
            runtimeDir.mkdirs()
            android.util.Log.d("JavaManager", "JRE dir not found, creating: ${runtimeDir.absolutePath}")
            return emptyList()
        }
        
        val allItems = runtimeDir.listFiles() ?: emptyArray()
        android.util.Log.d("JavaManager", "Scanning JRE dir: ${runtimeDir.absolutePath} (${allItems.size} items)")
        
        for (javaDir in allItems) {
            if (!javaDir.isDirectory) continue
            
            val javaExe = findJavaExecutable(javaDir)
            if (javaExe == null || !javaExe.exists()) {
                android.util.Log.d("JavaManager", "  SKIP ${javaDir.name}: no java executable")
                continue
            }
            
            val version = detectJavaVersion(javaExe)
            if (version == null) {
                android.util.Log.d("JavaManager", "  SKIP ${javaDir.name}: cannot detect Java version")
                continue
            }
            
            val jreRoot = findJreRoot(javaDir)
            android.util.Log.d("JavaManager", "  FOUND Java $version @ ${javaDir.name}, JRE root: ${jreRoot.name}")
            installedRuntimes.add(
                JavaRuntime(
                    name = "Java $version",
                    version = version,
                    path = jreRoot,
                    arch = detectArch(javaDir),
                    vendor = detectVendor(javaDir)
                )
            )
        }
        
        android.util.Log.d("JavaManager", "Scan complete: ${installedRuntimes.size} runtimes found")
        installedRuntimes.forEach { rt ->
            android.util.Log.d("JavaManager", "  - Java ${rt.version} @ ${rt.path.absolutePath}")
        }
        
        return installedRuntimes.toList()
    }
    
    fun getRuntimeForVersion(javaVersion: JavaVersion): JavaRuntime? {
        return installedRuntimes.find { it.version >= javaVersion.majorVersion }
    }
    
    fun getBestRuntime(): JavaRuntime? {
        return installedRuntimes.maxByOrNull { it.version }
    }
    
    private fun findJavaExecutable(javaDir: File): File? {
        fun checkBin(binDir: File): File? {
            if (!binDir.exists()) return null
            val javaExe = File(binDir, "java")
            if (javaExe.exists()) return javaExe
            val javaSh = File(binDir, "java.sh")
            if (javaSh.exists()) return javaSh
            val javaExeBat = File(binDir, "java.exe")
            if (javaExeBat.exists()) return javaExeBat
            return null
        }

        checkBin(File(javaDir, "bin"))?.let { return it }
        checkBin(File(javaDir, "jre/bin"))?.let { return it }
        return null
    }

    private fun findJreRoot(javaDir: File): File {
        if (isJDK8Layout(javaDir)) return File(javaDir, "jre")
        if (File(javaDir, "lib/rt.jar").exists()) return javaDir
        if (File(javaDir, "lib/modules").exists()) return javaDir
        return javaDir
    }

    private fun isJDK8Layout(javaDir: File): Boolean {
        return File(javaDir, "jre/lib/rt.jar").exists() ||
               File(javaDir, "jre/lib/aarch64/server/libjvm.so").exists() ||
               File(javaDir, "jre/lib/amd64/server/libjvm.so").exists() ||
               File(javaDir, "jre/lib/i386/server/libjvm.so").exists()
    }
    
    private fun detectJavaVersion(javaExe: File): Int? {
        val releaseFile = File(javaExe.parentFile?.parentFile, "release")
        if (releaseFile.exists()) {
            try {
                val content = releaseFile.readText()
                // 取引号内的完整版本值再换算大版本：
                // 旧写法 `JAVA_VERSION="(\d+)` 缺少结尾引号，会把 1.8.0_442 识别成 Java 1，
                // 导致已安装的 Java 8 既选不中也被判定为缺失。
                val match = Regex("""JAVA_VERSION="([^"]+)"""").find(content)
                val value = match?.groupValues?.get(1)
                val ver = value?.let { parseMajorVersion(it) }
                if (ver != null) {
                    android.util.Log.d("JavaManager", "    release file: JAVA_VERSION=$value -> $ver")
                    return ver
                }
            } catch (e: Exception) {
                android.util.Log.d("JavaManager", "    release file read error: ${e.message}")
            }
        } else {
            android.util.Log.d("JavaManager", "    no release file at ${releaseFile.absolutePath}")
        }

        val dirName = javaExe.parentFile?.parentFile?.name ?: ""
        val dirVersionRegex = """jre-(\d+)-""".toRegex()
        val dirMatch = dirVersionRegex.find(dirName)
        if (dirMatch != null) {
            val ver = dirMatch.groupValues[1].toIntOrNull()
            android.util.Log.d("JavaManager", "    dir name match: $ver (from $dirName)")
            return ver
        }

        android.util.Log.d("JavaManager", "    no version detected from $dirName, trying java -version...")
        return try {
            javaExe.setExecutable(true)
            val process = ProcessBuilder(javaExe.absolutePath, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val match = Regex("""version "([^"]+)""").find(output)
            val ver = match?.groupValues?.get(1)?.let { parseMajorVersion(it) }
            android.util.Log.d("JavaManager", "    java -version: $ver")
            ver
        } catch (e: Exception) {
            android.util.Log.d("JavaManager", "    java -version error: ${e.message}")
            null
        }
    }

    /** 版本值换算大版本：1.8.0_442 -> 8，17.0.10 -> 17，21 -> 21 */
    private fun parseMajorVersion(value: String): Int? {
        val parts = value.trim().split('.', '_', '-')
        return if (parts.firstOrNull() == "1") {
            parts.getOrNull(1)?.toIntOrNull()
        } else {
            parts.firstOrNull()?.toIntOrNull()
        }
    }
    
    private fun detectArch(javaDir: File): String {
        val releaseFile = File(javaDir, "release")
        if (releaseFile.exists()) {
            try {
                val content = releaseFile.readText()
                val osArch = Regex("""OS_ARCH="([^"]+)"""").find(content)?.groupValues?.get(1)
                if (osArch != null) {
                    return when (osArch) {
                        "aarch64", "arm64" -> "aarch64"
                        "arm" -> "arm"
                        "amd64", "x86_64" -> "x86_64"
                        "x86", "i386", "i486", "i586" -> "x86"
                        else -> osArch
                    }
                }
            } catch (_: Exception) {}
        }

        val libDir = File(javaDir, "lib")
        if (libDir.exists()) {
            val archCandidates = listOf("aarch64", "arm", "amd64", "x86_64", "i386", "x86")
            for (candidate in archCandidates) {
                if (File(libDir, candidate).isDirectory) return candidate
            }
        }

        return android.os.Build.SUPPORTED_ABIS.firstOrNull()?.let { abi ->
            when {
                abi.startsWith("arm64") -> "aarch64"
                abi.startsWith("armeabi") -> "arm"
                abi.startsWith("x86_64") -> "x86_64"
                abi.startsWith("x86") -> "x86"
                else -> "aarch64"
            }
        } ?: "aarch64"
    }
    
    private fun detectVendor(javaDir: File): String {
        return "Unknown"
    }
}
