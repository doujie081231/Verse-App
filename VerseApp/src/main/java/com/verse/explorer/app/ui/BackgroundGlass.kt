package com.verse.explorer.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.core.graphics.withSave
import com.verse.explorer.app.data.VersePersonalization
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

/**
 * 背景毛玻璃共享状态：背景有效性与屏幕区域、背景捕获节点发布的预模糊产物
 * （Android 12+ 共享 GPU 模糊层 / Android 12- 预模糊位图），供卡片毛玻璃切片采样。
 */
object VerseBackgroundStore {
    /** 自定义背景是否有效（存在有效的图片或视频） */
    var isValid by mutableStateOf(false)
        internal set

    var isVideo by mutableStateOf(false)
        internal set

    var isImage by mutableStateOf(false)
        internal set

    /** 背景变更代次，用于触发视频/图片重新加载 */
    var refreshTrigger by mutableStateOf(false)
        internal set

    /** 背景容器在屏幕坐标系中的区域，由背景捕获节点在布局时记录；毛玻璃切片据此对齐采样 */
    var backgroundBounds by mutableStateOf(Rect.Zero)
        internal set

    private val engine = BackgroundGlassBlur()

    /** Android 12+ 上由背景捕获节点维护的共享预模糊 GPU 层 */
    val glassLayer: GraphicsLayer? get() = engine.glassLayer

    /** Android 12- 上由背景捕获节点维护的清晰捕获层 */
    val captureLayer: GraphicsLayer? get() = engine.captureLayer

    /** 预模糊的背景位图（Android 12-） */
    val blurredBackground: ImageBitmap? get() = engine.blurredBackground

    fun updateBackgroundBounds(coordinates: LayoutCoordinates) {
        val position = coordinates.positionOnScreen()
        if (!position.isSpecified) return
        backgroundBounds = Rect(position, coordinates.size.toSize())
    }

    fun attachGlassLayer(layer: GraphicsLayer?) {
        engine.attachGlassLayer(layer)
    }

    fun attachCaptureLayer(layer: GraphicsLayer?) {
        engine.attachCaptureLayer(layer)
    }

    /** 捕获当前捕获层的一帧并模糊为前景毛玻璃位图（Android 12-） */
    suspend fun captureGlassFrame(density: Float) {
        engine.captureFrame(density)
    }

    /** 依据图片文件重新生成静态模糊背景位图（Android 12- 背景模糊模式） */
    suspend fun refreshStaticDisplayBitmap(blur: Int, backgroundFile: File, density: Float) {
        engine.refreshStaticDisplayBitmap(blur, backgroundFile, density)
    }

    fun clearBlurredBackground() {
        engine.clearBlurredBackground()
    }
}

/**
 * 背景毛玻璃效果：在元素内容下方对齐绘制启动器背景的预模糊结果
 * @param blur 模糊半径（Dp）
 * @param color 元素容器颜色，绘制在模糊切片上方用于着色
 * @param enabled 是否应用毛玻璃效果
 */
@Composable
fun Modifier.backgroundGlass(
    blur: Int,
    color: Color,
    enabled: Boolean = true,
): Modifier {
    if (VersePersonalization.backgroundBlurType == "background") return this
    if (!enabled) return this
    if (!VerseBackgroundStore.isValid) return this
    if (blur <= 0 || VersePersonalization.backgroundOpacity >= 100) return this
    return this then GlassElement(blur, color, VerseBackgroundStore)
}

/**
 * 背景捕获：由背景层挂载，发布背景内容与屏幕区域供毛玻璃效果采样
 * @param recordContent 是否将内容录制到共享层（前景毛玻璃模式）
 * @param blurRadiusPx 前景模式 GPU 预模糊半径（像素，仅 Android 12+ 生效）
 * @param whiteOverlayAlpha 白色提亮层透明度（0 表示不绘制）
 */
