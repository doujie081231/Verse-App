package com.verse.explorer.app.game

import java.io.File
import java.time.Instant

/**
 * options.txt 的实际写入器：只负责「给定目录 + 策略 → 落盘结果」，
 * 不做渲染器识别、不读 SharedPreferences、不写日志，因此可以在纯 JVM 单测里跑。
 * 判定类逻辑（是否 MG、全局后端、是否自动中文）留在 OptionsInjector。
 *
 * 三条写入契约（改动会直接影响启动成功率）：
 * 1. 只追加/替换目标键，绝不整写模板 —— 旧格式行会被 MC 选项解析器跳过并可能导致启动期异常；
 * 2. 欢迎引导键幂等：已存在就不再追加，游戏退出会丢该行所以每次启动都要补；
 * 3. 语言按版本发布日期分档：1.1~1.10 用 `zh_CN`，更新版本用 `zh_cn`，1.0 及以下无此选项。
 */
internal object OptionsFileWriter {

    /** 1.1 的发布日期：此前版本没有语言选项 */
    private val MC_1_1 = Instant.parse("2012-01-12T00:00:00Z")

    /** 1.11 的发布日期：此后语言标识改为小写下划线 */
    private val MC_1_11 = Instant.parse("2016-06-08T00:00:00Z")

    /** 欢迎引导键 —— 缺失会卡在 "Welcome to Minecraft!" 界面 */
    const val ONBOARDING_KEY = "onboardAccessibility"

    /**
     * 跳过首次启动欢迎引导。targets 会去重；对每个文件幂等追加。
     * 文件不存在时按需创建（appendText 自动建文件）。
     */
    fun writeOnboardingSkip(targets: List<File>) {
        targets.distinct().forEach { optionsFile ->
            optionsFile.parentFile?.mkdirs()
            val content = if (optionsFile.exists() && optionsFile.isFile) optionsFile.readText() else ""
            if (!content.contains("$ONBOARDING_KEY:")) {
                optionsFile.appendText("\n$ONBOARDING_KEY:false\n")
            }
        }
    }

    /** 语言写入结果，供调用方决定日志文案 */
    data class LanguageOutcome(
        val optionsFile: File,
        /** 应写入的语言标识；null 表示该版本没有语言选项 */
        val requiredLang: String?,
        /** 写之前的语言标识，"none" 表示此前没有 lang 行 */
        val previousLang: String,
        /** 是否发生了实际变更 */
        val changed: Boolean,
        /** 是否因为没有 options.txt 而回退到 Yosbr Mod 的配置 */
        val usedYosbr: Boolean
    )

    /**
     * 强制写入中文语言。options.txt 不存在时优先用 Yosbr Mod 的配置（否则玩家的
     * 语言会被 Yosbr 覆盖回去）。返回值描述发生了什么，日志由调用方输出。
     */
    fun writeGameLanguage(runDir: File, releaseTime: Instant?): LanguageOutcome {
        var optionsFile = File(runDir, "options.txt")
        val yosbrFile = File(File(File(runDir, "config"), "yosbr"), "options.txt")
        var usedYosbr = false
        if (!optionsFile.exists() && yosbrFile.exists()) {
            usedYosbr = true
            optionsFile = yosbrFile
        }

        // 1.0 及以下没有语言选项，直接返回
        if (releaseTime != null && !releaseTime.isAfter(MC_1_1)) {
            return LanguageOutcome(optionsFile, null, "none", false, usedYosbr)
        }

        val requiredLang = if (releaseTime != null &&
            releaseTime.isAfter(MC_1_1) && !releaseTime.isAfter(MC_1_11)
        ) {
            "zh_CN"
        } else {
            "zh_cn"
        }

        var content = if (optionsFile.exists() && optionsFile.isFile) optionsFile.readText() else ""
        val langRegex = Regex("^lang:(.+)$", RegexOption.MULTILINE)
        val currentLang = langRegex.find(content)?.groupValues?.get(1)?.trim() ?: "none"

        val changed = currentLang != requiredLang
        if (changed) {
            content = when {
                content.isNotEmpty() && currentLang != "none" -> content.replace(langRegex, "lang:$requiredLang")
                content.isNotEmpty() -> content + "\nlang:$requiredLang"
                else -> "lang:$requiredLang\n"
            }
        }

        optionsFile.parentFile?.mkdirs()
        optionsFile.writeText(content)
        return LanguageOutcome(optionsFile, requiredLang, currentLang, changed, usedYosbr)
    }

    /**
     * 渲染后端键值对。GL 渲染器（MobileGlues 等）必须强制 opengl：
     * Vulkan 后端下它的窗口/EGL 初始化会失败。26.3 起选项类被模块化拆分，
     * 后端键名可能变化，因此对 GL 渲染器同时写入全部候选键（MC 只读它认识的那个）。
     * 值一律小写并带引号：大写变体 MC 不识别，会回落到默认后端。
     */
    fun backendEntries(isMobileGlues: Boolean, backend: String): List<Pair<String, String>> =
        if (isMobileGlues) {
            listOf(
                "preferredGraphicsBackend" to "\"opengl\"",
                "graphicsApi" to "\"opengl\"",
                "graphicsBackend" to "\"opengl\"",
                "apiPreference" to "\"opengl\"",
                "renderBackend" to "\"opengl\""
            )
        } else {
            listOf("preferredGraphicsBackend" to "\"$backend\"")
        }

    /**
     * 把后端键写入 options.txt。只改这些键，其它行（玩家在游戏里调好的视距、
     * 平滑光照、mipmap、垂直同步等）原样保留。
     */
    fun writeBackend(runDir: File, entries: List<Pair<String, String>>) {
        runDir.mkdirs()
        val optionsFile = File(runDir, "options.txt")
        if (!optionsFile.exists()) optionsFile.writeText("")

        var content = optionsFile.readText()
        for ((key, value) in entries) {
            content = OptionsText.setOption(content, key, value)
        }
        optionsFile.writeText(content)
    }
}
