package com.verse.explorer.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * 个性化响应式单例：自定义背景（图片/视频）与背景透明度。
 * 状态用 mutableStateOf 承载，Compose 读取处（AppBackground/SettingsScreen）随变化自动重组；
 * 变更同时持久化到 SettingsRepository（SharedPreferences）。
 */
object VersePersonalization {

    /** 背景风格：none / image / video */
    var backgroundStyle by mutableStateOf("none")
        private set

    /** 已导入的背景图片绝对路径（文件不存在时为 null） */
    var backgroundImagePath by mutableStateOf<String?>(null)
        private set

    /** 已导入的背景视频绝对路径（文件不存在时为 null） */
    var backgroundVideoPath by mutableStateOf<String?>(null)
        private set

    /** 背景透明度 0..100（0=全透 100=不透明） */
    var backgroundOpacity by mutableStateOf(95)
        private set

    /** 侧边栏液态玻璃效果（Android 13+ 生效，默认开） */
    var sidebarGlass by mutableStateOf(true)
        private set

    /** 启动/重置时从持久化加载（文件已删除则置 null） */
    fun load(context: Context) {
        backgroundStyle = SettingsRepository.getBackgroundStyle()
        backgroundOpacity = SettingsRepository.getBackgroundOpacity().coerceIn(0, 100)
        sidebarGlass = SettingsRepository.getSidebarGlass()
        backgroundImagePath = SettingsRepository.getBackgroundImagePath()
            .takeIf { it.isNotEmpty() && File(it).exists() }
        backgroundVideoPath = SettingsRepository.getBackgroundVideoPath()
            .takeIf { it.isNotEmpty() && File(it).exists() }
        if (backgroundStyle == "image" && backgroundImagePath == null) {
            backgroundStyle = "none"
            SettingsRepository.setBackgroundStyle("none")
        }
        if (backgroundStyle == "video" && backgroundVideoPath == null) {
            backgroundStyle = "none"
            SettingsRepository.setBackgroundStyle("none")
        }
    }

    fun setStyle(style: String) {
        backgroundStyle = style
        SettingsRepository.setBackgroundStyle(style)
    }

    fun setOpacity(opacity: Int) {
        backgroundOpacity = opacity.coerceIn(0, 100)
        SettingsRepository.setBackgroundOpacity(backgroundOpacity)
    }

    fun applySidebarGlass(enabled: Boolean) {
        sidebarGlass = enabled
        SettingsRepository.setSidebarGlass(enabled)
    }

    /** 从 SAF content uri 导入图片到内部目录，成功返回 true 并置为图片背景 */
    fun importImage(context: Context, uri: Uri): Boolean {
        val dir = File(context.filesDir, "background").apply { mkdirs() }
        val ext = extensionOf(context, uri, fallback = "jpg")
        return copyToInternal(context, uri, File(dir, "background_image.$ext")).also { ok ->
            if (ok) {
                val path = File(dir, "background_image.$ext").absolutePath
                backgroundImagePath = path
                SettingsRepository.setBackgroundImagePath(path)
                setStyle("image")
            }
        }
    }

    /** 从 SAF content uri 导入视频到内部目录，成功返回 true 并置为视频背景 */
    fun importVideo(context: Context, uri: Uri): Boolean {
        val dir = File(context.filesDir, "background").apply { mkdirs() }
        val ext = extensionOf(context, uri, fallback = "mp4")
        return copyToInternal(context, uri, File(dir, "background_video.$ext")).also { ok ->
            if (ok) {
                val path = File(dir, "background_video.$ext").absolutePath
                backgroundVideoPath = path
                SettingsRepository.setBackgroundVideoPath(path)
                setStyle("video")
            }
        }
    }

    /** 移除指定类型的背景（"image"/"video"）及其文件 */
    fun clear(context: Context, kind: String) {
        val dir = File(context.filesDir, "background")
        when (kind) {
            "image" -> {
                backgroundImagePath?.let { runCatching { File(it).delete() } }
                backgroundImagePath = null
                SettingsRepository.setBackgroundImagePath("")
                if (backgroundStyle == "image") {
                    backgroundStyle = "none"
                    SettingsRepository.setBackgroundStyle("none")
                }
            }
            "video" -> {
                backgroundVideoPath?.let { runCatching { File(it).delete() } }
                backgroundVideoPath = null
                SettingsRepository.setBackgroundVideoPath("")
                if (backgroundStyle == "video") {
                    backgroundStyle = "none"
                    SettingsRepository.setBackgroundStyle("none")
                }
            }
        }
    }

    /** 从 content uri 推导扩展名（优先 DISPLAY_NAME，兜底按 fallback） */
    private fun extensionOf(context: Context, uri: Uri, fallback: String): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) {
                            val dot = name.lastIndexOf('.')
                            if (dot in 0 until name.length - 1) {
                                val ext = name.substring(dot + 1).lowercase()
                                    .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                                if (!ext.isNullOrEmpty()) {
                                    return ext
                                }
                            }
                        }
                    }
                }
        }
        return fallback
    }

    /** 复制 content uri 到内部目标文件（先删旧文件），成功返回 true */
    private fun copyToInternal(context: Context, uri: Uri, target: File): Boolean {
        return runCatching {
            target.delete()
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            target.length() > 0
        }.getOrDefault(false)
    }
}
