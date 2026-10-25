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
import com.verse.explorer.core.account.AccountType
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
 * 皮肤源解析顺序与 AvatarRepository 一致：本地皮肤文件 → 网络皮肤 URL → 默认 alex/steve。
 */
@Composable
fun FCLSkinViewer(
    account: Account,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    autoRotate: Boolean = false,
    capeVersion: Int = 0
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var renderer by remember { mutableStateOf<SkinRenderer?>(null) }
    var viewer by remember { mutableStateOf<SkinViewer?>(null) }
    var bitmap by remember(account.id, account.skinFile, account.skinUrl) { mutableStateOf<Bitmap?>(null) }
    // 披风位图（本地装备披风优先，回退微软官方披风 URL）；capeVersion 由调用方在披风切换后递增
    var capeBitmap by remember(account.id, account.capeUrl, capeVersion) { mutableStateOf<Bitmap?>(null) }

    // 皮肤位图加载（完整皮肤，NormalizedSkin 内部判断 slim/旧格式）
    LaunchedEffect(account.id, account.skinFile, account.skinUrl) {
        bitmap = withContext(Dispatchers.IO) {
            loadFullSkin(context, account)
        }
    }

    LaunchedEffect(account.id, account.capeUrl, capeVersion) {
        capeBitmap = withContext(Dispatchers.IO) {
            loadCape(context, account)
        }
    }

    LaunchedEffect(bitmap, capeBitmap) {
        val bmp = bitmap
        val r = renderer
        if (bmp != null && r != null) {
            r.setTexture(bmp, capeBitmap)
            // 按需渲染模式下纹理更新后主动请求一帧
            viewer?.requestRender()
        }
    }


    LaunchedEffect(autoRotate) {
        renderer?.skinModel?.autoRotate = autoRotate
        viewer?.setAutoRotate(autoRotate)
        viewer?.requestRender()
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
                    viewer = this
                }
            },
            update = { },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 完整皮肤位图解析：外置登录真实纹理 → 本地皮肤文件 → 网络皮肤 URL → 默认 al 皮肤 */
private suspend fun loadFullSkin(context: Context, account: Account): Bitmap? {
    // 外置登录：验证服务器的 session profile 里带真实皮肤纹理，
    // 账号上记录的 skinUrl 是头像接口（只是头部裁剪图），直接当整张皮肤渲染会出现错位/默认皮肤
    if (account.type == AccountType.AUTHLIB && !account.serverUrl.isNullOrBlank()) {
        val textureUrl = runCatching {
            com.verse.explorer.core.account.AuthlibServices.authlibAuthenticator
                .fetchSkinTextureUrl(account.serverUrl!!, account.uuid)
        }.getOrNull()
        if (!textureUrl.isNullOrBlank()) {
            runCatching {
                val request = Request.Builder().url(textureUrl).build()
                skinClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes() ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                }
            }.getOrNull()?.let { return it }
        }
    }

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

    return runCatching {
        context.assets.open("skins/alex.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull() ?: runCatching {
        context.assets.open("skins/steve.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

/** 披风位图解析：本地装备披风 → 微软官方披风 URL → 无披风（null） */
private fun loadCape(context: Context, account: Account): Bitmap? {
    // 本地装备披风（披风库选中项）
    val selected = com.verse.explorer.core.data.account.CapeRepository.selected(context, account.id)
    if (selected != null && selected.file.exists()) {
        BitmapFactory.decodeFile(selected.file.absolutePath)?.let { return it }
    }
    // 微软官方披风
    val capeUrl = account.capeUrl
    if (!capeUrl.isNullOrBlank()) {
        try {
            val request = Request.Builder().url(capeUrl).build()
            skinClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: return@use
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
                }
            }
        } catch (_: Exception) {
        }
    }
    return null
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