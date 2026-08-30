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

            // FCL 语义（版本隔离）：加载器作为独立子版本写入自己的版本目录，不覆盖原版。
            // 版本 id "<mc>-<loader>-<loaderVer>"（如 1.20.1-forge-47.0.1），
            // 与 InstalledVersionsScreen.loaderBadge / isModdedVersion 的 id 判定一致。
            //
            // 库列表必须用 resolve 后的完整库（原版库 + 补丁库去重）：
            // Verse 的 ClasspathBuilder.buildFclClasspath 只读 config.version.libraries，
            // 不会递归合并 inheritsFrom 父版本库；仅写补丁库会导致启动类路径缺原版库
            // （fabric 缺 jopt-simple / neoforge 静默退出）。inheritsFrom 与 jar 仍指向
            // 原版，供资产索引、启动参数、游戏 JAR 沿继承链解析。
            val patch = installTask.getResult()
                ?: throw IOException("安装器未返回补丁版本")
            val attached = if (patch.inheritsFrom.isNullOrEmpty() && patch.id != versionId) {
                patch.setInheritsFrom(versionId)
            } else patch
            val resolved = attached.resolve(repo)
            // 类路径上去重同一 group:artifact 的库（补丁库先到先得）：
            // 原版 asm 9.6 + 补丁 asm 9.10.1 同存会触发 Fabric verifyClasspath 拒绝启动。
            // 注意去重键必须保留 classifier（如 forge-universal 与 forge-client 是
            // group:artifact:version:classifier 四段，去掉 version 后 key 分别为
            // net.minecraftforge:forge:universal / :client，两者都必须保留，
            // minecraft 客户端类只存在于 client jar）；不能用 substringBeforeLast(":")，
            // 否则 client 与 universal 共享同一 key 会被误删。
            val seenArtifacts = java.util.HashSet<String>()
            val libraries = resolved.libraries.filter { lib ->
                val name = lib.name
                val parts = name.split(":")
                val key = when {
                    parts.size >= 4 -> "${parts[0]}:${parts[1]}:${parts[3]}"
                    parts.size == 3 -> "${parts[0]}:${parts[1]}"
                    parts.size == 2 -> "${parts[0]}:${parts[1]}"
                    else -> name
                }
                // 原版带 log4j-slf4j-impl（module: org.apache.logging.log4j.slf4j），
                // 补丁/NeoForge 带 log4j-slf4j2-impl（module: org.apache.logging.log4j.slf4j2.impl）。
                // 两者都含 module-info 且都向 ALL-MODULE-PATH 导出 org.apache.logging.slf4j，
                // 同存会触发 java.lang.module.ResolutionException（模块导出冲突）。
                // 保留补丁侧 log4j-slf4j2-impl，剔除原版 log4j-slf4j-impl。
                if (key.endsWith(":log4j-slf4j-impl")) {
                    return@filter false
                }
                seenArtifacts.add(key)
            }
            val newId = "$versionId-$loaderType-$loaderVersion"
            // arguments：
            //  - jvm 部分必须用 resolve 后的完整参数（含 -p module path、--add-opens、
            //    ${library_directory} 等，ForgeBootstrap 靠它们发现 net/minecraft/client
            //    Minecraft.class；只写 patch 的 jvm 会因缺 module path 报
            //    "Could not find net/minecraft/client/Minecraft.class"）。
            //  - game 部分用加载器自身参数（ForgeBootstrap 会把版本 JSON 的
            //    arguments.game 追加到 argv；写 resolved 的完整 game 会把
            //    --username/--gameDir/--width 等通用参数重复传入，
            //    ModLauncher 抛 MultipleArgumentsForOptionException）。
            val patchArgs = patch.arguments.orElse(null)
            val resolvedArgs = resolved.arguments.orElse(null)
            val mergedArgs = if (resolvedArgs != null && patchArgs != null) {
                resolvedArgs.withGame(patchArgs.getGame())
            } else {
                patchArgs ?: resolvedArgs
            }
            val flat = com.tungsten.fclcore.game.Version(
                false,
                newId,
                resolved.version,
                resolved.priority,
                patch.minecraftArguments.orElse(null),
                mergedArgs,
                resolved.mainClass,
                versionId,   // inheritsFrom=原版（资产/参数/游戏JAR沿继承链解析）
                versionId,   // jar=原版，找不到本版本 JAR 时沿继承回落
                resolved.assetIndex,
                null, // assets 由 inheritsFrom 沿继承链解析
                resolved.complianceLevel,
                resolved.javaVersion,
                libraries,
                resolved.compatibilityRules,
                resolved.downloads,
                resolved.logging,
                resolved.type,
                resolved.time,
                resolved.releaseTime,
                resolved.minimumLauncherVersion,
                null,
                false,
                null
            )
            val dir = File(File(gameDir, "versions"), newId)
            if (!dir.exists()) dir.mkdirs()
            File(dir, "$newId.json").writeText(JsonUtils.GSON.toJson(flat))
            // Forge 安装器已把所有加载器库放进 libraries/ 公共目录，无需复制 JAR；
            // 游戏 JAR 经 inheritsFrom/jar 字段沿继承链由启动器定位。
            Log.i("FclLoaderInstaller", "加载器版本已写入: $newId (inheritsFrom=$versionId, libraries=${libraries.size})")

            progress("$loaderType 安装完成", 0.98f)
        }
    }
}