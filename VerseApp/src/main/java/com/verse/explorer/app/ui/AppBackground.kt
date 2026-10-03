package com.verse.explorer.app.ui

import android.content.Context
import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import com.verse.explorer.app.data.VersePersonalization
import java.io.File
import kotlin.math.max

/**
 * 个性化自定义背景层：按 VersePersonalization 的 style 渲染图片（coil 裁剪铺满）或
 * 视频（VideoView 循环播放、静音、裁剪铺满），透明度由背景透明度设置驱动。
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val style = VersePersonalization.backgroundStyle
    val imagePath = VersePersonalization.backgroundImagePath
    val videoPath = VersePersonalization.backgroundVideoPath
    val opacity = (VersePersonalization.backgroundOpacity / 100f).coerceIn(0f, 1f)

    when (style) {
        "image" -> if (imagePath != null) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = opacity,
                modifier = modifier.fillMaxSize()
            )
        }
        "video" -> if (videoPath != null) {
            VideoBackground(path = videoPath, opacity = opacity, modifier = modifier)
        }
    }
}

/**
 * 视频背景：循环播放、静音、按视频比例放大铺满（超出的部分由外层 clipToBounds 裁掉）。
 * 生命周期：切后台暂停、回前台恢复；离开组合时释放 MediaPlayer。
 */
@Composable
private fun VideoBackground(path: String, opacity: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var videoView by remember { mutableStateOf<CoverVideoView?>(null) }

    // 生命周期观察 + 资源释放：LocalContext 在 MainActivity 组合内即 Activity（LifecycleOwner）
    DisposableEffect(context, path) {
        val lifecycleOwner = (context as? LifecycleOwner)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> videoView?.pause()
                Lifecycle.Event.ON_START -> videoView?.start()
                else -> {}
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            videoView?.let {
                it.stopPlayback()
                it.setVideoURI(null)
            }
            videoView = null
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                CoverVideoView(ctx).also { vv ->
                    videoView = vv
                    vv.setVideoPath(path)
                    vv.setAlpha(opacity)
                    vv.start()
                }
            },
            update = { vv ->
                vv.setAlpha(opacity)
                vv.setVideoPath(path)
                vv.start()
            }
        )
    }
}

/**
 * 裁剪铺满型 VideoView：onMeasure 按视频宽高比放大到覆盖容器（宽高取 max 比例），
 * 中心居中显示，超出部分由父容器裁剪；循环播放 + 静音。
 */
private class CoverVideoView(context: Context) : VideoView(context) {

    private var videoW = 0
    private var videoH = 0

    init {
        setOnPreparedListener { mp ->
            videoW = mp.videoWidth
            videoH = mp.videoHeight
            mp.setVolume(0f, 0f) // 背景视频静音
            mp.isLooping = true
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val containerW = getDefaultSize(0, widthMeasureSpec)
        val containerH = getDefaultSize(0, heightMeasureSpec)
        var width = containerW
        var height = containerH
        if (videoW > 0 && videoH > 0) {
            val scale = max(
                containerW.toFloat() / videoW,
                containerH.toFloat() / videoH
            )
            width = (videoW * scale).toInt()
            height = (videoH * scale).toInt()
        }
        setMeasuredDimension(width, height)
    }
}
