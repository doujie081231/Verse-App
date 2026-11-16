package com.verse.explorer.launcher.renderer.config

import android.content.Context
import org.json.JSONObject

/**
 * 渲染器配置的持久化。
 *
 * 两套存储：全局（对所有版本生效）与版本级（只对某个版本生效）。
 * 用 SharedPreferences + 扁平 JSON 而不是每版本一个文件：仓库里
 * [com.verse.explorer.core.data.VersionSettingsRepository] 已经是这个模式，
 * 读写路径少一条，迁移和备份都好处理。
 */
class RendererConfigStore(context: Context) {

    private val appContext = context.applicationContext

    private val globalPrefs by lazy {
        appContext.getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE)
    }
    private val versionPrefs by lazy {
        appContext.getSharedPreferences(PREFS_VERSION, Context.MODE_PRIVATE)
    }

    // ---------- 全局 ----------

    fun loadGlobal(rendererId: String, spec: RendererConfigSpec? = null): Map<String, Any> {
        val stored = decode(globalPrefs.getString(globalKey(rendererId), null))
        return if (spec != null) RendererConfigResolver.pruneStored(spec, stored) else stored
    }

    fun saveGlobal(rendererId: String, spec: RendererConfigSpec, values: Map<String, Any>) {
        val managed = RendererConfigResolver.pruneStored(spec, values)
        globalPrefs.edit().putString(globalKey(rendererId), encode(managed)).apply()
    }

    fun clearGlobal(rendererId: String) {
        globalPrefs.edit().remove(globalKey(rendererId)).apply()
    }

    fun hasGlobal(rendererId: String): Boolean =
        globalPrefs.contains(globalKey(rendererId))

    // ---------- 版本级 ----------

    fun loadVersion(
        versionId: String,
        rendererId: String,
        spec: RendererConfigSpec? = null
    ): Map<String, Any> {
        val stored = decode(versionPrefs.getString(versionKey(versionId, rendererId), null))
        return if (spec != null) RendererConfigResolver.pruneStored(spec, stored) else stored
    }

    fun saveVersion(
        versionId: String,
        rendererId: String,
        spec: RendererConfigSpec,
        values: Map<String, Any>
    ) {
        val managed = RendererConfigResolver.pruneStored(spec, values)
        versionPrefs.edit().putString(versionKey(versionId, rendererId), encode(managed)).apply()
    }

    fun clearVersion(versionId: String, rendererId: String) {
        versionPrefs.edit().remove(versionKey(versionId, rendererId)).apply()
    }

    fun hasVersion(versionId: String, rendererId: String): Boolean =
        versionPrefs.contains(versionKey(versionId, rendererId))

    // ---------- 迁移 ----------

    /**
     * 一次性清洗所有已存配置（全局 + 版本级）。
     *
     * @param flag    迁移标记键，执行过之后直接返回，不再遍历
     * @param transform 清洗函数，返回原对象表示无需改写
     * @return 是否有键被改写
     */
    fun migrateAll(flag: String, transform: (Map<String, Any>) -> Map<String, Any>): Boolean {
        if (globalPrefs.getBoolean(flag, false)) return false
        var changed = false
        for (prefs in arrayOf(globalPrefs, versionPrefs)) {
            val editor = prefs.edit()
            for ((key, raw) in prefs.all) {
                if (key == flag || raw !is String) continue
                val before = decode(raw)
                val after = transform(before)
                if (after != before) {
                    editor.putString(key, encode(after))
                    changed = true
                }
            }
            editor.apply()
        }
        globalPrefs.edit().putBoolean(flag, true).apply()
        return changed
    }

    // ---------- 序列化 ----------

    private fun encode(values: Map<String, Any>): String {
        val json = JSONObject()
        for ((key, value) in values) {
            json.put(key, value)
        }
        return json.toString()
    }

    private fun decode(raw: String?): Map<String, Any> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            val out = LinkedHashMap<String, Any>(json.length())
            for (key in json.keys()) {
                when (val value = json.opt(key)) {
                    is Boolean, is Int -> out[key] = value
                    is Number -> out[key] = value.toInt()
                    is String -> out[key] = value
                }
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun globalKey(rendererId: String): String = "g:$rendererId"

    private fun versionKey(versionId: String, rendererId: String): String = "v:$versionId:$rendererId"

    private companion object {
        const val PREFS_GLOBAL = "verse_renderer_config"
        const val PREFS_VERSION = "verse_version_renderer_config"
    }
}
