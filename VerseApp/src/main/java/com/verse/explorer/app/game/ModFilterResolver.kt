package com.verse.explorer.app.game

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.verse.explorer.core.config.GameConfig
import java.io.File

/**
 * 模组/资源页的「适配当前实例」筛选数据。
 *
 * 规则：
 *  - 版本 = 当前实例的真实 MC 版本（走 McVersionResolver，兼容 clientVersion / inheritsFrom）；
 *  - 加载器 = 实例实际安装的加载器：forge / neoforge / fabric；quilt 同时接受 Fabric 与 Quilt；
 *    OptiFine 不算模组加载器；原版（无加载器）时不做加载器限制（放开为全部）。
 */
object ModFilterResolver {

    data class Filter(
        val mcVersion: String?,
        /** Modrinth facets 的 categories 值（小写）；空表示不限加载器 */
        val loaders: List<String>
    )

    fun forVersion(context: Context, versionId: String?): Filter {
        if (versionId.isNullOrBlank()) return Filter(null, emptyList())
        val gameDir = GameConfig.getGameDir(context)
        val mcVersion = McVersionResolver.resolve(gameDir, versionId)
        val loader = detectLoader(gameDir, versionId)
        val loaders = when (loader) {
            LoaderTypeResolver.FORGE -> listOf("forge")
            LoaderTypeResolver.NEOFORGE -> listOf("neoforge")
            LoaderTypeResolver.FABRIC -> listOf("fabric")
            // Quilt 实例同时接受 Fabric 与 Quilt 的模组
            LoaderTypeResolver.QUILT -> listOf("fabric", "quilt")
            // OptiFine 不是模组加载器；原版同样不限加载器
            else -> emptyList()
        }
        return Filter(mcVersion, loaders)
    }

    /** 读版本 json 走继承链，交给 LoaderTypeResolver 判定加载器 */
    private fun detectLoader(gameDir: File, versionId: String): String? {
        var current = versionId
        val visited = LinkedHashSet<String>()
        while (current.isNotBlank() && visited.size < 6 && visited.add(current)) {
            val json = readVersionJson(gameDir, current) ?: break
            val info = LoaderTypeResolver.resolve(json, current)
            if (info.loaderType != null) return info.loaderType
            val parent = json.get("inheritsFrom")?.takeIf { !it.isJsonNull }?.asString
            if (parent.isNullOrBlank() || parent == current) break
            current = parent
        }
        return null
    }

    private fun readVersionJson(gameDir: File, id: String): JsonObject? = runCatching {
        val file = File(File(gameDir, "versions/$id"), "$id.json")
        if (!file.isFile || file.length() <= 0) null
        else JsonParser.parseString(file.readText()).asJsonObject
    }.getOrNull()
}
