package com.verse.explorer.launcher.fcl.launch

import java.io.File
import java.util.LinkedHashSet

/**
 * DefaultLauncher 类路径替换。
 *
 * 纯集合/文件逻辑：官方 JNA 与 LWJGL 各模块 jar 替换为本地 Android 兼容套装。
 * 本地 jar 按文件名排序后置顶，官方 `org/lwjgl/`、`jinput`、`lwjgl_util` 全部剔除。
 */
object FclClasspathPolicy {

    /**
     * 把 classpath 中 MC 自带的 jna 核心 jar 替换为本地 jna.jar。
     * 本地文件不存在时保持原样（安装未完成时不擅自删掉官方 jar）。
     */
    @JvmStatic
    fun replaceJna(classpath: MutableSet<String>, localJna: File) {
        if (!localJna.exists()) return
        val temp = LinkedHashSet<String>()
        for (entry in classpath) {
            if (entry.contains("net/java/dev/jna/jna/")) {
                temp.add(localJna.absolutePath)
            } else {
                temp.add(entry)
            }
        }
        classpath.clear()
        classpath.addAll(temp)
    }

    /**
     * 把官方 LWJGL/JInput jar 替换为本地 LWJGL-Pojav 套装并置于最前。
     * 现代模块加载器（mainClass 含 bootstrap）额外剔除 lwjglx 兼容层，
     * 避免与 merged jar 的 org.lwjgl 模块分包冲突。
     */
    @JvmStatic
    fun replaceLwjgl(
        classpath: MutableSet<String>,
        localLwjglJars: List<File>,
        modernModuleLoader: Boolean
    ) {
        if (localLwjglJars.isEmpty()) return
        val temp = LinkedHashSet<String>()
        localLwjglJars.sortedBy { it.name }.forEach { jar ->
            if (modernModuleLoader && jar.name.contains("lwjglx")) return@forEach
            temp.add(jar.absolutePath)
        }
        for (entry in classpath) {
            if (entry.contains("/org/lwjgl/") ||
                entry.contains("/jinput/") ||
                entry.contains("lwjgl_util")
            ) {
                continue
            }
            temp.add(entry)
        }
        classpath.clear()
        classpath.addAll(temp)
    }
}
