package com.verse.explorer.app.launch

import android.content.Context
import android.content.Intent
import android.util.Log
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.app.ui.game.GameActivity
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.java.JavaRuntime
import com.verse.explorer.core.java.JavaRuntimeManager
import com.verse.explorer.core.java.JavaVersion
import com.verse.explorer.core.version.VersionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class LaunchStep(
    val name: String,
    val status: String,
    val icon: String
)

enum class LaunchPhase {
    IDLE, PREPARING, ACCOUNT_CHECK, VERSION_CHECK,
    JAVA_CHECK, FILE_CHECK, BUILD_ARGS, LAUNCHING, FAILED
}

data class LaunchState(
    val phase: LaunchPhase = LaunchPhase.IDLE,
    val steps: List<LaunchStep> = emptyList(),
    val currentStepIndex: Int = -1,
    val message: String = "点击启动开始游戏",
    val isLaunching: Boolean = false,
    val errors: List<String> = emptyList()
)

class GameLaunchController(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settingsRepo = SettingsRepository(context)
    private val gameDir = GameConfig.getGameDir(context)
    private val versionManager = VersionManager(gameDir)

    private val _launchState = MutableStateFlow(LaunchState())
    val launchState: StateFlow<LaunchState> = _launchState.asStateFlow()

    fun launchGame(versionId: String?, multiplayerArgs: Map<String, String>? = null) {
        if (_launchState.value.isLaunching) return

        scope.launch {
            _launchState.value = LaunchState(
                phase = LaunchPhase.PREPARING,
                isLaunching = true,
                message = "正在准备启动..."
            )
            VerseLogger.info("开始启动游戏流程", "LaunchController")

            try {
                val phaseSteps = launchCheck(versionId) ?: return@launch
                val (account, version, bestRuntime, javaVersion, javaHome, steps) = phaseSteps

                val maxMemory = settingsRepo.getMaxMemory()
                val gcType = settingsRepo.getGcType()
                val renderer = settingsRepo.getRenderer()
                val launchMethod = settingsRepo.getLaunchMethod()
                VerseLogger.info("Java:$javaVersion Mem:${maxMemory}M GC:$gcType Renderer:$renderer", "LaunchController")

                steps[steps.lastIndex] = LaunchStep("构建参数", "完成", "check")
                val finalSteps = steps + LaunchStep("启动游戏", "准备中...", "play")

                _launchState.value = _launchState.value.copy(
                    phase = LaunchPhase.LAUNCHING,
                    steps = finalSteps,
                    currentStepIndex = finalSteps.lastIndex,
                    message = "正在启动 Minecraft..."
                )

                val intent = Intent(context, GameActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("javaHome", javaHome.absolutePath)
                    putExtra("javaVersion", javaVersion)
                    putExtra("versionId", version.id)
                    putExtra("gameDir", gameDir.absolutePath)
                    putExtra("assetsDir", GameConfig.getAssetsDir(gameDir).absolutePath)
                    putExtra("librariesDir", GameConfig.getLibrariesDir(gameDir).absolutePath)
                    putExtra("nativesDir", GameConfig.getNativesDir(gameDir).absolutePath)
                    putExtra("maxMemory", maxMemory)
                    putExtra("minMemory", settingsRepo.getMinMemory())
                    putExtra("gcType", gcType)
                    putExtra("customJvmArgs", settingsRepo.getCustomJvmArgs())
                    putExtra("width", settingsRepo.getGameWidth())
                    putExtra("height", settingsRepo.getGameHeight())
                    putExtra("renderer", renderer)
                    putExtra("launchMethod", launchMethod)
                    putExtra("username", account.username)
                    putExtra("uuid", account.uuid)
                    putExtra("accessToken", account.accessToken)
                    putExtra("userType", when (account.type.name) {
                        "MICROSOFT" -> "msa"
                        "MOJANG" -> "mojang"
                        else -> "mojang"
                    })
                    if (multiplayerArgs != null) {
                        putExtra("multiplayerMode", true)
                        multiplayerArgs.forEach { (k, v) -> putExtra(k, v) }
                    }
                }

                context.startActivity(intent)

                val allSteps = finalSteps.toMutableList()
                allSteps[allSteps.lastIndex] = LaunchStep("启动游戏", "已启动", "play")
                _launchState.value = _launchState.value.copy(
                    steps = allSteps,
                    isLaunching = false,
                    message = "Minecraft 正在启动..."
                )
                VerseLogger.info("GameActivity 已启动", "LaunchController")

            } catch (e: Exception) {
                Log.e("LaunchController", "启动异常: ${e.message}", e)
                VerseLogger.error("启动异常: ${e.message}", "LaunchController")
                _launchState.value = _launchState.value.copy(
                    phase = LaunchPhase.FAILED,
                    isLaunching = false,
                    message = "启动异常",
                    errors = listOf("系统异常: ${e.message ?: "未知错误"}")
                )
            }
        }
    }

    private suspend fun launchCheck(versionId: String?): PhaseData? {
        val steps = mutableListOf<LaunchStep>()
        val criticalErrors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var stepIdx = 0
        var cachedVersion: com.verse.explorer.core.version.MinecraftVersion? = null
        var cachedAccount: com.verse.explorer.core.account.Account? = null
        var cachedRuntime: JavaRuntime? = null
        var cachedRecommendedJava: com.verse.explorer.core.java.JavaVersion? = null

        val targetVersionId = versionId ?: settingsRepo.getCustomSetting("last_version")

        // ========== 账户验证 ==========
        steps.add(LaunchStep("账户验证", "进行中...", "person"))
        emitState(LaunchPhase.ACCOUNT_CHECK, steps, stepIdx, "正在检查账户状态...")
        delay(200)

        val account = VerseApplication.accountManager.selectedAccount.value
        if (account == null) {
            steps[stepIdx] = LaunchStep("账户验证", "失败", "error")
            criticalErrors.add("未选择账户，请先在账户页面添加账户")
        } else {
            steps[stepIdx] = LaunchStep("账户验证", "完成 (${account.username})", "check")
            cachedAccount = account
        }
        emitState(LaunchPhase.ACCOUNT_CHECK, steps, stepIdx, steps[stepIdx].status)

        // ========== 版本解析 ==========
        stepIdx++
        steps.add(LaunchStep("版本解析", "进行中...", "download"))
        emitState(LaunchPhase.VERSION_CHECK, steps, stepIdx, "正在解析版本信息...")
        delay(200)

        if (cachedAccount == null) {
            steps[stepIdx] = LaunchStep("版本解析", "跳过 (未登录)", "skip")
        } else {
            val version = versionManager.getLocalVersions().find { it.id == targetVersionId }
            if (version == null) {
                steps[stepIdx] = LaunchStep("版本解析", "失败", "error")
                criticalErrors.add("未找到游戏版本: $targetVersionId，请先下载版本")
            } else {
                steps[stepIdx] = LaunchStep("版本解析", "完成 (${version.id})", "check")
                cachedVersion = version
                settingsRepo.setCustomSetting("last_version", version.id)
            }
        }
        emitState(LaunchPhase.VERSION_CHECK, steps, stepIdx, steps[stepIdx].status)

        // ========== Java 检查 ==========
        stepIdx++
        steps.add(LaunchStep("Java 检查", "进行中...", "code"))
        emitState(LaunchPhase.JAVA_CHECK, steps, stepIdx, "正在检查 Java 运行时...")

        if (cachedVersion == null) {
            steps[stepIdx] = LaunchStep("Java 检查", "跳过 (前置失败)", "skip")
        } else {
            val jsonJavaVer = resolveVersionJsonJavaVersion(cachedVersion.id)
            val recommendedJava = jsonJavaVer?.let { JavaVersion.fromMajorVersion(it) }
                ?: JavaVersion.getRecommended(cachedVersion.id)
            cachedRecommendedJava = recommendedJava
            VerseLogger.info("需要 Java: ${recommendedJava.displayName}", "Launch")

            val installedRuntimes = JavaRuntimeManager.getInstalledRuntimes(context)
            var bestRuntime = selectBestRuntime(installedRuntimes, recommendedJava.majorVersion)

            if (bestRuntime == null) {
                VerseLogger.warn("JRE ${recommendedJava.displayName} 未安装，尝试自动安装...", "Launch")
                steps[stepIdx] = LaunchStep("Java 检查", "自动安装中...", "download")
                emitState(LaunchPhase.JAVA_CHECK, steps, stepIdx, "正在自动安装 ${recommendedJava.displayName}...")

                val javaKey = when (recommendedJava.majorVersion) {
                    8 -> "java8"; 21 -> "java21"; 25 -> "java25"
                    else -> "java17"
                }
                val result = JavaRuntimeManager.installJre(context, javaKey) { progress ->
                    val status = when {
                        progress.status.name == "COMPLETED" -> "安装完成"
                        progress.status.name.contains("FAILED", ignoreCase = true) -> "安装失败: ${progress.message}"
                        else -> "解压中..."
                    }
                    steps[stepIdx] = LaunchStep("Java 检查", status, if (status == "安装完成") "check" else "download")
                    emitState(LaunchPhase.JAVA_CHECK, steps, stepIdx, progress.message)
                }

                if (result.isSuccess) {
                    val newRuntimes = JavaRuntimeManager.getInstalledRuntimes(context)
                    bestRuntime = selectBestRuntime(newRuntimes, recommendedJava.majorVersion)
                }
            }

            if (bestRuntime == null) {
                steps[stepIdx] = LaunchStep("Java 检查", "失败", "error")
                criticalErrors.add("需要 ${recommendedJava.displayName}+，请先在Java页面安装")
            } else {
                steps[stepIdx] = LaunchStep("Java 检查", "完成 (${bestRuntime.name})", "check")
                cachedRuntime = bestRuntime
            }
        }
        emitState(LaunchPhase.JAVA_CHECK, steps, stepIdx, steps[stepIdx].status)

        // ========== 文件检查 (仅警告，不阻断) ==========
        stepIdx++
        steps.add(LaunchStep("文件检查", "进行中...", "folder"))
        emitState(LaunchPhase.FILE_CHECK, steps, stepIdx, "正在验证游戏文件...")
        delay(200)

        if (cachedVersion == null) {
            steps[stepIdx] = LaunchStep("文件检查", "跳过 (版本缺失)", "skip")
        } else {
            val versionDir = File(gameDir, "versions/${cachedVersion.id}")
            val versionJar = File(versionDir, "${cachedVersion.id}.jar")
            val versionJson = File(versionDir, "${cachedVersion.id}.json")
            val nativesDir = GameConfig.getNativesDir(gameDir)

            val clientOk = (versionJar.exists() && versionJar.length() > 1024) ||
                findInheritedJar(versionDir, gameDir) != null
            val jsonOk = versionJson.exists() && versionJson.length() > 0
            val nativesOk = nativesDir.exists() && (nativesDir.listFiles()?.size ?: 0) > 0

            val problems = mutableListOf<String>()
            if (!clientOk) problems.add("客户端JAR缺失")
            if (!jsonOk) problems.add("版本JSON缺失")
            if (!nativesOk) problems.add("原生库缺失")

            if (problems.isNotEmpty()) {
                steps[stepIdx] = LaunchStep("文件检查", "警告", "warning")
                warnings.addAll(problems)
            } else {
                steps[stepIdx] = LaunchStep("文件检查", "完成", "check")
            }
        }
        emitState(LaunchPhase.FILE_CHECK, steps, stepIdx, steps[stepIdx].status)

        // ========== 检查汇总 ==========
        if (criticalErrors.isNotEmpty()) {
            _launchState.value = _launchState.value.copy(
                phase = LaunchPhase.FAILED,
                steps = steps,
                currentStepIndex = stepIdx,
                message = "启动检查失败，请修复以下问题后重试",
                isLaunching = false,
                errors = criticalErrors
            )
            VerseLogger.error("启动检查失败: ${criticalErrors.joinToString("; ")}", "LaunchController")
            return null
        }

        // ========== 有警告但继续启动 ==========
        if (warnings.isNotEmpty()) {
            VerseLogger.warn("启动时有文件警告: ${warnings.joinToString(", ")}", "LaunchController")
        }

        // ========== 准备启动 ==========
        stepIdx++
        steps.add(LaunchStep("构建参数", "进行中...", "settings"))
        emitState(LaunchPhase.BUILD_ARGS, steps, stepIdx, "正在生成启动参数...")
        delay(200)
        steps[stepIdx] = LaunchStep("构建参数", "完成", "check")

        return PhaseData(
            cachedAccount!!,
            cachedVersion!!,
            cachedRuntime!!,
            cachedRuntime!!.version,
            cachedRuntime!!.path,
            steps
        )
    }

    fun stopGame() {
        _launchState.value = LaunchState(message = "游戏已停止")
    }

    fun destroy() {
        scope.cancel()
    }

    // ========== FCL-style: 辅助方法 ==========

    private fun selectBestRuntime(runtimes: List<JavaRuntime>, requiredVersion: Int): JavaRuntime? {
        val exactMatch = runtimes.find { it.version == requiredVersion }
        if (exactMatch != null) {
            VerseLogger.info("精确匹配Java ${exactMatch.version}", "Launch")
            return exactMatch
        }
        val minHigher = runtimes.filter { it.version >= requiredVersion }.minByOrNull { it.version }
        if (minHigher != null) {
            VerseLogger.info("选择最小兼容Java ${minHigher.version}", "Launch")
            return minHigher
        }
        val maxAvailable = runtimes.maxByOrNull { it.version }
        if (maxAvailable != null) {
            VerseLogger.warn("无兼容版本，回退最高可用Java ${maxAvailable.version}", "Launch")
            return maxAvailable
        }
        return null
    }

    private fun resolveVersionJsonJavaVersion(versionId: String?): Int? {
        if (versionId.isNullOrEmpty()) return null
        try {
            val versionsRootDir = File(gameDir, "versions")
            var jsonFile = File(versionsRootDir, "$versionId/$versionId.json")
            val resolved = mutableSetOf<String>()

            while (jsonFile.exists() && resolved.add(jsonFile.parentFile?.name ?: "")) {
                val jsonStr = jsonFile.readText()
                val json = com.google.gson.GsonBuilder().create()
                    .fromJson(jsonStr, com.google.gson.JsonObject::class.java)

                val javaVersionObj = json.getAsJsonObject("javaVersion")
                if (javaVersionObj != null) {
                    val majorVersion = javaVersionObj.get("majorVersion")?.asInt
                    if (majorVersion != null && majorVersion > 0) return majorVersion
                }

                val complianceLevel = json.get("complianceLevel")?.asInt
                if (complianceLevel != null && complianceLevel > 0) return complianceLevel

                val inheritsFrom = json.get("inheritsFrom")?.asString
                if (inheritsFrom.isNullOrEmpty() || inheritsFrom == versionId) break
                jsonFile = File(versionsRootDir, "$inheritsFrom/$inheritsFrom.json")
            }
        } catch (e: Exception) {
            VerseLogger.error("解析版本JSON继承链失败: ${e.message}", "Launch")
        }
        return null
    }

    private fun findInheritedJar(versionDir: File, gameDir: File): File? {
        val jsonFile = File(versionDir, "${versionDir.name}.json")
        if (!jsonFile.exists()) return null
        try {
            val jsonText = jsonFile.readText()
            val jarMatch = Regex("\"jar\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)
            val inheritMatch = Regex("\"inheritsFrom\"\\s*:\\s*\"([^\"]+)\"").find(jsonText)
            val parentId = jarMatch?.groupValues?.get(1) ?: inheritMatch?.groupValues?.get(1)
            if (parentId != null) {
                val parentDir = File(gameDir, "versions/$parentId")
                val parentJar = File(parentDir, "$parentId.jar")
                if (parentJar.exists() && parentJar.length() > 1024) return parentJar
            }
        } catch (_: Exception) {}
        return null
    }

    private fun emitState(phase: LaunchPhase, steps: List<LaunchStep>, idx: Int, msg: String) {
        _launchState.value = _launchState.value.copy(
            phase = phase,
            steps = steps.toList(),
            currentStepIndex = idx,
            message = msg
        )
    }

    private data class PhaseData(
        val account: com.verse.explorer.core.account.Account,
        val version: com.verse.explorer.core.version.MinecraftVersion,
        val runtime: JavaRuntime,
        val javaVersion: Int,
        val javaHome: File,
        val steps: MutableList<LaunchStep>
    )
}