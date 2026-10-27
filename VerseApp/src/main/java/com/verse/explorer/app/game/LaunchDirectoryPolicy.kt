package com.verse.explorer.app.game

import java.io.File

/**
 * 启动目录策略：版本隔离、运行目录、mods 目录。
 *
 * 启动参数与模组预检必须使用同一套判定，避免两处各算一遍导致目录不一致。
 */
internal object LaunchDirectoryPolicy {

    /** 原语义：开启全局目录或版本隔离任一条件都使用版本目录 */
    fun isIsolated(usesGlobal: Boolean, isolateGameDir: Boolean): Boolean =
        usesGlobal || isolateGameDir

    /** 隔离时 `游戏目录/versions/<版本 id>`，否则游戏根目录 */
    fun runDir(gameDir: File, versionId: String, isolated: Boolean): File =
        if (isolated) File(File(gameDir, "versions"), versionId) else gameDir

    /** 隔离时版本目录下的 mods，否则游戏根目录下的 mods */
    fun modsDir(gameDir: File, versionId: String, isolated: Boolean): File =
        if (isolated) File(runDir(gameDir, versionId, true), "mods") else File(gameDir, "mods")
}
