package com.verse.explorer.core.account

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 皮肤站开发者 API Key 的本地存储。
 *
 * 这是账号级的**写权限凭据**（能把任意公开皮肤挂到你名下角色上），所以不能明文落盘：
 * 走 Jetpack Security 的 EncryptedSharedPreferences，主密钥由 Android Keystore 托管，
 * 应用被 root 后翻数据目录、或备份被导出，拿到的都是密文。
 *
 * 兼容与降级：
 *  - 老版本把 Key 明文存在同名文件里，读到后自动迁移进加密存储并删掉明文那份；
 *  - 个别定制 ROM 的 Keystore 会抛异常（恢复出厂后密钥失效也属此类），
 *    此时退回普通存储 —— 宁可存得不够安全，也不要让玩家填过的 Key 凭空消失。
 *
 * 无论哪种情况，Key 都不会出现在日志里：本类不写任何日志，调用方也只展示末四位。
 */
object SkinLibraryKeyStore {

    private const val PREF = "skin_library_prefs"
    private const val KEY_API = "dev_api_key"

    @Volatile
    private var cached: SharedPreferences? = null

    fun get(context: Context): String {
        val value = prefs(context).getString(KEY_API, null)?.trim().orEmpty()
        if (value.isNotEmpty()) return value

        // 兼容旧版明文存储：读到就迁移，随后删除明文
        val legacy = legacyPrefs(context).getString(KEY_API, null)?.trim().orEmpty()
        if (legacy.isNotEmpty()) {
            set(context, legacy)
            legacyPrefs(context).edit().remove(KEY_API).apply()
        }
        return legacy
    }

    fun set(context: Context, key: String) {
        val value = key.trim()
        val editor = prefs(context).edit()
        if (value.isEmpty()) {
            editor.remove(KEY_API)
        } else {
            editor.putString(KEY_API, value)
        }
        editor.apply()
    }

    /** 给界面显示用：只露末四位，避免整串 Key 出现在截图里 */
    fun masked(context: Context): String {
        val key = get(context)
        if (key.isEmpty()) return ""
        return if (key.length <= 8) "••••" else "••••${key.takeLast(4)}"
    }

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        val app = context.applicationContext
        val resolved = runCatching {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                PREF,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            legacyPrefs(app)
        }
        cached = resolved
        return resolved
    }

    private fun legacyPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
