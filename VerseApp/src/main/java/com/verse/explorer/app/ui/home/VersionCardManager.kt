package com.verse.explorer.app.ui.home

import android.content.Context
import com.google.gson.Gson
import com.verse.explorer.core.version.MinecraftVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 主页版本卡片记录的管理器：
 * 记录持久化到私有偏好存储，版本列表加载后重算各卡片的可用性；
 * 主页网格以本管理器的记录为准补齐缺失的卡片。
 */
object VersionCardManager {

    private const val PREFS = "home_version_cards"
    private const val KEY_RECORDS = "versionCards"
    private const val KEY_SEEDED = "seeded_v2"

    private val gson = Gson()

    private val _cards = MutableStateFlow<List<VersionCardState>>(emptyList())

    /** 全部版本卡片及其当前可用性状态 */
    val cards: StateFlow<List<VersionCardState>> = _cards.asStateFlow()

    fun initialize(context: Context) {
        synchronized(this) {
            if (_cards.value.isNotEmpty()) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _cards.value = loadRecords(prefs).map { VersionCardState(it, VersionCardStatus.Loading) }
        }
    }

    /** 是否已经做过首次固定（避免用户清空卡片后每次进主页又自动补回） */
    fun hasSeeded(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SEEDED, false)

    fun markSeeded(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SEEDED, true).apply()
    }

    /** 指定版本是否已存在对应卡片 */
    fun hasCard(versionId: String): Boolean =
        _cards.value.any { it.record.versionId == versionId }

    /**
     * 为指定版本创建主页卡片，已存在时返回 false
     */
    fun addCard(context: Context, versionId: String): Boolean {
        synchronized(this) {
            if (hasCard(versionId)) return false
            val record = VersionCardRecord(
                cardId = java.util.UUID.randomUUID().toString(),
                versionId = versionId
            )
            _cards.update { states -> states + VersionCardState(record, VersionCardStatus.Loading) }
            saveRecords(context)
        }
        return true
    }

    /** 移除指定卡片 */
    fun removeCard(context: Context, cardId: String) {
        synchronized(this) {
            val exists = _cards.value.any { it.record.cardId == cardId }
            if (!exists) return
            _cards.update { states -> states.filterNot { it.record.cardId == cardId } }
            saveRecords(context)
        }
    }

    /** 按版本移除其主页卡片（版本列表页取消固定时使用） */
    fun removeByVersionId(context: Context, versionId: String) {
        synchronized(this) {
            val ids = _cards.value.filter { it.record.versionId == versionId }.map { it.record.cardId }
            if (ids.isEmpty()) return
            _cards.update { states -> states.filterNot { it.record.versionId == versionId } }
            saveRecords(context)
        }
    }

    /**
     * 版本列表加载后重算全部卡片的可用性状态
     */
    fun refresh(context: Context, versions: List<MinecraftVersion>) {
        synchronized(this) {
            val updated = _cards.value.map { state ->
                val version = versions.firstOrNull { it.id == state.record.versionId }
                val status = if (version != null) VersionCardStatus.Available(version) else VersionCardStatus.Deleted
                state.copy(status = status)
            }
            if (updated != _cards.value) {
                _cards.value = updated
            }
        }
    }

    private fun loadRecords(prefs: android.content.SharedPreferences): List<VersionCardRecord> {
        val json = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        if (json.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson(json, Array<VersionCardRecord>::class.java)
                .orEmpty()
                .filter { it.cardId.isNotBlank() && it.versionId.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun saveRecords(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECORDS, gson.toJson(_cards.value.map { it.record })).apply()
    }
}
