package com.verse.explorer.core.install.loader

import android.util.Log
import com.tungsten.fclcore.download.BMCLAPIDownloadProvider
import com.tungsten.fclcore.download.DefaultCacheRepository
import com.tungsten.fclcore.download.DefaultDependencyManager
import com.tungsten.fclcore.download.RemoteVersion
import com.tungsten.fclcore.download.fabric.FabricRemoteVersions
import com.tungsten.fclcore.download.forge.ForgeRemoteVersion
import com.tungsten.fclcore.download.neoforge.NeoForgeRemoteVersion
import com.tungsten.fclcore.download.optifine.OptiFineRemoteVersion
import com.tungsten.fclcore.game.DefaultGameRepository
import com.tungsten.fclcore.task.AsyncTaskExecutor
import com.tungsten.fclcore.task.TaskListener
import com.tungsten.fclcore.util.gson.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * download 模块任务：ForgeInstallTask(新/旧版分派) / NeoForgeInstallTask /
 * FabricInstallTask / OptiFineInstallTask。
 *
 * - 安装器下载源用 BMCLAPI（BMCLAPIDownloadProvider）；
 * - 完成后把安装任务产出的补丁版本原样写回 versions/<id>/<id>.json（仅 id 改实命名、
 *   inheritsFrom 指向基础原版），库/参数/游戏JAR沿继承链由启动侧 resolve 合并——
 */
object FclLoaderInstaller {

    private const val BMCL = "https://bmclapi2.bangbang93.com"
    private const val FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader"

    suspend fun installFcl(
        gameDir: File,
        versionId: String,
        loaderType: String,
        loaderVersion: String,
        baseVersionId: String = versionId,
        newVersionId: String = "$versionId-$loaderType-$loaderVersion",
        progress: (String, Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            com.verse.explorer.core.log.DownloadLog.loader(
                "开始安装加载器: $loaderType $loaderVersion (版本=$versionId)"
            )
            progress("安装 $loaderType $loaderVersion...", 0.93f)

            val repo = DefaultGameRepository(gameDir)
            repo.refreshVersions()
            val base = repo.getVersion(baseVersionId)
                ?: throw IOException("版本 $baseVersionId 不存在")

            val dm = DefaultDependencyManager(
                repo,
                BMCLAPIDownloadProvider(BMCL),
                DefaultCacheRepository(File(com.tungsten.fclauncher.utils.FCLPath.CACHE_DIR).toPath())
            )

            val remote: RemoteVersion = when (loaderType) {
                "forge" -> ForgeRemoteVersion(
                    versionId,
                    loaderVersion,
                    Instant.now(),
                    listOf("$BMCL/forge/download?mcversion=$versionId&version=$loaderVersion&branch=&category=installer&format=jar")
                )
                "neoforge" -> NeoForgeRemoteVersion(
                    versionId,
                    loaderVersion,
                    listOf("$BMCL/neoforge/version/$loaderVersion/download/installer.jar")
                )
                "fabric" -> FabricRemoteVersions.of(
                    versionId,
                    loaderVersion,
                    listOf("$FABRIC_META/$versionId/$loaderVersion")
                )
                "quilt" -> {
                    // Quilt 的远程版本类构造器是包内可见，只能经版本列表取：
                    // 列表会从 meta 拉全量组合（游戏版本 × 加载器版本）后按坐标精确命中。
                    // 拿不到说明该 Quilt 版本不适用于当前 MC 版本，直接给可读错误，
                    // 不要让用户误以为是网络问题。
                    val list = com.tungsten.fclcore.download.quilt.QuiltVersionList(dm.downloadProvider)
                    list.refreshAsync().join()
                    list.getVersion(versionId, loaderVersion).orElse(null)
                        ?: throw IOException("Quilt $loaderVersion 不适用于 MC $versionId")
                }
                "optifine" -> {
                    // 版本串 = type + "_" + patch（如 HD_U_I7 → type=HD_U, patch=I7），
                    // 拆分必须按最后一个下划线，否则拼成 /optifine/{mc}/HD/U_I7 404。
                    val type = loaderVersion.substringBeforeLast("_")
                    val patch = loaderVersion.substringAfterLast("_")
                    if (type.isBlank() || patch.isBlank() || patch == loaderVersion) {
                        throw IOException("OptiFine 版本格式错误: $loaderVersion")
                    }
                    OptiFineRemoteVersion(
                        versionId,
                        loaderVersion,
                        listOf("$BMCL/optifine/$versionId/$type/$patch"),
                        false
                    )
                }
                else -> throw IllegalArgumentException("不支持的加载器类型: $loaderType")
            }

            val installTask = remote.getInstallTask(dm, base)
            val executor = AsyncTaskExecutor(installTask)
            executor.addTaskListener(object : TaskListener() {
                override fun onRunning(task: com.tungsten.fclcore.task.Task<*>) {
                    task.name?.let { Log.i("FclLoaderInstaller", it) }
                }
            })
            // 关键：本 fork 的 AsyncTaskExecutor.test() 内部会无条件 start()（重跑整个任务图），
            // 绝不能像旧实现那样 start() 后轮询 test()——那会让每条安装链路被并发重跑 N 次，
            // 处理器重复执行、安装器并发下载互相截断为 0 字节（zip END header not found）。
            executor.test()
            executor.getException()?.let { throw it }

            // 仅把 id 改为实命名（命名弹窗结果）、inheritsFrom 指向基础原版版本目录
            // （如官方 Forge/NeoForge 安装器内嵌 JSON 已带 inheritsFrom 则原样保留，
            //
            // 不再在此预合并库列表/参数/游戏JAR：全部沿继承链由启动侧 resolve() 合并
            // { id, inheritsFrom: <原版>, libraries: [仅加载器库], arguments, mainClass, ... }
            //
            // 注意：安装任务返回的补丁 id 是 patch id（如 "forge"/"fabric-loader"），
            // 写盘时必须替换为 newId，否则版本列表会出现 patch 名的孤立条目。
            val patch = installTask.getResult()
                ?: throw IOException("安装器未返回补丁版本")
            val newId = newVersionId.ifBlank { "$versionId-$loaderType-$loaderVersion" }
            val written = if (patch.inheritsFrom.isNullOrEmpty() && patch.id != baseVersionId) {
                patch.setInheritsFrom(baseVersionId)
            } else {
                patch
            }.setId(newId)
            val dir = File(File(gameDir, "versions"), newId)
            if (!dir.exists()) dir.mkdirs()
            File(dir, "$newId.json").writeText(JsonUtils.GSON.toJson(written))
            // Forge 安装器已把所有加载器库放进 libraries/ 公共目录，无需复制 JAR；
            // 游戏 JAR 沿 inheritsFrom 继承链由启动器 resolve 后定位。
            Log.i("FclLoaderInstaller", "加载器版本已写入(继承式): $newId (inheritsFrom=${written.inheritsFrom ?: "无"}, libraries=${(written.libraries ?: emptyList()).size})")
            com.verse.explorer.core.log.DownloadLog.loader("加载器安装完成: $newId")

            progress("$loaderType 安装完成", 0.98f)
        }.onFailure { e ->
            com.verse.explorer.core.log.DownloadLog.loader(
                "加载器安装失败: $loaderType $loaderVersion — ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }
}