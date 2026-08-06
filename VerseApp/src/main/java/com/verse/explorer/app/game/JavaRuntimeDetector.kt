package com.verse.explorer.app.game

import android.widget.Toast
import androidx.activity.ComponentActivity
import com.verse.explorer.app.data.version.VersionJsonResolver
import com.verse.explorer.app.ui.game.GameStatusOverlay
import com.verse.explorer.core.java.JavaRuntime
import com.verse.explorer.core.java.JavaRuntimeManager
import java.io.File
import kotlin.concurrent.thread

class JavaRuntimeDetector(
    private val activity: ComponentActivity,
    private val log: (String) -> Unit,
    private val statusOverlay: GameStatusOverlay,
    private val versionResolver: VersionJsonResolver
) {

    fun getRequiredJavaVersion(versionId: String, gameDir: File): Int {
        val versionsRootDir = File(gameDir, "versions")
        val jsonFile = File(versionsRootDir, "$versionId/$versionId.json")
        val versionJson = if (jsonFile.exists()) versionResolver.resolveVersionJson(gameDir, versionId) else null

        if (versionJson != null) {
            val javaVersionObj = versionJson.getAsJsonObject("javaVersion")
            if (javaVersionObj != null) {
                val majorVersion = javaVersionObj.get("majorVersion")?.asInt
                if (majorVersion != null && majorVersion > 0) {
                    log("版本JSON javaVersion.majorVersion=$majorVersion ✅\n")
                    return majorVersion
                }
            }

            val complianceLevel = versionJson.get("complianceLevel")?.asInt
            if (complianceLevel != null && complianceLevel > 0) {
                log("版本JSON complianceLevel=$complianceLevel\n")
                return complianceLevel
            }
        }

        log("版本JSON无javaVersion/complianceLevel，解析版号推断...\n")
        return inferJavaVersionFromId(versionId)
    }

    fun inferJavaVersionFromId(mcVersionStr: String): Int {
        if (mcVersionStr.isBlank()) return 17

        if (mcVersionStr.toIntOrNull() != null) {
            val ver = mcVersionStr.toInt()
            return when { ver >= 25 -> 25; ver >= 21 -> 21; ver >= 17 -> 17; else -> 8 }
        }

        val parts = mcVersionStr.split(".")
        if (parts.size < 2) {
            val ver = mcVersionStr.toIntOrNull() ?: 17
            return when { ver >= 25 -> 25; ver >= 21 -> 21; ver >= 17 -> 17; else -> 8 }
        }

        val major = parts[0].toIntOrNull() ?: 1
        val minor = parts[1].toIntOrNull() ?: 0
        val patch = if (parts.size >= 3) {
            parts[2].split("-").first().toIntOrNull() ?: 0
        } else 0

        val isForge = mcVersionStr.contains("forge", ignoreCase = true) ||
                      mcVersionStr.contains("fabric", ignoreCase = true) ||
                      mcVersionStr.contains("quilt", ignoreCase = true) ||
                      mcVersionStr.contains("neoforged", ignoreCase = true)

        if (isForge && major == 1 && minor == 16 && patch >= 5) {
            log("推断: Forge 1.16.5+ → Java 11\n")
            return 11
        }

        if (isForge && major == 1 && minor == 16) {
            log("推断: Forge 1.16 → Java 8\n")
            return 8
        }

        if (major != 1) {
            return when { major >= 22 -> 25; major >= 18 -> 21; major >= 13 -> 17; else -> 8 }
        }

        return when {
            minor >= 20 && patch >= 5 -> {
                log("推断: 1.20.5+ → Java 21\n")
                21
            }
            minor >= 30 -> {
                log("推断: 1.30+ → Java 25\n")
                25
            }
            minor >= 25 -> {
                log("推断: 1.25+ → Java 25\n")
                25
            }
            minor >= 22 -> {
                log("推断: 1.22+ → Java 25\n")
                25
            }
            minor >= 21 -> {
                log("推断: 1.21+ → Java 21\n")
                21
            }
            minor >= 17 -> {
                log("推断: 1.17+ → Java 17\n")
                17
            }
            minor >= 16 && isForge -> {
                log("推断: Forge 1.16+ → Java 8\n")
                8
            }
            else -> {
                log("推断: 默认 → Java 8\n")
                8
            }
        }
    }

    fun autoDetectJavaVersion(versionId: String, gameDir: File, hintJavaVersion: Int): Int {
        if (hintJavaVersion > 0) {
            log("传入Java版本指示: $hintJavaVersion (仅供参考)\n")
        }

        return getRequiredJavaVersion(versionId, gameDir)
    }

    fun selectBestRuntime(runtimes: List<JavaRuntime>, requiredVersion: Int): JavaRuntime? {
        val exactMatch = runtimes.find { it.version == requiredVersion }
        if (exactMatch != null) {
            log("精确匹配: Java ${exactMatch.version}\n")
            return exactMatch
        }
        val minHigher = runtimes.filter { it.version >= requiredVersion }.minByOrNull { it.version }
        if (minHigher != null) {
            log("选择最小兼容版本: Java ${minHigher.version}\n")
            return minHigher
        }
        val maxAvailable = runtimes.maxByOrNull { it.version }
        if (maxAvailable != null) {
            log("无兼容版本，使用最高可用: Java ${maxAvailable.version} (可能不兼容!)\n")
            return maxAvailable
        }
        return null
    }

    fun isValidJreDir(dir: File): Boolean {
        val javaBin = File(dir, "bin/java")
        val javaSh = File(dir, "bin/java.sh")
        if (!javaBin.exists() && !javaSh.exists()) return false
        val libDir = File(dir, "lib")
        if (!libDir.exists()) return false
        return true
    }

    fun findFileInDir(dir: File, name: String): Boolean {
        if (!dir.exists()) return false
        dir.listFiles()?.forEach {
            if (it.isFile && it.name == name) return true
            if (it.isDirectory && findFileInDir(it, name)) return true
        }
        return false
    }

    fun findJavaHome(): String? {
        val runtimes = JavaRuntimeManager.getInstalledRuntimes(activity)
        val best = runtimes.maxByOrNull { it.version }
        if (best != null && isValidJreDir(best.path)) {
            return best.path.absolutePath
        }
        return null
    }

    fun checkJavaAndLaunch(
        versionId: String,
        gameDir: File,
        hintJavaVersion: Int,
        onReady: (javaHome: String, javaVersion: Int) -> Unit
    ) {
        thread(name = "JavaCheckThread") {
            try {
                activity.runOnUiThread {
                    statusOverlay.setTitle("正在自动检测Java...")
                    statusOverlay.setMessage("扫描已安装的Java运行时...")
                    statusOverlay.setIndeterminate(true)
                    statusOverlay.show()
                }

                val detectedJava = autoDetectJavaVersion(versionId, gameDir, hintJavaVersion)
                log("自动检测需要的Java版本: $detectedJava\n")

                val runtimes = try {
                    JavaRuntimeManager.getInstalledRuntimes(activity)
                } catch (e: Exception) {
                    log("扫描Java目录异常: ${e.message}\n")
                    emptyList()
                }

                log("找到 ${runtimes.size} 个已安装运行时:\n")
                runtimes.forEach { rt ->
                    log("  - Java ${rt.version} @ ${rt.path.absolutePath}\n")
                }

                val matchingRuntime = selectBestRuntime(runtimes, detectedJava)
                if (matchingRuntime == null) {
                    log("未找到任何Java运行时！\n")
                    activity.runOnUiThread {
                        statusOverlay.setTitle("未找到Java运行时")
                        statusOverlay.setMessage("需要Java $detectedJava+，请先安装Java运行时")
                        Toast.makeText(activity, "未找到Java运行时，请先安装Java", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }
                val detectedJavaHome = matchingRuntime.path.absolutePath
                val detectedJavaVersion = matchingRuntime.version
                log("自动选择: Java ${matchingRuntime.version} @ $detectedJavaHome\n")

                if (!isValidJreDir(File(detectedJavaHome))) {
                    log("找不到有效的Java目录！\n")
                    activity.runOnUiThread {
                        statusOverlay.setTitle("Java检测失败")
                        statusOverlay.setMessage("Java目录无效，请重新安装")
                        Toast.makeText(activity, "Java运行时无效", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }

                val jreLibDir = File(detectedJavaHome, "lib")
                val libjliFound = findFileInDir(jreLibDir, "libjli.so")
                val libjvmFound = findFileInDir(jreLibDir, "libjvm.so")
                if (!libjliFound || !libjvmFound) {
                    log("JRE不完整！\n")
                    log("libjli.so: ${if (libjliFound) "✓" else "✗ 缺失"}\n")
                    log("libjvm.so: ${if (libjvmFound) "✓" else "✗ 缺失"}\n")
                    log("JRE目录内容: ${jreLibDir.absolutePath}\n")
                    jreLibDir.listFiles()?.forEach {
                        log("  ${it.name} (dir=${it.isDirectory})\n")
                    }
                    val serverDir = File(jreLibDir, "server")
                    if (serverDir.exists()) {
                        log("server目录内容:\n")
                        serverDir.listFiles()?.forEach {
                            log("    ${it.name}\n")
                        }
                    }
                    activity.runOnUiThread {
                        statusOverlay.setTitle("JRE不完整")
                        statusOverlay.setMessage("缺少 ${if (!libjliFound) "libjli.so " else ""}${if (!libjvmFound) "libjvm.so " else ""}")
                        Toast.makeText(activity, "JRE不完整，请重新安装Java运行时", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }

                log("Java运行时验证通过: $detectedJavaHome\n")
                log("libjli.so ✓, libjvm.so ✓\n")

                activity.runOnUiThread {
                    statusOverlay.setTitle("正在启动游戏...")
                    statusOverlay.setMessage("")
                    statusOverlay.setProgress(100)
                }
                Thread.sleep(400)
                onReady(detectedJavaHome, detectedJavaVersion)
            } catch (e: Exception) {
                log("启动检查异常: ${e.message}\n")
                e.printStackTrace()
                activity.runOnUiThread {
                    statusOverlay.setTitle("启动失败")
                    statusOverlay.setMessage(e.message)
                    Toast.makeText(activity, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
