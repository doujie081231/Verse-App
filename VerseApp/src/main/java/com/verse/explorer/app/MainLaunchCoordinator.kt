package com.verse.explorer.app

import android.widget.Toast
import com.verse.explorer.app.setup.RuntimeSetupManager
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.core.java.JavaRuntimeManager
import com.verse.explorer.core.launch.LaunchRequest
import com.verse.explorer.app.launch.LaunchIntentCodec
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/**
 * 启动协调器：运行时状态检查与游戏启动转发。
 * 从 MainActivity 拆出，持有 Activity 引用读写其状态（jreReady/setupComplete/stateChecked）。
 */
internal class MainLaunchCoordinator(private val activity: MainActivity) {

    @Volatile
    private var runtimeCheckRunning = false

    fun checkRuntimeAsync() {
        if (runtimeCheckRunning || activity.stateChecked.value) return
        runtimeCheckRunning = true
        activity.lifecycleScope.launch(Dispatchers.Main) {
            try {
                val status = withContext(Dispatchers.IO) {
                    RuntimeSetupManager.loadPaths(activity.applicationContext)
                    RuntimeSetupManager.checkAllComponents(activity.applicationContext)
                }
                val allReady = status.all { (component, ready) ->
                    component.isOptional || ready
                }
                if (allReady) {
                    activity.setupComplete.value = true
                    activity.jreReady.value = true
                    VerseLogger.info("所有运行时已就绪，跳过设置", "MainActivity")
                } else {
                    val missing = status.filter { !it.value }.keys.joinToString { it.displayName }
                    VerseLogger.info("缺少运行时: $missing", "MainActivity")
                }
                activity.stateChecked.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VerseLogger.error("运行时检查失败: ${e.message}", "MainActivity")
                activity.stateChecked.value = true
            } finally {
                runtimeCheckRunning = false
            }
        }
    }

    fun launchGame(versionId: String?) {
        if (!activity.jreReady.value) {
            Toast.makeText(activity, "Java运行时尚未初始化完成，请稍候...", Toast.LENGTH_LONG).show()
            return
        }
        val settingsRepo = SettingsRepository
        val gameDir = GameConfig.getGameDir(activity)
        val javaVerSetting = settingsRepo.getJavaVersion()
        val javaVer = javaVerSetting.toIntOrNull() ?: 17

        val anyJavaInstalled = JavaRuntimeManager.hasJava(activity)

        if (!anyJavaInstalled) {
            Toast.makeText(activity, "正在自动安装Java运行时，请稍候...", Toast.LENGTH_LONG).show()
            activity.lifecycleScope.launch(Dispatchers.Main) {
                val javaVersion = when {
                    javaVer <= 8 -> "java8"
                    javaVer >= 21 -> "java21"
                    else -> "java17"
                }
                val installResult = activity.appContainer.runtimeInstalls.install(javaVersion).await()
                activity.runOnUiThread {
                    if (installResult.isSuccess) {
                        startGameWithJava(versionId, gameDir)
                    } else {
                        Toast.makeText(activity, "Java安装失败，请到Java页面手动安装", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }

        startGameWithJava(versionId, gameDir)
    }

    private fun startGameWithJava(versionId: String?, gameDir: File) {
        var javaHome: String? = null

        val settingsRepo = SettingsRepository
        val javaVerSetting = settingsRepo.getJavaVersion()
        val requiredJavaVer = javaVerSetting.toIntOrNull() ?: 17

        val allRuntimes = JavaRuntimeManager.getInstalledRuntimes(activity)
        val preferredRuntime = allRuntimes.find { it.version == requiredJavaVer }
        if (preferredRuntime != null) {
            javaHome = preferredRuntime.path.absolutePath
        }

        val jreDir = File(gameDir, "jre")
        if (javaHome == null && jreDir.exists()) {
            jreDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val javaBin = File(dir, "bin/java")
                    if (javaBin.exists()) {
                        javaHome = dir.absolutePath
                    }
                }
            }
        }

        val internalJreDir = File(activity.filesDir, "jre")
        if (javaHome == null && internalJreDir.exists()) {
            internalJreDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val javaBin = File(dir, "bin/java")
                    if (javaBin.exists()) {
                        javaHome = dir.absolutePath
                    }
                }
            }
        }

        if (javaHome == null) {
            val best = allRuntimes.maxByOrNull { it.version }
            if (best != null) {
                javaHome = best.path.absolutePath
            }
        }

        if (javaHome == null) {
            Toast.makeText(activity, "Java安装失败，请到设置中手动安装", Toast.LENGTH_LONG).show()
            return
        }

        var targetVersion = versionId
        if (targetVersion.isNullOrEmpty()) {
            val versionsDir = File(gameDir, "versions")
            if (versionsDir.exists()) {
                targetVersion = versionsDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.lastModified() }
                    ?.firstOrNull()?.name
            }
        }

        if (targetVersion.isNullOrEmpty()) {
            Toast.makeText(activity, "请先安装Minecraft版本", Toast.LENGTH_LONG).show()
            return
        }

        val intent = LaunchIntentCodec.create(activity, LaunchRequest(
            versionId = targetVersion,
            gameDirPath = gameDir.absolutePath,
            javaHome = javaHome,
            renderer = settingsRepo.getRenderer()
        ))
        activity.startActivity(intent)
    }
}
