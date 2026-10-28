package com.verse.explorer.app.data

import com.verse.explorer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 版本更新检查：读取官网维护的版本更新配置，与本机 versionCode 比较。
 * 配置由官网后台维护（可编辑 / 上传 / 删除），客户端启动与「关于」页手动检查时调用。
 */
object UpdateChecker {

    /** 官网版本更新配置接口 */
    private const val ENDPOINT = "https://verselauncher.cn/api/app-update"

    /** GitHub Release 页面：手机浏览器打开即可看到整包与分卷（应用内不再下载） */
    const val GITHUB_RELEASES_URL = "https://github.com/doujie081231/Verse-App/releases/latest"

    /** 网盘下载入口（夸克，国内网络更稳） */
    const val PAN_DOWNLOAD_URL = "https://pan.quark.cn/s/1dc3bc96e683"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val releaseDate: String,
        val forceUpdate: Boolean,
        val changelog: List<String>
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
                val versionCode = config.optInt("versionCode", 0)
                if (versionCode <= BuildConfig.VERSION_CODE) return@withContext null

                val changelog = mutableListOf<String>()
                config.optJSONArray("changelog")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let { changelog.add(it) }
                    }
                }
                UpdateInfo(
                    versionCode = versionCode,
                    versionName = config.optString("versionName", ""),
                    releaseDate = config.optString("releaseDate", ""),
                    forceUpdate = config.optBoolean("forceUpdate", false),
                    changelog = changelog
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
