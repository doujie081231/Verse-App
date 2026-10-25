package com.verse.explorer.app.install

import android.content.Context
import android.widget.Toast
import com.verse.explorer.app.download.DownloadCenter
import com.verse.explorer.core.install.InstallationSnapshot
import com.verse.explorer.core.resource.ResourceRepository
import com.verse.explorer.core.resource.ResourceVersion
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.util.FileUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class ResourceInstallStatus(val done: Boolean = false, val text: String = "", val error: String? = null)

/** 应用级资源安装动作；捕获目标和配置后再提交任务，不保留页面回调。 */
class ResourceInstallActions(context: Context, private val scope: CoroutineScope, private val snapshot: () -> InstallationSnapshot) {
    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(ResourceInstallStatus())
    val state = mutableState.asStateFlow()
    private var downloadDone: Boolean
        get() = mutableState.value.done
        set(value) { mutableState.update { it.copy(done = value) } }
    private var downloadText: String
        get() = mutableState.value.text
        set(value) { mutableState.update { it.copy(text = value) } }
    private var downloadError: String?
        get() = mutableState.value.error
        set(value) { mutableState.update { it.copy(error = value) } }

    /** 判断目标版本的 folder 目录下是否已有同名（且同 SHA1，若已知）文件 */
    fun isFileInstalledInTargets(
        versionsDir: File,
        targetVersions: List<MinecraftVersion>,
        folder: String,
        fileName: String,
        sha1: String
    ): Boolean = targetVersions.any { tv ->
        val f = File(File(versionsDir, tv.id), "$folder/$fileName")
        if (!f.isFile || f.length() == 0L) false
        else if (sha1.isBlank()) true
        else runCatching { FileUtils.calculateSha1(f).equals(sha1, ignoreCase = true) }.getOrDefault(false)
    }

