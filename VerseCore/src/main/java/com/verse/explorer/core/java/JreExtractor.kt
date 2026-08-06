package com.verse.explorer.core.java

import android.system.Os
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "JavaRuntimeManager"

internal object JreExtractor {

    fun extractTarXz(tarXzFile: File, destDir: File, onProgress: (Int, Int) -> Unit) {
        destDir.mkdirs()
        var currentFile = 0

        // First pass: count entries for progress
        var totalFiles = 0
        try {
            FileInputStream(tarXzFile).use { fis ->
                XZCompressorInputStream(fis).use { xzIn ->
                    TarArchiveInputStream(xzIn).use { tarIn ->
                        var entry = tarIn.nextEntry
                        while (entry != null) { totalFiles++; entry = tarIn.nextEntry }
                    }
                }
            }
        } catch (_: Exception) {
            totalFiles = 1
        }

        // Second pass: extract with symlink support
        try {
            FileInputStream(tarXzFile).use { fis ->
                XZCompressorInputStream(fis).use { xzIn ->
                    TarArchiveInputStream(xzIn).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextEntry
                        while (entry != null) {
                            val e = entry!!
                            val destPath = File(destDir, e.name)

                            if (!destPath.canonicalPath.startsWith(destDir.canonicalPath)) {
                                entry = tarIn.nextEntry
                                continue
                            }

                            if (e.isSymbolicLink) {
                                destPath.parentFile?.mkdirs()
                                // FCL-style: use Os.symlink() instead of ln command
                                // Handle relative symlinks properly by resolving against destDir
                                try {
                                    val linkTarget = e.linkName
                                    if (!linkTarget.startsWith("/")) {
                                        // Relative symlink - create absolute target first, then relative symlink
                                        val resolvedTarget = File(destPath.parentFile, linkTarget).canonicalPath
                                        Os.symlink(resolvedTarget, destPath.absolutePath)
                                    } else {
                                        Os.symlink(linkTarget, destPath.absolutePath)
                                    }
                                } catch (symErr: Exception) {
                                    Log.w(TAG, "Symlink failed for ${e.name} -> ${e.linkName}: ${symErr.message}")
                                    // Fallback: create regular file with symlink path content
                                    try {
                                        destPath.writeText(e.linkName)
                                    } catch (_: Exception) {}
                                }
                            } else if (e.isDirectory) {
                                destPath.mkdirs()
                                try { destPath.setExecutable(true) } catch (_: Exception) {}
                            } else {
                                destPath.parentFile?.mkdirs()
                                // Only write if doesn't exist or size differs (FCL-style optimization)
                                if (!destPath.exists() || destPath.length() != e.size) {
                                    try { destPath.createNewFile() } catch (_: Exception) {}
                                    FileOutputStream(destPath).use { fos ->
                                        val buffer = ByteArray(8192)
                                        var len: Int
                                        while (tarIn.read(buffer).also { len = it } != -1) {
                                            fos.write(buffer, 0, len)
                                        }
                                        fos.flush()
                                    }
                                }
                                try {
                                    if ((e.mode and 0b1000000) != 0) destPath.setExecutable(true, false)
                                    destPath.setReadable(true, false)
                                } catch (_: Exception) {}
                            }

                            currentFile++
                            onProgress(currentFile, totalFiles)
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractTarXz failed: ${e.message}", e)
            throw e
        }
    }

    fun extractPojavJreZip(
        zipFile: File,
        destDir: File,
        arch: String,
        onProgress: (JavaInstallProgress) -> Unit
    ) {
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val entryName = entry.name

                if (entryName.contains("bin-x86_64") && arch != "x86_64" ||
                    entryName.contains("bin-x86") && !entryName.contains("bin-x86_64") && arch != "x86" ||
                    entryName.contains("bin-arm") && !entryName.contains("bin-aarch64") && arch != "arm" ||
                    entryName.contains("bin-aarch64") && arch != "aarch64") {
                    if (entryName.contains("/bin-") || entryName.startsWith("bin-")) {
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }
                }

                val outputFile = File(destDir, entryName)
                if (!outputFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                    continue
                }

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().use { output -> zipIn.copyTo(output) }
                    if (entryName.contains("bin/") || entryName.endsWith(".so")) {
                        outputFile.setExecutable(true, false)
                    }
                    outputFile.setReadable(true, false)
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        val universalTar = findFileInDir(destDir, "universal.tar.xz")
        val archTar = findFileInDir(destDir, "bin-$arch.tar.xz")

        val tarExtractDir = File(destDir, "jre_extracted")
        tarExtractDir.mkdirs()

        if (universalTar != null) {
            extractTarXz(universalTar, tarExtractDir) { current, total ->
                val progress = if (total > 0) ((current * 100) / total).toInt() else 0
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压通用文件... ($current/$total)"
                ))
            }
            universalTar.delete()
        }

        if (archTar != null) {
            extractTarXz(archTar, tarExtractDir) { current, total ->
                val progress = if (total > 0) ((current * 100) / total).toInt() else 0
                onProgress(JavaInstallProgress(
                    JavaInstallStatus.EXTRACTING, progress,
                    "正在解压架构文件... ($current/$total)"
                ))
            }
            archTar.delete()
        }

        if (tarExtractDir.exists()) {
            val jreRoot = findJreRootDir(tarExtractDir)
            if (jreRoot != null && jreRoot != destDir) {
                jreRoot.listFiles()?.forEach { file ->
                    val target = File(destDir, file.name)
                    if (target.exists()) target.deleteRecursively()
                    file.renameTo(target)
                }
                tarExtractDir.deleteRecursively()
            }
        }

        val remainingTarFiles = mutableListOf<File>()
        findFilesByExtension(destDir, ".tar.xz", remainingTarFiles)
        for (tarFile in remainingTarFiles) {
            tarFile.delete()
        }
    }

    fun moveDirReliably(src: File, dest: File): Boolean {
        if (src.absolutePath == dest.absolutePath) return true
        if (!src.exists()) {
            Log.e(TAG, "moveDirReliably: source does not exist: ${src.absolutePath}")
            return false
        }
        if (dest.exists()) {
            dest.deleteRecursively()
        }
        dest.parentFile?.mkdirs()
        val renamed = src.renameTo(dest)
        if (renamed && dest.exists()) {
            Log.d(TAG, "moveDirReliably: renameTo succeeded: ${src.absolutePath} -> ${dest.absolutePath}")
            return true
        }
        Log.w(TAG, "moveDirReliably: renameTo failed, falling back to copy: ${src.absolutePath} -> ${dest.absolutePath}")
        try {
            copyDirRecursive(src, dest)
            if (dest.exists()) {
                src.deleteRecursively()
                Log.d(TAG, "moveDirReliably: copy+delete succeeded")
                return true
            }
            Log.e(TAG, "moveDirReliably: copy completed but dest doesn't exist")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "moveDirReliably: copy failed: ${e.message}")
            return false
        }
    }

