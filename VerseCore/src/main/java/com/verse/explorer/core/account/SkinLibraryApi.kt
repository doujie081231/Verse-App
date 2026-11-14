package com.verse.explorer.core.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 皮肤库里的一件公开皮肤 */
data class SkinItem(
    val tid: Int,
    val name: String,
    /** classic / slim，对应皮肤站的 steve / alex */
    val model: String,
    /** 图片直链（服务端给出，客户端不拼地址） */
    val url: String,
    val likes: Int,
    val uploadedAt: String
) {
    val isSlim: Boolean get() = model == "slim"
}

data class SkinPage(
    val page: Int,
    val limit: Int,
    val total: Int,
    val items: List<SkinItem>
)

/**
 * Verse 皮肤站「皮肤库」接口客户端。
 *
 * 接口契约（所有启动器通用）：
 *  - `GET  {origin}/api/verse-skins?page=&limit=&sort=new|likes` 公开只读，无需鉴权；
 *  - `POST {origin}/api/verse-skins/apply` 携带该账号的 Yggdrasil accessToken，
 *    body `{ "tid": 皮肤 id, "uuid": "可选角色 uuid" }`，只能作用于 token 归属账号的角色。
 *
 * 鉴权只复用站点签发的 Yggdrasil accessToken，不引入启动器私有凭据，
 * 因此第三方启动器也能直接对接。
 */
class SkinLibraryApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    /**
     * 拉公开皮肤列表。
     *
     * 列表接口现在要求携带 API Key（与 CurseForge 的 x-api-key 一致），
     * [apiKey] 为空时用启动器内置的应用级 key；玩家填了自己的账号 key 时优先用它，
     * 这样调用量能统计到具体账号。
     */
    suspend fun list(
        origin: String,
        page: Int,
        limit: Int = 24,
        sort: String = "new",
        apiKey: String = ""
    ): Result<SkinPage> = withContext(Dispatchers.IO) {
        runCatching {
            require(origin.isNotBlank()) { "服务器地址为空" }
            val key = apiKey.ifBlank { SkinLibraryAppKey.value() }
            val request = Request.Builder()
                .url("$origin/api/verse-skins?page=$page&limit=$limit&sort=$sort")
                .header("X-Api-Key", key)
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error(
                        when (resp.code) {
                            401 -> "缺少或无效的 API Key：请到皮肤站「开发者中心」申请"
                            404 -> "该皮肤站未开放皮肤库接口"
                            429 -> "请求过于频繁，请稍后再试"
                            else -> "获取皮肤库失败（HTTP ${resp.code}）"
                        }
                    )
                }
                val json = JSONObject(text)
                val arr = json.optJSONArray("items") ?: JSONArray()
                val items = ArrayList<SkinItem>(arr.length())
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val url = item.optString("url", "")
                    if (url.isBlank()) continue
                    items.add(
                        SkinItem(
                            tid = item.optInt("tid", 0),
                            name = item.optString("name", "未命名"),
                            model = item.optString("model", "classic"),
                            url = url,
                            likes = item.optInt("likes", 0),
                            uploadedAt = item.optString("uploaded_at", "")
                        )
                    )
                }
                SkinPage(
                    page = json.optInt("page", page),
                    limit = json.optInt("limit", limit),
                    total = json.optInt("total", items.size),
                    items = items
                )
            }
        }
    }

    /**
     * 用开发者 API Key 应用皮肤。
     *
     * 与 [apply] 的区别只在凭据：API Key 与「用哪种账号进游戏」无关，
     * 微软账号、离线账号也能用它把皮肤挂到皮肤站角色上。
     */
    suspend fun applyWithKey(
        origin: String,
        apiKey: String,
        uuid: String,
        tid: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(origin.isNotBlank()) { "皮肤站地址为空" }
            require(apiKey.isNotBlank()) { "还没有填写 API Key" }
            val payload = JSONObject().apply {
                put("tid", tid)
                if (uuid.isNotBlank()) put("uuid", uuid)
            }
            val request = Request.Builder()
                .url("${origin.trimEnd('/')}/api/verse-skins/apply")
                .post(payload.toString().toRequestBody(JSON))
                .header("X-Api-Key", apiKey.trim())
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (!resp.isSuccessful) {
                    val msg = json?.optString("message").orEmpty()
                    error(msg.ifBlank {
                        when (resp.code) {
                            401 -> "API Key 无效或已被撤销，请到皮肤站开发者页面重新生成"
                            403 -> "该 Key 所属账号下没有这个角色"
                            404 -> "该皮肤已下架或未公开"
                            429 -> "操作过于频繁，请稍后再试"
                            else -> "应用皮肤失败（HTTP ${resp.code}）"
                        }
                    })
                }
                json?.optString("player").orEmpty().ifBlank { "角色" }
            }
        }
    }

    /** API Key 归属校验：返回账号下的角色名列表，用于填完 Key 立刻确认「这是谁的 Key」 */
    suspend fun verifyKey(origin: String, apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${origin.trimEnd('/')}/api/verse-skins/me")
                    .header("X-Api-Key", apiKey.trim())
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (!resp.isSuccessful) {
                        error(
                            json?.optString("message").orEmpty().ifBlank {
                                if (resp.code == 401) "API Key 无效或已被撤销" else "校验失败（HTTP ${resp.code}）"
                            }
                        )
                    }
                    val players = json?.optJSONArray("players") ?: return@use emptyList()
                    buildList {
                        for (i in 0 until players.length()) {
                            val name = players.optJSONObject(i)?.optString("name").orEmpty()
                            if (name.isNotBlank()) add(name)
                        }
                    }
                }
            }
        }

    /** 把库里的皮肤挂到自己的角色上；返回被作用的角色名 */
    suspend fun apply(
        origin: String,
        accessToken: String,
        uuid: String,
        tid: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(origin.isNotBlank()) { "服务器地址为空" }
            require(accessToken.isNotBlank()) { "当前账号没有可用的登录令牌，请重新登录" }
            val payload = JSONObject().apply {
                put("tid", tid)
                if (uuid.isNotBlank()) put("uuid", uuid)
            }
            val request = Request.Builder()
                .url("$origin/api/verse-skins/apply")
                .post(payload.toString().toRequestBody(JSON))
                .header("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (!resp.isSuccessful) {
                    val msg = json?.optString("message").orEmpty()
                    error(msg.ifBlank {
                        when (resp.code) {
                            401 -> "登录令牌已失效，请重新登录账号"
                            403 -> "当前账号下没有该角色"
                            404 -> "该皮肤已下架或未公开"
                            429 -> "操作过于频繁，请稍后再试"
                            else -> "应用皮肤失败（HTTP ${resp.code}）"
                        }
                    })
                }
                json?.optString("player").orEmpty().ifBlank { "角色" }
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
