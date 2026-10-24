package com.verse.explorer.app.game.compat

import androidx.activity.ComponentActivity
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.core.launch.LaunchRequest
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.launcher.fcl.auth.AuthInfo
import com.verse.explorer.launcher.fcl.game.DefaultGameRepository
import com.verse.explorer.launcher.fcl.game.JavaVersion
import com.verse.explorer.launcher.fcl.game.LaunchOptions
import com.verse.explorer.launcher.fcl.game.VerseJavaVersion
import com.verse.explorer.launcher.fcl.game.Version
import com.verse.explorer.launcher.fcl.launch.DefaultLauncher
import com.verse.explorer.launcher.fcl.util.FCLPath
import com.tungsten.fcl.setting.MenuSetting
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

import com.verse.explorer.app.game.*
import com.verse.explorer.launcher.runtime.PreparedFclLaunch

/** FCL 类型只在此适配器中参与准备，界面会话通过 Core 协议工作。 */
internal class FclLaunchPreparation(
    private val activity: ComponentActivity,
    private val nativesDeployer: NativesDeployer,
    private val logManager: GameLogManager,
    private val javaDetector: JavaRuntimeDetector,
    private val surfaceSize: () -> Pair<Int, Int>,
    private val progress: suspend (String, String?) -> Unit
) {
    companion object { private const val TAG = "GameLaunchOrchestrator" }
    var javaHome: String? = null
        private set
    private val optionsInjector = OptionsInjector(activity)
    private val jreResolver = JreResolver(activity, javaDetector)
    private val surfaceWidth: Int get() = surfaceSize().first
    private val surfaceHeight: Int get() = surfaceSize().second
    private suspend fun updateProgress(title: String, message: String?) = progress(title, message)
    private fun elapsed(start: Long): Long = System.currentTimeMillis() - start

    suspend fun prepare(params: LaunchRequest): PreparedFclLaunch {
        val t0 = System.currentTimeMillis()
        val gameDir = File(params.gameDirPath)
        VerseLogger.info("==========================================", TAG)
        VerseLogger.info("启动流程开始 (step-by-step)", TAG)
        VerseLogger.info("==========================================", TAG)

        // 1) 版本解析
        updateProgress("解析版本信息...", "版本: ${params.versionId}")
        val fclVersion = resolveFclVersion(params, gameDir)
        VerseLogger.info("[1/9 版本解析] ${fclVersion.id}, mainClass=${fclVersion.getMainClass()}, 耗时=${elapsed(t0)}ms", TAG)

        // 1.5) 模组兼容性预检：不兼容的模组（加载器不对 / MC 版本不符 / Java 不够）
        // 以前只能在游戏里静默失败或崩溃，用户无从得知原因（后台反馈「模组玩不了」）。
        // 这里提前到启动期给出条目式原因；版本设置里关掉「模组检查」可跳过。
        val verSettings = com.verse.explorer.core.data.VersionSettingsRepository(activity)
            .getVersionSettings(params.versionId)
        if (!verSettings.notCheckMod) {
            runModCompatibilityCheck(params, gameDir, verSettings)
        }

        // 2) 原生库部署（静默执行，不在启动界面显示部署过程）
        val nativesDir = deployNatives(gameDir, params)
        VerseLogger.info("[2/9 原生库] 部署到 ${nativesDir.absolutePath}, 耗时=${elapsed(t0)}ms", TAG)

        nativesDeployer.deployFclRuntime(gameDir)
        VerseLogger.info("[3/9 运行时] 完成, 耗时=${elapsed(t0)}ms", TAG)

        // 4) Java 运行时检查
        updateProgress("检查 Java 运行时...", null)
        val resolvedJavaHome = jreResolver.resolveJavaHome(params, gameDir)
        javaHome = resolvedJavaHome
        logManager.javaHomeProvider = { javaHome }
        VerseLogger.info("[4/9 Java运行时] 选用 JRE=$resolvedJavaHome, 耗时=${elapsed(t0)}ms", TAG)

        updateProgress("初始化运行时路径...", null)
        FCLPath.loadPaths(activity, gameDir.absolutePath)
        VerseLogger.info("[5/9 运行时路径] FILES_DIR=${FCLPath.FILES_DIR}, 耗时=${elapsed(t0)}ms", TAG)

        // 6) 启动选项构建
        updateProgress("构建启动选项...", null)
        val authInfo = createAuthInfo(params)
        val javaVersion = VerseJavaVersion(params.javaVersion, resolvedJavaHome)
        val launchOptions = buildLaunchOptions(params, javaVersion, verSettings)
        val repository = DefaultGameRepository(gameDir)
        repository.setRunDirectoryIsolated(verSettings.usesGlobal || verSettings.isolateGameDir)
        repository.refreshVersions()
        optionsInjector.injectOnboardingSkip(repository, params.versionId)
        optionsInjector.injectGameLanguage(repository, fclVersion)
        optionsInjector.injectGraphicsBackend(
            repository, fclVersion, params.renderer,
            if (!verSettings.usesGlobal) verSettings.graphicsBackend else null
        )
        optionsInjector.dumpRenderOptions(repository, fclVersion, params)
        VerseLogger.info("[6/9 启动选项] renderer=${params.renderer}, options=${launchOptions.fullSummary()}, 耗时=${elapsed(t0)}ms", TAG)

        val launcher = DefaultLauncher(activity, repository, fclVersion, authInfo, launchOptions)
        launcher.setNativesDirectory(nativesDir.absolutePath)
        return PreparedFclLaunch(
            request = params,
            launcher = launcher,
            gameDir = gameDir,
            nativesDir = nativesDir,
            startedAtMillis = t0,
            vkDriverSystem = if (!verSettings.usesGlobal) verSettings.vkDriverSystem else SettingsRepository.getVkDriverSystem(),
            vulkanShimEnabled = com.verse.explorer.app.device.VulkanCheckManager.needsVulkanShim,
            pipelineCacheEnabled = SettingsRepository.getVersexPipelineCache()
        )
    }

    private fun resolveFclVersion(params: LaunchRequest, gameDir: File): Version {
        val repository = DefaultGameRepository(gameDir)
        repository.refreshVersions()
        return repository.getVersion(params.versionId)
    }

    /**
     * 启动前模组兼容性预检。
     *
     * mods 目录语义必须与 buildLaunchOptions 的运行目录一致：
     * `usesGlobal || isolateGameDir` → 版本隔离，mods 在 versions/<id>/mods。
     * 发现不兼容条目直接抛错停止启动（错误信息走启动失败弹窗），
     * 避免用户装了不生效/进游戏即崩还不知道原因。
     */
    private fun runModCompatibilityCheck(
        params: LaunchRequest,
        gameDir: File,
        verSettings: com.verse.explorer.core.data.VersionSettingsRepository.VersionSettings
    ) {
        runCatching {
            val versionJsonFile = File(
                com.verse.explorer.core.config.GameConfig.getVersionsDir(gameDir),
                "${params.versionId}/${params.versionId}.json"
            )
            val versionJson = if (versionJsonFile.isFile) {
                runCatching {
                    com.google.gson.JsonParser.parseString(versionJsonFile.readText()).asJsonObject
                }.getOrNull()
            } else null
            val info = LoaderTypeResolver.resolve(versionJson, params.versionId)

            val isolated = verSettings.usesGlobal || verSettings.isolateGameDir
            val modsDir = if (isolated) {
                File(File(File(params.gameDirPath), "versions"), "${params.versionId}/mods")
            } else {
                File(params.gameDirPath, "mods")
            }

            val report = com.verse.explorer.core.mod.ModCompatibilityChecker.check(
                modsDir, info.baseVersionId, info.loaderType, params.javaVersion
            )
            if (report.modCount > 0 && !report.hasIssues) {
                VerseLogger.info("[1.5/9 模组预检] ${report.modCount} 个模组全部兼容", TAG)
            }
            if (!report.hasIssues) return

            val shown = report.issues.take(3).joinToString("\n") {
                "• ${it.file}：${it.reason}"
            }
            val more = report.issues.size - minOf(3, report.issues.size)
            val detail = shown + if (more > 0) "\n• …等共 ${report.issues.size} 个问题" else ""
            VerseLogger.error("模组兼容性预检未通过:\n$detail", TAG)
            throw IllegalStateException(
                "检测到与当前版本不兼容的模组，已停止启动：\n$detail\n\n" +
                    "请到 版本设置 → 模组管理 中移除或禁用这些模组后重试；" +
                    "确认无误也可在版本设置中关闭「模组检查」强制启动。"
            )
        }.onFailure { e ->
            if (e is IllegalStateException) throw e
            // 预检自身异常不阻断启动，只记日志
            VerseLogger.warn("模组兼容性预检异常（已跳过）: ${e.message}", TAG)
        }
    }

    private suspend fun createAuthInfo(params: LaunchRequest): AuthInfo {
        // 外置登录：把验证服务器返回的 textures 属性一并交给游戏（--userProperties），
        // 否则客户端拿不到角色皮肤，只能显示默认皮肤
        val authlibServerUrl = params.authlibServerUrl
        val userProperties = if (!authlibServerUrl.isNullOrEmpty()) {
            val textures = runCatching {
                VerseApplication.authlibAuthenticator
                    .fetchTexturesValue(authlibServerUrl, params.uuid)
            }.getOrNull()
            if (textures.isNullOrEmpty()) "{}" else """{"textures":["$textures"]}"""
        } else {
            "{}"
        }
        return AuthInfo(
            params.username,
            parseUuid(params.uuid),
            params.accessToken,
            params.userType,
            userProperties
        )
    }

    private fun parseUuid(raw: String): UUID {
        val compact = raw.replace("-", "")
        if (compact.length != 32) {
            return UUID.nameUUIDFromBytes(raw.toByteArray())
        }
        return UUID.fromString(
            compact.substring(0, 8) + "-" +
            compact.substring(8, 12) + "-" +
            compact.substring(12, 16) + "-" +
            compact.substring(16, 20) + "-" +
            compact.substring(20)
        )
    }

    private fun buildLaunchOptions(
        params: LaunchRequest,
        javaVersion: JavaVersion,
        verSettings: com.verse.explorer.core.data.VersionSettingsRepository.VersionSettings
    ): LaunchOptions {
        // 强制分辨率（版本级设置）：直接用设定宽高，不按百分比规则换算。
        // setWidth((int)(screenWidth * scaleFactor)) —— 缩放三同步的另外两处：
        // execute 前 setScaleFactor + setDefaultBufferSize/pushEventWindow（见第 9 步）。
        val forceRes = !verSettings.usesGlobal && verSettings.forceResolution &&
            verSettings.width > 0 && verSettings.height > 0
        val resolvedSize = MenuSetting.resolveRenderSize(surfaceWidth, surfaceHeight)
        val effectiveWidth = if (forceRes) {
            verSettings.width
        } else if (surfaceWidth > 0) {
            resolvedSize.first
        } else {
            params.gameWidth
        }
        val effectiveHeight = if (forceRes) {
            verSettings.height
        } else if (surfaceHeight > 0) {
            resolvedSize.second
        } else {
            params.gameHeight
        }
        // 版本隔离：开启时运行目录指向版本文件夹（与 getRunDirectory 一致），
        // 关闭时回到游戏根目录
        val isolated = verSettings.usesGlobal || verSettings.isolateGameDir
        val runDir = if (isolated) {
            File(File(File(params.gameDirPath), "versions"), params.versionId).apply { mkdirs() }
        } else {
            File(params.gameDirPath).apply { mkdirs() }
        }
        val debugLog = if (!verSettings.usesGlobal) verSettings.debugLog else SettingsRepository.getDebugLog()
        return LaunchOptions.Builder()
            .setGameDir(runDir)
            .setJava(javaVersion)
            .setWidth(effectiveWidth)
            .setHeight(effectiveHeight)
            .setMaxMemory(params.maxMemory)
            .setMinMemory(params.minMemory)
            .setRenderer(params.renderer)
            .setUUid(params.uuid)
            .setJavaArguments(params.customJvmArgs.split(" ").filter { it.isNotBlank() })
            .setGameArguments(verSettings.minecraftArgs.split(" ").filter { it.isNotBlank() })
            .setServerIp(verSettings.serverIp)
            .setDebugLog(debugLog)
            .setAuthlibServerUrl(params.authlibServerUrl)
            .create()
    }

    private fun deployNatives(gameDir: File, params: LaunchRequest): File {
        val externalNativesDir = File(gameDir, "natives")
        val librariesDir = File(gameDir, "libraries")
        val internalNativesDir = nativesDeployer.copyNativesToInternalStorage(externalNativesDir, params.javaHome, librariesDir)
        nativesDeployer.extractDefaultNatives(internalNativesDir)
        return internalNativesDir
    }

}
