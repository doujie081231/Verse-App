// crash_analyzer/crit1.rs — 一级崩溃分析：高置信度关键字匹配
// 职责：对 logCrash/logMc/logHs 做 includes + 正则提取，识别 Java、Mod 解压、Mixin、显卡、内存等问题

package com.verse.explorer.app.crash

object Crit1 {

    // 一级分析：高置信度关键字匹配
    fun analyze(logs: PreparedLogs): LinkedHashMap<CrashReason, List<String>> {
        // 内部使用 MutableList 以匹配 CrashUtils.appendReason(MutableMap<CrashReason, MutableList<String>>) 的签名
        val reasons = LinkedHashMap<CrashReason, MutableList<String>>()
        val logMc = logs.logMc
        val logHs = logs.logHs
        val logCrash = logs.logCrash
        val logMcDebug = logs.logMcDebug

        // 空白分析
        if (logMc == null && logHs == null && logCrash == null) {
            CrashUtils.appendReason(reasons, CrashReason.NoAnalysisFiles, null)
            return LinkedHashMap<CrashReason, List<String>>(reasons)
        }

        // 崩溃报告分析（高优先级）
        if (logCrash != null) {
            val lc = logCrash
            if (lc.contains("Unable to make protected final java.lang.Class java.lang.ClassLoader.defineClass")) {
                CrashUtils.appendReason(reasons, CrashReason.JavaVersionTooHigh, null)
            }
            if (lc.contains("Failed loading config file ")) {
                val modName = CrashUtils.regexSeekGroup(lc, "Failed loading config file .+ for modid ([^\\n]+)", 1)?.trimEnd('\n')
                val configPath = CrashUtils.regexSeekGroup(lc, "Failed loading config file (.+) of type", 1)?.trimEnd('\n')
                val additional = mutableListOf<String>()
                if (modName != null) {
                    additional.addAll(StackAnalyzer.tryAnalyzeModNameOne(modName, logCrash, logMcDebug))
                }
                if (configPath != null) {
                    additional.add(configPath)
                }
                CrashUtils.appendReason(reasons, CrashReason.ModConfigCrash, additional)
            }
        }

        // 游戏日志分析
        if (logMc != null) {
            val lm = logMc
            if (lm.contains("Unrecognized option:")) {
                CrashUtils.appendReason(reasons, CrashReason.JavaArgsError, null)
            }
            if (lm.contains("Found multiple arguments for option fml.forgeVersion, but you asked for only one")) {
                CrashUtils.appendReason(reasons, CrashReason.MultipleForge, null)
            }
            if (lm.contains("The driver does not appear to support OpenGL")) {
                CrashUtils.appendReason(reasons, CrashReason.GpuNoOpenGL, null)
            }
            if (lm.contains("java.lang.ClassCastException: java.base/jdk")
                || lm.contains("java.lang.ClassCastException: class jdk.")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.UsingJDK, null)
            }
            if (lm.contains("java.lang.NoSuchMethodError: 'void net.minecraft.client.renderer.texture.SpriteContents.<init>")
                || lm.contains("java.lang.NoSuchMethodError: 'java.lang.String com.mojang.blaze3d.systems.RenderSystem.getBackendDescription")
                || lm.contains("java.lang.NoSuchMethodError: 'void net.minecraft.client.renderer.block.model.BakedQuad.<init>")
                || lm.contains("java.lang.NoSuchMethodError: 'void net.minecraftforge.client.gui.overlay.ForgeGui.renderSelectedItemName")
                || lm.contains("java.lang.NoSuchMethodError: 'void net.minecraft.server.level.DistanceManager")
                || lm.contains("java.lang.NoSuchMethodError: 'net.minecraft.network.chat.FormattedText net.minecraft.client.gui.Font.ellipsize")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.OptiFineIncompatible, null)
            }
            if (lm.contains("Open J9 is not supported")
                || lm.contains("OpenJ9 is incompatible")
                || lm.contains(".J9VMInternals.")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.UsingOpenJ9, null)
            }
            if (lm.contains("java.lang.NoSuchFieldException: ucp")
                || lm.contains("because module java.base does not export")
                || lm.contains("java.lang.ClassNotFoundException: jdk.nashorn.api.scripting.NashornScriptEngineFactory")
                || lm.contains("java.lang.ClassNotFoundException: java.lang.invoke.LambdaMetafactory")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.JavaVersionTooHigh, null)
            }
            if (lm.contains("The directories below appear to be extracted jar files. Fix this before you continue.")
                || lm.contains("Extracted mod jars found, loading will NOT continue")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.ModFileExtracted, null)
            }
            if (lm.contains("java.lang.ClassNotFoundException: org.spongepowered.asm.launch.MixinTweaker")) {
                CrashUtils.appendReason(reasons, CrashReason.MixinBootstrapError, null)
            }
            if (lm.contains("Couldn't set pixel format")) {
                CrashUtils.appendReason(reasons, CrashReason.PixelFormatNotAccelerated, null)
            }
            if (lm.contains("java.lang.OutOfMemoryError") || lm.contains("an out of memory error")) {
                CrashUtils.appendReason(reasons, CrashReason.OutOfMemory, null)
            }
            if (lm.contains("java.lang.RuntimeException: Shaders Mod detected. Please remove it, OptiFine has built-in support for shaders.")) {
                CrashUtils.appendReason(reasons, CrashReason.ShadersModWithOptiFine, null)
            }
            if (lm.contains("java.lang.NoSuchMethodError: sun.security.util.ManifestEntryVerifier")
                || lm.contains("java.lang.NoSuchMethodError: 'void sun.security.util.ManifestEntryVerifier")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.ModLoaderVersionIncompatible, null)
            }
            if (lm.contains("1282: Invalid operation")) {
                CrashUtils.appendReason(reasons, CrashReason.OpenGL1282Error, null)
            }
            if (lm.contains("signer information does not match signer information of other classes in the same package")) {
                val className = CrashUtils.regexSeekGroup(lm, "class \"([^']+)\"='s signer information", 1)?.trimEnd('\n')
                CrashUtils.appendReason(reasons, CrashReason.SecurityException, className?.let { listOf(it) })
            }
            if (lm.contains("Maybe try a lower resolution resourcepack?")) {
                CrashUtils.appendReason(reasons, CrashReason.TextureTooLarge, null)
            }
            if (lm.contains("java.lang.NoSuchMethodError: net.minecraft.world.server.ChunkManager\$ProxyTicketManager.shouldForceTicks(J)Z")
                && lm.contains("OptiFine")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.OptiFineCannotLoadWorld, null)
            }
            if (lm.contains("com.electronwill.nightconfig.core.io.ParsingException: Not enough data available")
                && !reasons.containsKey(CrashReason.ModConfigCrash)
            ) {
                CrashUtils.appendReason(reasons, CrashReason.NightConfigBug, null)
            }
            if (lm.contains("Cannot find launch target fmlclient, unable to launch")) {
                CrashUtils.appendReason(reasons, CrashReason.ForgeMissing, null)
            }
            if (lm.contains("Invalid paths argument, contained no existing paths")
                && lm.contains("libraries\\net\\minecraftforge\\fmlcore")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.ForgeMissing, null)
            }
            if (lm.contains("Invalid module name: '' is not a Java identifier")) {
                CrashUtils.appendReason(reasons, CrashReason.ModNameContainsSpecialChars, null)
            }
            if (lm.contains("has been compiled by a more recent version of the Java Runtime (class file version 55.0), this version of the Java Runtime only recognizes class file versions up to")
                || lm.contains("java.lang.RuntimeException: java.lang.NoSuchMethodException: no such method: sun.misc.Unsafe.defineAnonymousClass(Class,byte[],Object[])Class/invokeVirtual")
                || lm.contains("java.lang.IllegalArgumentException: The requested compatibility level JAVA_11 could not be set. Level is not supported by the active JRE or ASM version")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.ModRequiresJava11, null)
            }
            if (lm.contains("Unsupported class file major version")
                || lm.contains("Unsupported major.minor version")
                || lm.contains("Level is not supported by the active JRE or ASM version")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.JavaTooOld, null)
            }
            if (lm.contains("Invalid maximum heap size")) {
                CrashUtils.appendReason(reasons, CrashReason.Java32Bit, null)
            }
            if (lm.contains("Could not reserve enough space")) {
                if (lm.contains("for 1048576KB object heap")) {
                    CrashUtils.appendReason(reasons, CrashReason.Java32Bit, null)
                } else {
                    CrashUtils.appendReason(reasons, CrashReason.OutOfMemory, null)
                }
            }

            // 确定的 Mod 导致崩溃
            if (lm.contains("Caught exception from ")) {
                val name = CrashUtils.regexSeekGroup(lm, "Caught exception from ([^\\n]+)", 1)?.trimEnd('\n')
                if (name != null) {
                    val names = StackAnalyzer.tryAnalyzeModNameOne(name, logCrash, logMcDebug)
                    CrashUtils.appendReason(reasons, CrashReason.ModCrashed, names)
                }
            }

            // Mod 重复 / 前置问题
            if (lm.contains("DuplicateModsFoundException")) {
                val jars = CrashUtils.regexSeekAllGroup("(?i)\\n\\t\\w+ : [A-Za-z][^/\\n]+(/|\\\\)([^/\\\\\\n]+\\.jar)", lm, 1)
                CrashUtils.appendReason(reasons, CrashReason.ModDuplicateModFiles, jars)
            }
            if (lm.contains("Found a duplicate mod")) {
                val jars = CrashUtils.regexSeekAllGroup("(?i)[^\\\\/]+\\.jar", lm, 0)
                CrashUtils.appendReason(reasons, CrashReason.ModDuplicateModFiles, prevDistinct(jars))
            }
            if (lm.contains("Found duplicate mods")) {
                val modIds = CrashUtils.regexSeekAllGroup("Mod ID: '(\\w+)' from mod files:", lm, 1)
                val ids = mutableListOf<String>()
                for (m in modIds) {
                    for (line in m.lineSequence()) {
                        if (line.isNotEmpty() && !ids.contains(line)) {
                            ids.add(line)
                        }
                    }
                }
                CrashUtils.appendReason(reasons, CrashReason.ModDuplicateModFiles, ids)
            }
            if (lm.contains("ModResolutionException: Duplicate")) {
                val jars = CrashUtils.regexSeekAllGroup("(?i)[^\\\\/]+\\.jar", lm, 0)
                CrashUtils.appendReason(reasons, CrashReason.ModDuplicateModFiles, prevDistinct(jars))
            }
            if (lm.contains("Incompatible mods found!")) {
                val incompatible = CrashUtils.regexSeekGroup(
                    lm,
                    "Incompatible mods found![\\s\\S]+: ([\\s\\S]+?)(?=\\tat )",
                    1
                )?.let { s ->
                    CrashUtils.beforeFirst(s, "更多信息：")
                        .replace("Some of your mods are incompatible with the game or each other!", "")
                        .trim()
                }
                CrashUtils.appendReason(reasons, CrashReason.ModIncompatible, singleNonempty(incompatible))
            }
            if (lm.contains("Missing or unsupported mandatory dependencies:")) {
                val depsRaw = CrashUtils.regexSeekAllGroup(
                    "(?i)Missing or unsupported mandatory dependencies:([\\n\\r]+\\t(.*))+",
                    lm,
                    0
                )
                val seen = mutableListOf<String>()
                val deps = depsRaw
                    .flatMap { s -> s.split('\n').map { x -> x.trim('\n', '\r', '\t', ' ') } }
                    .filter { x -> x.isNotEmpty() }
                    .fold(mutableListOf<String>()) { acc, x ->
                        if (!seen.contains(x)) {
                            seen.add(x)
                            acc.add(x)
                        }
                        acc
                    }
                CrashUtils.appendReason(reasons, CrashReason.ModMissingDependency, deps)
            }
        }

        // 虚拟机日志分析
        if (logHs != null) {
            val lh = logHs
            if (lh.contains("The system is out of physical RAM or swap space")
                || lh.contains("Out of Memory Error")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.OutOfMemory, null)
            }
            if (lh.contains("EXCEPTION_ACCESS_VIOLATION")) {
                if (lh.contains("# C  [ig")) {
                    CrashUtils.appendReason(reasons, CrashReason.IntelDriverCrash, null)
                }
                if (lh.contains("# C  [atio")) {
                    CrashUtils.appendReason(reasons, CrashReason.AMDDriverCrash, null)
                }
                if (lh.contains("# C  [nvoglv")) {
                    CrashUtils.appendReason(reasons, CrashReason.NVidiaDriverCrash, null)
                }
            }
        }

        // 崩溃报告分析（续）
        if (logCrash != null) {
            val lc = logCrash
            if (lc.contains("maximum id range exceeded")) {
                CrashUtils.appendReason(reasons, CrashReason.TooManyMods, null)
            }
            if (lc.contains("java.lang.OutOfMemoryError")) {
                CrashUtils.appendReason(reasons, CrashReason.OutOfMemory, null)
            }
            if (lc.contains("Pixel format not accelerated")) {
                CrashUtils.appendReason(reasons, CrashReason.PixelFormatNotAccelerated, null)
            }
            if (lc.contains("Manually triggered debug crash")) {
                CrashUtils.appendReason(reasons, CrashReason.ManuallyTriggeredCrash, null)
            }
            if (lc.contains("has mods that were not found")
                && CrashUtils.regexCheck(lc, "The Mod File [^\\n]+optifine\\\\OptiFine[^\\n]+ has mods that were not found")
            ) {
                CrashUtils.appendReason(reasons, CrashReason.OptiFineIncompatible, null)
            }
            // Mod 导致的崩溃
            if (lc.contains("-- MOD ")) {
                val logCrashMod = CrashUtils.between(lc, "-- MOD ", "Failure message:")
                if (logCrashMod.lowercase().contains(".jar")) {
                    val modFile = CrashUtils.regexSeekGroup(logCrashMod, "Mod File: (.+)", 1)?.trimEnd('\n')
                    CrashUtils.appendReason(reasons, CrashReason.ModCrashed, modFile?.let { listOf(it) })
                } else {
                    val failureMsg = CrashUtils.regexSeekGroup(lc, "Failure message: ([\\w\\W]+?)(?=\\tMod)", 1)
                        ?.replace("\t", " ")?.trimEnd('\n')?.trim()
                    CrashUtils.appendReason(reasons, CrashReason.ModNoInfo, failureMsg?.let { listOf(it) })
                }
            }
            if (lc.contains("Multiple entries with same key: ")) {
                val name = CrashUtils.regexSeekGroup(lc, "Multiple entries with same key: ([^=]+)", 1)?.trimEnd('\n')
                if (name != null) {
                    val names = StackAnalyzer.tryAnalyzeModNameOne(name, logCrash, logMcDebug)
                    CrashUtils.appendReason(reasons, CrashReason.ModCrashed, names)
                }
            }
            if (lc.contains("LoaderExceptionModCrash: Caught exception from ")) {
                val name = CrashUtils.regexSeekGroup(lc, "LoaderExceptionModCrash: Caught exception from ([^\\n]+)", 1)?.trimEnd('\n')
                if (name != null) {
                    val names = StackAnalyzer.tryAnalyzeModNameOne(name, logCrash, logMcDebug)
                    CrashUtils.appendReason(reasons, CrashReason.ModCrashed, names)
                }
            }
            if (lc.contains("Failed loading config file ")) {
                val modName = CrashUtils.regexSeekGroup(lc, "Failed loading config file .+ for modid ([^\\n]+)", 1)?.trimEnd('\n')
                val configPath = CrashUtils.regexSeekGroup(lc, "Failed loading config file (.+) of type", 1)?.trimEnd('\n')
                val additional = mutableListOf<String>()
                if (modName != null) {
                    additional.addAll(StackAnalyzer.tryAnalyzeModNameOne(modName, logCrash, logMcDebug))
                }
                if (configPath != null) {
                    additional.add(configPath)
                }
                CrashUtils.appendReason(reasons, CrashReason.ModConfigCrash, additional)
            }
        }

        val result = LinkedHashMap<CrashReason, List<String>>()
        for ((key, value) in reasons) {
            result[key] = value
        }
        return result
    }

    private fun prevDistinct(v: List<String>): List<String> {
        val seen = mutableListOf<String>()
        return v.filter { x ->
            if (x.isEmpty() || seen.contains(x)) {
                false
            } else {
                seen.add(x)
                true
            }
        }
    }

    private fun singleNonempty(v: String?): List<String>? {
        return v?.takeIf { s -> s.isNotEmpty() }?.let { s -> listOf(s) }
    }
}