    /**
     * 将全部必需前置模组安装到选中的版本（隔离安装到各版本的 mods 文件夹）：
     * 依赖逐项解析后去重（项目 id + 本地文件名 + 本地已装文件 SHA1），再逐项
     * 提交到下载中心并发下载（各自独立进度），不再串行阻塞。
     */
    fun startDepsForVersions(projectId: String, versions: List<ResourceVersion>, selectedVersion: ResourceVersion?, targetVersions: List<MinecraftVersion>) {
        if (targetVersions.isEmpty()) return
        val versionIds = targetVersions.map { it.id }
        val appContext = context.applicationContext
        val config = snapshot()
        val repository = ResourceRepository(context, snapshot = config)
        scope.launch(Dispatchers.Main) {
            val deps = versions.flatMap { it.dependencies }
                .distinctBy { it.projectId }
                .filter { it.projectId.isNotBlank() && it.type == "required" }
            if (deps.isEmpty()) {
                Toast.makeText(context, "没有需要安装的前置模组", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // 解析各依赖的最新版本文件（按依赖自身来源解析），并做去重/已装跳过
            // gameVersions 与宿主版本有交集才兼容；loaders 能精确匹配的候选优先
            val rootVersion = selectedVersion
            fun pickCompatible(list: List<ResourceVersion>): ResourceVersion? {
                if (list.isEmpty()) return null
                val rootGV = rootVersion?.gameVersions.orEmpty()
                if (rootGV.isEmpty()) return list.firstOrNull()
                val intersected = list.filter { v -> v.gameVersions.any { it in rootGV } }
                if (intersected.isEmpty()) return list.firstOrNull()
                val rootLoaders = rootVersion?.loaders.orEmpty()
                if (rootLoaders.isEmpty()) return intersected.firstOrNull()
                return intersected.firstOrNull { v -> v.loaders.any { it in rootLoaders } } ?: intersected.firstOrNull()
            }
            val planned = mutableListOf<Pair<String, ResourceVersion>>() // (队列 key, 版本)
            val seenFileKeys = mutableSetOf<String>()
            var skippedInstalled = 0
            var resolveFailed = 0
            val versionsDir = config.paths.versions
            for (d in deps) {
                val ver = runCatching { pickCompatible(repository.getVersions(d.projectId, d.source)) }.getOrNull()
                val primary = ver?.primaryFile ?: ver?.files?.firstOrNull()
                if (ver == null || primary == null || primary.url.isBlank()) {
                    resolveFailed++
                    continue
                }
                val safeName = (primary.filename.ifBlank { "${ver.id}.jar" })
                    .replace(Regex("""[^a-zA-Z0-9._\-]"""), "_")
                // 批次内去重：同一文件（名+SHA1）只入队一次
                val fileKey = "$safeName:${primary.sha1}"
                if (!seenFileKeys.add(fileKey)) continue
                // 本地已装去重：目标版本的 mods 中已有同名同 SHA1 文件时跳过
                if (isFileInstalledInTargets(versionsDir, targetVersions, "mods", safeName, primary.sha1)) {
                    skippedInstalled++
                    continue
                }
                planned.add("dep:$projectId:${d.projectId}" to ver)
            }

            val queued = AtomicInteger(planned.size)
            val ok = AtomicInteger(0)
            val fail = AtomicInteger(0)
            if (planned.isEmpty()) {
                val msg = buildString {
                    if (skippedInstalled > 0) append("前置模组均已安装")
                    else if (resolveFailed > 0) append("前置模组解析失败")
                    else append("没有需要安装的前置模组")
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                return@launch
            }
            downloadDone = false
            downloadError = null
            downloadText = "准备安装前置模组..."
            planned.forEach { (key, ver) ->
                if (DownloadCenter.findByKey(key) != null) {
                    // 已在队列中，视为完成计数，避免汇总回调永不触发
                    ok.incrementAndGet()
                    return@forEach
                }
                val primary = ver.primaryFile ?: ver.files.firstOrNull()
                DownloadCenter.submit(
                    key = key,
                    title = primary?.filename ?: ver.versionNumber,
                    onComplete = { success, _ ->
                        scope.launch(Dispatchers.Main) {
                            if (success) ok.incrementAndGet() else fail.incrementAndGet()
                            if (ok.get() + fail.get() >= queued.get()) {
                                if (fail.get() == 0) {
                                    downloadDone = true
                                    downloadText = "前置模组已安装到 ${versionIds.size} 个版本"
                                } else {
                                    downloadError = "完成:成功 ${ok.get()} / 失败 ${fail.get()}"
                                    downloadText = downloadError ?: "下载失败"
                                }
                                Toast.makeText(
                                    appContext,
                                    if (fail.get() == 0) "前置模组安装完成 (${ok.get()})"
                                    else "完成:成功 ${ok.get()} / 失败 ${fail.get()}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    task = { onProgress, isCancelled ->
                        repository.downloadVersionForVersions("mod", ver, versionIds, isCancelled, onProgress).isSuccess
                    }
                )
            }
            if (skippedInstalled > 0) {
                Toast.makeText(appContext, "已跳过 $skippedInstalled 个已安装的前置模组", Toast.LENGTH_SHORT).show()
            }
            if (resolveFailed > 0) {
                Toast.makeText(appContext, "$resolveFailed 个前置模组解析失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 安装到选中的多个版本：提交到下载中心，页面退出不中断 */
    fun startDownloadForVersions(projectType: String, projectTitle: String?, selectedVersion: ResourceVersion?, targetVersions: List<MinecraftVersion>) {
        val ver = selectedVersion ?: return
        if (targetVersions.isEmpty()) return
        val itemKey = "resource:$projectType:${ver.id}"
        if (DownloadCenter.findByKey(itemKey) != null) {
            Toast.makeText(context, "该资源已在下载队列中", Toast.LENGTH_SHORT).show()
            return
        }
        downloadDone = false
        downloadError = null
        downloadText = "准备下载..."
        val appContext = context.applicationContext
        val config = snapshot()
        val repository = ResourceRepository(context, snapshot = config)
        val primary = ver.primaryFile ?: ver.files.firstOrNull()
        DownloadCenter.submit(
            key = itemKey,
            title = primary?.filename?.takeIf { it.isNotBlank() } ?: (projectTitle ?: ver.versionNumber),
            onComplete = { success, error ->
                scope.launch(Dispatchers.Main) {
                    if (success) {
                        downloadDone = true
                        downloadText = "已安装到 ${targetVersions.size} 个版本"
                        Toast.makeText(appContext, "已安装到 ${targetVersions.size} 个版本", Toast.LENGTH_SHORT).show()
                    } else if (error != "已取消") {
                        downloadError = error ?: "下载失败"
                        downloadText = downloadError ?: "下载失败"
                        Toast.makeText(appContext, downloadError ?: "下载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            task = { onProgress, isCancelled ->
                repository.downloadVersionForVersions(projectType, ver, targetVersions.map { it.id }, isCancelled, onProgress).isSuccess
            }
        )
        Toast.makeText(context, "已加入下载队列", Toast.LENGTH_SHORT).show()
    }

}
