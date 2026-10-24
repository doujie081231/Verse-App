package com.verse.explorer.app.game

import android.content.Context
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.app.domain.model.launch.GameLaunchParams
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.launcher.fcl.game.DefaultGameRepository
import com.verse.explorer.launcher.fcl.game.Version
import com.verse.explorer.launcher.renderer.RendererManager
import com.tungsten.fcl.setting.MenuSetting
import java.io.File
import java.time.Instant

/**
 * 启动前 options.txt 注入器。
 *
 * 职责：每次启动前把启动器侧策略写入版本运行目录的 options.txt——
 * 跳过首次欢迎引导、强制中文、渲染后端键。只追加/替换目标键，不整写模板，
 * 规避旧格式行被 MC 选项解析器跳过引发的启动期异常；
 * 视距/平滑光照/mipmap/垂直同步等画面设置交还游戏内设置，启动不覆盖。
 *
 * 与 GameLaunchOrchestrator 共用同一个日志 TAG，保持 logcat 过滤契约不变。
 */
class OptionsInjector(private val activity: Context) {

    companion object {
        private const val TAG = "GameLaunchOrchestrator"
    }

    /**
     * 跳过 MC 首次启动欢迎引导（Welcome to Minecraft! 会阻止进入主菜单）：
     * 在版本目录 options.txt 写 onboardAccessibility:false。
     * 游戏退出时会重写 options.txt 丢弃该行，因此每次启动前都注入。
     */
    fun injectOnboardingSkip(repository: DefaultGameRepository, versionId: String) {
        runCatching {
            val targets = listOf(
                File(repository.getRunDirectory(versionId), "options.txt"),
                File(File(File(repository.getBaseDirectory(), "versions"), versionId), "options.txt")
            )
            targets.distinct().forEach { optionsFile ->
                if (optionsFile.parentFile != null) optionsFile.parentFile.mkdirs()
                val content = if (optionsFile.exists() && optionsFile.isFile) optionsFile.readText() else ""
                if (!content.contains("onboardAccessibility:")) {
                    optionsFile.appendText("\nonboardAccessibility:false\n")
                }
            }
        }.onFailure {
            VerseLogger.warn("注入 onboardAccessibility 失败: ${it.message}", TAG)
        }
    }

    /**
     * 按版本发布日期选择 lang 值（1.1~1.10 用 zh_CN，其余支持语言版本用 zh_cn；
     * 1.0 及以下无语言选项直接跳过），写入版本目录 options.txt。
     * 用户要求强制中文：每次启动都会覆盖为 requiredLang（不保留存档中的旧语言选择）。
     */
    fun injectGameLanguage(repository: DefaultGameRepository, version: Version) {
        if (!SettingsRepository.isAutoSetChinese()) {
            VerseLogger.info("自动设置中文已关闭，跳过语言设置", TAG)
            return
        }
        runCatching {
            val runDir = repository.getRunDirectory(version.id)
            var optionsFile = File(runDir, "options.txt")
            val yosbrFile = File(File(File(runDir, "config"), "yosbr"), "options.txt")
            if (!optionsFile.exists() && yosbrFile.exists()) {
                VerseLogger.info("使用 Yosbr Mod 中的 options.txt", TAG)
                optionsFile = yosbrFile
            }

            val releaseTime = version.releaseTime ?: version.time
            val mc1_1 = Instant.parse("2012-01-12T00:00:00Z")
            val mc1_11 = Instant.parse("2016-06-08T00:00:00Z")
            // 1.0 及以下版本无语言选项
            if (releaseTime != null && !releaseTime.isAfter(mc1_1)) {
                VerseLogger.info("1.0 及以下版本，无语言选项", TAG)
                return@runCatching
            }
            val requiredLang = if (releaseTime != null && releaseTime.isAfter(mc1_1) && !releaseTime.isAfter(mc1_11)) {
                "zh_CN"
            } else {
                "zh_cn"
            }

            var content = if (optionsFile.exists() && optionsFile.isFile) optionsFile.readText() else ""
            val langRegex = Regex("^lang:(.+)$", RegexOption.MULTILINE)
            val currentLang = langRegex.find(content)?.groupValues?.get(1)?.trim() ?: "none"

            // 强制中文：无论是否已有存档/语言选择，每次启动都写入 requiredLang
            if (currentLang != requiredLang) {
                content = if (content.isNotEmpty() && currentLang != "none") {
                    content.replace(langRegex, "lang:$requiredLang")
                } else if (content.isNotEmpty()) {
                    content + "\nlang:$requiredLang"
                } else {
                    "lang:$requiredLang\n"
                }
                VerseLogger.info("已将游戏语言设置为 $requiredLang（原 $currentLang）", TAG)
            } else {
                VerseLogger.info("当前语言已为 $requiredLang，无需修改", TAG)
            }

            optionsFile.parentFile?.mkdirs()
            optionsFile.writeText(content)
        }.onFailure {
            VerseLogger.warn("自动设置中文失败: ${it.message}", TAG)
        }
    }

