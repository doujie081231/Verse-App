package com.verse.explorer.app.launch

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

class JavaManager(private val context: Context) {

    companion object {
        private const val TAG = "JavaManager"
        
        val JAVA_VERSIONS = listOf(
            JavaVersion("8", "Java 8", "1.8", listOf(8)),
            JavaVersion("11", "Java 11", "11", listOf(11)),
            JavaVersion("17", "Java 17", "17", listOf(17)),
            JavaVersion("21", "Java 21", "21", listOf(21)),
            JavaVersion("25", "Java 25", "25", listOf(25))
        )
        
        val RECOMMENDED_VERSIONS = mapOf(
            "1.21" to "21",
            "1.20.5" to "21",
            "1.20.4" to "17",
            "1.20" to "17",
            "1.19" to "17",
            "1.18" to "17",
            "1.17" to "17",
            "1.16" to "8",
            "1.15" to "8",
            "1.14" to "8",
            "1.13" to "8",
            "1.12" to "8",
            "1.11" to "8",
            "1.10" to "8",
            "1.9" to "8",
            "1.8" to "8",
            "1.7" to "8"
        )
        
        fun getRecommendedJavaVersion(mcVersion: String): String {
            val majorVersion = mcVersion.substringBefore(".").toIntOrNull() ?: return "17"
            val minorVersion = mcVersion.substringAfter(".").substringBefore(".").toIntOrNull() ?: 0
            
            val versionKey = if (majorVersion >= 1 && minorVersion >= 20) {
                if (minorVersion >= 20 && minorVersion < 21) "1.20"
                else if (minorVersion >= 19) "1.19"
                else "1.$minorVersion"
            } else {
                "1.$majorVersion"
            }
            
            return RECOMMENDED_VERSIONS[versionKey] ?: "17"
        }
    }

    data class JavaVersion(
        val id: String,
        val displayName: String,
        val version: String,
        val majorVersions: List<Int>
    )

    data class JavaRuntime(
        val version: String,
        val versionNumber: Int,
        val path: String,
        val isSystem: Boolean = false,
        val isEmbedded: Boolean = false
    ) {
        fun getExecutable(): String {
            return if (isSystem) {
                path
            } else {
                File(path, "bin/java").absolutePath
            }
        }
    }

    private val embeddedJavaDirs = listOf(
        File(context.filesDir, "jre"),
        File(context.filesDir, "java")
    )

    fun getInstalledJavas(): List<JavaRuntime> {
        val runtimes = mutableListOf<JavaRuntime>()
        
        runtimes.addAll(findSystemJavas())
        
        runtimes.addAll(findEmbeddedJavas())
        
        return runtimes
    }

    private fun findSystemJavas(): List<JavaRuntime> {
        val runtimes = mutableListOf<JavaRuntime>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val systemJavaPath = System.getProperty("java.home")
            if (!systemJavaPath.isNullOrEmpty()) {
                val version = System.getProperty("java.version") ?: "17"
                val versionNumber = parseJavaVersion(version)
                runtimes.add(JavaRuntime(
                    version = version,
                    versionNumber = versionNumber,
                    path = File(systemJavaPath, "bin/java").absolutePath,
                    isSystem = true
                ))
            }
        }
        
