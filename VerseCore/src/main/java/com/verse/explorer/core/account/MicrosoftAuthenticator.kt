package com.verse.explorer.core.account

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 微软登录模块
 *
 * 自 ZalithLauncher2（ZL2）的 game/account/microsoft 模块完整迁移：
 * 设备码登录（授权轮询处理 authorization_pending / slow_down / 连续网络失败）、
 * 令牌刷新（login.live.com 端点）、XBL 认证（RpsTicket 去 "d=" 前缀回退）、
 * XSTS 认证（完整 XErr 错误码映射）、Minecraft 服务认证（429/403 细分）、
 * 游戏所有权校验（entitlements/mcstore）、玩家档案获取与访问令牌有效性校验。
 */
class MicrosoftAuthenticator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "MsaLogin"

        private const val AUTHORITY = "https://login.microsoftonline.com"
        private const val CLIENT_ID = "00000000402b5328"

        /** 与 ZL2 一致的作用域列表（子集沿用 Verse 已验证可用的配置） */
        private val SCOPES = listOf("XboxLive.signin", "offline_access")
        private const val TENANT = "/consumers"

        /**
         * 与 ZL2 一致：轮询令牌时允许的最大连续网络失败次数
         * 网络环境较差时，偶发的失败不应中断登录，但持续失败说明网络不可用，应提前放弃
         */
        private const val MAX_CONSECUTIVE_POLL_FAILURES = 5

        // 与 ZL2 的 MICROSOFT_AUTH_URL / LIVE_AUTH_URL / XBL_AUTH_URL / XSTS_AUTH_URL / MINECRAFT_SERVICES_URL 一致
        private const val MICROSOFT_AUTH_URL = "https://login.microsoftonline.com"
        private const val LIVE_AUTH_URL = "https://login.live.com"
        private const val XBL_AUTH_URL = "https://user.auth.xboxlive.com"
        private const val XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com"
        private const val MINECRAFT_SERVICES_URL = "https://api.minecraftservices.com"

        private const val DEVICE_CODE_URL = "$MICROSOFT_AUTH_URL$TENANT/oauth2/v2.0/devicecode"
        private const val TOKEN_URL = "$MICROSOFT_AUTH_URL$TENANT/oauth2/v2.0/token"

        /**
         * 与 ZL2 的 XboxLoginException.XErr 映射一致
         * Reference : https://github.com/PrismarineJS/prismarine-auth/blob/1aef6e1/src/common/Constants.js#L50-L59
         */
        private val XERR_LOOKUP = mapOf<Long, XboxLoginException.ExceptionStatus>(
            2148916227L to XboxLoginException.ExceptionStatus.BANNED,
            2148916229L to XboxLoginException.ExceptionStatus.RESTRICTED,
            2148916233L to XboxLoginException.ExceptionStatus.UNREGISTERED,
            2148916234L to XboxLoginException.ExceptionStatus.NOT_ACCEPTED_SERVICE,
            2148916235L to XboxLoginException.ExceptionStatus.BLOCKED_REGION,
            2148916236L to XboxLoginException.ExceptionStatus.REQUIRES_PROOF_OF_AGE,
            2148916237L to XboxLoginException.ExceptionStatus.REACHED_PLAYTIME_LIMIT,
            2148916238L to XboxLoginException.ExceptionStatus.UNDERAGE
        )
    }

    /**
     * 从 Microsoft 身份验证终端节点获取设备代码响应
     * 设备代码用于在单独的设备或浏览器上授权用户
     */
    suspend fun getDeviceCode(): DeviceCodeResponse = withContext(Dispatchers.IO) {
        Log.i(TAG, "获取设备码: POST ${DEVICE_CODE_URL}（client_id=${CLIENT_ID}, scope=${SCOPES.joinToString(" ")}）")
        withRetry {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPES.joinToString(" "))
                .build()

            val request = Request.Builder().url(DEVICE_CODE_URL).post(body).build()
            try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string()
                    Log.i(TAG, "获取设备码 HTTP ${response.code}（body 长度=${raw?.length}）")
                    if (!response.isSuccessful) {
                        Log.e(TAG, "获取设备码失败 HTTP ${response.code}: ${raw?.take(300)}")
                        val errorJson = runCatching { parseJson(raw) }.getOrNull()
                        throw AuthHttpException(
                            httpStatus = response.code,
                            errorCode = errorJson?.get("error")?.takeIf { !it.isJsonNull }?.asString,
                            message = "获取设备码失败: HTTP ${response.code}"
                        )
                    }
                    val json = parseJson(raw)
                    val userCode = json.get("user_code")?.asString ?: ""
                    val verificationUri = json.get("verification_uri")?.asString ?: ""
                    val deviceCode = json.get("device_code")?.asString ?: ""
                    val expiresIn = json.get("expires_in")?.asInt ?: 0
                    val interval = json.get("interval")?.asInt ?: 5
                    Log.i(
                        TAG,
                        "设备码返回: user_code=$userCode, verification_uri=${verificationUri.ifBlank { "<空!>" }}（长度=${verificationUri.length}）, " +
                            "expires_in=${expiresIn}s, interval=${interval}s, device_code 存在=${deviceCode.isNotEmpty()}"
                    )
                    if (verificationUri.isBlank()) {
                        Log.w(TAG, "verification_uri 为空！内置浏览器页将无地址可打开，登录必然失败")
                    }
                    DeviceCodeResponse(
                        userCode = userCode,
                        deviceCode = deviceCode,
                        verificationUri = verificationUri,
                        expiresIn = expiresIn,
                        interval = interval
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "获取设备码网络异常: ${e.javaClass.simpleName} ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 使用设备代码流从 Microsoft Azure Active Directory 检索访问令牌和刷新令牌
     * 此函数会定期轮询 Microsoft 令牌端点，直到获取访问令牌或设备码超时
     *
     * 与 ZL2 的 getTokenResponse 逻辑一致：
     * - 服务器响应 authorization_pending 时继续轮询，slow_down 时将轮询间隔 +1s
     * - 临时性网络错误不中断轮询，但连续 [MAX_CONSECUTIVE_POLL_FAILURES] 次失败后放弃
     * - [checkCancelled] 需连续两次确认才真正取消，防止误判
     */
    suspend fun awaitDeviceAuth(
        codeResponse: DeviceCodeResponse,
        checkCancelled: suspend (time: Int) -> Boolean = { false }
    ): TokenResponse = withContext(Dispatchers.IO) {
        var pollingInterval = codeResponse.interval * 1000L
        val expireTime = System.currentTimeMillis() + codeResponse.expiresIn * 1000L
        Log.i(TAG, "开始轮询设备码授权: interval=${codeResponse.interval}s, expiresIn=${codeResponse.expiresIn}s（约 ${codeResponse.expiresIn / 60} 分钟内完成）")

        var cancelled = 0
        suspend fun checkIsReallyCancelled(): Boolean {
            if (checkCancelled(cancelled)) cancelled++
            return cancelled > 1
        }

        var consecutiveFailures = 0
        var pollTime = 0

        while (System.currentTimeMillis() < expireTime) {
            ensureActive()

            try {
                val body = FormBody.Builder()
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .add("device_code", codeResponse.deviceCode)
                    .add("client_id", CLIENT_ID)
                    .add("tenant", TENANT)
                    .build()

                val request = Request.Builder().url(TOKEN_URL).post(body).build()
                val json = client.newCall(request).execute().use { response ->
                    parseJson(response.body?.string())
                }
                consecutiveFailures = 0

                if (json.get("token_type")?.asString == "Bearer") {
                    Log.i(TAG, "轮询成功（第 $pollTime 次）：收到 Bearer 令牌")
                    return@withContext TokenResponse(
                        accessToken = json.get("access_token")?.asString ?: "",
                        refreshToken = json.get("refresh_token")?.takeIf { !it.isJsonNull }?.asString,
                        expiresIn = json.get("expires_in")?.asLong ?: 0
                    )
                }
                Log.d(TAG, "轮询 #$pollTime: 响应非 Bearer（token_type=${json.get("token_type")?.asString}），忽略继续")
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthHttpException) {
                when (e.errorCode) {
                    // 服务器正常响应，说明网络可用
                    "authorization_pending" -> {
                        consecutiveFailures = 0 // 正常情况，继续轮询
                        Log.d(TAG, "轮询 #$pollTime: authorization_pending（等待用户在浏览器授权）")
                    }
                    "slow_down" -> {
                        consecutiveFailures = 0
                        pollingInterval += 1000L
                        Log.w(TAG, "轮询 #$pollTime: slow_down（服务器要求放慢，间隔调整为 ${pollingInterval / 1000}s）")
                    }
                    else -> {
                        Log.e(TAG, "轮询 #$pollTime 收到致命错误: error=${e.errorCode}, HTTP ${e.httpStatus}, msg=${e.message}")
                        throw e
                    }
                }
            } catch (e: Exception) {
                // 轮询期间的临时性错误（网络波动、DNS解析失败、服务器5xx、请求超时等）不应中断整个登录流程
                // 只要设备码尚未过期，就继续轮询；但网络持续不可用时应提前放弃
                consecutiveFailures++
                Log.d(TAG, "轮询 #$pollTime 临时错误（$consecutiveFailures/$MAX_CONSECUTIVE_POLL_FAILURES）: ${e.javaClass.simpleName} ${e.message}")
                if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES ||
                    System.currentTimeMillis() + pollingInterval >= expireTime
                ) {
                    Log.e(TAG, "轮询连续失败 $consecutiveFailures 次或接近过期，放弃轮询：$e")
                    throw e
                }
            }

            if (checkIsReallyCancelled()) {
                Log.w(TAG, "轮询被用户取消")
                throw CancellationException("Authentication cancelled")
            }

            pollTime++
            delay(pollingInterval)
            ensureActive()
        }
        Log.w(TAG, "设备码轮询超时（未在 expires_in 内完成授权），抛 AuthenticationTimeoutException")
        throw AuthenticationTimeoutException()
    }

    /**
     * 使用不同的身份验证类型验证用户，并检索其 Minecraft 帐户信息
     *
     * 与 ZL2 的 microsoftAuthAsync 一致：访问令牌（或刷新令牌换取）→ XBL → XSTS →
     * Minecraft 服务认证 → 所有权校验 → 玩家档案，[statusUpdate] 回调当前步骤。
     */
    suspend fun authenticate(
        msAccessToken: String,
        msRefreshToken: String? = null,
        statusUpdate: (AsyncStatus) -> Unit = {}
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val mcAccessToken = fullAuthChain(msAccessToken, msRefreshToken, AuthType.Access, statusUpdate)
            val profile = fetchProfileWithOwnership(mcAccessToken, statusUpdate)
            buildSuccessResult(profile, mcAccessToken, msRefreshToken, statusUpdate)
        } catch (e: NotPurchasedMinecraftException) {
            AuthResult(success = false, error = e.message, needPurchase = true)
        } catch (e: EmptyProfileException) {
            AuthResult(success = false, error = e.message, needCreateProfile = true)
        } catch (e: XboxLoginException) {
            AuthResult(success = false, error = e.toLocalMessage())
        } catch (e: MinecraftProfileException) {
            AuthResult(success = false, error = e.toLocalMessage())
        } catch (e: CredentialsExpiredException) {
            AuthResult(success = false, error = e.message, needRelogin = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message)
        }
    }

    /**
     * 使用 refresh token 刷新微软账号（与 ZL2 的 refreshAccessToken 一致，
     * 走 login.live.com/oauth20_token.srf 端点）。
     * 刷新成功后重新走 Xbox/Minecraft 认证，返回新的访问令牌、刷新令牌与档案。
     */
    suspend fun refresh(refreshToken: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val (newAccessToken, newRefreshToken) = refreshAccessToken(refreshToken) {}
            authenticate(newAccessToken, newRefreshToken)
        } catch (e: CredentialsExpiredException) {
            AuthResult(success = false, error = e.message, needRelogin = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message ?: "刷新令牌失败")
        }
    }

    /**
     * 校验缓存的 accessToken 是否仍被服务端接受（与 ZL2 的 validateAccessToken 一致）
     * @return false 表示服务端已拒绝该凭据
     */
    suspend fun validateAccessToken(account: Account): Boolean = withContext(Dispatchers.IO) {
        try {
            getPlayerProfile(account.accessToken)
            true
        } catch (e: MinecraftProfileException) {
            false
        } catch (e: AuthHttpException) {
            e.httpStatus != 401 && e.httpStatus != 403
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * 完整认证链（与 ZL2 的 microsoftAuthAsync 对应）：
     * 访问令牌准备 → XBL → XSTS → Minecraft 登录 → 所有权校验，返回 Minecraft 访问令牌
     */
    private suspend fun fullAuthChain(
        accessToken: String,
        refreshToken: String?,
        authType: AuthType,
        statusUpdate: (AsyncStatus) -> Unit
    ): String {
        val (finalAccessToken, _) = when (authType) {
            AuthType.Refresh -> refreshAccessToken(refreshToken ?: "", statusUpdate)
            else -> Pair(accessToken, refreshToken)
        }

        statusUpdate(AsyncStatus.GETTING_XBL_TOKEN)
        Log.i(TAG, "认证链步骤: XBL（GETTING_XBL_TOKEN）")
        val xblToken = authenticateXBL(finalAccessToken)
        statusUpdate(AsyncStatus.GETTING_XSTS_TOKEN)
        Log.i(TAG, "认证链步骤: XSTS（GETTING_XSTS_TOKEN）")
        val xstsToken = authenticateXSTS(xblToken.first, xblToken.second)
        statusUpdate(AsyncStatus.AUTHENTICATE_MINECRAFT)
        Log.i(TAG, "认证链步骤: Minecraft 服务认证（AUTHENTICATE_MINECRAFT）")
        return authenticateMinecraft(xstsToken.first, xstsToken.second)
    }

    /** 与 ZL2 的 refreshAccessToken 一致：走 login.live.com 旧端点，invalid_grant 视为凭据过期 */
    private suspend fun refreshAccessToken(
        refreshToken: String,
        update: (AsyncStatus) -> Unit
    ): Pair<String, String> {
        update(AsyncStatus.GETTING_ACCESS_TOKEN)
        Log.i(TAG, "刷新令牌: POST ${LIVE_AUTH_URL}/oauth20_token.srf")

        return withRetry {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder().url("$LIVE_AUTH_URL/oauth20_token.srf").post(body).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.i(TAG, "刷新令牌 HTTP ${response.code}（body 长度=${responseBody?.length}）")
            if (!response.isSuccessful) {
                val json = runCatching { parseJson(responseBody) }.getOrNull()
                val error = json?.get("error")?.asString
                response.close()
                Log.w(TAG, "刷新令牌失败: error=$error, HTTP=${response.code}, body=${responseBody?.take(200)}")
                //刷新令牌已被撤销或失效，无法自动恢复
                if (error == "invalid_grant") throw CredentialsExpiredException()
                throw AuthHttpException(
                    httpStatus = response.code,
                    errorCode = error,
                    message = "刷新令牌失败: $error"
                )
            }
            response.close()
            val json = parseJson(responseBody)
            Pair(
                json.get("access_token")?.asString ?: "",
                json.get("refresh_token")?.takeIf { !it.isJsonNull }?.asString ?: refreshToken
            )
        }
    }

    /**
     * 与 ZL2 的 authenticateXBL 一致：RpsTicket 携带 "d=" 前缀；
     * 如遇 400 Bad Request，可去掉 "d=" 前缀重新请求
     * https://zh.minecraft.wiki/w/Tutorial:%E7%BC%96%E5%86%99%E5%90%AF%E5%8A%A8%E5%99%A8#Xbox_Live%E8%BA%AB%E4%BB%BD%E9%AA%8C%E8%AF%81
     */
    private suspend fun authenticateXBL(accessToken: String): Pair<String, String> = withRetry {
        try {
            requestXblToken("d=$accessToken")
        } catch (e: AuthHttpException) {
            if (e.httpStatus == 400) {
                requestXblToken(accessToken)
            } else throw e
        }
    }

    private fun requestXblToken(rpsTicket: String): Pair<String, String> {
        // 不打印完整 RpsTicket（含 access token），仅打印结构
        Log.i(TAG, "XBL 请求: POST $XBL_AUTH_URL/user/authenticate（RpsTicket 带 d= 前缀=${rpsTicket.startsWith("d=")}）")
        val properties = JsonObject().apply {
            addProperty("AuthMethod", "RPS")
            addProperty("SiteName", "user.auth.xboxlive.com")
            addProperty("RpsTicket", rpsTicket)
        }
        val body = JsonObject().apply {
            add("Properties", properties)
            addProperty("RelyingParty", "http://auth.xboxlive.com")
            addProperty("TokenType", "JWT")
        }

        val request = Request.Builder()
            .url("$XBL_AUTH_URL/user/authenticate")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code != 200) Log.w(TAG, "XBL HTTP ${response.code}（非 200）")
            val json = parseJsonOrThrow(response)
            //提取uhs
            val uhs = json.getAsJsonObject("DisplayClaims")
                ?.getAsJsonArray("xui")?.firstOrNull()?.asJsonObject
                ?.get("uhs")?.asString
                ?: throw Exception("Missing uhs in XBL response")
            return Pair(json.get("Token")?.asString ?: "", uhs)
        }
    }

    /** 与 ZL2 的 authenticateXSTS 一致：从 4xx 响应体解析 XErr 并细分账号问题 */
    private suspend fun authenticateXSTS(xblToken: String, uhs: String): Pair<String, String> = withRetry {
        val properties = JsonObject().apply {
            addProperty("SandboxId", "RETAIL")
            add("UserTokens", gson.toJsonTree(arrayOf(xblToken)))
        }
        val body = JsonObject().apply {
            add("Properties", properties)
            addProperty("RelyingParty", "rp://api.minecraftservices.com/")
            addProperty("TokenType", "JWT")
        }

        val request = Request.Builder()
            .url("$XSTS_AUTH_URL/xsts/authorize")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            Log.i(TAG, "XSTS POST $XSTS_AUTH_URL/xsts/authorize → HTTP ${response.code}")
            if (!response.isSuccessful) {
                // XSTS 对账号类问题统一返回 4xx 及 XErr 错误码，
                // 因此必须从异常响应体中解析 XErr，才能向用户展示真实的失败原因
                val errorJson = runCatching { gson.fromJson(responseBody, JsonObject::class.java) }.getOrNull()
                val xerr = errorJson?.get("XErr")?.takeIf { !it.isJsonNull }?.asLong
                val status = xerr?.let { XERR_LOOKUP[it] }
                if (status != null) throw XboxLoginException(status)
                Log.w(TAG, "XSTS 认证失败: HTTP=${response.code}, XErr=$xerr, body=${responseBody?.take(200)}")
                throw AuthHttpException(
                    httpStatus = response.code,
                    errorCode = null,
                    message = "XSTS 认证失败 (HTTP ${response.code}): XErr=$xerr ${errorJson?.get("Message")?.asString ?: ""}"
                )
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val token = json.get("Token")?.asString ?: ""
            val newUhs = json.getAsJsonObject("DisplayClaims")
                ?.getAsJsonArray("xui")?.firstOrNull()?.asJsonObject
                ?.get("uhs")?.asString ?: uhs
            Pair(token, newUhs)
        }
    }

    /** 与 ZL2 的 authenticateMinecraft 一致：429 → 登录过于频繁，403 → IP 被禁止 */
    private suspend fun authenticateMinecraft(uhs: String, xstsToken: String): String = withRetry {
        val body = JsonObject().apply {
            addProperty("identityToken", "XBL3.0 x=$uhs;$xstsToken")
        }

        val request = Request.Builder()
            .url("$MINECRAFT_SERVICES_URL/authentication/login_with_xbox")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            Log.i(TAG, "Minecraft 认证 POST $MINECRAFT_SERVICES_URL/authentication/login_with_xbox → HTTP ${response.code}")
            if (!response.isSuccessful) {
                when (response.code) {
                    429 -> throw MinecraftProfileException(MinecraftProfileException.ExceptionStatus.FREQUENT)
                    403 -> throw MinecraftProfileException(MinecraftProfileException.ExceptionStatus.BLOCKED_IP)
                }
                Log.w(TAG, "Minecraft 认证失败: HTTP=${response.code}, body=${responseBody?.take(200)}")
                throw AuthHttpException(
                    httpStatus = response.code,
                    errorCode = null,
                    message = "Minecraft 认证失败 (HTTP ${response.code})"
                )
            }
            gson.fromJson(responseBody, JsonObject::class.java).get("access_token")?.asString ?: ""
        }
    }

    /**
     * 所有权校验 + 档案获取（与 ZL2 的 verifyGameOwnership / getPlayerProfile 对应）：
     * mcstore 所有权项为空 → 未购买；档案 429 → 频繁；404 → 区分未创建档案/未购买
     */
    private suspend fun fetchProfileWithOwnership(
        mcToken: String,
        statusUpdate: (AsyncStatus) -> Unit
    ): MinecraftProfile {
        statusUpdate(AsyncStatus.VERIFY_GAME_OWNERSHIP)
        withRetry {
            val request = Request.Builder()
                .url("$MINECRAFT_SERVICES_URL/entitlements/mcstore")
                .header("Authorization", "Bearer $mcToken")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                Log.i(TAG, "所有权校验 GET $MINECRAFT_SERVICES_URL/entitlements/mcstore → HTTP ${response.code}")
                if (response.code != 200) throw NotPurchasedMinecraftException()
                val json = parseJson(response.body?.string())
                val items = json.getAsJsonArray("items")
                Log.i(TAG, "所有权 items 数量=${items?.size()}")
                if (items == null || items.size() == 0) throw NotPurchasedMinecraftException()
            }
        }

        statusUpdate(AsyncStatus.GETTING_PLAYER_PROFILE)
        return getPlayerProfile(mcToken)
    }

    /** 与 ZL2 的 getPlayerProfile 一致：429 → 频繁；404 → 未创建档案（结合许可证检查细分未购买） */
    private fun getPlayerProfile(mcToken: String): MinecraftProfile {
        val request = Request.Builder()
            .url("$MINECRAFT_SERVICES_URL/minecraft/profile")
            .header("Authorization", "Bearer $mcToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            Log.i(TAG, "获取玩家档案 GET $MINECRAFT_SERVICES_URL/minecraft/profile → HTTP ${response.code}")
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    // 档案不存在时再查许可证，区分"没创建角色"和"没购买"
                    val hasLicense = checkLicense(mcToken)
                    if (hasLicense) throw EmptyProfileException()
                    throw NotPurchasedMinecraftException()
                }
                if (response.code == 429) {
                    throw MinecraftProfileException(MinecraftProfileException.ExceptionStatus.FREQUENT)
                }
                throw AuthHttpException(
                    httpStatus = response.code,
                    errorCode = null,
                    message = "获取 Minecraft 档案失败 (HTTP ${response.code})"
                )
            }
            val json = parseJson(response.body?.string())
            val id = json.get("id")?.takeIf { !it.isJsonNull }?.asString ?: ""
            val name = json.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
            if (id.isBlank() || name.isBlank()) {
                throw EmptyProfileException()
            }
            var skinUrl: String? = null
            var capeUrl: String? = null
            json.getAsJsonArray("skins")?.forEach { skin ->
                val s = skin.asJsonObject
                if (s.get("state")?.asString == "ACTIVE") {
                    skinUrl = s.get("url")?.asString
                }
            }
            json.getAsJsonArray("capes")?.forEach { cape ->
                val c = cape.asJsonObject
                if (c.get("state")?.asString == "ACTIVE") {
                    capeUrl = c.get("url")?.asString
                }
            }
            return MinecraftProfile(id, name, skinUrl, capeUrl)
        }
    }

    private fun buildSuccessResult(
        profile: MinecraftProfile,
        mcToken: String,
        msRefreshToken: String?,
        statusUpdate: (AsyncStatus) -> Unit
    ): AuthResult {
        if (profile.id.isBlank() || profile.name.isBlank()) {
            return AuthResult(
                success = false,
                error = "Minecraft 档案为空，请先创建角色",
                needCreateProfile = true
            )
        }

        statusUpdate(AsyncStatus.GETTING_PLAYER_PROFILE)
        return AuthResult(
            success = true,
            account = Account(
                username = profile.name,
                uuid = profile.id,
                accessToken = mcToken,
                refreshToken = msRefreshToken ?: "",
                type = AccountType.MICROSOFT,
                skinUrl = profile.skinUrl,
                capeUrl = profile.capeUrl
            )
        )
    }

    /** 查询 Minecraft 许可证，返回是否拥有 game_minecraft 许可证 */
    private fun checkLicense(mcToken: String): Boolean {
        val request = Request.Builder()
            .url("$MINECRAFT_SERVICES_URL/entitlements/license")
            .header("Authorization", "Bearer $mcToken")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val json = parseJson(response.body?.string())
                val items = json.getAsJsonArray("items") ?: return false
                items.any {
                    it.asJsonObject.get("name")?.asString == "game_minecraft"
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseJson(body: String?): JsonObject =
        gson.fromJson(body, JsonObject::class.java) ?: JsonObject()

    private fun parseJsonOrThrow(response: okhttp3.Response): JsonObject {
        val body = response.body?.string()
        if (!response.isSuccessful) {
            val json = runCatching { parseJson(body) }.getOrNull()
            throw AuthHttpException(
                httpStatus = response.code,
                errorCode = json?.get("error")?.takeIf { !it.isJsonNull }?.asString,
                message = "HTTP ${response.code}: $body"
            )
        }
        return parseJson(body)
    }

    /**
     * 与 ZL2 的 withRetry 一致：3 次重试、1s 起步指数退避（上限 10s）。
     * 可重试条件：5xx、DNS 解析失败、网络 IO 异常；取消不重试。
     */
    private suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10_000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        var retryCount = 0
        var lastError: Throwable? = null

        while (retryCount < maxRetries) {
            try {
                return block()
            } catch (e: CancellationException) {
                //协程被取消时不重试，直接抛出
                throw e
            } catch (e: Exception) {
                lastError = e
                if (canRetry(e)) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)
                    retryCount++
                } else {
                    throw e //不可重试
                }
            }
        }
        throw lastError ?: Exception("Failed after $maxRetries retries")
    }

    private fun canRetry(e: Exception): Boolean {
        return when {
            e is AuthHttpException && e.httpStatus in 500..599 -> true //5xx错误可重试
            e is UnknownHostException -> true // DNS解析失败，可能是临时性故障
            e is IOException -> true //网络错误
            else -> false
        }
    }
}

