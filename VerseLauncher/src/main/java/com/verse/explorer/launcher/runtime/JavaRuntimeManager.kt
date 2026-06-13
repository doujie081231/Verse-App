package com.verse.explorer.launcher.runtime

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class JreInfo(
    val version: Int,
    val arch: String,
    val url: String,
    val size: Long,
    val sha256: String
)

class JavaRuntimeManager(private val context: Context) {

    private val runtimeDir = File(context.filesDir, "jre")

    companion object {
        private const val TAG = "JavaRuntimeManager"

        private val REQUIRED_VERSIONS = listOf(8, 17, 21)

        private val FCL_JRE_URLS = mapOf(
            8 to "fcl-repo",
            17 to "fcl-repo",
            21 to "fcl-repo"
        )

        private val POJAV_JRE_URLS = mapOf(
            8 to listOf(
                "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/actions/runs/14955501976/artifacts/jre8-pojav.zip"
            ),
            17 to listOf(
                "pojav-api-jre17"
            ),
            21 to listOf(
                "pojav-api-jre21"
            )
        )
    }

    fun getInstalledRuntimes(): List<File> {
        if (!runtimeDir.exists()) return emptyList()

        return runtimeDir.listFiles()
            ?.filter { it.isDirectory && File(it, "bin/java").exists() }
            ?.toList() ?: emptyList()
    }

    fun getRuntimeForVersion(javaVersion: Int): File? {
        val installed = getInstalledRuntimes()

        val matchingRuntime = installed.find { dir ->
            val version = detectJavaVersion(dir)
            version != null && version >= javaVersion
        }

        return matchingRuntime
    }

