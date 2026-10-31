package com.verse.explorer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 *
 *  - roseK=5 五叶玫瑰线，粒子沿曲线运动并带拖影
 *  - detailScale 呼吸变化（0.52 ~ 1.0）
 *  - 整体每 28s 缓慢旋转一圈
 */
private object RoseConfig {
    const val rotate = true
    const val particleCount = 86
    const val trailSpan = 0.12f
    const val durationMs = 5400
    const val rotationDurationMs = 28000
    const val pulseDurationMs = 4600
    const val strokeWidth = 4.5f
    const val roseA = 9.2f
    const val roseABoost = 0.6f
    const val roseBreathBase = 0.72f
    const val roseBreathBoost = 0.28f
    const val roseK = 5
    const val roseScale = 3.25f
}

internal fun rosePoint(progress: Float, detailScale: Float): Offset {
    val t = progress * PI.toFloat() * 2f
    val a = RoseConfig.roseA + detailScale * RoseConfig.roseABoost
    val k = RoseConfig.roseK
    val r = a * (RoseConfig.roseBreathBase + detailScale * RoseConfig.roseBreathBoost) * cos(k * t)
    return Offset(
        x = 50f + cos(t) * r * RoseConfig.roseScale,
        y = 50f + sin(t) * r * RoseConfig.roseScale
    )
}

internal fun roseNormalizeProgress(progress: Float): Float {
    return ((progress % 1f) + 1f) % 1f
}

internal fun roseDetailScale(timeMs: Long): Float {
    val pulseProgress = (timeMs % RoseConfig.pulseDurationMs).toFloat() / RoseConfig.pulseDurationMs
    val pulseAngle = pulseProgress * PI.toFloat() * 2f
    return 0.52f + ((sin(pulseAngle + 0.55f) + 1f) / 2f) * 0.48f
}

internal fun roseRotation(timeMs: Long): Float {
    if (!RoseConfig.rotate) return 0f
    return -((timeMs % RoseConfig.rotationDurationMs).toFloat() / RoseConfig.rotationDurationMs) * 360f
}

internal fun roseBuildPath(detailScale: Float, width: Float = 100f, height: Float = 100f): Path {
    val path = Path()
    val steps = 480
    for (i in 0..steps) {
        val p = rosePoint(i.toFloat() / steps, detailScale)
        // 映射到画布实际尺寸：路径与粒子必须走同一套 0~100 → 画布 的换算，
        // 否则画布不是 100px 时灰底（固定 0~100px）和粒子（按尺寸缩放）错位
        val x = p.x / 100f * width
        val y = p.y / 100f * height
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    return path
}

private class RoseParticle(val x: Float, val y: Float, val radius: Float, val opacity: Float)

private fun roseGetParticle(index: Int, progress: Float, detailScale: Float): RoseParticle {
    val tailOffset = index.toFloat() / (RoseConfig.particleCount - 1)
    val p = rosePoint(roseNormalizeProgress(progress - tailOffset * RoseConfig.trailSpan), detailScale)
    val fade = (1.0 - tailOffset.toDouble()).pow(0.56).toFloat()
    return RoseParticle(
        x = p.x,
        y = p.y,
        radius = 0.9f + fade * 2.7f,
        opacity = 0.04f + fade * 0.96f
    )
}

/**
 * 通过 Canvas 在 100x100 逻辑坐标中绘制路径（4.5 宽、10% 透明描边）与 86 个粒子（带拖影）。
 */
@Composable
fun RoseCurveLoader(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    var nowMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        while (true) {
            nowMs = System.currentTimeMillis() - startedAt
            delay(16)
        }
    }

    val strokeWidthPx = 4.5f

    Canvas(modifier = modifier) {
        val time = nowMs
        val progress = (time % RoseConfig.durationMs).toFloat() / RoseConfig.durationMs
        val detailScale = roseDetailScale(time)
        val rotationDeg = roseRotation(time)

        // 整组旋转（绕 50,50 中心，映射到画布实际尺寸）
        val cx = 50f / 100f * size.width
        val cy = 50f / 100f * size.height

        rotate(
            degrees = rotationDeg,
            pivot = Offset(cx, cy)
        ) {
            // 1. 玫瑰曲线路径（弱 10% 透明描边）——与粒子同一套画布映射
            val path = roseBuildPath(detailScale, size.width, size.height)
            drawPath(
                path = path,
                color = color.copy(alpha = 0.1f),
                style = Stroke(width = strokeWidthPx / 100f * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 2. 粒子（带拖影）
            for (i in 0 until RoseConfig.particleCount) {
                val particle = roseGetParticle(i, progress, detailScale)
                drawCircle(
                    color = color.copy(alpha = particle.opacity),
                    radius = particle.radius / 100f * size.width,
                    center = Offset(particle.x / 100f * size.width, particle.y / 100f * size.height)
                )
            }
        }
    }
}