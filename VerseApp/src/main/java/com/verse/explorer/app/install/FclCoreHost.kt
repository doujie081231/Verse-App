package com.verse.explorer.app.install

import android.content.Context
import android.util.Log
import com.verse.explorer.core.java.JavaRuntimeManager
import java.io.File

/**
 *   processor 由任务内的 SocketServer + ProcessService(:jvm) +
 *   不同，因此仅注入路径/架构常量供 ProcessService 定位 JRE。
 */
object FclCoreHost {

    private const val TAG = "FclCoreHost"

    fun init(context: Context) {
        com.tungsten.fcl.FCLApp.attach(context)

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
        com.tungsten.fclauncher.utils.FCLPath.JRE_ARCH = JavaRuntimeManager.getArchName()

        // 下载任务（如 VersionJsonDownloadTask 的 GetTask）在 injectConnection 时 NPE。
        val cacheRepository = com.tungsten.fclcore.download.DefaultCacheRepository(
            File(com.tungsten.fclauncher.utils.FCLPath.CACHE_DIR).toPath()
        )
        com.tungsten.fclcore.util.CacheRepository.setInstance(cacheRepository)
    }
}