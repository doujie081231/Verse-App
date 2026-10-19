package com.verse.explorer.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.AccountAvatar
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.app.viewmodel.MicrosoftLoginState
import com.verse.explorer.core.account.Account
import com.verse.explorer.core.account.AccountType
import com.verse.explorer.core.account.AsyncStatus
import com.verse.explorer.core.account.AuthlibProfile
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val AcctsCardWidth = 180.dp
private val AcctsCardHeight = 240.dp
private val AcctsCardGap = 24.dp

/**
 * 暗色主题卡片颜色；
 * 浅色主题回落到默认玻璃白（rgba(255,255,255,0.08/0.12) + 白边框），消除白底下的灰板块。
 */
private val AcctsCardBg: Color
    get() = if (VerseColors.isDark) Color(0x59121218) else Color(0x14FFFFFF)
private val AcctsCardBgSel: Color
    get() = if (VerseColors.isDark) Color(0x8C181820) else Color(0x1FFFFFFF)
private val AcctsCardBorder: Color
    get() = if (VerseColors.isDark) Color(0x14FFFFFF) else Color(0x28FFFFFF)
private val AcctsCardBorderSel: Color
    get() = if (VerseColors.isDark) Color(0x28FFFFFF) else Color(0x40FFFFFF)

/**
 *  - 顶部标题「账户」
 *  - 可拖动账号轮播轨道：第一张为「添加账号」卡片，之后为账号卡片；
 *    拖动切换账号，上滑删除，点击卡片进入详情。
 */
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
    var showMsaWeb by remember { mutableStateOf(false) }
    var msaWebUrl by remember { mutableStateOf("") }
    var msaUserCode by remember { mutableStateOf("") }
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
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 返回键：账户页可从主页账户卡片进入，提供显式返回入口
                if (navController.previousBackStackEntry != null) {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp).size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = VerseColors.PcTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AccountCarousel(
                        accounts = accounts,
                        selectedAccount = selectedAccount,
                        onAddAccount = {
                            // 先进入登录方式选择卡片（微软/离线/外置登录），不直接进微软流程
                            addSheetInitialType = ""
                            showAddSheet = true
                        },
                        onOpenDetail = { selectedDetailAccount = it },
                        onDelete = { showDeleteConfirm = it },
                        onSelectAccount = { id -> scope.launch { viewModel.selectAccount(id) } }
                    )
                }
                Text(
                    text = "← 左右拖动切换账号，最左侧可添加 →",
                    fontSize = 12.sp,
                    color = VerseColors.PcTextMuted,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                )
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
            onMicrosoftWeb = { url, userCode ->
                VerseLogger.info("账号页：准备打开内置浏览器验证页 url=${url.ifBlank { "<空!>" }}（长度=${url.length}）, userCode=$userCode", "MsaLogin")
                if (url.isBlank()) {
                    VerseLogger.warn("账号页：verification_uri 为空！内置浏览器页将无地址可打开，登录必然失败", "MsaLogin")
                }
                showAddSheet = false
                msaWebUrl = url
                msaUserCode = userCode
                showMsaWeb = true
                VerseLogger.info("账号页：showMsaWeb=true，开始渲染浏览器页（此日志之后若没有'浏览器页：创建 WebView'，说明 Compose 未渲染出浏览器页）", "MsaLogin")
            },
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
                                    RoseLoader(
                                        modifier = Modifier.size(16.dp),
                                        size = 16.dp,
                                        color = VerseColors.PcAccent
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

    // 观察登录状态：成功/失败时收回浏览器覆盖层
    val msaLoginState by viewModel.microsoftLoginState.collectAsState()
    LaunchedEffect(msaLoginState) {
        when (val state = msaLoginState) {
            is MicrosoftLoginState.Success -> {
                VerseLogger.info("账号页：登录成功 ${state.account.username}，收回浏览器页", "MsaLogin")
                showMsaWeb = false
                showAddSheet = false
                android.widget.Toast.makeText(
                    context,
                    "微软账号登录成功：${state.account.username}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                viewModel.resetMicrosoftLogin()
            }
            is MicrosoftLoginState.Failed -> {
                VerseLogger.warn("账号页：登录失败 -> ${state.message}", "MsaLogin")
                showMsaWeb = false
                // 重新弹出添加账户弹窗，展示失败原因（AddAccountSheet 内部已重置到选择步骤并显示错误）
                showAddSheet = true
            }
            is MicrosoftLoginState.Cancelled -> {
                VerseLogger.warn("账号页：登录已取消", "MsaLogin")
            }
            else -> {}
        }
    }

    // 轮询状态卡片文案（与 AddAccountSheet 内进度文案保持一致）
    val msaStatusText = when (val s = msaLoginState) {
        is MicrosoftLoginState.Polling -> "等待在浏览器中完成授权..."
        is MicrosoftLoginState.Authenticating -> when (s.status) {
            AsyncStatus.GETTING_ACCESS_TOKEN -> "获取访问令牌"
            AsyncStatus.GETTING_XBL_TOKEN -> "获取 XBL 令牌"
            AsyncStatus.GETTING_XSTS_TOKEN -> "获取 XSTS 令牌"
            AsyncStatus.AUTHENTICATE_MINECRAFT -> "验证 Minecraft 基本信息"
            AsyncStatus.VERIFY_GAME_OWNERSHIP -> "验证是否拥有 Minecraft"
            AsyncStatus.GETTING_PLAYER_PROFILE -> "获取玩家账户信息"
        }
        is MicrosoftLoginState.Failed -> "登录失败，请重试"
        is MicrosoftLoginState.Cancelled -> "已取消登录"
        else -> "正在连接..."
    }

    // 内置浏览器 + 一旁悬浮的轮询状态卡片：从右侧 1/3 屏幕滑入 + 轻微缩放 + 淡入
    AnimatedVisibility(
        visible = showMsaWeb,
        modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally(
            animationSpec = tween(340, easing = FastOutSlowInEasing)
        ) { it / 3 } +
            scaleIn(
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                initialScale = 0.92f
            ) +
            fadeIn(tween(220)),
        exit = slideOutHorizontally(
            animationSpec = tween(340, easing = FastOutSlowInEasing)
        ) { it / 3 } +
            scaleOut(
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                targetScale = 0.92f
            ) +
            fadeOut(tween(220))
    ) {
        androidx.activity.compose.BackHandler {
            viewModel.cancelMicrosoftLogin()
            showMsaWeb = false
        }
        MicrosoftLoginScreen(
            webUrl = msaWebUrl,
            userCode = msaUserCode,
            statusText = msaStatusText,
            onCancel = {
                viewModel.cancelMicrosoftLogin()
                showMsaWeb = false
            },
            onBack = {
                viewModel.cancelMicrosoftLogin()
                showMsaWeb = false
            }
        )
    }
}

/**
 * 账号轮播轨道。
 * 卡片顺序：[添加账号] + accounts；选中账号居中放大；左右拖动切换；上滑删除。
 */
@Composable
private fun AccountCarousel(
    accounts: List<Account>,
    selectedAccount: Account?,
    onAddAccount: () -> Unit,
    onOpenDetail: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onSelectAccount: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardWpx = with(density) { AcctsCardWidth.toPx() }
    val stepPx = with(density) { (AcctsCardWidth + AcctsCardGap).toPx() }
    val ejectThresholdPx = with(density) { 40.dp.toPx() }

    // cards：null 表示「添加账号」卡片
    val cards = remember(accounts) { listOf<Account?>(null) + accounts }
    val maxIndex = cards.size - 1

    // 选中索引初值：选中+1；无选中但有账户 -> 1；无账户 -> 0
    var currentIndex by remember(accounts, selectedAccount?.id) {
        mutableIntStateOf(
            accounts.indexOfFirst { it.id == selectedAccount?.id }
                .let { if (it >= 0) it + 1 else if (accounts.isNotEmpty()) 1 else 0 }
        )
    }

    // 轨道额外偏移：拖动手势期间为拖拽量，松手后动画吸附
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragAccumX by remember { mutableFloatStateOf(0f) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    // 上滑删除：选中卡片沿 z 轴翻飞，动画结束后弹删除确认
    var ejectIndex by remember { mutableStateOf(-1) }
    var ejectProgress by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()

    // 从 from 平滑吸附到 to（每帧回写 dragOffset 状态驱动重组）
    suspend fun animateOffsetTo(from: Float, to: Float) {
        if (from == to) {
            dragOffset = to
            return
        }
        val anim = Animatable(from)
        anim.animateTo(to, animationSpec = tween(380, easing = FastOutSlowInEasing)) {
            dragOffset = value
        }
        dragOffset = to
    }

    LaunchedEffect(ejectIndex) {
        if (ejectIndex >= 0) {
            val anim = Animatable(0f)
            anim.animateTo(1f, animationSpec = tween(420, easing = LinearEasing)) {
                ejectProgress = value
            }
            val target = cards.getOrNull(ejectIndex)
            ejectProgress = 0f
            ejectIndex = -1
            if (target != null) onDelete(target)
        }
    }

    // 拖动阻尼（超界反弹系数 0.25）
    fun dampedOffset(accum: Float): Float {
        val eff = currentIndex - accum / stepPx
        val maxF = maxIndex.toFloat()
        if (eff > maxF) {
            val excess = eff - maxF
            return (currentIndex - (maxF + excess * 0.25f)) * stepPx
        }
        if (eff < 0f) {
            val excess = -eff
            return (currentIndex + excess * 0.25f) * stepPx
        }
        return accum
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(accounts.size) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        dragAccumX = 0f
                        dragAccumY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumX += dragAmount.x
                        dragAccumY += dragAmount.y
                        dragOffset = dampedOffset(dragAccumX)
                    },
                    onDragEnd = {
                        dragging = false
                        // 上滑删除：dy<-40 且正交判定
                        if (dragAccumY < -ejectThresholdPx &&
                            abs(dragAccumY) > abs(dragAccumX) * 1.1f
                        ) {
                            if (currentIndex > 0) {
                                dragOffset = 0f
                                ejectIndex = currentIndex
                            } else {
                                scope.launch {
                                    animateOffsetTo(dragOffset, 0f)
                                }
                            }
                            return@detectDragGestures
                        }
                        // 松手吸附到最近卡片
                        val eff = (currentIndex - dragAccumX / stepPx).coerceIn(0f, maxIndex.toFloat())
                        val target = eff.roundToInt()
                        val finalDelta = (currentIndex - target) * stepPx
                        scope.launch {
                            animateOffsetTo(dragOffset, finalDelta)
                            if (target != currentIndex) {
                                currentIndex = target
                                val id = cards.getOrNull(target)
                                if (id == null) {
                                    onAddAccount()
                                } else {
                                    onSelectAccount(id.id)
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch {
                            animateOffsetTo(dragOffset, 0f)
                        }
                    }
                )
            }
    ) {
        val containerWpx = with(density) { maxWidth.toPx() }
        val cardHPx = with(density) { AcctsCardHeight.toPx() }
        val centerY = (with(density) { maxHeight.toPx() } - cardHPx) / 2f
        // 轨道左端 = 容器中心 - 卡片中心 - index*(卡宽+间距)
        val baseX = containerWpx / 2 - cardWpx / 2 - currentIndex * stepPx

        cards.forEachIndexed { i, card ->
            val isSel = i == currentIndex
            val scale by animateFloatAsState(
                targetValue = if (isSel && ejectIndex != i) 1.12f else 1f,
                animationSpec = tween(350),
                label = "cardScale$i"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isSel && ejectIndex != i) 1f else 0.55f,
                animationSpec = tween(300),
                label = "cardAlpha$i"
            )
            val x = baseX + i * stepPx + dragOffset

            Box(
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), centerY.roundToInt()) }
                    .size(AcctsCardWidth, AcctsCardHeight),
                contentAlignment = Alignment.Center
            ) {
                if (ejectIndex == i) {
                    // 上滑删除翻飞动画
                    EjectingCard(
                        modifier = Modifier.fillMaxSize(),
                        progress = ejectProgress
                    ) {
                        EjectCardContent(
                            card = card,
                            selected = true,
                            onAdd = onAddAccount,
                            onOpen = onOpenDetail
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.scaleX = scale
                                this.scaleY = scale
                                this.alpha = alpha
                            }
                    ) {
                        EjectCardContent(
                            card = card,
                            selected = isSel,
                            onAdd = onAddAccount,
                            onOpen = onOpenDetail
                        )
                    }
                }
            }
        }
    }
}

