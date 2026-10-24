package com.verse.explorer.core.launch

import com.google.gson.JsonArray
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 依赖修复过程中的通用工具：rules 判定、原生库解压、体积格式化。
 * 全部为纯函数，不依赖下载器状态。
 */
internal object DependencyUtils {

    /** 判断一组 rules 在当前平台是否放行（空规则视为放行；带 features 的规则跳过） */
    fun evaluateRules(rules: JsonArray): Boolean {
        if (rules.size() == 0) return true

        var allowed = false
        for (ruleElement in rules) {
            val rule = ruleElement.asJsonObject
            val action = rule.get("action")?.asString ?: continue
            val os = rule.getAsJsonObject("os")
            val features = rule.getAsJsonObject("features")

            if (features != null) continue

            if (os != null) {
                val osName = os.get("name")?.asString ?: continue
                if (osName == "linux") {
                    allowed = action == "allow"
                }
            } else {
                allowed = action == "allow"
            }
        }
        return allowed
    }

    /** 把原生库 jar 内的文件平铺解到目标目录（跳过目录项与 META-INF） */
    fun extractZip(zipFile: File, targetDir: File) {
        var entryCount = 0
        var soFileCount = 0

        ZipInputStream(zipFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && !entry.name.contains("META-INF")) {
                    val fileName = entry.name.substringAfterLast("/")
                    val file = File(targetDir, fileName)
                    file.parentFile?.mkdirs()

                    file.outputStream().use { output -> zipIn.copyTo(output) }
                    entryCount++

                    if (fileName.endsWith(".so")) {
                        soFileCount++
                        println("[DependencyDownloader]   Extracted: $fileName (${file.length()} bytes)")
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        println("[DependencyDownloader] extractZip: $zipFile.name -> $entryCount files ($soFileCount .so)")
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024))
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