/** 与 ZL2 的 AuthType 一致：Access = 直接使用访问令牌，Refresh = 刷新令牌换取 */
enum class AuthType {
    Access, Refresh
}

/** 与 ZL2 的 AsyncStatus 一致：认证执行到哪个步骤 */
enum class AsyncStatus {
    GETTING_ACCESS_TOKEN,
    GETTING_XBL_TOKEN,
    GETTING_XSTS_TOKEN,
    AUTHENTICATE_MINECRAFT,
    VERIFY_GAME_OWNERSHIP,
    GETTING_PLAYER_PROFILE
}

/** HTTP 状态异常，携带从响应体解析出的 OAuth error 字段（对应 ZL2 的 ClientRequestException 场景） */
class AuthHttpException(
    val httpStatus: Int,
    val errorCode: String?,
    message: String
) : Exception(message)

/** 与 ZL2 的 CredentialsExpiredException 一致：刷新令牌已被撤销或失效，无法自动恢复 */
class CredentialsExpiredException : Exception("登录已过期，请重新登录")

/** 与 ZL2 的 HttpRequestTimeoutException 一致：设备码过期前用户未完成授权 */
class AuthenticationTimeoutException : Exception("网络访问超时，请检查网络环境。")

/**
 * 与 ZL2 的 XboxLoginException 一致：XSTS 认证返回的账号状态问题
 */
