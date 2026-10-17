package com.verse.explorer.core.java

import android.system.Os
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

private const val TAG = "JavaRuntimeManager"

internal object JreExtractor {

    /**
     */
    fun extractTarXz(inputStream: InputStream, destDir: File, onProgress: (Int, Int) -> Unit) {
        destDir.mkdirs()
        var currentFile = 0
        var totalFiles = 1
        try {
            val inStream = java.io.BufferedInputStream(inputStream)
            XZCompressorInputStream(inStream).use { xzIn ->
                TarArchiveInputStream(xzIn).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        val e = entry
                        val destPath = File(destDir, e.name)
                        if (!destPath.canonicalPath.startsWith(destDir.canonicalPath)) {
                            entry = tarIn.nextEntry
                            continue
                        }
                        if (e.isSymbolicLink) {
                            destPath.parentFile?.mkdirs()
                            try {
                                val linkTarget = e.linkName
                                if (!linkTarget.startsWith("/")) {
                                    val resolvedTarget = File(destPath.parentFile, linkTarget).canonicalPath
                                    Os.symlink(resolvedTarget, destPath.absolutePath)
                                } else {
                                    Os.symlink(linkTarget, destPath.absolutePath)
                                }
                            } catch (symErr: Exception) {
                                Log.w(TAG, "Symlink failed for ${e.name} -> ${e.linkName}: ${symErr.message}")
                                try {
                                    destPath.writeText(e.linkName)
                                } catch (_: Exception) {}
                            }
                        } else if (e.isDirectory) {
                            destPath.mkdirs()
                            try { destPath.setExecutable(true) } catch (_: Exception) {}
                        } else {
                            destPath.parentFile?.mkdirs()
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
        } catch (e: Exception) {
            Log.e(TAG, "extractTarXz(InputStream) failed: ${e.message}", e)
            throw e
        }
    }

    fun extractTarXz(tarXzFile: File, destDir: File, onProgress: (Int, Int) -> Unit) {
        extractTarXz(FileInputStream(tarXzFile), destDir, onProgress)
    }

    /**
     */
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
                    outputFile.setReadable(true, false)
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
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
