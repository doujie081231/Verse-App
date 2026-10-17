package com.verse.explorer.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.components.AccountAvatar
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.pcTextFieldColors
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.account.Account
import com.verse.explorer.core.account.AccountType
import com.verse.explorer.core.account.AsyncStatus
import com.verse.explorer.core.account.AuthlibProfile
import com.verse.explorer.core.account.AuthResult
import com.verse.explorer.core.account.DeviceCodeResponse
import com.verse.explorer.core.account.ServerInfo
import com.verse.explorer.app.viewmodel.MicrosoftLoginState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddAccountSheet(
    viewModel: AccountsViewModel,
    initialType: String,
    onDismiss: () -> Unit,
    onMicrosoftWeb: (url: String, userCode: String) -> Unit = { _, _ -> },
    onProfilesAvailable: (serverUrl: String, username: String, password: String, profiles: List<AuthlibProfile>) -> Unit
) {
    // 方式选择卡片优先：initialType 为空时先进登录方式选择步骤（step 0），由用户挑选登录方式
    var step by remember { mutableStateOf(if (initialType.isBlank()) 0 else 1) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val msaLoginState by viewModel.microsoftLoginState.collectAsState()

    var selectedType by remember { mutableStateOf(initialType) }

    var offlineUsername by remember { mutableStateOf("") }
    var authlibServerUrl by remember { mutableStateOf("") }
    var authlibUsername by remember { mutableStateOf("") }
    var authlibPassword by remember { mutableStateOf("") }

    var deviceCodeResult by remember { mutableStateOf<DeviceCodeResponse?>(null) }
    var authStatus by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    var serverInfo by remember { mutableStateOf<ServerInfo?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    LaunchedEffect(msaLoginState) {
        when (val state = msaLoginState) {
            is MicrosoftLoginState.Polling -> {
                step = 2
                authStatus = "等待在浏览器中完成授权..."
            }
            is MicrosoftLoginState.Authenticating -> {
                step = 2
                authStatus = when (state.status) {
                    AsyncStatus.GETTING_ACCESS_TOKEN -> "获取访问令牌"
                    AsyncStatus.GETTING_XBL_TOKEN -> "获取 XBL 令牌"
                    AsyncStatus.GETTING_XSTS_TOKEN -> "获取 XSTS 令牌"
                    AsyncStatus.AUTHENTICATE_MINECRAFT -> "验证 Minecraft 基本信息"
                    AsyncStatus.VERIFY_GAME_OWNERSHIP -> "验证是否拥有 Minecraft"
                    AsyncStatus.GETTING_PLAYER_PROFILE -> "获取玩家账户信息"
                }
            }
            is MicrosoftLoginState.Success -> {
                viewModel.resetMicrosoftLogin()
                onDismiss()
            }
            is MicrosoftLoginState.Failed -> {
                authError = formatMicrosoftError(
                    AuthResult(
                        success = false,
                        error = state.message,
                        needPurchase = state.needPurchase,
                        needCreateProfile = state.needCreateProfile,
                        needRelogin = state.needRelogin
                    )
                )
                isAuthenticating = false
                deviceCodeResult = null
                step = 1
                viewModel.resetMicrosoftLogin()
            }
            is MicrosoftLoginState.Cancelled -> {
                authStatus = "您离开了浏览器页面，已取消微软登录流程"
                isAuthenticating = false
                deviceCodeResult = null
                step = 1
                viewModel.resetMicrosoftLogin()
            }
            MicrosoftLoginState.Idle -> {}
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isAuthenticating && !isVerifying) onDismiss()
        },
        containerColor = VerseColors.PcBgCard,
        titleContentColor = VerseColors.PcTextPrimary,
        textContentColor = VerseColors.PcTextSecondary,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                when (step) {
                    0 -> "添加新账户"
                    1 -> when (selectedType) {
                        "microsoft" -> "Microsoft 登录"
                        "offline" -> "离线账户"
                        "authlib" -> "外置登录"
                        else -> "添加账户"
                    }
                    else -> "正在登录..."
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            // 整体可滚动：横屏小屏下对话框高度受限，避免方式卡片被压扁、登录表单被挤出可视区
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                when (step) {
                0 -> {
                    Column {
                        val types = listOf(
                            Triple("microsoft", "Microsoft 登录", "使用微软账户正版登录"),
                            Triple("offline", "离线账户", "无需网络，直接输入用户名"),
                            Triple("authlib", "外置登录 (Authlib)", "第三方验证服务器登录"),
                        )
                        types.forEach { (type, label, desc) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedType == type) VerseColors.PcAccent.copy(alpha = 0.08f) else VerseColors.PcBgCard,
                                border = if (selectedType == type) BorderStroke(1.dp, VerseColors.PcAccent) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { selectedType = type }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(
                                            when (type) {
                                                "microsoft" -> VerseColors.PcAccent.copy(alpha = 0.12f)
                                                "authlib" -> VerseColors.PcPurple.copy(alpha = 0.12f)
                                                else -> VerseColors.PcTextMuted.copy(alpha = 0.12f)
                                            }
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            when (type) {
                                                "microsoft" -> Icons.Filled.Email
                                                "authlib" -> Icons.Filled.Dns
                                                else -> Icons.Filled.Person
                                            },
                                            null,
                                            Modifier.size(20.dp),
                                            when (type) {
                                                "microsoft" -> VerseColors.PcAccent
                                                "authlib" -> VerseColors.PcPurple
                                                else -> VerseColors.PcTextSecondary
                                            }
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(label, fontSize = 14.sp, color = VerseColors.PcTextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text(desc, fontSize = 11.sp, color = VerseColors.PcTextSecondary)
                                    }
                                    RadioButton(
                                        selected = selectedType == type,
                                        onClick = { selectedType = type },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = VerseColors.PcAccent,
                                            unselectedColor = VerseColors.PcTextMuted
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    when (selectedType) {
                        "microsoft" -> {
                            if (deviceCodeResult == null) {
                                Column {
                                    Text(
                                        "点击「获取设备码」后，将显示一个验证码和授权网址。请在浏览器中打开该网址，输入验证码完成登录。",
                                        fontSize = 13.sp,
                                        color = VerseColors.PcTextSecondary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = VerseColors.PcOrange.copy(alpha = 0.08f)
                                    ) {
                                        Text(
                                            "确保使用已购买 Minecraft Java Edition 的微软账户登录。",
                                            fontSize = 11.sp,
                                            color = VerseColors.PcOrange,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            } else {
                                val code = deviceCodeResult!!
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = VerseColors.PcBgTertiary,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "验证码",
                                                fontSize = 11.sp,
                                                color = VerseColors.PcTextSecondary
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = VerseColors.PcBgCard,
                                                modifier = Modifier.clickable {
                                                    clipboardManager.setText(AnnotatedString(code.userCode))
                                                }
                                            ) {
                                                Text(
                                                    code.userCode,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = VerseColors.PcAccent,
                                                    letterSpacing = 4.sp,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text("点击复制验证码", fontSize = 10.sp, color = VerseColors.PcTextMuted)

                                            Spacer(Modifier.height(12.dp))

                                            Text(
                                                "请在浏览器中打开",
                                                fontSize = 11.sp,
                                                color = VerseColors.PcTextSecondary
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                code.verificationUri,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = VerseColors.PcTextPrimary
                                            )

                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    if (authStatus.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            RoseLoader(
                                                modifier = Modifier.size(14.dp),
                                                size = 14.dp,
                                                color = VerseColors.PcAccent
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                authStatus,
                                                fontSize = 12.sp,
                                                color = VerseColors.PcTextSecondary
                                            )
                                        }
                                    }

                                    if (authError != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = VerseColors.PcRed.copy(alpha = 0.08f)
                                        ) {
                                            Text(
                                                authError!!,
                                                fontSize = 11.sp,
                                                color = VerseColors.PcRed,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "offline" -> {
                            Column {
                                Text(
                                    "离线账户仅提供基础登录功能，无法进入正版服务器。",
                                    fontSize = 13.sp,
                                    color = VerseColors.PcTextSecondary
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = offlineUsername,
                                    onValueChange = { offlineUsername = it },
                                    label = { Text("用户名", fontSize = 12.sp) },
                                    placeholder = { Text("输入游戏用户名", fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = pcTextFieldColors(
                                        focusedLabelColor = VerseColors.PcAccent,
                                        unfocusedLabelColor = VerseColors.PcTextSecondary
                                    )
                                )
                            }
                        }
                        "authlib" -> {
                            Column {
                                Text(
                                    "使用第三方验证服务器登录，例如 LittleSkin、Blessing Skin 等。",
                                    fontSize = 13.sp,
                                    color = VerseColors.PcTextSecondary
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = authlibServerUrl,
                                    onValueChange = {
                                        authlibServerUrl = it
                                        serverInfo = null
                                    },
                                    label = { Text("验证服务器地址", fontSize = 12.sp) },
                                    placeholder = { Text("https://littleskin.cn/api/yggdrasil", fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = pcTextFieldColors(
                                        focusedLabelColor = VerseColors.PcAccent,
                                        unfocusedLabelColor = VerseColors.PcTextSecondary
                                    )
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PcButton(
                                        text = if (isVerifying) "验证中..." else "验证服务器",
                                        onClick = {
                                            if (authlibServerUrl.isNotBlank() && !isVerifying) {
                                                scope.launch {
                                                    isVerifying = true
                                                    try {
                                                        val info = viewModel.verifyAuthlibServer(authlibServerUrl.trim())
                                                        serverInfo = info
                                                    } catch (_: Exception) {
                                                        serverInfo = ServerInfo(success = false)
                                                    } finally {
                                                        isVerifying = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = authlibServerUrl.isNotBlank() && !isVerifying,
                                        variant = PcButtonVariant.OUTLINE
                                    )
                                }
                                serverInfo?.let { info ->
                                    Spacer(Modifier.height(6.dp))
                                    if (info.success) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = VerseColors.PcPurple.copy(alpha = 0.06f),
                                            border = BorderStroke(1.dp, VerseColors.PcPurple.copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.Verified,
                                                    null,
                                                    Modifier.size(16.dp),
                                                    VerseColors.PcPurple
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        info.serverName.ifBlank { "未知服务器" },
                                                        fontSize = 12.sp,
                                                        color = VerseColors.PcTextPrimary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    if (info.description.isNotEmpty()) {
                                                        Text(
                                                            info.description,
                                                            fontSize = 10.sp,
                                                            color = VerseColors.PcTextSecondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = VerseColors.PcRed.copy(alpha = 0.06f)
                                        ) {
                                            Text(
                                                "无法连接到服务器，请检查地址",
                                                fontSize = 11.sp,
                                                color = VerseColors.PcRed,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = authlibUsername,
                                    onValueChange = { authlibUsername = it },
                                    label = { Text("邮箱/用户名", fontSize = 12.sp) },
                                    placeholder = { Text("输入登录邮箱", fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    colors = pcTextFieldColors(
                                        focusedLabelColor = VerseColors.PcAccent,
                                        unfocusedLabelColor = VerseColors.PcTextSecondary
                                    )
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = authlibPassword,
                                    onValueChange = { authlibPassword = it },
                                    label = { Text("密码", fontSize = 12.sp) },
                                    placeholder = { Text("输入密码", fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    colors = pcTextFieldColors(
                                        focusedLabelColor = VerseColors.PcAccent,
                                        unfocusedLabelColor = VerseColors.PcTextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
                2 -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val msaState = msaLoginState
                        if (msaState is MicrosoftLoginState.Polling) {
                            Text(
                                "请在浏览器页面输入以下验证码",
                                fontSize = 12.sp,
                                color = VerseColors.PcTextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = VerseColors.PcBgTertiary
                            ) {
                                Text(
                                    msaState.deviceCode.userCode,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 3.sp,
                                    color = VerseColors.PcAccent,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "验证码已复制到剪贴板；返回浏览器页将取消登录",
                                fontSize = 10.sp,
                                color = VerseColors.PcTextMuted
                            )
                        } else {
                            RoseLoader(
                                modifier = Modifier.size(24.dp),
                                size = 24.dp,
                                color = VerseColors.PcAccent
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            authStatus.ifEmpty { "正在登录..." },
                            fontSize = 13.sp,
                            color = VerseColors.PcTextSecondary
                        )
                        if (authError != null) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VerseColors.PcRed.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    authError!!,
                                    fontSize = 11.sp,
                                    color = VerseColors.PcRed,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        },
        confirmButton = {
            when (step) {
                0 -> {
                    PcButton(
                        text = "下一步",
                        onClick = { step = 1 },
                        variant = PcButtonVariant.PRIMARY
                    )
                }
                1 -> {
                    when {
                        isAuthenticating -> {
                            PcButton(
                                text = "等待中...",
                                onClick = {},
                                enabled = false,
                                variant = PcButtonVariant.PRIMARY
                            )
                        }
                        selectedType == "microsoft" -> {
                            if (deviceCodeResult == null) {
                                PcButton(
                                    text = "获取设备码",
                                    onClick = {
                                        scope.launch {
                                            try {
                                                isAuthenticating = true
                                                authStatus = "获取设备码"
                                                authError = null
                                                VerseLogger.info("账号弹窗：点击获取设备码，开始请求", "MsaLogin")
                                                val code = viewModel.getDeviceCode()
                                                deviceCodeResult = code
                                                clipboardManager.setText(AnnotatedString(code.userCode))
                                                VerseLogger.info("账号弹窗：设备码已获取并复制到剪贴板，启动轮询 + 打开发浏览器页", "MsaLogin")
                                                // 启动后台轮询 + 认证流程，并打开内置浏览器验证页（优先预填验证码的完整链接）
                                                viewModel.startMicrosoftLogin(code)
                                                onMicrosoftWeb(code.verificationUriComplete.ifBlank { code.verificationUri }, code.userCode)
                                            } catch (e: Exception) {
                                                VerseLogger.error("账号弹窗：获取设备码异常 ${e.javaClass.simpleName}: ${e.message}", "MsaLogin")
                                                authError = e.message ?: "获取设备码失败"
                                                isAuthenticating = false
                                                step = 1
                                            }
                                        }
                                    },
                                    variant = PcButtonVariant.PRIMARY
                                )
                            } else {
                                PcButton(
                                    text = "重新获取",
                                    onClick = {
                                        deviceCodeResult = null
                                        authStatus = ""
                                        authError = null
                                        isAuthenticating = false
                                    },
                                    variant = PcButtonVariant.OUTLINE
                                )
                            }
                        }
                        selectedType == "offline" -> {
                            PcButton(
                                text = "添加",
                                onClick = {
                                    if (offlineUsername.isNotBlank()) {
                                        scope.launch {
                                            viewModel.createOfflineAccount(offlineUsername.trim())
                                        }
                                        onDismiss()
                                    }
                                },
                                enabled = offlineUsername.isNotBlank() && !isAuthenticating,
                                variant = PcButtonVariant.PRIMARY
                            )
                        }
                        selectedType == "authlib" -> {
                            PcButton(
                                text = "登录",
                                onClick = {
                                    if (authlibServerUrl.isNotBlank() && authlibUsername.isNotBlank() && authlibPassword.isNotBlank()) {
                                        scope.launch {
                                            isAuthenticating = true
                                            step = 2
                                            authStatus = "正在验证..."
                                            authError = null
                                            try {
                                                val result = viewModel.authenticateAuthlib(
                                                    authlibServerUrl.trim(),
                                                    authlibUsername.trim(),
                                                    authlibPassword
                                                )
                                                if (result.success && result.account != null) {
                                                    viewModel.addAccount(result.account!!.copy(selected = true))
                                                    onDismiss()
                                                } else if (result.profiles != null && result.profiles!!.isNotEmpty()) {
                                                    onProfilesAvailable(
                                                        authlibServerUrl.trim(),
                                                        authlibUsername.trim(),
                                                        authlibPassword,
                                                        result.profiles!!
                                                    )
                                                } else {
                                                    isAuthenticating = false
                                                    authError = result.error ?: "登录失败"
                                                    step = 1
                                                }
                                            } catch (e: Exception) {
                                                isAuthenticating = false
                                                authError = e.message ?: "登录失败"
                                                step = 1
                                            }
                                        }
                                    }
                                },
                                enabled = authlibServerUrl.isNotBlank() && authlibUsername.isNotBlank() && authlibPassword.isNotBlank() && !isAuthenticating,
                                variant = PcButtonVariant.PRIMARY
                            )
                        }
                    }
                }
                2 -> {}
            }
        },
        dismissButton = {
            PcButton(
                text = if (step == 0) "取消" else "上一步",
                onClick = {
                    if (step == 0) onDismiss()
                    else {
                        step = 0
                        deviceCodeResult = null
                        authStatus = ""
                        authError = null
                        isAuthenticating = false
                    }
                },
                enabled = !isAuthenticating && !isVerifying,
                variant = PcButtonVariant.GHOST
            )
        }
    )
}

private fun formatMicrosoftError(result: AuthResult): String {
    // 返回精确的中文错误文本，优先直接展示
    return result.error
        ?: if (result.needPurchase) {
            "该账号未购买 Minecraft，请先在微软商店购买 Minecraft Java Edition"
        } else if (result.needCreateProfile) {
            "未找到 Minecraft 档案，请先在 Minecraft 官网创建角色名"
        } else {
            "认证失败"
        }
}

@Composable
internal fun AccountCard(
    account: Account,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = VerseColors.PcAccentGlow,
                    spotColor = VerseColors.PcAccentGlow
                ) else Modifier
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, if (isSelected) VerseColors.PcAccent else VerseColors.PcBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountAvatar(
                account = account,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.username,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (account.type) {
                        AccountType.MICROSOFT -> "Microsoft"
                        AccountType.AUTHLIB -> "外置登录"
                        else -> "离线"
                    },
                    fontSize = 12.sp,
                    color = when (account.type) {
                        AccountType.MICROSOFT -> VerseColors.PcAccent
                        AccountType.AUTHLIB -> VerseColors.PcPurple
                        else -> VerseColors.PcTextMuted
                    }
                )
                if (account.uuid.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = account.uuid,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = VerseColors.PcTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = VerseColors.PcAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = VerseColors.PcTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
