package com.verse.explorer.app.data

import android.content.Context
import android.content.SharedPreferences

object SettingsRepository {

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun prefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("SettingsRepository 未初始化，请先调用 init(context)")
    }

    private const val PREFS_NAME = "verse_explorer_settings"


        private const val KEY_GAME_DIR = "game_dir"
        private const val KEY_MAX_MEMORY = "max_memory"
        private const val KEY_MIN_MEMORY = "min_memory"
        private const val KEY_SHOW_VIRTUAL_MOUSE = "show_virtual_mouse"
        private const val KEY_VIRTUAL_KEY_OPACITY = "virtual_key_opacity"
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SHOW_FPS = "show_fps"
        private const val KEY_AUTO_SET_CHINESE = "auto_set_chinese"
        private const val KEY_FORCE_LANDSCAPE = "force_landscape"

        private const val KEY_DOWNLOAD_SOURCE = "download_source"
        private const val KEY_VERSION_SOURCE = "version_source"
        private const val KEY_MAX_THREADS = "max_threads"
        private const val KEY_MAX_CHUNKS_PER_FILE = "max_chunks_per_file"
        private const val KEY_MIN_FILE_SIZE_FOR_CHUNKING = "min_file_size_for_chunking"
        private const val KEY_SPEED_LIMIT = "speed_limit"
        private const val KEY_ENABLE_MULTI_THREAD = "enable_multi_thread"
        private const val KEY_ENABLE_RESUME = "enable_resume"
        private const val KEY_TARGET_FOLDER = "target_folder"
        private const val KEY_JAVA_VERSION = "java_version"
        private const val KEY_COMMUNITY_SOURCE = "community_source"
        private const val KEY_FILE_NAME_FORMAT = "file_name_format"
        private const val KEY_MOD_STYLE = "mod_style"
        private const val KEY_IGNORE_QUILT = "ignore_quilt"
        private const val KEY_SHOW_ICONS = "show_icons"
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val KEY_BACKGROUND_MUSIC = "background_music"
        private const val KEY_SOUND_EFFECTS = "sound_effects"
        private const val KEY_VIBRATION = "vibration"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_LOG_LEVEL = "log_level"
        private const val KEY_DEBUG_MODE = "debug_mode"

        private const val KEY_GC_TYPE = "gc_type"
        private const val KEY_CUSTOM_JVM_ARGS = "custom_jvm_args"
        private const val KEY_RENDERER = "renderer"
        private const val KEY_LAUNCH_METHOD = "launch_method"
        private const val KEY_BE_GESTURE = "be_gesture"
        private const val KEY_VK_DRIVER_SYSTEM = "vk_driver_system"

        private const val KEY_VERIFY_SSL = "verify_ssl"
        private const val KEY_COMMUNITY_RESOURCES = "community_resources"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_BACKGROUND_STYLE = "background_style"
        private const val KEY_THEME_ACCENT = "theme_accent"
        private const val KEY_BACKGROUND_IMAGE = "background_image_path"
        private const val KEY_BACKGROUND_VIDEO = "background_video_path"
        private const val KEY_WINDOW_TITLE = "window_title"
        private const val KEY_CUSTOM_INFO = "custom_info"
        private const val KEY_LAUNCHER_VISIBILITY = "launcher_visibility"
        private const val KEY_VERSION_ISOLATION = "version_isolation"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_BACKGROUND_OPACITY = "background_opacity"
        private const val KEY_GAME_WIDTH = "game_width"
        private const val KEY_GAME_HEIGHT = "game_height"
        private const val KEY_FULLSCREEN = "fullscreen"
        private const val KEY_IGNORE_NOTCH = "ignore_notch"

        private const val DEFAULT_GAME_DIR = "/storage/emulated/0/VerseExplorerX"
        private const val DEFAULT_MAX_MEMORY = 2048
        private const val DEFAULT_MIN_MEMORY = 512
        private const val DEFAULT_SHOW_VIRTUAL_MOUSE = true
        private const val DEFAULT_VIRTUAL_KEY_OPACITY = 0.7f
        private const val DEFAULT_THEME = "浅色"
        private const val DEFAULT_LANGUAGE = "简体中文"
        private const val DEFAULT_KEEP_SCREEN_ON = true
        private const val DEFAULT_SHOW_FPS = false
        private const val DEFAULT_AUTO_SET_CHINESE = true
        private const val DEFAULT_FORCE_LANDSCAPE = true

        private const val DEFAULT_DOWNLOAD_SOURCE = "bmclapi"
        private const val DEFAULT_VERSION_SOURCE = "bmclapi"
        private const val DEFAULT_MAX_THREADS = 32
        private const val DEFAULT_MAX_CHUNKS_PER_FILE = 4
        private const val DEFAULT_MIN_FILE_SIZE_FOR_CHUNKING = 5242880L
        private const val DEFAULT_SPEED_LIMIT = 0
        private const val DEFAULT_ENABLE_MULTI_THREAD = true
        private const val DEFAULT_ENABLE_RESUME = true
        private const val DEFAULT_TARGET_FOLDER = ""
        private const val DEFAULT_JAVA_VERSION = "17"
        private const val DEFAULT_COMMUNITY_SOURCE = "bmclapi"
        private const val DEFAULT_FILE_NAME_FORMAT = "[机械动力] create-1.21.1-6.0.4"
        private const val DEFAULT_MOD_STYLE = "标题显示译名，详情显示文件名"
        private const val DEFAULT_IGNORE_QUILT = true
        private const val DEFAULT_SHOW_ICONS = true
        private const val DEFAULT_ICON_STYLE = "彩色图标"
        private const val DEFAULT_AUTO_UPDATE = true
        private const val DEFAULT_BACKGROUND_MUSIC = true
        private const val DEFAULT_SOUND_EFFECTS = true
        private const val DEFAULT_VIBRATION = true
        private const val DEFAULT_NOTIFICATIONS = true
        private const val DEFAULT_LOG_LEVEL = "INFO"
        private const val DEFAULT_DEBUG_MODE = false

        private const val DEFAULT_GC_TYPE = "G1GC"
        private const val DEFAULT_CUSTOM_JVM_ARGS = ""
        private const val DEFAULT_RENDERER = "nggl4es"
        private const val DEFAULT_LAUNCH_METHOD = "pojav"
        private const val DEFAULT_BE_GESTURE = true
        private const val DEFAULT_VK_DRIVER_SYSTEM = false

        private const val DEFAULT_VERIFY_SSL = true
        private const val DEFAULT_COMMUNITY_RESOURCES = true
        private const val DEFAULT_THEME_COLOR = "white"
        private const val DEFAULT_WINDOW_TITLE = ""
        private const val DEFAULT_CUSTOM_INFO = ""
        private const val DEFAULT_LAUNCHER_VISIBILITY = "keep"
        private const val DEFAULT_VERSION_ISOLATION = "all"
        private const val DEFAULT_FONT_SIZE = 14
        private const val DEFAULT_BACKGROUND_OPACITY = 95
        private const val DEFAULT_GAME_WIDTH = 854
        private const val DEFAULT_GAME_HEIGHT = 480
        private const val DEFAULT_FULLSCREEN = false
        // FCL ThemePreference.fullscreen 默认 false：默认避让屏幕刘海区域（缩小）
        private const val DEFAULT_IGNORE_NOTCH = false
        // VerseX 渲染器（vkshim 兼容层之上的性能优化层）：pipeline 对象级缓存（M1）
        private const val KEY_VERSERX_RENDERER = "versex_renderer"
        private const val KEY_VERSERX_PIPELINE_CACHE = "versex_pipeline_cache"
        private const val DEFAULT_VERSERX_RENDERER = true
        private const val DEFAULT_VERSERX_PIPELINE_CACHE = true

    fun getVersexRenderer(): Boolean = prefs().getBoolean(KEY_VERSERX_RENDERER, DEFAULT_VERSERX_RENDERER)

    fun setVersexRenderer(on: Boolean) {
        prefs().edit().putBoolean(KEY_VERSERX_RENDERER, on).apply()
    }

    fun getVersexPipelineCache(): Boolean = prefs().getBoolean(KEY_VERSERX_PIPELINE_CACHE, DEFAULT_VERSERX_PIPELINE_CACHE)

    fun setVersexPipelineCache(on: Boolean) {
        prefs().edit().putBoolean(KEY_VERSERX_PIPELINE_CACHE, on).apply()
    }

    // 图形 API（FCL VersionSetting.graphicsBackend：default/opengl/vulkan，
    // 启动时写 MC options.txt 的 preferredGraphicsBackend）
    private const val KEY_GRAPHICS_BACKEND = "graphics_backend"
    private const val DEFAULT_GRAPHICS_BACKEND = "default"

    fun getGraphicsBackend(): String =
        prefs().getString(KEY_GRAPHICS_BACKEND, DEFAULT_GRAPHICS_BACKEND) ?: DEFAULT_GRAPHICS_BACKEND

    fun setGraphicsBackend(value: String) {
        prefs().edit().putString(KEY_GRAPHICS_BACKEND, value).apply()
    }

    // FCL LauncherHelper isNotCheckGame：跳过启动前游戏文件完整性检查以加快启动
    private const val KEY_NOT_CHECK_GAME = "not_check_game"
    private const val DEFAULT_NOT_CHECK_GAME = false

    fun getNotCheckGame(): Boolean = prefs().getBoolean(KEY_NOT_CHECK_GAME, DEFAULT_NOT_CHECK_GAME)

    fun setNotCheckGame(on: Boolean) {
        prefs().edit().putBoolean(KEY_NOT_CHECK_GAME, on).apply()
    }

    // FCL LaunchOptions.debugLog：游戏内 log4j2 输出详细调试日志（log4j2-1.12-debug.xml）
    private const val KEY_DEBUG_LOG = "debug_log"
    private const val DEFAULT_DEBUG_LOG = false

    fun getDebugLog(): Boolean = prefs().getBoolean(KEY_DEBUG_LOG, DEFAULT_DEBUG_LOG)

    fun setDebugLog(on: Boolean) {
        prefs().edit().putBoolean(KEY_DEBUG_LOG, on).apply()
    }

    // FCL LauncherSettingPage：启动游戏前请求 RECORD_AUDIO（语音聊天模组用）
    private const val KEY_REQUEST_MIC_PERMISSION = "request_mic_permission"
    private const val DEFAULT_REQUEST_MIC_PERMISSION = false

    fun getRequestMicPermission(): Boolean =
        prefs().getBoolean(KEY_REQUEST_MIC_PERMISSION, DEFAULT_REQUEST_MIC_PERMISSION)

    fun setRequestMicPermission(on: Boolean) {
        prefs().edit().putBoolean(KEY_REQUEST_MIC_PERMISSION, on).apply()
    }

    // Vulkan 驱动器（FCL VersionSetting.isVKDriverSystem）：true=系统驱动，
    // false=Turnip（内置 libvulkan_freedreno.so，仅 Adreno 6xx-8xx 有效，其余设备自动回落）
    // setter 见 setVkDriverSystem(enabled)

    fun getGameDir(): String = prefs().getString(KEY_GAME_DIR, DEFAULT_GAME_DIR) ?: DEFAULT_GAME_DIR

    fun setGameDir(dir: String) {
        prefs().edit().putString(KEY_GAME_DIR, dir).apply()
    }

    fun getMaxMemory(): Int = prefs().getInt(KEY_MAX_MEMORY, DEFAULT_MAX_MEMORY)

    fun setMaxMemory(memory: Int) {
        prefs().edit().putInt(KEY_MAX_MEMORY, memory).apply()
    }

    fun getMinMemory(): Int = prefs().getInt(KEY_MIN_MEMORY, DEFAULT_MIN_MEMORY)

    fun setMinMemory(memory: Int) {
        prefs().edit().putInt(KEY_MIN_MEMORY, memory).apply()
    }

    fun getShowVirtualMouse(): Boolean = prefs().getBoolean(KEY_SHOW_VIRTUAL_MOUSE, DEFAULT_SHOW_VIRTUAL_MOUSE)

    fun setShowVirtualMouse(show: Boolean) {
        prefs().edit().putBoolean(KEY_SHOW_VIRTUAL_MOUSE, show).apply()
    }

    fun getVirtualKeyOpacity(): Float = prefs().getFloat(KEY_VIRTUAL_KEY_OPACITY, DEFAULT_VIRTUAL_KEY_OPACITY)

    fun setVirtualKeyOpacity(opacity: Float) {
        prefs().edit().putFloat(KEY_VIRTUAL_KEY_OPACITY, opacity).apply()
    }

    fun getTheme(): String = prefs().getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME

    fun setTheme(theme: String) {
        prefs().edit().putString(KEY_THEME, theme).apply()
    }

    fun getLanguage(): String = prefs().getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    fun setLanguage(language: String) {
        prefs().edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getKeepScreenOn(): Boolean = prefs().getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON)

    fun setKeepScreenOn(keep: Boolean) {
        prefs().edit().putBoolean(KEY_KEEP_SCREEN_ON, keep).apply()
    }

    fun getShowFps(): Boolean = prefs().getBoolean(KEY_SHOW_FPS, DEFAULT_SHOW_FPS)

    fun setShowFps(show: Boolean) {
        prefs().edit().putBoolean(KEY_SHOW_FPS, show).apply()
    }

    fun isAutoSetChinese(): Boolean = prefs().getBoolean(KEY_AUTO_SET_CHINESE, DEFAULT_AUTO_SET_CHINESE)

    fun setAutoSetChinese(on: Boolean) {
        prefs().edit().putBoolean(KEY_AUTO_SET_CHINESE, on).apply()
    }

    fun getForceLandscape(): Boolean = prefs().getBoolean(KEY_FORCE_LANDSCAPE, DEFAULT_FORCE_LANDSCAPE)

    fun setForceLandscape(force: Boolean) {
        prefs().edit().putBoolean(KEY_FORCE_LANDSCAPE, force).apply()
    }

    fun getDownloadSource(): String = prefs().getString(KEY_DOWNLOAD_SOURCE, DEFAULT_DOWNLOAD_SOURCE) ?: DEFAULT_DOWNLOAD_SOURCE

    fun setDownloadSource(source: String) {
        prefs().edit().putString(KEY_DOWNLOAD_SOURCE, source).apply()
    }

    fun getVersionSource(): String = prefs().getString(KEY_VERSION_SOURCE, DEFAULT_VERSION_SOURCE) ?: DEFAULT_VERSION_SOURCE

    fun setVersionSource(source: String) {
        prefs().edit().putString(KEY_VERSION_SOURCE, source).apply()
    }

    fun getMaxThreads(): Int = prefs().getInt(KEY_MAX_THREADS, DEFAULT_MAX_THREADS)

    fun setMaxThreads(threads: Int) {
        prefs().edit().putInt(KEY_MAX_THREADS, threads).apply()
    }

    fun getMaxChunksPerFile(): Int = prefs().getInt(KEY_MAX_CHUNKS_PER_FILE, DEFAULT_MAX_CHUNKS_PER_FILE)

    fun setMaxChunksPerFile(chunks: Int) {
        prefs().edit().putInt(KEY_MAX_CHUNKS_PER_FILE, chunks).apply()
    }

    fun getMinFileSizeForChunking(): Long = prefs().getLong(KEY_MIN_FILE_SIZE_FOR_CHUNKING, DEFAULT_MIN_FILE_SIZE_FOR_CHUNKING)

    fun setMinFileSizeForChunking(size: Long) {
        prefs().edit().putLong(KEY_MIN_FILE_SIZE_FOR_CHUNKING, size).apply()
    }

    fun getSpeedLimit(): Int = prefs().getInt(KEY_SPEED_LIMIT, DEFAULT_SPEED_LIMIT)

    fun setSpeedLimit(limit: Int) {
        prefs().edit().putInt(KEY_SPEED_LIMIT, limit).apply()
    }

    fun getEnableMultiThread(): Boolean = prefs().getBoolean(KEY_ENABLE_MULTI_THREAD, DEFAULT_ENABLE_MULTI_THREAD)

    fun setEnableMultiThread(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_ENABLE_MULTI_THREAD, enabled).apply()
    }

    fun getEnableResume(): Boolean = prefs().getBoolean(KEY_ENABLE_RESUME, DEFAULT_ENABLE_RESUME)

    fun setEnableResume(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_ENABLE_RESUME, enabled).apply()
    }

    fun getTargetFolder(): String = prefs().getString(KEY_TARGET_FOLDER, DEFAULT_TARGET_FOLDER) ?: DEFAULT_TARGET_FOLDER

    fun setTargetFolder(folder: String) {
        prefs().edit().putString(KEY_TARGET_FOLDER, folder).apply()
    }

    fun getJavaVersion(): String = prefs().getString(KEY_JAVA_VERSION, DEFAULT_JAVA_VERSION) ?: DEFAULT_JAVA_VERSION

    fun setJavaVersion(version: String) {
        prefs().edit().putString(KEY_JAVA_VERSION, version).apply()
    }

    fun getCommunitySource(): String = prefs().getString(KEY_COMMUNITY_SOURCE, DEFAULT_COMMUNITY_SOURCE) ?: DEFAULT_COMMUNITY_SOURCE

    fun setCommunitySource(source: String) {
        prefs().edit().putString(KEY_COMMUNITY_SOURCE, source).apply()
    }

    fun getFileNameFormat(): String = prefs().getString(KEY_FILE_NAME_FORMAT, DEFAULT_FILE_NAME_FORMAT) ?: DEFAULT_FILE_NAME_FORMAT

    fun setFileNameFormat(format: String) {
        prefs().edit().putString(KEY_FILE_NAME_FORMAT, format).apply()
    }

    fun getModStyle(): String = prefs().getString(KEY_MOD_STYLE, DEFAULT_MOD_STYLE) ?: DEFAULT_MOD_STYLE

    fun setModStyle(style: String) {
        prefs().edit().putString(KEY_MOD_STYLE, style).apply()
    }

    fun getIgnoreQuilt(): Boolean = prefs().getBoolean(KEY_IGNORE_QUILT, DEFAULT_IGNORE_QUILT)

    fun setIgnoreQuilt(ignore: Boolean) {
        prefs().edit().putBoolean(KEY_IGNORE_QUILT, ignore).apply()
    }

    fun getShowIcons(): Boolean = prefs().getBoolean(KEY_SHOW_ICONS, DEFAULT_SHOW_ICONS)

    fun setShowIcons(show: Boolean) {
        prefs().edit().putBoolean(KEY_SHOW_ICONS, show).apply()
    }

    fun getIconStyle(): String = prefs().getString(KEY_ICON_STYLE, DEFAULT_ICON_STYLE) ?: DEFAULT_ICON_STYLE

    fun setIconStyle(style: String) {
        prefs().edit().putString(KEY_ICON_STYLE, style).apply()
    }

    fun getAutoUpdate(): Boolean = prefs().getBoolean(KEY_AUTO_UPDATE, DEFAULT_AUTO_UPDATE)

    fun setAutoUpdate(auto: Boolean) {
        prefs().edit().putBoolean(KEY_AUTO_UPDATE, auto).apply()
    }

    fun getBackgroundMusic(): Boolean = prefs().getBoolean(KEY_BACKGROUND_MUSIC, DEFAULT_BACKGROUND_MUSIC)

    fun setBackgroundMusic(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_BACKGROUND_MUSIC, enabled).apply()
    }

    fun getSoundEffects(): Boolean = prefs().getBoolean(KEY_SOUND_EFFECTS, DEFAULT_SOUND_EFFECTS)

    fun setSoundEffects(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_SOUND_EFFECTS, enabled).apply()
    }

    fun getVibration(): Boolean = prefs().getBoolean(KEY_VIBRATION, DEFAULT_VIBRATION)

    fun setVibration(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun getNotifications(): Boolean = prefs().getBoolean(KEY_NOTIFICATIONS, DEFAULT_NOTIFICATIONS)

    fun setNotifications(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    fun getLogLevel(): String = prefs().getString(KEY_LOG_LEVEL, DEFAULT_LOG_LEVEL) ?: DEFAULT_LOG_LEVEL

    fun setLogLevel(level: String) {
        prefs().edit().putString(KEY_LOG_LEVEL, level).apply()
    }

    fun getDebugMode(): Boolean = prefs().getBoolean(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE)

    fun setDebugMode(debug: Boolean) {
        prefs().edit().putBoolean(KEY_DEBUG_MODE, debug).apply()
    }

    fun getGcType(): String = prefs().getString(KEY_GC_TYPE, DEFAULT_GC_TYPE) ?: DEFAULT_GC_TYPE

    fun setGcType(gcType: String) {
        prefs().edit().putString(KEY_GC_TYPE, gcType).apply()
    }

    fun getCustomJvmArgs(): String = prefs().getString(KEY_CUSTOM_JVM_ARGS, DEFAULT_CUSTOM_JVM_ARGS) ?: DEFAULT_CUSTOM_JVM_ARGS

    fun setCustomJvmArgs(args: String) {
        prefs().edit().putString(KEY_CUSTOM_JVM_ARGS, args).apply()
    }

    fun getRenderer(): String = prefs().getString(KEY_RENDERER, DEFAULT_RENDERER) ?: DEFAULT_RENDERER

    fun setRenderer(renderer: String) {
        prefs().edit().putString(KEY_RENDERER, renderer).apply()
    }

    fun getLaunchMethod(): String = prefs().getString(KEY_LAUNCH_METHOD, DEFAULT_LAUNCH_METHOD) ?: DEFAULT_LAUNCH_METHOD

    fun setLaunchMethod(method: String) {
        prefs().edit().putString(KEY_LAUNCH_METHOD, method).apply()
    }

    fun getBeGesture(): Boolean = prefs().getBoolean(KEY_BE_GESTURE, DEFAULT_BE_GESTURE)

    fun setBeGesture(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_BE_GESTURE, enabled).apply()
    }

    fun getVkDriverSystem(): Boolean = prefs().getBoolean(KEY_VK_DRIVER_SYSTEM, DEFAULT_VK_DRIVER_SYSTEM)

    fun setVkDriverSystem(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_VK_DRIVER_SYSTEM, enabled).apply()
    }

    fun getVerifySsl(): Boolean = prefs().getBoolean(KEY_VERIFY_SSL, DEFAULT_VERIFY_SSL)

    fun setVerifySsl(verify: Boolean) {
        prefs().edit().putBoolean(KEY_VERIFY_SSL, verify).apply()
    }

    fun getCommunityResources(): Boolean = prefs().getBoolean(KEY_COMMUNITY_RESOURCES, DEFAULT_COMMUNITY_RESOURCES)

    fun setCommunityResources(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_COMMUNITY_RESOURCES, enabled).apply()
    }

    fun getThemeColor(): String = prefs().getString(KEY_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR

    fun setThemeColor(color: String) {
        prefs().edit().putString(KEY_THEME_COLOR, color).apply()
    }

    /** 背景风格（对齐 PC `versepc_wallpaper`：none / panorama / image / solid） */
    fun getBackgroundStyle(): String = prefs().getString(KEY_BACKGROUND_STYLE, "none") ?: "none"

    fun setBackgroundStyle(style: String) {
        prefs().edit().putString(KEY_BACKGROUND_STYLE, style).apply()
    }

    /** 自定义强调色（8位ARGB hex，空=跟随黑白主题默认） */
    fun getThemeAccent(): String = prefs().getString(KEY_THEME_ACCENT, "") ?: ""

    fun setThemeAccent(hex: String) {
        prefs().edit().putString(KEY_THEME_ACCENT, hex).apply()
    }

    fun getBackgroundImagePath(): String = prefs().getString(KEY_BACKGROUND_IMAGE, "") ?: ""

    fun setBackgroundImagePath(path: String) {
        prefs().edit().putString(KEY_BACKGROUND_IMAGE, path).apply()
    }

    fun getBackgroundVideoPath(): String = prefs().getString(KEY_BACKGROUND_VIDEO, "") ?: ""

    fun setBackgroundVideoPath(path: String) {
        prefs().edit().putString(KEY_BACKGROUND_VIDEO, path).apply()
    }

    fun getWindowTitle(): String = prefs().getString(KEY_WINDOW_TITLE, DEFAULT_WINDOW_TITLE) ?: DEFAULT_WINDOW_TITLE

    fun setWindowTitle(title: String) {
        prefs().edit().putString(KEY_WINDOW_TITLE, title).apply()
    }

    fun getCustomInfo(): String = prefs().getString(KEY_CUSTOM_INFO, DEFAULT_CUSTOM_INFO) ?: DEFAULT_CUSTOM_INFO

    fun setCustomInfo(info: String) {
        prefs().edit().putString(KEY_CUSTOM_INFO, info).apply()
    }

    fun getLauncherVisibility(): String = prefs().getString(KEY_LAUNCHER_VISIBILITY, DEFAULT_LAUNCHER_VISIBILITY) ?: DEFAULT_LAUNCHER_VISIBILITY

    fun setLauncherVisibility(visibility: String) {
        prefs().edit().putString(KEY_LAUNCHER_VISIBILITY, visibility).apply()
    }

    fun getVersionIsolation(): String = prefs().getString(KEY_VERSION_ISOLATION, DEFAULT_VERSION_ISOLATION) ?: DEFAULT_VERSION_ISOLATION

    fun setVersionIsolation(isolation: String) {
        prefs().edit().putString(KEY_VERSION_ISOLATION, isolation).apply()
    }

    fun getFontSize(): Int = prefs().getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)

    fun setFontSize(size: Int) {
        prefs().edit().putInt(KEY_FONT_SIZE, size).apply()
    }

    fun getBackgroundOpacity(): Int = prefs().getInt(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY)

    fun setBackgroundOpacity(opacity: Int) {
        prefs().edit().putInt(KEY_BACKGROUND_OPACITY, opacity).apply()
    }

    fun getGameWidth(): Int = prefs().getInt(KEY_GAME_WIDTH, DEFAULT_GAME_WIDTH)

    fun setGameWidth(width: Int) {
        prefs().edit().putInt(KEY_GAME_WIDTH, width).apply()
    }

    fun getGameHeight(): Int = prefs().getInt(KEY_GAME_HEIGHT, DEFAULT_GAME_HEIGHT)

    fun setGameHeight(height: Int) {
        prefs().edit().putInt(KEY_GAME_HEIGHT, height).apply()
    }

    fun getFullscreen(): Boolean = prefs().getBoolean(KEY_FULLSCREEN, DEFAULT_FULLSCREEN)

    fun setFullscreen(fullscreen: Boolean) {
        prefs().edit().putBoolean(KEY_FULLSCREEN, fullscreen).apply()
    }

    /** 忽略屏幕刘海区域全屏显示（false=避让刘海，窗口在刘海侧缩小） */
    fun getIgnoreNotch(): Boolean = prefs().getBoolean(KEY_IGNORE_NOTCH, DEFAULT_IGNORE_NOTCH)

    fun setIgnoreNotch(ignoreNotch: Boolean) {
        prefs().edit().putBoolean(KEY_IGNORE_NOTCH, ignoreNotch).apply()
    }

    fun resetToDefaults() {
        prefs().edit().clear().apply()
    }

    fun getCustomSetting(key: String): String? = prefs().getString("custom_$key", null)

    fun setCustomSetting(key: String, value: String) {
        prefs().edit().putString("custom_$key", value).apply()
    }
}