internal fun Modifier.backgroundCapture(
    store: VerseBackgroundStore,
    recordContent: Boolean,
    blurRadiusPx: Float,
    whiteOverlayAlpha: Float,
): Modifier = this then BackgroundCaptureElement(store, recordContent, blurRadiusPx, whiteOverlayAlpha)

/** 白色提亮层透明度：模糊越强背景越亮，Softlight 混合缓解暗背景下的可读性问题 */
internal fun whiteOverlayAlpha(blur: Int): Float {
    val t = (blur / 80f).coerceIn(0f, 1f)
    return lerp(start = 0f, stop = 0.25f, fraction = sqrt(t))
}

private data class GlassElement(
    val blur: Int,
    val color: Color,
    val store: VerseBackgroundStore,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(blur, color, store)

    override fun update(node: GlassNode) {
        node.blur = blur
        node.color = color
    }
}

/**
 * 毛玻璃绘制节点：绘制期间读取的状态变化只触发重绘，不触发重组
 */
private class GlassNode(
    blur: Int,
    color: Color,
    val store: VerseBackgroundStore,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    var blur: Int = blur
        set(value) {
            if (field == value) return
            field = value
            invalidateDraw()
        }

    var color: Color = color
        set(value) {
            if (field == value) return
            field = value
            invalidateDraw()
        }

    // 元素在屏幕坐标系中的位置，随布局逐帧更新，绘制时据此对齐采样
    private var screenOrigin = Offset.Zero
    private var hasOrigin = false

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val position = coordinates.positionOnScreen()
        if (!position.isSpecified) return
        if (!hasOrigin || position != screenOrigin) {
            screenOrigin = position
            hasOrigin = true
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawGlass()
        drawContent()
    }

    private fun DrawScope.drawGlass() {
        if (blur <= 0 || VersePersonalization.backgroundOpacity >= 100) return
        val bounds = store.backgroundBounds
        if (bounds.isEmpty || !hasOrigin) return

        val nativeCanvas = drawContext.canvas.nativeCanvas
        nativeCanvas.withSave {
            val current = Matrix()
            getMatrix(current)
            val inverted = Matrix()
            if (current.invert(inverted)) {
                val values = FloatArray(9).also(current::getValues)
                val left = bounds.left - screenOrigin.x + values[Matrix.MTRANS_X]
                val top = bounds.top - screenOrigin.y + values[Matrix.MTRANS_Y]

                concat(inverted)
                if (Build.VERSION.SDK_INT >= 31) {
                    store.glassLayer?.let { layer ->
                        this@drawGlass.translate(left, top) {
                            drawLayer(layer)
                        }
                    }
                } else {
                    store.blurredBackground?.let { bitmap ->
                        this@drawGlass.drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt())
                        )
                    }
                }
            }
        }

        drawRect(color = color, blendMode = BlendMode.SrcOver)
        drawRect(
            color = Color.White.copy(alpha = whiteOverlayAlpha(blur)),
            blendMode = BlendMode.Softlight
        )
    }
}

private data class BackgroundCaptureElement(
    val store: VerseBackgroundStore,
    val recordContent: Boolean,
    val blurRadiusPx: Float,
    val whiteOverlayAlpha: Float,
) : ModifierNodeElement<BackgroundCaptureNode>() {
    override fun create() = BackgroundCaptureNode(store, recordContent, blurRadiusPx, whiteOverlayAlpha)

    override fun update(node: BackgroundCaptureNode) {
        node.store = store
        node.recordContent = recordContent
        node.blurRadiusPx = blurRadiusPx
        node.whiteOverlayAlpha = whiteOverlayAlpha
        node.sync()
        node.invalidateDraw()
    }
}

/**
 * 背景捕获节点：持有共享模糊层并随节点生命周期发布/摘除，
 * 布局时上报背景屏幕区域，Android 12- 前景模式下驱动帧捕获循环
 */
