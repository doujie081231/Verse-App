package com.verse.explorer.core.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Verse 皮肤站「浏览器快捷登录」（设备码流程，与微软的 device code 一致）。
 *
 * 流程：
 *  1. 启动器 POST /oauth/device/code → 拿到 user_code（8 位码）与 verification_uri；
 *  2. 打开浏览器，用户登录皮肤站并确认授权，输入/粘贴 user_code；
 *  3. 启动器按 interval 轮询 POST /oauth/token，授权成功后拿到 Yggdrasil 令牌；
 *  4. 用令牌构造 Authlib 账号（type=AUTHLIB + serverUrl），游戏侧仍走 authlib-injector。
 *
 * 服务端接口契约（Blessing Skin 插件需实现）：
 *
 *  POST {origin}/api/verse-oauth/device-code
 *    form: client_id=verse-launcher
 *    → 200 {"device_code":"...","user_code":"ABCD-EFGH",
 *            "verification_uri":"https://skin.example.com/verse-oauth/device",
 *            "interval":5,"expires_in":600}
 *
 *  POST {origin}/api/verse-oauth/token
 *    form: grant_type=urn:ietf:params:oauth:grant-type:device_code
 *          device_code=...&client_id=verse-launcher
 *    → 400 {"error":"authorization_pending"}   用户尚未确认
 *    → 400 {"error":"slow_down"}               轮询过快
 *    → 400 {"error":"access_denied"}           用户拒绝
 *    → 400 {"error":"expired_token"}           设备码过期
 *    → 200 {"yggdrasil":{"accessToken":"...","clientToken":"...",
 *                        "selectedProfile":{"id":"<uuid 无横线>","name":"<玩家名>"}}}
 *
 * 若 200 响应缺少 selectedProfile，启动器会退一步调用 Yggdrasil 的
 *  {apiRoot}/authserver/refresh 取角色（标准 Yggdrasil 接口，Blessing Skin 自带）。
 */
