package com.verse.explorer.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.verse.explorer.core.data.SettingsRepository
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

    /** 背景透明度 0..100（0=全透 100=不透明），背景有效时作用于内容卡片容器色的不透明度 */
    var backgroundOpacity by mutableStateOf(95)
        private set

    /** 背景模糊半径 0..40（单位 Dp），0 表示关闭模糊 */
    var backgroundBlur by mutableStateOf(0)
        private set

    /** 背景模糊类型：background=整体背景模糊 / foreground=前景毛玻璃（卡片下绘制模糊切片） */
    var backgroundBlurType by mutableStateOf("background")
        private set

    /** 视频背景音量 0..100 */
    var videoVolume by mutableStateOf(0)
        private set

    /** 自定义背景是否有效（风格已选且对应文件存在），驱动模糊效果与卡片颜色适配的开关 */
    val backgroundValid: Boolean
        get() = (backgroundStyle == "image" && backgroundImagePath != null) ||
            (backgroundStyle == "video" && backgroundVideoPath != null)

    /** 加载动画样式：rose / linear / wave / dots / ring */
    var loaderStyle by mutableStateOf("rose")
        private set

    /** 下载进度条动画样式：linear / wave / stripe / glow / segment / dots（默认经典线性） */
    var progressStyle by mutableStateOf("linear")
        private set

    /** 页面切换动画样式：material / slide / fade / scale / sharedAxisY / none */
    var pageAnimStyle by mutableStateOf("material")
        private set

    /** 页面切换速度倍率（1=标准，>1 更快） */
    var pageAnimSpeed by mutableStateOf(1f)
        private set

    /** 侧边栏液态玻璃效果（Android 13+ 生效，默认关：实时着色器采样在低端机掉帧明显，可在设置开启） */
    var sidebarGlass by mutableStateOf(false)
        private set

    /** 侧边栏按钮是否在图标下显示功能名（默认开） */
    var sidebarLabels by mutableStateOf(true)
        private set

    // ==================== 颜色主题与外观（改即存）====================

    /** 颜色主题选择（ColorThemeType 枚举名，默认浅色沿用黑白主题历史行为） */
    var colorTheme by mutableStateOf("LIGHT")
        private set

    /** 自定义取色种子颜色（ARGB 整数） */
    var themeCustomColor by mutableStateOf(0xFF0000FF.toInt())
        private set

    /** 自定义取色配色风格（PaletteStyle 名称） */
    var paletteStyle by mutableStateOf("TonalSpot")
        private set

    /** 主题明暗三态（DarkMode 枚举名，默认跟随系统） */
    var darkMode by mutableStateOf("FOLLOW_SYSTEM")
        private set

    /** 主界面操作菜单停靠侧（ActionMenuSide 枚举名，默认 START=左） */
    var actionMenuSide by mutableStateOf("START")
        private set

    /** 启动器部分屏幕全屏（默认开=沉浸式全屏并延伸到刘海区域） */
    var launcherFullScreen by mutableStateOf(true)
        private set

    /** 启动/重置时从持久化加载（文件已删除则置 null） */
    fun load(context: Context) {
        backgroundStyle = SettingsRepository.getBackgroundStyle()
        backgroundOpacity = SettingsRepository.getBackgroundOpacity().coerceIn(0, 100)
        backgroundBlur = SettingsRepository.getBackgroundBlur().coerceIn(0, 40)
        backgroundBlurType = SettingsRepository.getBackgroundBlurType()
            .takeIf { it == "background" || it == "foreground" } ?: "background"
        videoVolume = SettingsRepository.getVideoVolume().coerceIn(0, 100)
        loaderStyle = SettingsRepository.getLoaderStyle()
        progressStyle = SettingsRepository.getProgressStyle()
        pageAnimStyle = SettingsRepository.getPageAnimStyle()
        pageAnimSpeed = SettingsRepository.getPageAnimSpeed()
        sidebarGlass = SettingsRepository.getSidebarGlass()
        sidebarLabels = SettingsRepository.getSidebarLabels()
        colorTheme = SettingsRepository.getThemeSelection()
        themeCustomColor = SettingsRepository.getThemeCustomColor()
        paletteStyle = SettingsRepository.getThemePaletteStyle()
        darkMode = SettingsRepository.getDarkMode()
        actionMenuSide = SettingsRepository.getActionMenuSide()
        launcherFullScreen = SettingsRepository.getLauncherFullScreen()
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

    fun applyBackgroundBlur(blur: Int) {
        backgroundBlur = blur.coerceIn(0, 40)
        SettingsRepository.setBackgroundBlur(backgroundBlur)
    }

    fun applyBackgroundBlurType(type: String) {
        backgroundBlurType = if (type == "foreground") "foreground" else "background"
        SettingsRepository.setBackgroundBlurType(backgroundBlurType)
    }

    fun applyVideoVolume(volume: Int) {
        videoVolume = volume.coerceIn(0, 100)
        SettingsRepository.setVideoVolume(videoVolume)
    }

    fun applyLoaderStyle(style: String) {
        loaderStyle = style
        SettingsRepository.setLoaderStyle(style)
    }

    /** 下载进度条动画样式（改即存，进度条在绘制时读取） */
    fun applyProgressStyle(style: String) {
        progressStyle = style
        SettingsRepository.setProgressStyle(style)
    }

    /** 页面切换动画样式（下一次导航生效） */
    fun applyPageAnimStyle(style: String) {
        pageAnimStyle = style
        SettingsRepository.setPageAnimStyle(style)
    }

    /** 页面切换速度倍率（下一次导航生效） */
    fun applyPageAnimSpeed(speed: Float) {
        pageAnimSpeed = speed.coerceIn(0.5f, 2f)
        SettingsRepository.setPageAnimSpeed(pageAnimSpeed)
    }

    fun applySidebarGlass(enabled: Boolean) {
        sidebarGlass = enabled
        SettingsRepository.setSidebarGlass(enabled)
    }

    fun applySidebarLabels(enabled: Boolean) {
        sidebarLabels = enabled
        SettingsRepository.setSidebarLabels(enabled)
    }

    /** 应用颜色主题选择（改即存，主题层组合期间读取并组装配色方案） */
    fun applyColorTheme(selection: String) {
        colorTheme = selection
        SettingsRepository.setThemeSelection(selection)
    }

    /** 应用自定义取色种子颜色（改即存） */
    fun applyThemeCustomColor(argb: Int) {
        themeCustomColor = argb
        SettingsRepository.setThemeCustomColor(argb)
    }

    /** 应用自定义取色配色风格（改即存） */
    fun applyPaletteStyle(style: String) {
        paletteStyle = style
        SettingsRepository.setThemePaletteStyle(style)
    }

    /** 应用主题明暗三态（改即存，深浅由主题层解析） */
    fun applyDarkMode(mode: String) {
        darkMode = mode
        SettingsRepository.setDarkMode(mode)
    }

    /** 应用主界面操作菜单停靠侧（改即存） */
    fun applyActionMenuSide(side: String) {
        actionMenuSide = side
        SettingsRepository.setActionMenuSide(side)
    }

    /** 应用启动器部分屏幕全屏开关（改即存，MainActivity 观察后即时生效） */
    fun applyLauncherFullScreen(fullScreen: Boolean) {
        launcherFullScreen = fullScreen
        SettingsRepository.setLauncherFullScreen(fullScreen)
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
