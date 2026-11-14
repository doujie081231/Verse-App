package com.verse.explorer.app.ui.home

import android.content.Context
import com.google.gson.Gson

/**
 * 版本列表的自定义拖拽顺序。
 *
 * 只在用户真正拖动过条目之后写入；未写过时返回空列表，页面沿用原有的
 * 「置顶优先 + 版本名升序」默认排序。按游戏目录分别保存，切换目录互不干扰。
 *
 * 存的是版本 id 的完整有序列表，而不是每项一个序号：拖动一次整表重写，
 * 不会出现序号重复／空洞，新增版本自动排在末尾（按 id 升序）。
 */
object VersionOrderStore {

    private const val PREFS = "version_order"
    private val gson = Gson()

    /** null = 内置默认游戏目录，用固定哨兵串参与分桶 */
    private fun key(dirPath: String?) = "order_${(dirPath ?: "__default__").hashCode()}"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, dirPath: String?): List<String> {
        val raw = prefs(context).getString(key(dirPath), null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<String>::class.java)?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, dirPath: String?, ids: List<String>) {
        prefs(context).edit().putString(key(dirPath), gson.toJson(ids)).apply()
    }

    /** 恢复默认排序（置顶优先 + 版本名升序） */
    fun clear(context: Context, dirPath: String?) {
        prefs(context).edit().remove(key(dirPath)).apply()
    }
}
