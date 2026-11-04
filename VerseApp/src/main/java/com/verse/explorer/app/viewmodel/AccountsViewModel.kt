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

/**
 * Verse 皮肤站快捷登录状态（设备码流程，与微软登录同构）。
 *
 * [Waiting] 附带设备码与授权页地址，账号页据此渲染内置浏览器 + 右下角识别码卡片。
 */
sealed class VerseLoginState {
    data object Idle : VerseLoginState()

    /** 正在向皮肤站申请设备码 */
    data object Requesting : VerseLoginState()

    /** 已拿到设备码，等待用户在浏览器里确认授权 */
    data class Waiting(
        val code: com.verse.explorer.core.account.VerseQuickLogin.DeviceCode,
        val status: String,
        /** 授权页地址（已带 user_code，网页端会预填验证码） */
        val webUrl: String
    ) : VerseLoginState()

    data class Success(val account: Account) : VerseLoginState()

    data class Failed(val message: String) : VerseLoginState()

    data object Cancelled : VerseLoginState()
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

    /** Verse 皮肤站快捷登录（设备码流程，见 VerseQuickLogin 的接口契约） */
    private val verseQuickLogin = com.verse.explorer.core.account.VerseQuickLogin()

    private val _verseLoginState = MutableStateFlow<VerseLoginState>(VerseLoginState.Idle)
    val verseLoginState: StateFlow<VerseLoginState> = _verseLoginState.asStateFlow()

    private var verseLoginJob: Job? = null

