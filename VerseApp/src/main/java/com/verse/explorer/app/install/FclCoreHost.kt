package com.verse.explorer.app.install

import android.content.Context
import android.util.Log
import com.verse.explorer.core.java.JavaRuntimeManager
import java.io.File

/**
 * fclcore（FCL download 模块）宿主接入：
 * - FCLApp / FCLPath 统一在这里初始化（VerseApplication.onCreate 调用）；
 * - 处理器执行完全走 FCL 原版链路：Forge/NeoForge/OptiFine 的 install_profile
 *   processor 由任务内的 SocketServer + ProcessService(:jvm) +
 *   FCLauncher.launchAPIInstaller 执行，退出码经 UDP 回传 —— 不再使用自研网关；
 * - JRE 目录布局（files/jre/jre-<ver>-<arch>）与 FCL 原版（runtime/java/jre<ver>）
 *   不同，因此仅注入路径/架构常量供 ProcessService 定位 JRE。
 */
object FclCoreHost {

    private const val TAG = "FclCoreHost"

    fun init(context: Context) {
        com.tungsten.fcl.FCLApp.attach(context)

        // fclcore 静态初始化（如 CurseForgeRemoteModRepository.<clinit> 读 apiKey）和
        // FCLBridge 剪贴板等直接取 FCLPath.CONTEXT；setPaths 不写 CONTEXT，需在此补齐，
        // 否则整合包下载/游戏内剪贴板路径会拿到 null Context 崩溃。
        com.tungsten.fclauncher.utils.FCLPath.CONTEXT = context

        val filesDir = context.filesDir.absolutePath
        com.tungsten.fclauncher.utils.FCLPath.setPaths(
            context.cacheDir.absolutePath,
            File(filesDir, "jre").absolutePath,
            File(filesDir, "runtime").absolutePath,
            filesDir,
            context.applicationInfo.nativeLibraryDir
        )
        // JRE 目录命名后缀（aarch64/x86_64），供 FCL 原版 ProcessService 拼 jre-<ver>-<arch>
        com.tungsten.fclauncher.utils.FCLPath.JRE_ARCH = JavaRuntimeManager.getArchName()

        // 与 FCL 原版 Settings.init() 对应：初始化全局下载缓存仓库单例。
        // FCL 原版此处为 CacheRepository.setInstance(FCLCacheRepository.REPOSITORY) +
        // setDirectory(FCLPath.CACHE_DIR)，否则默认单例的 index 为 null，
        // 下载任务（如 VersionJsonDownloadTask 的 GetTask）在 injectConnection 时 NPE。
        val cacheRepository = com.tungsten.fclcore.download.DefaultCacheRepository(
            File(com.tungsten.fclauncher.utils.FCLPath.CACHE_DIR).toPath()
        )
        com.tungsten.fclcore.util.CacheRepository.setInstance(cacheRepository)
    }
}