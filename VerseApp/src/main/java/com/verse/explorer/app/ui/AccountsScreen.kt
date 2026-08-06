package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.core.account.Account
import com.verse.explorer.core.account.AuthlibProfile
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel = remember { AccountsViewModel(context.applicationContext as android.app.Application) }
    val accounts by viewModel.accounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedDetailAccount by remember { mutableStateOf<Account?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var addSheetInitialType by remember { mutableStateOf("microsoft") }
    var showDeleteConfirm by remember { mutableStateOf<Account?>(null) }
    var profileSelectData by remember { mutableStateOf<List<AuthlibProfile>?>(null) }
    var pendingAuthlibUrl by remember { mutableStateOf("") }
    var pendingAuthlibUser by remember { mutableStateOf("") }
    var pendingAuthlibPass by remember { mutableStateOf("") }
    var isProfileAuthenticating by remember { mutableStateOf(false) }
    var profileSelectError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadAccounts() }

    val detailAccount = selectedDetailAccount
    if (detailAccount != null) {
        AccountDetailContent(
            account = detailAccount,
            onBack = { selectedDetailAccount = null },
            onSelect = {
                scope.launch {
                    viewModel.selectAccount(detailAccount.id)
                    selectedDetailAccount = null
                }
            },
            onDelete = { showDeleteConfirm = detailAccount },
            onRefreshSkin = {
                scope.launch {
                    viewModel.loadAccounts()
                    viewModel.accounts.value.find { it.id == detailAccount.id }?.let {
                        selectedDetailAccount = it
                    }
                }
            },
            viewModel = viewModel
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VerseColors.PcBgPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            ) {
                PageHeader(
                    title = "账户管理",
                    actions = {
                        PcButton(
                            text = "微软登录",
                            onClick = {
                                addSheetInitialType = "microsoft"
                                showAddSheet = true
                            },
                            variant = PcButtonVariant.PRIMARY
                        )
                        PcButton(
                            text = "外置登录",
                            onClick = {
                                addSheetInitialType = "authlib"
                                showAddSheet = true
                            },
                            variant = PcButtonVariant.SECONDARY
                        )
                        PcButton(
                            text = "离线账户",
                            onClick = {
                                addSheetInitialType = "offline"
                                showAddSheet = true
                            },
                            variant = PcButtonVariant.OUTLINE
                        )
                    }
                )

                Text(
                    text = "共 ${accounts.size} 个账户",
                    fontSize = 12.sp,
                    color = VerseColors.PcTextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (accounts.isEmpty()) {
                    PcCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.PersonOff,
                                null,
                                Modifier.size(48.dp),
                                VerseColors.PcTextMuted
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "暂无账户，请点击上方按钮添加",
                                color = VerseColors.PcTextMuted,
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
                                onClick = { selectedDetailAccount = account },
                                onDelete = { showDeleteConfirm = account }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm != null) {
        val account = showDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = VerseColors.PcBgCard,
            titleContentColor = VerseColors.PcTextPrimary,
            textContentColor = VerseColors.PcTextSecondary,
            shape = RoundedCornerShape(12.dp),
            title = { Text("删除账户", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除账户「${account.username}」吗？此操作不可撤销。") },
            confirmButton = {
                PcButton(
                    text = "删除",
                    onClick = {
                        scope.launch {
                            viewModel.removeAccount(account.id)
                        }
                        showDeleteConfirm = null
                        if (selectedDetailAccount?.id == account.id) {
                            selectedDetailAccount = null
                        }
                    },
                    variant = PcButtonVariant.DANGER
                )
            },
            dismissButton = {
                PcButton(
                    text = "取消",
                    onClick = { showDeleteConfirm = null },
                    variant = PcButtonVariant.GHOST
                )
            }
        )
    }

    if (showAddSheet) {
        AddAccountSheet(
            viewModel = viewModel,
            initialType = addSheetInitialType,
            onDismiss = { showAddSheet = false },
            onProfilesAvailable = { url, user, pass, profiles ->
                pendingAuthlibUrl = url
                pendingAuthlibUser = user
                pendingAuthlibPass = pass
                profileSelectData = profiles
                showAddSheet = false
            }
        )
    }

    profileSelectData?.let { profiles ->
        AlertDialog(
            onDismissRequest = {
                if (!isProfileAuthenticating) {
                    profileSelectData = null
                    profileSelectError = null
                }
            },
            containerColor = VerseColors.PcBgCard,
            titleContentColor = VerseColors.PcTextPrimary,
            textContentColor = VerseColors.PcTextSecondary,
            shape = RoundedCornerShape(12.dp),
            title = { Text("选择角色", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "该账号有多个角色，请选择一个继续登录。",
                        fontSize = 12.sp,
                        color = VerseColors.PcTextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    profiles.forEach { profile ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VerseColors.PcBgTertiary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    if (!isProfileAuthenticating) {
                                        isProfileAuthenticating = true
                                        profileSelectError = null
                                        scope.launch {
                                            try {
                                                val result = viewModel.authenticateAuthlibWithProfile(
                                                    pendingAuthlibUrl.trim(),
                                                    pendingAuthlibUser.trim(),
                                                    pendingAuthlibPass,
                                                    profile.id
                                                )
                                                if (result.success && result.account != null) {
                                                    viewModel.addAccount(result.account!!.copy(selected = true))
                                                    profileSelectData = null
                                                } else {
                                                    profileSelectError = result.error ?: "登录失败"
                                                }
                                            } catch (e: Exception) {
                                                profileSelectError = e.message ?: "登录失败"
                                            } finally {
                                                isProfileAuthenticating = false
                                            }
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(VerseColors.PcPurple.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        profile.name.first().uppercase(),
                                        color = VerseColors.PcPurple,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        profile.name,
                                        fontSize = 14.sp,
                                        color = VerseColors.PcTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        profile.id,
                                        fontSize = 11.sp,
                                        color = VerseColors.PcTextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isProfileAuthenticating) {
                                    CircularProgressIndicator(
                                        Modifier.size(16.dp),
                                        VerseColors.PcAccent,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        null,
                                        Modifier.size(18.dp),
                                        VerseColors.PcTextMuted
                                    )
                                }
                            }
                        }
                    }
                    if (profileSelectError != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VerseColors.PcRed.copy(alpha = 0.08f)
                        ) {
                            Text(
                                profileSelectError!!,
                                fontSize = 11.sp,
                                color = VerseColors.PcRed,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                PcButton(
                    text = "取消",
                    onClick = {
                        profileSelectData = null
                        profileSelectError = null
                    },
                    enabled = !isProfileAuthenticating,
                    variant = PcButtonVariant.GHOST
                )
            }
        )
    }
}
