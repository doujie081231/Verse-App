package com.verse.explorer.core.account

import android.content.Context
import com.google.gson.JsonObject
import com.verse.explorer.core.data.account.AvatarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 把皮肤库里的皮肤应用到「当前账号」。
 *
 * 三类账号走三条完全不同的路，**都不需要玩家申请任何 API Key**：
 *  - 微软：Mojang 官方换肤接口，用账号自己的 Minecraft 令牌，客户端直连；
 *  - 外置（第三方验证服务器）：该服务器的 Yggdrasil 换肤接口，用账号自己的 accessToken；
 *  - 离线：没有服务端身份，只能把皮肤落到本地文件，供启动器内预览与支持本地皮肤的客户端使用。
 *
 * 皮肤站签发的开发者 API Key 与这里无关 —— 那只是给「接接口的第三方工具」做统计用的。
 */
object SkinApplier {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val PNG = "image/png".toMediaType()

    /** 统一入口；成功时返回给用户看的说明 */
    suspend fun apply(
        context: Context,
        manager: AccountManager,
        account: Account,
        skinUrl: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(skinUrl.isNotBlank()) { "皮肤地址为空" }
            val variant = if (model.equals("slim", true) || model.equals("alex", true)) "slim" else "classic"
            val message = when (account.type) {
                AccountType.MICROSOFT -> {
                    uploadToMojang(account, skinUrl, variant)
                    "已更新 ${account.username} 的官方皮肤，重新进入服务器后生效"
                }

                AccountType.AUTHLIB -> {
                    uploadToAuthlib(account, skinUrl, variant)
                    manager.updateAccountSkinUrl(account.id, skinUrl, variant)
                    "已更新 ${account.username} 在 ${serverHost(account)} 的皮肤，重新进入服务器后生效"
                }

                AccountType.OFFLINE -> {
                    val fileName = saveLocal(context, account, skinUrl)
                    android.util.Log.i("SkinApplier", "offline apply: id=${account.id} file=$fileName")
                    manager.updateAccountSkin(account.id, fileName, variant)
                    // 回读确认真的写进去了：id 为 0（未落库）或写库异常时会静默失败，
                    // 那样界面照样提示"已保存"，用户看到的就是"提示成功但没切换"
                    val written = manager.accounts.value.firstOrNull { it.id == account.id }?.skinFile
                    if (written != fileName) {
                        error("皮肤写入失败：账号记录没有更新（id=${account.id}）。请退出账户页重进后再试")
                    }
                    "已保存为 ${account.username} 的本地皮肤：启动器内立即生效，进游戏后客户端会加载这张（联机时其他玩家看到的仍由服务器决定）"
                }
            }

            // 头像/模型是按账号 id 缓存的，换完肤必须让缓存失效，
            // 否则卡片、详情页、皮肤库都还显示旧皮肤（用户会以为"没切换成功"）
            AvatarRepository.invalidate(account.id)
            message
        }
    }

    // ---------- 微软：Mojang 官方 ----------

    private fun uploadToMojang(account: Account, skinUrl: String, variant: String) {
        require(account.accessToken.isNotBlank()) { "微软账号没有可用令牌，请到账户页重新登录" }
        val png = download(skinUrl) ?: error("皮肤图片下载失败，请稍后重试")

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("variant", variant)
            .addFormDataPart("file", "skin.png", png.toRequestBody(PNG))
            .build()

        val request = Request.Builder()
            .url("https://api.minecraftservices.com/minecraft/profile/skins")
            .header("Authorization", "Bearer ${account.accessToken}")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) return
            val text = resp.body?.string().orEmpty()
            error(
                when (resp.code) {
                    401 -> "微软登录已过期，请在账户页重新登录后再试"
                    403 -> "该微软账号没有 Minecraft 所有权，无法换肤"
                    429 -> "请求过于频繁，请稍后再试"
                    else -> "官方接口返回 ${resp.code}：${text.take(120)}"
                }
            )
        }
    }

    // ---------- 外置：账号所在验证服务器 ----------

    private fun uploadToAuthlib(account: Account, skinUrl: String, variant: String) {
        val root = account.serverUrl?.trim()?.trimEnd('/')
        require(!root.isNullOrBlank()) { "账号缺少验证服务器地址，无法换肤" }
        require(account.accessToken.isNotBlank()) { "外置账号没有可用令牌，请重新登录" }

        val payload = JsonObject().apply {
            addProperty("variant", variant)
            addProperty("url", skinUrl)
        }

        val request = Request.Builder()
            .url("$root/minecraft/profile/skins")
            .header("Authorization", "Bearer ${account.accessToken}")
            .put(payload.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) return
            val text = resp.body?.string().orEmpty()
            error(
                when (resp.code) {
                    401 -> "该验证服务器的登录已过期，请重新登录账号"
                    403 -> "服务器拒绝换肤：可能未开放上传，或角色不在该账号下"
                    404 -> "该验证服务器不支持换肤接口"
                    else -> "服务器返回 ${resp.code}：${text.take(120)}"
                }
            )
        }
    }

    // ---------- 离线：落本地 ----------

    /**
     * 保存本地皮肤，返回**文件名**（不是绝对路径）。
     *
     * 必须遵守 AvatarRepository.resolveSkinFile 的约定：只有 `custom_` 开头的名字才会按
     * `filesDir/skins/<name>` 解析，其余都当 assets 内置皮肤找。之前这里存的是绝对路径，
     * 于是写库成功了、头像/模型却去 assets 里找这个路径 → 找不到 → 退回默认皮肤，
     * 表现就是「提示已保存，但皮肤没变」。
     */
    private fun saveLocal(context: Context, account: Account, skinUrl: String): String {
        val bytes = download(skinUrl) ?: error("皮肤图片下载失败，请稍后重试")
        val dir = File(context.filesDir, "skins").apply { mkdirs() }
        val name = "custom_" + (account.uuid.ifBlank { account.username }).replace("-", "") + ".png"
        File(dir, name).writeBytes(bytes)
        return name
    }

    // ---------- 公共 ----------

    private fun download(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes() else null
        }
    }.getOrNull()

    private fun serverHost(account: Account): String =
        account.serverUrl.orEmpty()
            .replace("https://", "")
            .replace("http://", "")
            .substringBefore("/")
            .ifBlank { "验证服务器" }
}
