package com.verse.explorer.app.data

import android.content.Context
import com.verse.explorer.core.resource.ResourceDetail
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏仓库（Android 本地收藏体系，对齐 PC `_favorites`：按项目 id 收藏，跨启动保持）。
 * 收藏内容保存项目快照（id/title/icon/source/downloads），供列表页"收藏"筛选直接展示。
 */
data class FavProject(
    val id: String,
    val title: String,
    val iconUrl: String?,
    val source: String,
    val downloads: Long,
    val description: String = "",
    val loaders: List<String> = emptyList(),
    val gameVersions: List<String> = emptyList()
)

object FavoritesRepository {

    private const val KEY = "fav_projects"
    private const val MAX_ITEMS = 200

    fun list(context: Context): List<FavProject> {
        val raw = context.getSharedPreferences("verse_explorer", Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        FavProject(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            iconUrl = o.optString("icon").ifBlank { null },
                            source = o.optString("source", "modrinth"),
                            downloads = o.optLong("downloads"),
                            description = o.optString("description"),
                            loaders = (o.optJSONArray("loaders")?.let { ja -> List(ja.length()) { ja.getString(it) } }) ?: emptyList(),
                            gameVersions = (o.optJSONArray("game_versions")?.let { ja -> List(ja.length()) { ja.getString(it) } }) ?: emptyList()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFavorite(context: Context, id: String, source: String): Boolean =
        list(context).any { it.id == id && it.source == source }

    /** 切换收藏，返回切换后是否已收藏 */
    fun toggle(context: Context, project: ResourceDetail): Boolean {
        val prefs = context.getSharedPreferences("verse_explorer", Context.MODE_PRIVATE)
        val cur = list(context)
        val exists = cur.any { it.id == project.id && it.source == project.source }
        val next = if (exists) {
            cur.filterNot { it.id == project.id && it.source == project.source }
        } else {
            cur + FavProject(
                id = project.id,
                title = project.title.ifEmpty { project.id },
                iconUrl = project.iconUrl,
                source = project.source.ifBlank { "modrinth" },
                downloads = project.downloads,
                description = project.description,
                loaders = project.loaders,
                gameVersions = project.gameVersions
            )
        }
        val arr = JSONArray()
        next.take(MAX_ITEMS).forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("title", f.title)
                    put("icon", f.iconUrl ?: "")
                    put("source", f.source)
                    put("downloads", f.downloads)
                    put("description", f.description)
                    put("loaders", JSONArray(f.loaders))
                    put("game_versions", JSONArray(f.gameVersions))
                }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        return !exists
    }
}