package com.verse.explorer.core.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class AuthlibAuthenticator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** 已解析的验证服务器 API 根（键=用户输入，值=规范化 API 根，带尾斜杠） */
    private val apiRootCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private class LocatedServer(val apiUrl: String, val serverName: String, val description: String)

    private class HttpResult(val body: String, val finalUrl: String, val apiLocation: String?)

    /** 无协议时默认补 https（外置登录服务器均为 https 服务） */
    private fun addHttpsIfMissing(url: String): String {
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(url)) return url
        return if (url.startsWith("//")) "https:$url" else "https://$url"
    }

    private fun urlEqualsIgnoreSlash(a: String, b: String): Boolean = a.trimEnd('/') == b.trimEnd('/')

    private fun httpGetString(url: String): HttpResult {
        val request = Request.Builder()
            .url(url)
            .header("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            return HttpResult(
                body = response.body?.string() ?: "",
                finalUrl = response.request.url.toString(),
                apiLocation = response.header("x-authlib-injector-api-location")
            )
        }
    }

    /**
     * 定位验证服务器：入口 URL 可以是主页或 API 根。若响应头携带
     * X-Authlib-Injector-API-Location，则解析为绝对地址并重连到该 API 根；
     * 元数据即 API 根路径返回的 JSON（serverName 位于根字段或 meta 内）。
     */
    private fun locateServer(rawUrl: String): LocatedServer {
        val entry = addHttpsIfMissing(rawUrl.trim())
        var apiUrl = entry
        var result = httpGetString(entry)
        result.apiLocation?.let { location ->
            val absolute = java.net.URL(java.net.URL(result.finalUrl), location).toString()
            if (!urlEqualsIgnoreSlash(entry, absolute)) {
                apiUrl = absolute
                result = httpGetString(absolute)
            } else {
                apiUrl = absolute
            }
        }
        if (!apiUrl.endsWith("/")) apiUrl += "/"

        val json = gson.fromJson(result.body, JsonObject::class.java)
            ?: throw IllegalStateException("服务器元数据无效")
        val meta = json.getAsJsonObject("meta")
        fun str(obj: JsonObject?, key: String): String? =
            obj?.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        val serverName = str(json, "serverName") ?: str(meta, "serverName") ?: "未知服务器"
        val implementation = str(json, "implementationName") ?: str(meta, "implementationName") ?: ""
        val version = str(json, "implementationVersion") ?: ""
        val description = listOf(implementation, version).filter { it.isNotBlank() }.joinToString(" ")
        return LocatedServer(apiUrl, serverName, description)
    }

    /** 键=用户输入，值=规范化 API 根；未验证过时现场定位 */
    private fun resolveApiRoot(serverUrl: String): String {
        val key = addHttpsIfMissing(serverUrl.trim())
        return apiRootCache[key] ?: locateServer(serverUrl).also { apiRootCache[key] = it.apiUrl }.apiUrl
    }

    suspend fun verifyServer(url: String): ServerInfo {
        return withContext(Dispatchers.IO) {
            try {
                val located = locateServer(url)
                apiRootCache[addHttpsIfMissing(url.trim())] = located.apiUrl
                ServerInfo(
                    success = true,
                    serverName = located.serverName,
                    description = located.description,
                    apiUrl = located.apiUrl
                )
            } catch (e: Exception) {
                ServerInfo(success = false)
            }
        }
    }

    private fun postJson(url: String, jsonBody: String): Triple<Boolean, Int, String> {
        val request = Request.Builder()
            .url(url)
            // 部分验证服务器会校验请求来源标识，缺失时可能直接返回 5xx
            .header("User-Agent", "authlib-injector/1.2.5")
            .header("Accept", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            return Triple(response.isSuccessful, response.code, response.body?.string() ?: "")
        }
    }

    /**
     * 认证失败提示：5xx 通常是验证服务器自身异常（与请求格式无关），单独给出可读提示，
     * 并附带接口地址，便于用户/维护者定位。
     */
    private fun authErrorPrefix(code: Int, url: String): String =
        if (code >= 500) "验证服务器返回异常 (HTTP $code)：$url"
        else "登录失败 (HTTP $code)：$url"

    /**
     * 读取验证服务器 session profile 的 textures 属性（base64 值）。
     * authlib-injector 启动参数里的 userProperties 需要它，游戏据此显示角色皮肤。
     */
    suspend fun fetchTexturesValue(apiRoot: String, uuid: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUuid = uuid.replace("-", "")
            val root = apiRoot.trimEnd('/')
            val result = httpGetString("$root/sessionserver/session/minecraft/profile/$cleanUuid")
            val json = gson.fromJson(result.body, JsonObject::class.java) ?: return@runCatching null
            json.getAsJsonArray("properties")?.forEach { p ->
                val obj = p.asJsonObject
                if (obj.get("name")?.asString == "textures") {
                    return@runCatching obj.get("value")?.asString
                }
            }
            null
        }.getOrNull()
    }

    /** 从 textures 属性解析皮肤纹理 URL（SKIN.url），供启动器头像展示 */
    suspend fun fetchSkinTextureUrl(apiRoot: String, uuid: String): String? =
        fetchTextureUrls(apiRoot, uuid).first

    /**
     * 只取披风纹理 URL（textures.CAPE.url）。
     * 外置登录（皮肤站）的披风就在 yggdrasil profile 的 textures 里，
     * 与 ZL2 的 CapeFileDownloader 取法一致。
     */
    suspend fun fetchCapeTextureUrl(apiRoot: String, uuid: String): String? =
        fetchTextureUrls(apiRoot, uuid).second

    /**
     * 一次请求同时解析皮肤与披风（避免为两件贴图各请求一次 profile）。
     * @return (skinUrl, capeUrl)，任一不存在则为 null
     */
    suspend fun fetchTextureUrls(apiRoot: String, uuid: String): Pair<String?, String?> {
        val value = fetchTexturesValue(apiRoot, uuid) ?: return null to null
        return runCatching {
            val decoded = String(android.util.Base64.decode(value, android.util.Base64.DEFAULT))
            val textures = gson.fromJson(decoded, JsonObject::class.java)?.getAsJsonObject("textures")
            val skin = textures?.getAsJsonObject("SKIN")?.get("url")?.asString
            val cape = textures?.getAsJsonObject("CAPE")?.get("url")?.asString
            skin to cape
        }.getOrDefault(null to null)
    }

    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
        profileId: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val apiRoot = resolveApiRoot(serverUrl)
            val clientToken = UUID.randomUUID().toString().replace("-", "")

            val body = JsonObject().apply {
                add("agent", JsonObject().apply {
                    addProperty("name", "Minecraft")
                    addProperty("version", 1)
                })
                addProperty("username", username)
                addProperty("password", password)
                addProperty("clientToken", clientToken)
                addProperty("requestUser", true)
            }

            val (ok, code, respBody) = postJson("${apiRoot}authserver/authenticate", body.toString())
            if (!ok) {
                return@withContext AuthResult(
                    success = false,
                    error = "${authErrorPrefix(code, "${apiRoot}authserver/authenticate")} $respBody"
                )
            }

            val json = gson.fromJson(respBody, JsonObject::class.java)
                ?: return@withContext AuthResult(success = false, error = "登录失败: 响应无效")
            val accessToken = json.get("accessToken")?.takeIf { it.isJsonPrimitive }?.asString
                ?: return@withContext AuthResult(success = false, error = "登录失败: 响应缺少 accessToken")
            val selectedProfile = json.getAsJsonObject("selectedProfile")
            val availableProfiles = json.getAsJsonArray("availableProfiles")

            suspend fun buildAccount(profileUuid: String, profileName: String, token: String): AuthResult {
                // 皮肤/披风都来自皮肤站的 textures（ZL2 同样从 CAPE.url 取披风），
                // 取不到时回落到 mc-heads 头像，不影响登录
                val (skinUrl, capeUrl) = fetchTextureUrls(apiRoot, profileUuid)
                return AuthResult(
                    success = true,
                    account = Account(
                        username = profileName,
                        uuid = profileUuid,
                        accessToken = token,
                        type = AccountType.AUTHLIB,
                        serverUrl = apiRoot,
                        loginUsername = username,
                        skinUrl = skinUrl ?: "https://mc-heads.net/avatar/$profileUuid/64",
                        capeUrl = capeUrl
                    )
                )
            }

            // 多角色且未指定：先返回候选列表，等用户选择后带 profileId 再走一遍
            // （此时经 /authserver/refresh 选定角色）
            if (selectedProfile == null && availableProfiles != null && availableProfiles.size() > 0) {
                val profilesList = mutableListOf<AuthlibProfile>()
                availableProfiles.forEach { p ->
                    val obj = p.asJsonObject
                    profilesList.add(
                        AuthlibProfile(
                            id = obj.get("id").asString,
                            name = obj.get("name").asString
                        )
                    )
                }
                if (profilesList.size > 1 && profileId == null) {
                    return@withContext AuthResult(success = true, profiles = profilesList)
                }
                val chosen = if (profileId != null) {
                    profilesList.firstOrNull { it.id == profileId }
                        ?: return@withContext AuthResult(success = false, error = "角色不存在")
                } else {
                    profilesList.first()
                }
                return@withContext selectProfile(apiRoot, accessToken, clientToken, chosen, username)
            }

            val profileName = selectedProfile?.get("name")?.asString ?: username
            val resolvedProfileId = selectedProfile?.get("id")?.asString ?: UUID.randomUUID().toString()
            buildAccount(resolvedProfileId, profileName, accessToken)
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message?.let { "登录失败: $it" } ?: "外置登录失败")
        }
    }

    /**
     * 角色选定：认证响应未含 selectedProfile 时，通过 /authserver/refresh 携带
     * selectedProfile 完成（需复用认证时的 clientToken）。
     */
    private suspend fun selectProfile(
        apiRoot: String,
        accessToken: String,
        clientToken: String,
        profile: AuthlibProfile,
        loginUsername: String
    ): AuthResult {
        return try {
            val body = JsonObject().apply {
                addProperty("accessToken", accessToken)
                addProperty("clientToken", clientToken)
                addProperty("requestUser", true)
                add("selectedProfile", JsonObject().apply {
                    addProperty("id", profile.id)
                    addProperty("name", profile.name)
                })
            }
            val (ok, code, respBody) = postJson("${apiRoot}authserver/refresh", body.toString())
            if (!ok) {
                return AuthResult(
                    success = false,
                    error = "选择角色失败：" + authErrorPrefix(code, "${apiRoot}authserver/refresh") + " $respBody"
                )
            }
            val json = gson.fromJson(respBody, JsonObject::class.java)
                ?: return AuthResult(success = false, error = "选择角色失败: 响应无效")
            val selected = json.getAsJsonObject("selectedProfile")
            val newToken = json.get("accessToken")?.takeIf { it.isJsonPrimitive }?.asString ?: accessToken
            val profileId = selected?.get("id")?.asString ?: profile.id
            val profileName = selected?.get("name")?.asString ?: profile.name
            val (skinUrl, capeUrl) = fetchTextureUrls(apiRoot, profileId)
            AuthResult(
                success = true,
                account = Account(
                    username = profileName,
                    uuid = profileId,
                    accessToken = newToken,
                    type = AccountType.AUTHLIB,
                    serverUrl = apiRoot,
                    loginUsername = loginUsername,
                    skinUrl = skinUrl ?: "https://mc-heads.net/avatar/$profileId/64",
                    capeUrl = capeUrl
                )
            )
        } catch (e: Exception) {
            AuthResult(success = false, error = "选择角色失败: ${e.message}")
        }
    }
}
