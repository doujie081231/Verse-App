package com.verse.explorer.core.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class MicrosoftAuthenticator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val AUTHORITY = "https://login.microsoftonline.com"
        private const val CLIENT_ID = "00000000402b5328"
        private const val SCOPE = "XboxLive.signin offline_access"

        private const val XBOX_LIVE_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
        private const val XBOX_LIVE_XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
        private const val MINECRAFT_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
        private const val MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
        private const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        private const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    }

    suspend fun getDeviceCode(): DeviceCodeResponse = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", SCOPE)
            .build()

        val request = Request.Builder().url(DEVICE_CODE_URL).post(body).build()
        client.newCall(request).execute().use { response ->
            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            DeviceCodeResponse(
                userCode = json.get("user_code").asString,
                deviceCode = json.get("device_code").asString,
                verificationUri = json.get("verification_uri").asString,
                expiresIn = json.get("expires_in").asInt,
                interval = json.get("interval").asInt
            )
        }
    }

    suspend fun awaitDeviceAuth(deviceCode: String, interval: Int): TokenResponse = withContext(Dispatchers.IO) {
        var attempts = 0
        while (attempts < 90) {
            delay(interval * 1000L)
            attempts++

            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("device_code", deviceCode)
                .build()

            val request = Request.Builder().url(TOKEN_URL).post(body).build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()
            if (responseBody == null) continue
            val json = gson.fromJson(responseBody, JsonObject::class.java)

            if (json.has("access_token")) {
                return@withContext TokenResponse(
                    accessToken = json.get("access_token").asString,
                    refreshToken = if (json.has("refresh_token")) json.get("refresh_token").asString else null,
                    expiresIn = json.get("expires_in")?.asLong ?: 3600
                )
            }

            val error = json.get("error")?.asString
            if (error == "authorization_pending") continue
            if (error == "slow_down") { delay(5000); continue }
            if (error == "expired_token") throw Exception("设备码已过期，请重新获取")
            if (error != null) throw Exception("认证失败: $error")
        }
        throw Exception("认证超时")
    }

    suspend fun authenticate(msAccessToken: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val xboxResponse = getXboxLiveToken(msAccessToken)
            val xstsResponse = getXstsToken(xboxResponse.token, xboxResponse.uhs)
            val mcToken = getMinecraftToken(xstsResponse.uhs, xstsResponse.token)
            val profile = getMinecraftProfile(mcToken)

            AuthResult(
                success = true,
                account = Account(
                    username = profile.name,
                    uuid = profile.id,
                    accessToken = mcToken,
                    type = AccountType.MICROSOFT,
                    skinUrl = profile.skinUrl,
                    capeUrl = profile.capeUrl
                )
            )
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message)
        }
    }

    private fun getXboxLiveToken(msToken: String): XboxLiveAuthResponse {
        val properties = JsonObject().apply {
            addProperty("AuthMethod", "RPS")
            addProperty("SiteName", "user.auth.xboxlive.com")
            addProperty("RpsTicket", "d=$msToken")
        }
        val body = JsonObject().apply {
            add("Properties", properties)
            addProperty("RelyingParty", "http://auth.xboxlive.com")
            addProperty("TokenType", "JWT")
        }

        val request = Request.Builder()
            .url(XBOX_LIVE_AUTH_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val token = json.get("Token").asString
            val uhs = json.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).asJsonObject.get("uhs").asString
            return XboxLiveAuthResponse(token, uhs)
        }
    }

    private fun getXstsToken(xblToken: String, uhs: String): XboxLiveAuthResponse {
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
            .url(XBOX_LIVE_XSTS_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw Exception("XSTS 认证失败 (HTTP ${response.code}): $errorBody")
            }
            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val token = json.get("Token").asString
            val newUhs = json.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).asJsonObject.get("uhs").asString
            return XboxLiveAuthResponse(token, newUhs)
        }
    }

    private fun getMinecraftToken(uhs: String, xstsToken: String): String {
        val body = JsonObject().apply {
            addProperty("identityToken", "XBL3.0 x=$uhs;$xstsToken")
        }

        val request = Request.Builder()
            .url(MINECRAFT_AUTH_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            return json.get("access_token").asString
        }
    }

    private fun getMinecraftProfile(mcToken: String): MinecraftProfile {
        val request = Request.Builder()
            .url(MINECRAFT_PROFILE_URL)
            .header("Authorization", "Bearer $mcToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) throw NotPurchasedMinecraftException()
                throw Exception("获取 Minecraft 档案失败 (HTTP ${response.code})")
            }
            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val id = json.get("id").asString
            val name = json.get("name").asString
            var skinUrl: String? = null
            var capeUrl: String? = null
            val skins = json.getAsJsonArray("skins")
            skins?.forEach { skin ->
                val s = skin.asJsonObject
                if (s.get("state").asString == "ACTIVE") {
                    skinUrl = s.get("url").asString
                }
            }
            val capes = json.getAsJsonArray("capes")
            capes?.forEach { cape ->
                val c = cape.asJsonObject
                if (c.get("state").asString == "ACTIVE") {
                    capeUrl = c.get("url").asString
                }
            }
            return MinecraftProfile(id, name, skinUrl, capeUrl)
        }
    }
}

class NotPurchasedMinecraftException : Exception("该微软账号未购买 Minecraft Java Edition")

class AuthlibAuthenticator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String
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
                val profileName = selectedProfile?.get("name")?.asString ?: username
                val profileId = selectedProfile?.get("id")?.asString ?: UUID.randomUUID().toString()

                AuthResult(
                    success = true,
                    account = Account(
                        username = profileName,
                        uuid = profileId,
                        accessToken = accessToken,
                        type = AccountType.AUTHLIB,
                        serverUrl = serverUrl,
                        loginUsername = username,
                        skinUrl = "https://mc-heads.net/avatar/$profileId/64"
                    )
                )
            }
        } catch (e: Exception) {
            AuthResult(success = false, error = e.message ?: "外置登录失败")
        }
    }
}