class VerseQuickLogin(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSec: Long,
        val expiresInSec: Long
    )

    enum class PollState { SUCCESS, PENDING, SLOW_DOWN, DENIED, EXPIRED, ERROR }

    data class PollResult(
        val state: PollState,
        val account: AuthResult? = null,
        val message: String? = null
    )

    /** 站点根地址：把用户填的验证服务器地址规范成 origin（去掉 /api/yggdrasil 等后缀） */
    fun originOf(serverUrl: String): String {
        var url = serverUrl.trim()
        if (url.isEmpty()) return ""
        if (!Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(url)) {
            url = if (url.startsWith("//")) "https:$url" else "https://$url"
        }
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return ""
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: return ""
        val port = if (uri.port > 0 && uri.port != defaultPort(scheme)) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }

    private fun defaultPort(scheme: String): Int = if (scheme == "http") 80 else 443

    suspend fun requestDeviceCode(origin: String): Result<DeviceCode> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .build()
            val request = Request.Builder()
                .url("$origin/api/verse-oauth/device-code")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error(
                        when (resp.code) {
                            404 -> "该服务器未启用 Verse 快捷登录（缺少 /api/verse-oauth/device-code）"
                            401, 403 -> "服务器拒绝了客户端标识"
                            else -> "请求设备码失败（HTTP ${resp.code}）"
                        }
                    )
                }
                val json = JSONObject(text)
                val deviceCode = json.optString("device_code")
                val userCode = json.optString("user_code")
                val verifyUri = json.optString("verification_uri").ifBlank {
                    json.optString("verification_url")
                }
                if (deviceCode.isBlank() || userCode.isBlank() || verifyUri.isBlank()) {
                    error("设备码响应不完整")
                }
                DeviceCode(
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUri = verifyUri,
                    intervalSec = json.optLong("interval", 5L).coerceAtLeast(1L),
                    expiresInSec = json.optLong("expires_in", 600L)
                )
            }
        }
    }

    /**
     * 轮询一次。调用方按 [DeviceCode.intervalSec] 间隔循环，直到返回非 PENDING。
     * @param apiRoot 用户填写的验证服务器 API 根（用于缺 profile 时调 Yggdrasil refresh）
     */
    suspend fun pollOnce(origin: String, apiRoot: String, deviceCode: DeviceCode): PollResult =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", GRANT_TYPE)
                    .add("device_code", deviceCode.deviceCode)
                    .add("client_id", CLIENT_ID)
                    .build()
                val request = Request.Builder()
                    .url("$origin/api/verse-oauth/token")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (!resp.isSuccessful) {
                        return@withContext when (json?.optString("error")) {
                            "authorization_pending" -> PollResult(PollState.PENDING)
                            "slow_down" -> PollResult(PollState.SLOW_DOWN)
                            "access_denied" -> PollResult(PollState.DENIED, message = "已在网页上拒绝授权")
                            "expired_token" -> PollResult(PollState.EXPIRED, message = "设备码已过期，请重试")
                            else -> PollResult(
                                PollState.ERROR,
                                message = json?.optString("error_description")?.takeIf { it.isNotBlank() }
                                    ?: "授权失败（HTTP ${resp.code}）"
                            )
                        }
                    }
                    val ygg = json?.optJSONObject("yggdrasil")
                        ?: return@withContext PollResult(PollState.ERROR, message = "响应缺少 yggdrasil 令牌")
                    val accessToken = ygg.optString("accessToken")
                    val clientToken = ygg.optString("clientToken")
                    if (accessToken.isBlank()) {
                        return@withContext PollResult(PollState.ERROR, message = "令牌为空")
                    }
                    val profile = ygg.optJSONObject("selectedProfile")
                    val fromToken = profile?.let {
                        val id = it.optString("id")
                        val name = it.optString("name")
                        if (id.isBlank() || name.isBlank()) null else id to name
                    }
                    val resolved = fromToken ?: refreshProfile(apiRoot, accessToken, clientToken)
                    ?: return@withContext PollResult(
                        PollState.ERROR,
                        message = "已授权，但未能获取玩家角色（请确认皮肤站已创建角色）"
                    )
                    PollResult(
                        PollState.SUCCESS,
                        account = AuthResult(
                            success = true,
                            account = Account(
                                username = resolved.second,
                                uuid = resolved.first,
                                accessToken = accessToken,
                                type = AccountType.AUTHLIB,
                                serverUrl = apiRoot,
                                loginUsername = resolved.second,
                                skinUrl = "https://mc-heads.net/avatar/${resolved.first}/64"
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                PollResult(PollState.ERROR, message = e.message ?: "网络错误")
            }
        }

    /** 标准 Yggdrasil /authserver/refresh：用浏览器授权拿到的令牌换角色信息 */
    private fun refreshProfile(apiRoot: String, accessToken: String, clientToken: String): Pair<String, String>? {
        val root = apiRoot.trimEnd('/')
        val payload = JSONObject().apply {
            put("accessToken", accessToken)
            put("clientToken", clientToken)
            put("requestUser", false)
        }
        val request = Request.Builder()
            .url("$root/authserver/refresh")
            .post(okhttp3.RequestBody.create(null, payload.toString()))
            .header("Content-Type", "application/json")
            .build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                val p = json.optJSONObject("selectedProfile") ?: return null
                val id = p.optString("id")
                val name = p.optString("name")
                if (id.isBlank() || name.isBlank()) null else id to name
            }
        }.getOrNull()
    }

    companion object {
        const val CLIENT_ID = "verse-launcher"
        private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

        /** Verse 皮肤站默认地址（Yggdrasil API 根）：客户端预设，用户无需手输 */
        const val DEFAULT_SERVER = "https://skin.verselauncher.cn/api/yggdrasil"

        /**
         * 授权页地址：把 user_code 直接拼进 query，网页端会预填验证码，
         * 用户只需点「允许登录」——与微软登录的设备码页体验一致。
         */
        fun authorizeUrl(code: DeviceCode): String {
            val base = code.verificationUri
            if (base.isBlank()) return ""
            val sep = if (base.contains('?')) "&" else "?"
            return base + sep + "user_code=" + java.net.URLEncoder.encode(code.userCode, "UTF-8")
        }
    }
}
