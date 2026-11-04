package com.verse.explorer.app.data

import android.content.Context
import org.json.JSONObject

/** 一条赞助记录（名单只公开昵称与金额，不涉及头像等其它信息） */
data class SponsorEntry(
    val name: String,
    val amount: Double
)

/** 一个月的赞助名单 */
data class SponsorMonth(
    val month: String,
    val title: String,
    val sponsors: List<SponsorEntry>
) {
    /** 总额（用于页脚展示） */
    val total: Double get() = sponsors.sumOf { it.amount }
}

/**
 * 月度赞助名单。
 *
 * 数据来自 assets/sponsors.json（与 ZL2 的做法一致：名单作为资源随包分发，
 * 由维护者按月更新；区别是它从 GitHub 拉取，我们直接读本地资源，避免额外依赖网络）。
 * 读取失败时返回 null，弹窗不展示——不因为名单问题打断启动。
 */
object SponsorRepository {

    private const val PREFS = "verse_sponsor"
    private const val KEY_LAST_SHOWN_MONTH = "last_shown_month"

    fun load(context: Context): SponsorMonth? = runCatching {
        val text = context.assets.open("sponsors.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(text)
        val list = obj.optJSONArray("sponsors") ?: return null
        val sponsors = ArrayList<SponsorEntry>(list.length())
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            sponsors.add(
                SponsorEntry(
                    name = item.optString("name", "匿名赞助者"),
                    amount = item.optDouble("amount", 0.0)
                )
            )
        }
        if (sponsors.isEmpty()) return null
        SponsorMonth(
            month = obj.optString("month", ""),
            title = obj.optString("title", "本月赞助者"),
            // 金额从高到低：高金额用户排在前面并享受更强的卡片效果
            sponsors = sponsors.sortedByDescending { it.amount }
        )
    }.getOrNull()

    /** 本月是否已经弹过（每月只弹一次） */
    fun shouldShowThisMonth(context: Context, month: String): Boolean {
        if (month.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_SHOWN_MONTH, "") != month
    }

    fun markShown(context: Context, month: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_SHOWN_MONTH, month).apply()
    }
}
