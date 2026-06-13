package com.verse.explorer.launcher.crash

import android.util.Log
import java.io.File

enum class CrashType {
    OUT_OF_MEMORY,
    STACK_OVERFLOW,
    LWJGL_ERROR,
    NATIVE_LIB_ERROR,
    MINECRAFT_CRASH,
    CLASS_NOT_FOUND,
    GLFW_ERROR,
    SHADER_ERROR,
    JVM_CREATION_FAILED,
    JVM_FATAL_ERROR,
    PROCESS_CRASH,
    UNKNOWN
}

data class CrashResult(
    val isCrash: Boolean,
    val crashType: CrashType,
    val message: String,
    val details: String,
    val crashReportPath: String? = null,
    val suggestion: String = ""
)

object CrashDetector {

    private const val TAG = "CrashDetector"

    private val crashPatterns = mapOf(
        "java.lang.OutOfMemoryError" to CrashType.OUT_OF_MEMORY,
        "java.lang.StackOverflowError" to CrashType.STACK_OVERFLOW,
        "org.lwjgl.LWJGLException" to CrashType.LWJGL_ERROR,
        "java.lang.UnsatisfiedLinkError" to CrashType.NATIVE_LIB_ERROR,
        "net.minecraft.util.ReportedException" to CrashType.MINECRAFT_CRASH,
        "java.lang.NoClassDefFoundError" to CrashType.CLASS_NOT_FOUND,
        "GLFW error" to CrashType.GLFW_ERROR,
        "Shader compilation error" to CrashType.SHADER_ERROR,
        "Could not create the Java Virtual Machine" to CrashType.JVM_CREATION_FAILED,
        "A fatal error has been detected" to CrashType.JVM_FATAL_ERROR,
        "SIGSEGV" to CrashType.PROCESS_CRASH,
        "SIGABRT" to CrashType.PROCESS_CRASH,
        "crash-reports" to CrashType.MINECRAFT_CRASH
    )

    private val crashMessages = mapOf(
        CrashType.OUT_OF_MEMORY to "内存不足",
        CrashType.STACK_OVERFLOW to "栈溢出",
        CrashType.LWJGL_ERROR to "LWJGL 渲染错误",
        CrashType.NATIVE_LIB_ERROR to "原生库加载失败",
        CrashType.MINECRAFT_CRASH to "Minecraft 崩溃",
        CrashType.CLASS_NOT_FOUND to "类缺失",
        CrashType.GLFW_ERROR to "GLFW 窗口错误",
        CrashType.SHADER_ERROR to "着色器编译失败",
        CrashType.JVM_CREATION_FAILED to "JVM 创建失败",
        CrashType.JVM_FATAL_ERROR to "JVM 致命错误",
        CrashType.PROCESS_CRASH to "进程崩溃",
        CrashType.UNKNOWN to "未知错误"
    )

    private val crashSuggestions = mapOf(
        CrashType.OUT_OF_MEMORY to "尝试增加内存分配或减少渲染距离",
        CrashType.STACK_OVERFLOW to "检查是否有模组冲突或无限递归",
        CrashType.LWJGL_ERROR to "尝试切换渲染器",
        CrashType.NATIVE_LIB_ERROR to "尝试重新安装游戏",
        CrashType.MINECRAFT_CRASH to "查看崩溃报告获取详细信息",
        CrashType.CLASS_NOT_FOUND to "检查游戏完整性或重新安装",
        CrashType.GLFW_ERROR to "检查显卡驱动或尝试不同渲染器",
        CrashType.SHADER_ERROR to "禁用光影或更新显卡驱动",
        CrashType.JVM_CREATION_FAILED to "检查 Java 运行时是否正确安装",
        CrashType.JVM_FATAL_ERROR to "尝试重新安装 Java 运行时",
        CrashType.PROCESS_CRASH to "检查设备兼容性或重新安装游戏",
        CrashType.UNKNOWN to "查看日志获取更多信息"
    )

