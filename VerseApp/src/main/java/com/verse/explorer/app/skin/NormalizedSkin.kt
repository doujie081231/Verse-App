package com.verse.explorer.app.skin

import android.graphics.Bitmap

/**
 * Describes a Minecraft 1.8+ skin (64x64).
 * Old format skins are converted to the new format.
 */
class NormalizedSkin(texture: Bitmap) {

    private val texture: Bitmap
    private val normalizedTexture: Bitmap
    private val scale: Int
    private val oldFormat: Boolean

    init {
        this.texture = texture

        // check format
        val w = texture.width
        val h = texture.height
        if (w % 64 != 0) {
            throw InvalidSkinException("Invalid size $w x $h")
        }
        oldFormat = when {
            w == h -> false
            w == h * 2 -> true
            else -> throw InvalidSkinException("Invalid size $w x $h")
        }

        // compute scale
        scale = w / 64

        normalizedTexture = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
        copyImage(texture, normalizedTexture, 0, 0, 0, 0, w, h, false)
        if (oldFormat) {
            convertOldSkin()
        }
    }

    private fun convertOldSkin() {
        copyImageRelative(4, 16, 20, 48, 4, 4, true) // Top Leg
        copyImageRelative(8, 16, 24, 48, 4, 4, true) // Bottom Leg
        copyImageRelative(0, 20, 24, 52, 4, 12, true) // Outer Leg
        copyImageRelative(4, 20, 20, 52, 4, 12, true) // Front Leg
        copyImageRelative(8, 20, 16, 52, 4, 12, true) // Inner Leg
        copyImageRelative(12, 20, 28, 52, 4, 12, true) // Back Leg
        copyImageRelative(44, 16, 36, 48, 4, 4, true) // Top Arm
        copyImageRelative(48, 16, 40, 48, 4, 4, true) // Bottom Arm
        copyImageRelative(40, 20, 40, 52, 4, 12, true) // Outer Arm
        copyImageRelative(44, 20, 36, 52, 4, 12, true) // Front Arm
        copyImageRelative(48, 20, 32, 52, 4, 12, true) // Inner Arm
        copyImageRelative(52, 20, 44, 52, 4, 12, true) // Back Arm
    }

    private fun copyImageRelative(sx: Int, sy: Int, dx: Int, dy: Int, w: Int, h: Int, flipHorizontal: Boolean) {
        copyImage(normalizedTexture, normalizedTexture, sx * scale, sy * scale, dx * scale, dy * scale, w * scale, h * scale, flipHorizontal)
    }

    fun getOriginalTexture(): Bitmap {
        return texture
    }

    fun getNormalizedTexture(): Bitmap {
        return normalizedTexture
    }

    fun getScale(): Int {
        return scale
    }

    fun isOldFormat(): Boolean {
        return oldFormat
    }

    fun isSlim(): Boolean {
        return (hasTransparencyRelative(50, 16, 2, 4) ||
                hasTransparencyRelative(54, 20, 2, 12) ||
                hasTransparencyRelative(42, 48, 2, 4) ||
                hasTransparencyRelative(46, 52, 2, 12)) ||
                (isAreaBlackRelative(50, 16, 2, 4) &&
                        isAreaBlackRelative(54, 20, 2, 12) &&
                        isAreaBlackRelative(42, 48, 2, 4) &&
                        isAreaBlackRelative(46, 52, 2, 12))
    }

    private fun hasTransparencyRelative(x0In: Int, y0In: Int, wIn: Int, hIn: Int): Boolean {
        var x0 = x0In * scale
        var y0 = y0In * scale
        var w = wIn * scale
        var h = hIn * scale
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = normalizedTexture.getPixel(x0 + x, y0 + y)
                if (pixel ushr 24 != 0xff) {
                    return true
                }
            }
        }
        return false
    }

    private fun isAreaBlackRelative(x0In: Int, y0In: Int, wIn: Int, hIn: Int): Boolean {
        var x0 = x0In * scale
        var y0 = y0In * scale
        var w = wIn * scale
        var h = hIn * scale
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = normalizedTexture.getPixel(x0 + x, y0 + y).toLong()
                if (pixel != 0xff000000L) {
                    return false
                }
            }
        }
        return true
    }

    companion object {
        private fun copyImage(src: Bitmap, dst: Bitmap, sx: Int, sy: Int, dx: Int, dy: Int, w: Int, h: Int, flipHorizontal: Boolean) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val pixel = src.getPixel(sx + x, sy + y)
                    dst.setPixel(dx + (if (flipHorizontal) w - x - 1 else x), dy + y, pixel)
                }
            }
        }
    }
}