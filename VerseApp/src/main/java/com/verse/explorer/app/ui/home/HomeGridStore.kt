package com.verse.explorer.app.ui.home

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.verse.explorer.cardgrid.state.GridCard

/** 单张主页卡片的持久化数据 */
data class HomeCardSnapshot(
    @SerializedName("id") val id: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("x") val x: Int = 0,
    @SerializedName("y") val y: Int = 0,
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0
)

/** 主页网格布局的持久化快照，[columns] 为保存时的网格列数 */
data class HomeGridSnapshot(
    @SerializedName("columns") val columns: Int = 0,
    @SerializedName("cards") val cards: List<HomeCardSnapshot> = emptyList()
)

/**
 * 主页网格布局的持久化：
 * 使用主页网格专用的偏好文件存储序列化 JSON，与启动器设置存储分离，
 * 仅用户卡片参与持久化。
 */
object HomeGridStore {
    private const val PREFS = "home_grid"
    // v6：改键名丢弃既有快照。旧快照里的固定功能卡片按「贴顶」行位置持久化，
    // 改为按可视高度垂直居中播种后需重新播种才能生效。
    private const val KEY_LAYOUT = "homeCardLayout_v6"

    private val gson = Gson()

    /** 读取布局快照，无有效数据时返回 null */
    fun load(context: Context): HomeGridSnapshot? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LAYOUT, null) ?: return null
        if (json.isBlank()) return null
        return runCatching {
            gson.fromJson(json, HomeGridSnapshot::class.java)
        }.getOrNull()?.takeIf { it.cards.isNotEmpty() }
    }

    /** 保存布局快照 */
    fun save(context: Context, cards: List<GridCard>, columns: Int) {
        val snapshot = HomeGridSnapshot(
            columns = columns,
            cards = cards.map { card ->
                HomeCardSnapshot(
                    id = card.id,
                    type = card.type.typeId,
                    x = card.layout.x,
                    y = card.layout.y,
                    width = card.layout.width,
                    height = card.layout.height
                )
            }
        )
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAYOUT, gson.toJson(snapshot)).apply()
    }
}