/** 统一的卡片内层：添加账号卡 或 账号卡 */
@Composable
private fun EjectCardContent(
    card: Account?,
    selected: Boolean,
    onAdd: () -> Unit,
    onOpen: (Account) -> Unit
) {
    if (card == null) {
        AddAccountCard(selected = selected, onClick = onAdd)
    } else {
        AccountCardView(account = card, selected = selected, onClick = { onOpen(card) })
    }
}

/**
 * 上滑删除的 z 轴翻飞：
 *   0%   scale(1.12) rotateX(0)   z0   y0
 *   40%  scale(1.18) rotateX(18)  z60  y-30
 *   100% scale(1.3)  rotateX(72)  z120 y-260 / opacity 0
 */
@Composable
private fun EjectingCard(
    modifier: Modifier = Modifier,
    progress: Float,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val px = { dp: Dp -> with(density) { dp.toPx() } }
    Box(
        modifier = modifier.graphicsLayer {
            val t = progress.coerceIn(0f, 1f)
            val seg1 = (t / 0.4f).coerceIn(0f, 1f)
            val seg2 = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
            val s1 = 1.12f + (1.18f - 1.12f) * seg1
            val s2 = 1.18f + (1.3f - 1.18f) * seg2
            val rx1 = 0f + 18f * seg1
            val rx2 = 18f + (72f - 18f) * seg2
            val y1 = 0f + (px(-30.dp)) * seg1
            val y2 = px(-30.dp) + (px(-260.dp) - px(-30.dp)) * seg2
            // 此版本 Compose 无 translationZ，用加大 cameraDistance 的近景透视近似 translateZ 外翻感
            cameraDistance = 180f * density.density
            scaleX = if (t <= 0.4f) s1 else s2
            scaleY = if (t <= 0.4f) s1 else s2
            rotationX = if (t <= 0.4f) rx1 else rx2
            translationY = if (t <= 0.4f) y1 else y2
            alpha = 1f - t.coerceIn(0f, 1f)
        }
    ) {
        content()
    }
}

