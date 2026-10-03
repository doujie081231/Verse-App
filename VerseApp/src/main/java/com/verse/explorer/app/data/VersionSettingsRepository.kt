package com.verse.explorer.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 版本级设置存取。
 *
 * 每个 Minecraft 版本可拥有独立设置（usesGlobal=false），未开启独立设置时
 * 启动链读取全局设置（SettingsRepository）。存储键与取值语义对齐 FCL 的
 * version_settings 条目，便于日后互换。
 */
class VersionSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "verse_version_settings"
        private const val KEY_VERSION_PREFIX = "version_"

        const val JAVA_AUTO = "Auto"
        val LOADERS_FOR_MODS = listOf("fabric", "forge", "neoforge", "quilt")
    }

    fun getVersionSettings(versionId: String): VersionSettings {
        val jsonStr = prefs.getString(KEY_VERSION_PREFIX + versionId, null)
        return if (jsonStr != null) {
            try {
                VersionSettings.fromJson(jsonStr)
            } catch (_: Exception) {
                VersionSettings()
            }
        } else {
            VersionSettings()
        }
    }

    fun setVersionSettings(versionId: String, settings: VersionSettings) {
        prefs.edit().putString(KEY_VERSION_PREFIX + versionId, settings.toJson()).apply()
    }

    /** 开启版本独立设置：以当前全局值初始化一份版本私有设置（对齐 FCL specialize） */
    fun specialize(versionId: String): VersionSettings {
        val fromGlobal = VersionSettings(
            usesGlobal = false,
            graphicsBackend = SettingsRepository.getGraphicsBackend(),
            vkDriverSystem = SettingsRepository.getVkDriverSystem(),
            debugLog = SettingsRepository.getDebugLog(),
            javaArgs = SettingsRepository.getCustomJvmArgs(),
            renderer = SettingsRepository.getRenderer()
        )
        setVersionSettings(versionId, fromGlobal)
        return fromGlobal
    }

    /** 关闭版本独立设置：回到全局设置（对齐 FCL globalize） */
    fun globalize(versionId: String) {
        deleteVersionSettings(versionId)
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
                } catch (_: Exception) {
                }
            }
        }
        return result
    }

    /**
     * 版本设置数据模型（字段语义对齐 FCL VersionSetting）。
     * JSON 键名与 FCL 的序列化格式一致。
     */
    data class VersionSettings(
        /** false = 该版本使用独立设置；true = 跟随全局启动设置 */
        val usesGlobal: Boolean = true,
        /** 定向 Java 运行时名，JAVA_AUTO = 自动选择 */
        val java: String = JAVA_AUTO,
        /** 最大内存 MB，<=0 = 自动 */
        val maxMemory: Int = 0,
        /** 最小内存 MB，<=0 = 未指定 */
        val minMemory: Int = 0,
        val autoMemory: Boolean = true,
        /** 自定义 JVM 参数（空格分隔） */
        val javaArgs: String = "",
        /** 自定义游戏参数（空格分隔） */
        val minecraftArgs: String = "",
        val notCheckJVM: Boolean = false,
        val notCheckGame: Boolean = false,
        val notCheckMod: Boolean = false,
        /** 启动后自动加入的服务器，格式 ip 或 ip:port */
        val serverIp: String = "",
        /** 版本隔离：true = 运行目录为 versions/<id>/，false = 游戏根目录 */
        val isolateGameDir: Boolean = true,
        val graphicsBackend: String = "default",
        val vkDriverSystem: Boolean = false,
        /** 渲染器 id（nggl4es/gl4es/mobileglues），空 = 跟随全局 */
        val renderer: String = "",
        /** 驱动名（预留，当前由 vkDriverSystem 决定） */
        val driver: String = "",
        val debugLog: Boolean = false,
        /** 强制分辨率：true = 用 width/height 作为启动窗口尺寸，不跟随窗口缩放 */
        val forceResolution: Boolean = false,
        val width: Int = 0,
        val height: Int = 0,
        /** 控制布局文件名（不含 .json），空 = 默认布局 */
        val controller: String = ""
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("usesGlobal", usesGlobal)
                put("java", java)
                put("javaArgs", javaArgs)
                put("minecraftArgs", minecraftArgs)
                put("maxMemory", maxMemory)
                put("minMemory", minMemory)
                put("autoMemory", autoMemory)
                put("serverIp", serverIp)
                put("notCheckGame", notCheckGame)
                put("notCheckJVM", notCheckJVM)
                put("graphicsBackend", graphicsBackend)
                put("vulkanDriverSystem", vkDriverSystem)
                put("renderer", renderer)
                put("driver", driver)
                put("isolateGameDir", isolateGameDir)
                put("notCheckMod", notCheckMod)
                put("debugLog", debugLog)
                put("forceResolution", forceResolution)
                put("width", width)
                put("height", height)
                put("controller", controller)
            }.toString()
        }

        companion object {
            fun fromJson(json: String): VersionSettings {
                val obj = JSONObject(json)
                return VersionSettings(
                    usesGlobal = obj.optBoolean("usesGlobal", true),
                    java = obj.optString("java", JAVA_AUTO),
                    javaArgs = obj.optString("javaArgs", ""),
                    minecraftArgs = obj.optString("minecraftArgs", ""),
                    maxMemory = obj.optInt("maxMemory", 0),
                    minMemory = obj.optInt("minMemory", 0),
                    autoMemory = obj.optBoolean("autoMemory", true),
                    serverIp = obj.optString("serverIp", ""),
                    notCheckGame = obj.optBoolean("notCheckGame", false),
                    notCheckJVM = obj.optBoolean("notCheckJVM", false),
                    graphicsBackend = obj.optString("graphicsBackend", "default"),
                    vkDriverSystem = obj.optBoolean("vulkanDriverSystem", false),
                    renderer = obj.optString("renderer", ""),
                    driver = obj.optString("driver", ""),
                    isolateGameDir = obj.optBoolean("isolateGameDir", true),
                    notCheckMod = obj.optBoolean("notCheckMod", false),
                    debugLog = obj.optBoolean("debugLog", false),
                    forceResolution = obj.optBoolean("forceResolution", false),
                    width = obj.optInt("width", 0),
                    height = obj.optInt("height", 0),
                    controller = obj.optString("controller", "")
                )
            }
        }
    }
}
