package com.verse.explorer.app.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verse.explorer.app.VerseApplication
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
 * 微软登录流程状态（与 ZL2 的 microsoftLogin 任务编排对应）
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

    /** 用户离开内置浏览器页，流程被取消（与 ZL2 的 account_microsoft_exit_by_user 对应） */
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
        return msAuth.getDeviceCode()
    }

    private val _microsoftLoginState = MutableStateFlow<MicrosoftLoginState>(MicrosoftLoginState.Idle)
    val microsoftLoginState: StateFlow<MicrosoftLoginState> = _microsoftLoginState.asStateFlow()

    private var microsoftLoginJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 启动微软登录（与 ZL2 的 microsoftLogin 编排对应）：
     * 轮询设备码授权 → 完整认证链 → 保存账号。
     * 轮询期间用户在内置浏览器页完成验证；离开浏览器页时调用 [cancelMicrosoftLogin]。
     */
    fun startMicrosoftLogin(deviceCode: DeviceCodeResponse) {
        if (microsoftLoginJob?.isActive == true) return
        _microsoftLoginState.value = MicrosoftLoginState.Polling(deviceCode)
        microsoftLoginJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = msAuth.awaitDeviceAuth(deviceCode)
                val result = msAuth.authenticate(token.accessToken, token.refreshToken) { status ->
                    mainHandler.post {
                        _microsoftLoginState.value = MicrosoftLoginState.Authenticating(status)
                    }
                }
                if (result.success && result.account != null) {
                    // 与 ZL2 一致：同一档案更新而不是重复添加
                    val saved = saveMicrosoftAccount(result.account!!)
                    _microsoftLoginState.value = MicrosoftLoginState.Success(saved)
                } else {
                    _microsoftLoginState.value = MicrosoftLoginState.Failed(
                        message = result.error,
                        needPurchase = result.needPurchase,
                        needCreateProfile = result.needCreateProfile,
                        needRelogin = result.needRelogin
                    )
                }
            } catch (e: CancellationException) {
                _microsoftLoginState.value = MicrosoftLoginState.Cancelled
            } catch (e: Exception) {
                _microsoftLoginState.value = MicrosoftLoginState.Failed(message = e.message)
            }
        }
    }

    /** 用户离开内置浏览器页时取消登录（与 ZL2 的 checkIfInWebScreen 取消逻辑对应） */
    fun cancelMicrosoftLogin() {
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
     * 设备码轮询（自 ZL2 迁移）：过期、限速、网络波动等均由认证模块内部处理，
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

    /** 校验微软账号缓存的访问令牌是否仍有效（自 ZL2 迁移） */
    suspend fun validateMicrosoft(account: Account): Boolean {
        return msAuth.validateAccessToken(account)
    }

    /**
     * 保存微软账号（与 ZL2 的 AccountsManager.loadFromProfileID 对应）：
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
