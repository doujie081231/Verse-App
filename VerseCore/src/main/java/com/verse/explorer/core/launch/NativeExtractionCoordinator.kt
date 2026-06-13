package com.verse.explorer.core.launch

import android.content.Context
import android.os.Build
import com.verse.explorer.core.config.GameConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object NativeExtractionCoordinator {

    private const val MARKER_FILE = ".native_version"

    private val isArm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")
    private val isArm32 = Build.SUPPORTED_ABIS.contains("armeabi-v7a")
    private val isX86_64 = Build.SUPPORTED_ABIS.contains("x86_64")
    private val isX86 = Build.SUPPORTED_ABIS.contains("x86")

    private val gson = Gson()

    fun ensureNatives(context: Context, versionId: String, gameDir: File, nativesDir: File): Boolean {
        val versionHash = computeVersionHash(versionId)
        val markerFile = File(nativesDir, MARKER_FILE)

        if (markerFile.exists()) {
            val storedHash = try { markerFile.readText().trim() } catch (_: Exception) { "" }
            if (storedHash == versionHash) {
                if (hasValidNatives(nativesDir)) return true
            }
        }

        if (!nativesDir.exists()) nativesDir.mkdirs()

        val builtinOk = BuiltinNatives.extractBuiltinNatives(context, nativesDir) > 0
                && BuiltinNatives.hasAllRequiredNatives(nativesDir)

        if (!builtinOk) {
            val librariesDir = GameConfig.getLibrariesDir(gameDir)
            val versionsDir = GameConfig.getVersionsDir(gameDir)
            extractFromClassifierJars(versionsDir, librariesDir, nativesDir, versionId)
        }

        val success = hasValidNatives(nativesDir)
        if (success) {
            try { markerFile.writeText(versionHash) } catch (_: Exception) {}
        }
        return success
    }

    private fun computeVersionHash(versionId: String): String {
        val arch = when {
            isArm64 -> "arm64-v8a"
            isArm32 -> "armeabi-v7a"
            isX86_64 -> "x86_64"
            isX86 -> "x86"
            else -> "unknown"
        }
        val raw = "$versionId|$arch"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun hasValidNatives(nativesDir: File): Boolean {
        if (!nativesDir.exists()) return false
        val soFiles = nativesDir.listFiles()?.filter {
            it.name.endsWith(".so") && it.isFile && it.length() > 1024
        } ?: return false
        return soFiles.isNotEmpty()
    }

    private fun extractFromClassifierJars(
        versionsDir: File,
        librariesDir: File,
        nativesDir: File,
        versionId: String
    ) {
        val versionJsonFile = File(versionsDir, "$versionId/$versionId.json")
        if (!versionJsonFile.exists()) return

        val json = try {
            gson.fromJson(versionJsonFile.readText(), JsonObject::class.java)
        } catch (_: Exception) {
            return
        }

        val libraries = json.getAsJsonArray("libraries") ?: return
        val classifierKeys = resolveClassifierKeys()

        for (libElement in libraries) {
            val lib = libElement.asJsonObject
            val rules = lib.getAsJsonArray("rules")
            if (rules != null && !evaluateRules(rules)) continue

            val downloads = lib.getAsJsonObject("downloads") ?: continue
            val classifiers = downloads.getAsJsonObject("classifiers") ?: continue
            val nativesInfo = lib.getAsJsonObject("natives")

            var resolvedKey: String? = null
            for (key in classifierKeys) {
                if (nativesInfo != null && nativesInfo.has(key)) {
                    resolvedKey = nativesInfo.get(key)?.asString
                    if (resolvedKey == null || !classifiers.has(resolvedKey)) {
                        resolvedKey = key
                    }
                    break
                }
                if (classifiers.has(key)) {
                    resolvedKey = key
                    break
                }
            }

            if (resolvedKey == null) continue

            val nativeLib = classifiers.get(resolvedKey)?.asJsonObject ?: continue
            val nativePath = nativeLib.get("path")?.asString ?: continue
            val nativeFile = File(librariesDir, nativePath)

            if (nativeFile.exists() && nativeFile.length() > 1024) {
                extractNativeJar(nativeFile, nativesDir)
            }
        }
    }

    private fun resolveClassifierKeys(): List<String> {
        return when {
            isArm64 -> listOf("natives-linux-arm64", "natives-linux", "linux-arm64")
            isArm32 -> listOf("natives-linux-arm32", "natives-linux", "linux-arm32")
            isX86_64 -> listOf("natives-linux-x86_64", "natives-linux", "linux-x86_64")
            isX86 -> listOf("natives-linux", "linux")
            else -> listOf("natives-linux", "linux")
        }
    }

    private fun extractNativeJar(jarFile: File, targetDir: File) {
        try {
            ZipInputStream(jarFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && !entry.name.contains("META-INF")) {
                        val fileName = entry.name.substringAfterLast("/").ifEmpty { entry.name }
                        if (fileName.endsWith(".so") || fileName.endsWith(".dll") || fileName.endsWith(".dylib")) {
                            val file = File(targetDir, fileName)
                            file.outputStream().use { output -> zipIn.copyTo(output) }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (_: Exception) {}
    }

    private fun evaluateRules(rules: com.google.gson.JsonArray): Boolean {
        if (rules.size() == 0) return true
        for (ruleElement in rules) {
            val rule = ruleElement.asJsonObject
            val action = rule.get("action")?.asString ?: continue
            val os = rule.getAsJsonObject("os")
            val features = rule.getAsJsonObject("features")
            if (features != null) continue
            if (os != null) {
                val osName = os.get("name")?.asString ?: continue
                if (osName == "linux" || osName == "osx") {
                    return action == "allow"
                }
            } else {
                return action == "allow"
            }
        }
        return false
    }
}
