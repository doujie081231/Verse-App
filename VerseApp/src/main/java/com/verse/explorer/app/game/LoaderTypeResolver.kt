package com.verse.explorer.app.game

import com.google.gson.JsonObject

/**
 * 从版本 JSON 里判断「基础 MC 版本」与「当前已装加载器」。
 *
 * 版本设置页拿到的 versionId 可能是加载器版本（如 `1.20.1-forge-47.2.0`），
 * 也可能就是原版 id。给已有实例加加载器时必须以**基础版本**为父，
 * 否则会拼出 `1.20.1-forge-47.2.0-forge-47.3.0` 这种层层套娃的版本。
 *
 * 判断优先级：`inheritsFrom` → `mainClass` → 版本 id 后缀。
 * 纯函数，不碰文件系统，便于单测。
 */
internal object LoaderTypeResolver {

    /** 加载器标识（与 FclLoaderInstaller 的 loaderType 参数保持一致） */
    const val FORGE = "forge"
    const val NEOFORGE = "neoforge"
    const val FABRIC = "fabric"
    const val QUILT = "quilt"
    const val OPTIFINE = "optifine"

    /** 能被自动安装的加载器（Cleanroom 等其余类型只做识别，安装时不给选项） */
    val INSTALLABLE = setOf(FORGE, NEOFORGE, FABRIC, QUILT, OPTIFINE)

    data class Info(
        /** 用于安装加载器时的父版本 */
        val baseVersionId: String,
        /** 当前已装的加载器；null 表示原版 */
        val loaderType: String?
    )

    /**
     * @param versionJson 该版本的 `<id>.json` 内容；读不到时传 null
     * @param versionId 版本目录名
     */
    fun resolve(versionJson: JsonObject?, versionId: String): Info {
        val inheritsFrom = versionJson?.get("inheritsFrom")?.takeIf { !it.isJsonNull }?.asString
        val mainClass = versionJson?.get("mainClass")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

        val loader = detectLoader(mainClass, versionId)
        // 有 inheritsFrom 就一定是「加载器版本」，父版本即基础版本；
        // 没有则当前 id 本身就是基础版本（原版或手动拼的整合版本）
        val base = inheritsFrom?.takeIf { it.isNotBlank() } ?: versionId
        return Info(baseVersionId = base, loaderType = loader)
    }

    fun detectLoader(mainClass: String, versionId: String): String? {
        val mc = mainClass.lowercase()
        val id = versionId.lowercase()
        // neoforge 必须排在 forge 之前：`neoforge` 的主类名里同样含 "forge"
        return when {
            mc.contains("neoforge") || id.contains("neoforge") -> NEOFORGE
            mc.contains("fabric") || id.contains("fabric") -> FABRIC
            mc.contains("quilt") || id.contains("quilt") -> QUILT
            mc.contains("forge") || id.contains("forge") -> FORGE
            mc.contains("optifine") || id.contains("optifine") -> OPTIFINE
            else -> null
        }
    }

    /** 安装后的新版本 id（与 FclLoaderInstaller 的默认命名保持一致） */
    fun targetVersionId(baseVersionId: String, loaderType: String, loaderVersion: String): String =
        "$baseVersionId-$loaderType-$loaderVersion"

    fun displayName(loaderType: String?): String = when (loaderType) {
        FORGE -> "Forge"
        NEOFORGE -> "NeoForge"
        FABRIC -> "Fabric"
        QUILT -> "Quilt"
        OPTIFINE -> "OptiFine"
        else -> "原版"
    }
}
