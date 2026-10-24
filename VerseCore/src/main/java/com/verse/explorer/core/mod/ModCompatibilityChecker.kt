package com.verse.explorer.core.mod

import com.google.gson.JsonParser
import java.io.File
import java.util.zip.ZipFile

/**
 * 启动前模组兼容性预检。
 *
 * 背景（用户反馈「模组玩不了，我试了两遍」）：模组与目标版本不兼容
 * （加载器不对 / MC 版本不符 / Java 版本不够）时，游戏只会静默不加载或
 * 进游戏即崩，用户完全不知道原因。本检查把这类问题提前到启动期，
 * 给出明确条目式的失败原因。
 *
 * 刻意的保守原则：**拿不准就不报**。版本约束解析不了（快照号、
 * 未知的区间写法）一律放行，宁可漏报也不能误报拦住能玩的组合。
 *
 * 只读 zip 与文本，不依赖 Android，可直接单测。
 */
object ModCompatibilityChecker {

    data class ModIssue(
        val file: String,
        val modName: String,
        val reason: String
    )

    data class Report(
        /** 参与检查的模组数量（无法解析元数据的 jar 不计入） */
        val modCount: Int,
        val issues: List<ModIssue>
    ) {
        val hasIssues: Boolean get() = issues.isNotEmpty()
    }

    fun check(
        modsDir: File?,
        mcVersion: String,
        loaderType: String?,
        javaMajor: Int
    ): Report {
        val jars = modsDir?.listFiles()
            ?.filter {
                it.isFile && it.name.endsWith(".jar", ignoreCase = true) &&
                    !it.name.endsWith(".disabled", ignoreCase = true)
            }
            ?.sortedBy { it.name.lowercase() }
            ?: return Report(0, emptyList())

        val issues = mutableListOf<ModIssue>()
        var checked = 0

        for (jar in jars) {
            // 元数据解析不出来的交给冲突扫描（UNREADABLE），这里不重复报
            val meta = runCatching { ModConflictScanner.readJar(jar) }.getOrNull() ?: continue
            checked++

            checkLoader(meta.loader, loaderType)?.let {
                issues += ModIssue(jar.name, meta.name, it)
            }

            val constraints = runCatching { readConstraints(jar) }.getOrNull() ?: continue

            constraints.mcRanges.forEach { range ->
                if (McVersionRange.satisfies(mcVersion, range) == false) {
                    issues += ModIssue(
                        jar.name, meta.name,
                        "仅支持 MC $range，当前版本是 $mcVersion"
                    )
                }
            }

            constraints.javaRange?.let { range ->
                if (javaMajor > 0 && McVersionRange.satisfies(javaMajor.toString(), range) == false) {
                    issues += ModIssue(
                        jar.name, meta.name,
                        "需要 $range，当前启动使用 Java $javaMajor"
                    )
                }
            }
        }

        return Report(checked, issues)
    }

    /** 加载器匹配检查；返回 null 表示兼容 */
    private fun checkLoader(modLoader: String, versionLoader: String?): String? {
        // 原版没有加载器，任何 mod jar 都不会生效
        versionLoader ?: return "该版本未安装 mod 加载器（原版），模组不会生效"
        return when (modLoader) {
            "fabric" ->
                if (versionLoader == "fabric" || versionLoader == "quilt") null
                else "Fabric 模组无法在 ${versionLoader} 版本上运行"
            "forge" ->
                if (versionLoader == "forge") null
                else "Forge 模组无法在 ${versionLoader} 版本上运行"
            "neoforge" ->
                if (versionLoader == "neoforge") null
                else "NeoForge 模组无法在 ${versionLoader} 版本上运行"
            // quilt 原生 mod（quilt.mod.json）readJar 读不到元数据，到不了这里
            else -> null
        }
    }

    /** 模组 jar 内声明的 minecraft / java 版本约束 */
    private data class Constraints(val mcRanges: List<String>, val javaRange: String?)

