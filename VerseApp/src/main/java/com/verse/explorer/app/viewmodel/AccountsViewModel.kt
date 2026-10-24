package com.verse.explorer.app.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.core.account.Account
import com.verse.explorer.core.account.AccountType
import com.verse.explorer.core.account.AsyncStatus
import com.verse.explorer.core.account.AuthResult
import com.verse.explorer.core.account.DeviceCodeResponse
import com.verse.explorer.core.account.ServerInfo
import com.verse.explorer.core.account.TokenResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 */
sealed class MicrosoftLoginState {
    data object Idle : MicrosoftLoginState()

    /** 已取到设备码，正在内置浏览器页面等待用户完成授权 */
    data class Polling(val deviceCode: DeviceCodeResponse) : MicrosoftLoginState()

    /** 设备码授权完成，正在执行认证链 */
    data class Authenticating(val status: AsyncStatus) : MicrosoftLoginState()

    data class Success(val account: Account) : MicrosoftLoginState()

    data class Failed(
        val message: String?,
        val needPurchase: Boolean = false,
        val needCreateProfile: Boolean = false,
        val needRelogin: Boolean = false
    ) : MicrosoftLoginState()

    data object Cancelled : MicrosoftLoginState()
}

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

    suspend fun updateAccountSkinUrl(accountId: Int, skinUrl: String?, skinModel: String) {
        accountManager.updateAccountSkinUrl(accountId, skinUrl, skinModel)
    }

    suspend fun getDeviceCode(): DeviceCodeResponse {
        VerseLogger.info("账号页：请求微软设备码", "MsaLogin")
        val code = msAuth.getDeviceCode()
        VerseLogger.info("账号页：设备码获取成功 user_code=${code.userCode} verification_uri=${code.verificationUri}（浏览器页将打开此地址）", "MsaLogin")
        return code
    }

    private val _microsoftLoginState = MutableStateFlow<MicrosoftLoginState>(MicrosoftLoginState.Idle)
    val microsoftLoginState: StateFlow<MicrosoftLoginState> = _microsoftLoginState.asStateFlow()

    private var microsoftLoginJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 轮询设备码授权 → 完整认证链 → 保存账号。
     * 轮询期间用户在内置浏览器页完成验证；离开浏览器页时调用 [cancelMicrosoftLogin]。
     */
    fun startMicrosoftLogin(deviceCode: DeviceCodeResponse) {
        if (microsoftLoginJob?.isActive == true) {
            VerseLogger.warn("微软登录：上次轮询仍在进行，忽略重复启动", "MsaLogin")
            return
        }
        VerseLogger.info("微软登录：启动认证轮询 user_code=${deviceCode.userCode} verification_uri=${deviceCode.verificationUri}（应已同步打开内置浏览器页）", "MsaLogin")
        _microsoftLoginState.value = MicrosoftLoginState.Polling(deviceCode)
        microsoftLoginJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = msAuth.awaitDeviceAuth(deviceCode)
                VerseLogger.info("微软登录：设备码授权成功，进入完整认证链", "MsaLogin")
                val result = msAuth.authenticate(token.accessToken, token.refreshToken) { status ->
                    mainHandler.post {
                        VerseLogger.info("微软登录：认证进度 ${status.name}", "MsaLogin")
                        _microsoftLoginState.value = MicrosoftLoginState.Authenticating(status)
                    }
                }
                if (result.success && result.account != null) {
                    val saved = saveMicrosoftAccount(result.account!!)
                    VerseLogger.info("微软登录：认证成功，保存账号 ${saved.username} (uuid=${saved.uuid})", "MsaLogin")
                    _microsoftLoginState.value = MicrosoftLoginState.Success(saved)
                } else {
                    VerseLogger.error(
                        "微软登录：认证失败 error=${result.error}, needPurchase=${result.needPurchase}, needCreateProfile=${result.needCreateProfile}, needRelogin=${result.needRelogin}",
                        "MsaLogin"
                    )
                    _microsoftLoginState.value = MicrosoftLoginState.Failed(
                        message = result.error,
                        needPurchase = result.needPurchase,
                        needCreateProfile = result.needCreateProfile,
                        needRelogin = result.needRelogin
                    )
                }
            } catch (e: CancellationException) {
                VerseLogger.warn("微软登录：流程被取消（用户离开浏览器页）", "MsaLogin")
                _microsoftLoginState.value = MicrosoftLoginState.Cancelled
            } catch (e: Exception) {
                VerseLogger.error("微软登录：流程异常 ${e.javaClass.simpleName}: ${e.message}", "MsaLogin")
                _microsoftLoginState.value = MicrosoftLoginState.Failed(message = e.message)
            }
        }
    }

    fun cancelMicrosoftLogin() {
        VerseLogger.warn("微软登录：取消（返回/离开浏览器页）", "MsaLogin")
        microsoftLoginJob?.cancel()
        microsoftLoginJob = null
        _microsoftLoginState.value = MicrosoftLoginState.Cancelled
    }

    /** 复位登录状态，回到 Idle */
    fun resetMicrosoftLogin() {
        if (microsoftLoginJob?.isActive != true) {
            _microsoftLoginState.value = MicrosoftLoginState.Idle
        }
    }

    /**
     * 设备码过期时抛出 [AuthenticationTimeoutException]，由 UI 提示重新获取。
     */
    suspend fun awaitDeviceAuth(codeResponse: DeviceCodeResponse): TokenResponse {
        return msAuth.awaitDeviceAuth(codeResponse)
    }

    suspend fun authenticateMicrosoft(
        accessToken: String,
        refreshToken: String? = null,
        onStatus: (AsyncStatus) -> Unit = {}
    ): AuthResult {
        return msAuth.authenticate(accessToken, refreshToken, onStatus)
    }

    suspend fun validateMicrosoft(account: Account): Boolean {
        return msAuth.validateAccessToken(account)
    }

    /**
     * 同一档案已存在时更新而不是重复添加
     */
    suspend fun saveMicrosoftAccount(account: Account): Account {
        val existing = accounts.value.find {
            it.uuid == account.uuid && it.type == AccountType.MICROSOFT
        }
        return if (existing != null) {
            val merged = existing.copy(
                username = account.username,
                accessToken = account.accessToken,
                refreshToken = account.refreshToken,
                skinUrl = account.skinUrl,
                capeUrl = account.capeUrl
            )
            accountManager.updateAccount(merged)
            accountManager.selectAccount(existing.id)
            merged
        } else {
            accountManager.addAccount(account.copy(selected = true))
            account.copy(selected = true)
        }
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