class XboxLoginException(val status: ExceptionStatus) : RuntimeException() {
    enum class ExceptionStatus {
        /** 封禁 */
        BANNED,

        /** 受到限制 */
        RESTRICTED,

        /** 未创建个人资料 */
        UNREGISTERED,

        /** 未同意服务条款 */
        NOT_ACCEPTED_SERVICE,

        /** 地区被禁止登陆 */
        BLOCKED_REGION,

        /** 未提供年龄证明 */
        REQUIRES_PROOF_OF_AGE,

        /** 达到游玩时间限制 */
        REACHED_PLAYTIME_LIMIT,

        /** 未成年 */
        UNDERAGE
    }

    /** 与 ZL2 的 XboxLoginException.toLocal 一致（中文提示） */
    fun toLocalMessage(): String = when (status) {
        ExceptionStatus.BANNED -> "您的账号因违反 Xbox 社区规范已被封禁，无法使用！"
        ExceptionStatus.RESTRICTED -> "您的账号目前受到限制，监护人尚未授权您进行在线游戏。"
        ExceptionStatus.UNREGISTERED -> "您的账号尚未创建 Xbox 个人资料，请前往 https://signup.live.com/signup 注册。"
        ExceptionStatus.NOT_ACCEPTED_SERVICE -> "您的账号尚未接受 Xbox 的服务条款。"
        ExceptionStatus.BLOCKED_REGION -> "您的账号所在的地区未被 Xbox 授权使用，登录尝试已被拦截。"
        ExceptionStatus.REQUIRES_PROOF_OF_AGE -> "您的账号需要提供年龄证明，请登录 https://login.live.com/login.srf 提供相关信息。"
        ExceptionStatus.REACHED_PLAYTIME_LIMIT -> "您的账号已达到游戏时间限制，已被禁止登录。"
        ExceptionStatus.UNDERAGE -> "账号的出生日期显示未满 18 岁，必须由成年人添加到家庭中才能继续使用。"
    }
}

