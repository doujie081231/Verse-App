package com.verse.explorer.app.data.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.verse.explorer.core.account.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 微软账户本地皮肤库。
 * 皮肤 PNG 存储在 filesDir/ms-skins/<accountId>/，元数据记录在 meta.json。
 * 支持：导入、列表、删除、应用到 Minecraft 官方（multipart 上传 Mojang）。
 */
object MsSkinRepository {

    data class MsSkin(
        val id: String,
        val name: String,
        val file: String,
        val model: String = "default",
        val importedAt: String = ""
    )

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val SKIN_UPLOAD_URL = "https://api.minecraftservices.com/minecraft/profile/skins"

    private fun skinDir(context: Context, accountId: Int): File {
        val dir = File(context.filesDir, "ms-skins/$accountId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun metaFile(context: Context, accountId: Int): File =
        File(skinDir(context, accountId), "meta.json")

    private fun loadMeta(context: Context, accountId: Int): List<MsSkin> {
        val file = metaFile(context, accountId)
        return try {
            if (file.exists()) {
                val type = object : TypeToken<List<MsSkin>>() {}.type
                gson.fromJson<List<MsSkin>>(file.readText(), type) ?: emptyList()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveMeta(context: Context, accountId: Int, skins: List<MsSkin>) {
        metaFile(context, accountId).writeText(gson.toJson(skins))
    }

    suspend fun list(context: Context, accountId: Int): List<MsSkin> = withContext(Dispatchers.IO) {
        loadMeta(context, accountId)
    }

    suspend fun delete(context: Context, accountId: Int, skinId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val skins = loadMeta(context, accountId).toMutableList()
            val idx = skins.indexOfFirst { it.id == skinId }
            if (idx == -1) return@withContext false
            val dir = skinDir(context, accountId)
            val target = File(dir, skins[idx].file)
            if (target.exists()) target.delete()
            skins.removeAt(idx)
            saveMeta(context, accountId, skins)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 导入本地 PNG 皮肤。非 64x64 或 64x32 的会按最近邻缩放到 64x64。
     */
    suspend fun import(
        context: Context,
        accountId: Int,
        uri: Uri,
        model: String,
        name: String
    ): MsSkin? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            var finalBytes = bytes
            if (!(options.outWidth == 64 && (options.outHeight == 64 || options.outHeight == 32))) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    val scaled = Bitmap.createScaledBitmap(bmp, 64, 64, true)
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                    finalBytes = out.toByteArray()
                    if (scaled != bmp) scaled.recycle()
                    bmp.recycle()
                }
            }

            val dir = skinDir(context, accountId)
            val skinId = "skin_${System.currentTimeMillis()}"
            val fileName = "$skinId.png"
            File(dir, fileName).writeBytes(finalBytes)

            val skins = loadMeta(context, accountId).toMutableList()
            val skin = MsSkin(
                id = skinId,
                name = name.ifBlank { "自定义皮肤 ${skins.size + 1}" },
                file = fileName,
                model = if (model == "slim") "slim" else "default",
                importedAt = System.currentTimeMillis().toString()
            )
            skins.add(skin)
            saveMeta(context, accountId, skins)
            skin
        } catch (_: Exception) {
            null
        }
    }

    fun getSkinFile(context: Context, accountId: Int, skinId: String): File? {
        val skin = loadMeta(context, accountId).find { it.id == skinId } ?: return null
        val file = File(skinDir(context, accountId), skin.file)
        return if (file.exists()) file else null
    }

    /**
     * 应用本地皮肤到 Minecraft 官方。
     * 对齐 VersePC：multipart 上传，字段 variant + file。
     * 返回结果，含频率限制（429）与登录过期（401）细分。
     */
    suspend fun apply(
        context: Context,
        account: Account,
        skinId: String
    ): ApplyResult = withContext(Dispatchers.IO) {
        if (account.accessToken.isBlank()) {
            return@withContext ApplyResult(success = false, needRelogin = true, error = "账户未登录，请重新登录微软账户")
        }
        val skin = loadMeta(context, account.id).find { it.id == skinId }
            ?: return@withContext ApplyResult(success = false, error = "皮肤不存在或已被删除")
        val file = File(skinDir(context, account.id), skin.file)
        if (!file.exists()) {
            return@withContext ApplyResult(success = false, error = "皮肤文件缺失")
        }

        val variant = if (skin.model == "slim") "slim" else "classic"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("variant", variant)
            .addFormDataPart("file", "skin.png",
                file.readBytes().toRequestBody("image/png".toMediaType()))
            .build()

        val request = Request.Builder()
            .url(SKIN_UPLOAD_URL)
            .header("Authorization", "Bearer ${account.accessToken}")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                when (response.code) {
                    200 -> {
                        var newSkinUrl: String? = null
                        try {
                            val json = gson.fromJson(respBody, com.google.gson.JsonObject::class.java)
                            val skins = json.getAsJsonArray("skins")
                            skins?.forEach { s ->
                                val obj = s.asJsonObject
                                if (obj.get("state")?.asString == "ACTIVE") {
                                    newSkinUrl = obj.get("url")?.asString
                                }
                            }
                        } catch (_: Exception) {}
                        ApplyResult(success = true, skinUrl = newSkinUrl)
                    }
                    401 -> ApplyResult(success = false, needRelogin = true, error = "登录已过期，请重新登录微软账户")
                    429 -> {
                        val retryAfter = response.header("Retry-After")?.toIntOrNull() ?: 60
                        ApplyResult(
                            success = false,
                            rateLimited = true,
                            retryAfter = retryAfter,
                            error = "操作过于频繁，Mojang 限制每分钟只能更换一次皮肤，请 ${formatRetry(retryAfter)}后再试"
                        )
                    }
                    else -> {
                        var errMsg = "上传失败 (HTTP ${response.code})"
                        try {
                            val json = gson.fromJson(respBody, com.google.gson.JsonObject::class.java)
                            if (json.has("errorMessage")) errMsg = json.get("errorMessage").asString
                        } catch (_: Exception) {}
                        ApplyResult(success = false, error = errMsg)
                    }
                }
            }
        } catch (e: Exception) {
            ApplyResult(success = false, error = "网络错误: ${e.message}")
        }
    }

    private fun formatRetry(seconds: Int): String {
        return if (seconds > 60) "${(seconds + 59) / 60} 分钟" else "$seconds 秒"
    }

    data class ApplyResult(
        val success: Boolean,
        val skinUrl: String? = null,
        val error: String? = null,
        val needRelogin: Boolean = false,
        val rateLimited: Boolean = false,
        val retryAfter: Int? = null
    )
}