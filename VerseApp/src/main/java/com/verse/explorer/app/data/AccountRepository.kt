package com.verse.explorer.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import com.google.gson.GsonBuilder

class AccountRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "verse_accounts"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_SELECTED = "selected_account"
    }

    data class Account(
        val id: String,
        val name: String,
        val type: String,
        val avatar: String? = null,
        val serverUrl: String? = null,
        val username: String? = null,
        val accessToken: String? = null,
        val uuid: String? = null,
        val clientToken: String? = null
    )

    data class Profile(
        val id: String,
        val name: String,
        val skinUrl: String? = null
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = GsonBuilder().setLenient().create()

    fun getAllAccounts(): List<Account> {
        val jsonStr = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Account(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    type = obj.optString("type", "offline"),
                    avatar = if (obj.has("avatar")) obj.optString("avatar") else null,
                    serverUrl = if (obj.has("serverUrl")) obj.optString("serverUrl") else null,
                    username = if (obj.has("username")) obj.optString("username") else null,
                    accessToken = if (obj.has("accessToken")) obj.optString("accessToken") else null,
                    uuid = if (obj.has("uuid")) obj.optString("uuid") else null
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("AccountRepository", "Failed to parse accounts", e)
            emptyList()
        }
    }

    fun saveAccounts(accounts: List<Account>) {
        val arr = org.json.JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject()
            obj.put("id", acc.id)
            obj.put("name", acc.name)
            obj.put("type", acc.type)
            acc.avatar?.let { obj.put("avatar", it) }
            acc.serverUrl?.let { obj.put("serverUrl", it) }
            acc.username?.let { obj.put("username", it) }
            acc.accessToken?.let { obj.put("accessToken", it) }
            acc.uuid?.let { obj.put("uuid", it) }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    fun getSelectedAccountId(): String? = prefs.getString(KEY_SELECTED, null)

    fun setSelectedAccountId(id: String?) {
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    fun createOfflineAccount(username: String): Account? {
        val existing = getAllAccounts()
        if (existing.any { it.type == "offline" && it.name == username }) {
            return existing.find { it.type == "offline" && it.name == username }
        }

        val account = Account(
            id = UUID.randomUUID().toString(),
            name = username,
            type = "offline",
            uuid = generateOfflineUUID(username)
        )
        
        saveAccounts(existing + account)
        setSelectedAccountId(account.id)
        return account
    }

    data class AuthlibLoginResult(
        val success: Boolean,
        val account: Account? = null,
        val needSelectProfile: Boolean = false,
        val accessToken: String? = null,
        val clientToken: String? = null,
        val serverUrl: String? = null,
        val availableProfiles: List<Profile> = emptyList(),
        val error: String? = null
    )

    fun createAuthlibAccount(serverUrl: String, username: String, password: String): AuthlibLoginResult {
        return try {
            val authUrl = "$serverUrl/authserver/authenticate"
            
            val body = JSONObject().apply {
                put("agent", JSONObject().apply { put("name", "Minecraft"); put("version", 1) })
                put("username", username)
                put("password", password)
                put("requestUser", true)
            }.toString()
            
            val url = URL(authUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            
            conn.outputStream.use { os -> os.write(body.toByteArray()) }
            
            val responseCode = conn.responseCode
            val response = conn.inputStream.bufferedReader().readText()
            
            if (responseCode == 200) {
                val respJson = JSONObject(response)
                val accessToken = respJson.optString("accessToken")
                val clientToken = respJson.optString("clientToken")
                var profile = respJson.optJSONObject("selectedProfile")
                
                val availableProfilesArray = respJson.optJSONArray("availableProfiles")
                val availableProfiles = mutableListOf<Profile>()
                
                if (availableProfilesArray != null) {
                    for (i in 0 until availableProfilesArray.length()) {
                        val p = availableProfilesArray.getJSONObject(i)
                        availableProfiles.add(Profile(
                            id = p.optString("id"),
                            name = p.optString("name"),
                            skinUrl = getSkinUrl(p.optString("id"))
                        ))
                    }
                }
                
                if (profile == null && availableProfiles.isEmpty()) {
                    return AuthlibLoginResult(
                        success = false,
                        error = "未找到游戏角色，请先在皮肤站创建角色",
                        serverUrl = serverUrl
                    )
                }
                
                if (profile == null && availableProfiles.size > 1) {
                    android.util.Log.d("AccountRepository", "Multiple profiles found, need selection")
                    return AuthlibLoginResult(
                        success = false,
                        needSelectProfile = true,
                        accessToken = accessToken,
                        clientToken = clientToken,
                        serverUrl = serverUrl,
                        availableProfiles = availableProfiles
                    )
                }
                
                if (profile == null && availableProfiles.size == 1) {
                    profile = org.json.JSONObject().apply {
                        put("id", availableProfiles[0].id)
                        put("name", availableProfiles[0].name)
                    }
                }
                
                val selectedProfileId = profile?.optString("id") ?: availableProfiles.firstOrNull()?.id ?: ""
                val selectedProfileName = profile?.optString("name") ?: availableProfiles.firstOrNull()?.name ?: username
                
                val account = Account(
                    id = UUID.randomUUID().toString(),
                    name = selectedProfileName,
                    type = "authlib",
                    serverUrl = serverUrl,
                    username = username,
                    accessToken = accessToken,
                    uuid = selectedProfileId,
                    clientToken = clientToken,
                    avatar = getSkinUrl(selectedProfileId)
                )
                
                val existing = getAllAccounts()
                saveAccounts(existing + account)
                setSelectedAccountId(account.id)
                
                AuthlibLoginResult(success = true, account = account)
            } else {
                android.util.Log.w("AccountRepository", "Authlib login failed: $responseCode $response")
                AuthlibLoginResult(success = false, error = "登录失败: HTTP $responseCode")
            }
        } catch (e: Exception) {
            android.util.Log.e("AccountRepository", "Authlib login error", e)
            AuthlibLoginResult(success = false, error = e.message ?: "登录失败")
        }
    }
    
    fun selectProfile(accessToken: String, clientToken: String?, serverUrl: String, profileId: String, profileName: String): Account? {
        return try {
            val apiUrl = serverUrl.replace(Regex("/$"), "")
            val refreshUrl = "$apiUrl/authserver/refresh"
            
            val body = JSONObject().apply {
                put("accessToken", accessToken)
                put("clientToken", clientToken ?: "")
                put("selectedProfile", JSONObject().apply {
                    put("id", profileId)
                    put("name", profileName)
                })
                put("requestUser", true)
            }.toString()
            
            val url = URL(refreshUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            
            conn.outputStream.use { os -> os.write(body.toByteArray()) }
            
            val responseCode = conn.responseCode
            val response = conn.inputStream.bufferedReader().readText()
            
            if (responseCode == 200) {
                val respJson = JSONObject(response)
                val newAccessToken = respJson.optString("accessToken", accessToken)
                val refreshedProfile = respJson.optJSONObject("selectedProfile")
                
                val finalProfileId = refreshedProfile?.optString("id") ?: profileId
                val finalProfileName = refreshedProfile?.optString("name") ?: profileName
                
                val account = Account(
                    id = UUID.randomUUID().toString(),
                    name = finalProfileName,
                    type = "authlib",
                    serverUrl = serverUrl,
                    username = "",
                    accessToken = newAccessToken,
                    uuid = finalProfileId,
                    clientToken = clientToken,
                    avatar = getSkinUrl(finalProfileId)
                )
                
                val existing = getAllAccounts()
                saveAccounts(existing + account)
                setSelectedAccountId(account.id)
                
                account
            } else {
                android.util.Log.w("AccountRepository", "Profile selection failed: $responseCode $response")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AccountRepository", "Profile selection error", e)
            null
        }
    }
    
    private fun getSkinUrl(uuid: String): String {
        val dashedUuid = if (uuid.contains("-")) uuid else 
            "${uuid.substring(0, 8)}-${uuid.substring(8, 12)}-${uuid.substring(12, 16)}-${uuid.substring(16, 20)}-${uuid.substring(20)}"
        return "https://mc-heads.net/avatar/$dashedUuid/64"
    }

    fun deleteAccount(accountId: String): Boolean {
        val accounts = getAllAccounts().filter { it.id != accountId }
        saveAccounts(accounts)
        
        if (getSelectedAccountId() == accountId) {
            setSelectedAccountId(accounts.firstOrNull()?.id)
        }
        return true
    }

    private fun generateOfflineUUID(name: String): String {
        var hash = 0L
        for (c in name) {
            hash = (hash shl 5) - hash + c.code.toLong()
        }
        val hex = java.math.BigInteger(java.math.BigInteger.valueOf(hash).toString(16), 16).toString(16).padStart(32, '0')
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