/**
 * 与 ZL2 的 MinecraftProfileException 一致：Minecraft 服务认证 / 档案获取异常
 */
class MinecraftProfileException(val status: ExceptionStatus) : RuntimeException() {
    enum class ExceptionStatus {
        /** 登陆过于频繁 */
        FREQUENT,

        /** IP 地址被禁止 */
        BLOCKED_IP,

        /** 未创建配置 */
        PROFILE_NOT_EXISTS
    }

    /** 与 ZL2 的 MinecraftProfileException.toLocal 一致（中文提示） */
    fun toLocalMessage(): String = when (status) {
        ExceptionStatus.FREQUENT -> "登录过于频繁，请稍后再试。"
        ExceptionStatus.BLOCKED_IP -> "当前 IP 地址已被禁止登陆。"
        ExceptionStatus.PROFILE_NOT_EXISTS -> "请先创建 Minecraft 玩家档案，然后再重新登陆！"
    }
}

/** 与 ZL2 的 NotPurchasedMinecraftException 一致（提示文本采用 ZL2 文案） */
class NotPurchasedMinecraftException : Exception("未检查到 Minecraft 配置信息，请前往 Minecraft 的官方网站购买。")

class EmptyProfileException : Exception("请先创建 Minecraft 玩家档案，然后再重新登陆！")

