package com.verse.explorer.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.app.ui.theme.VerseColors
import kotlin.math.PI
import kotlin.math.sin

/** 可选的下载进度条动画样式（key 与 SettingsRepository 持久化的字符串一致） */
val ProgressStyleOptions: List<Pair<String, String>> = listOf(
    "linear" to "经典线性",
    "wave" to "波浪",
    "stripe" to "流动斜纹",
    "glow" to "流光",
    "segment" to "分段",
    "dots" to "圆点"
)

/**
 * 下载进度条（统一入口）。
 *
 * progress 为 0f..1f 的确定进度；传 null 表示总量未知，走不确定进度的滑动块。
 * 具体画法由个性化设置 [VersePersonalization.progressStyle] 决定，
 * 项目里所有下载/安装进度都走这里，改设置即全局生效。
 */
@Composable
fun VerseProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = VerseColors.PcAccent,
    trackColor: Color = VerseColors.PcBgActive
) {
    ProgressBarStyled(
        style = VersePersonalization.progressStyle,
        progress = progress,
        modifier = modifier,
        height = height,
        color = color,
        trackColor = trackColor
    )
}

/** 指定样式的进度条（设置页预览用，不受当前设置影响） */
@Composable
fun ProgressBarStyled(
    style: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = VerseColors.PcAccent,
    trackColor: Color = VerseColors.PcBgActive
) {
    val transition = rememberInfiniteTransition(label = "verseProgress")
    // 相位用于波浪流动 / 斜纹平移 / 流光扫过，周期 1.6 秒，线性以保证匀速
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "verseProgressPhase"
    )
    val shape = RoundedCornerShape(height / 2)
    Canvas(modifier = modifier.height(height).clip(shape)) {
        // 轨道
        drawRoundRect(color = trackColor, cornerRadius = CornerRadius(size.height / 2f))
        val p = progress?.coerceIn(0f, 1f)
        if (p == null) {
            drawIndeterminate(color, phase)
        } else {
            drawDeterminate(style, p, phase, color)
        }
    }
}

/** 总量未知：一段滑动的色块从左掠到右 */
private fun DrawScope.drawIndeterminate(color: Color, phase: Float) {
    val w = size.width
    val h = size.height
    val segW = (w * 0.32f).coerceAtLeast(h * 2f)
    val left = ((w + segW) * phase - segW)
    val l = left.coerceAtLeast(0f)
    val r = (left + segW).coerceAtMost(w)
    if (r <= l) return
    drawRoundRect(
        color = color,
        topLeft = Offset(l, 0f),
        size = Size(r - l, h),
        cornerRadius = CornerRadius(h / 2f)
    )
}

private fun DrawScope.drawDeterminate(style: String, progress: Float, phase: Float, color: Color) {
    val w = size.width
    val h = size.height
    val fillW = w * progress
    if (fillW <= 0.5f) return

    when (style) {
        "wave" -> drawWave(fillW, h, phase, color)
        "stripe" -> drawStripe(fillW, h, phase, color)
        "glow" -> drawGlow(fillW, h, phase, color)
        "segment" -> drawSegment(w, h, progress, color)
        "dots" -> drawDots(w, h, progress, phase, color)
        else -> drawRoundRect(
            color = color,
            size = Size(fillW, h),
            cornerRadius = CornerRadius(h / 2f)
        )
    }
}

/** 波浪：填充区内两条相位错开的正弦水面 */
private fun DrawScope.drawWave(fillW: Float, h: Float, phase: Float, color: Color) {
    val amp = (h / 5f).coerceAtLeast(0.8f)
    val midY = h * 0.55f
    fun path(shift: Float): Path {
        val p = Path()
        p.moveTo(0f, h)
        val steps = 40
        for (i in 0..steps) {
            val x = fillW * i / steps
            val angle = (x / h * 1.4f + phase * 2f * PI.toFloat() + shift).toDouble()
            val y = midY + amp * sin(angle).toFloat()
            p.lineTo(x, y)
        }
        p.lineTo(fillW, h)
        p.close()
        return p
    }
    drawPath(path(0f), color)
    drawPath(path((PI / 2).toFloat()), color.copy(alpha = 0.45f))
}

/** 流动斜纹：填充区内的斜向白条纹匀速右移 */
private fun DrawScope.drawStripe(fillW: Float, h: Float, phase: Float, color: Color) {
    drawRoundRect(
        color = color,
        size = Size(fillW, h),
        cornerRadius = CornerRadius(h / 2f)
    )
    // 条纹宽度按 dp 固定（不随高度缩放），6dp 高的细条上也不会糊成一片刻度线
    val stripeW = 7.dp.toPx().coerceAtMost(fillW * 0.5f).coerceAtLeast(3f)
    val period = stripeW * 2.2f
    val shift = phase * period
    clipRect(right = fillW) {
        var x = -period + shift
        while (x < fillW) {
            val p = Path()
            p.moveTo(x, h)
            p.lineTo(x + stripeW, 0f)
            p.lineTo(x + stripeW * 1.75f, 0f)
            p.lineTo(x + stripeW * 0.75f, h)
            p.close()
            drawPath(p, Color.White.copy(alpha = 0.22f))
            x += period
        }
    }
}

