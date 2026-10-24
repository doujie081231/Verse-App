// crash_analyzer/stack.rs — 虚拟机堆栈分析与 Mod 名称匹配
// 职责：
// 1. 从堆栈中提取可能的 Mod 关键词（analyzeStackKeyword）
// 2. 根据关键词匹配实际的 Mod 名称（analyzeModName）
// 3. 查找不匹配时回退原关键词的辅助（tryAnalyzeModName）

package com.verse.explorer.app.crash

object StackAnalyzer {

    // 堆栈开头需要忽略的包前缀
    private val IGNORE_STACKS: List<String> = listOf(
        "java", "sun", "javax", "jdk", "oolloo", "org.lwjgl", "com.sun", "net.minecraftforge",
        "paulscode.sound", "com.mojang", "net.minecraft", "cpw.mods", "com.google", "org.apache",
        "org.spongepowered", "net.fabricmc", "com.mumfrey",
        "com.electronwill.nightconfig", "it.unimi.dsi",
        "MojangTricksIntelDriversForPerformance_javaw"
    )

    // 不作为关键词的常见词
    private val WORD_STOPLIST: List<String> = listOf(
        "com", "org", "net", "asm", "fml", "mod", "jar", "sun", "lib", "map", "gui", "dev", "nio",
        "api", "dsi", "top", "mcp", "core", "init", "mods", "main", "file", "game", "load", "read",
        "done", "util", "tile", "item", "base", "fake", "oshi", "impl", "data", "pool", "task",
        "forge", "setup", "block", "model", "mixin", "event", "unimi", "netty", "world", "lwjgl",
        "fakes", "fabric", "gitlab", "common", "server", "config", "mixins", "compat", "loader",
        "launch", "script", "entity", "assist", "client", "plugin", "modapi", "mojang", "shader",
        "events", "github", "recipe", "render", "packet", "events", "preinit", "preload", "machine",
        "reflect", "channel", "general", "handler", "content", "systems", "modules", "service",
        "scripts", "network", "fastutil", "optifine", "internal", "platform", "override", "fabricmc",
        "neoforge", "external", "injection", "listeners", "scheduler", "minecraft", "universal",
        "multipart", "neoforged", "microsoft", "transformer", "transformers", "minecraftforge",
        "blockentity", "spongepowered", "electronwill", "concurrent"
    )

    // 从堆栈中提取 Mod ID 关键词。若失败则返回空列表。
    fun analyzeStackKeyword(stackLog: String): List<String> {
        val es = "\n" + stackLog + "\n"

        // 正则匹配
        val results = mutableListOf<String>()
        results.addAll(
            CrashUtils.regexSeekAllGroup(
                "\\n[^{]+([a-zA-Z_]+\\w+\\.[a-zA-Z_]+[\\w.]+)(?=\\.[\\w.$]+\\.)",
                es,
                1
            )
        )
        results.addAll(
            CrashUtils.regexSeekAllGroup(
                "at [^(]+?\\.\\w+\\$\\w+\\$([\\w\\$]+?)(?=\\$\\w+\\()",
                es,
                1
            ).map { s -> s.replace('$', '.') }
        )
        // 去重
        val seen = mutableSetOf<String>()
        val deduped = results.filter { s -> seen.add(s) }

        // 检查堆栈开头，过滤掉框架包
        val possibleStacks = mutableListOf<String>()
        for (stack in deduped) {
            if (IGNORE_STACKS.any { p -> stack.startsWith(p) }) {
                continue
            }
            possibleStacks.add(stack.trim())
        }
        val seen2 = mutableSetOf<String>()
        val uniqueStacks = possibleStacks.filter { s -> seen2.add(s) }

        if (uniqueStacks.isEmpty()) {
            return emptyList()
        }

        // 检查堆栈关键词（取包名前最多 4 节中的有效词作为可能的 Mod ID）
        val possibleWords = mutableListOf<String>()
        for (stack in uniqueStacks) {
            val splited = stack.split('.')
            val max = Math.min(3, splited.size - 1)
            for (i in 0..max) {
                val word = splited[i]
                if (word.length <= 2 || word.startsWith("func_")) {
                    continue
                }
                val wl = word.lowercase()
                if (WORD_STOPLIST.any { w -> w == wl }) {
                    continue
                }
                possibleWords.add(word.trim())
            }
        }
        val seen3 = mutableSetOf<String>()
        val uniqueWords = possibleWords.filter { s -> seen3.add(s) }

        return if (uniqueWords.size > 10) {
            // 关键词过多，视为匹配出错，不纳入考虑
            emptyList()
        } else {
            uniqueWords
        }
    }

