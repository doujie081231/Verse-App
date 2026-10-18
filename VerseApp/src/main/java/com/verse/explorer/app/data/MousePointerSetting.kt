package com.verse.explorer.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.verse.explorer.app.R
import java.io.File

/**
 * 鼠标指针外观设置：按指针形状保存自定义图片与热点（百分比坐标）。
 * 图片与热点持久化到 <filesDir>/mouse_pointer/。
 */
object MousePointerSetting {

    /**
     * 指针形状。glfwId 与 GLFW 标准光标编号一致（GLFW_ARROW_CURSOR 等）。
     * 默认热点对齐各形状的“作用点”：箭头/手形在图形尖部，其余居中。
     */
    enum class Shape(
        val glfwId: Int,
        val defaultRes: Int,
        val defaultHotspotX: Int,
        val defaultHotspotY: Int
    ) {
        ARROW(0x00036001, R.drawable.img_mouse_pointer_arrow, 0, 0),
        IBEAM(0x00036002, R.drawable.img_mouse_pointer_ibeam, 50, 50),
        CROSSHAIR(0x00036003, R.drawable.img_mouse_pointer_crosshair, 50, 50),
        HAND(0x00036004, R.drawable.img_mouse_pointer_link, 23, 0),
        RESIZE_EW(0x00036005, R.drawable.img_mouse_pointer_resize_ew, 50, 50),
        RESIZE_NS(0x00036006, R.drawable.img_mouse_pointer_resize_ns, 50, 50),
        RESIZE_ALL(0x00036007, R.drawable.img_mouse_pointer_resize_move, 50, 50),
        NOT_ALLOWED(0x00036008, R.drawable.img_mouse_pointer_not_allowed, 50, 50);

        companion object {
            fun fromGlfw(id: Int): Shape? = entries.firstOrNull { it.glfwId == id }
        }
    }

    private val gson = Gson()
    private var dir: File? = null
    private val hotspots = mutableMapOf<String, IntArray>()

    fun init(filesDir: File) {
        if (dir != null) return
        val d = File(filesDir, "mouse_pointer")
        if (!d.exists()) d.mkdirs()
        dir = d
        loadHotspots()
    }

    private fun hotspotsFile(): File? = dir?.let { File(it, "hotspots.json") }

    private fun loadHotspots() {
        val file = hotspotsFile() ?: return
        if (!file.exists()) return
        runCatching {
            val type = object : TypeToken<Map<String, List<Int>>>() {}.type
            val raw: Map<String, List<Int>> = gson.fromJson(file.readText(), type) ?: return
            raw.forEach { (k, v) -> if (v.size >= 2) hotspots[k] = intArrayOf(v[0], v[1]) }
        }
    }

    private fun saveHotspots() {
        val file = hotspotsFile() ?: return
        runCatching {
            val raw = hotspots.mapValues { it.value.toList() }
            file.writeText(gson.toJson(raw))
        }
    }

    /** 指定形状的自定义图片文件（存在即代表已导入） */
    fun imageFile(shape: Shape): File? = dir?.let { File(it, shape.name.lowercase() + ".image") }

    fun hasImage(shape: Shape): Boolean = imageFile(shape)?.exists() == true

    /** 热点（百分比坐标），未自定义时返回该形状的默认热点 */
    fun hotspot(shape: Shape): IntArray =
        hotspots[shape.name] ?: intArrayOf(shape.defaultHotspotX, shape.defaultHotspotY)

    fun setHotspot(shape: Shape, xPercent: Int, yPercent: Int) {
        hotspots[shape.name] = intArrayOf(xPercent.coerceIn(0, 100), yPercent.coerceIn(0, 100))
        saveHotspots()
    }

    /** 导入自定义指针图片 */
    fun importImage(context: Context, shape: Shape, uri: Uri): Boolean {
        val file = imageFile(shape) ?: return false
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            // 校验为可用图片
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                file.delete()
                return false
            }
            true
        }.getOrDefault(false)
    }

    /** 重置回内置默认指针图片 */
    fun resetImage(shape: Shape) {
        runCatching { imageFile(shape)?.delete() }
    }

    /** 读取绘制用位图：已导入自定义图片时返回该位图，否则返回 null（调用方用内置资源） */
    fun loadBitmap(shape: Shape): Bitmap? {
        val file = imageFile(shape) ?: return null
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }
}
