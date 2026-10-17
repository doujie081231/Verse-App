package com.verse.explorer.app.data.account

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.min
import kotlin.math.roundToInt

/**
 *  - 脸部像素块放大到输出的 0.75，帽子层放大到 0.875（帽子比脸大，完整显示帽子像素块）；
 *  - 先画脸部（居中背景），再按 alpha 叠加帽子层（透明像素露出脸部）；
 *  - 支持高清皮肤（scale = 宽 / 64）。
 */
object SkinHeadCrop {

    /**
     * 裁剪皮肤头部。
     * @param skin 完整皮肤 Bitmap（64x64 或 64x32，可为高清皮肤）
     * @return 头像 Bitmap；无法裁剪时返回原图
     */
    fun cropHead(skin: Bitmap, outputSize: Int = 64): Bitmap {
        val w = skin.width
        val h = skin.height
        if (w < 64 || h < 32) return skin

        val scale = w / 64f
        // 脸 0.75 / 帽 0.875，居中偏移
        val faceSize = (outputSize * 0.75f).roundToInt()
        val hatSize = (outputSize * 0.875f).roundToInt()
        val faceOffset = ((outputSize - faceSize) / 2f).roundToInt()
        val hatOffset = ((outputSize - hatSize) / 2f).roundToInt()

        val sw = (8 * scale).roundToInt()
        val hatLeft = (40 * scale).roundToInt()

        val face = Bitmap.createBitmap(skin, sw, sw, sw, sw)
        val faceScaled = Bitmap.createScaledBitmap(face, faceSize, faceSize, false)
        val out = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(faceScaled, faceOffset.toFloat(), faceOffset.toFloat(), null)

        if (h >= 64 && hatLeft + sw <= w) {
            val hat = Bitmap.createBitmap(skin, hatLeft, sw, sw, sw)
            val hatScaled = Bitmap.createScaledBitmap(hat, hatSize, hatSize, false)
            val hatCanvas = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
            Canvas(hatCanvas).drawBitmap(hatScaled, hatOffset.toFloat(), hatOffset.toFloat(), null)
            return blendOverlay(out, hatCanvas, outputSize)
        }
        return out
    }

    fun isFullSkin(bitmap: Bitmap): Boolean {
        return bitmap.width >= 64 && bitmap.height >= 32
    }

    /**
     * 逐像素将帽子层叠加到脸部层（标准 alpha over 混合）。
     */
    private fun blendOverlay(face: Bitmap, hat: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val facePixels = IntArray(size * size)
        val hatPixels = IntArray(size * size)
        face.getPixels(facePixels, 0, size, 0, 0, size, size)
        hat.getPixels(hatPixels, 0, size, 0, 0, size, size)

        val outPixels = IntArray(size * size)
        for (i in 0 until size * size) {
            val f = facePixels[i]
            val h = hatPixels[i]
            val fr = Color.red(f)
            val fg = Color.green(f)
            val fb = Color.blue(f)
            val fa = Color.alpha(f)
            val hr = Color.red(h)
            val hg = Color.green(h)
            val hb = Color.blue(h)
            val ha = Color.alpha(h)

            var or: Int
            var og: Int
            var ob: Int
            var oa: Int
            if (ha == 0) {
                or = fr; og = fg; ob = fb; oa = fa
            } else if (ha == 255) {
                or = hr; og = hg; ob = hb; oa = 255
            } else if (fa == 0) {
                or = hr; og = hg; ob = hb; oa = ha
            } else {
                val hA = ha / 255f
                val fA = fa / 255f
                val outA = hA + fA * (1 - hA)
                val invOutA = if (outA > 0) 1 / outA else 0f
                or = ((hr * hA + fr * fA * (1 - hA)) * invOutA).toInt()
                og = ((hg * hA + fg * fA * (1 - hA)) * invOutA).toInt()
                ob = ((hb * hA + fb * fA * (1 - hA)) * invOutA).toInt()
                oa = (outA * 255).toInt()
            }
            outPixels[i] = Color.argb(
                oa.coerceIn(0, 255),
                or.coerceIn(0, 255),
                og.coerceIn(0, 255),
                ob.coerceIn(0, 255)
            )
        }
        out.setPixels(outPixels, 0, size, 0, 0, size, size)
        return out
    }
}