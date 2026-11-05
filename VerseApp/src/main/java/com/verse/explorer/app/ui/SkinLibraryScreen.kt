package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.core.account.AccountType
import com.verse.explorer.core.account.SkinItem
import com.verse.explorer.core.account.SkinLibraryApi
import com.verse.explorer.core.account.VerseQuickLogin
import kotlinx.coroutines.launch

/**
 * 皮肤库。
 *
 * 数据来自 Verse 皮肤站的公开皮肤接口（`GET /api/verse-skins`），
 * 选中后通过 `POST /api/verse-skins/apply` 挂到当前 Verse 账号的角色上——
 * 两个接口的契约对所有启动器通用，鉴权只用站点签发的 Yggdrasil accessToken。
 *
 * 只支持外置登录（Verse 皮肤站）账号：离线/微软账号没有站点侧角色，换肤没有落点。
 */
@Composable
fun SkinLibraryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel = AccountsViewModel.shared(context.applicationContext as android.app.Application)
    val account by viewModel.selectedAccount.collectAsState()
    val scope = rememberCoroutineScope()

    val api = remember { SkinLibraryApi() }
    val login = remember { VerseQuickLogin() }

    val verseAccount = account?.takeIf {
        it.type == AccountType.AUTHLIB && !it.serverUrl.isNullOrBlank()
    }
    val origin = remember(verseAccount?.serverUrl) {
        verseAccount?.serverUrl?.let { login.originOf(it) }.orEmpty()
    }

    var items by remember { mutableStateOf<List<SkinItem>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<SkinItem?>(null) }
    var applying by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun loadPage(next: Int) {
        if (origin.isBlank()) return
        loading = true
        api.list(origin, next).onSuccess { p ->
            items = if (next <= 1) p.items else items + p.items
            page = p.page
            total = p.total
            error = null
        }.onFailure {
            error = it.message ?: "加载失败"
        }
        loading = false
    }

    LaunchedEffect(origin) {
        items = emptyList()
        page = 0
        total = 0
        error = null
        if (origin.isNotBlank()) loadPage(1)
    }

    val gridState = rememberLazyGridState()
    // 接近底部时补下一页：公开皮肤可能上百条，一次拉全量既慢也给站点压力
    LaunchedEffect(gridState, page, total, items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (!loading && items.size < total && last >= items.size - 3) {
                    loadPage(page + 1)
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.PcBgPrimary)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = VerseColors.PcTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "皮肤库",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary
                )
                Text(
                    text = if (verseAccount != null) {
                        "${verseAccount.username} · 共 $total 款公开皮肤"
                    } else {
                        "需要 Verse 账号"
                    },
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when {
            verseAccount == null -> EmptyHint(
                title = "先添加 Verse 账号",
                detail = "皮肤库只对 Verse 皮肤站账号生效：离线账号与微软账号没有站点侧角色，换肤无处可落。\n回到账户页，点「添加账号」选择 Verse 账号登录即可。"
            )

            error != null && items.isEmpty() -> EmptyHint(
                title = "加载失败",
                detail = error ?: "",
                action = "重试" to { scope.launch { loadPage(1) } }
            )

            items.isEmpty() && loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = VerseColors.PcAccent,
                    strokeWidth = 2.dp
                )
            }

            items.isEmpty() -> EmptyHint(
                title = "皮肤库还是空的",
                detail = "皮肤站目前没有公开皮肤。到皮肤站「皮肤库」上传并设为公开后，这里就能看到。"
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.tid }) { skin ->
                    SkinCell(skin = skin, onClick = { preview = skin })
                }
            }
        }
    }

    notice?.let { text ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(10.dp),
            color = VerseColors.PcBgCard
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = VerseColors.PcTextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }

    preview?.let { skin ->
        Dialog(
            onDismissRequest = { if (!applying) preview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = VerseColors.PcBgSecondary,
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VerseColors.PcBgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = skin.url,
                            contentDescription = skin.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(10.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = skin.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerseColors.PcTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (skin.isSlim) "纤细模型（Alex）" else "经典模型（Steve）",
                        fontSize = 11.sp,
                        color = VerseColors.PcTextMuted
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PcButton(
                            text = if (applying) "应用中…" else "应用到我的角色",
                            onClick = {
                                if (applying || verseAccount == null) return@PcButton
                                applying = true
                                scope.launch {
                                    api.apply(
                                        origin = origin,
                                        accessToken = verseAccount.accessToken,
                                        uuid = verseAccount.uuid,
                                        tid = skin.tid
                                    ).onSuccess { player ->
                                        notice = "已把「${skin.name}」应用到角色 $player"
                                        viewModel.loadAccounts()
                                    }.onFailure {
                                        notice = it.message ?: "应用失败"
                                    }
                                    applying = false
                                    preview = null
                                }
                            },
                            variant = PcButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                        PcButton(
                            text = "取消",
                            onClick = { preview = null },
                            variant = PcButtonVariant.OUTLINE,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "换肤只作用于你自己的角色，其他玩家的皮肤不受影响",
                        fontSize = 10.sp,
                        color = VerseColors.PcTextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SkinCell(skin: SkinItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(VerseColors.PcBgCard)
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(VerseColors.PcBgTertiary),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = skin.url,
                contentDescription = skin.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = skin.name,
            fontSize = 11.sp,
            color = VerseColors.PcTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyHint(
    title: String,
    detail: String,
    action: Pair<String, () -> Unit>? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerseColors.PcTextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                fontSize = 12.sp,
                color = VerseColors.PcTextMuted,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                PcButton(
                    text = action.first,
                    onClick = action.second,
                    variant = PcButtonVariant.OUTLINE
                )
            }
        }
    }
}
