package com.verse.explorer.app.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private fun jsStr(s: String?): String =
    (s ?: "").replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SkinViewerWebView(
    skinUrl: String?,
    model: String = "default",
    animation: String = "idle",
    background: String = "white",
    nameTag: String? = null,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let { wv ->
                wv.evaluateJavascript("dispose()", null)
                wv.destroy()
            }
            webView = null
            pageLoaded = false
        }
    }

    LaunchedEffect(pageLoaded, skinUrl, model) {
        if (pageLoaded) {
            webView?.evaluateJavascript(
                "initViewer('${jsStr(skinUrl)}','${jsStr(model)}')", null
            )
        }
    }

    LaunchedEffect(pageLoaded, animation) {
        if (pageLoaded) {
            webView?.evaluateJavascript("setAnimation('${jsStr(animation)}')", null)
        }
    }

    LaunchedEffect(pageLoaded, background) {
        if (pageLoaded) {
            webView?.evaluateJavascript("setBackground('${jsStr(background)}')", null)
        }
    }

    LaunchedEffect(pageLoaded, nameTag) {
        if (pageLoaded) {
            webView?.evaluateJavascript("setNameTag('${jsStr(nameTag)}')", null)
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.mediaPlaybackRequiresUserGesture = false
                setBackgroundColor(0x00000000)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded = true
                    }
                }
                loadUrl("file:///android_asset/skinview3d/skin_viewer.html")
                webView = this
            }
        },
        update = { },
        modifier = modifier
    )
}
