package com.verse.explorer.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.account.Account
import com.verse.explorer.core.account.AccountType
import com.verse.explorer.core.account.DeviceCodeResponse
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    navController: NavHostController
) {
    val app = VerseApplication.instance
    val accountManager = VerseApplication.accountManager
    val msAuth = VerseApplication.microsoftAuthenticator
    val authlibAuth = VerseApplication.authlibAuthenticator

    val accounts by accountManager.accounts.collectAsState()
    val selectedAccount by accountManager.selectedAccount.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Account?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        accountManager.loadAccounts()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.McBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
        ) {
            PageHeader(
                title = "账户管理",
                subtitle = "管理游戏账户和皮肤"
            )

            Spacer(modifier = Modifier.height(16.dp))
            // Profile header
            if (selectedAccount != null) {
                val acc = selectedAccount!!
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = VerseColors.McCardBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(
                                Brush.linearGradient(
                                    when (acc.type) {
                                        AccountType.MICROSOFT -> listOf(VerseColors.McGreen, VerseColors.McWater)
                                        AccountType.AUTHLIB -> listOf(VerseColors.McWater, VerseColors.McGold)
                                        else -> listOf(VerseColors.McStone, VerseColors.McTextMuted)
                                    }
                                )
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!acc.skinUrl.isNullOrBlank()) {
                                AsyncImage(model = acc.skinUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            } else {
                                Text(acc.username.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(acc.username, color = VerseColors.McTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                when (acc.type) {
                                    AccountType.MICROSOFT -> "Microsoft 账户"
                                    AccountType.AUTHLIB -> "外置登录 · ${acc.serverUrl?.let { it.take(24) } ?: ""}"
                                    else -> "离线模式"
                                },
                                color = VerseColors.McTextSecondary, fontSize = 11.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.McGreen.copy(alpha = 0.1f)) {
                                    Text("当前账户", fontSize = 10.sp, color = VerseColors.McGreen, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.McCardBgLight) {
                                    Text("${accounts.size} 个账户", fontSize = 10.sp, color = VerseColors.McTextMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), VerseColors.McTextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "共 ${accounts.size} 个账户",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McTextMuted,
                    fontSize = 12.sp
                )
                FilledTonalButton(
                    onClick = { showAddSheet = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = VerseColors.McGreen.copy(alpha = 0.15f),
                        contentColor = VerseColors.McGreen
                    )
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加账户", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.PersonOff,
                            null,
                            Modifier.size(48.dp),
                            VerseColors.McTextMuted
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "还没有添加任何账户",
                            color = VerseColors.McTextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "点击上方按钮添加一个账户",
                            color = VerseColors.McTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            isSelected = account.id == selectedAccount?.id,
                            onSelect = {
                                scope.launch {
                                    accountManager.selectAccount(account.id)
                                }
                            },
                            onDelete = {
                                showDeleteConfirm = account
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        val account = showDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = VerseColors.McOverlayContentBg,
            titleContentColor = VerseColors.McTextPrimary,
            textContentColor = VerseColors.McTextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = { Text("删除账户", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除账户「${account.username}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            accountManager.removeAccount(account.id)
                        }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = VerseColors.McRedstone)
                ) {
                    Text("删除", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消", color = VerseColors.McTextSecondary)
                }
            }
        )
    }

    if (showAddSheet) {
        AddAccountSheet(
            accountManager = accountManager,
            msAuth = msAuth,
            authlibAuth = authlibAuth,
            onDismiss = { showAddSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(
    accountManager: com.verse.explorer.core.account.AccountManager,
    msAuth: com.verse.explorer.core.account.MicrosoftAuthenticator,
    authlibAuth: com.verse.explorer.core.account.AuthlibAuthenticator,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedType by remember { mutableStateOf("microsoft") }

    var offlineUsername by remember { mutableStateOf("") }
    var authlibServerUrl by remember { mutableStateOf("") }
    var authlibUsername by remember { mutableStateOf("") }
    var authlibPassword by remember { mutableStateOf("") }

    var deviceCodeResult by remember { mutableStateOf<DeviceCodeResponse?>(null) }
    var authStatus by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!isAuthenticating) onDismiss()
        },
        containerColor = VerseColors.McOverlayContentBg,
        titleContentColor = VerseColors.McTextPrimary,
        textContentColor = VerseColors.McTextSecondary,
        shape = RoundedCornerShape(16.dp),
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
                                color = if (selectedType == type) VerseColors.McGreen.copy(alpha = 0.08f) else VerseColors.McCardBg,
                                border = if (selectedType == type) androidx.compose.foundation.BorderStroke(1.dp, VerseColors.McGreen.copy(alpha = 0.3f)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { selectedType = type }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(
                                            when (type) {
                                                "microsoft" -> VerseColors.McGreen.copy(alpha = 0.12f)
                                                "authlib" -> VerseColors.McWater.copy(alpha = 0.12f)
                                                else -> VerseColors.McTextDim.copy(alpha = 0.12f)
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
                                                "microsoft" -> VerseColors.McGreen
                                                "authlib" -> VerseColors.McWater
                                                else -> VerseColors.McTextSecondary
                                            }
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(label, fontSize = 14.sp, color = VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text(desc, fontSize = 11.sp, color = VerseColors.McTextSecondary)
                                    }
                                    RadioButton(
                                        selected = selectedType == type,
                                        onClick = { selectedType = type },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = VerseColors.McGreen,
                                            unselectedColor = VerseColors.McTextMuted
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
                                        color = VerseColors.McTextSecondary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = VerseColors.McGold.copy(alpha = 0.08f)
                                    ) {
                                        Text(
                                            "确保使用已购买 Minecraft Java Edition 的微软账户登录。",
                                            fontSize = 11.sp,
                                            color = VerseColors.McGold,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            } else {
                                val code = deviceCodeResult!!
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = VerseColors.McGreen.copy(alpha = 0.06f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                "验证码",
                                                fontSize = 11.sp,
                                                color = VerseColors.McTextSecondary
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = VerseColors.McCardBg,
                                                modifier = Modifier.clickable {
                                                    clipboardManager.setText(AnnotatedString(code.userCode))
                                                }
                                            ) {
                                                Text(
                                                    code.userCode,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = VerseColors.McGreen,
                                                    letterSpacing = 4.sp,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text("点击复制验证码", fontSize = 10.sp, color = VerseColors.McTextMuted)

                                            Spacer(Modifier.height(12.dp))

                                            Text(
                                                "请在浏览器中打开",
                                                fontSize = 11.sp,
                                                color = VerseColors.McTextSecondary
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                code.verificationUri,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = VerseColors.McTextPrimary
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
                                            CircularProgressIndicator(
                                                Modifier.size(14.dp),
                                                VerseColors.McGreen,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                authStatus,
                                                fontSize = 12.sp,
                                                color = VerseColors.McTextSecondary
                                            )
                                        }
                                    }

                                    if (authError != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = VerseColors.McRedstone.copy(alpha = 0.08f)
                                        ) {
                                            Text(
                                                authError!!,
                                                fontSize = 11.sp,
                                                color = VerseColors.McRedstone,
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
                                    color = VerseColors.McTextSecondary
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
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = VerseColors.McGreen.copy(alpha = 0.4f),
                                        unfocusedBorderColor = VerseColors.McCardBorder,
                                        focusedContainerColor = VerseColors.McCardBg,
                                        unfocusedContainerColor = VerseColors.McCardBg,
                                        focusedLabelColor = VerseColors.McGreen,
                                        unfocusedLabelColor = VerseColors.McTextSecondary,
                                        focusedTextColor = VerseColors.McTextPrimary,
                                        unfocusedTextColor = VerseColors.McTextPrimary,
                                        cursorColor = VerseColors.McGreen
                                    )
                                )
                            }
                        }
                        "authlib" -> {
                            Column {
                                Text(
                                    "使用第三方验证服务器登录，例如 LittleSkin、Blessing Skin 等。",
                                    fontSize = 13.sp,
                                    color = VerseColors.McTextSecondary
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = authlibServerUrl,
                                    onValueChange = { authlibServerUrl = it },
                                    label = { Text("验证服务器地址", fontSize = 12.sp) },
                                    placeholder = { Text("https://littleskin.cn/api/yggdrasil", fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = VerseColors.McGreen.copy(alpha = 0.4f),
                                        unfocusedBorderColor = VerseColors.McCardBorder,
                                        focusedContainerColor = VerseColors.McCardBg,
                                        unfocusedContainerColor = VerseColors.McCardBg,
                                        focusedLabelColor = VerseColors.McGreen,
                                        unfocusedLabelColor = VerseColors.McTextSecondary,
                                        focusedTextColor = VerseColors.McTextPrimary,
                                        unfocusedTextColor = VerseColors.McTextPrimary,
                                        cursorColor = VerseColors.McGreen
                                    )
                                )
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
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = VerseColors.McGreen.copy(alpha = 0.4f),
                                        unfocusedBorderColor = VerseColors.McCardBorder,
                                        focusedContainerColor = VerseColors.McCardBg,
                                        unfocusedContainerColor = VerseColors.McCardBg,
                                        focusedLabelColor = VerseColors.McGreen,
                                        unfocusedLabelColor = VerseColors.McTextSecondary,
                                        focusedTextColor = VerseColors.McTextPrimary,
                                        unfocusedTextColor = VerseColors.McTextPrimary,
                                        cursorColor = VerseColors.McGreen
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
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = VerseColors.McGreen.copy(alpha = 0.4f),
                                        unfocusedBorderColor = VerseColors.McCardBorder,
                                        focusedContainerColor = VerseColors.McCardBg,
                                        unfocusedContainerColor = VerseColors.McCardBg,
                                        focusedLabelColor = VerseColors.McGreen,
                                        unfocusedLabelColor = VerseColors.McTextSecondary,
                                        focusedTextColor = VerseColors.McTextPrimary,
                                        unfocusedTextColor = VerseColors.McTextPrimary,
                                        cursorColor = VerseColors.McGreen
                                    )
                                )
                            }
                        }
                    }
                }
                2 -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp),
                            VerseColors.McGreen,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            authStatus.ifEmpty { "正在登录..." },
                            fontSize = 13.sp,
                            color = VerseColors.McTextSecondary
                        )
                        if (authError != null) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VerseColors.McRedstone.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    authError!!,
                                    fontSize = 11.sp,
                                    color = VerseColors.McRedstone,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> {
                    Button(
                        onClick = { step = 1 },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McButtonBg)
                    ) {
                        Text("下一步", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                1 -> {
                    when {
                        isAuthenticating -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McButtonBg)
                            ) {
                                CircularProgressIndicator(Modifier.size(14.dp), Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("等待中...", fontSize = 13.sp)
                            }
                        }
                        selectedType == "microsoft" -> {
                            if (deviceCodeResult == null) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                isAuthenticating = true
                                                authStatus = "正在获取设备码..."
                                                authError = null
                                                deviceCodeResult = msAuth.getDeviceCode()
                                                authStatus = "请在浏览器中输入验证码: ${deviceCodeResult!!.userCode}"
                                                step = 2
                                                val token = msAuth.awaitDeviceAuth(
                                                    deviceCodeResult!!.deviceCode,
                                                    deviceCodeResult!!.interval
                                                )
                                                authStatus = "正在验证 Minecraft 身份..."
                                                val result = msAuth.authenticate(token.accessToken)
                                                if (result.success && result.account != null) {
                                                    accountManager.addAccount(result.account!!.copy(selected = true))
                                                    onDismiss()
                                                } else {
                                                    authError = result.error ?: "认证失败"
                                                    isAuthenticating = false
                                                    step = 1
                                                }
                                            } catch (e: Exception) {
                                                authError = e.message ?: "认证失败"
                                                isAuthenticating = false
                                                step = 1
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McButtonBg)
                                ) {
                                    Text("获取设备码", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            deviceCodeResult = null
                                            authStatus = ""
                                            authError = null
                                            isAuthenticating = false
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("重新获取", fontSize = 13.sp, color = VerseColors.McTextSecondary)
                                    }
                                }
                            }
                        }
                        selectedType == "offline" -> {
                            Button(
                                onClick = {
                                    if (offlineUsername.isNotBlank()) {
                                        scope.launch {
                                            accountManager.createOfflineAccount(offlineUsername.trim())
                                        }
                                        onDismiss()
                                    }
                                },
                                enabled = offlineUsername.isNotBlank() && !isAuthenticating,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VerseColors.McButtonBg,
                                    disabledContainerColor = VerseColors.McCardBg,
                                    disabledContentColor = VerseColors.McTextMuted
                                )
                            ) {
                                Text("添加", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        selectedType == "authlib" -> {
                            Button(
                                onClick = {
                                    if (authlibServerUrl.isNotBlank() && authlibUsername.isNotBlank() && authlibPassword.isNotBlank()) {
                                        scope.launch {
                                            isAuthenticating = true
                                            step = 2
                                            authStatus = "正在验证..."
                                            authError = null
                                            try {
                                                val result = authlibAuth.authenticate(
                                                    authlibServerUrl.trim(),
                                                    authlibUsername.trim(),
                                                    authlibPassword
                                                )
                                                if (result.success && result.account != null) {
                                                    accountManager.addAccount(result.account!!.copy(selected = true))
                                                    onDismiss()
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
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VerseColors.McButtonBg,
                                    disabledContainerColor = VerseColors.McCardBg,
                                    disabledContentColor = VerseColors.McTextMuted
                                )
                            ) {
                                Text("登录", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                2 -> {}
            }
        },
        dismissButton = {
            TextButton(
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
                enabled = !isAuthenticating
            ) {
                Text(
                    if (step == 0) "取消" else "上一步",
                    color = VerseColors.McTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    )
}

@Composable
private fun AccountCard(
    account: Account,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isSelected) onSelect() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            when (account.type) {
                                AccountType.MICROSOFT -> listOf(VerseColors.McGreen, VerseColors.McGreenLight)
                                AccountType.AUTHLIB -> listOf(VerseColors.McWater, VerseColors.McWater.copy(alpha = 0.7f))
                                else -> listOf(VerseColors.McTextSecondary, VerseColors.McTextMuted)
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!account.skinUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = account.skinUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (account.type == AccountType.OFFLINE) {
                    Text(
                        text = account.username.first().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                } else {
                    Icon(
                        Icons.Filled.Person,
                        null,
                        Modifier.size(22.dp),
                        Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = account.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VerseColors.McTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.McGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.McGreen,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (account.type) {
                        AccountType.MICROSOFT -> "Microsoft 账户"
                        AccountType.AUTHLIB -> "外置登录 · ${account.serverUrl?.let { it.take(30) + if (it.length > 30) "..." else "" } ?: ""}"
                        else -> "离线账户"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McTextSecondary,
                    fontSize = 11.sp
                )
                if (account.uuid.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = account.uuid.take(16),
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McTextMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!isSelected) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = VerseColors.McTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