/**
 * 添加账号卡片：
 * 虚线 2.5px + 十字 + 「添加账号」 + 「微软 / 离线 / 外置登录」
 */
@Composable
private fun AddAccountCard(
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) VerseColors.PcAccent.copy(alpha = 0.08f) else Color(0x0AFFFFFF)
    val borderColor = if (selected) VerseColors.PcAccent else VerseColors.PcBorder
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .drawBehind {
                val strokeWidth = with(density) { 2.5.dp.toPx() }
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = if (selected) VerseColors.PcAccent else VerseColors.PcTextMuted,
                modifier = Modifier.size(60.dp)
            )
            Text(
                text = "添加账号",
                fontSize = if (selected) 22.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) VerseColors.PcAccent else VerseColors.PcTextMuted
            )
            Text(
                text = "微软 / 离线 / 外置登录",
                fontSize = 12.sp,
                color = VerseColors.PcTextMuted,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 账号卡片：
 * 头像(110dp/26%圆角) + 用户名 + 选中时显示类型胶囊
 */
@Composable
private fun AccountCardView(
    account: Account,
    selected: Boolean,
    onClick: () -> Unit
) {
    val typeLabel = when (account.type) {
        AccountType.MICROSOFT -> "微软账户"
        AccountType.AUTHLIB -> "外置登录"
        AccountType.OFFLINE -> "离线账户"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) AcctsCardBgSel else AcctsCardBg)
            .border(
                BorderStroke(1.dp, if (selected) AcctsCardBorderSel else AcctsCardBorder),
                RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(28.dp)),  // 无底色：头像透明边缘透出卡片背景
                contentAlignment = Alignment.Center
            ) {
                AccountAvatar(
                    account = account,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(28.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = account.username,
                    fontSize = if (selected) 20.sp else 16.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = VerseColors.PcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selected) {
                    // accent 渐变胶囊（暗色下白底黑字，与启动按钮一致）
                    Box(
                        modifier = Modifier
                            .shadow(12.dp, RoundedCornerShape(999.dp), spotColor = VerseColors.PcAccent.copy(alpha = 0.35f))
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(VerseColors.PcAccent, VerseColors.PcAccent)
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerseColors.PcAccentText
                        )
                    }
                }
            }
        }
    }
}