    /**
     * 读取约束声明：
     * - Fabric/Quilt：fabric.mod.json 的 depends.minecraft（字符串或数组）、depends.java
     * - Forge/NeoForge：META-INF/mods.toml（或 neoforge.mods.toml）中
     *   modId 为 minecraft / java 的依赖段 versionRange
     * 都没有时返回空约束（即不检查）。
     */
    private fun readConstraints(jar: File): Constraints {
        ZipFile(jar).use { zip ->
            zip.getEntry("fabric.mod.json")?.let { entry ->
                val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                if (json != null) {
                    val depends = json.getAsJsonObject("depends")
                    if (depends != null) {
                        val mc = mutableListOf<String>()
                        when (val v = depends.get("minecraft")) {
                            null, com.google.gson.JsonNull.INSTANCE -> {}
                            is com.google.gson.JsonArray ->
                                v.forEach { e -> if (e.isJsonPrimitive) mc += e.asString }
                            is com.google.gson.JsonPrimitive -> if (v.isString) mc += v.asString
                            else -> {}
                        }
                        val java = depends.get("java")
                            ?.takeIf { it.isJsonPrimitive && it.asString.isNotBlank() }?.asString
                        return Constraints(mc, java)
                    }
                }
            }

            val tomlEntry = zip.getEntry("META-INF/neoforge.mods.toml")
                ?: zip.getEntry("META-INF/mods.toml")
            if (tomlEntry != null) {
                val text = zip.getInputStream(tomlEntry).bufferedReader().use { it.readText() }
                return Constraints(parseTomlConstraints(text), null)
            }
        }
        return Constraints(emptyList(), null)
    }

    /** 从 Forge toml 的 `[[dependencies.<modid>]]` 段提取 minecraft/java 的 versionRange */
    private fun parseTomlConstraints(text: String): List<String> {
        val ranges = mutableListOf<String>()
        var inDependencySection = false
        var depId = ""
        var rangeValue = ""

        fun flush() {
            if (inDependencySection && depId.equals("minecraft", true) && rangeValue.isNotBlank()) {
                ranges += rangeValue
            }
            depId = ""
            rangeValue = ""
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("[[dependencies.") -> {
                    flush()
                    inDependencySection = true
                }

                line.startsWith("[") -> {
                    flush()
                    inDependencySection = false
                }

                line.contains('=') && inDependencySection -> {
                    val key = line.substringBefore('=').trim()
                    val value = line.substringAfter('=').trim().trim('"')
                    when (key) {
                        "modId" -> if (depId.isBlank()) depId = value
                        "versionRange" -> if (rangeValue.isBlank()) rangeValue = value
                    }
                }
            }
        }
        flush()
        return ranges
    }
}

/**
 * MC 版本约束匹配（保守实现）。
 *
 * 支持两类写法：
 * - Fabric semver 条件：`1.20.1`、`>=1.20 <1.21`、`~1.20.1`、`^1.20.1`、`>=1.20 || >=26`，
 *   条件间空格 = AND，`||` = OR；
 * - Forge maven 区间：`[1.20,1.21)`、`[1.20]`、`[1.20,)`、`(,1.21)`。
 *
 * 返回 true/false；**无法判定时返回 null**（调用方必须把 null 当「放行」处理）。
 * 版本号含非数字段（快照号 26w14a、1.20.1-rc1 等）时只支持精确相等比较。
 */
internal object McVersionRange {

    fun satisfies(actual: String, range: String): Boolean? {
        val r = range.trim()
        if (r.isEmpty()) return null

        // maven 区间（可多段 OR：`[1.16,1.17) [1.18,)`）
        if (r.startsWith("[") || r.startsWith("(")) {
            var anyTrue = false
            var hasUndecided = false
            r.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { seg ->
                when (mavenSegment(actual, seg)) {
                    true -> anyTrue = true
                    null -> hasUndecided = true
                    false -> {}
                }
            }
            return if (anyTrue) true else if (hasUndecided) null else false
        }

        // fabric semver 条件
        var hasUndecided = false
        r.split("||").forEach { seg ->
            when (segmentSatisfied(actual, seg)) {
                true -> return true
                null -> hasUndecided = true
                false -> {}
            }
        }
        return if (hasUndecided) null else false
    }

