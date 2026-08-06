package com.verse.explorer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.core.account.Account
import com.verse.explorer.core.account.AuthResult
import com.verse.explorer.core.account.DeviceCodeResponse
import com.verse.explorer.core.account.ServerInfo
import com.verse.explorer.core.account.TokenResponse
import kotlinx.coroutines.flow.StateFlow

class AccountsViewModel(application: Application) : AndroidViewModel(application) {

    private val accountManager = VerseApplication.accountManager
    private val msAuth = VerseApplication.microsoftAuthenticator
    private val authlibAuth = VerseApplication.authlibAuthenticator

    val accounts: StateFlow<List<Account>> = accountManager.accounts
    val selectedAccount: StateFlow<Account?> = accountManager.selectedAccount

    suspend fun loadAccounts() {
        accountManager.loadAccounts()
    }

    suspend fun selectAccount(accountId: Int) {
        accountManager.selectAccount(accountId)
    }

    suspend fun removeAccount(accountId: Int) {
        accountManager.removeAccount(accountId)
    }

    suspend fun createOfflineAccount(username: String): Account {
        return accountManager.createOfflineAccount(username)
    }

    suspend fun addAccount(account: Account) {
        accountManager.addAccount(account)
    }

    suspend fun updateAccountSkin(accountId: Int, skinFile: String?, skinModel: String) {
        accountManager.updateAccountSkin(accountId, skinFile, skinModel)
    }

    suspend fun getDeviceCode(): DeviceCodeResponse {
        return msAuth.getDeviceCode()
    }

    suspend fun awaitDeviceAuth(deviceCode: String, interval: Int): TokenResponse {
        var retries = 0
        var currentDeviceCode = deviceCode
        var currentInterval = interval
        while (true) {
            try {
                return msAuth.awaitDeviceAuth(currentDeviceCode, currentInterval)
            } catch (e: Exception) {
                val message = e.message ?: ""
                if (message.contains("过期") || message.contains("expired")) {
                    if (retries >= 2) {
                        throw e
                    }
                    retries++
                    val newCode = msAuth.getDeviceCode()
                    currentDeviceCode = newCode.deviceCode
                    currentInterval = newCode.interval
                } else {
                    throw e
                }
            }
        }
    }

    suspend fun authenticateMicrosoft(accessToken: String): AuthResult {
        return msAuth.authenticate(accessToken)
    }

    suspend fun authenticateAuthlib(serverUrl: String, username: String, password: String): AuthResult {
        return authlibAuth.authenticate(serverUrl, username, password)
    }

    suspend fun verifyAuthlibServer(url: String): ServerInfo {
        return authlibAuth.verifyServer(url)
    }

    suspend fun authenticateAuthlibWithProfile(
        serverUrl: String,
        username: String,
        password: String,
        profileId: String? = null
    ): AuthResult {
        return authlibAuth.authenticate(serverUrl, username, password, profileId)
    }
}