private class BackgroundCaptureNode(
    var store: VerseBackgroundStore,
    var recordContent: Boolean,
    var blurRadiusPx: Float,
    var whiteOverlayAlpha: Float,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private var graphicsContext: GraphicsContext? = null
    private var sourceLayer: GraphicsLayer? = null
    private var blurredLayer: GraphicsLayer? = null
    private var blurredEffectRadius = 0f
    private var captureJob: Job? = null

    override fun onAttach() {
        sync()
    }

    override fun onDetach() {
        releaseLayers()
        graphicsContext = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        store.updateBackgroundBounds(coordinates)
    }

    override fun ContentDrawScope.draw() {
        val source = sourceLayer
        if (recordContent && source != null) {
            source.record { this@draw.drawContent() }
            drawLayer(source)
            if (Build.VERSION.SDK_INT >= 31) {
                ensureBlurredLayer()?.record { drawLayer(source) }
            }
        } else {
            drawContent()
        }
        if (whiteOverlayAlpha > 0f) {
            drawRect(
                color = Color.White.copy(alpha = whiteOverlayAlpha),
                blendMode = BlendMode.Softlight
            )
        }
    }

    /**
     * 依据当前参数调和共享层的发布与捕获循环的启停
     */
    fun sync() {
        syncLayers()
        syncCaptureLoop()
    }

    private fun syncLayers() {
        if (recordContent) {
            val source = sourceLayer ?: createLayer()?.also { sourceLayer = it }
            if (Build.VERSION.SDK_INT >= 31) {
                store.attachGlassLayer(ensureBlurredLayer())
                store.attachCaptureLayer(null)
            } else {
                store.attachCaptureLayer(source)
                store.attachGlassLayer(null)
            }
        } else {
            releaseLayers()
        }
    }

    private fun ensureBlurredLayer(): GraphicsLayer? {
        blurredLayer?.let { layer ->
            if (blurredEffectRadius != blurRadiusPx) {
                layer.renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                blurredEffectRadius = blurRadiusPx
            }
            return layer
        }
        val layer = createLayer() ?: return null
        layer.renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
        blurredEffectRadius = blurRadiusPx
        blurredLayer = layer
        return layer
    }

    private fun createLayer(): GraphicsLayer? {
        // release 必须归还给创建它的同一 context，故在创建时捕获
        val context = currentValueOf(LocalGraphicsContext)
        graphicsContext = context
        return context.createGraphicsLayer()
    }

    private fun syncCaptureLoop() {
        if (recordContent && Build.VERSION.SDK_INT < 31) {
            if (captureJob?.isActive == true) return
            captureJob = coroutineScope.launch {
                val density = currentValueOf(LocalDensity).density
                androidx.compose.runtime.snapshotFlow {
                    store.isValid && store.captureLayer != null &&
                        VersePersonalization.backgroundBlur > 0 &&
                        VersePersonalization.backgroundOpacity < 100
                }.collectLatest { active ->
                    if (!active) return@collectLatest
                    while (currentCoroutineContext().isActive) {
                        val started = System.nanoTime()
                        store.captureGlassFrame(density)
                        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                        delay((16L - elapsedMs).coerceAtLeast(0L).milliseconds)
                    }
                }
            }
        } else {
            captureJob?.cancel()
            captureJob = null
        }
    }

    private fun releaseLayers() {
        store.attachGlassLayer(null)
        store.attachCaptureLayer(null)
        val context = graphicsContext
        sourceLayer?.let { context?.releaseGraphicsLayer(it) }
        sourceLayer = null
        blurredLayer?.let { context?.releaseGraphicsLayer(it) }
        blurredLayer = null
    }
}

private const val TAG = "BackgroundGlassBlur"

/** 采样快照的最大边长（像素），超出则等比缩放后再做盒式模糊 */
private const val SAMPLING_MAX_DIM = 384f

/**
 * 背景玻璃模糊管线：
 * Android 12- 前景模式下按帧捕获背景内容，降采样后做多轮盒式模糊；
 * 背景模糊模式下从图片文件生成静态模糊位图。
 */