    /**
     * 每次启动写 preferredGraphicsBackend：GL 渲染器（MobileGlues 等）强制 "opengl"
     * （MobileGlues 是 OpenGL 实现，Vulkan 后端下其窗口/EGL 初始化失败
     * "Failed to query window size in pixels: Invalid window"）；
     * 其余渲染器写 "default"（MC 26.2 据此自选后端）。
     * 注意：不整写旧版 options 模板——MC 26.2 的选项解析器对无引号旧格式行
     * （fov:0.0/clouds:true 等）报 "Skipping bad option"，可能引发启动期异常；
     * options.txt 首次由 MC 自行生成，这里只追加/更新 backend 键。
     */
    fun injectGraphicsBackend(
        repository: DefaultGameRepository,
        version: Version,
        renderer: String,
        backendOverride: String? = null
    ) {
        runCatching {
            val runDir = repository.getRunDirectory(version.id)
            runDir.mkdirs()
            val optionsFile = File(runDir, "options.txt")
            if (!optionsFile.exists()) {
                optionsFile.writeText("")
            }

            var content = optionsFile.readText()

            // preferredGraphicsBackend：GL 渲染器强制 opengl（Vulkan 后端下 MobileGlues 的
            // 窗口/EGL 初始化失败 "Invalid window"）；其余走 default（MC 自选）。
            // 26.3 起选项类被模块化拆分，后端键名可能非 preferredGraphicsBackend，
            // 对 MobileGlues 额外写入一组候选键（MC 只读取它识别的那个）。
            // 值一律小写（default/opengl/vulkan），大写变体 MC 不识别会回落默认后端。
            val rendererConfig = RendererManager.getRendererById(activity, renderer)
            val isMobileGlues = rendererConfig != null &&
                (rendererConfig.glName.contains("mobileglues", ignoreCase = true) ||
                    rendererConfig.displayName.contains("MobileGlues", ignoreCase = true))
            if (isMobileGlues) {
                content = setOption(content, "preferredGraphicsBackend", "\"opengl\"")
                content = setOption(content, "graphicsApi", "\"opengl\"")
                content = setOption(content, "graphicsBackend", "\"opengl\"")
                content = setOption(content, "apiPreference", "\"opengl\"")
                content = setOption(content, "renderBackend", "\"opengl\"")
            } else {
                // default 由 MC 自选（26.3 探测到 Vulkan 强制 Vulkan），opengl/vulkan 显式指定。
                // 版本级独立设置优先；26.2+ 版本在全局仍是 default 时按实测适配为 vulkan
                // （G925 实测：default 时 MC 探测回落 GL 后端 glGetError mismatch 失败，
                // 显式 vulkan 才走 Krypton/系统驱动正常），用户显式选择的值不受影响
                val backend = backendOverride ?: run {
                    val global = SettingsRepository.getGraphicsBackend()
                    if (global == "default" &&
                        com.verse.explorer.app.device.VulkanCheckManager.hasVulkanBackend(version.id)
                    ) "vulkan" else global
                }
                content = setOption(content, "preferredGraphicsBackend", "\"$backend\"")
            }

            // 只写入渲染后端键；视距/平滑光照/mipmap/垂直同步等交给游戏内设置，
            // 每次启动强制覆盖会导致玩家在游戏里调好的画面设置下一局被重置。
            optionsFile.writeText(content)
            VerseLogger.info("已注入渲染后端: ${SettingsRepository.getGraphicsBackend()}", TAG)
        }.onFailure {
            VerseLogger.warn("注入 preferredGraphicsBackend 失败: ${it.message}", TAG)
        }
    }

    /**
     * options.txt 精确替换/追加单个键。value 为原始 options 行等号后的字面量（字符串需自带引号）。
     * 只改目标键，不整写模板，规避 MC 26.2 对旧格式行 "Skipping bad option" 的启动期异常。
     */
    private fun setOption(content: String, key: String, value: String): String {
        val regex = Regex("^$key:.*$", RegexOption.MULTILINE)
        val line = "${key}:$value"
        return if (regex.containsMatchIn(content)) {
            regex.replace(content, line)
        } else {
            (if (content.trimEnd().isEmpty()) content else content.trimEnd() + "\n") + line + "\n"
        }
    }

    /**
     * 输出 options.txt 中的渲染相关键，仅用于排查画面问题，不修改玩家的游戏内设置。
     */
    fun dumpRenderOptions(repository: DefaultGameRepository, version: Version, params: GameLaunchParams) {
        runCatching {
            val runDir = repository.getRunDirectory(version.id)
            val optionsFile = File(runDir, "options.txt")
            if (!optionsFile.exists() || !optionsFile.isFile) {
                VerseLogger.warn("[RENDER-DIAG] options.txt 不存在于 ${optionsFile.absolutePath}", TAG)
                return@runCatching
            }
            val content = optionsFile.readText()
            VerseLogger.info("[RENDER-DIAG] ==== options.txt (${optionsFile.absolutePath}) 完整内容 ====", TAG)
            content.lineSequence().forEachIndexed { i, line ->
                if (line.isNotBlank()) VerseLogger.info("[RENDER-DIAG] options[${i}] = $line", TAG)
            }
            VerseLogger.info("[RENDER-DIAG] ==== 与水体/渲染相关的键（缺失即默认） ====", TAG)
            val keys = listOf(
                "preferredGraphicsBackend", "mipmapLevels", "renderDistance", "simulationDistance",
                "ao", "fancyGraphics", "graphicsPreset", "renderClouds", "particles", "gamma",
                "entityShadows", "maxFps", "fullscreen"
            )
            keys.forEach { key ->
                val m = Regex("(?m)^$key:(.*)$").find(content)
                val v = m?.groupValues?.get(1)?.trim() ?: "<缺失/默认>"
                VerseLogger.info("[RENDER-DIAG]   $key = $v", TAG)
            }
            VerseLogger.info("[RENDER-DIAG] 渲染: renderer=${params.renderer}, windowScale=${MenuSetting.windowScale}（窗口缩放实际尺寸见 [9/9 渲染接入] 行）", TAG)
        }.onFailure {
            VerseLogger.warn("[RENDER-DIAG] dump 渲染参数失败: ${it.message}", TAG)
        }
    }
}
