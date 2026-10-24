// 收集游戏目录下的日志（MC 运行日志 / hs_err / crash-report / debug 日志）→
// LogPrepare.prepare 预处理 → Crit1 → Crit2 → 堆栈分析（Mod 场景）→ Crit3 → 建议文本
package com.verse.explorer.app.crash

import com.verse.explorer.app.crash.Crit1
import com.verse.explorer.app.crash.Crit2
import com.verse.explorer.app.crash.Crit3
import com.verse.explorer.app.crash.StackAnalyzer
import java.io.File

/** 分析输出（对应 mod.rs 的 AnalysisOutput） */
data class Pc2Analysis(
    val detail: String,
    val files: List<String>,
    val crashReasons: List<Pair<CrashReason, List<String>>>,
    val logMc: String?,
    val logHs: String?,
    val logCrash: String?
)

object VersePc2CrashAnalyzer {

    /** 收集游戏目录下的日志文件（Android 路径适配） */
    fun collectLogs(gameDir: File?, debugLog: File?): List<RawLogFile> {
        val raw = mutableListOf<RawLogFile>()
        val dir = gameDir

        // MC 运行日志：logs/latest.log，其次 logs/game.log
        if (dir != null) {
            val latest = File(dir, "logs/latest.log")
            val gameLog = File(dir, "logs/game.log")
            val mcLog = when {
                latest.exists() && latest.length() > 0 -> latest
                gameLog.exists() && gameLog.length() > 0 -> gameLog
                else -> null
            }
            mcLog?.let { raw.add(RawLogFile(it.name, readLines(it))) }

            // JVM 致命错误日志：hs_err_pid*.log（gameDir 与 crash-reports 下）
            val hsDirs = listOf(dir, File(dir, "crash-reports"))
            hsDirs.flatMap { d -> d.listFiles()?.filter { it.isFile && it.name.startsWith("hs_err") } ?: emptyList() }
                .maxByOrNull { it.lastModified() }
                ?.let { raw.add(RawLogFile(it.name, readLines(it))) }

            // 崩溃报告：crash-reports/crash-*.txt 最新
            File(dir, "crash-reports").listFiles()
                ?.filter { it.isFile && it.name.startsWith("crash-") && it.name.endsWith(".txt") }
                ?.maxByOrNull { it.lastModified() }
                ?.let { raw.add(RawLogFile(it.name, readLines(it))) }
        }

        // 启动器侧 debug 日志（ExtraLogFile，无 MC 日志时顶替）
        debugLog?.takeIf { it.exists() && it.length() > 0 }?.let {
            raw.add(RawLogFile(it.name, readLines(it)))
        }
        return raw
    }

    private fun readLines(f: File): List<String> =
        runCatching { f.readLines() }.getOrDefault(emptyList())

    /** 完整分析编排（mod.rs analyze_logs）：Crit1 → Crit2 → 堆栈(Mod) → Crit3 */
    fun analyzeLogs(logs: PreparedLogs): LinkedHashMap<CrashReason, List<String>> {
        // 1. 一级分析
        val crit1 = Crit1.analyze(logs)
        if (crit1.isNotEmpty()) return crit1

        // 2. 二级分析
        val crit2 = Crit2.analyze(logs)
        if (crit2.isNotEmpty()) return crit2

        // 拼接全部日志判断是否安装了加载器
        val logMc = logs.logMc ?: ""
        val logMcDebug = logs.logMcDebug ?: ""
        val logHs = logs.logHs ?: ""
        val logCrash = logs.logCrash ?: ""
        val logAll = if (logMc.isNotEmpty()) logMc + logHs + logCrash else logMcDebug + logHs + logCrash

        // 3. 堆栈分析（仅当安装了 Mod：forge/fabric/quilt/liteloader 字样）
        if (logAll.contains("orge") || logAll.contains("abric") ||
            logAll.contains("uilt") || logAll.contains("iteloader")
        ) {
            val keywords = mutableListOf<String>()

            if (logCrash.isNotEmpty()) {
                val stack = CrashUtils.beforeFirst(logCrash, "System Details")
                keywords.addAll(StackAnalyzer.analyzeStackKeyword(stack))
            }
            if (logMc.isNotEmpty()) {
                val fatals = CrashUtils.regexSeekAll("/FATAL] .+?(?=\\n+\\[)", logMc).toMutableList()
                if (logMc.contains("Unreported exception thrown!")) {
                    fatals.add(CrashUtils.between(logMc, "Unreported exception thrown!", "at oolloo.jlw.Wrapper"))
                }
                for (fatal in fatals) {
                    keywords.addAll(StackAnalyzer.analyzeStackKeyword(fatal))
                }
            }
            if (logHs.isNotEmpty()) {
                val stackLogs = CrashUtils.between(logHs, "T H R E A D", "Registers:")
                keywords.addAll(StackAnalyzer.analyzeStackKeyword(stackLogs))
            }

            if (keywords.isNotEmpty()) {
                val reasons = LinkedHashMap<CrashReason, MutableList<String>>()
                val modNames = StackAnalyzer.analyzeModName(keywords, logs.logCrash, logs.logMcDebug)
                if (modNames != null) {
                    CrashUtils.appendReason(reasons, CrashReason.StackModNameFound, modNames.toMutableList())
                } else {
                    CrashUtils.appendReason(reasons, CrashReason.StackKeywordFound, keywords.toMutableList())
                }
                return LinkedHashMap(reasons)
            }
        }

        // 4. 三级分析
        return Crit3.analyze(logs)
    }

    /** 主分析入口：收集 → 预处理 → 分析 → 建议文本（按 reasonOrder 排序） */
    fun analyze(gameDir: File?, debugLog: File?): Pc2Analysis {
        val raw = collectLogs(gameDir, debugLog)
        val logs = LogPrepare.prepare(raw)
        val reasons = analyzeLogs(logs)
        val detail = CrashSuggest.getAnalyzeResult(reasons, false)

        val sorted = reasons.entries
            .sortedBy { CrashSuggest.reasonOrder(it.key) }
            .map { it.key to it.value }

        return Pc2Analysis(
            detail = detail,
            files = logs.outputFiles,
            crashReasons = sorted,
            logMc = logs.logMc,
            logHs = logs.logHs,
            logCrash = logs.logCrash
        )
    }
}
