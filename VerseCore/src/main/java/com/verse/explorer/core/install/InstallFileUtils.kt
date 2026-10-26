package com.verse.explorer.core.install

import java.io.File

/**
 * 安装过程中的文件辅助：SHA-1 摘要与人类可读体积。
 */
internal object InstallFileUtils {

    fun calculateSHA1(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    /**
     * 判断已存在的库文件是否需要重新下载。
     *
     * 只看「文件存在」是不够的：下载被取消或中断时，目标路径会残留半截文件，
     * 下次安装会把它当成已下载而跳过，直到启动时 loader 读不到 zip 中央目录才报
     * `zip END header not found`。因此按清单校验：
     *  1. 不存在 —— 必然要下；
     *  2. 清单给了 sha1 —— 以摘要为准（长度可查时先比长度快速否定）；
     *     官方对个别库下发 22 字节占位 jar（如 1.12.2 的 lwjgl-platform），
     *     摘要匹配即视为完整，不能因为「小于 1KB」就反复重下；
     *  3. 清单给了 size 且长度不符 —— 半截文件；
     *  4. 都没有时，jar 至少要能打开 zip 头，否则视为损坏。
     *
     * @param expectedSize 清单里的字节数，未知传 -1
     */
    fun libraryNeedsDownload(file: File, sha1: String?, expectedSize: Long): Boolean {
        if (!file.exists()) return true
        if (!sha1.isNullOrBlank()) {
            if (expectedSize > 0 && file.length() != expectedSize) return true
            val actual = runCatching { calculateSHA1(file) }.getOrNull() ?: return true
            return !actual.equals(sha1, ignoreCase = true)
        }
        if (file.length() < 1024) return true
        if (expectedSize > 0 && file.length() != expectedSize) return true
        // 无校验信息：jar 能正常打开（中央目录可解析）才认为完整
        if (file.name.endsWith(".jar", ignoreCase = true)) return !isZipIntact(file)
        return false
    }

    /**
     * 判断 zip/jar 是否结构完整：构造 ZipFile 会解析中央目录，
     * 被截断的文件在这里会抛异常，不必遍历全部条目。
     */
    private fun isZipIntact(file: File): Boolean = runCatching {
        java.util.zip.ZipFile(file).use { it.entries().hasMoreElements() }
    }.getOrDefault(false)
}