    fun detectCrash(logFile: File): CrashResult {
        if (!logFile.exists()) {
            return CrashResult(
                isCrash = false,
                crashType = CrashType.UNKNOWN,
                message = "日志文件不存在",
                details = ""
            )
        }

        try {
            val logContent = logFile.readText()
            val lines = logContent.lines()

            for (line in lines.reversed()) {
                for ((pattern, crashType) in crashPatterns) {
                    if (line.contains(pattern, ignoreCase = true)) {
                        val details = extractCrashDetails(lines, pattern)
                        val crashReportPath = findCrashReport(logFile.parentFile)

                        Log.e(TAG, "检测到崩溃: ${crashMessages[crashType]}")
                        Log.e(TAG, "崩溃行: $line")

                        return CrashResult(
                            isCrash = true,
                            crashType = crashType,
                            message = crashMessages[crashType] ?: "未知错误",
                            details = details,
                            crashReportPath = crashReportPath,
                            suggestion = crashSuggestions[crashType] ?: ""
                        )
                    }
                }
            }

            return CrashResult(
                isCrash = false,
                crashType = CrashType.UNKNOWN,
                message = "未检测到崩溃",
                details = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析日志文件失败: ${e.message}", e)
            return CrashResult(
                isCrash = false,
                crashType = CrashType.UNKNOWN,
                message = "解析日志失败",
                details = e.message ?: ""
            )
        }
    }

    fun getLatestCrashReport(gameDir: File): String? {
        val crashReportsDir = File(gameDir, "crash-reports")
        if (!crashReportsDir.exists() || !crashReportsDir.isDirectory) {
            return null
        }

        val crashFiles = crashReportsDir.listFiles { file ->
            file.isFile && file.name.startsWith("crash-") && file.name.endsWith(".txt")
        }

        if (crashFiles.isNullOrEmpty()) {
            return null
        }

        val latestFile = crashFiles.maxByOrNull { it.lastModified() } ?: return null

        return try {
            val content = latestFile.readText()
            val lines = content.lines()

            val descriptionBuilder = StringBuilder()
            var inSystemDetails = false
            val systemDetailsBuilder = StringBuilder()

            for (line in lines) {
                if (line.startsWith("---- Minecraft Crash Report ----")) {
                    continue
                }

                if (line.startsWith("Description:")) {
                    descriptionBuilder.appendLine(line.substringAfter("Description:").trim())
                    continue
                }

                if (line.startsWith("System Details:")) {
                    inSystemDetails = true
                    continue
                }

                if (inSystemDetails) {
                    if (line.isBlank() || line.startsWith("--")) {
                        inSystemDetails = false
                    } else {
                        systemDetailsBuilder.appendLine(line)
                    }
                }

                if (descriptionBuilder.isEmpty() && line.isNotBlank() && !line.startsWith("--")) {
                    descriptionBuilder.appendLine(line)
                }
            }

            buildString {
                appendLine("=== 崩溃报告: ${latestFile.name} ===")
                appendLine()
                if (descriptionBuilder.isNotEmpty()) {
                    appendLine("描述:")
                    appendLine(descriptionBuilder.toString().trim())
                    appendLine()
                }
                if (systemDetailsBuilder.isNotEmpty()) {
                    appendLine("系统详情:")
                    appendLine(systemDetailsBuilder.toString().trim())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取崩溃报告失败: ${e.message}", e)
            null
        }
    }

    private fun extractCrashDetails(logLines: List<String>, pattern: String): String {
        val detailsBuilder = StringBuilder()
        var foundPattern = false
        var lineCount = 0

        for (line in logLines) {
            if (line.contains(pattern, ignoreCase = true)) {
                foundPattern = true
            }

            if (foundPattern) {
                detailsBuilder.appendLine(line)
                lineCount++

                if (lineCount >= 10) {
                    break
                }

                if (line.isBlank() && lineCount > 3) {
                    break
                }
            }
        }

        return detailsBuilder.toString().trim()
    }

    private fun findCrashReport(gameDir: File?): String? {
        if (gameDir == null) return null

        val crashReportsDir = File(gameDir, "crash-reports")
        if (!crashReportsDir.exists()) return null

        val crashFiles = crashReportsDir.listFiles { file ->
            file.isFile && file.name.startsWith("crash-") && file.name.endsWith(".txt")
        }

        return crashFiles?.maxByOrNull { it.lastModified() }?.absolutePath
    }
}