class AuthlibAuthenticator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun verifyServer(url: String): ServerInfo {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = url.trimEnd('/')
                val request = Request.Builder()
                    .url("$baseUrl/server-meta")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = gson.fromJson(body, JsonObject::class.java)
                        val meta = json?.getAsJsonObject("meta")
                        val serverName = json?.get("serverName")?.takeIf { !it.isJsonNull }?.asString
                            ?: meta?.get("serverName")?.takeIf { !it.isJsonNull }?.asString
                            ?: "未知服务器"
                        val description = json?.get("implementationDescription")?.takeIf { !it.isJsonNull }?.asString
                            ?: meta?.get("implementationDescription")?.takeIf { !it.isJsonNull }?.asString
                            ?: ""
                        ServerInfo(
                            success = true,
                            serverName = serverName,
                            description = description
                        )
                    } else {
                        ServerInfo(success = false)
                    }
                }
            } catch (e: Exception) {
                ServerInfo(success = false)
            }
        }
    }

    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
        profileId: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val body = JsonObject().apply {
                add("agent", JsonObject().apply {
                    addProperty("name", "Minecraft")
                    addProperty("version", 1)
                })
                addProperty("username", username)
                addProperty("password", password)
                addProperty("requestUser", true)
                if (profileId != null) {
                    addProperty("profile", profileId)
                }
            }

            val request = Request.Builder()
                .url("${serverUrl.trimEnd('/')}/authserver/authenticate")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@withContext AuthResult(success = false, error = "登录失败: HTTP ${response.code} $errBody")
                }

                val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
                val accessToken = json.get("accessToken").asString
                val selectedProfile = json.getAsJsonObject("selectedProfile")
                val availableProfiles = json.getAsJsonArray("availableProfiles")

                if (availableProfiles != null && availableProfiles.size() > 1 && selectedProfile == null) {
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
                    return@withContext AuthResult(
                        success = true,
                        profiles = profilesList
                    )
                }

                val profileName = selectedProfile?.get("name")?.asString ?: username
                val resolvedProfileId = selectedProfile?.get("id")?.asString ?: UUID.randomUUID().toString()

                AuthResult(
                    success = true,
                    account = Account(
                        username = profileName,
                        uuid = resolvedProfileId,
                        accessToken = accessToken,
                        type = AccountType.AUTHLIB,
                        serverUrl = serverUrl,
                        loginUsername = username,
                        skinUrl = "https://mc-heads.net/avatar/$resolvedProfileId/64"
                    )
                )
            }
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message ?: "外置登录失败")
        }
    }
}
