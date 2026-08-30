package com.verse.explorer.app.data.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.verse.explorer.core.account.Account
import com.verse.explorer.core.account.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 头像仓库。
 * 负责解析账号的完整皮肤来源，加载为 Bitmap 并裁剪头部（含帽子层），
 * 结果按账号 id 缓存，供账号列表与详情页展示。
 */
object AvatarRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Bitmap>()

    /**
     * 根据账号解析可用于头像展示的位图。
     * - 离线/外置：优先本地皮肤文件，否则网络皮肤 URL
     * - 微软/外置网络皮肤：下载完整皮肤后裁剪头部
     * - 外置若为 mc-heads 头像服务返回的已是头部，则直接使用
     * @param outputSize 头部裁剪输出边长，默认 256（对齐 PC home-avatar `min(128, 宽)` 的高清输出，
     *   避免 64px 小图被放大后模糊）
     * @return 头像 Bitmap，失败返回 null
     */
    suspend fun getAvatar(context: Context, account: Account, outputSize: Int = 256): Bitmap? {
        val cacheKey = "${account.id}_$outputSize"
        if (account.id != 0) cache[cacheKey]?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            loadAvatar(context, account, outputSize)
        }
        if (bitmap != null && account.id != 0) {
            cache[cacheKey] = bitmap
        }
        return bitmap
    }

    private fun loadAvatar(context: Context, account: Account, outputSize: Int): Bitmap? {
        // 对齐 accounts.js：offline = (type==='offline' && !serverUrl)
        val offline = account.type == AccountType.OFFLINE && account.serverUrl.isNullOrBlank()
        val accUuid = account.uuid.replace("-", "")

        // 外置登录：serverUrl 皮肤站的头像服务通常已返回头部图，直接使用
        if (account.type == AccountType.AUTHLIB && account.loginUsername != null &&
            account.serverUrl?.isNotBlank() == true
        ) {
            try {
                val serverUrl = account.serverUrl!!.trimEnd('/')
                    .replace("/api/yggdrasil", "")
                    .replace("/api/authlib-injector", "")
                val url = "$serverUrl/avatar/$accUuid?size=$outputSize"
                downloadBitmap(url)?.let { return it }
            } catch (_: Exception) {
            }
        }

        // 优先本地皮肤文件（离线 / 外置落地 / 上传的自定义皮肤）
        val skinFile = account.skinFile
        if (!skinFile.isNullOrBlank()) {
            val file = resolveSkinFile(context, skinFile)
            if (file != null && file.exists()) {
                val skin = BitmapFactory.decodeFile(file.absolutePath)
                if (skin != null) return SkinHeadCrop.cropHead(skin, outputSize)
            }
        }

        // 网络皮肤 URL（微软正版 / 外置网络纹理）：
        // 整张皮肤才裁剪头部（对齐 PC resolveFinal isFullSkin 判定），否则原图即头像
        val skinUrl = account.skinUrl
        if (!skinUrl.isNullOrBlank()) {
            val skin = downloadBitmap(skinUrl)
            if (skin != null) {
                return if (SkinHeadCrop.isFullSkin(skin)) {
                    SkinHeadCrop.cropHead(skin, outputSize)
                } else {
                    skin
                }
            }
        }

        // 有 UUID（离线自动生成 / 正版无皮肤）→ 默认皮肤头：
        // 对齐 PC 头像接口行为（离线无 uuid / 无皮肤账号均回退 Steve 头）
        if (accUuid.isNotEmpty() || offline) {
            val defaultFile = if (account.skinModel == "slim") "alex.png" else "steve.png"
            return try {
                context.assets.open("skins/$defaultFile").use { input ->
                    val skin = BitmapFactory.decodeStream(input)
                    if (skin != null) SkinHeadCrop.cropHead(skin, outputSize) else null
                }
            } catch (_: Exception) {
                null
            }
        }

        // 非离线且无 UUID：PC 显示启动器占位图标（img/icon.png），Android 由调用方回退首字符
        return null
    }

    private fun resolveSkinFile(context: Context, skinFile: String): File? {
        if (skinFile.startsWith("custom_")) {
            return File(context.filesDir, "skins/$skinFile")
        }
        // 内置默认皮肤 asset：复制到缓存目录后返回
        return try {
            val fileName = "asset_$skinFile"
            val dest = File(context.cacheDir, "skins/$fileName")
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

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun invalidate(accountId: Int) {
        cache.keys.removeIf { it.startsWith("${accountId}_") }
    }

    fun clear() {
        cache.clear()
    }
}