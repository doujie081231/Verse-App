package com.verse.explorer.core.util

import android.content.Context
import android.os.Environment
import android.system.Os
import android.system.StructStat
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {
    
    fun getGameDir(context: Context): File {
        val externalDir = File(Environment.getExternalStorageDirectory(), "VerseExplorerX")
        if (!externalDir.exists()) {
            externalDir.mkdirs()
        }
        return externalDir
    }
    
    fun getVersionsDir(gameDir: File): File = ensureDir(File(gameDir, "versions"))
    fun getAssetsDir(gameDir: File): File = ensureDir(File(gameDir, "assets"))
    fun getLibrariesDir(gameDir: File): File = ensureDir(File(gameDir, "libraries"))
    fun getModsDir(gameDir: File): File = ensureDir(File(gameDir, "mods"))
    fun getNativesDir(gameDir: File): File = ensureDir(File(gameDir, "natives"))
    fun getLogsDir(gameDir: File): File = ensureDir(File(gameDir, "logs"))
    fun getSavesDir(gameDir: File): File = ensureDir(File(gameDir, "saves"))
    fun getResourcePacksDir(gameDir: File): File = ensureDir(File(gameDir, "resourcepacks"))
    fun getShaderPacksDir(gameDir: File): File = ensureDir(File(gameDir, "shaderpacks"))
    fun getScreenshotDir(gameDir: File): File = ensureDir(File(gameDir, "screenshots"))
    fun getCrashReportsDir(gameDir: File): File = ensureDir(File(gameDir, "crash-reports"))
    fun getConfigDir(gameDir: File): File = ensureDir(File(gameDir, "config"))
    fun getOptionsFile(gameDir: File): File = File(gameDir, "options.txt")
    
    private fun ensureDir(dir: File): File {
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    fun parseFileSize(sizeStr: String): Long {
        val regex = Regex("([\\d.]+)\\s*([KMGT]?B)?", RegexOption.IGNORE_CASE)
        val match = regex.find(sizeStr) ?: return 0
        
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0
        val unit = match.groupValues[2].uppercase()
        
        return when (unit) {
            "KB" -> (value * 1024).toLong()
            "MB" -> (value * 1024 * 1024).toLong()
            "GB" -> (value * 1024 * 1024 * 1024).toLong()
            "TB" -> (value * 1024 * 1024 * 1024 * 1024).toLong()
            else -> value.toLong()
        }
    }
    
    fun deleteDirectory(directory: File): Boolean {
        if (!directory.exists()) return true
        
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                file.delete()
            }
        }
        
        return directory.delete()
    }
    
    fun copyFile(source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun copyDirectory(source: File, dest: File): Boolean {
        return try {
            if (!dest.exists()) dest.mkdirs()
            
            source.listFiles()?.forEach { file ->
                val destFile = File(dest, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, destFile)
                } else {
                    copyFile(file, destFile)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun moveFile(source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            if (copyFile(source, dest)) {
                source.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun readFile(file: File): String? {
        return try {
            file.readText()
        } catch (e: Exception) {
            null
        }
    }
    
    fun writeFile(file: File, content: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) fileName.substring(lastDot + 1) else ""
    }
    
    fun getFileNameWithoutExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) fileName.substring(0, lastDot) else fileName
    }
    
    fun calculateMD5(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    fun calculateSHA256(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    fun zipDirectory(sourceDir: File, zipFile: File): Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                sourceDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entry = ZipEntry(file.relativeTo(sourceDir).path)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun unzipFile(zipFile: File, destDir: File): Boolean {
        return try {
            if (!destDir.exists()) destDir.mkdirs()
            
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val file = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getDirectorySize(directory: File): Long {
        if (!directory.exists() || !directory.isDirectory) return 0
        
        var size = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }
    
    fun getFileCount(directory: File): Int {
        if (!directory.exists() || !directory.isDirectory) return 0
        
        var count = 0
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                count++
            }
        }
        return count
    }
    
    fun findFiles(directory: File, extension: String): List<File> {
        if (!directory.exists()) return emptyList()
        
        return directory.walkTopDown()
            .filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            .toList()
    }
    
    fun findFilesByName(directory: File, namePattern: String): List<File> {
        if (!directory.exists()) return emptyList()
        
        val regex = Regex(namePattern, RegexOption.IGNORE_CASE)
        return directory.walkTopDown()
            .filter { it.isFile && regex.containsMatchIn(it.name) }
            .toList()
    }
    
    fun createTempFile(prefix: String, suffix: String, directory: File? = null): File? {
        return try {
            if (directory != null) {
                File.createTempFile(prefix, suffix, directory)
            } else {
                File.createTempFile(prefix, suffix)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun setExecutable(file: File): Boolean {
        return try {
            file.setExecutable(true)
        } catch (e: Exception) {
            false
        }
    }
    
    fun isExecutable(file: File): Boolean {
        return try {
            file.canExecute()
        } catch (e: Exception) {
            false
        }
    }
    
    fun getFreeSpace(directory: File): Long {
        return try {
            directory.freeSpace
        } catch (e: Exception) {
            0
        }
    }
    
    fun getTotalSpace(directory: File): Long {
        return try {
            directory.totalSpace
        } catch (e: Exception) {
            0
        }
    }
    
    fun getUsableSpace(directory: File): Long {
        return try {
            directory.usableSpace
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 用 ZipFile 验证 ZIP 结构并将所有条目读取一遍，能检测出损坏/截断的文件。
     * 用于下载后的 JAR 完整性校验，避免损坏的 mod/库导致启动崩溃。
     */
    fun isValidJar(file: File): Boolean {
        if (!file.exists() || file.length() < 200) return false
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    zip.getInputStream(entry).use { it.skip(Long.MAX_VALUE) }
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     */
    fun calculateSha1(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
