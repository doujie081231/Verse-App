package com.verse.explorer.app.install

import android.content.Context
import android.util.Log
import com.verse.explorer.core.java.JavaRuntimeManager
import java.io.File

/**
 * 初始化 FclCore 所需的宿主环境。
 *
 * 在创建下载与安装任务之前，注入 Context、运行时路径、JRE 架构和缓存仓库。
 */
object FclCoreHost {

    private const val TAG = "FclCoreHost"

    fun init(context: Context) {
        com.tungsten.fcl.FCLApp.attach(context)

        // 核心模块的整合包下载与剪贴板桥需要可用的宿主 Context。
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

        // 在建立下载连接之前初始化缓存仓库，避免任务访问未设置的单例。
        val cacheRepository = com.tungsten.fclcore.download.DefaultCacheRepository(
            File(com.tungsten.fclauncher.utils.FCLPath.CACHE_DIR).toPath()
        )
        com.tungsten.fclcore.util.CacheRepository.setInstance(cacheRepository)
    }
}