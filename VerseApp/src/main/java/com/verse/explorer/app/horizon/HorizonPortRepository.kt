package com.verse.explorer.app.horizon

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.verse.explorer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Horizon 安卓移植的下载 / 安装 / 启动。
 *
 * 设计要点：
 * - 列表来自两个社区移植仓库的 GitHub Releases；未认证 API 限 60 次/小时，
 *   所以带 30 分钟磁盘缓存，网络失败降级用上一次缓存
 * - 不接第三方下载镜像：APK 是要装进系统的二进制，走不明代理等于把签名校验前置环节交出去；
 *   直连失败就让用户重试（GitHub release 资产在国内多数网络可达，只是慢）
 * - 包名不写死：从下载好的 APK 里用 getPackageArchiveInfo 实时解析（两个移植包名不同且会变），
 *   安装成功后用 getLaunchIntentForPackage 拉起
 */
object HorizonPortRepository {

    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    private const val CACHE_FILE = "horizon_releases_cache.json"
    private const val PREFS = "horizon_install"
    private const val KEY_INSTALLED_TAG = "installed_tag"
    private const val KEY_INSTALLED_PACKAGE = "installed_package"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 拉取全部可用版本；force=true 跳过缓存 TTL */
    suspend fun fetchReleases(deviceAbi: String, force: Boolean = false, context: Context): List<HorizonRelease> =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.filesDir, CACHE_FILE)
            if (!force && cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < CACHE_TTL_MS) {
                runCatching { parseCache(cacheFile, deviceAbi) }.getOrNull()?.let { return@withContext it }
            }
            val fetched = mutableListOf<HorizonRelease>()
            for (source in HorizonCatalog.sources) {
                val json = runCatching { fetchSourceReleases(source.repo) }.getOrNull() ?: continue
                fetched += HorizonCatalog.parseReleases(source, json, deviceAbi)
            }
            if (fetched.isNotEmpty()) {
                runCatching { writeCache(cacheFile, deviceAbi, fetched) }
                return@withContext fetched
            }
            // 网络全挂：宁可给旧缓存，也不给空列表
            runCatching { parseCache(cacheFile, deviceAbi) }.getOrNull() ?: emptyList()
        }

    private fun fetchSourceReleases(repo: String): com.google.gson.JsonArray {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases?per_page=10")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            val body = resp.body?.string().orEmpty()
            return JsonParser.parseString(body).asJsonArray
        }
    }

    private fun parseCache(cacheFile: File, deviceAbi: String): List<HorizonRelease> {
        val root = JsonParser.parseString(cacheFile.readText()).asJsonObject
        val out = mutableListOf<HorizonRelease>()
        for (source in HorizonCatalog.sources) {
            val arr = root.getAsJsonArray(source.repo) ?: continue
            out += HorizonCatalog.parseReleases(source, arr, deviceAbi)
        }
        return out
    }

    private fun writeCache(cacheFile: File, deviceAbi: String, releases: List<HorizonRelease>) {
        val root = com.google.gson.JsonObject()
        // 按源分组回写，保证降级解析的键与实时解析一致
        for (source in HorizonCatalog.sources) {
            val arr = com.google.gson.JsonArray()
            releases.filter { it.repo == source.repo }.forEach { r ->
                arr.add(com.google.gson.JsonObject().apply {
                    addProperty("tag_name", r.tag)
                    addProperty("published_at", r.publishedAt)
                    addProperty("html_url", r.notesUrl)
                    addProperty("prerelease", r.experimental)
                    add("assets", com.google.gson.JsonArray().apply {
                        add(com.google.gson.JsonObject().apply {
                            addProperty("name", r.apkName)
                            addProperty("size", r.apkSize)
                            addProperty("browser_download_url", r.apkUrl)
                        })
                    })
                })
            }
            root.add(source.repo, arr)
        }
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(root.toString())
    }

    // ---------- 下载 ----------

    fun apkFile(context: Context, release: HorizonRelease): File =
        File(File(context.cacheDir, "horizon"), "${release.tag}.apk")

    suspend fun downloadApk(
        context: Context,
        release: HorizonRelease,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dest = apkFile(context, release)
        dest.parentFile?.mkdirs()
        if (dest.exists() && dest.length() == release.apkSize) {
            onProgress(release.apkSize, release.apkSize)
            return@withContext dest
        }
        val request = Request.Builder().url(release.apkUrl).build()
        downloadClient.newCall(request).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            val body = resp.body ?: error("空响应")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastNotify = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastNotify >= 512 * 1024) {
                            lastNotify = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                    output.flush()
                    onProgress(downloaded, total)
                }
            }
            if (total > 0 && dest.length() != total) {
                dest.delete()
                error("下载不完整：${dest.length()}/${total}")
            }
            dest
        }
    }

    // ---------- 安装与启动 ----------

    /** 从 APK 读取包名（两个移植包名不同且可能变，写死必坏） */
    fun resolvePackageName(context: Context, apk: File): String? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName

    fun recordInstalled(context: Context, release: HorizonRelease, packageName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_INSTALLED_TAG, release.tag)
            .putString(KEY_INSTALLED_PACKAGE, packageName)
            .apply()
    }

    fun installedTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_INSTALLED_TAG, null)

    /** 已安装且有可用启动入口时返回启动 Intent */
    fun launchIntent(context: Context): Intent? {
        val pkg = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_INSTALLED_PACKAGE, null) ?: return null
        return context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 交给系统安装器；未授权「安装未知应用」时系统自己会引导用户去开权限 */
    fun startInstall(context: Context, apk: File): Boolean {
        val uri = FileProvider.getUriForFile(
            context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    /** 详情页展示的官方/项目链接 */
    fun notesUrl(release: HorizonRelease): String = release.notesUrl
}
