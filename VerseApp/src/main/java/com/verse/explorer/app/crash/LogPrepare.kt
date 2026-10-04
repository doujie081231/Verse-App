// 对应 crash_analyzer/prepare.rs — 日志预处理与分类
// 职责：按文件名分类（HsErr/CrashReport/MinecraftLog/ExtraLog），截取头尾行用于分析

package com.verse.explorer.app.crash

/**
 * 收集到的原始日志文件
 * 对应 crash_analyzer/file_collector.rs 的 RawLogFile
 */
data class RawLogFile(
    val path: String,
    val lines: List<String>
)

/** 文件类型分类 */
private enum class FileType {
    HsErr,
    CrashReport,
    MinecraftLog,
    ExtraLogFile,
    ExtraReportFile
}

/** 预处理后的日志内容 */
data class PreparedLogs(
    val logMc: String? = null,
    val logMcDebug: String? = null,
    val logHs: String? = null,
    val logCrash: String? = null,
    val outputFiles: List<String> = emptyList()
)

object LogPrepare {

    /**
     * 步骤 2：预处理日志文件
     *
     * 流程：
     * 1. 按文件名分类
     * 2. 无 MinecraftLog 时用 ExtraLogFile 顶替
     * 3. 按类型截取头尾行
     *    - HsErr: 头200 + 尾100 行（去重）
     *    - CrashReport: 头300 + 尾700 行（去重）
     *    - MinecraftLog: 优先启动器输出日志，其次 latest.log 头1500+尾500 行
     *    - logMcDebug: debug.log 头1000 行
     * 4. 返回是否找到有效日志
     */
    fun prepare(rawFiles: List<RawLogFile>): PreparedLogs {
        var logMc: String? = null
        var logMcDebug: String? = null
        var logHs: String? = null
        var logCrash: String? = null
        val outputFiles = ArrayList<String>()
        val allFiles = HashMap<FileType, RawLogFile>()

        // 1. 按文件名分类
        for (logFile in rawFiles) {
            val fileName = fileNameOf(logFile.path)

            val fileType = when {
                fileName.startsWith("hs_err") -> FileType.HsErr
                fileName.startsWith("crash-") -> FileType.CrashReport
                isMinecraftLog(fileName) -> FileType.MinecraftLog
                fileName.endsWith(".log") -> FileType.ExtraLogFile
                fileName.endsWith(".txt") -> FileType.ExtraReportFile
                else -> continue
            }

            if (logFile.lines.isEmpty()) {
                continue
            }
            allFiles[fileType] = logFile
        }

        // 2. 没有 MinecraftLog 时用 ExtraLogFile 顶替
        if (!allFiles.containsKey(FileType.MinecraftLog)) {
            val extra = allFiles[FileType.ExtraLogFile]
            if (extra != null) {
                allFiles[FileType.MinecraftLog] = extra
                allFiles.remove(FileType.ExtraLogFile)
            }
        }

        // 3. 按类型截取
        for ((fileType, file) in allFiles) {
            outputFiles.add(file.path)

            when (fileType) {
                FileType.HsErr -> {
                    // JVM 崩溃日志：头 200 行 + 尾 100 行
                    logHs = getHeadTailLines(file.lines, 200, 100)
                }
                FileType.CrashReport -> {
                    // 崩溃报告：头 300 行 + 尾 700 行
                    logCrash = getHeadTailLines(file.lines, 300, 700)
                }
                FileType.MinecraftLog -> {
                    val logMcBuilder = StringBuilder()
                    val logMcDebugBuilder = StringBuilder()

                    // 建立文件名 → 文件对象的映射
                    val fileNameDict = HashMap<String, RawLogFile>()
                    for ((_, f) in allFiles) {
                        fileNameDict[fileNameOf(f.path)] = f
                    }

                    // 优先使用启动器输出日志（从标记行开始截取）
                    for (launcherName in arrayOf(
                        "rawoutput.log",
                        "启动器输出日志.txt",
                        "log1.txt",
                        "pcl2启动器输出日志.txt",
                        "pcl启动器输出日志.txt"
                    )) {
                        val currentLog = fileNameDict[launcherName]
                        if (currentLog != null) {
                            var hasLauncherMark = false
                            for (line in currentLog.lines) {
                                if (hasLauncherMark) {
                                    logMcBuilder.append(line)
                                    logMcBuilder.append('\n')
                                } else if (line.contains("启动器输出日志")) {
                                    hasLauncherMark = true
                                }
                            }
                            if (!hasLauncherMark) {
                                logMcBuilder.append(getHeadTailLines(currentLog.lines, 0, 500))
                            }
                            break
                        }
                    }

                    // 其次使用 latest.log / debug.log（取头 1500 行 + 尾 500 行）
                    for (latestName in arrayOf("latest.log", "latest log.txt", "debug.log", "debug log.txt")) {
                        val currentLog = fileNameDict[latestName]
                        if (currentLog != null) {
                            logMcBuilder.append(getHeadTailLines(currentLog.lines, 1500, 500))
                            break
                        }
                    }

                    // 单独提取 debug.log 作为 Debug 日志（取头 1000 行）
                    for (debugName in arrayOf("debug.log", "debug log.txt")) {
                        val currentLog = fileNameDict[debugName]
                        if (currentLog != null) {
                            logMcDebugBuilder.append(getHeadTailLines(currentLog.lines, 1000, 0))
                            break
                        }
                    }

                    // 兜底
                    if (logMcBuilder.isEmpty()) {
                        if (logMcDebugBuilder.isNotEmpty()) {
                            logMcBuilder.append(logMcDebugBuilder)
                        } else if (fileNameDict.isNotEmpty()) {
                            val firstLog = fileNameDict.entries.first().value
                            logMcBuilder.append(getHeadTailLines(firstLog.lines, 1500, 500))
                        }
                    }

                    logMc = if (logMcBuilder.isEmpty()) null else logMcBuilder.toString()
                    logMcDebug = if (logMcDebugBuilder.isEmpty()) null else logMcDebugBuilder.toString()
                }
                FileType.ExtraLogFile, FileType.ExtraReportFile -> {
                    // 额外日志文件不主动处理
                }
            }
        }

        return PreparedLogs(logMc, logMcDebug, logHs, logCrash, outputFiles)
    }

