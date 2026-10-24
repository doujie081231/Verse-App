package com.verse.explorer.launcher.renderer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 已安装渲染器插件的识别与解析。
 *
 * 插件以普通 APK 形式安装，读取其 AndroidManifest 元数据：
 *  - 新一代：`fclPlugin_V2` 指向字符串资源，内容为 JSON 渲染器配置；
 *  - 旧一代：`fclPlugin`/`zalithRendererPlugin=true` + `renderer`/`des`/`pojavEnv` 字符串。
 * 解析结果作为 [RendererManager.RendererConfig] 合并进渲染器列表，
 * 唯一标识使用插件包名，避免不同插件使用相同 rendererId 时冲突。
 */
object RendererPluginManager {

    private const val TAG = "RendererPlugin"

    private const val META_V2 = "fclPlugin_V2"
    private const val META_V1 = "fclPlugin"
    private const val META_ALT = "zalithRendererPlugin"

    private const val QUERY_FLAGS =
        PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES

    @Volatile
    private var loaded = false

    @Volatile
    private var plugins: List<RendererManager.RendererConfig> = emptyList()

    /** 扫描已安装插件；[force] 为 true 时重扫（安装/卸载插件后刷新） */
    fun load(context: Context, force: Boolean = false): List<RendererManager.RendererConfig> {
        synchronized(this) {
            if (loaded && !force) return plugins
            plugins = scan(context.applicationContext)
            loaded = true
            return plugins
        }
    }

    fun ensureLoaded(context: Context): List<RendererManager.RendererConfig> =
        if (loaded) plugins else load(context)

    fun all(): List<RendererManager.RendererConfig> = plugins

    /** 按唯一标识（包名）或插件提供的 rendererId 查找 */
    fun find(id: String): RendererManager.RendererConfig? {
        if (id.isEmpty()) return null
        return plugins.firstOrNull {
            it.id.equals(id, ignoreCase = true) ||
                it.pluginPackage.equals(id, ignoreCase = true)
        } ?: if (id.equals("mobileglues", ignoreCase = true)) {
            // 旧设置里保存的内置 MobileGlues 标识：命中已安装的同类插件
            plugins.firstOrNull {
                it.glName.contains("mobileglues", ignoreCase = true) ||
                    it.pluginPackage.contains("mobileglues", ignoreCase = true) ||
                    it.displayName.contains("MobileGlues", ignoreCase = true)
            }
        } else null
    }

