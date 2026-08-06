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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
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
import com.verse.explorer.app.data.account.SkinUploadRepository
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.SkinViewerWebView
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

    val skinUrl = remember(account.id, account.skinFile, account.skinUrl) {
        val sf = account.skinFile
        if (!sf.isNullOrBlank()) {
            DefaultSkinRepository.getSkinUrl(context, sf)
        } else {
            account.skinUrl
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VerseColors.PcBgPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = VerseColors.PcTextPrimary
                    )
                }
                Text(
                    "账户详情",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    PcCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SkinViewerWebView(
                                skinUrl = skinUrl,
                                model = account.skinModel,
                                animation = animation,
                                background = background,
                                nameTag = account.username,
                                modifier = Modifier.size(width = 360.dp, height = 420.dp)
                            )
                        }
                    }
                }

                item {
                    PcCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                account.username,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = VerseColors.PcTextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    account.uuid.ifBlank { "无 UUID" },
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = VerseColors.PcTextMuted,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = {
                                    if (account.uuid.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(account.uuid))
                                        Toast.makeText(context, "UUID 已复制", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = "复制 UUID",
                                        tint = VerseColors.PcTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val (typeLabel, typeColor) = when (account.type) {
                                AccountType.MICROSOFT -> "微软账户" to VerseColors.PcAccent
                                AccountType.AUTHLIB -> "外置登录" to VerseColors.PcPurple
                                AccountType.OFFLINE -> "离线账户" to VerseColors.PcTextMuted
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = typeColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    typeLabel,
                                    fontSize = 11.sp,
                                    color = typeColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    PcCard(title = "皮肤控制", modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val animations = listOf("idle", "walk", "run", "fly", "wave", "crouch", "hit", "swim")
                            animations.chunked(4).forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    row.forEach { anim ->
                                        val selected = animation == anim
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (selected) VerseColors.PcAccent else VerseColors.PcBgTertiary,
                                            border = if (selected) null else BorderStroke(1.dp, VerseColors.PcBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { animation = anim }
                                        ) {
                                            Text(
                                                anim,
                                                fontSize = 11.sp,
                                                color = if (selected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "背景",
                                    fontSize = 13.sp,
                                    color = VerseColors.PcTextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("white" to "白", "black" to "黑").forEach { (color, label) ->
                                        val selected = background == color
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (selected) VerseColors.PcAccent else VerseColors.PcBgTertiary,
                                            border = if (selected) null else BorderStroke(1.dp, VerseColors.PcBorder),
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { background = color }
                                        ) {
                                            Text(
                                                label,
                                                fontSize = 11.sp,
                                                color = if (selected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                PcButton(
                                    text = "刷新皮肤",
                                    onClick = onRefreshSkin,
                                    variant = PcButtonVariant.OUTLINE,
                                    icon = Icons.Filled.Refresh
                                )
                            }
                        }
                    }
                }

                if (account.type == AccountType.OFFLINE) {
                    item {
                        PcCard(title = "皮肤选择", modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(
                                    "默认皮肤",
                                    fontSize = 12.sp,
                                    color = VerseColors.PcTextSecondary
                                )
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
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isSelected) {
                            PcButton(
                                text = "选择为当前账户",
                                onClick = onSelect,
                                variant = PcButtonVariant.PRIMARY,
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Filled.Check
                            )
                        }
                        PcButton(
                            text = "删除账户",
                            onClick = onDelete,
                            variant = PcButtonVariant.DANGER,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Filled.Delete
                        )
                    }
                }
            }
        }
    }
}
