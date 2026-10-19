package com.verse.explorer.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.verse.explorer.app.ui.VerseLogger

/**
 * 资源汉化名映射：assets/mod_translations.json 内置 [中文名, CurseForgeSlug, ModrinthSlug] 表，
 * 提供 slug→中文名的展示替换与"中文查询→英文关键词"的搜索映射。
 */
object ModTranslationMapper {

    @Volatile
    private var loaded = false

    /** slug（CurseForge / Modrinth）→ 中文名 */
    private val bySlug = HashMap<String, String>()

    /** 全部含中文名的条目（中文名, 英文关键词列表），供中文搜索 */
    private val zhEntries = ArrayList<Pair<String, List<String>>>()

    private val gson = Gson()

    /** 懒加载翻译表；重复调用无害。表缺失或损坏时保持空表，全部调用退化为直通。 */
    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            try {
                val type = object : TypeToken<List<List<String>>>() {}.type
                val raw: List<List<String>> = context.assets.open("mod_translations.json").use {
                    gson.fromJson(it.reader(Charsets.UTF_8), type)
                }
                raw.forEach { row ->
                    if (row.size < 3) return@forEach
                    val zh = row[0].trim()
                    val cf = row[1].trim().lowercase()
                    val mr = row[2].trim().lowercase()
                    if (zh.isEmpty()) return@forEach
                    val slugs = mutableListOf<String>()
                    if (cf.isNotEmpty() && bySlug.putIfAbsent(cf, zh) == null) slugs.add(cf)
                    if (mr.isNotEmpty() && mr != cf && bySlug.putIfAbsent(mr, zh) == null) slugs.add(mr)
                    if (slugs.isNotEmpty()) {
                        zhEntries.add(zh to slugs.map { it.replace('-', ' ') })
                    }
                }
                VerseLogger.info("资源汉化表加载完成：${bySlug.size} slug / ${zhEntries.size} 中文名", "ModI18n")
            } catch (e: Exception) {
                VerseLogger.warn("资源汉化表加载失败: ${e.message}", "ModI18n")
            }
        }
    }

    /** 按 slug 取中文名，无映射返回 null */
    fun translateBySlug(slug: String?): String? {
        if (slug.isNullOrBlank() || bySlug.isEmpty()) return null
        return bySlug[slug.trim().lowercase()]
    }

    /**
     * 中文查询 → 英文搜索关键词：返回中文名包含查询的条目的 slug 词组。
     * 与参照实现一致：中文无法直接命中英文 API，先经本地表转换为英文关键词。
     */
    fun searchZh(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty() || zhEntries.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        for ((zh, keywords) in zhEntries) {
            if (zh.contains(q)) {
                keywords.forEach { out.add(it) }
                if (out.size >= 8) break
            }
        }
        return out.toList()
    }

    /**
     * 已安装模组文件名 → 中文名：文件名（去扩展名、小写、分隔符归一）包含某 slug 即命中。
     * 逐条线性匹配；文件数有限，开销可接受。
     */
    fun translateFileName(fileName: String): String? {
        if (bySlug.isEmpty()) return null
        val base = fileName.removeSuffix(".jar").removeSuffix(".zip").lowercase()
        if (base.isEmpty()) return null
        for ((slug, zh) in bySlug) {
            if (base.contains(slug) || base.contains(slug.replace('-', '_'))) return zh
        }
        return null
    }
}
