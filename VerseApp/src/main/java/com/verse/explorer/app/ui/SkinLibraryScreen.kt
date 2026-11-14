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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import com.verse.explorer.app.skin.SkinFigureRenderer
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
/**
 * 没有外置登录账号时（离线 / 微软）皮肤库回落到官方皮肤站，
 * 配合开发者 API Key 使用——换肤对象是站内角色，与用哪个账号进游戏无关。
 */
private const val VERSE_SKIN_ORIGIN = "https://skin.verselauncher.cn"

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
    // 有外置账号就用它的站点地址；离线/微软账号没有站点地址，回落到官方皮肤站，
    // 此时靠开发者 API Key 鉴权
    val origin = remember(verseAccount?.serverUrl) {
        verseAccount?.serverUrl?.let { login.originOf(it) }?.takeIf { it.isNotBlank() }
            ?: VERSE_SKIN_ORIGIN
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
                    text = when {
                        account != null -> "将应用到 ${account!!.username} · 共 $total 款公开皮肤"
                        else -> "共 $total 款公开皮肤"
                    },
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when {
            account == null -> EmptyHint(
                title = "先添加一个账号",
                detail = "皮肤库会直接给「当前选中的账号」换肤：微软账号走官方接口、外置账号走其验证服务器、离线账号保存到本地。\n回到账户页添加账号后即可使用。"
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

    // 操作结果用小气泡（居中底部、限宽、自动消失），不要横跨整屏的横幅：
    // 之前 fillMaxWidth 会让一句"已保存为 xxx 的皮肤"铺满屏幕，看起来像弹了个大窗
    notice?.let { text ->
        LaunchedEffect(text) {
            kotlinx.coroutines.delay(2800)
            if (notice == text) notice = null
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 40.dp, vertical = 28.dp)
                    .widthIn(max = 320.dp),
                shape = RoundedCornerShape(12.dp),
                color = VerseColors.PcBgCard,
                shadowElevation = 6.dp
            ) {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = VerseColors.PcTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }

    preview?.let { skin ->
        // 尺寸对齐「版本下载/安装」弹窗：默认平台宽度 + 高度上限 480dp，不用百分比撑满全屏。
        // 模型区固定 1:1，高度受限时按高度缩（仍是正方形），不会出现被压扁的细条。
        Dialog(onDismissRequest = { if (!applying) preview = null }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = VerseColors.PcBgSecondary,
                modifier = Modifier.heightIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = skin.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerseColors.PcTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = (if (skin.isSlim) "纤细模型（Alex）" else "经典模型（Steve）") + " · 拖动查看",
                        fontSize = 11.sp,
                        color = VerseColors.PcTextMuted
                    )

                    Spacer(Modifier.height(10.dp))

                    // 正方形模型容器：宽度撑满、高度上限 300dp，两者取小保证 1:1
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VerseColors.PcBgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        com.verse.explorer.app.ui.components.SkinPreviewViewer(
                            skinUrl = skin.url,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    PcButton(
                        text = if (applying) "切换中…" else "切换到此皮肤",
                        onClick = {
                            if (applying) return@PcButton
                            val target = account
                            if (target == null) {
                                notice = "先在账户页添加一个账号"
                                return@PcButton
                            }
                            applying = true
                            scope.launch {
                                // 直接给「当前账号」换肤：微软走官方、外置走其验证服务器、离线落本地
                                com.verse.explorer.core.account.SkinApplier.apply(
                                    context = context,
                                    manager = com.verse.explorer.app.VerseApplication.accountManager,
                                    account = target,
                                    skinUrl = skin.url,
                                    model = skin.model
                                ).onSuccess { msg ->
                                    notice = "「${skin.name}」$msg"
                                    viewModel.loadAccounts()
                                }.onFailure {
                                    notice = it.message ?: "切换失败"
                                }
                                applying = false
                                preview = null
                            }
                        },
                        variant = PcButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    PcButton(
                        text = "取消",
                        onClick = { preview = null },
                        variant = PcButtonVariant.OUTLINE,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SkinCell(skin: SkinItem, onClick: () -> Unit) {
    val context = LocalContext.current
    // 卡片展示的是「穿上这套皮肤后的样子」而不是一张平铺的纹理图：
    // 复用账号详情页那套离屏渲染器（EGL Pbuffer，出 180×252 正面站姿），
    // 结果在渲染器内部按 key 缓存，翻回来不会重复渲染。
    var figure by remember(skin.tid, skin.url) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(skin.tid, skin.url) {
        figure = withContext(Dispatchers.IO) {
            val skinBitmap = runCatching {
                val request = coil.request.ImageRequest.Builder(context)
                    .data(skin.url)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                val drawable = (result as? coil.request.SuccessResult)?.drawable
                (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }.getOrNull()

            if (skinBitmap == null) {
                null
            } else {
                SkinFigureRenderer.renderFigure(skinBitmap, null, "lib_${skin.tid}")
            }
        }
    }

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
            val fig = figure
            if (fig != null) {
                Image(
                    bitmap = fig.asImageBitmap(),
                    contentDescription = skin.name,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize().padding(2.dp)
                )
            } else {
                // 模型没渲染好之前不铺原始纹理图（那是一张摊平的皮肤，和成品差别很大），
                // 只留骨架底色 + 菊花，渲染完直接出人形
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = VerseColors.PcTextMuted,
                    strokeWidth = 1.5.dp
                )
            }
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
