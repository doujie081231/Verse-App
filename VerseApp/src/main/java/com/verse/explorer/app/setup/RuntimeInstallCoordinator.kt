package com.verse.explorer.app.setup

import android.content.Context
import com.verse.explorer.core.java.JavaRuntimeManager
import kotlinx.coroutines.*
import java.io.File

/** 不持有页面。页面停止等待不会取消其他页面正在使用的运行时安装。 */
class RuntimeInstallCoordinator(context: Context, private val scope: CoroutineScope) {
    private val application = context.applicationContext
    private val running = mutableMapOf<String, Deferred<Result<File>>>()

    @Synchronized fun install(version: String): Deferred<Result<File>> {
        running[version]?.takeIf { !it.isCompleted }?.let { return it }
        val task = scope.async(start = CoroutineStart.LAZY) { JavaRuntimeManager.installJre(application, version) {} }
        running[version] = task
        task.invokeOnCompletion { synchronized(this) { if (running[version] === task) running.remove(version) } }
        task.start()
        return task
    }
}
