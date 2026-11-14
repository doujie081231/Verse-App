package com.verse.explorer.core.data.account

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
     * 取账号的**完整皮肤**位图（64×64 / 64×32 原图，不裁头部），供账号卡片的 3D 全身预览使用。
     *
     * 与 [getAvatar] 的区别：头像是裁好的方图，全身预览需要整张皮肤图自己去摆模型。
     * 顺序与头像一致：本地皮肤文件 → 外置登录的纹理接口 → 微软账号的用户名换肤接口。
     */
    suspend fun getFullSkin(context: Context, account: Account): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "full_${account.id}_${account.skinFile}_${account.skinUrl}"
        if (account.id != 0) cache[cacheKey]?.let { return@withContext it }

        val bitmap = runCatching {
            // 1) 本地皮肤文件（离线自定义 / 外置落地 / 皮肤库应用后落盘）
            account.skinFile?.takeIf { it.isNotBlank() }?.let { skinFile ->
                resolveSkinFile(context, skinFile)?.takeIf { it.exists() }?.let { file ->
                    BitmapFactory.decodeFile(file.absolutePath)?.let { return@runCatching it }
                }
            }
            // 2) 账号记录的皮肤直链
            account.skinUrl?.takeIf { it.isNotBlank() }?.let { url ->
                downloadBitmap(url)?.let { return@runCatching it }
            }
            // 3) 外置登录：向验证服务器取当前纹理
            if (account.type == AccountType.AUTHLIB && !account.serverUrl.isNullOrBlank()) {
                val textureUrl = runCatching {
                    com.verse.explorer.core.account.AuthlibServices.authlibAuthenticator
                        .fetchSkinTextureUrl(account.serverUrl!!, account.uuid)
                }.getOrNull()
                if (!textureUrl.isNullOrBlank()) {
                    downloadBitmap(textureUrl)?.let { return@runCatching it }
                }
            }
            // 4) 微软账号：按 UUID 取原图（返回的不是完整皮肤时丢弃）
            if (account.type == AccountType.MICROSOFT && account.uuid.isNotBlank()) {
                val url = "https://mc-heads.net/skin/${account.uuid.replace("-", "")}"
                downloadBitmap(url)?.takeIf { SkinHeadCrop.isFullSkin(it) }?.let { return@runCatching it }
            }
            null
        }.getOrNull()

        if (bitmap != null && account.id != 0) cache[cacheKey] = bitmap
        bitmap
    }

    /**
     * 根据账号解析可用于头像展示的位图。
     * - 离线/外置：优先本地皮肤文件，否则网络皮肤 URL
     * - 微软/外置网络皮肤：下载完整皮肤后裁剪头部
     * - 外置若为 mc-heads 头像服务返回的已是头部，则直接使用
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

    private suspend fun loadAvatar(context: Context, account: Account, outputSize: Int): Bitmap? {
        val offline = account.type == AccountType.OFFLINE && account.serverUrl.isNullOrBlank()
        val accUuid = account.uuid.replace("-", "")

        // 外置登录：优先从验证服务器的 session profile 取真实皮肤纹理。
        // 第三方验证服务器分配的 UUID 在 mc-heads 等 Mojang 皮肤服务上查不到，
        // 只靠头像接口会一直显示默认皮肤。
        if (account.type == AccountType.AUTHLIB && account.serverUrl?.isNotBlank() == true) {
            val textureUrl = runCatching {
                com.verse.explorer.core.account.AuthlibServices.authlibAuthenticator
                    .fetchSkinTextureUrl(account.serverUrl!!, account.uuid)
            }.getOrNull()
            if (!textureUrl.isNullOrBlank()) {
                downloadBitmap(textureUrl)?.let { skin ->
                    return if (SkinHeadCrop.isFullSkin(skin)) {
                        SkinHeadCrop.cropHead(skin, outputSize)
                    } else {
                        skin
                    }
                }
            }
            // 兼容 Blessing Skin 的头像接口
            if (account.loginUsername != null) {
                try {
                    val serverUrl = account.serverUrl!!.trimEnd('/')
                        .replace("/api/yggdrasil", "")
                        .replace("/api/authlib-injector", "")
                    val url = "$serverUrl/avatar/$accUuid?size=$outputSize"
                    downloadBitmap(url)?.let { return it }
                } catch (_: Exception) {
                }
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