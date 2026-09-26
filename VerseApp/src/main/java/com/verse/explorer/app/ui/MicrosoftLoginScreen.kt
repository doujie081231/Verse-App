package com.verse.explorer.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.verse.explorer.app.ui.theme.VerseColors
import java.io.File

private const val MSA_LOG_TAG = "MsaLogin"

/**
 * 内置浏览器页（微软登录验证用）
 *
 * 与 ZL2 的 WebViewScreen 对应：全屏 WebView 打开验证网址，
 * 顶部显示加载进度与当前网址（点击可用外部浏览器打开）；
 * 退出时销毁 WebView 并清理 Cookie 与 WebView 缓存。
 *
 * 关键节点均输出 [VerseLogger] 详细日志（tag=MSA_LOG_TAG），
 * 用于一次定位"打不开浏览器"问题：WebView 创建/配置 → loadUrl →
 * 页面加载/重定向/HTTP 错误/SSL 错误/渲染进程崩溃等。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MicrosoftLoginScreen(
    webUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var webUrl by remember { mutableStateOf(webUrl) }
    var isWebLoading by remember { mutableStateOf(true) }
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    /**
     * 打开浏览器前检查本机网络状态（用于错误日志辅助定位）。
     * 无网络/无可用网络（如 Wi-Fi 断开）时 WebView 会加载失败显示空白。
     */
    fun networkStatus(): String = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) return@runCatching "ConnectivityService=null"
        val active = cm.activeNetwork ?: return@runCatching "activeNetwork=null"
        val caps = cm.getNetworkCapabilities(active) ?: return@runCatching "NetworkCapabilities=null"
        buildString {
            append("activeNetwork=${active.toString().take(40)}")
            append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI).let { if (it) ",WiFi" else "" })
            append(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR).let { if (it) ",Cellular" else "" })
            append(if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) ",Internet" else ",NO_INTERNET")
            append(if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) ",Validated" else ",NOT_VALIDATED")
        }
    }.getOrDefault("网络状态获取失败")

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VerseColors.PcBgPrimary)
        ) {
            // 顶栏：返回 + 加载进度 + 当前网址（点击外部打开）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VerseColors.PcBgSecondary)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack, "返回",
                        Modifier.size(20.dp), VerseColors.PcTextPrimary
                    )
                }
                Text(
                    "微软登录",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    webUrl,
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = webUrl.isNotBlank()) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                            )
                        }
                )
                IconButton(onClick = {
                    if (webUrl.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                    }
                }) {
                    Icon(
                        Icons.Filled.OpenInBrowser, "用外部浏览器打开",
                        Modifier.size(18.dp), VerseColors.PcTextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = isWebLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    color = VerseColors.PcAccent
                )
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    VerseLogger.info("浏览器页：创建 WebView（SDK=${Build.VERSION.SDK_INT}, 目标 URL=${webUrl.ifBlank { "<空!>" }}）", MSA_LOG_TAG)
                    if (webUrl.isBlank()) {
                        VerseLogger.warn("浏览器页：webUrl 为空！WebView 将无地址可加载（检查上一环节 verification_uri）", MSA_LOG_TAG)
                    }
                    try {
                        WebView(context).apply {
                            webViewClient = object : WebViewClient() {
                                @Suppress("DEPRECATION")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    VerseLogger.info("浏览器页：shouldOverrideUrlLoading(legacy) -> $url", MSA_LOG_TAG)
                                    return super.shouldOverrideUrlLoading(view, url)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    val last = url.takeLast(60)
                                    VerseLogger.info("浏览器页：shouldOverrideUrlLoading -> …$last（整站重定向/新开窗口都会走到这里，若只有这一条而页面不动，说明卡在重定向）", MSA_LOG_TAG)
                                    return super.shouldOverrideUrlLoading(view, request)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    VerseLogger.info("浏览器页：onPageStarted url=${url ?: "<null>"}", MSA_LOG_TAG)
                                    webUrl = url ?: ""
                                    isWebLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    VerseLogger.info("浏览器页：onPageFinished url=${url ?: "<null>"}（页面加载完成标志：若始终没有这条日志，则页面从未加载成功=打不开）", MSA_LOG_TAG)
                                    webUrl = url ?: ""
                                    isWebLoading = false
                                }

                                // API 23+：主框架/子资源加载出错（含 ERR_ 系列错误码）
                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    val url = request?.url?.toString() ?: ""
                                    val main = request?.isForMainFrame == true
                                    if (main) {
                                        VerseLogger.error(
                                            "浏览器页：主框架加载失败 errorCode=${error?.errorCode}（${webViewErrorName(error?.errorCode ?: 0)}）desc=${error?.description} url=${url} | 网络状态: ${networkStatus()}",
                                            MSA_LOG_TAG
                                        )
                                    } else {
                                        VerseLogger.warn("浏览器页：子资源加载失败 errorCode=${error?.errorCode} desc=${error?.description} url=${url}", MSA_LOG_TAG)
                                    }
                                }

                                // API <23 的旧回调
                                @Suppress("DEPRECATION")
                                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                    super.onReceivedError(view, errorCode, description, failingUrl)
                                    VerseLogger.error(
                                        "浏览器页：onReceivedError(errorCode=$errorCode, ${webViewErrorName(errorCode)}, desc=$description, failingUrl=$failingUrl) | 网络状态: ${networkStatus()}",
                                        MSA_LOG_TAG
                                    )
                                }

                                // HTTP 层错误（如 404/500/重定向失败），页面可能显示空白
                                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    val status = errorResponse?.statusCode ?: -1
                                    val reason = errorResponse?.reasonPhrase ?: ""
                                    val url = request?.url?.toString() ?: ""
                                    val main = request?.isForMainFrame == true
                                    if (main) {
                                        VerseLogger.error("浏览器页：HTTP 错误 statusCode=$status reason=$reason url=$url（主框架 HTTP 失败→页面空白，典型：代理拦截/证书/CDN 故障）", MSA_LOG_TAG)
                                    } else {
                                        VerseLogger.warn("浏览器页：子资源 HTTP 错误 statusCode=$status url=${url.take(120)}", MSA_LOG_TAG)
                                    }
                                }

                                // API 35 移除 android.webkit.SslError，onReceivedSslError 覆盖已不可用；
                                // 登录页保持系统默认（拒绝证书错误）行为即可，SSL 错误仍经 onReceivedError 记录

                                // API 26+：渲染进程被杀（如内存不足/WebView 更新异常）→ 白屏/黑屏但无网络错误
                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    val crashed = detail?.didCrash() == true
                                    val oom = detail?.rendererPriorityAtExit() ?: -1
                                    VerseLogger.error(
                                        "浏览器页：渲染进程崩溃/退出 didCrash=$crashed priorityAtExit=$oom（白屏/打不开的常见原因：系统 WebView 被杀或内存不足）",
                                        MSA_LOG_TAG
                                    )
                                    return super.onRenderProcessGone(view, detail)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    // 里程碑式打点，避免刷屏（10 的倍数 + 首尾）
                                    if (newProgress % 25 == 0 || newProgress == 100 || newProgress < 10) {
                                        VerseLogger.info("浏览器页：加载进度 $newProgress% url=${view?.url?.take(80)}", MSA_LOG_TAG)
                                    }
                                }

                                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                                    if (message != null && (message.message().startsWith("Uncaught") || message.message().contains("net::ERR"))) {
                                        VerseLogger.warn("浏览器页：JS console(${message.messageLevel()}) ${message.message()} @${message.sourceId()}", MSA_LOG_TAG)
                                    }
                                    return super.onConsoleMessage(message)
                                }
                            }

                            settings.javaScriptEnabled = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.domStorageEnabled = true
                            settings.userAgentString = null // 保持默认 UA（部分微软登录页对改 UA 敏感）
                            VerseLogger.info(
                                "浏览器页：WebView 配置完成 js=${settings.javaScriptEnabled} domStorage=${settings.domStorageEnabled} cacheMode=${settings.cacheMode} ua=${settings.userAgentString?.take(80)}",
                                MSA_LOG_TAG
                            )
                            VerseLogger.info("浏览器页：调用 loadUrl(${webUrl.take(80)})", MSA_LOG_TAG)
                            try {
                                loadUrl(webUrl)
                                VerseLogger.info("浏览器页：loadUrl 已调用，等待页面回包", MSA_LOG_TAG)
                            } catch (e: Exception) {
                                VerseLogger.error("浏览器页：loadUrl 抛异常 ${e.javaClass.simpleName}: ${e.message}", MSA_LOG_TAG)
                                throw e
                            }
                            webViewHolder.value = this
                        }
                    } catch (e: Throwable) {
                        VerseLogger.error("浏览器页：WebView 创建/初始化异常 ${e.javaClass.simpleName}: ${e.message}", MSA_LOG_TAG)
                        throw e
                    }
                },
                update = {
                    // 不在此处重复加载 url
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 与 ZL2 一致：退出时销毁 WebView 并清理 Cookie 与缓存
            VerseLogger.info("浏览器页：销毁 WebView 并清理 Cookie（用户退出登录页）", MSA_LOG_TAG)
            webViewHolder.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewHolder.value = null

            runCatching {
                File(context.getDir("webview", 0), "").deleteRecursively()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                VerseLogger.info("浏览器页：Cookie 与缓存已清理", MSA_LOG_TAG)
            }.onFailure {
                VerseLogger.warn("浏览器页：清理 Cookie/缓存失败: ${it.message}", MSA_LOG_TAG)
            }
        }
    }
}

