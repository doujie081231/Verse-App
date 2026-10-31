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
            OptionsFileWriter.writeOnboardingSkip(targets)
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
            val releaseTime = version.releaseTime ?: version.time
            val outcome = OptionsFileWriter.writeGameLanguage(runDir, releaseTime)

            if (outcome.usedYosbr) {
                VerseLogger.info("使用 Yosbr Mod 中的 options.txt", TAG)
            }
            // 1.0 及以下版本无语言选项
            if (outcome.requiredLang == null) {
                VerseLogger.info("1.0 及以下版本，无语言选项", TAG)
                return@runCatching
            }
            // 强制中文：无论是否已有存档/语言选择，每次启动都写入 requiredLang
            if (outcome.changed) {
                VerseLogger.info("已将游戏语言设置为 ${outcome.requiredLang}（原 ${outcome.previousLang}）", TAG)
            } else {
                VerseLogger.info("当前语言已为 ${outcome.requiredLang}，无需修改", TAG)
            }
        }.onFailure {
            VerseLogger.warn("自动设置中文失败: ${it.message}", TAG)
        }
    }

    /**
     * 每次启动写 preferredGraphicsBackend：
     * - GL 系渲染器（MobileGlues / GL4ES / Krypton Wrapper 等）固定 "opengl"：
     *   它们只实现 OpenGL，Vulkan 后端下窗口/EGL 初始化失败
     *   （"Failed to query window size in pixels: Invalid window"），或直接初始化崩溃；
     * - Zink / Freedreno 等 Vulkan 渲染器按用户选择（版本级优先，其次全局）；
     * - 未显式选择时，只有该版本确实检测到可用 Vulkan 才写 "vulkan"，否则 "default" 交给游戏探测。
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

            // GL 渲染器强制 opengl（Vulkan 后端下其窗口/EGL 初始化失败
            // "Failed to query window size in pixels: Invalid window"）；
            // 其余按版本级/全局设置写 default / opengl / vulkan。
            val rendererConfig = RendererManager.getRendererById(activity, renderer)
            val isMobileGlues = rendererConfig != null &&
                (rendererConfig.glName.contains("mobileglues", ignoreCase = true) ||
                    rendererConfig.displayName.contains("MobileGlues", ignoreCase = true))
            // GL 系渲染器：MobileGlues / GL4ES / Krypton Wrapper(libng_gl4es) / SimpleFPEWrapper。
            // 它们只实现 OpenGL，写 vulkan 后端时 MC 会先去初始化 Vulkan 再回落，
            // 渲染器接不上（真机日志：nggl4es + vulkan 后端在初始化阶段直接崩）
            val isGlRenderer = rendererConfig != null && listOf("mobileglues", "gl4es", "fpew").any {
                rendererConfig.glName.contains(it, ignoreCase = true) ||
                    rendererConfig.eglName.contains(it, ignoreCase = true)
            }
            // 用户显式选择：版本级独立设置优先，其次全局
            val chosen = (backendOverride ?: SettingsRepository.getGraphicsBackend()).ifBlank { "default" }
            val backend = when {
                // GL 渲染器固定 opengl——与用户在设置里选 OpenGL 的结果一致，
                // 也避免把 GL 渲染器强行塞进 Vulkan 后端（旧逻辑在全局为 default 时会这么写）
                isGlRenderer -> "opengl"
                // Zink / Freedreno 等 Vulkan 渲染器：尊重用户选择
                chosen != "default" -> chosen
                // 未显式选择时，只有确实检测到该版本 Vulkan 可用才写 vulkan，否则交给游戏探测
                com.verse.explorer.app.device.VulkanCheckManager.hasVulkanBackend(version.id) &&
                    com.verse.explorer.app.device.VulkanCheckManager.cachedSupport(activity, version.id) == true -> "vulkan"
                else -> "default"
            }

            // 只写入渲染后端键；视距/平滑光照/mipmap/垂直同步等交给游戏内设置，
            // 每次启动强制覆盖会导致玩家在游戏里调好的画面设置下一局被重置。
            OptionsFileWriter.writeBackend(runDir, OptionsFileWriter.backendEntries(isMobileGlues, backend))
            // 游戏内 GUI 缩放：快捷栏自动判定要用它算 9 格尺寸（0 = 跟随自动，-1 = 读不到）。
            // 对齐 ZL2 的 MCOptions.get("guiScale")。
            val guiScale = readGuiScale(runDir)
            com.tungsten.fcl.setting.MenuSetting.mcGuiScale = guiScale
            VerseLogger.info("快捷栏自动尺寸基准: options.txt guiScale=$guiScale", TAG)
            VerseLogger.info(
                "已注入渲染后端: $backend（渲染器=$renderer, 选择=${backendOverride ?: "跟随全局"}," +
                    " 全局=${SettingsRepository.getGraphicsBackend()}" +
                    (if (isGlRenderer) ", GL 渲染器固定 opengl" else "") + "）", TAG
            )
            // 渲染器支持上限校验：超出上限的渲染器跑新版本会在初始化阶段直接崩，
            // 日志里先标出来，便于对照崩溃报告判断（不阻断启动，用户可换渲染器）
            val maxVer = rendererConfig?.maxMcVersion
            if (!maxVer.isNullOrBlank() && version.id.isNotBlank() &&
                com.tungsten.fclcore.util.versioning.GameVersionNumber.compare(
                    com.verse.explorer.app.device.normalizeMcVersion(version.id), maxVer
                ) > 0
            ) {
                VerseLogger.warn(
                    "渲染器 $renderer 最高支持 MC $maxVer，当前版本 ${version.id} 超出上限，可能出现图形初始化崩溃",
                    TAG
                )
            }
        }.onFailure {
            VerseLogger.warn("注入 preferredGraphicsBackend 失败: ${it.message}", TAG)
        }
    }

    /**
     * 读 options.txt 的 guiScale（MC 的 GUI 缩放：0 = 自动，缺省行同样视为自动）。
     * @return 0/1..N；读不到返回 -1（调用方按自动处理）
     */
    private fun readGuiScale(runDir: File): Int {
        return runCatching {
            val f = File(runDir, "options.txt")
            if (!f.isFile) return -1
            f.readLines()
                .firstOrNull { it.startsWith("guiScale:") }
                ?.substringAfter(':')?.trim()?.toIntOrNull()
                ?: -1
        }.getOrDefault(-1)
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