class BackgroundGlassBlur {
    var glassLayer by mutableStateOf<GraphicsLayer?>(null)
        internal set

    var captureLayer by mutableStateOf<GraphicsLayer?>(null)
        internal set

    var blurredBackground by mutableStateOf<ImageBitmap?>(null)
        internal set

    private var sampleFrame: Bitmap? = null
    private var sampleResultA: Bitmap? = null
    private var sampleResultB: Bitmap? = null
    private var sampleResultUseA = false
    private var samplePixels: IntArray? = null
    private var sampleTemp: IntArray? = null

    fun attachGlassLayer(layer: GraphicsLayer?) {
        glassLayer = layer
    }

    fun attachCaptureLayer(layer: GraphicsLayer?) {
        captureLayer = layer
    }

    fun clearBlurredBackground() {
        blurredBackground = null
    }

    suspend fun captureFrame(density: Float) {
        val layer = captureLayer ?: return
        // 层尚未完成首次录制时尺寸为零，跳过本帧
        if (layer.size.width < 1 || layer.size.height < 1) return
        val snapshot = runCatching { layer.toImageBitmap() }
            .onFailure { logSampleFailure(it) }
            .getOrNull() ?: return
        blurredBackground = withContext(Dispatchers.Default) {
            runCatching { downscaleAndBlurSnapshot(snapshot, density) }
                .onFailure { logSampleFailure(it) }
                .getOrNull()
        }
    }

    private var sampleFailureLogged = false

    private fun logSampleFailure(t: Throwable) {
        if (sampleFailureLogged) return
        sampleFailureLogged = true
        Log.w(TAG, "背景毛玻璃采样失败，后续失败帧将被跳过", t)
    }

    suspend fun refreshStaticDisplayBitmap(blur: Int, backgroundFile: File, density: Float) {
        blurredBackground = withContext(Dispatchers.IO) {
            runCatching { regenerateStaticBitmap(blur, backgroundFile, density) }.getOrNull()
        }
    }

    private fun downscaleAndBlurSnapshot(snapshot: ImageBitmap, density: Float): ImageBitmap {
        val androidBitmap = snapshot.asAndroidBitmap()
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            androidBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: androidBitmap
        } else {
            androidBitmap
        }
        val scale = SAMPLING_MAX_DIM / max(source.width, source.height).coerceAtLeast(1)
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val frame = ensureSampleFrame(width, height)
        frame.eraseColor(0)
        val canvas = Canvas(frame)
        canvas.drawBitmap(source, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        return blurSampledFrame(frame, frameRadius(density, scale)).asImageBitmap()
    }

    private suspend fun regenerateStaticBitmap(
        blur: Int,
        backgroundFile: File,
        density: Float
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(backgroundFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val radiusPx = blur * density
        val maxDim = max(bounds.outWidth, bounds.outHeight).toFloat()
        val factor = downscaleFactor(radiusPx, maxDim).toInt().coerceAtLeast(1)

        val bitmap = BitmapFactory.decodeFile(
            backgroundFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = factor }
        ) ?: return@withContext null

        blurBitmap(bitmap, (radiusPx / factor).coerceIn(0.5f, 25f)).asImageBitmap()
    }

    private fun ensureSampleFrame(width: Int, height: Int): Bitmap =
        sampleFrame?.takeIf { it.width == width && it.height == height }
            ?: createBitmap(width, height).also { sampleFrame = it }