        return runtimes
    }

    private fun findEmbeddedJavas(): List<JavaRuntime> {
        val runtimes = mutableListOf<JavaRuntime>()
        
        for (embeddedJavaDir in embeddedJavaDirs) {
            if (!embeddedJavaDir.exists()) continue
            
            embeddedJavaDir.listFiles()?.forEach { javaDir ->
                if (javaDir.isDirectory) {
                    val releaseFile = File(javaDir, "release")
                    if (releaseFile.exists()) {
                        try {
                            val content = releaseFile.readText()
                            val versionMatch = Regex("JAVA_VERSION=\"([^\"]+)\"").find(content)
                            if (versionMatch != null) {
                                val version = versionMatch.groupValues[1]
                                val versionNumber = parseJavaVersion(version)
                                runtimes.add(JavaRuntime(
                                    version = version,
                                    versionNumber = versionNumber,
                                    path = javaDir.absolutePath,
                                    isEmbedded = true
                                ))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to read release file for ${javaDir.name}", e)
                        }
                    }
                }
            }
        }
        
        return runtimes
    }

    fun parseJavaVersion(version: String): Int {
        val parts = version.split(".")
        return if (parts.isNotEmpty()) {
            if (parts[0] == "1" && parts.size > 1) {
                parts[1].toIntOrNull() ?: 8
            } else {
                parts[0].toIntOrNull() ?: 8
            }
        } else {
            8
        }
    }

    fun getJavaByVersion(versionId: String): JavaRuntime? {
        return getInstalledJavas().find { 
            it.versionNumber == parseJavaVersion(versionId) || 
            it.version == versionId 
        }
    }

    fun getBestJava(mcVersion: String): JavaRuntime? {
        val recommendedVersion = getRecommendedJavaVersion(mcVersion)
        val installedJavas = getInstalledJavas()
        
        val recommended = installedJavas.find { 
            it.versionNumber == parseJavaVersion(recommendedVersion) 
        }
        if (recommended != null) return recommended
        
        val higher = installedJavas.filter { 
            it.versionNumber >= parseJavaVersion(recommendedVersion) 
        }.minByOrNull { it.versionNumber }
        if (higher != null) return higher
        
        return installedJavas.maxByOrNull { it.versionNumber }
    }

    fun isJavaInstalled(versionId: String): Boolean {
        return getJavaByVersion(versionId) != null
    }

    fun deleteJavaByVersion(versionId: String): Boolean {
        val versionNumber = parseJavaVersion(versionId)
        var deleted = false

        for (embeddedJavaDir in embeddedJavaDirs) {
            if (!embeddedJavaDir.exists()) continue

            embeddedJavaDir.listFiles()?.forEach { javaDir ->
                if (!javaDir.isDirectory) return@forEach

                val releaseFile = File(javaDir, "release")
                var matches = false

                if (releaseFile.exists()) {
                    try {
                        val content = releaseFile.readText()
                        val versionMatch = Regex("JAVA_VERSION=\"([^\"]+)\"").find(content)
                        if (versionMatch != null) {
                            val v = parseJavaVersion(versionMatch.groupValues[1])
                            matches = v == versionNumber
                        }
                    } catch (_: Exception) {}
                }

                if (!matches) {
                    val dirName = javaDir.name.lowercase()
                    matches = dirName.contains("jre$versionNumber") ||
                              dirName.contains("java$versionNumber") ||
                              dirName.contains("$versionNumber")
                }

                if (matches) {
                    try {
                        if (javaDir.deleteRecursively()) {
                            deleted = true
                            Log.i(TAG, "Deleted Java directory: ${javaDir.absolutePath}")
                        } else {
                            Log.w(TAG, "Failed to delete Java directory: ${javaDir.absolutePath}")
                            deleteRecursiveForce(javaDir)
                            if (!javaDir.exists()) deleted = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting Java directory: ${javaDir.absolutePath}", e)
                        deleteRecursiveForce(javaDir)
                        if (!javaDir.exists()) deleted = true
                    }
                }
            }
        }

        return deleted
    }

    private fun deleteRecursiveForce(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursiveForce(child)
            }
        }
        try {
            file.setWritable(true)
            file.setReadable(true)
            file.setExecutable(true)
            file.delete()
        } catch (_: Exception) {}
    }

    fun getJavaInfo(): JSONObject {
        val installed = getInstalledJavas()
        val obj = JSONObject()
        
        obj.put("installedCount", installed.size)
        
        val installedArray = org.json.JSONArray()
        installed.forEach { runtime ->
            val runtimeObj = JSONObject()
            runtimeObj.put("version", runtime.version)
            runtimeObj.put("versionNumber", runtime.versionNumber)
            runtimeObj.put("path", runtime.path)
            runtimeObj.put("isSystem", runtime.isSystem)
            runtimeObj.put("isEmbedded", runtime.isEmbedded)
            installedArray.put(runtimeObj)
        }
        obj.put("installed", installedArray)
        
        val availableArray = org.json.JSONArray()
        JAVA_VERSIONS.forEach { version ->
            val versionObj = JSONObject()
            versionObj.put("id", version.id)
            versionObj.put("displayName", version.displayName)
            versionObj.put("installed", isJavaInstalled(version.id))
            availableArray.put(versionObj)
        }
        obj.put("available", availableArray)
        
        return obj
    }
}