/** WebViewClient 错误码 → 可读名称（自实现映射，不依赖框架附加类，兼容低版本 SDK） */
private fun webViewErrorName(code: Int): String = when (code) {
    android.webkit.WebViewClient.ERROR_UNKNOWN -> "UNKNOWN(-1)"
    android.webkit.WebViewClient.ERROR_HOST_LOOKUP -> "HOST_LOOKUP(-2)"
    android.webkit.WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME -> "UNSUPPORTED_AUTH_SCHEME(-3)"
    android.webkit.WebViewClient.ERROR_AUTHENTICATION -> "AUTHENTICATION(-4)"
    android.webkit.WebViewClient.ERROR_PROXY_AUTHENTICATION -> "PROXY_AUTHENTICATION(-5)"
    android.webkit.WebViewClient.ERROR_CONNECT -> "CONNECT(-6)"
    android.webkit.WebViewClient.ERROR_IO -> "IO(-7)"
    android.webkit.WebViewClient.ERROR_TIMEOUT -> "TIMEOUT(-8)"
    android.webkit.WebViewClient.ERROR_REDIRECT_LOOP -> "REDIRECT_LOOP(-9)"
    android.webkit.WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "UNSUPPORTED_SCHEME(-10)"
    android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "FAILED_SSL_HANDSHAKE(-11)"
    android.webkit.WebViewClient.ERROR_BAD_URL -> "BAD_URL(-12)"
    android.webkit.WebViewClient.ERROR_FILE -> "FILE(-13)"
    android.webkit.WebViewClient.ERROR_FILE_NOT_FOUND -> "FILE_NOT_FOUND(-14)"
    android.webkit.WebViewClient.ERROR_TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS(-15)"
    else -> "ERR($code)"
}

/** SslError 主错误码 → 可读名称（常量位于 android.net.http.SslError） */
private fun sslErrorName(code: Int): String = when (code) {
    android.net.http.SslError.SSL_NOTYETVALID -> "NOTYETVALID(0)"
    android.net.http.SslError.SSL_EXPIRED -> "EXPIRED(1)"
    android.net.http.SslError.SSL_IDMISMATCH -> "IDMISMATCH(2)"
    android.net.http.SslError.SSL_UNTRUSTED -> "UNTRUSTED(3)"
    android.net.http.SslError.SSL_DATE_INVALID -> "DATE_INVALID(4)"
    android.net.http.SslError.SSL_INVALID -> "INVALID(5)"
    else -> "SSL($code)"
}
