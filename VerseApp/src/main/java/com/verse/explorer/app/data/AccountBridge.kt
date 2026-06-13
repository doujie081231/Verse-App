package com.verse.explorer.app.data

import android.content.Context
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.core.account.AccountType

data class UnifiedAccount(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val userType: String,
    val accountType: String
)

object AccountBridge {

    fun getActiveAccount(context: Context): UnifiedAccount? {
        val appAccount = getFromAccountRepository(context)
        if (appAccount != null) return appAccount

        return getFromAccountManager()
    }

    private fun getFromAccountRepository(context: Context): UnifiedAccount? {
        val repo = AccountRepository(context)
        val selectedId = repo.getSelectedAccountId() ?: return null
        val accounts = repo.getAllAccounts()
        val account = accounts.find { it.id == selectedId } ?: accounts.firstOrNull() ?: return null

        val accessToken = account.accessToken ?: return null

        val userType = mapAppAccountType(account.type)

        return UnifiedAccount(
            username = account.name,
            uuid = account.uuid ?: "",
            accessToken = accessToken,
            userType = userType,
            accountType = account.type
        )
    }

    private fun getFromAccountManager(): UnifiedAccount? {
        return try {
            val accountManager = VerseApplication.accountManager
            val account = accountManager.selectedAccount.value ?: return null

            val userType = mapCoreAccountType(account.type)

            UnifiedAccount(
                username = account.username,
                uuid = account.uuid,
                accessToken = account.accessToken,
                userType = userType,
                accountType = account.type.name
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun mapCoreAccountType(type: AccountType): String {
        return when (type) {
            AccountType.MICROSOFT -> "msa"
            AccountType.AUTHLIB -> "authlib"
            AccountType.OFFLINE -> "legacy"
        }
    }

    private fun mapAppAccountType(type: String): String {
        return when (type.lowercase()) {
            "msa", "microsoft" -> "msa"
            "mojang" -> "mojang"
            "authlib" -> "authlib"
            "offline" -> "legacy"
            else -> "legacy"
        }
    }
}
