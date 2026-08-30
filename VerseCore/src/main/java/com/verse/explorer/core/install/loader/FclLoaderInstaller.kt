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
 * 加载器安装（Fabric / Forge / NeoForge / OptiFine）——全量使用 FCL（fclcore）的
 * download 模块任务：ForgeInstallTask(新/旧版分派) / NeoForgeInstallTask /
 * FabricInstallTask / OptiFineInstallTask。
 *
 * 与 FCL 的差异仅为宿主适配：
 * - 处理器由 FCL 原版 InstallTask 经 SocketServer + ProcessService(:jvm) 执行
 *   （Forge/NeoForge/OptiFine 的 processors 链路，与 FCL 一致）；
 * - 安装器下载源用 BMCLAPI（BMCLAPIDownloadProvider）；
 * - 完成后把任务 resolve 出的合并版本写回 versions/<id>/<id>.json，供 Verse 启动器读取
 *   （对应 FCL 仓库自身的版本写盘语义）。
 */
object FclLoaderInstaller {

    private const val BMCL = "https://bmclapi2.bangbang93.com"
    private const val FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader"

    suspend fun installFcl(
        gameDir: File,
        versionId: String,
        loaderType: String,
        loaderVersion: String,
        progress: (String, Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            progress("安装 $loaderType $loaderVersion...", 0.93f)

            val repo = DefaultGameRepository(gameDir)
            repo.refreshVersions()
            val base = repo.getVersion(versionId)
                ?: throw IOException("版本 $versionId 不存在")

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
                "optifine" -> {
                    // 版本串 = type + "_" + patch（如 HD_U_I7 → type=HD_U, patch=I7），
                    // 与 FCL OptiFineBMCLVersionList 的 versionlist 字段定义一致；
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
            // FCL 原版只对 TaskExecutor 调一次 start()，然后由 TaskListener 驱动进度。
            executor.test()
            executor.getException()?.let { throw it }

            // 合并（resolve）后的完整版本写回 versions/<id>/<id>.json，供 Verse 启动器读取
            // FCL 补丁语义：Fabric/OptiFine(等) 的补丁版本自身没有 inheritsFrom，
            // 直接 resolve() 只会得到补丁自己的库（缺 jopt-simple 等原版库，Knot 启动即
            // NoClassDefFoundError）。必须先把它挂在原版版本上继承，resolve 才会并入原版库。
            val patch = installTask.getResult()
            val attached = if (patch.inheritsFrom.isNullOrEmpty() && patch.id != versionId) {
                patch.setInheritsFrom(versionId)
            } else patch
            val resolved = attached.resolve(repo)
            // FCL 的 patch 版本 id 是补丁名（neoforge/fabric/forge/optifine），但 Verse 的版本
            // 仓库以版本目录名（如 "1.21.1"）为键；id 必须与目录一致，否则启动时
            // getVersion("1.21.1") 能查到目录却因 JSON id 不匹配导致 "Version 'xxx' does not exist"。
            // 同理 jar 字段：Fabric 等补丁 resolve 后 jar 会落成补丁名（"fabric"），
            // 而实际游戏 JAR 是 versions/<versionId>/<versionId>.jar，必须回写为版本目录名。
            val finalVersion =
                if (resolved.id != versionId) resolved.setId(versionId).setJar(versionId)
                else if (resolved.jar != versionId) resolved.setJar(versionId)
                else resolved
            // resolve() 得到的版本带 root/resolved=true 且把补丁内嵌进 "patches"；
            // Verse 启动时会对扁平 JSON 再次 resolve，root 版本会从"空版本+补丁"重建，
            // 结果类路径只剩补丁自己的库（fabric 启动即缺 jopt-simple 等原版库、neoforge
            // 缺游戏库直接静默退出）。已 resolve 的信息本身是完整的，这里重建一个干净的
            // 非 root / 非 resolved / 无 patches 的扁平版本再写盘，启动按原样解析。
            val fv = finalVersion
            // 类路径上去重同一 group:artifact 的库（补丁库排在前面，先到先得）：
            // Fabric 的原版库含 asm:9.6、补丁带 asm:9.10.1，若两者都上类路径，
            // Fabric Knot 的 verifyClasspath 会因重复 ASM 类直接拒绝启动；
            // jopt-simple 同理（原版 6.0-alpha-3 vs 补丁 5.0.4）。
            val seenArtifacts = java.util.HashSet<String>()
            val libraries = fv.libraries.filter { lib ->
                val groupArtifact = lib.name.substringBeforeLast(":")
                seenArtifacts.add(groupArtifact)
            }
            val flat = com.tungsten.fclcore.game.Version(
                false,
                versionId,
                fv.version,
                fv.priority,
                fv.minecraftArguments.orElse(null),
                fv.arguments.orElse(null),
                fv.mainClass,
                null,
                versionId,
                fv.assetIndex,
                null,
                fv.complianceLevel,
                fv.javaVersion,
                libraries,
                fv.compatibilityRules,
                fv.downloads,
                fv.logging,
                fv.type,
                fv.time,
                fv.releaseTime,
                fv.minimumLauncherVersion,
                null,
                false,
                null
            )
            val dir = File(File(gameDir, "versions"), versionId)
            if (!dir.exists()) dir.mkdirs()
            File(dir, "$versionId.json").writeText(JsonUtils.GSON.toJson(flat))

            progress("$loaderType 安装完成", 0.98f)
        }
    }
}