    private fun blurSampledFrame(source: Bitmap, radiusPx: Float): Bitmap {
        val width = source.width
        val height = source.height
        val size = width * height
        val radius = radiusPx.roundToInt()
            .coerceIn(1, 25)
            .coerceAtMost(minOf(width, height) - 1)

        val pixels = samplePixels?.takeIf { it.size == size }
            ?: IntArray(size).also { samplePixels = it }
        val temp = sampleTemp?.takeIf { it.size == size }
            ?: IntArray(size).also { sampleTemp = it }
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        if (radius >= 1) {
            repeat(3) {
                boxBlur(pixels, temp, width, height, radius, horizontal = true)
                boxBlur(temp, pixels, width, height, radius, horizontal = false)
            }
        }

        sampleResultUseA = !sampleResultUseA
        val result = (if (sampleResultUseA) sampleResultA else sampleResultB)
            ?.takeIf { it.width == width && it.height == height }
            ?: createBitmap(width, height).also {
                if (sampleResultUseA) sampleResultA = it else sampleResultB = it
            }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun frameRadius(density: Float, scale: Float): Float =
        (VersePersonalization.backgroundBlur * density * scale)
            .coerceIn(0.5f, 25f)

    private fun downscaleFactor(radiusPx: Float, maxDim: Float): Float {
        val target = max(radiusPx / 20f, maxDim / 512f)
        if (target <= 1f) return 1f
        return 2f.pow(log2(target).roundToInt()).coerceIn(1f, 64f)
    }

    private fun blurBitmap(source: Bitmap, radiusPx: Float): Bitmap {
        val width = source.width
        val height = source.height
        val radius = radiusPx.roundToInt()
            .coerceIn(1, 25)
            .coerceAtMost(minOf(width, height) - 1)
        if (radius < 1) return source

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val temp = IntArray(pixels.size)
        repeat(3) {
            boxBlur(pixels, temp, width, height, radius, horizontal = true)
            boxBlur(temp, pixels, width, height, radius, horizontal = false)
        }

        val result = createBitmap(width, height)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun boxBlur(
        src: IntArray,
        dst: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        val window = radius * 2 + 1
        if (horizontal) {
            for (y in 0 until height) {
                val row = y * width
                var sumA = 0
                var sumR = 0
                var sumG = 0
                var sumB = 0
                for (i in -radius..radius) {
                    val p = src[row + i.coerceIn(0, width - 1)]
                    sumA += p ushr 24 and 0xFF
                    sumR += p ushr 16 and 0xFF
                    sumG += p ushr 8 and 0xFF
                    sumB += p and 0xFF
                }
                for (x in 0 until width) {
                    dst[row + x] = (sumA / window shl 24) or
                        (sumR / window shl 16) or
                        (sumG / window shl 8) or
                        (sumB / window)
                    val outP = src[row + (x - radius).coerceIn(0, width - 1)]
                    val inP = src[row + (x + radius + 1).coerceIn(0, width - 1)]
                    sumA += (inP ushr 24 and 0xFF) - (outP ushr 24 and 0xFF)
                    sumR += (inP ushr 16 and 0xFF) - (outP ushr 16 and 0xFF)
                    sumG += (inP ushr 8 and 0xFF) - (outP ushr 8 and 0xFF)
                    sumB += (inP and 0xFF) - (outP and 0xFF)
                }
            }
        } else {
            for (x in 0 until width) {
                var sumA = 0
                var sumR = 0
                var sumG = 0
                var sumB = 0
                for (i in -radius..radius) {
                    val p = src[i.coerceIn(0, height - 1) * width + x]
                    sumA += p ushr 24 and 0xFF
                    sumR += p ushr 16 and 0xFF
                    sumG += p ushr 8 and 0xFF
                    sumB += p and 0xFF
                }
                for (y in 0 until height) {
                    dst[y * width + x] = (sumA / window shl 24) or
                        (sumR / window shl 16) or
                        (sumG / window shl 8) or
                        (sumB / window)
                    val outP = src[(y - radius).coerceIn(0, height - 1) * width + x]
                    val inP = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                    sumA += (inP ushr 24 and 0xFF) - (outP ushr 24 and 0xFF)
                    sumR += (inP ushr 16 and 0xFF) - (outP ushr 16 and 0xFF)
                    sumG += (inP ushr 8 and 0xFF) - (outP ushr 8 and 0xFF)
                    sumB += (inP and 0xFF) - (outP and 0xFF)
                }
            }
        }
    }

    private fun createBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
}
