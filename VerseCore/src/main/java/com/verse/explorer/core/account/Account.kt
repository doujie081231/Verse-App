package com.verse.explorer.core.account

import androidx.room.TypeConverter

enum class AccountType {
    OFFLINE, MICROSOFT, AUTHLIB
}

class AccountTypeConverter {
    @TypeConverter
    fun toAccountType(value: String): AccountType = try { AccountType.valueOf(value) } catch (_: Exception) { AccountType.OFFLINE }

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name
}

data class Account(
    val id: Int = 0,
    val username: String,
    val uuid: String,
    val accessToken: String = "",
    val refreshToken: String = "",
    val type: AccountType = AccountType.OFFLINE,
    val skinUrl: String? = null,
    val capeUrl: String? = null,
    val serverUrl: String? = null,
    val loginUsername: String? = null,
    val skinFile: String? = null,
    val skinModel: String = "default",
    val selected: Boolean = false
)

data class AuthlibProfile(
    val id: String,
    val name: String
)

data class ServerInfo(
    val success: Boolean,
    val serverName: String = "",
    val description: String = ""
)

data class AuthResult(
    val success: Boolean,
    val account: Account? = null,
    val error: String? = null,
    val profiles: List<AuthlibProfile>? = null,
    val needPurchase: Boolean = false,
    val needCreateProfile: Boolean = false,
    val needRelogin: Boolean = false,
    val xerr: Int? = null,
    val isRateLimit: Boolean = false,
    val retryAfter: Int? = null
)

data class DeviceCodeResponse(
    val userCode: String,
    val deviceCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long
)

data class XboxLiveAuthResponse(
    val token: String,
    val uhs: String
)

data class MinecraftProfile(
    val id: String,
    val name: String,
    val skinUrl: String? = null,
    val capeUrl: String? = null
)
