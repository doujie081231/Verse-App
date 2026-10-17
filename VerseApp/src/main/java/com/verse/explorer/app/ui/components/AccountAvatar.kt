package com.verse.explorer.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.data.account.AvatarRepository
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.account.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val AVATAR_OUTPUT_SIZE = 256

/**
 * 账号头像：从完整皮肤裁剪头部（含帽子层）后展示。
 * 放大后仍保持清晰的像素块。加载失败时回退为用户名首字符。
 */
@Composable
fun AccountAvatar(
    account: Account,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp)
) {
    val context = LocalContext.current
    var bitmap by remember(account.id, account.skinFile, account.skinUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(account.id, account.skinFile, account.skinUrl) {
        bitmap = withContext(Dispatchers.IO) {
            AvatarRepository.getAvatar(context, account, AVATAR_OUTPUT_SIZE)
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            // 仅在未加载时显示灰色底图；加载完成后去掉底色，
            .then(if (bitmap == null) Modifier.background(VerseColors.PcBgActive) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = bmp.asImageBitmap(),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.None
                )
            }
        }
    }
}