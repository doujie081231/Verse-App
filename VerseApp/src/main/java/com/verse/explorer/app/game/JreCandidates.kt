package com.verse.explorer.app.game

import java.io.File

/**
 * JRE 候选路径规划：决定本次启动按什么顺序尝试运行时目录。
 *
 * 顺序契约（改动会影响启动成功率，勿随意调整）：
 * 1. 参数显式指定的 `javaHome`（用户/版本设置优先）；
 * 2. 按所需大版本找到的已安装运行时（与 1 相同则去重）；
 * 3. 安装器自己的目标目录 `files/jre/jre-<大版本>-<架构>`（永远兜底）。
 *
 * 兜底必须是安装器目标目录：它由版本清单推导，重装后写回的就是这里。
 * 纯函数，不碰 Android 与文件系统，便于单测。
 */
internal object JreCandidates {

    fun plan(
        explicitHome: String?,
        installedForVersion: String?,
        installTarget: String
    ): List<String> {
        val result = mutableListOf<String>()
        if (!explicitHome.isNullOrEmpty()) result.add(explicitHome)
        if (!installedForVersion.isNullOrEmpty() && installedForVersion != explicitHome) {
            result.add(installedForVersion)
        }
        result.add(installTarget)
        return result.distinct()
    }

    /** 安装器目标目录：`<jreRoot>/jre-<大版本>-<架构>` */
    fun installTargetOf(jreRoot: File, javaVersion: Int, archName: String): File =
        File(jreRoot, "jre-$javaVersion-$archName")
}