    // 根据 Mod 关键词尝试获取实际的 Mod 名称。若失败则返回 null。
    fun analyzeModName(
        keywords: List<String>,
        logCrash: String?,
        logMcDebug: String?
    ): List<String>? {
        val modFileNames = mutableListOf<String>()

        // 预处理关键词（分割括号）
        val realKeywords = mutableListOf<String>()
        for (kw in keywords) {
            for (sub in kw.split('(')) {
                val t = sub.trim(' ', ')')
                if (t.isNotEmpty()) {
                    realKeywords.add(t)
                }
            }
        }

        // 从崩溃报告获取 Mod 信息
        if (logCrash != null) {
            val lc = logCrash
            if (lc.contains("A detailed walkthrough of the error")) {
                var details = lc.replace("A detailed walkthrough of the error", "¨")
                val isFabricDetail = details.contains("Fabric Mods")
                if (isFabricDetail) {
                    details = details.replace("Fabric Mods", "¨")
                }
                details = CrashUtils.afterLast(details, "¨")

                // [Forge] 获取所有包含 .jar 的行；[Fabric] 获取所有含 Mod 信息的行
                val modNameLines = mutableListOf<String>()
                for (line in details.split('\n')) {
                    val hasSingleJar = countOccurrences(line.lowercase(), ".jar") == 1
                    val isFabricModLine = isFabricDetail
                        && line.startsWith("\t\t")
                        && CrashUtils.regexSeek(line, "\\t\\tfabric[\\w-]*: Fabric") == null
                    if (hasSingleJar || isFabricModLine) {
                        modNameLines.add(line)
                    }
                }

                // 获取 Mod ID 与关键词的匹配行
                val hintLines = mutableListOf<String>()
                for (kw in realKeywords) {
                    val kwLower = kw.lowercase().replace("_", "")
                    for (modString in modNameLines) {
                        val realMod = modString.lowercase().replace("_", "")
                        if (!realMod.contains(kwLower)) {
                            continue
                        }
                        val lower = modString.lowercase()
                        if (lower.contains("minecraft.jar")
                            || lower.contains(" forge-")
                            || lower.contains(" mixin-")
                        ) {
                            continue
                        }
                        hintLines.add(modString.trim('\n'))
                        break
                    }
                }
                val seen = mutableSetOf<String>()
                val uniqueHintLines = hintLines.filter { s -> seen.add(s) }

                // 从 Mod 匹配行中提取 .jar 文件的名称
                for (line in uniqueHintLines) {
                    val name = if (isFabricDetail) {
                        // Fabric: 取冒号后到下个空白前的部分
                        CrashUtils.regexSeekGroup(line, ": ([^\\n]+)(?= [^\\n]+)", 1)
                    } else {
                        // Forge: 括号内或 (tab/| 之后) 的 .jar
                        CrashUtils.regexSeekGroup(line, "(?:\\(|(?:\\t\\t|\\| ))([^\\t|]+\\.jar)", 1)
                    }
                    if (name != null && !modFileNames.contains(name)) {
                        modFileNames.add(name)
                    }
                }
            }
        }

        // 从 debug.log 获取 Mod 信息
        if (logMcDebug != null) {
            val debug = logMcDebug
            // Forge: Found valid mod file YungsBetterStrongholds-...jar with {betterstrongholds} mods
            val modNameLines = CrashUtils.regexSeekAllGroup("(?m)valid mod file (.*)", debug, 1)

            val hintLines = mutableListOf<String>()
            for (kw in realKeywords) {
                for (modString in modNameLines) {
                    if (modString.contains("{" + kw + "}")) {
                        hintLines.add(modString)
                    }
                }
            }
            val seen = mutableSetOf<String>()
            val uniqueHintLines = hintLines.filter { s -> seen.add(s) }

            for (line in uniqueHintLines) {
                val name = CrashUtils.regexSeek(line, ".*(?= with)")
                if (name != null && !modFileNames.contains(name)) {
                    modFileNames.add(name)
                }
            }
        }

        return if (modFileNames.isEmpty()) null else modFileNames
    }

    // 统计 sub 在 s 中非重叠出现的次数（对应 Rust str::matches(sub).count()）
    private fun countOccurrences(s: String, sub: String): Int {
        var count = 0
        var idx = s.indexOf(sub)
        while (idx >= 0) {
            count++
            idx = s.indexOf(sub, idx + sub.length)
        }
        return count
    }

    // 尝试从关键字获取 Mod 名称，若失败则返回原关键字列表。
    fun tryAnalyzeModName(
        orig: List<String>,
        logCrash: String?,
        logMcDebug: String?
    ): List<String> {
        if (orig.isEmpty()) {
            return orig
        }
        return analyzeModName(orig, logCrash, logMcDebug) ?: orig
    }

    // 为单关键词适配的便捷辅助：返回 String 列表
    fun tryAnalyzeModNameOne(
        kw: String,
        logCrash: String?,
        logMcDebug: String?
    ): List<String> {
        val orig = if (kw.isEmpty()) emptyList() else listOf(kw)
        if (orig.isEmpty()) {
            return orig
        }
        return analyzeModName(orig, logCrash, logMcDebug) ?: orig
    }
}
