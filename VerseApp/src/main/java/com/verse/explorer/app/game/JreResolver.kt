package com.verse.explorer.app.game

import android.content.Context
import com.verse.explorer.app.domain.model.launch.GameLaunchParams
import com.verse.explorer.app.ui.VerseLogger
import com.verse.explorer.core.java.JavaRuntimeManager
import java.io.File

/**
 * Java 运行时（JRE）解析器。
 *
 * 职责：为本次启动挑选一个完整可用的 JRE 目录——按所需大版本在候选
 * （参数指定 / 已安装运行时 / 安装器目标目录）中校验，全部不可用时触发
 * 内置运行时重装并复验；最后修复执行权限与 libawt_xawt.so 桩库。
 * 与 GameLaunchOrchestrator 共用同一个日志 TAG，保持 logcat 过滤契约不变。
 */
class JreResolver(
    private val activity: Context,
    private val javaDetector: JavaRuntimeDetector
) {

    companion object {
        private const val TAG = "GameLaunchOrchestrator"
    }

    suspend fun resolveJavaHome(params: GameLaunchParams, gameDir: File): String {
        // 本次启动真正需要的 Java 大版本：参数未指定时按版本清单推断
        val requiredVersion = if (params.javaVersion > 0) params.javaVersion
            else javaDetector.getRequiredJavaVersion(params.versionId, gameDir)

        // 安装器自己的目标目录：files/jre/jre-<大版本>-<架构>
        val installTarget = File(
            JavaRuntimeManager.getJreDir(activity),
            "jre-$requiredVersion-${JavaRuntimeManager.getArchName()}"
        )

        val candidates = mutableListOf<String>()
        if (!params.javaHome.isNullOrEmpty()) candidates.add(params.javaHome)
        // 兜底运行时必须与游戏所需的大版本一致：findJavaHome() 返回的是「最高版本」，
        // 拿 Java 25 启动 1.12 会因 -Xbootclasspath/p 不再支持而直接退出
        findRuntimeForVersion(requiredVersion)?.let {
            if (it != params.javaHome) candidates.add(it)
        }
        candidates.add(installTarget.absolutePath)

        for (home in candidates) {
            val reason = jreInvalidReason(home)
            if (reason == null) return executableJre(home)
            VerseLogger.warn("JRE $home 不可用（$reason）", TAG)
        }

        // 没有可用运行时：交给安装器解包内置运行时。
        // 不删除候选目录：别的版本/别处安装的运行时可能仍在使用，安装器只清空自己的目标目录。
        val javaKey = "java$requiredVersion"
        try {
            JavaRuntimeManager.installJre(activity, javaKey) { progress ->
                VerseLogger.info("JRE 重装进度: ${progress.message}", TAG)
            }
        } catch (e: Exception) {
            VerseLogger.warn("JRE 重装失败: ${e.message}", TAG)
        }

        val reasonAfter = jreInvalidReason(installTarget.absolutePath)
        if (reasonAfter == null) {
            VerseLogger.info("JRE 重装后验证通过: ${installTarget.absolutePath}", TAG)
            return executableJre(installTarget.absolutePath)
        }
        // 安装器可能保留了旧目录或写到了其它位置，最后再按大版本扫一遍
        findRuntimeForVersion(requiredVersion)?.let { newHome ->
            if (jreInvalidReason(newHome) == null) {
                VerseLogger.info("JRE 重装后验证通过: $newHome", TAG)
                return executableJre(newHome)
            }
        }

        throw RuntimeException("未找到完整的 Java $requiredVersion 运行时（需包含 bin/java、lib/libjli.so、lib/libjvm.so、lib/libawt.so），请在设置中配置 JRE 路径")
    }

    /**
     * 校验 JRE 目录能否用于启动：返回 null 表示可用，否则返回缺失项。
     * 纯文件系统判定，逻辑在 JreValidator（可单测）。
     */
    private fun jreInvalidReason(home: String): String? = JreValidator.jreInvalidReason(home)

    private fun executableJre(home: String): String {
        ensureJreExecutable(File(home))
        return File(home).absolutePath
    }

    /**
     * 按所需 Java 大版本挑选已安装运行时。
     * 优先取 `jre-<版本>-<架构>` 目录（安装器产出），其余同版本运行时作为备选。
     */
    private fun findRuntimeForVersion(requiredVersion: Int): String? = runCatching {
        JavaRuntimeManager.getInstalledRuntimes(activity)
            .filter { it.version == requiredVersion }
            .sortedByDescending { it.path.name.startsWith("jre-$requiredVersion-") }
            .firstOrNull { javaDetector.isValidJreDir(it.path) }
            ?.path?.absolutePath
    }.getOrNull()

    /**
     * JRE8 核心库可用性：lib 目录下的 rt.jar 能读出 java/lang/Object.class 才算完好。
     * 局部损坏（中央目录正常、条目数据损坏）无法靠文件大小判断，用条目可读性兜底。
     */
    private fun isRtJarUsable(libDir: File): Boolean = JreValidator.isRtJarUsable(libDir)

    private fun findFileInTree(dir: File, name: String): File? = JreValidator.findFileInTree(dir, name)

    private fun ensureJreExecutable(jreHome: File) {
        try {
            val binDir = File(jreHome, "bin")
            if (binDir.isDirectory) {
                binDir.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        f.setExecutable(true, false)
                        f.setReadable(true, false)
                    }
                }
            }
            val libDir = File(jreHome, "lib")
            if (libDir.isDirectory) {
                libDir.walkTopDown().forEach { f ->
                    if (f.isFile && f.name.endsWith(".so")) {
                        f.setExecutable(true, false)
                        f.setReadable(true, false)
                    }
                }
            }

            // JRE 自带的 libawt_xawt.so 依赖 X11 无法在 Android 加载，必须替换为我们编译的桩库
            // 即使文件已存在也要覆盖（可能是 JRE 自带版本或之前损坏的版本）
            val libawtFile = File(libDir, "libawt_xawt.so")
            libawtFile.delete()
            JavaRuntimeManager.extractLibFromApk(activity, "libawt_xawt.so", libawtFile)

            // 某些 JRE 将原生库存放在 lib/<arch>/ 子目录中，同步覆盖以覆盖所有加载路径
            val archSubdir = JavaRuntimeManager.detectArchSubdir(libDir)
            if (archSubdir != null) {
                val archAwtFile = File(libDir, "$archSubdir/libawt_xawt.so")
                archAwtFile.delete()
                JavaRuntimeManager.extractLibFromApk(activity, "libawt_xawt.so", archAwtFile)
            }

            VerseLogger.info("JRE 权限已修复: ${jreHome.absolutePath}", TAG)
        } catch (e: Exception) {
            VerseLogger.warn("JRE 权限修复失败: ${e.message}", TAG)
        }
    }
}
