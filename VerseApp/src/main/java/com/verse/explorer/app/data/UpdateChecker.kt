package com.verse.explorer.app.data

import android.content.Context
import com.verse.explorer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 版本更新检查 —— 迁移自 ZL2（Zalith Launcher 2）的 LauncherUpgradeViewModel 逻辑：
 *
 *  - 远端配置读取版本号/版本名/发布时间/更新日志/网盘链接（对应 ZL2 的 RemoteData）；
 *  - 启动时限速检查（1 小时内不重复请求），且启动检查会跳过用户已忽略的版本；
 *  - 手动检查限速更短（5 秒），且不受「忽略」影响，检测到新版必定弹窗；
 *  - 客户端只负责「检测 + 弹窗 + 打开链接」，链接一律通过系统外部浏览器打开
 *    （[com.verse.explorer.app.util.IntentHelper.openUrl]），应用内不下载 APK、不内置浏览器。
 *
 * 配置仍由官网后台维护（verselauncher.cn，国内直连可用，相当于 ZL2 的国内镜像通道）。
 */
object UpdateChecker {

    /** 官网版本更新配置接口 */
    private const val ENDPOINT = "https://verselauncher.cn/api/app-update"

    /** GitHub Release 页面：外部浏览器打开即可看到整包 */
    const val GITHUB_RELEASES_URL = "https://github.com/doujie081231/Verse-App/releases/latest"

    /** 默认网盘下载入口（夸克）：远端配置未下发 cloud_drive 时回退使用 */
    const val DEFAULT_CLOUD_DRIVE_URL = "https://pan.quark.cn/s/1dc3bc96e683"

    /** 启动时检查的限速间隔（ZL2：1 小时） */
    const val STARTUP_INTERVAL_MS: Long = 60 * 60 * 1000L

    /** 手动检查的限速间隔（ZL2：5 秒） */
    const val MANUAL_INTERVAL_MS: Long = 5_000L

    private const val PREFS = "launcher"
    private const val KEY_LAST_CHECK = "last_update_check_time"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val releaseDate: String,
        val forceUpdate: Boolean,
        val changelog: List<String>,
        /** 远端下发的网盘链接（ZL2 cloud_drive）；未配置时为 null，调用方回退 [DEFAULT_CLOUD_DRIVE_URL] */
        val cloudDriveUrl: String? = null,
        /** 远端下发的安装包直链（config.downloadUrl）：浏览器点击即开始下载；未配置时回退 Release 页面 */
        val downloadUrl: String? = null
    )

    /**
     * 请求更新配置并判断是否有新版本。
     * 网络异常、无配置、配置版本不高于本机时返回 null（静默忽略，不打扰用户）。
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(ENDPOINT).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val config = JSONObject(body).optJSONObject("config") ?: return@withContext null
                val info = parseConfig(config) ?: return@withContext null
                if (info.versionCode <= BuildConfig.VERSION_CODE) return@withContext null
                info
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 解析配置（字段名同时兼容官网现有格式与 ZL2 RemoteData 格式） */
    private fun parseConfig(config: JSONObject): UpdateInfo? {
        val versionCode = config.optInt("versionCode", config.optInt("code", 0))
        if (versionCode <= 0) return null
        val versionName = config.optString("versionName", config.optString("version", ""))
        val releaseDate = config.optString("releaseDate", config.optString("created_at", ""))
        val changelog = mutableListOf<String>()
        config.optJSONArray("changelog")?.let { arr -> collectStrings(arr, changelog) }
        // ZL2 的 bodies/default_body 为 markdown 日志，这里同样兼容：取 default_body 剩余条目
        config.optJSONObject("default_body")?.optString("markdown")?.takeIf { it.isNotBlank() }
            ?.let { changelog.add(it) }
        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            releaseDate = releaseDate,
            forceUpdate = config.optBoolean("forceUpdate", false),
            changelog = changelog,
            cloudDriveUrl = parseCloudDrive(config),
            downloadUrl = config.optString("downloadUrl").takeIf { it.isNotBlank() }
        )
    }

    /** 网盘链接：cloud_drive 直串 → cloud_drive.link → cloud_drives[0].link → cloud_drives[0] */
    private fun parseCloudDrive(config: JSONObject): String? {
        runCatching {
            val cd = config.opt("cloud_drive") ?: return@runCatching
            when (cd) {
                is String -> return cd.takeIf { it.isNotBlank() }
                is JSONObject -> return cd.optString("link").takeIf { it.isNotBlank() }
            }
        }
        runCatching {
            val drives = config.optJSONArray("cloud_drives") ?: return@runCatching
            if (drives.length() > 0) {
                val first = drives.optJSONObject(0)
                val link = first?.optString("link")?.takeIf { it.isNotBlank() }
                    ?: first?.optString("links")?.takeIf { it.isNotBlank() }
                if (link != null) return link
            }
        }
        return null
    }

    private fun collectStrings(arr: JSONArray, into: MutableList<String>) {
        for (i in 0 until arr.length()) {
            arr.optString(i).takeIf { it.isNotBlank() }?.let { into.add(it) }
        }
    }

    // ===== 限速（ZL2 isWithinRateLimit / updateLastCheckTime） =====

    fun isWithinRateLimit(context: Context, intervalMs: Long): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last < intervalMs
    }

    fun updateLastCheckTime(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    // ===== 忽略版本（ZL2 lastIgnoredVersion） =====

    /** 启动检查用：用户已忽略到的版本号（≥ 该版本不再提示） */
    fun ignoredVersion(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("update_ignored_version", 0)

    /** 记录用户忽略的版本（旧数据沿用同一 key，不做迁移） */
    fun setIgnoredVersion(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("update_ignored_version", versionCode).apply()
    }
}
