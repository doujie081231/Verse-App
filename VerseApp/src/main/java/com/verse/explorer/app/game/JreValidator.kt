package com.verse.explorer.app.game

import java.io.File

/**
 * JRE 目录校验纯函数集合。
 *
 * 从 JreResolver 抽出的无副作用逻辑（只读文件系统，不碰 Context/APK assets），
 * 便于单元测试用临时目录覆盖各缺失分支。语义与原实现逐条对齐：
 * 文件探测为递归搜索（与 JavaRuntimeDetector.findFileInDir 相同行为）。
 */
internal object JreValidator {

    /**
     * 校验 JRE 目录能否用于启动：返回 null 表示可用，否则返回缺失项。
     * JRE8 的核心运行库是 lib/rt.jar（安装时由 rt.jar.pack 还原），JDK9+ 是 lib/modules；
     * 与安装器的完整性判定保持一致，避免出现「安装器说已安装、启动又说找不到」的循环。
     */
    fun jreInvalidReason(home: String): String? {
        val dir = File(home)
        if (!dir.exists()) return "目录不存在"
        if (!File(dir, "bin/java").exists()) return "缺少 bin/java"
        val libDir = File(dir, "lib")
        if (!libDir.isDirectory) return "缺少 lib/"
        if (!hasFileInTree(libDir, "libjli.so")) return "缺少 libjli.so"
        if (!hasFileInTree(libDir, "libjvm.so")) return "缺少 libjvm.so"
        if (!hasFileInTree(libDir, "libawt.so")) return "缺少 libawt.so"
        if (!hasFileInTree(libDir, "libfontmanager.so")) return "缺少 libfontmanager.so"
        val classesOk = hasNonEmptyFileInTree(libDir, "modules") ||
            (hasNonEmptyFileInTree(libDir, "rt.jar") && isRtJarUsable(libDir))
        if (!classesOk) return "核心运行库(modules/rt.jar)缺失或损坏"
        return null
    }

    /**
     * JRE8 核心库可用性：lib 目录下的 rt.jar 能读出 java/lang/Object.class 才算完好。
     * 局部损坏（中央目录正常、条目数据损坏）无法靠文件大小判断，用条目可读性兜底。
     */
    fun isRtJarUsable(libDir: File): Boolean = runCatching {
        val rtJar = findFileInTree(libDir, "rt.jar") ?: return@runCatching false
        java.util.zip.ZipFile(rtJar).use { zip ->
            zip.getEntry("java/lang/Object.class") != null &&
                zip.getInputStream(zip.getEntry("java/lang/Object.class")).use { it.read() } >= 0
        }
    }.getOrDefault(false)

    /** 递归查找目录树中第一个指定名字的文件，找不到返回 null。 */
    fun findFileInTree(dir: File, name: String): File? {
        if (!dir.exists()) return null
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name == name) return f
            if (f.isDirectory) findFileInTree(f, name)?.let { return it }
        }
        return null
    }

    private fun hasFileInTree(dir: File, name: String): Boolean {
        if (!dir.exists()) return false
        dir.listFiles()?.forEach {
            if (it.isFile && it.name == name) return true
            if (it.isDirectory && hasFileInTree(it, name)) return true
        }
        return false
    }

    private fun hasNonEmptyFileInTree(dir: File, name: String): Boolean {
        if (!dir.exists()) return false
        dir.listFiles()?.forEach {
            if (it.isFile && it.name == name && it.length() > 0) return true
            if (it.isDirectory && hasNonEmptyFileInTree(it, name)) return true
        }
        return false
    }
}
