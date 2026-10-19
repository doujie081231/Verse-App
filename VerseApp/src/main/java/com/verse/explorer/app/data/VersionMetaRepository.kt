package com.verse.explorer.app.data

import android.content.Context
import com.google.gson.Gson

/**
 * 版本元数据（与启动设置无关的展示信息）：版本摘要、自定义信息、列表置顶。
 * 独立于 VersionSettingsRepository 持久化，不受"跟随全局/独立设置"切换影响。
 */
object VersionMetaRepository {

    data class VersionMeta(
        val summary: String = "",
        val customInfo: String = "",
        val pinned: Boolean = false
    )

    private val gson = Gson()
    private var prefs: android.content.SharedPreferences? = null

    private fun p(context: Context): android.content.SharedPreferences {
        val existing = prefs
        if (existing != null) return existing
        val created = context.applicationContext
            .getSharedPreferences("version_meta", Context.MODE_PRIVATE)
        prefs = created
        return created
    }

    private fun key(versionId: String) = "meta_$versionId"

    fun get(context: Context, versionId: String): VersionMeta {
        val raw = p(context).getString(key(versionId), null) ?: return VersionMeta()
        return runCatching { gson.fromJson(raw, VersionMeta::class.java) }.getOrNull() ?: VersionMeta()
    }

    fun set(context: Context, versionId: String, meta: VersionMeta) {
        p(context).edit().putString(key(versionId), gson.toJson(meta)).apply()
    }

    fun setSummary(context: Context, versionId: String, summary: String) =
        set(context, versionId, get(context, versionId).copy(summary = summary.trim()))

    fun setCustomInfo(context: Context, versionId: String, info: String) =
        set(context, versionId, get(context, versionId).copy(customInfo = info.trim()))

    fun setPinned(context: Context, versionId: String, value: Boolean) =
        set(context, versionId, get(context, versionId).copy(pinned = value))

    /** 版本重命名后迁移元数据；旧记录清除 */
    fun rename(context: Context, oldId: String, newId: String) {
        val store = p(context)
        val raw = store.getString(key(oldId), null) ?: return
        store.edit().putString(key(newId), raw).remove(key(oldId)).apply()
    }

    /** 版本删除后清理元数据 */
    fun remove(context: Context, versionId: String) {
        p(context).edit().remove(key(versionId)).apply()
    }
}
