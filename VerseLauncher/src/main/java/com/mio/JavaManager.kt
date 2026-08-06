package com.mio

import android.util.Log
import com.verse.explorer.launcher.fcl.game.JavaVersion
import com.verse.explorer.launcher.fcl.game.Version
import com.verse.explorer.launcher.fcl.util.FCLPath
import java.io.File

object JavaManager {

    private const val TAG = "JavaManager"

    @JvmStatic
    val javaList: MutableList<JavaVersion> = mutableListOf()

    init {
        scanJava()
    }

    private fun scanJava() {
        javaList.clear()
        val javaDir = File(FCLPath.JAVA_PATH)
        if (!javaDir.exists() || !javaDir.isDirectory) {
            Log.w(TAG, "Java path not found: ${FCLPath.JAVA_PATH}")
            return
        }
        javaDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val binDir = File(file, "bin")
                val javaExe = File(binDir, "java")
                val javaSh = File(binDir, "java.sh")
                if (javaExe.exists() || javaSh.exists()) {
                    val name = file.name
                    val versionName = detectVersionName(file)
                    javaList.add(JavaVersion(false, versionName, name))
                    Log.d(TAG, "Found Java: $name -> $versionName")
                }
            }
        }
        // Always add auto option
        javaList.add(0, JavaVersion.JAVA_AUTO)
    }

    private fun detectVersionName(javaHome: File): String {
        val releaseFile = File(javaHome, "release")
        if (releaseFile.exists()) {
            try {
                val content = releaseFile.readText()
                val lines = content.lines()
                for (line in lines) {
                    if (line.startsWith("JAVA_VERSION=")) {
                        val value = line.substringAfter("=").replace("\"", "").trim()
                        return value
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read release file: ${releaseFile.absolutePath}", e)
            }
        }
        return guessVersionFromName(javaHome.name)
    }

    private fun guessVersionFromName(name: String): String {
        return when {
            name.contains("8") || name.contains("1.8") -> "1.8"
            name.contains("11") -> "11"
            name.contains("17") -> "17"
            name.contains("21") -> "21"
            name.contains("25") -> "25"
            else -> "17"
        }
    }

    @JvmStatic
    fun getSuitableJavaVersion(version: Version?): JavaVersion {
        if (version == null) return JavaVersion.JAVA_AUTO
        val gameJavaVersion = version.javaVersion ?: return JavaVersion.JAVA_AUTO
        return getSuitableJavaVersion(gameJavaVersion.majorVersion)
    }

    @JvmStatic
    fun getSuitableJavaVersion(version: Int): JavaVersion {
        val targetName = when {
            version <= 8 -> "jre8"
            version <= 16 -> "jre17"
            version <= 20 -> "jre21"
            else -> "jre25"
        }
        val found = javaList.firstOrNull { it.name.contains(targetName, ignoreCase = true) }
        return found ?: javaList.firstOrNull { it !== JavaVersion.JAVA_AUTO } ?: JavaVersion.JAVA_AUTO
    }

    @JvmStatic
    fun getJavaFromVersionName(name: String): JavaVersion? {
        return javaList.firstOrNull { it.versionName == name || it.name == name }
    }
}
