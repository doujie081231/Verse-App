package com.verse.explorer.app.data.account

import android.content.Context
import android.graphics.BitmapFactory
import com.verse.explorer.core.account.Account
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 本地披风库：按账户存放导入的披风 PNG（filesDir/capes/<accountId>/），
 * 选中披风记录在 SharedPreferences（cape_selected，键=账户id，值=文件名；
 * 空串=未装备披风）。披风仅用于账户详情 3D 模型与卡片预览展示。
 */
object CapeRepository {

    private const val PREFS = "cape_selected"

    data class Cape(
        val id: String,
        val file: File
    )

    private fun dir(context: Context, accountId: Int): File =
        File(context.filesDir, "capes/$accountId").apply { mkdirs() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 列出某账户的本地披风（文件名去扩展名作 id） */
    fun list(context: Context, accountId: Int): List<Cape> {
        val d = dir(context, accountId)
        return d.listFiles { f -> f.isFile && f.name.endsWith(".png") }
            ?.map { Cape(it.name.removeSuffix(".png"), it) }
            ?.sortedBy { it.id }
            ?: emptyList()
    }

    /** 当前装备的披风（未装备返回 null） */
    fun selected(context: Context, accountId: Int): Cape? {
        val name = prefs(context).getString("cape_$accountId", "").orEmpty()
        if (name.isEmpty()) return null
        val f = File(dir(context, accountId), "$name.png")
        return if (f.exists()) Cape(name, f) else null
    }

    /** 装备/卸下披风（capeId 为空串即卸下） */
    fun select(context: Context, accountId: Int, capeId: String) {
        prefs(context).edit().putString("cape_$accountId", capeId).apply()
    }

    /** 从 SAF uri 导入披风 PNG，成功返回披风 id */
    fun import(context: Context, accountId: Int, uri: android.net.Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val target = File(dir(context, accountId), "cape_${System.currentTimeMillis()}.png")
                target.outputStream().use { output -> input.copyTo(output) }
                // 校验为可解码图片，坏文件删除
                if (BitmapFactory.decodeFile(target.absolutePath) == null) {
                    target.delete()
                    return null
                }
                target.name.removeSuffix(".png")
            }
        }.getOrNull()
    }

    /** 删除本地披风；若删除的是已装备披风则同时卸下 */
    fun delete(context: Context, accountId: Int, capeId: String): Boolean {
        val f = File(dir(context, accountId), "$capeId.png")
        val deleted = f.delete()
        if (selected(context, accountId)?.id == capeId) {
            select(context, accountId, "")
        }
        return deleted
    }

    /**
     * 获取官方披风（微软账户）：下载账户自带的 capeUrl 到本地披风库并装备，
     * 无官方披风（capeUrl 为空）时返回 null。
     */
    fun fetchOfficial(context: Context, account: Account): String? {
        val url = account.capeUrl?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.inputStream.use { input ->
                val target = File(dir(context, account.id), "official.png")
                target.outputStream().use { output -> input.copyTo(output) }
                if (BitmapFactory.decodeFile(target.absolutePath) == null) {
                    target.delete()
                    return null
                }
            }
            select(context, account.id, "official")
            "official"
        }.getOrNull()
    }
}
