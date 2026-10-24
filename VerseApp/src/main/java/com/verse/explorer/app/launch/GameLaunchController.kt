package com.verse.explorer.app.launch

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.core.data.VersionSettingsRepository
import com.verse.explorer.app.device.VulkanCheckManager
import com.verse.explorer.app.device.VulkanEnsureResult
import com.verse.explorer.app.device.normalizeMcVersion
import com.verse.explorer.app.device.supportFor
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
    private val settingsRepo = SettingsRepository
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

                val verSettings = VersionSettingsRepository(context).getVersionSettings(version.id)
                val verIndependent = !verSettings.usesGlobal

                val maxMemory = if (verIndependent && !verSettings.autoMemory && verSettings.maxMemory > 0) {
                    verSettings.maxMemory
                } else {
                    resolveLaunchMaxMemory(settingsRepo.getMaxMemory(), settingsRepo.getMinMemory())
                }
                val minMemory = if (verIndependent && !verSettings.autoMemory && verSettings.minMemory > 0) {
                    verSettings.minMemory
                } else {
                    settingsRepo.getMinMemory()
                }
                val gcType = settingsRepo.getGcType()
                val renderer = resolveRendererForVersion(
                    resolveMinecraftVersionForJudgement(version.id),
                    if (verIndependent && verSettings.renderer.isNotBlank()) verSettings.renderer
                    else settingsRepo.getRenderer()
                )
                val customJvmArgs = if (verIndependent) verSettings.javaArgs else settingsRepo.getCustomJvmArgs()
                val launchMethod = settingsRepo.getLaunchMethod()
                VerseLogger.info(
                    "Java:$javaVersion Mem:${maxMemory}M GC:$gcType Renderer:$renderer " +
                        "VerSetting:${if (verIndependent) "independent" else "global"}",
                    "LaunchController"
                )

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
                    putExtra("minMemory", minMemory)
                    putExtra("gcType", gcType)
                    putExtra("customJvmArgs", customJvmArgs)
                    putExtra("width", settingsRepo.getGameWidth())
                    putExtra("height", settingsRepo.getGameHeight())
                    putExtra("renderer", renderer)
                    putExtra("launchMethod", launchMethod)
                    putExtra("username", account.username)
                    putExtra("TERRACOTTA_PLAYER", account.username)
                    putExtra("uuid", account.uuid)
                    putExtra("accessToken", account.accessToken)
                    putExtra("userType", when (account.type.name) {
                        "MICROSOFT" -> "msa"
                        "MOJANG" -> "mojang"
                        else -> "mojang"
                    })
                    // 外置登录：把验证服务器 API 根透传给启动链，供 authlib-injector 注入
                    putExtra(
                        "authlibServerUrl",
                        if (account.type.name == "AUTHLIB") account.serverUrl else null
                    )
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

    /**
     * 启动内存分配：autoMemory 模式下按启动时实时可用内存计算建议值，与用户设定的下限取大。
     */
    private fun resolveLaunchMaxMemory(userMax: Int, userMin: Int): Int {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val freeM = (memInfo.availMem / 1048576L).toInt()
            val availableM = freeM - 384
            if (availableM <= 0) {
                userMax
            } else {
                val threshold = 8 * 1024
                val suggested = if (availableM <= threshold) {
                    (availableM * 0.8).toInt()
                } else {
                    ((threshold * 0.8) + (availableM - threshold) * 0.2).toInt()
                }.coerceAtMost(16384)
                val allocated = maxOf(userMax, suggested)
                VerseLogger.info(
                    "内存分配: 用户设定=${userMax}M, 可用=${freeM}M, 建议=${suggested}M, 实际=$allocated M",
                    "LaunchController"
                )
                allocated
            }
        } catch (e: Exception) {
            VerseLogger.warn("内存自动分配失败，使用用户设定值: ${e.message}", "LaunchController")
            userMax
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

        var account = VerseApplication.accountManager.selectedAccount.value
        if (account == null) {
            // 离线模式：未选择账户时自动创建并选中默认离线账户
            account = VerseApplication.accountManager.createOfflineAccount("Player")
            steps[stepIdx] = LaunchStep("账户验证", "自动创建离线账户", "check")
        } else {
            val currentAccount = account!!
            if (currentAccount.type == com.verse.explorer.core.account.AccountType.MICROSOFT &&
                currentAccount.refreshToken.isNotBlank()
            ) {
                steps[stepIdx] = LaunchStep("账户验证", "正在刷新微软令牌...", "person")
                emitState(LaunchPhase.ACCOUNT_CHECK, steps, stepIdx, "正在刷新微软令牌...")
                val refreshResult = VerseApplication.microsoftAuthenticator.refresh(currentAccount.refreshToken)
                if (refreshResult.success && refreshResult.account != null) {
                    val refreshed = refreshResult.account!!
                    account = refreshed.copy(
                        id = currentAccount.id,
                        selected = true,
                        refreshToken = refreshed.refreshToken.ifBlank { currentAccount.refreshToken }
                    )
                    VerseApplication.accountManager.updateAccount(account)
                } else {
                    VerseLogger.warn("微软令牌刷新失败: ${refreshResult.error}", "Launch")
                }
            }
            steps[stepIdx] = LaunchStep("账户验证", "完成 (${account.username})", "check")
        }
        cachedAccount = account
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
            val rawJsonJavaVer = resolveVersionJsonJavaVersion(cachedVersion.id)
            // 26.2 需要 java-runtime-epsilon (Java 25)；arm64 真机直接使用 JSON 要求的版本。
            // 严格采用版本 JSON 声明的 Java 版本（26.2 -> Java 25 -> jre25）。
            val jsonJavaVer = rawJsonJavaVer
            val recommendedJava = jsonJavaVer?.let { JavaVersion.fromMajorVersion(it) }
                ?: JavaVersion.getRecommended(cachedVersion.id)
            cachedRecommendedJava = recommendedJava
            VerseLogger.info("需要 Java: ${recommendedJava.displayName}", "Launch")

            val installedRuntimes = JavaRuntimeManager.getInstalledRuntimes(context)
            // 找不到时回退自动选择并告警
            val verSettingsForJava = versionId?.let {
                VersionSettingsRepository(context).getVersionSettings(it)
            }
            var bestRuntime: com.verse.explorer.core.java.JavaRuntime? = null
            if (verSettingsForJava != null && !verSettingsForJava.usesGlobal &&
                verSettingsForJava.java != VersionSettingsRepository.JAVA_AUTO
            ) {
                bestRuntime = installedRuntimes.find { it.name == verSettingsForJava.java }
                if (bestRuntime == null) {
                    VerseLogger.warn("定向 Java ${verSettingsForJava.java} 未安装，回退自动选择", "Launch")
                }
            }
            if (bestRuntime == null) {
                bestRuntime = selectBestRuntime(installedRuntimes, recommendedJava.majorVersion)
            }

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

        val notCheckGame = versionId?.let {
            val vs = VersionSettingsRepository(context).getVersionSettings(it)
            if (!vs.usesGlobal) vs.notCheckGame else null
        } ?: settingsRepo.getNotCheckGame()
        if (notCheckGame) {
            steps[stepIdx] = LaunchStep("文件检查", "跳过 (未检查)", "skip")
        } else if (cachedVersion == null) {
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

        // ========== Vulkan 检查（26.2+ 带 Vulkan 渲染后端的版本）==========
        // 启动前判定设备 Vulkan 能力是否满足该版本依赖，缺失项警告后允许继续；
        // 同时刷新 needsVulkanShim 门控（仅设备缺口可被 vkshim 补齐时才启用包装层）
        if (cachedVersion != null && VulkanCheckManager.hasVulkanBackend(cachedVersion.id)) {
            stepIdx++
            steps.add(LaunchStep("Vulkan 检查", "进行中...", "code"))
            emitState(LaunchPhase.FILE_CHECK, steps, stepIdx, "正在检测设备 Vulkan 能力...")

            val verSettingsForVk = versionId?.let {
                VersionSettingsRepository(context).getVersionSettings(it)
            }
            val vkDriverSystem = if (verSettingsForVk != null && !verSettingsForVk.usesGlobal) {
                verSettingsForVk.vkDriverSystem
            } else {
                settingsRepo.getVkDriverSystem()
            }
            val useTurnip = !vkDriverSystem
            val driverPath = if (useTurnip) {
                (context.applicationInfo.nativeLibraryDir)
            } else null

            var ensure = VulkanCheckManager.ensureSupported(context, cachedVersion.id, useTurnip, driverPath)
            if (ensure != VulkanEnsureResult.SUPPORTED) {
                // 无有效缓存或判定不支持：执行一次完整检测刷新结论与 shim 门控
                val capabilities = VulkanCheckManager.check(context, useTurnip, driverPath)
                ensure = if (capabilities != null &&
                    capabilities.supportFor(com.verse.explorer.app.device.normalizeMcVersion(cachedVersion.id)).isSupported
                ) VulkanEnsureResult.SUPPORTED else VulkanEnsureResult.UNSUPPORTED
                val missing = capabilities
                    ?.supportFor(com.verse.explorer.app.device.normalizeMcVersion(cachedVersion.id))
                    ?.missingRequired
                    ?.joinToString(", ") { it.dependency.name }
                if (!missing.isNullOrEmpty()) {
                    // Vulkan 缺失提示只针对 26.3：26.2 仍可回落到 OpenGL 后端，
                    // 提示缺失 Vulkan 会让玩家误以为无法启动。
                    if (vulkanMissingPromptNeeded(cachedVersion.id)) {
                        steps[stepIdx] = LaunchStep("Vulkan 检查", "警告", "warning")
                        warnings.add("设备缺失本版本必需的 Vulkan 依赖: $missing（可继续但渲染后端可能初始化失败）")
                        VerseLogger.warn("Vulkan 检查: 设备缺失 $missing", "LaunchController")
                        // launchCheck 运行在 Dispatchers.IO，Toast 必须切主线程（否则
                        // Can't toast on a thread that has not called Looper.prepare()）
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(
                                context,
                                "当前设备图形能力有限，可能无法运行此版本；可尝试在版本设置中更换渲染器",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        steps[stepIdx] = LaunchStep("Vulkan 检查", "完成 (缺项可回落 GL)", "check")
                        VerseLogger.info("Vulkan 检查: 设备缺失 $missing，26.2 按 OpenGL 回落", "LaunchController")
                    }
                } else if (capabilities == null) {
                    steps[stepIdx] = LaunchStep("Vulkan 检查", "跳过 (检测失败)", "skip")
                    VerseLogger.warn("Vulkan 检测失败，按无信息继续", "LaunchController")
                } else {
                    steps[stepIdx] = LaunchStep("Vulkan 检查", "完成 (${capabilities.versionString})", "check")
                }
            } else {
                steps[stepIdx] = LaunchStep("Vulkan 检查", "完成", "check")
            }
            emitState(LaunchPhase.FILE_CHECK, steps, stepIdx, steps[stepIdx].status)
        }

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


    /**
     * 根据游戏版本自动选择渲染器。
     * GL4ES（ES2 模式）最高支持 MC 1.21.4；26.x 及更高版本引入了
     * glMapBufferRange/持久化缓冲映射等 ES3/GL3 API，ES2 下会
     * "Failed to map buffer"，因此自动切换到 NGGL4ES(ES3)。
     */
    /**
     * 解析用于渲染器判定的真实 Minecraft 版本号。
     * 普通版本直接返回版本 id；整合包版本（id 为整合包名）从版本目录的
     * modpack.json 读取 manifest.dependencies.minecraft（如 "26.2"）。
     */
    private fun resolveMinecraftVersionForJudgement(versionId: String): String {
        val id = versionId.ifBlank { return "" }
        // 版本 id 以数字/字母数字形式出现（"26.2"、"1.21.1"、"1.RV-Pre1"）无需特殊处理
        if (Regex("""^\d""").matches(id)) return id
        try {
            val modpackJson = File(gameDir, "versions/$id/modpack.json")
            if (modpackJson.exists()) {
                val json = com.google.gson.GsonBuilder().create()
                    .fromJson(modpackJson.readText(), com.google.gson.JsonObject::class.java)
                val deps = json.getAsJsonObject("manifest")?.getAsJsonObject("dependencies")
                val mc = deps?.get("minecraft")?.asString
                if (!mc.isNullOrBlank()) {
                    VerseLogger.info("整合包 $id 的 Minecraft 版本: $mc (来自 modpack.json)", "Launch")
                    return mc
                }
            }
        } catch (e: Exception) {
            VerseLogger.warn("解析整合包 Minecraft 版本失败: ${e.message}", "Launch")
        }
        return id
    }

    private fun resolveRendererForVersion(versionId: String, selectedRenderer: String): String {
        // gl4es（ES2，libgl4es_114.so）无法提供，主菜单渲染即 "Failed to map buffer" 崩溃。
        // 其配套 EGL 必须为 ANGLE（libEGL_angle.so/libGLESv2_angle.so），否则落到系统 Mali EGL SIGSEGV。
        // APK 已打包 libng_gl4es.so/libspirv-cross-c-shared.so/libEGL_angle.so/libGLESv2_angle.so。
        if (selectedRenderer.isBlank() || selectedRenderer.equals("gl4es", ignoreCase = true)) {
            if (versionNewerThan(versionId, "1.21.4")) {
                return "nggl4es"
            }
        }
        return selectedRenderer.ifBlank { "gl4es" }
    }

    private fun versionNewerThan(versionId: String, base: String): Boolean {
        try {
            val a = numericPrefix(versionId)
            val b = numericPrefix(base)
            if (a.isNotEmpty() && b.isNotEmpty()) {
                val len = maxOf(a.size, b.size)
                for (i in 0 until len) {
                    val x = if (i < a.size) a[i] else 0
                    val y = if (i < b.size) b[i] else 0
                    if (x != y) return x > y
                }
                return false
            }
        } catch (_: Exception) {}
        return versionId.startsWith("26.") || versionId.startsWith("25.") ||
            versionId.startsWith("24.") || versionId.startsWith("1.22.")
    }

    private fun numericPrefix(v: String): List<Int> {
        val m = Regex("""\d+(\.\d+)*""").find(v) ?: return emptyList()
        return m.value.split(".").mapNotNull { it.toIntOrNull() }
    }

    /** Vulkan 缺失提示的版本门槛：只有 26.3 起必须 Vulkan，26.2 可回落 OpenGL */
    private fun vulkanMissingPromptNeeded(versionId: String): Boolean =
        com.tungsten.fclcore.util.versioning.GameVersionNumber.compare(
            com.verse.explorer.app.device.normalizeMcVersion(versionId), "26.3"
        ) >= 0

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
        VerseLogger.warn("无兼容版本 (需要Java $requiredVersion)", "Launch")
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