    fun detectJavaVersion(javaHome: File): Int? {
        val releaseFile = File(javaHome, "release")
        if (releaseFile.exists()) {
            try {
                val content = releaseFile.readText()
                val versionRegex = """JAVA_VERSION="(\d+)""".toRegex()
                val match = versionRegex.find(content)
                if (match != null) return match.groupValues[1].toIntOrNull()
            } catch (_: Exception) {
            }
        }

        val hasLibjli = File(javaHome, "lib/libjli.so").exists() ||
                        File(javaHome, "lib/jli/libjli.so").exists() ||
                        File(javaHome, "lib/aarch64/jli/libjli.so").exists()
        val hasLibjvm = File(javaHome, "lib/server/libjvm.so").exists() ||
                        File(javaHome, "lib/aarch64/server/libjvm.so").exists() ||
                        File(javaHome, "lib/amd64/server/libjvm.so").exists()
        val hasJavaBin = File(javaHome, "bin/java").exists() || File(javaHome, "bin/java.sh").exists()

        if (!hasLibjli && !hasLibjvm && !hasJavaBin) return null

        val dirName = javaHome.name
        val dirVersion = Regex("""jre(\d+)""").find(dirName)?.groupValues?.get(1)?.toIntOrNull()
        if (dirVersion != null) return dirVersion

        return try {
            val javaExe = File(javaHome, "bin/java")
            if (!javaExe.exists()) return null

            val process = ProcessBuilder(javaExe.absolutePath, "-version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val versionRegex = """version "(\d+)""".toRegex()
            versionRegex.find(output)?.groupValues?.get(1)?.toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadRuntime(
        version: Int,
        progressCallback: ((downloaded: Long, total: Long) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {

        val targetDir = File(runtimeDir, "jre$version")
        if (targetDir.exists() && detectJavaVersion(targetDir) != null) {
            return@withContext targetDir
        }

        val arch = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "aarch64"
            Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
            Build.SUPPORTED_ABIS.contains("x86") -> "x86"
            else -> "arm"
        }

        val fclResult = downloadFclJre(version, arch, targetDir, progressCallback)
        if (fclResult != null) return@withContext fclResult

        val pojavResult = downloadPojavJre(version, arch, targetDir, progressCallback)
        if (pojavResult != null) return@withContext pojavResult

        val fallbackUrl = getFallbackUrl(version, arch)
        if (fallbackUrl != null) {
            try {
                val tempFile = File(context.cacheDir, "jre$version.tar.gz")
                downloadFile(fallbackUrl, tempFile, progressCallback)

                targetDir.mkdirs()
                ProcessBuilder("tar", "-xzf", tempFile.absolutePath, "-C", targetDir.absolutePath)
                    .inheritIO()
                    .start()
                    .waitFor()

                tempFile.delete()

                val extractedDir = targetDir.listFiles()?.firstOrNull { it.isDirectory }
                if (extractedDir != null && extractedDir != targetDir) {
                    extractedDir.listFiles()?.forEach { it.renameTo(File(targetDir, it.name)) }
                    extractedDir.delete()
                }

                if (detectJavaVersion(targetDir) != null) {
                    return@withContext targetDir
                }
            } catch (_: Exception) {
            }
        }

        null
    }

    private suspend fun downloadFclJre(
        version: Int,
        arch: String,
        targetDir: File,
        progressCallback: ((downloaded: Long, total: Long) -> Unit)?
    ): File? = withContext(Dispatchers.IO) {
        val fclArch = when (arch) {
            "aarch64" -> "arm64"
            "aarch32" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "arm64"
        }

        val fclBasePath = "FCL-Team/FoldCraftLauncher/main/FCL/src/main/assets/app_runtime/java/jre$version"

        val proxyUrls = listOf(
            "https://ghp.ci/https://raw.githubusercontent.com/$fclBasePath" to "FCL仓库(ghp.ci加速)",
            "https://raw.githubusercontent.com/$fclBasePath" to "FCL仓库(GitHub直连)"
        )

        for ((baseUrl, sourceName) in proxyUrls) {
            try {
                val universalUrl = "$baseUrl/universal.tar.xz"
                val archUrl = "$baseUrl/bin-$fclArch.tar.xz"

                if (targetDir.exists()) targetDir.deleteRecursively()
                targetDir.mkdirs()

                val universalFile = File(context.cacheDir, "fcl_jre${version}_universal.tar.xz")
                downloadFile(universalUrl, universalFile, progressCallback)

                if (universalFile.length() < 1024) {
                    universalFile.delete()
                    continue
                }

                ProcessBuilder("tar", "-xJf", universalFile.absolutePath, "-C", targetDir.absolutePath)
                    .inheritIO().start().waitFor()
                universalFile.delete()

                val archFile = File(context.cacheDir, "fcl_jre${version}_bin.tar.xz")
                downloadFile(archUrl, archFile, progressCallback)

                if (archFile.length() < 1024) {
                    archFile.delete()
                    continue
                }

                ProcessBuilder("tar", "-xJf", archFile.absolutePath, "-C", targetDir.absolutePath)
                    .inheritIO().start().waitFor()
                archFile.delete()

                setExecutableRecursive(File(targetDir, "bin"))

                val releaseFile = File(targetDir, "release")
                if (!releaseFile.exists()) {
                    try {
                        releaseFile.writeText("JAVA_VERSION=\"$version\"\n")
                    } catch (_: Exception) {}
                }

                if (detectJavaVersion(targetDir) != null) {
                    return@withContext targetDir
                }
            } catch (_: Exception) {
                continue
            }
        }

        null
    }

    private suspend fun downloadPojavJre(
        version: Int,
        arch: String,
        targetDir: File,
        progressCallback: ((downloaded: Long, total: Long) -> Unit)?
    ): File? = withContext(Dispatchers.IO) {
        val urls = POJAV_JRE_URLS[version] ?: return@withContext null

        for (url in urls) {
            try {
                val resolvedUrl = resolveRedirect(url)
                if (resolvedUrl == null) continue

                val tempFile = File(context.cacheDir, "pojav-jre$version.zip")
                downloadFile(resolvedUrl, tempFile, progressCallback)

                if (tempFile.length() < 1024) {
                    tempFile.delete()
                    continue
                }

                if (targetDir.exists()) targetDir.deleteRecursively()
                targetDir.mkdirs()

                extractPojavZip(tempFile, targetDir, arch)
                tempFile.delete()

                setExecutableRecursive(File(targetDir, "bin"))

                val releaseFile = File(targetDir, "release")
                if (!releaseFile.exists()) {
                    try {
                        releaseFile.writeText("JAVA_VERSION=\"$version\"\n")
                    } catch (_: Exception) {}
                }

                if (File(targetDir, "bin/java").exists()) {
                    return@withContext targetDir
                }

            } catch (_: Exception) {
                continue
            }
        }

        null
    }

    private fun extractPojavZip(zipFile: File, destDir: File, arch: String) {
        ZipInputStream(zipFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val entryName = entry.name

                if (entryName.contains("bin-x86_64") && arch != "x86_64" ||
                    entryName.contains("bin-x86") && !entryName.contains("bin-x86_64") && arch != "x86" ||
                    entryName.contains("bin-arm") && !entryName.contains("bin-aarch64") && arch != "arm" ||
                    entryName.contains("bin-aarch64") && arch != "aarch64") {
                    if (entryName.startsWith("bin-")) {
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

        if (universalTar != null) {
            ProcessBuilder("tar", "-xJf", universalTar.absolutePath, "-C", destDir.absolutePath)
                .inheritIO().start().waitFor()
            universalTar.delete()
        }

        if (archTar != null) {
            ProcessBuilder("tar", "-xJf", archTar.absolutePath, "-C", destDir.absolutePath)
                .inheritIO().start().waitFor()
            archTar.delete()
        }
    }

    private fun findFileInDir(dir: File, fileName: String): File? {
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

    private fun resolveRedirect(url: String): String? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER -> {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newUrl != null) resolveRedirect(newUrl) else null
                }
                HttpURLConnection.HTTP_OK -> {
                    connection.disconnect()
                    url
                }
                else -> {
                    connection.disconnect()
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getFallbackUrl(version: Int, arch: String): String? {
        val fclArch = when (arch) {
            "aarch64" -> "arm64"
            "aarch32" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> "arm64"
        }
        val fclBasePath = "FCL-Team/FoldCraftLauncher/main/FCL/src/main/assets/app_runtime/java/jre$version"
        return "https://ghp.ci/https://raw.githubusercontent.com/$fclBasePath/universal.tar.xz"
    }

    private fun downloadFile(url: String, target: File, progressCallback: ((Long, Long) -> Unit)?) {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 30000
        connection.readTimeout = 60000

        val totalSize = connection.contentLengthLong
        var downloaded = 0L

        connection.getInputStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    progressCallback?.invoke(downloaded, totalSize)
                }
            }
        }
    }

    private fun setExecutableRecursive(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                setExecutableRecursive(file)
            } else {
                try {
                    file.setExecutable(true, false)
                } catch (_: Exception) {}
            }
        }
    }

    fun deleteRuntime(version: Int): Boolean {
        val runtime = File(runtimeDir, "jre$version")
        return runtime.exists() && runtime.deleteRecursively()
    }

    fun getRequiredVersions(): List<Int> = REQUIRED_VERSIONS

    fun isRuntimeInstalled(version: Int): Boolean {
        val runtime = File(runtimeDir, "jre$version")
        return runtime.exists() && detectJavaVersion(runtime) != null
    }
}