    private fun copyDirRecursive(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyDirRecursive(child, File(dest, child.name))
            }
        } else {
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.setExecutable(src.canExecute(), false)
            dest.setReadable(src.canRead(), false)
        }
    }

    fun findJreRootDir(dir: File): File? {
        if (File(dir, "bin/java").exists() || File(dir, "bin/java.sh").exists()) {
            return dir
        }
        if (File(dir, "lib/server/libjvm.so").exists() || File(dir, "lib/aarch64/server/libjvm.so").exists() || File(dir, "lib/libjli.so").exists()) {
            return dir
        }
        dir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
            val found = findJreRootDir(subDir)
            if (found != null) return found
        }
        return null
    }

    fun findFilesByExtension(dir: File, extension: String, result: MutableList<File>) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                findFilesByExtension(file, extension, result)
            } else if (file.name.endsWith(extension)) {
                result.add(file)
            }
        }
    }

    fun findFileInDir(dir: File, fileName: String): File? {
        if (!dir.exists()) return null
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findFileInDir(file, fileName)
                if (found != null) return found
            } else if (file.name == fileName) {
                return file
            }
        }
        return null
    }

    fun findFileRecursive(dir: File, fileName: String): Boolean {
        if (!dir.exists()) return false
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name == fileName) return true
            if (file.isDirectory && findFileRecursive(file, fileName)) return true
        }
        return false
    }

    /**
     * 合并嵌入式 JRE 子目录（如 jdk-25/）的内容到目标目录。
     * 当 universal.tar.xz 和 bin-*.tar.xz 的路径前缀不一致时，
     * universal 的内容会被提取到子目录中，导致 conf/legal 等目录丢失。
     */
    fun mergeStrayJreSubdir(targetDir: File) {
        if (!targetDir.exists()) return
        val strayDirs = targetDir.listFiles()?.filter {
            it.isDirectory && (
                it.name.startsWith("jdk") ||
                it.name.startsWith("java") ||
                it.name.startsWith("jre") ||
                it.name.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) // e.g. 25.0.0+13
            )
        } ?: emptyList()
        for (strayDir in strayDirs) {
            // 跳过自身就是 JRE 根目录的子目录（如 jre-25-aarch64 本身）
            if (strayDir.name == targetDir.name) continue
            Log.d(TAG, "Merging stray JRE subdir: ${strayDir.name} into ${targetDir.name}")
            strayDir.listFiles()?.forEach { child ->
                val target = File(targetDir, child.name)
                if (!target.exists()) {
                    child.renameTo(target)
                } else if (child.isDirectory) {
                    // 合并目录内容
                    child.listFiles()?.forEach { subChild ->
                        val subTarget = File(target, subChild.name)
                        if (!subTarget.exists()) {
                            subChild.renameTo(subTarget)
                        }
                    }
                    child.deleteRecursively()
                }
            }
            // 如果子目录还有内容没合并完，直接删除
            if (strayDir.exists()) {
                strayDir.deleteRecursively()
            }
        }
    }

    fun setExecutableRecursive(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                setExecutableRecursive(file)
            } else {
                file.setExecutable(true, false)
            }
        }
    }
}
