// crash_analyzer/crit2.rs — 二级崩溃分析
// 职责：Mixin 失败、Forge/Fabric 报错、Suspected Mod 等较精准的匹配
// 运行优先级：高于堆栈分析、低于一级分析；一级分析已命中则不执行。

package com.verse.explorer.app.crash

import java.util.regex.Pattern

object Crit2 {

    private val FABRIC_SOLUTION_NEEDLES: List<String> = listOf(
        "A potential solution has been determined:",
        "A potential solution has been determined, this may resolve your problem:",
        "确定了一种可能的解决方法，这样做可能会解决你的问题："
    )

    // 二级分析
    fun analyze(logs: PreparedLogs): LinkedHashMap<CrashReason, List<String>> {
        // 内部使用 MutableList 以匹配 CrashUtils.appendReason(MutableMap<CrashReason, MutableList<String>>) 的签名
        val reasons = LinkedHashMap<CrashReason, MutableList<String>>()
        val logMc = logs.logMc
        val logCrash = logs.logCrash
        val logMcDebug = logs.logMcDebug

        // Mixin 分析（游戏日志）
        if (logMc != null) {
            val lm = logMc
            val isMixin = mixinAnalyze(lm, logCrash, logMcDebug, reasons)

            // Forge 报错
            if (lm.contains("An exception was thrown, the game will display an error screen and halt.")) {
                val msg = CrashUtils.regexSeekGroup(
                    lm,
                    "the game will display an error screen and halt.[\\n\\r]+[^\\n]+?Exception: ([\\s\\S]+?)(?=\\n\\tat)",
                    1
                )
                if (msg != null) {
                    CrashUtils.appendReason(reasons, CrashReason.ForgeCrash, listOf(msg.trim()))
                }
            }

            // Fabric 报错并给出解决方案（三种文案）
            for (needle in FABRIC_SOLUTION_NEEDLES) {
                if (lm.contains(needle)) {
                    // 提取解决方案块（以 "- xxx" 开头的行），再取每行内容
                    val block = CrashUtils.regexSeekGroup(
                        lm,
                        Pattern.quote(needle) + "[\\n\\r]+((?:\\s+ - [^\\n]+[\\n\\r]+)+)",
                        1
                    )
                    if (block != null) {
                        val lines = CrashUtils.regexSeekAllGroup("(?m)\\s+ - ([^\\n]+)", block, 1)
                            .map { s -> s.trim() }
                            .filter { s -> s.isNotEmpty() }
                        if (lines.isNotEmpty()) {
                            CrashUtils.appendReason(reasons, CrashReason.FabricCrash, lines)
                        }
                    }
                    break
                }
            }

            // "due to errors, provided by '...'"（Mixin 失败除外）
            if (!isMixin && lm.contains("due to errors, provided by ")) {
                val name = CrashUtils.regexSeekGroup(lm, "due to errors, provided by '([^']+)", 1)
                if (name != null) {
                    val names = StackAnalyzer.tryAnalyzeModNameOne(name.trimEnd('\n'), logCrash, logMcDebug)
                    CrashUtils.appendReason(reasons, CrashReason.ModCrashed, names)
                }
            }
        }

        // 崩溃报告分析
        if (logCrash != null) {
            val lc = logCrash
            mixinAnalyze(lc, logCrash, logMcDebug, reasons)

            // Suspected Mod
            if (lc.contains("Suspected Mod")) {
                val suspectsRaw = CrashUtils.between(lc, "Suspected Mod", "Stacktrace")
                if (!suspectsRaw.trimStart().startsWith("s: None")) {
                    val suspects = CrashUtils.regexSeekGroup(suspectsRaw, "(?m)\\n\\t[^(\\t]+\\(([^)\\n]+)", 1)
                    if (suspects != null) {
                        val lines = suspects.lineSequence()
                            .map { x -> x.trim() }
                            .filter { x -> x.isNotEmpty() }
                            .toList()
                        val names = StackAnalyzer.tryAnalyzeModName(lines, logCrash, logMcDebug)
                        CrashUtils.appendReason(reasons, CrashReason.SuspectedModCrash, names)
                    }
                }
            }
        }

        val result = LinkedHashMap<CrashReason, List<String>>()
        for ((key, value) in reasons) {
            result[key] = value
        }
        return result
    }

    // Mixin 失败检测；返回是否检测到 Mixin 崩溃
    private fun mixinAnalyze(
        logText: String,
        logCrash: String?,
        logMcDebug: String?,
        reasons: LinkedHashMap<CrashReason, MutableList<String>>
    ): Boolean {
        val isMixin = logText.contains("Mixin prepare failed ")
            || logText.contains("Mixin apply failed ")
            || logText.contains("MixinApplyError")
            || logText.contains("MixinTransformerError")
            || logText.contains("mixin.injection.throwables.")
            || logText.contains(".json] FAILED during )")
        if (!isMixin) {
            return false
        }

        // Mod 名称匹配
        val modName = CrashUtils.regexSeekGroup(logText, "from mod ([^./ ]+)(?=\\] from)", 1)
            ?: CrashUtils.regexSeekGroup(logText, "for mod ([^./ ]+)(?= failed)", 1)
        if (modName != null) {
            val names = StackAnalyzer.tryAnalyzeModNameOne(modName.trimEnd('\n'), logCrash, logMcDebug)
            CrashUtils.appendReason(reasons, CrashReason.ModMixinError, names)
            return true
        }

        // JSON 名称匹配
        for (jsonName in CrashUtils.regexSeekAllGroup(
            "(?m)^[^\\t]+[ \\[{(]([^ \\[{(]+\\.[^ ]+)(?=\\.json)",
            logText,
            1
        )) {
            val name = jsonName
                .replace("mixins", "mixin")
                .replace(".mixin", "")
                .replace("mixin.", "")
            val names = StackAnalyzer.tryAnalyzeModNameOne(name, logCrash, logMcDebug)
            CrashUtils.appendReason(reasons, CrashReason.ModMixinError, names)
            return true
        }

        // 没有明确匹配
        CrashUtils.appendReason(reasons, CrashReason.ModMixinError, null)
        return true
    }
}
