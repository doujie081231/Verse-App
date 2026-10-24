package com.verse.explorer.app.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.verse.explorer.app.R
import com.verse.explorer.app.data.VersePersonalization
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/**
 * 卡片等前景容器色的背景适配：背景有效且不透明度低于 100% 时，
 * 容器色按设置的不透明度降低 alpha 以透出背景画面；否则返回原色。
 */
@Composable
fun influencedByBackgroundColor(
    color: Color,
    influencedAlpha: Float = VersePersonalization.backgroundOpacity / 100f,
    enabled: Boolean = true
): Color {
    if (!enabled) return color
    return if (VersePersonalization.backgroundValid &&
        VersePersonalization.backgroundOpacity < 100
    ) {
        color.copy(alpha = influencedAlpha)
    } else {
        color
    }
}

/**
 * 个性化自定义背景层：按 VersePersonalization 的 style 渲染图片（裁剪铺满）或
 * 视频（循环播放、音量可调、裁剪铺满）。背景层恒为不透明，
 * 内容卡片通过受背景不透明度影响的容器色透出背景画面；
 * 背景模糊模式下 Android 12+ 直接模糊背景层并叠白色提亮层，
 * Android 12- 显示按模糊半径降采样解码后多轮盒式模糊的静态位图。
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val style = VersePersonalization.backgroundStyle
    val imagePath = VersePersonalization.backgroundImagePath
    val videoPath = VersePersonalization.backgroundVideoPath
    val blur = VersePersonalization.backgroundBlur
    val opacity = VersePersonalization.backgroundOpacity
    val backgroundMode = VersePersonalization.backgroundBlurType == "background"
    val backgroundBlurEnabled = backgroundMode && blur > 0 && opacity < 100
    val layerCapture = !backgroundMode && blur > 0 && opacity < 100 && VerseBackgroundStore.isValid
    val density = LocalDensity.current

    // 同步背景有效性与代次到共享毛玻璃状态（卡片毛玻璃切片读取）
    LaunchedEffect(style, imagePath, videoPath) {
        VerseBackgroundStore.isImage = style == "image" && imagePath != null
        VerseBackgroundStore.isVideo = style == "video" && videoPath != null
        VerseBackgroundStore.isValid = VerseBackgroundStore.isImage || VerseBackgroundStore.isVideo
        VerseBackgroundStore.refreshTrigger = !VerseBackgroundStore.refreshTrigger
    }

    // Android 12- 背景模糊模式的静态模糊位图：任一相关配置变化时重新生成，否则清空
    LaunchedEffect(Unit) {
        snapshotFlow {
            BlurConfig(
                blur = VersePersonalization.backgroundBlur,
                blurType = VersePersonalization.backgroundBlurType,
                opacity = VersePersonalization.backgroundOpacity,
                valid = VerseBackgroundStore.isValid,
                isImage = VerseBackgroundStore.isImage,
                imagePath = VersePersonalization.backgroundImagePath,
                generation = VerseBackgroundStore.refreshTrigger
            )
        }.collectLatest { config ->
            when {
                Build.VERSION.SDK_INT >= 31 || !config.valid ||
                    config.blur <= 0 || config.opacity >= 100 ->
                    VerseBackgroundStore.clearBlurredBackground()
                config.isImage && config.blurType == "background" ->
                    VerseBackgroundStore.refreshStaticDisplayBitmap(
                        config.blur,
                        File(config.imagePath ?: return@collectLatest),
                        density.density
                    )
            }
        }
    }

    Box(
        modifier = modifier
            .then(
                if (backgroundBlurEnabled && Build.VERSION.SDK_INT >= 31) {
                    Modifier.blur(blur.dp)
                } else {
                    Modifier
                }
            )
            .backgroundCapture(
                store = VerseBackgroundStore,
                recordContent = layerCapture,
                blurRadiusPx = blur * density.density,
                whiteOverlayAlpha = if (backgroundBlurEnabled) whiteOverlayAlpha(blur) else 0f
            )
    ) {
        when {
            style == "video" && videoPath != null -> {
                VideoBackground(
                    path = videoPath,
                    volume = VersePersonalization.videoVolume / 100f,
                    modifier = Modifier.fillMaxSize()
                )
            }
            style == "image" && imagePath != null -> {
                val blurred = VerseBackgroundStore.blurredBackground
                if (backgroundBlurEnabled && Build.VERSION.SDK_INT < 31 && blurred != null) {
                    Image(
                        bitmap = blurred,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** 静态模糊位图的触发条件快照，任一变化都会重新生成或清空 */
private data class BlurConfig(
    val blur: Int,
    val blurType: String,
    val opacity: Int,
    val valid: Boolean,
    val isImage: Boolean,
    val imagePath: String?,
    val generation: Boolean
)

/**
 * 视频背景：循环播放、按视频比例放大铺满（RESIZE_MODE_ZOOM，超出部分裁掉）、
 * 音量由设置驱动；切后台暂停、回前台恢复，离开组合时释放播放器。
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoBackground(path: String, volume: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val player = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true) // 开启解码器 fallback
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            val audioAttr = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttr, false)
            repeatMode = Player.REPEAT_MODE_ONE
            this.volume = volume
        }
    }

    // TextureView 渲染的 PlayerView：背景层场景下 SurfaceView 会穿透窗口层级，故用 texture_view
    val playerView = remember {
        val view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.player_texture_view, null) as PlayerView
        view.apply {
            this.player = player
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    LaunchedEffect(path) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        player.prepare()
    }

    LaunchedEffect(volume) {
        player.volume = volume
    }

    // 生命周期观察 + 资源释放：LocalContext 在 MainActivity 组合内即 Activity（LifecycleOwner）
    DisposableEffect(context, path) {
        val lifecycleOwner = (context as? LifecycleOwner)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.playWhenReady = true
                Lifecycle.Event.ON_STOP -> player.playWhenReady = false
                else -> {}
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
