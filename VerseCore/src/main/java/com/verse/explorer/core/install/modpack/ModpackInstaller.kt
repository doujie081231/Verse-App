package com.verse.explorer.core.install.modpack

import android.util.Log
import com.tungsten.fclcore.download.BMCLAPIDownloadProvider
import com.tungsten.fclcore.download.DefaultCacheRepository
import com.tungsten.fclcore.download.DefaultDependencyManager
import com.tungsten.fclcore.game.DefaultGameRepository
import com.tungsten.fclcore.mod.Modpack
import com.tungsten.fclcore.mod.ModpackProvider
import com.tungsten.fclcore.mod.modrinth.ModrinthInstallTask
import com.tungsten.fclcore.mod.modrinth.ModrinthManifest
import com.tungsten.fclcore.mod.modrinth.ModrinthModpackProvider
import com.tungsten.fclcore.task.AsyncTaskExecutor
import com.tungsten.fclcore.task.TaskListener
import com.tungsten.fclcore.util.io.CompressingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

/**
 * 整合包安装——全量使用 FCL（fclcore）原版链路，不自研：
 * - 读取 mrpack manifest：尝试各 ModpackProvider（Modrinth 优先）
 * - 安装：Modpack.getInstallTask() → ModrinthInstallTask
 *   （内部 = 安装游戏本体 `dependencyManager.gameBuilder()`
 *     + ModpackInstallTask 解压 overrides 到游戏目录
 *     + MinecraftInstanceTask 生成 modpack 配置
 *     + ModrinthCompletionTask 解析依赖并下载 mods）
 *
 * 与 FCL 的差异仅为宿主适配：安装器下载源用 BMCLAPI；版本名由调用方指定
 * （默认取 manifest 的 name）。
 */
object ModpackInstaller {

    private const val BMCL = "https://bmclapi2.bangbang93.com"

    /**
     * 尝试用各 ModpackProvider 识别整合包文件，返回 FCL Modpack 对象。
     * 不支持的格式（手动整合包等）抛 UnsupportedModpackException。
     */
    fun readModpack(zipFile: File, charset: Charset = Charsets.UTF_8): Modpack {
        // 顺序与 FCL ModpackHelper.readModpackManifest 一致
        val providers: List<ModpackProvider> = listOf(
            com.tungsten.fclcore.mod.mcbbs.McbbsModpackProvider.INSTANCE,
            com.tungsten.fclcore.mod.curse.CurseModpackProvider.INSTANCE,
            ModrinthModpackProvider.INSTANCE,
            com.tungsten.fclcore.mod.multimc.MultiMCModpackProvider.INSTANCE,
            com.tungsten.fclcore.mod.server.ServerModpackProvider.INSTANCE
        )
        try {
            ZipFile(zipFile).use { zip ->
                for (provider in providers) {
                    try {
                        val pack = provider.readManifest(zip, zipFile.toPath(), charset)
                        Log.i(TAG, "整合包格式识别: ${provider.getName()} (${pack.name} ${pack.version})")
                        return pack
                    } catch (_: Exception) {
                        // 尝试下一个 provider
                    }
                }
            }
        } catch (_: Exception) {
        }
        throw IOException("无法识别的整合包格式: ${zipFile.name}")
    }

    /**
     * 安装整合包到游戏目录。
     *
     * @param gameDir  游戏根目录（versions/libraries 所在）
     * @param zipFile  mrpack/zip 整合包文件
     * @param name     生成的游戏版本名（默认用 manifest name）
     * @param progress 进度回调（阶段消息 + 0~1 进度）
     */
    suspend fun installModpack(
        gameDir: File,
        zipFile: File,
        name: String? = null,
        progress: (String, Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            progress("解析整合包...", 0.05f)
            val modpack = readModpack(zipFile)

            val runName = name ?: modpack.name
            val repo = DefaultGameRepository(gameDir)
            repo.refreshVersions()

            // Modrinth 安装任务需要 loader/mc 版本信息
            if (modpack.manifest !is ModrinthManifest) {
                throw IOException("当前仅支持 Modrinth 整合包安装，实际格式: ${modpack.manifest.provider.name}")
            }

            val dm = DefaultDependencyManager(
                repo,
                BMCLAPIDownloadProvider(BMCL),
                DefaultCacheRepository(File(com.tungsten.fclauncher.utils.FCLPath.CACHE_DIR).toPath())
            )

            progress("开始安装整合包 $runName...", 0.1f)

            // FCL 原版安装任务：modpack.getInstallTask(dm, zipFile, name)
            // 对 Modrinth 即 new ModrinthInstallTask(dm, zipFile, modpack, manifest, name)
            val installTask = modpack.getInstallTask(dm, zipFile, runName)

            val executor = AsyncTaskExecutor(installTask)
            executor.addTaskListener(object : TaskListener() {
                override fun onRunning(task: com.tungsten.fclcore.task.Task<*>) {
                    task.name?.let { Log.i(TAG, it) }
                }
            })
            // 本 fork 的 AsyncTaskExecutor.test() 内部会无条件 start() 重跑整个任务图，
            // 只能对任务调用一次（与 FclLoaderInstaller 一致）。
            executor.test()
            executor.getException()?.let { throw it }

            repo.refreshVersions()

            if (!repo.hasVersion(runName)) {
                throw IOException("整合包安装结束但未生成版本 $runName")
            }
            Log.i(TAG, "整合包安装完成: $runName")
            progress("整合包安装完成", 1f)
            // 安装期间下载的整合包文件清理由调用方决定
        }
    }

    private const val TAG = "ModpackInstaller"
}