    /**
     * 发起 Verse 快捷登录：申请设备码 → 交给账号页用内置浏览器打开授权页 → 轮询 → 建档。
     * 会话在 viewModelScope 里跑，用户退出浏览器页前轮询不受影响。
     */
    fun startVerseLogin(
        serverUrl: String = com.verse.explorer.core.account.VerseQuickLogin.DEFAULT_SERVER
    ) {
        if (verseLoginJob?.isActive == true) {
            VerseLogger.warn("Verse 登录：上次流程仍在进行，忽略重复启动", "VerseLogin")
            return
        }
        val origin = verseQuickLogin.originOf(serverUrl)
        if (origin.isBlank()) {
            _verseLoginState.value = VerseLoginState.Failed("皮肤站地址无效")
            return
        }
        _verseLoginState.value = VerseLoginState.Requesting
        VerseLogger.info("Verse 登录：申请设备码 origin=$origin", "VerseLogin")
        verseLoginJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val code = verseQuickLogin.requestDeviceCode(origin).getOrElse { e ->
                    VerseLogger.error("Verse 登录：设备码申请失败 ${e.message}", "VerseLogin")
                    _verseLoginState.value = VerseLoginState.Failed(e.message ?: "无法发起 Verse 快捷登录")
                    return@launch
                }
                val webUrl = com.verse.explorer.core.account.VerseQuickLogin.authorizeUrl(code)
                VerseLogger.info("Verse 登录：设备码 ${code.userCode} 授权页 $webUrl", "VerseLogin")
                _verseLoginState.value = VerseLoginState.Waiting(code, "等待在浏览器中确认授权…", webUrl)

                var intervalMs = code.intervalSec * 1000
                var consecutiveErrors = 0
                val deadline = System.currentTimeMillis() + code.expiresInSec * 1000
                var round = 0
                while (true) {
                    kotlinx.coroutines.delay(intervalMs)
                    if (System.currentTimeMillis() > deadline) {
                        _verseLoginState.value = VerseLoginState.Failed("授权码已过期，请点「重新授权」")
                        return@launch
                    }
                    round++
                    val result = verseQuickLogin.pollOnce(origin, serverUrl.trim(), code)
                    when (result.state) {
                        com.verse.explorer.core.account.VerseQuickLogin.PollState.PENDING -> {
                            consecutiveErrors = 0
                            _verseLoginState.value = VerseLoginState.Waiting(
                                code, "等待在浏览器中确认授权…（第 $round 次检查）", webUrl
                            )
                        }
                        com.verse.explorer.core.account.VerseQuickLogin.PollState.SLOW_DOWN -> {
                            intervalMs += 3000
                            _verseLoginState.value = VerseLoginState.Waiting(
                                code, "授权等待中（服务端要求放慢轮询）", webUrl
                            )
                        }
                        com.verse.explorer.core.account.VerseQuickLogin.PollState.SUCCESS -> {
                            val account = result.account?.account
                            if (account == null) {
                                _verseLoginState.value = VerseLoginState.Failed("登录失败：账号信息为空")
                            } else {
                                val saved = saveVerseAccount(account)
                                VerseLogger.info(
                                    "Verse 登录：成功 ${saved.username} (uuid=${saved.uuid})",
                                    "VerseLogin"
                                )
                                _verseLoginState.value = VerseLoginState.Success(saved)
                            }
                            return@launch
                        }
                        else -> {
                            // 轮询期间的网络抖动不应直接判死（切网、锁屏、服务器瞬时 5xx）：
                            // 连续失败 6 次（约 30 秒）才终止。只有明确的拒绝/过期才立即结束，
                            // 这两类在上面分支已单独处理。
                            consecutiveErrors++
                            if (consecutiveErrors >= 6) {
                                _verseLoginState.value = VerseLoginState.Failed(
                                    result.message ?: "授权失败"
                                )
                                return@launch
                            }
                            VerseLogger.warn(
                                "Verse 登录：轮询失败（第 $consecutiveErrors 次），继续重试 - ${result.message}",
                                "VerseLogin"
                            )
                            _verseLoginState.value = VerseLoginState.Waiting(
                                code,
                                "网络异常，正在重试（$consecutiveErrors/6）…",
                                webUrl
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                VerseLogger.warn("Verse 登录：流程被取消", "VerseLogin")
                _verseLoginState.value = VerseLoginState.Cancelled
            } catch (e: Exception) {
                VerseLogger.error("Verse 登录：异常 ${e.javaClass.simpleName}: ${e.message}", "VerseLogin")
                _verseLoginState.value = VerseLoginState.Failed(e.message ?: "登录异常")
            }
        }
    }

    /** 重新授权：丢掉旧设备码重新申请（授权页打不开/验证码过期时用） */
    fun restartVerseLogin(
        serverUrl: String = com.verse.explorer.core.account.VerseQuickLogin.DEFAULT_SERVER
    ) {
        VerseLogger.info("Verse 登录：重新授权（重新申请设备码）", "VerseLogin")
        verseLoginJob?.cancel()
        verseLoginJob = null
        _verseLoginState.value = VerseLoginState.Idle
        startVerseLogin(serverUrl)
    }

    fun cancelVerseLogin() {
        VerseLogger.warn("Verse 登录：取消（退出浏览器页）", "VerseLogin")
        verseLoginJob?.cancel()
        verseLoginJob = null
        _verseLoginState.value = VerseLoginState.Cancelled
    }

    /** 复位状态，回到 Idle */
    fun resetVerseLogin() {
        if (verseLoginJob?.isActive != true) {
            _verseLoginState.value = VerseLoginState.Idle
        }
    }

    /** 同一角色已存在时更新令牌而不是重复添加 */
    private suspend fun saveVerseAccount(account: Account): Account {
        val existing = accounts.value.find {
            it.uuid == account.uuid && it.type == AccountType.AUTHLIB
        }
        return if (existing != null) {
            val merged = existing.copy(
                username = account.username,
                accessToken = account.accessToken,
                serverUrl = account.serverUrl,
                loginUsername = account.loginUsername,
                skinUrl = account.skinUrl
            )
            accountManager.updateAccount(merged)
            accountManager.selectAccount(existing.id)
            merged
        } else {
            accountManager.addAccount(account.copy(selected = true))
            account.copy(selected = true)
        }
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

    companion object {
        @Volatile
        private var cached: AccountsViewModel? = null

        /** 应用级共享实例：切换页签时复用账号状态，避免整页重建后重复加载账号数据 */
        fun shared(application: Application): AccountsViewModel =
            cached ?: synchronized(this) {
                cached ?: AccountsViewModel(application).also { cached = it }
            }
    }
}
