package com.verse.explorer.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
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

/**
 * 内置浏览器页（微软登录验证用）
 *
 * 与 ZL2 的 WebViewScreen 对应：全屏 WebView 打开验证网址，
 * 顶部显示加载进度与当前网址（点击可用外部浏览器打开）；
 * 退出时销毁 WebView 并清理 Cookie 与 WebView 缓存。
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
                    WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                webUrl = url ?: ""
                                isWebLoading = false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                webUrl = url ?: ""
                                isWebLoading = true
                            }
                        }

                        settings.javaScriptEnabled = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.domStorageEnabled = true
                        loadUrl(webUrl)
                        webViewHolder.value = this
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
            }
        }
    }
}
