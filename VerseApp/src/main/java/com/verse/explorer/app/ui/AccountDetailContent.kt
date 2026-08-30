package com.verse.explorer.app.ui

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.verse.explorer.app.data.account.DefaultSkinRepository
import com.verse.explorer.app.data.account.MsSkinRepository
import com.verse.explorer.app.data.account.SkinUploadRepository
import com.verse.explorer.app.ui.components.FCLSkinViewer
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.core.account.Account
import com.verse.explorer.core.account.AccountType
import kotlinx.coroutines.launch

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

    var animation by remember { mutableStateOf("idle") }
    var background by remember { mutableStateOf("white") }

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
            // 左：模型容器（对齐 PC .acct-detail-left：白/黑背景 + 右上角色块切换）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (background == "black") Color.Black else Color.White)
            ) {
                // FCL 式 3D 人物模型（GLSurfaceView）
                FCLSkinViewer(
                    account = account,
                    background = if (background == "black") Color.Black else Color.White,
                    running = animation != "idle",
                    modifier = Modifier.fillMaxSize()
                )
                // 右上角 白/黑 切换（对齐 PC .acct-bg-toggle）
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BgToggleBtn(color = Color.White, active = background == "white") { background = "white" }
                    BgToggleBtn(color = Color.Black, active = background == "black") { background = "black" }
                }
            }

            // 右：信息 / 动作 / 皮肤 / 操作（对齐 PC .acct-detail-right：固定宽滚动面板）
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // 返回 + 用户名 + 类型 badge（对齐 PC .acct-detail-header-area）
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    // badge（对齐 PC .acct-detail-badge：绿底）
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

                // 字段区（对齐 PC .acct-detail-fields）
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

                // 动作（对齐 PC .acct-detail-animations：8 个按钮）
                Text("动作", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextMuted, letterSpacing = 0.6.sp)
                Spacer(Modifier.height(8.dp))
                val animations = listOf("idle", "walk", "run", "fly", "wave", "crouch", "hit", "swim")
                animations.chunked(4).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        row.forEach { anim ->
                            val animSelected = animation == anim
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (animSelected) VerseColors.PcAccent else VerseColors.PcBgTertiary,
                                border = if (animSelected) null else BorderStroke(1.dp, VerseColors.PcBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { animation = anim }
                            ) {
                                Text(
                                    text = anim,
                                    fontSize = 11.sp,
                                    color = if (animSelected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
                                    fontWeight = if (animSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 皮肤（对齐 PC .acct-detail-skins：离线默认皮肤+自定义 / 微软本地皮肤库）
                if (account.type == AccountType.OFFLINE) {
                    Text("皮肤", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextMuted, letterSpacing = 0.6.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("默认皮肤", fontSize = 12.sp, color = VerseColors.PcTextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DefaultSkinRepository.skins.forEach { skin ->
                            val selected = account.skinFile == skin.file
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
                                        scope.launch {
                                            viewModel.updateAccountSkin(account.id, skin.file, skin.model)
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            DefaultSkinRepository.getSkinUrl(context, skin.file)
                                        ),
                                        contentDescription = skin.name,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        skin.name,
                                        fontSize = 12.sp,
                                        color = if (selected) VerseColors.PcAccent else VerseColors.PcTextSecondary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
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
                                    if (pf != null && pf.exists()) {
                                        Image(
                                            painter = rememberAsyncImagePainter("file://${pf.absolutePath}"),
                                            contentDescription = skin.name,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(VerseColors.PcBgPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                skin.name.firstOrNull()?.uppercase() ?: "?",
                                                color = VerseColors.PcTextSecondary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
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

                Spacer(Modifier.height(20.dp))

                // 操作（对齐 PC .acct-detail-actions：切换到此账户 / 刷新皮肤 / 登出）
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

/** 右栏字段（对齐 PC .acct-detail-field：uppercase 小标签 + 内容 + 底部分隔线） */
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

/** 模型容器右上角白/黑背景切换（对齐 PC .acct-bg-btn：28x28 色块，active 蓝描边） */
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