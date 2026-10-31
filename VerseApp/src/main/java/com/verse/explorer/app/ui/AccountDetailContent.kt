package com.verse.explorer.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.core.data.account.CapeRepository
import com.verse.explorer.core.data.account.DefaultSkinRepository
import com.verse.explorer.core.data.account.MsSkinRepository
import com.verse.explorer.core.data.account.SkinHeadCrop
import com.verse.explorer.core.data.account.SkinUploadRepository
import com.verse.explorer.app.skin.SkinFigureRenderer
import com.verse.explorer.app.ui.components.FCLSkinViewer
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.navButtonAvoid
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.core.account.Account
import com.verse.explorer.core.account.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AccountDetailContent(
    account: Account,
    onBack: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRefreshSkin: () -> Unit,
    viewModel: AccountsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val isSelected = account.id == selectedAccount?.id

    var background by remember { mutableStateOf("white") }
    var autoRotate by remember { mutableStateOf(false) }

    // 本地披风库状态（离线/微软账户共用；披风仅用于 3D 模型与卡片预览展示）
    var capes by remember(account.id) { mutableStateOf<List<CapeRepository.Cape>>(emptyList()) }
    var selectedCapeId by remember(account.id) { mutableStateOf("") }
    var capeVersion by remember(account.id) { mutableIntStateOf(0) }
    var capeBusy by remember { mutableStateOf(false) }

    LaunchedEffect(account.id) {
        capes = CapeRepository.list(context, account.id)
        selectedCapeId = CapeRepository.selected(context, account.id)?.id.orEmpty()
    }

    val capePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                capeBusy = true
                val capeId = CapeRepository.import(context, account.id, uri)
                if (capeId != null) {
                    CapeRepository.select(context, account.id, capeId)
                    capes = CapeRepository.list(context, account.id)
                    selectedCapeId = capeId
                    capeVersion++
                    SkinFigureRenderer.clearCache()
                    Toast.makeText(context, "披风已装备", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "披风导入失败", Toast.LENGTH_SHORT).show()
                }
                capeBusy = false
            }
        }
    }

    // 微软本地皮肤库状态
    var msSkins by remember(account.id) { mutableStateOf<List<MsSkinRepository.MsSkin>>(emptyList()) }
    var msSkinLoading by remember { mutableStateOf(false) }
    var msSkinMessage by remember { mutableStateOf<String?>(null) }
    var msApplyingSkinId by remember { mutableStateOf<String?>(null) }
    var msImportingModel by remember { mutableStateOf("default") }

    LaunchedEffect(account.id) {
        if (account.type == AccountType.MICROSOFT) {
            msSkins = MsSkinRepository.list(context, account.id)
        }
    }

    val msSkinPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                msSkinLoading = true
                msSkinMessage = null
                val skin = MsSkinRepository.import(context, account.id, uri, msImportingModel, "")
                msSkinLoading = false
                if (skin != null) {
                    msSkins = MsSkinRepository.list(context, account.id)
                    Toast.makeText(context, "已导入到本地皮肤库", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "皮肤导入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val skinPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val fileName = SkinUploadRepository.saveSkin(context, uri, account.id)
                if (fileName != null) {
                    viewModel.updateAccountSkin(account.id, fileName, "default")
                } else {
                    Toast.makeText(context, "皮肤保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val typeLabel = when (account.type) {
        AccountType.MICROSOFT -> "微软账户"
        AccountType.AUTHLIB -> "外置登录"
        AccountType.OFFLINE -> "离线账户"
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (background == "black") Color.Black else Color.White)
            ) {
                FCLSkinViewer(
                    account = account,
                    background = if (background == "black") Color.Black else Color.White,
                    autoRotate = autoRotate,
                    capeVersion = capeVersion,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BgToggleBtn(color = Color.White, active = background == "white") { background = "white" }
                    BgToggleBtn(color = Color.Black, active = background == "black") { background = "black" }
                }
            }

            Column(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.navButtonAvoid(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VerseColors.PcBgTertiary,
                        border = BorderStroke(1.dp, VerseColors.PcBorder),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack
                        )
                    ) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                modifier = Modifier.size(18.dp),
                                tint = VerseColors.PcTextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = account.username,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerseColors.PcTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFE8F5E9)) {
                        Text(
                            text = typeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                DetailField(label = "UUID") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.uuid.ifBlank { "无 UUID" },
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = VerseColors.PcTextPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                if (account.uuid.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(account.uuid))
                                    Toast.makeText(context, "UUID 已复制", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, "复制 UUID", Modifier.size(15.dp), VerseColors.PcTextSecondary)
                        }
                    }
                }
                DetailField(label = "账户类型") {
                    Text(typeLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = VerseColors.PcTextPrimary)
                }

                Spacer(Modifier.height(16.dp))

                // 自动旋转开关（模型绕 Y 轴匀速展示）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val rotateSelected = autoRotate
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (rotateSelected) VerseColors.PcAccent else VerseColors.PcBgTertiary,
                        border = if (rotateSelected) null else BorderStroke(1.dp, VerseColors.PcBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { autoRotate = !autoRotate }
                    ) {
                        Text(
                            text = "旋转",
                            fontSize = 11.sp,
                            color = if (rotateSelected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
                            fontWeight = if (rotateSelected) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.weight(3f))
                }

                Spacer(Modifier.height(16.dp))

                if (account.type == AccountType.OFFLINE) {
                    Text("皮肤", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextMuted, letterSpacing = 0.6.sp)
                    Spacer(Modifier.height(8.dp))
                    // 内置默认皮肤选择卡片已移除：离线头像跟随所选皮肤，未上传时展示默认皮肤头像
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PcButton(
                            text = "上传皮肤",
                            onClick = { skinPickerLauncher.launch("image/png") },
                            variant = PcButtonVariant.PRIMARY,
                            icon = Icons.Filled.Upload
                        )
                    }
                }

                if (account.type == AccountType.MICROSOFT) {
                    Text("皮肤", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextMuted, letterSpacing = 0.6.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "导入的皮肤保存在本地，可应用到 Minecraft 官方（每分钟限一次）",
                        fontSize = 11.sp,
                        color = VerseColors.PcTextMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("模型", fontSize = 12.sp, color = VerseColors.PcTextSecondary)
                            listOf("default" to "经典", "slim" to "纤细").forEach { (model, label) ->
                                val selected = msImportingModel == model
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) VerseColors.PcAccent else VerseColors.PcBgTertiary,
                                    border = if (selected) null else BorderStroke(1.dp, VerseColors.PcBorder),
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { msImportingModel = model }
                                ) {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        color = if (selected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                        PcButton(
                            text = "导入皮肤",
                            onClick = { msSkinPickerLauncher.launch("image/*") },
                            variant = PcButtonVariant.PRIMARY,
                            icon = Icons.Filled.Upload,
                            enabled = !msSkinLoading
                        )
                    }
                    Text(
                        "模型决定导入时默认的体型（经典/纤细），可在导入前切换",
                        fontSize = 10.sp,
                        color = VerseColors.PcTextMuted
                    )

                    if (msSkinMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VerseColors.PcAccent.copy(alpha = 0.08f)
                        ) {
                            Text(
                                msSkinMessage.orEmpty(),
                                fontSize = 12.sp,
                                color = VerseColors.PcAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (msSkins.isEmpty()) {
                        Text(
                            "本地皮肤库为空，点击上方导入皮肤",
                            fontSize = 12.sp,
                            color = VerseColors.PcTextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        msSkins.forEach { skin ->
                            val previewFile = MsSkinRepository.getSkinFile(context, account.id, skin.id)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = VerseColors.PcBgTertiary,
                                border = BorderStroke(1.dp, VerseColors.PcBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val pf = previewFile
                                    SkinFigureThumb(
                                        load = {
                                            if (pf != null && pf.exists()) {
                                                runCatching { BitmapFactory.decodeFile(pf.absolutePath) }.getOrNull()
                                            } else null
                                        },
                                        key = "ms|${account.id}|${skin.id}",
                                        fallback = skin.name,
                                        modifier = Modifier
                                            .size(width = 52.dp, height = 72.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            skin.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = VerseColors.PcTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            if (skin.model == "slim") "纤细 (slim)" else "经典 (default)",
                                            fontSize = 11.sp,
                                            color = VerseColors.PcTextMuted
                                        )
                                    }
                                    if (msApplyingSkinId == skin.id) {
                                        Text(
                                            "应用中...",
                                            fontSize = 11.sp,
                                            color = VerseColors.PcAccent,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                    } else {
                                        PcButton(
                                            text = "应用",
                                            onClick = {
                                                scope.launch {
                                                    msApplyingSkinId = skin.id
                                                    msSkinMessage = null
                                                    val result = MsSkinRepository.apply(context, account, skin.id)
                                                    msApplyingSkinId = null
                                                    if (result.success) {
                                                        if (result.skinUrl != null) {
                                                            viewModel.updateAccountSkinUrl(account.id, result.skinUrl, skin.model)
                                                        }
                                                        msSkinMessage = "已应用到 Minecraft 官方"
                                                        Toast.makeText(context, "皮肤已应用到 Minecraft 官方", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        msSkinMessage = result.error
                                                        Toast.makeText(context, result.error ?: "应用失败", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            variant = PcButtonVariant.OUTLINE,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        IconButton(onClick = {
                                            scope.launch {
                                                if (MsSkinRepository.delete(context, account.id, skin.id)) {
                                                    msSkins = MsSkinRepository.list(context, account.id)
                                                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "删除皮肤",
                                                tint = VerseColors.PcTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 披风（离线/微软账户共用）：本地披风库网格 + 导入/获取官方/卸下，
                // 卡片与选中态结构与皮肤卡一致；披风在 3D 模型上即时呈现
                if (account.type == AccountType.OFFLINE || account.type == AccountType.MICROSOFT) {
                    Text("披风", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextMuted, letterSpacing = 0.6.sp)
                    Spacer(Modifier.height(8.dp))
                    if (capes.isEmpty()) {
                        Text(
                            "暂无披风，点击下方按钮导入",
                            fontSize = 12.sp,
                            color = VerseColors.PcTextMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            capes.chunked(3).forEach { rowCapes ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowCapes.forEach { cape ->
                                        val selected = selectedCapeId == cape.id
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) VerseColors.PcAccent.copy(alpha = 0.08f) else VerseColors.PcBgTertiary,
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) VerseColors.PcAccent else VerseColors.PcBorder
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    CapeRepository.select(context, account.id, cape.id)
                                                    selectedCapeId = cape.id
                                                    capeVersion++
                                                    Toast.makeText(context, "披风已装备", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                CapeThumb(
                                                    load = { BitmapFactory.decodeFile(cape.file.absolutePath) },
                                                    key = "cape|${account.id}|${cape.id}",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 40.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    cape.id,
                                                    fontSize = 12.sp,
                                                    color = if (selected) VerseColors.PcAccent else VerseColors.PcTextSecondary,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    repeat(3 - rowCapes.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PcButton(
                            text = "导入披风",
                            onClick = { capePickerLauncher.launch("image/png") },
                            variant = PcButtonVariant.OUTLINE,
                            enabled = !capeBusy,
                            modifier = Modifier.weight(1f)
                        )
                        // 微软账户的披风来自 account.capeUrl；外置登录（皮肤站）的披风
                        // 在 yggdrasil profile 的 textures.CAPE 里，取法对齐 ZL2 的 CapeFileDownloader
                        if (account.type == AccountType.MICROSOFT || account.type == AccountType.AUTHLIB) {
                            PcButton(
                                text = "获取账户披风",
                                onClick = {
                                    scope.launch {
                                        capeBusy = true
                                        // 局部变量：account.serverUrl 跨模块无法智能转换（String? → String）
                                        val serverUrl = account.serverUrl
                                        val source = if (!account.capeUrl.isNullOrBlank()) {
                                            account
                                        } else if (account.type == AccountType.AUTHLIB &&
                                            !serverUrl.isNullOrBlank()
                                        ) {
                                            val url = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    com.verse.explorer.core.account.AuthlibServices
                                                        .authlibAuthenticator
                                                        .fetchCapeTextureUrl(serverUrl, account.uuid)
                                                }.getOrNull()
                                            }
                                            url?.let { account.copy(capeUrl = it) } ?: account
                                        } else {
                                            account
                                        }
                                        val capeId = withContext(Dispatchers.IO) {
                                            CapeRepository.fetchOfficial(context, source)
                                        }
                                        if (capeId != null) {
                                            capes = CapeRepository.list(context, account.id)
                                            selectedCapeId = capeId
                                            capeVersion++
                                            Toast.makeText(context, "已装备官方披风", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "该账户没有官方披风", Toast.LENGTH_SHORT).show()
                                        }
                                        capeBusy = false
                                    }
                                },
                                variant = PcButtonVariant.OUTLINE,
                                enabled = !capeBusy && (
                                    !account.capeUrl.isNullOrBlank() ||
                                        (account.type == AccountType.AUTHLIB &&
                                            !account.serverUrl.isNullOrBlank())
                                    ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        PcButton(
                            text = "卸下披风",
                            onClick = {
                                CapeRepository.select(context, account.id, "")
                                selectedCapeId = ""
                                capeVersion++
                                Toast.makeText(context, "披风已卸下", Toast.LENGTH_SHORT).show()
                            },
                            variant = PcButtonVariant.OUTLINE,
                            enabled = selectedCapeId.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (!isSelected) {
                    PcButton(
                        text = "切换到此账户",
                        onClick = onSelect,
                        variant = PcButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Check
                    )
                    Spacer(Modifier.height(8.dp))
                }
                PcButton(
                    text = "刷新皮肤",
                    onClick = onRefreshSkin,
                    variant = PcButtonVariant.OUTLINE,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Refresh
                )
                Spacer(Modifier.height(8.dp))
                PcButton(
                    text = "登出",
                    onClick = onDelete,
                    variant = PcButtonVariant.DANGER,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Delete
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SkinHeadThumb(
    load: () -> Bitmap?,
    fallback: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        bitmap = withContext(Dispatchers.IO) { load() }
    }
    val head = bitmap?.let {
        runCatching { SkinHeadCrop.cropHead(it, 96) }.getOrNull()
    }
    if (head != null) {
        Image(
            bitmap = head.asImageBitmap(),
            contentDescription = fallback,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(VerseColors.PcBgPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                fallback.firstOrNull()?.uppercase() ?: "?",
                color = VerseColors.PcTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 离屏 GL 渲染失败时回退为头部裁剪预览。
 */
@Composable
private fun SkinFigureThumb(
    load: () -> Bitmap?,
    key: String,
    fallback: String,
    modifier: Modifier = Modifier
) {
    var figure by remember(key) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(key) {
        figure = withContext(Dispatchers.Default) {
            val skin = load() ?: return@withContext null
            SkinFigureRenderer.renderFigure(skin, null, key)
        }
    }
    val fig = figure
    if (fig != null) {
        Image(
            bitmap = fig.asImageBitmap(),
            contentDescription = fallback,
            modifier = modifier
        )
    } else {
        SkinHeadThumb(
            load = load,
            fallback = fallback,
            modifier = modifier
        )
    }
}

@Composable
private fun CapeThumb(
    load: () -> Bitmap?,
    key: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(key) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(key) {
        bitmap = withContext(Dispatchers.IO) { load() }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(VerseColors.PcBgPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text("披风", fontSize = 10.sp, color = VerseColors.PcTextMuted)
        }
    }
}

@Composable
private fun DetailField(
    label: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            label,
            fontSize = 11.sp,
            color = VerseColors.PcTextMuted,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = VerseColors.PcBorderSubtle
        )
    }
}

@Composable
private fun BgToggleBtn(
    color: Color,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color,
        border = BorderStroke(
            2.dp,
            if (active) Color(0xFF4A9EFF) else Color(0x4D808080)
        ),
        modifier = Modifier
            .size(28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {}
}