package com.verse.explorer.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class VersionSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "verse_version_settings"

        private const val KEY_VERSION_PREFIX = "version_"
        
        const val DEFAULT_JAVA_VERSION = "auto"
        const val DEFAULT_MIN_MEMORY = 512
        const val DEFAULT_MAX_MEMORY = 4096
        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
        const val DEFAULT_FULLSCREEN = true
        const val DEFAULT_RENDERER = "GL4ES"
        const val DEFAULT_SHOW_VIRTUAL_MOUSE = true
        const val DEFAULT_VIRTUAL_MOUSE_OPACITY = 0.7f
        const val DEFAULT_NO_JVM_ARGS = false
        const val DEFAULT_NO_GAME_CHECK = false
        const val DEFAULT_NO_MODPACK_CHECK = false
        const val DEFAULT_NO_JVM_CHECK = false
        const val DEFAULT_NO_NATIVES_PATCH = false
        const val DEFAULT_USE_NATIVE_GLFW = false
        const val DEFAULT_USE_NATIVE_OPENAL = false
    }

    fun getVersionSettings(versionId: String): VersionSettings {
        val jsonStr = prefs.getString(KEY_VERSION_PREFIX + versionId, null)
        return if (jsonStr != null) {
            try {
                VersionSettings.fromJson(jsonStr)
            } catch (e: Exception) {
                VersionSettings()
            }
        } else {
            VersionSettings()
        }
    }

    fun setVersionSettings(versionId: String, settings: VersionSettings) {
        prefs.edit().putString(KEY_VERSION_PREFIX + versionId, settings.toJson()).apply()
    }

    fun deleteVersionSettings(versionId: String) {
        prefs.edit().remove(KEY_VERSION_PREFIX + versionId).apply()
    }

    fun getAllVersionSettings(): Map<String, VersionSettings> {
        val result = mutableMapOf<String, VersionSettings>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_VERSION_PREFIX) && value is String) {
                val versionId = key.substring(KEY_VERSION_PREFIX.length)
                try {
                    result[versionId] = VersionSettings.fromJson(value)
                } catch (e: Exception) {
                }
            }
        }
        return result
    }

    data class VersionSettings(
        val javaVersion: String = DEFAULT_JAVA_VERSION,
        val minMemory: Int = DEFAULT_MIN_MEMORY,
        val maxMemory: Int = DEFAULT_MAX_MEMORY,
        val width: Int = DEFAULT_WIDTH,
        val height: Int = DEFAULT_HEIGHT,
        val fullscreen: Boolean = DEFAULT_FULLSCREEN,
        val renderer: String = DEFAULT_RENDERER,
        val showVirtualMouse: Boolean = DEFAULT_SHOW_VIRTUAL_MOUSE,
        val virtualMouseOpacity: Float = DEFAULT_VIRTUAL_MOUSE_OPACITY,
        val noJvmArgs: Boolean = DEFAULT_NO_JVM_ARGS,
        val noGameCheck: Boolean = DEFAULT_NO_GAME_CHECK,
        val noModpackCheck: Boolean = DEFAULT_NO_MODPACK_CHECK,
        val noJvmCheck: Boolean = DEFAULT_NO_JVM_CHECK,
        val noNativesPatch: Boolean = DEFAULT_NO_NATIVES_PATCH,
        val useNativeGlfw: Boolean = DEFAULT_USE_NATIVE_GLFW,
        val useNativeOpenal: Boolean = DEFAULT_USE_NATIVE_OPENAL,
        val customJvmArgs: String = "",
        val customGameArgs: String = "",
        val wrapperCommand: String = "",
        val preLaunchCommand: String = "",
        val postExitCommand: String = ""
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("javaVersion", javaVersion)
                put("minMemory", minMemory)
                put("maxMemory", maxMemory)
                put("width", width)
                put("height", height)
                put("fullscreen", fullscreen)
                put("renderer", renderer)
                put("showVirtualMouse", showVirtualMouse)
                put("virtualMouseOpacity", virtualMouseOpacity)
                put("noJvmArgs", noJvmArgs)
                put("noGameCheck", noGameCheck)
                put("noModpackCheck", noModpackCheck)
                put("noJvmCheck", noJvmCheck)
                put("noNativesPatch", noNativesPatch)
                put("useNativeGlfw", useNativeGlfw)
                put("useNativeOpenal", useNativeOpenal)
                put("customJvmArgs", customJvmArgs)
                put("customGameArgs", customGameArgs)
                put("wrapperCommand", wrapperCommand)
                put("preLaunchCommand", preLaunchCommand)
                put("postExitCommand", postExitCommand)
            }.toString()
        }

        companion object {
            fun fromJson(json: String): VersionSettings {
                val obj = JSONObject(json)
                return VersionSettings(
                    javaVersion = obj.optString("javaVersion", DEFAULT_JAVA_VERSION),
                    minMemory = obj.optInt("minMemory", DEFAULT_MIN_MEMORY),
                    maxMemory = obj.optInt("maxMemory", DEFAULT_MAX_MEMORY),
                    width = obj.optInt("width", DEFAULT_WIDTH),
                    height = obj.optInt("height", DEFAULT_HEIGHT),
                    fullscreen = obj.optBoolean("fullscreen", DEFAULT_FULLSCREEN),
                    renderer = obj.optString("renderer", DEFAULT_RENDERER),
                    showVirtualMouse = obj.optBoolean("showVirtualMouse", DEFAULT_SHOW_VIRTUAL_MOUSE),
                    virtualMouseOpacity = obj.optDouble("virtualMouseOpacity", DEFAULT_VIRTUAL_MOUSE_OPACITY.toDouble()).toFloat(),
                    noJvmArgs = obj.optBoolean("noJvmArgs", DEFAULT_NO_JVM_ARGS),
                    noGameCheck = obj.optBoolean("noGameCheck", DEFAULT_NO_GAME_CHECK),
                    noModpackCheck = obj.optBoolean("noModpackCheck", DEFAULT_NO_MODPACK_CHECK),
                    noJvmCheck = obj.optBoolean("noJvmCheck", DEFAULT_NO_JVM_CHECK),
                    noNativesPatch = obj.optBoolean("noNativesPatch", DEFAULT_NO_NATIVES_PATCH),
                    useNativeGlfw = obj.optBoolean("useNativeGlfw", DEFAULT_USE_NATIVE_GLFW),
                    useNativeOpenal = obj.optBoolean("useNativeOpenal", DEFAULT_USE_NATIVE_OPENAL),
                    customJvmArgs = obj.optString("customJvmArgs", ""),
                    customGameArgs = obj.optString("customGameArgs", ""),
                    wrapperCommand = obj.optString("wrapperCommand", ""),
                    preLaunchCommand = obj.optString("preLaunchCommand", ""),
                    postExitCommand = obj.optString("postExitCommand", "")
                )
            }
        }
    }
}
