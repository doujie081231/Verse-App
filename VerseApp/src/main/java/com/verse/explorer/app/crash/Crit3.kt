// crash_analyzer/crit3.rs — 三级崩溃分析
// 职责：极短输出、Mod 解析失败、Mod 实例创建失败、特定方块/实体导致崩溃
// 运行优先级：最低，仅在更高级别未命中时执行。

package com.verse.explorer.app.crash

object Crit3 {

    /** 渲染器缺失的 LWJGL GL 原生函数（org.lwjgl.opengl.GLxxC.nglXxx） */
    private val RENDERER_GL_FUNCTION =
        Regex("org\\.lwjgl\\.opengl\\.GL\\d+C?\\.ngl\\w+")

    // 三级分析
    fun analyze(logs: PreparedLogs): LinkedHashMap<CrashReason, List<String>> {
        // 内部使用 MutableList 以匹配 CrashUtils.appendReason(MutableMap<CrashReason, MutableList<String>>) 的签名
        val reasons = LinkedHashMap<CrashReason, MutableList<String>>()
        val logMc = logs.logMc
        val logHs = logs.logHs
        val logCrash = logs.logCrash
        val logMcDebug = logs.logMcDebug

        // 游戏日志分析
        if (logMc != null) {
            val lm = logMc
            // 极短的程序输出
            val isNormal = lm.contains("at net.") || lm.contains("INFO]")
            if (!isNormal && logHs == null && logCrash == null && lm.length < 100) {
                CrashUtils.appendReason(reasons, CrashReason.ShortLogOutput, listOf(lm))
            }

            // Mod 解析错误（常见于 Fabric 前置校验失败）
            if (lm.contains("Mod resolution failed")) {
                CrashUtils.appendReason(reasons, CrashReason.ModLoaderError, null)
            }

            // Mixin 失败可能造成大量 Mod 实例创建失败，因此放到低优先级
            if (lm.contains("Failed to create mod instance.")) {
                val name = CrashUtils.regexSeekGroup(lm, "Failed to create mod instance\\. ModID: ([^,]+)", 1)
                    ?: CrashUtils.regexSeekGroup(lm, "Failed to create mod instance\\. ModId ([^\\n]+)(?= for )", 1)
                if (name != null) {
                    val names = StackAnalyzer.tryAnalyzeModNameOne(name.trimEnd('\n'), logCrash, logMcDebug)
                    CrashUtils.appendReason(reasons, CrashReason.ModInitError, names)
                } else {
                    CrashUtils.appendReason(reasons, CrashReason.ModInitError, null)
                }
            }
        }

        // 崩溃报告分析
        if (logCrash != null) {
            val lc = logCrash
            // 仅支持桌面 Linux（glibc）的模组：Android 是 bionic，
            // dlopen("libc.so.6") 必然抛 UnsatisfiedLinkError（如 Voxy）
            if (lc.contains("libc.so.6") &&
                (lc.contains("UnsatisfiedLinkError") || lc.contains("Failed to dynamically load library"))
            ) {
                CrashUtils.appendReason(reasons, CrashReason.ModNeedsGlibc, null)
            }

            // 渲染器未实现游戏需要的 GL 函数：崩溃报告形如
            // java.lang.UnsatisfiedLinkError: 'void org.lwjgl.opengl.GL32C.nglDeleteSync(long)'
            // 这类缺失由渲染器插件（MobileGlues / ANGLE 等 GL 兼容层）决定，不是游戏本身
            if (lc.contains("UnsatisfiedLinkError") && RENDERER_GL_FUNCTION.containsMatchIn(lc)) {
                val fn = CrashUtils.regexSeekGroup(
                    lc, "(org\\.lwjgl\\.opengl\\.GL\\d+C?\\.ngl\\w+)", 1
                ) ?: ""
                CrashUtils.appendReason(reasons, CrashReason.RendererMissingGlFunction, listOf(fn))
            }

            // 特定方块导致崩溃
            if (lc.contains("\tBlock location: World: ")) {
                val block = CrashUtils.regexSeekGroup(lc, "\\tBlock: Block\\{([^}]+)", 1) ?: ""
                val location = CrashUtils.regexSeekGroup(lc, "\\tBlock location: World: (\\([^)]+\\))", 1) ?: ""
                val additional = listOf(("$block $location").trim())
                CrashUtils.appendReason(reasons, CrashReason.BlockCrash, additional)
            }

            // 特定实体导致崩溃
            if (lc.contains("\tEntity's Exact location: ")) {
                val entity = CrashUtils.regexSeekGroup(lc, "\\tEntity Type: ([^\\n]+)(?= \\()", 1) ?: ""
                val location = CrashUtils.regexSeekGroup(lc, "\\tEntity's Exact location: ([^\\n]+)", 1) ?: ""
                val additional = listOf(("$entity (" + location.trimEnd('\n') + ")").trim())
                CrashUtils.appendReason(reasons, CrashReason.EntityCrash, additional)
            }
        }

        val result = LinkedHashMap<CrashReason, List<String>>()
        for ((key, value) in reasons) {
            result[key] = value
        }
        return result
    }
}
