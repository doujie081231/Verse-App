package com.verse.explorer.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.verse.explorer.app.skin.SkinRenderer
import com.verse.explorer.app.skin.SkinViewer
import com.verse.explorer.core.account.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private val skinClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

/**
 * FCL 式 3D 人物模型查看器（对齐 FCL SkinViewer：GLSurfaceView + GL10 固定管线 MC 模型）。
 * 皮肤源解析顺序与 AvatarRepository 一致：本地皮肤文件 → 网络皮肤 URL → 默认 alex/steve。
 */
@Composable
fun FCLSkinViewer(
    account: Account,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    running: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var renderer by remember { mutableStateOf<SkinRenderer?>(null) }
    var bitmap by remember(account.id, account.skinFile, account.skinUrl) { mutableStateOf<Bitmap?>(null) }

    // 皮肤位图加载（完整皮肤，NormalizedSkin 内部判断 slim/旧格式）
    LaunchedEffect(account.id, account.skinFile, account.skinUrl) {
        bitmap = withContext(Dispatchers.IO) {
            loadFullSkin(context, account)
        }
    }

    LaunchedEffect(bitmap) {
        val bmp = bitmap
        val r = renderer
        if (bmp != null && r != null) {
            r.setTexture(bmp, null)
        }
    }

    // 非空闲动作 → 肢体摆动（对齐 FCL SkinModel.isRunning）
    LaunchedEffect(running) {
        renderer?.skinModel?.isRunning = running
    }

    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                SkinViewer(ctx).apply {
                    val r = SkinRenderer(ctx)
                    renderer = r
                    setRenderer(r, density)
                }
            },
            update = { },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 完整皮肤位图解析：本地皮肤文件 → 网络皮肤 URL → 默认 al 皮肤 */
private fun loadFullSkin(context: Context, account: Account): Bitmap? {
    // 本地皮肤文件（离线 / 外置落地 / 上传的自定义皮肤）
    val skinFile = account.skinFile
    if (!skinFile.isNullOrBlank()) {
        val file = resolveSkinFile(context, skinFile)
        if (file != null && file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
        }
    }

    // 网络皮肤 URL（微软正版 / 外置网络纹理）
    val skinUrl = account.skinUrl
    if (!skinUrl.isNullOrBlank()) {
        try {
            val request = Request.Builder().url(skinUrl).build()
            skinClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: return@use
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
                }
            }
        } catch (_: Exception) {
        }
    }

    // 默认皮肤：FCL 默认 alex，失败回退 steve
    return runCatching {
        context.assets.open("skins/alex.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull() ?: runCatching {
        context.assets.open("skins/steve.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

private fun resolveSkinFile(context: Context, skinFile: String): File? {
    if (skinFile.startsWith("custom_")) {
        return File(context.filesDir, "skins/$skinFile")
    }
    // 内置默认皮肤 asset：复制到缓存目录后返回
    return try {
        val dest = File(context.cacheDir, "skins/asset_$skinFile")
        if (!dest.exists()) {
            dest.parentFile?.mkdirs()
            context.assets.open("skins/$skinFile").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        dest
    } catch (_: Exception) {
        null
    }
}