    /** 判断文件名是否属于 Minecraft 主日志 */
    private fun isMinecraftLog(fileName: String): Boolean {
        return when (fileName) {
            "latest.log", "latest log.txt", "debug.log", "debug log.txt" -> true
            else -> fileName.contains("启动器输出日志") ||
                fileName == "rawoutput.log" ||
                fileName == "log1.txt" ||
                fileName.contains("pc l2启动器输出日志") ||
                fileName.contains("pcl启动器输出日志")
        }
    }

    /** 提取日志头部 head_lines 行 + 尾部 tail_lines 行（去重） */
    fun getHeadTailLines(lines: List<String>, headLines: Int, tailLines: Int): String {
        if (lines.size <= headLines + tailLines) {
            // 全量去重
            val seen = ArrayList<String>()
            for (line in lines) {
                if (!seen.contains(line)) {
                    seen.add(line)
                }
            }
            return seen.joinToString("\n")
        }

        val result = ArrayList<String>()

        // 头部 head_lines 行（去重）
        var realHeadCount = 0
        for (line in lines) {
            if (result.contains(line)) {
                continue
            }
            result.add(line)
            realHeadCount++
            if (realHeadCount >= headLines) {
                break
            }
        }

        // 尾部 tail_lines 行（去重），插入到头部行之后
        var realTailCount = 0
        for (line in lines.asReversed()) {
            if (result.contains(line)) {
                continue
            }
            result.add(realHeadCount, line)
            realTailCount++
            if (realTailCount >= tailLines) {
                break
            }
        }

        return result.joinToString("\n")
    }

    /** 提取路径中的文件名并转小写（对应 Rust 的 Path::file_name + to_lowercase） */
    private fun fileNameOf(path: String): String {
        return path.substringAfterLast('/').substringAfterLast('\\').lowercase()
    }
}
