package com.verse.explorer.core.install

import java.io.File

/** 在任务入口捕获。任务运行中不再跟随设置页切换目录或下载源。 */
data class InstallationSnapshot(val paths: InstallationPaths, val downloadSource: String)

data class InstallationPaths(val root: File) {
    val versions = File(root, "versions")
    val libraries = File(root, "libraries")
    val assets = File(root, "assets")
    val natives = File(root, "natives")

    fun ensureDirectories() = listOf(versions, libraries, assets, natives).forEach { it.mkdirs() }

    fun version(id: String): File {
        require(id.isNotBlank() && id != "." && id != ".." && '/' !in id && '\\' !in id) { "无效版本目录名" }
        return File(versions, id)
    }
}

data class InstallationPlan(val installId: String, val baseId: String) {
    companion object {
        fun create(versionId: String, installName: String, loaderType: String?, loaderVersion: String?): InstallationPlan {
            val installId = installName.ifBlank { versionId }
            val baseId = if (loaderType.isNullOrBlank() || loaderVersion.isNullOrBlank()) installId else versionId
            return InstallationPlan(installId, baseId)
        }
    }
}

/** 只清理由当前任务创建的版本目录；共享 libraries/assets 及既有版本不在清理集合内。 */
class InstallationCleanup(paths: InstallationPaths, plan: InstallationPlan) {
    private val createdCandidates = setOf(paths.version(plan.installId), paths.version(plan.baseId)).filterNot { it.exists() }

    fun afterFailure() { createdCandidates.forEach { it.deleteRecursively() } }
}