    private fun scan(context: Context): List<RendererManager.RendererConfig> {
        val pm = context.packageManager
        val resolved = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN), QUERY_FLAGS)
        }.getOrDefault(emptyList())

        val result = LinkedHashMap<String, RendererManager.RendererConfig>()
        for (item in resolved) {
            val info = item.activityInfo?.applicationInfo ?: continue
            if (info.packageName == context.packageName) continue
            if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            val config = runCatching { parsePlugin(context, info) }
                .onFailure { Log.w(TAG, "解析插件失败 ${info.packageName}: ${it.message}") }
                .getOrNull() ?: continue
            result[config.pluginPackage] = config
        }

        Log.i(
            TAG,
            "已识别渲染器插件: " + if (result.isEmpty()) "无"
            else result.values.joinToString { "${it.displayName}(${it.pluginPackage})" }
        )
        return result.values.toList()
    }

    private fun parsePlugin(
        context: Context,
        info: ApplicationInfo
    ): RendererManager.RendererConfig? {
        val metaData = info.metaData ?: return null
        val nativeDir = info.nativeLibraryDir ?: return null

        val v2Res = runCatching { metaData.getInt(META_V2, -1) }.getOrDefault(-1)
        if (v2Res > 0) {
            parseV2(context, info, metaData, v2Res, nativeDir)?.let { return it }
        }

        val isV1 = runCatching {
            metaData.getBoolean(META_V1, false) || metaData.getBoolean(META_ALT, false)
        }.getOrDefault(false)
        if (!isV1) return null
        return parseV1(context, info, metaData, nativeDir)
    }

    /** 新一代插件：JSON 配置，路径中的 `**|` 前缀代表插件 native 库目录 */
    private fun parseV2(
        context: Context,
        info: ApplicationInfo,
        metaData: Bundle,
        resId: Int,
        nativeDir: String
    ): RendererManager.RendererConfig? {
        val pm = context.packageManager
        val jsonText = runCatching {
            pm.getResourcesForApplication(info).getString(resId)
        }.getOrNull() ?: return null
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null

        val appName = runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName)
        val displayName = json.optString("displayName").ifBlank { appName }
        val rendererId = json.optString("rendererId").ifBlank { "opengles3" }
        val glPath = resolveToken(json.optString("rendererGLPath"), nativeDir) ?: return null
        val eglPath = resolveToken(json.optString("rendererEGLPath", "libEGL.so"), nativeDir)
            ?: "libEGL.so"
        val glFile = File(glPath)
        if (!glFile.isFile) {
            Log.w(TAG, "${info.packageName} 的 GL 库不存在: $glPath")
            return null
        }

        val libDir = glFile.parentFile?.absolutePath ?: nativeDir
        val dlopenLibs = mutableListOf<String>()
        json.optJSONArray("dlopenLibPaths")?.let { array ->
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                val path = resolveToken(value, nativeDir) ?: continue
                dlopenLibs.add(File(path).name)
            }
        }

        return RendererManager.RendererConfig(
            displayName = displayName,
            id = info.packageName,
            glName = glFile.name,
            eglName = if (eglPath.startsWith("/")) File(eglPath).name else eglPath,
            pojavRenderer = rendererId,
            envVars = parseV2Env(json.optJSONArray("env")),
            requiredLibs = emptyList(),
            minMcVersion = json.optString("minMCVer", ""),
            maxMcVersion = json.optString("maxMCVer", ""),
            nativeLibDir = libDir,
            pluginPackage = info.packageName,
            pluginAppName = appName,
            dlopenLibs = dlopenLibs
        )
    }

    /** 旧一代插件：renderer="rendererId:gl:egl" + des + pojavEnv */
    private fun parseV1(
        context: Context,
        info: ApplicationInfo,
        metaData: Bundle,
        nativeDir: String
    ): RendererManager.RendererConfig? {
        val pm = context.packageManager
        val rendererString = metaData.getString("renderer") ?: return null
        val parts = rendererString.split(":")
        if (parts.size < 2) return null

        val glName = parts[1]
        if (glName.isBlank() || !File(nativeDir, glName).isFile) {
            Log.w(TAG, "${info.packageName} 的 GL 库不存在: $nativeDir/$glName")
            return null
        }

        val appName = runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName)
        val displayName = metaData.getString("des") ?: appName
        var rendererId = parts[0]
        val env = LinkedHashMap<String, String?>()
        val dlopenLibs = mutableListOf<String>()

        metaData.getString("pojavEnv").orEmpty().split(":").forEach { entry ->
            val index = entry.indexOf('=')
            if (index <= 0) return@forEach
            val key = entry.substring(0, index)
            val value = entry.substring(index + 1)
            when (key) {
                "POJAV_RENDERER" -> rendererId = value
                "DLOPEN" -> value.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { dlopenLibs.add(it) }
                "LIB_MESA_NAME", "MESA_LIBRARY" -> env[key] = File(nativeDir, value).absolutePath
                else -> env[key] = value
            }
        }

        return RendererManager.RendererConfig(
            displayName = displayName,
            id = info.packageName,
            glName = glName,
            eglName = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "libEGL.so",
            pojavRenderer = rendererId,
            envVars = env,
            requiredLibs = emptyList(),
            minMcVersion = metaData.getVersionString("minMCVer") ?: "",
            maxMcVersion = metaData.getVersionString("maxMCVer") ?: "",
            nativeLibDir = nativeDir,
            pluginPackage = info.packageName,
            pluginAppName = appName,
            dlopenLibs = dlopenLibs
        )
    }

    /** 可配置型环境变量取默认值（当前不做插件配置界面，使用插件默认） */
    private fun parseV2Env(array: JSONArray?): Map<String, String?> {
        val env = LinkedHashMap<String, String?>()
        if (array == null) return env
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val key = item.optString("key")
            if (key.isEmpty()) continue
            when (item.optString("type")) {
                "SelectableEnv" -> {
                    val enabled = item.isNull("check") || item.optBoolean("check", true)
                    if (enabled) {
                        env[key] = item.optJSONObject("items")?.optString("defaultValue").orEmpty()
                    }
                }
                "CustomizableEnv" -> {
                    val value = item.optString("defaultValue")
                    if (value.isNotEmpty()) env[key] = value
                }
                "ToggleableEnv" -> {
                    val enabled = item.isNull("toggle") || item.optBoolean("toggle", true)
                    if (enabled) env[key] = item.optString("value")
                }
                else -> env[key] = item.optString("value")
            }
        }
        return env
    }

    private fun resolveToken(value: String, nativeDir: String): String? {
        if (value.isBlank()) return null
        return if (value.startsWith("**|")) {
            File(nativeDir, value.removePrefix("**|")).absolutePath
        } else value
    }

    private fun Bundle.getVersionString(key: String): String? {
        if (!containsKey(key)) return null
        return runCatching {
            when (val value = get(key)) {
                is String -> value
                is Number -> value.toString()
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