/** 流光：填充区上一次高光从左扫到右 */
private fun DrawScope.drawGlow(fillW: Float, h: Float, phase: Float, color: Color) {
    clipRect(right = fillW) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(color.copy(alpha = 0.75f), color),
                startX = 0f,
                endX = fillW
            ),
            size = Size(fillW, h)
        )
        val bandW = (fillW * 0.45f).coerceAtLeast(h * 2.5f)
        val bx = -bandW + (fillW + bandW) * phase
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                startX = bx,
                endX = bx + bandW
            ),
            topLeft = Offset(bx, 0f),
            size = Size(bandW, h)
        )
    }
}

/** 分段：整条等分成 10 块，按进度逐块点亮 */
private fun DrawScope.drawSegment(w: Float, h: Float, progress: Float, color: Color) {
    val count = (w / 20.dp.toPx()).toInt().coerceIn(3, 16)
    val gap = (h * 0.55f).coerceAtLeast(1.2f)
    val segW = ((w - gap * (count - 1)) / count).coerceAtLeast(1f)
    for (i in 0 until count) {
        val lit = (progress * count - i).coerceIn(0f, 1f)
        if (lit <= 0f) continue
        drawRoundRect(
            color = color.copy(alpha = 0.25f + 0.75f * lit),
            topLeft = Offset(i * (segW + gap), 0f),
            size = Size(segW, h),
            cornerRadius = CornerRadius(h / 3f)
        )
    }
}

/** 圆点：一排等距圆点按进度点亮，并带轻微呼吸 */
private fun DrawScope.drawDots(w: Float, h: Float, progress: Float, phase: Float, color: Color) {
    val count = (w / 9.dp.toPx()).toInt().coerceIn(4, 32)
    val r = (h * 0.30f).coerceAtLeast(0.8f)
    val span = w - r * 2
    val step = span / (count - 1)
    for (i in 0 until count) {
        val lit = (progress * count - i).coerceIn(0f, 1f)
        val breath = ((sin((phase + i * 0.07f) * 2 * PI.toDouble()) + 1.0) / 2.0).toFloat()
        val alpha = if (lit > 0f) (0.35f + 0.65f * lit) * (0.75f + 0.25f * breath) else 0f
        if (alpha <= 0.01f) continue
        drawCircle(
            color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
            radius = r,
            center = Offset(r + i * step, h / 2f)
        )
    }
}

/** 设置页预览：进度 5%→100% 循环，动画相位与真实进度条一致 */
@Composable
fun ProgressBarPreview(
    style: String,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "progressPreview")
    val p by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "progressPreviewValue"
    )
    ProgressBarStyled(
        style = style,
        progress = p,
        modifier = modifier,
        height = height
    )
}

/**
 * 个性化设置里的「下载进度条动画」选择器：
 * 右侧常驻当前样式的实时预览，点开展开全部样式，每项右侧同样带预览。
 */
@Composable
fun ProgressStylePicker(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val current = VersePersonalization.progressStyle
    val currentLabel = ProgressStyleOptions.firstOrNull { it.first == current }?.second ?: "经典线性"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "下载进度条动画",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = VerseColors.PcTextPrimary
                )
                Text(
                    text = "当前：$currentLabel",
                    fontSize = 10.sp,
                    color = VerseColors.PcTextMuted
                )
            }
            // 右侧预览：固定占位，避免不同样式尺寸差异导致行高抖动
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(22.dp),
                contentAlignment = Alignment.Center
            ) {
                ProgressBarPreview(style = current, modifier = Modifier.fillMaxWidth(), height = 8.dp)
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (expanded) 180f else 0f),
                tint = VerseColors.PcTextSecondary
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth()) {
                ProgressStyleOptions.forEach { (key, label) ->
                    val selected = key == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { VersePersonalization.applyProgressStyle(key) }
                            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp,
                            color = if (selected) VerseColors.PcAccent else VerseColors.PcTextSecondary
                        )
                        Box(
                            modifier = Modifier
                                .width(88.dp)
                                .height(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ProgressBarPreview(style = key, modifier = Modifier.fillMaxWidth(), height = 7.dp)
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = VerseColors.PcAccent
                            )
                        } else {
                            Spacer(Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