    /** 单个 OR 段：空格分隔的条件 AND */
    private fun segmentSatisfied(actual: String, segment: String): Boolean? {
        var hasUndecided = false
        segment.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { raw ->
            val conds = expandCondition(raw) ?: return null
            conds.forEach { cond ->
                when (conditionSatisfied(actual, cond)) {
                    false -> return false
                    null -> hasUndecided = true
                    true -> {}
                }
            }
        }
        return if (hasUndecided) null else true
    }

    /**
     * ~ / ^ 展开成上下界条件；无法展开返回 null（整体不可判定）。
     * 两者都按「同 minor 兼容」处理（~1.20 / ~1.20.1 / ^1.20.1 → <x.(y+1)）：
     * MC 的 minor 递增（1.20 → 1.21）必然破坏 mod，纯 semver 的 <(x+1) 会放过它，
     * 而 mod 作者写 ^1.20.1 的实际意图就是 1.20.x。
     */
    private fun expandCondition(cond: String): List<String>? {
        val c = cond.trim()
        if (c.startsWith("~") || c.startsWith("^")) {
            val t = c.removePrefix("~").removePrefix("^")
            val parts = t.split('.')
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return listOf(">=$t", "<${parts[0]}.${minor + 1}")
        }
        return listOf(c)
    }

    private fun conditionSatisfied(actual: String, cond: String): Boolean? {
        val c = cond.trim()
        val op = Regex("^(>=|<=|>|<|=)").find(c)?.value ?: ""
        val target = c.removePrefix(op).trim()
        if (target.isEmpty()) return null
        val cmp = compareMc(actual, target) ?: return null
        return when (op) {
            ">=" -> cmp >= 0
            "<=" -> cmp <= 0
            ">" -> cmp > 0
            "<" -> cmp < 0
            else -> cmp == 0
        }
    }

    /** 单个 maven 区间段：`[a,b)` / `[a]` / `[a,)` / `(,b]` */
    private fun mavenSegment(actual: String, seg: String): Boolean? {
        val open = seg.firstOrNull() ?: return null
        val close = seg.lastOrNull() ?: return null
        if (open !in "[(" || close !in "])") return null
        val inner = seg.substring(1, seg.length - 1)
        val parts = inner.split(',')
        if (parts.size == 1) {
            // `[a]` 单值闭区间
            return conditionSatisfied(actual, "=${parts[0].trim()}")
        }
        if (parts.size != 2) return null

        var hasUndecided = false
        val lo = parts[0].trim()
        val hi = parts[1].trim()
        if (lo.isNotEmpty()) {
            when (conditionSatisfied(actual, (if (open == '[') ">=" else ">") + lo)) {
                false -> return false
                null -> hasUndecided = true
                true -> {}
            }
        }
        if (hi.isNotEmpty()) {
            when (conditionSatisfied(actual, (if (close == ']') "<=" else "<") + hi)) {
                false -> return false
                null -> hasUndecided = true
                true -> {}
            }
        }
        return if (hasUndecided) null else true
    }

    /** MC 版本比较；含非数字段的版本只支持相等判断，其余返回 null（不可比） */
    internal fun compareMc(a: String, b: String): Int? {
        val ta = a.trim().split('.')
        val tb = b.trim().split('.')
        val allNumeric = (ta + tb).all { it.toIntOrNull() != null }
        if (!allNumeric) return if (a.trim() == b.trim()) 0 else null
        for (i in 0 until maxOf(ta.size, tb.size)) {
            val x = ta.getOrNull(i)?.toIntOrNull() ?: 0
            val y = tb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return x - y
        }
        return 0
    }
}
