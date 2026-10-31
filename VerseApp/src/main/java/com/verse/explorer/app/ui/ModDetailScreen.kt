package com.verse.explorer.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.core.data.FavoritesRepository
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.app.download.DownloadCenter
import com.verse.explorer.app.download.DownloadCenterState
import com.verse.explorer.app.download.ModpackInstallSession
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.ModpackInstallDialog
import com.verse.explorer.app.ui.components.ModpackInstallPhase
import com.verse.explorer.app.ui.components.ModpackInstallUiState
import com.verse.explorer.app.ui.components.NoInstalledVersionsDialog
import com.verse.explorer.app.ui.components.applyTaskEvent
import com.verse.explorer.app.ui.components.withFixedStageRows
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.VerseVersionItem
import com.verse.explorer.app.ui.components.VersionPickerDialog
import com.verse.explorer.app.ui.components.VersionNamingDialog
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.install.modpack.ModpackInstallCancelledException
import com.verse.explorer.core.install.modpack.ModpackInstaller
import com.verse.explorer.core.install.modpack.ModpackTaskEvent
import com.verse.explorer.core.resource.ResourceDetail
import com.verse.explorer.core.resource.ResourceRepository
import com.verse.explorer.core.resource.McVersionGroups
import com.verse.explorer.core.resource.ResourceVersion
import com.verse.explorer.core.util.FileUtils
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import com.verse.explorer.core.version.VersionType
import com.tungsten.fclcore.game.DefaultGameRepository
import com.tungsten.fclcore.task.Schedulers
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModDetailScreen(
    navController: NavHostController,
    projectId: String,
    projectType: String = "mod",
    source: String = "modrinth"
) {
    val context = LocalContext.current
    val repository = remember {
        ResourceRepository(context.applicationContext as Application)
    }
    // 详情标题汉化依赖翻译表（可能未经资源列表页直接进入详情）
    LaunchedEffect(Unit) {
        com.verse.explorer.app.data.ModTranslationMapper.init(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var project by remember { mutableStateOf<ResourceDetail?>(null) }
    var versions by remember { mutableStateOf<List<ResourceVersion>>(emptyList()) }
    var versionsError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedVersion by remember { mutableStateOf<ResourceVersion?>(null) }
    var retryCount by remember { mutableStateOf(0) }

    // 前置依赖：从版本列表聚合（描述 tab 内嵌展示），名称/图标按依赖自身来源解析
    // 同一项目在不同版本里可能一个标 required、一个标 optional（Modrinth 由作者各自标注），
    // 这里按「最严」合并：只要有一处标必需就按必需处理，避免把必装前置显示成可选、
    // 或者把可选当成必装塞给用户。
    val deps = remember(versions) {
        val flat = versions.flatMap { it.dependencies }.filter { it.projectId.isNotBlank() }
        val requiredIds = flat.filter { it.type == "required" }.map { it.projectId }.toSet()
        flat.distinctBy { it.projectId }.map {
            if (it.projectId in requiredIds && it.type != "required") it.copy(type = "required") else it
        }
    }
    var depDetails by remember { mutableStateOf<Map<String, ResourceDetail>>(emptyMap()) }
    LaunchedEffect(deps) {
        val map = mutableMapOf<String, ResourceDetail>()
        deps.forEach { d ->
            if (d.projectId !in map) {
                runCatching { repository.getDetail(d.projectId, d.source) }
                    .onSuccess { map[d.projectId] = it }
            }
        }
        depDetails = map
    }

    var detailTab by remember { mutableStateOf("description") }
    var versionFilter by remember { mutableStateOf("") }
    // 版本标签分组方式，对齐 PCL 的 GroupedDrop / GroupedOld（渐进退化，见 computeVersionTabs）
    var versionGroupByDrop by remember { mutableStateOf(false) }
    var versionGroupFoldOld by remember { mutableStateOf(false) }
    var versionTabKeys by remember { mutableStateOf<List<String>>(emptyList()) }

    // 下载状态：下载任务全部提交到下载中心（应用级协程，退出页面不中断），
    // 本页只订阅队列展示进度
    val centerItems by DownloadCenter.items.collectAsState()
    var downloadText by remember { mutableStateOf("") }
    var downloadDone by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    // 下载/安装结束后的结果弹窗：success + 提示文案（失败时带具体错误）
    var downloadResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // 本页提交的下载 key 前缀（整合包 zip / 单资源 / 前置模组）
    val modpackKeyPrefix = "modpack:$projectId:"
    val resourceKeyPrefix = "resource:$projectType:"
    val depKeyPrefix = "dep:$projectId:"
    val hasRunningDownload = centerItems.any { item ->
        (item.key.startsWith(modpackKeyPrefix) || item.key.startsWith(resourceKeyPrefix) || item.key.startsWith(depKeyPrefix)) &&
            item.state == DownloadCenterState.RUNNING
    }
    // 安装阶段通过 ModpackInstallSession 承载（应用级会话，页面退出不丢）
    val modpackInstalling by ModpackInstallSession.installing.collectAsState()
    val downloading = hasRunningDownload || modpackInstalling

    var showVersionPicker by remember { mutableStateOf(false) }
    var showNoVersionsWarning by remember { mutableStateOf(false) }
    var installedVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }

    var pickerMode by remember { mutableStateOf("main") }

    // ===== 整合包下载/安装弹窗状态（已提升为 ModpackInstallSession，可最小化后从小窗恢复） =====
    val modpackState by ModpackInstallSession.state.collectAsState()
    val minimizedKeys by DownloadCenter.minimizedKeys.collectAsState()
    /** 本页整合包详情的导航路由（小窗点击任务跳回） */
    val modpackRoute = "modpack_detail/$projectId?source=$source"

    /** 移动网络确认（开始下载前确认，避免无意消耗流量） */
    var showMobileDataConfirm by remember { mutableStateOf(false) }
    var pendingModpackInstall by remember { mutableStateOf<Pair<ResourceVersion, String?>?>(null) }

    var showModpackNamingDialog by remember { mutableStateOf(false) }
    var modpackConfirmName by remember { mutableStateOf<String?>(null) }

    // 从小窗「展开」跳回本页时恢复弹窗：最小化记录已被清除、安装链路仍在跑
    LaunchedEffect(minimizedKeys, modpackState.phase, modpackState.visible) {
        if (ModpackInstallSession.belongsTo(projectId) &&
            !modpackState.visible &&
            ModpackInstallSession.dialogKey?.let { it !in minimizedKeys } == true &&
            (modpackState.phase == ModpackInstallPhase.DOWNLOAD_FILE ||
                modpackState.phase == ModpackInstallPhase.INSTALLING)
        ) {
            ModpackInstallSession.showDialog()
        }
    }

    // 整合包文件下载进度（订阅下载中心条目，任务在应用级协程中执行）
    LaunchedEffect(centerItems, selectedVersion) {
        val item = centerItems.firstOrNull { it.key.startsWith(modpackKeyPrefix) } ?: return@LaunchedEffect
        if (modpackState.visible && modpackState.phase == ModpackInstallPhase.DOWNLOAD_FILE) {
            val percent = if (item.progress >= 0) "${(item.progress * 100).toInt()}%" else ""
            // 下载阶段同时展示已下载/总大小
            val sizeText = if (item.total > 0) {
                "${FileUtils.formatFileSize(item.downloaded)} / ${FileUtils.formatFileSize(item.total)}"
            } else ""
            val speedText = if (item.speed > 0) formatSpeed(item.speed) else ""
            ModpackInstallSession.syncDownloadProgress(
                progress = item.progress.coerceAtLeast(0f),
                speed = item.speed,
                text = listOf(percent, sizeText, speedText)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            )
        }
    }

    // "待安装"整合包自动续接安装（沿用下载完成即安装的交互；页面重新进入也续接）
    LaunchedEffect(centerItems) {
        val ready = centerItems.firstOrNull {
            it.key.startsWith(modpackKeyPrefix) && it.state == DownloadCenterState.WAIT_INSTALL
        }
        if (ready != null) DownloadCenter.install(ready)
    }

    // 画廊大图
    var galleryView by remember { mutableStateOf<String?>(null) }

    /**
     * 构建「适用版本」标签 —— 完全对齐 PCL `PageDownloadCompDetail.Load_OnFinish`：
     * 先用精确小版本分组，标签数仍 >= 9 时依次退化为
     * 「按 Drop 桶」→「精确 + 折叠远古版」→「Drop + 折叠远古版」，
     * 第一次得到少于 9 个标签就停下。
     *
     * 旧实现是「精确 > 9 就整体切到 major」，而 major 的 `versionToMajor()` 对 1.14 以前的版本
     * 直接返回 null —— 这些版本既没有标签、过滤时也永远匹配不上，表现为「有些版本被漏掉」。
     */
    fun computeVersionTabs(vers: List<ResourceVersion>) {
        val candidates = listOf(
            false to false, // 精确小版本（1.20.1 / 1.20.4 各自一项）
            true to false,  // 按 Drop 桶（1.20 / 26.2）
            false to true,  // 精确 + 远古版折叠
            true to true    // Drop + 远古版折叠
        )
        var groupedByDrop = false
        var foldOld = false
        var keys: List<String> = emptyList()
        for ((byDrop, fold) in candidates) {
            keys = vers.asSequence()
                .flatMap { it.gameVersions.asSequence() }
                .map { McVersionGroups.groupedName(it, byDrop, fold) }
                .distinct()
                .sortedWith(versionTabComparator())
                .toList()
            groupedByDrop = byDrop
            foldOld = fold
            if (keys.size < 9) break
        }
        versionGroupByDrop = groupedByDrop
        versionGroupFoldOld = foldOld
        versionTabKeys = keys
        VerseLogger.info(
            "版本标签: groupedByDrop=$groupedByDrop, foldOld=$foldOld, tabs=${keys.size} -> ${keys.take(12)}",
            "ModDetail"
        )
    }

    val fetchData = suspend {
        try {
            val proj = repository.getDetail(projectId, source)
            project = proj
            VerseLogger.info("Project loaded: ${proj.title} (id=$projectId, type=$projectType, source=$source)", "ModDetail")
            try {
                val vers = repository.getVersions(projectId, source)
                versions = vers
                versionsError = null
                selectedVersion = vers.firstOrNull()
                computeVersionTabs(vers)
                VerseLogger.info("Versions loaded: ${vers.size}, tabs: ${versionTabKeys.size}, depsTotal: ${vers.sumOf { it.dependencies.size }}, requiredDeps: ${vers.flatMap { it.dependencies }.count { it.type == "required" && it.projectId.isNotBlank() }}", "ModDetail")
            } catch (e: Exception) {
                // 版本列表失败要给用户可见反馈，不能静默显示"该筛选下暂无版本"
                versionsError = "版本列表加载失败: ${e.message}"
                VerseLogger.warn("Failed to load versions: ${e.message}\n${e.stackTraceToString()}", "ModDetail")
            }
            isLoading = false
        } catch (e: Exception) {
            val msg = e.message ?: "未知错误"
            VerseLogger.error("Detail load failed: $msg (projectId=$projectId, type=$projectType, source=$source, retry=$retryCount)", "ModDetail")
            error = "加载失败: $msg"
            isLoading = false
        }
    }

    LaunchedEffect(projectId, source, retryCount) {
        if (projectId.isBlank()) {
            error = "项目ID为空"
            isLoading = false
            return@LaunchedEffect
        }
        project = null
        versions = emptyList()
        versionsError = null
        selectedVersion = null
        downloadDone = false
        downloadError = null
        versionFilter = ""
        detailTab = "description"
        isLoading = true
        error = null
        scope.launch { fetchData() }
    }

    fun runModpackInstall(
        appContext: android.content.Context,
        file: File?,
        name: String?,
        ver: ResourceVersion?,
        iconFile: File?
    ) {
        if (file == null || !file.exists()) return
        // 安装链路整体迁入 ModpackInstallSession（页面退出后弹窗状态仍存活，
        // 并向下载中心提交独占镜像条目驱动小窗的总安装百分比）
        ModpackInstallSession.onFinished = { success, error ->
            Schedulers.androidUIThread().execute {
                if (success) {
                    downloadDone = true
                    downloadText = "整合包安装完成"
                } else {
                    downloadError = error ?: "整合包安装失败"
                    downloadText = downloadError ?: "整合包安装失败"
                }
            }
        }
        ModpackInstallSession.beginInstall(
            appContext = appContext,
            file = file,
            name = name,
            iconUrl = project?.iconUrl?.takeIf { it.isNotBlank() },
            zipCache = file,
            iconCache = iconFile
        )
    }

    /**
     * 整合包下载 + 安装（像下载版本一样弹出下载弹窗）：
     * 1. 整合包 .mrpack 文件提交到下载中心下载（应用级协程，退出页面不中断），
     *    弹窗第一阶段"下载整合包文件"订阅队列条目进度；
     *    下载完成后同步取整合包项目图标，供安装后作为版本图标；
     * 2. 下载完成后走安装链路（见 runModpackInstall），每个阶段以任务事件形式
     *    逐阶段展示在弹窗中；下载源由引擎（BMCLAPI）负责，不自研。
     *
     * @param name 版本名（命名弹窗确认值；为 null 时默认取整合包标题）
     */
    fun startModpackDownload(ver: ResourceVersion, name: String?, skipMobileCheck: Boolean = false) {
        if (downloading) return
        // 整合包下载是独占任务：任何下载进行中都不允许再开
        if (!DownloadCenter.canStart(exclusive = true)) {
            Toast.makeText(context, "有下载任务正在进行，完成后再下载整合包", Toast.LENGTH_SHORT).show()
            return
        }
        val itemKey = "$modpackKeyPrefix${ver.id}"
        // 同一整合包已在队列中：待安装则续接安装，下载中则恢复弹窗进度
        when (DownloadCenter.findByKey(itemKey)?.state) {
            DownloadCenterState.WAIT_INSTALL -> {
                DownloadCenter.findByKey(itemKey)?.let { DownloadCenter.install(it) }
                return
            }
            DownloadCenterState.RUNNING -> {
                ModpackInstallSession.beginDownloadPhase(
                    projectId = projectId,
                    route = modpackRoute,
                    title = project?.title ?: ver.versionNumber,
                    iconUrl = project?.iconUrl?.takeIf { it.isNotBlank() }
                )
                return
            }
            else -> {}
        }
        // 真正下发下载任务前确认移动网络，用户取消则不下发任何任务
        if (!skipMobileCheck && com.verse.explorer.core.util.NetworkUtils.isMobileDataConnected(context)) {
            pendingModpackInstall = ver to name
            showMobileDataConfirm = true
            return
        }
        downloadDone = false
        downloadError = null
        downloadText = "准备下载整合包..."
        ModpackInstallSession.beginDownloadPhase(
            projectId = projectId,
            route = modpackRoute,
            title = project?.title ?: ver.versionNumber,
            iconUrl = project?.iconUrl?.takeIf { it.isNotBlank() }
        )
        val appContext = context.applicationContext
        val runName = name ?: project?.title?.trim()?.takeIf { it.isNotBlank() }
        val zipRef = java.util.concurrent.atomic.AtomicReference<File?>(null)
        val iconRef = java.util.concurrent.atomic.AtomicReference<File?>(null)
        DownloadCenter.submit(
            key = itemKey,
            title = project?.title ?: ver.versionNumber,
            // 待安装动作：下载完成后（本页自动续接或重新进入本页时）执行安装
            installAction = Runnable { runModpackInstall(appContext, zipRef.get(), runName, ver, iconRef.get()) },
            cleanupAction = Runnable {
                runCatching { zipRef.get()?.delete() }
                runCatching { iconRef.get()?.delete() }
            },
            // 版本/整合包下载为独占任务：运行期间下载中心拒绝其他下载
            exclusive = true,
            route = modpackRoute,
            onComplete = { success, error ->
                Schedulers.androidUIThread().execute {
                    if (success) {
                        downloadText = "整合包下载完成"
                    } else {
                        downloadError = error ?: "整合包下载失败"
                        downloadText = downloadError ?: "整合包下载失败"
                        ModpackInstallSession.markDownloadFinished(
                            cancelled = error == "已取消",
                            error = error ?: "整合包下载失败"
                        )
                    }
                }
            },
            task = { onProgress, isCancelled ->
                val result = repository.downloadVersion("modpack", ver, isCancelled, onProgress)
                result.getOrNull()?.let { zipRef.set(it) }
                if (result.isSuccess) {
                    // 整合包项目图标：下载阶段取得，安装后作为版本图标（失败不影响安装）
                    val iconUrl = project?.iconUrl?.takeIf { it.isNotBlank() }
                    if (iconUrl != null) {
                        iconRef.set(
                            repository.downloadIcon(
                                url = iconUrl,
                                destFile = File(appContext.cacheDir, "modpack_icon_${ver.id}.png"),
                                isCancelled = isCancelled
                            )
                        )
                    }
                }
                result.isSuccess
            }
        )
    }

    /**
     * 开始整合包下载：移动网络确认在下发下载任务前完成（见 startModpackDownload）。
     */
    fun startModpackInstall(ver: ResourceVersion, name: String?) {
        startModpackDownload(ver, name)
    }

    /** 取消整合包下载/安装：下载中取消队列任务；安装中经会话取消安装器 */
    fun cancelModpackInstall() {
        val item = centerItems.firstOrNull { it.key.startsWith(modpackKeyPrefix) }
        if (item != null && item.state == DownloadCenterState.RUNNING) {
            DownloadCenter.cancel(item)
        } else {
            ModpackInstallSession.cancelInstall()
        }
    }

    /** 重试整合包下载/安装：先清理上次中断的半成品版本目录，真已装版本则重新命名 */
    fun retryModpackInstall() {
        val ver = selectedVersion ?: return
        val name = modpackConfirmName ?: project?.title?.trim()?.takeIf { it.isNotBlank() } ?: ver.versionNumber
        val gameDir = GameConfig.getGameDir(context.applicationContext)
        if (VersionManager(gameDir).versionIdConflicts(name)) {
            val repo = DefaultGameRepository(gameDir)
            runCatching { repo.refreshVersions() }
            if (repo.isModpack(name)) {
                // 真已装（安装完整落盘）：换名重装，重新走命名弹窗
                modpackConfirmName = null
                showModpackNamingDialog = true
                return
            }
            // 半成品版本目录（modpack.json 缺失）：清理后用旧名重试
            runCatching { repo.removeVersionFromDisk(name) }
        }
        startModpackInstall(ver, name)
    }

    /**
     * 模组/材质包/光影包等先弹出版本选择卡片，
     * 确认后安装到所选版本的对应文件夹（隔离安装，不写全局目录）。
     */
    fun beginInstall() {
        if (downloading) return
        // 版本/整合包下载期间禁止其他下载（独占规则）
        if (!DownloadCenter.canStart(exclusive = false)) {
            Toast.makeText(context, "版本/整合包下载中，暂时无法进行其他下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedVersion == null) return
        if (projectType == "modpack") {
            showModpackNamingDialog = true
            return
        }
        pickerMode = "main"
        scope.launch {
            installedVersions = withContext(Dispatchers.IO) {
                VersionManager(GameConfig.getGameDir(context)).getLocalVersions()
            }
            if (installedVersions.isEmpty()) {
                showNoVersionsWarning = true
            } else {
                showVersionPicker = true
            }
        }
    }

    /**
     * 先弹出版本选择卡片，再把全部必需前置安装到所选版本的 mods 文件夹。
     */
    fun beginInstallDeps() {
        if (downloading) return
        // 版本/整合包下载期间禁止其他下载（独占规则）
        if (!DownloadCenter.canStart(exclusive = false)) {
            Toast.makeText(context, "版本/整合包下载中，暂时无法进行其他下载", Toast.LENGTH_SHORT).show()
            return
        }
        // 与展示侧同一套合并规则：只要有一个版本把该项目标为必需，就按必需处理。
        // 原先 distinctBy 取首个元素，若首个体现在其他版本里被标成 optional 就会漏装。
        val flatDeps = versions.flatMap { it.dependencies }.filter { it.projectId.isNotBlank() }
        val requiredIds = flatDeps.filter { it.type == "required" }.map { it.projectId }.toSet()
        val deps = flatDeps.distinctBy { it.projectId }.filter { it.projectId in requiredIds }
        if (deps.isEmpty()) {
            Toast.makeText(context, "没有需要安装的前置模组", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            installedVersions = withContext(Dispatchers.IO) {
                VersionManager(GameConfig.getGameDir(context)).getLocalVersions()
            }
            if (installedVersions.isEmpty()) {
                showNoVersionsWarning = true
            } else {
                pickerMode = "deps"
                showVersionPicker = true
            }
        }
    }

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
    fun startDepsForVersions(targetVersions: List<MinecraftVersion>) {
        if (downloading || targetVersions.isEmpty()) return
        val versionIds = targetVersions.map { it.id }
        val appContext = context.applicationContext
        scope.launch {
            // 同上：必需优先合并，避免漏掉被别的版本标成 required 的依赖
            val flatDeps = versions.flatMap { it.dependencies }.filter { it.projectId.isNotBlank() }
            val requiredIds = flatDeps.filter { it.type == "required" }.map { it.projectId }.toSet()
            val deps = flatDeps.distinctBy { it.projectId }.filter { it.projectId in requiredIds }
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
            val versionsDir = GameConfig.getVersionsDir(GameConfig.getGameDir(appContext))
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

            val queued = AtomicInteger(0)
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
                        Schedulers.androidUIThread().execute {
                            if (success) ok.incrementAndGet() else fail.incrementAndGet()
                            if (ok.get() + fail.get() >= queued.get()) {
                                if (fail.get() == 0) {
                                    downloadDone = true
                                    downloadText = ""
                                    downloadResult = true to "前置模组已安装到 ${versionIds.size} 个版本"
                                } else {
                                    downloadText = ""
                                    downloadResult = false to "前置模组安装完成：成功 ${ok.get()} 个 / 失败 ${fail.get()} 个"
                                }
                            }
                        }
                    },
                    task = { onProgress, isCancelled ->
                        repository.downloadVersionForVersions("mod", ver, versionIds, isCancelled, onProgress).isSuccess
                    }
                )
                queued.incrementAndGet()
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
    fun startDownloadForVersions(targetVersions: List<MinecraftVersion>) {
        val ver = selectedVersion ?: return
        if (targetVersions.isEmpty()) return
        // 版本/整合包下载期间禁止其他下载（独占规则）
        if (!DownloadCenter.canStart(exclusive = false)) {
            Toast.makeText(context, "版本/整合包下载中，暂时无法进行其他下载", Toast.LENGTH_SHORT).show()
            return
        }
        val itemKey = "$resourceKeyPrefix${ver.id}"
        if (DownloadCenter.findByKey(itemKey) != null) {
            Toast.makeText(context, "该资源已在下载队列中", Toast.LENGTH_SHORT).show()
            return
        }
        downloadDone = false
        downloadError = null
        downloadText = "准备下载..."
        val appContext = context.applicationContext
        val primary = ver.primaryFile ?: ver.files.firstOrNull()
        DownloadCenter.submit(
            key = itemKey,
            title = primary?.filename?.takeIf { it.isNotBlank() } ?: (project?.title ?: ver.versionNumber),
            iconUrl = project?.iconUrl,
            onComplete = { success, error ->
                Schedulers.androidUIThread().execute {
                    if (success) {
                        downloadDone = true
                        downloadText = ""
                        downloadResult = true to "已安装到 ${targetVersions.size} 个版本"
                    } else if (error != "已取消") {
                        downloadText = ""
                        downloadResult = false to (error ?: "下载失败")
                    }
                }
            },
            task = { onProgress, isCancelled ->
                repository.downloadVersionForVersions(projectType, ver, targetVersions.map { it.id }, isCancelled, onProgress).isSuccess
            }
        )
    }

    // 按当前筛选标签过滤版本
    // 与标签同一套分组函数，保证「标签上有的版本一定过滤得出来」
    val filteredVersions = remember(versions, versionFilter, versionGroupByDrop, versionGroupFoldOld) {
        if (versionFilter.isBlank()) versions
        else versions.filter { v ->
            v.gameVersions.any { gv ->
                McVersionGroups.groupedName(gv, versionGroupByDrop, versionGroupFoldOld) == versionFilter
            }
        }
    }

    val typeLabel = when (projectType) {
        "modpack" -> "整合包"
        "resourcepack" -> "资源包"
        "shader" -> "光影"
        else -> "模组"
    }
    val sourceLabel = if (source == "curseforge") "CurseForge" else "Modrinth"

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RoseLoader(
                        modifier = Modifier.size(32.dp),
                        size = 32.dp,
                        color = VerseColors.PcAccent
                    )
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.CloudOff, null, Modifier.size(48.dp), VerseColors.PcTextMuted)
                        Text(error ?: "未知错误", color = VerseColors.PcTextSecondary, fontSize = 13.sp)
                        OutlinedButton(onClick = { retryCount++ }, shape = RoundedCornerShape(8.dp)) {
                            Text("重试", fontSize = 13.sp)
                        }
                    }
                }
            }
            project != null -> {
                val proj = project!!
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { try { navController.popBackStack() } catch (_: Exception) {} },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = VerseColors.PcTextPrimary, modifier = Modifier.size(20.dp))
                        }
                        // 图标（未加载/无图标时显示灰色底图）
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VerseColors.PcBgActive,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (!proj.iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = proj.iconUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            // 汉化名命中时标题显示中文名
                            val projZh = com.verse.explorer.app.data.ModTranslationMapper.translateBySlug(proj.slug)
                            Text(
                                projZh ?: proj.title.ifEmpty { projectId },
                                color = VerseColors.PcTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // 描述 + 统计行合并为一行（移动端不占额外行高；描述截短避免挤占统计区）
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    proj.description.take(24) + if (proj.description.length > 24) "…" else "",
                                    color = VerseColors.PcTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text("⬇ ${formatCount(proj.downloads)} ❤ ${formatCount(proj.followers)}",
                                    fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                if (proj.dateModified.isNotBlank()) {
                                    Text("  🕐 ${mdFormatDate(proj.dateModified)}", fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (source == "curseforge") VerseColors.PcOrange.copy(alpha = 0.12f) else VerseColors.PcPurple.copy(alpha = 0.12f),
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    Text(
                                        sourceLabel,
                                        color = if (source == "curseforge") VerseColors.PcOrange else VerseColors.PcPurple,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 版本 tab 下隐藏：版本卡片自带下载按钮，操作栏属描述类操作，为版本列表让出纵向空间
                    var isFav by remember { mutableStateOf(FavoritesRepository.isFavorite(context, proj.id, source)) }
                    if (detailTab != "versions") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PcButton(
                            text = if (selectedVersion == null) "安装" else "安装 ${selectedVersion?.versionNumber ?: ""}",
                            onClick = { beginInstall() },
                            variant = PcButtonVariant.PRIMARY,
                            icon = Icons.Filled.Download,
                            modifier = Modifier.weight(1f)
                        )
                        PcButton(
                            text = if (isFav) "已收藏" else "收藏",
                            onClick = {
                                isFav = FavoritesRepository.toggle(context, proj)
                                Toast.makeText(context, if (isFav) "已收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
                            },
                            variant = if (isFav) PcButtonVariant.PRIMARY else PcButtonVariant.OUTLINE,
                            icon = Icons.Filled.Favorite
                        )
                        PcButton(
                            text = "打开源",
                            onClick = {
                                val url = proj.sourceUrl.ifBlank { null }
                                if (url != null) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "无可用浏览器", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "该资源未提供源码链接", Toast.LENGTH_SHORT).show()
                                }
                            },
                            variant = PcButtonVariant.OUTLINE,
                            icon = Icons.Filled.OpenInNew
                        )
                        PcButton(
                            text = "复制名称",
                            onClick = {
                                clipboardManager.setText(AnnotatedString(proj.title))
                                Toast.makeText(context, "已复制名称", Toast.LENGTH_SHORT).show()
                            },
                            variant = PcButtonVariant.OUTLINE,
                            icon = Icons.Filled.ContentCopy
                        )
                    }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 版本 tab 下隐藏右侧信息栏：版本列表占满全宽（右侧兼容性/分类/链接/
                    // 详情/许可证属描述类元信息，在浏览版本时是次要内容）
                    Row(modifier = Modifier.fillMaxSize()) {
                        // —— 主区 ——
                        Column(
                            modifier = Modifier
                                .then(if (detailTab == "versions") Modifier.fillMaxWidth() else Modifier.weight(1f))
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MdTabBtn("description", "描述", detailTab) { detailTab = it }
                                MdTabBtn("versions", "版本", detailTab) { detailTab = it }
                                if (proj.gallery.isNotEmpty()) {
                                    MdTabBtn("gallery", "画廊", detailTab) { detailTab = it }
                                }
                            }
                            Spacer(Modifier.height(6.dp))

                            when (detailTab) {
                                "versions" -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MdVTab("全部", versionFilter == "") { versionFilter = "" }
                                        versionTabKeys.forEach { key ->
                                            MdVTab(key, versionFilter == key) { versionFilter = key }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                    ) {
                                        items(filteredVersions, key = { it.id }) { version ->
                                            val isSelected = selectedVersion?.id == version.id
                                            // 选中版本的下载进度来自下载中心条目（任务在应用级协程）
                                            val centerItem = if (isSelected) {
                                                centerItems.firstOrNull { it.key == "$resourceKeyPrefix${version.id}" }
                                            } else null
                                            VersionsDetailCard(
                                                version = version,
                                                isSelected = isSelected,
                                                onClick = {
                                                    selectedVersion = version
                                                    if (isSelected && !downloading) beginInstall()
                                                },
                                                selected = isSelected && !downloading && !downloadDone,
                                                downloading = centerItem?.state == DownloadCenterState.RUNNING,
                                                downloadProgress = centerItem?.progress?.coerceAtLeast(0f) ?: 0f,
                                                downloadText = centerItem?.let {
                                                    buildString {
                                                        if (it.progress >= 0) append("${(it.progress * 100).toInt()}%")
                                                        if (it.speed > 0) {
                                                            if (isNotEmpty()) append(" · ")
                                                            append(formatSpeed(it.speed))
                                                        }
                                                    }
                                                } ?: (if (isSelected) downloadText else ""),
                                                downloadDone = false,
                                                downloadError = null,
                                                onDownload = { beginInstall() },
                                                modifier = Modifier.padding(vertical = 3.dp)
                                            )
                                        }
                                        if (filteredVersions.isEmpty()) {
                                            item {
                                                val emptyText: String = when {
                                                    versionsError != null -> versionsError ?: "版本列表加载失败"
                                                    versions.isEmpty() -> "版本列表为空"
                                                    else -> "该筛选下暂无版本"
                                                }
                                                Text(
                                                    emptyText,
                                                    color = if (versionsError != null) VerseColors.PcRed else VerseColors.PcTextMuted,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).padding(horizontal = 8.dp),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                                "gallery" -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(proj.gallery) { item ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) { galleryView = item.url }
                                            ) {
                                                AsyncImage(
                                                    model = item.url,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxWidth().height(180.dp).background(VerseColors.PcBgCard)
                                                )
                                                if (item.title.isNotBlank()) {
                                                    Text(
                                                        item.title,
                                                        fontSize = 11.sp,
                                                        color = VerseColors.PcTextMuted,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
                                        // 随页面滚动，不占用固定纵向空间；整合包自带全部依赖，不展示前置模组区块
                                        if (deps.isNotEmpty() && projectType != "modpack") {
                                            item {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = VerseColors.PcBgCard,
                                                    border = BorderStroke(1.dp, VerseColors.PcBorder),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    // 区块限高 + 内部滚动：依赖数量多时不把正文撑爆
                                                    Column(
                                                        modifier = Modifier
                                                            .padding(12.dp)
                                                            .heightIn(max = 220.dp)
                                                            .verticalScroll(rememberScrollState())
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(
                                                                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(VerseColors.PcYellow.copy(alpha = 0.15f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(Icons.Filled.Warning, null, Modifier.size(13.dp), VerseColors.PcYellow)
                                                            }
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("前置模组", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VerseColors.PcTextPrimary)
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(
                                                                "${deps.count { it.type == "required" }} 必需",
                                                                fontSize = 12.sp,
                                                                color = VerseColors.PcTextMuted
                                                            )
                                                            Spacer(Modifier.weight(1f))
                                                            PcButton(
                                                                text = "一键下载",
                                                                onClick = { beginInstallDeps() },
                                                                variant = PcButtonVariant.GHOST,
                                                                icon = Icons.Filled.Download
                                                            )
                                                        }
                                                        Spacer(Modifier.height(6.dp))
                                                        // 前置模组行：与资源列表行同密度（紧凑单行），不展示描述
                                                        // 必需 / 可选分开展示（PCL、ZL2 都是这样归类的），
                                                        // 否则可选依赖会被一并当成"前置"塞给用户
                                                        listOf(
                                                            "必需" to deps.filter { it.type == "required" },
                                                            "可选" to deps.filter { it.type == "optional" },
                                                            "其它" to deps.filter { it.type != "required" && it.type != "optional" }
                                                        ).forEach { (groupName, group) ->
                                                            if (group.isEmpty()) return@forEach
                                                            Text(
                                                                groupName,
                                                                fontSize = 11.sp,
                                                                color = VerseColors.PcTextMuted,
                                                                modifier = Modifier.padding(top = 2.dp)
                                                            )
                                                            group.forEach { dep ->
                                                            val info = depDetails[dep.projectId]
                                                            DependencyRow(
                                                                title = info?.title?.ifEmpty { null } ?: dep.projectId,
                                                                iconUrl = info?.iconUrl,
                                                                badge = when (dep.type) {
                                                                    "required" -> "必需"
                                                                    "optional" -> "可选"
                                                                    "incompatible" -> "不兼容"
                                                                    "embedded" -> "内嵌"
                                                                    "tool" -> "工具"
                                                                    "include" -> "包含"
                                                                    else -> dep.type
                                                                },
                                                                badgeColor = when (dep.type) {
                                                                    "required" -> VerseColors.PcGreen
                                                                    "optional" -> VerseColors.PcOrange
                                                                    "incompatible" -> VerseColors.PcRed
                                                                    else -> VerseColors.PcTextMuted
                                                                },
                                                                meta = info?.let { "${formatCount(it.downloads)} 下载" } ?: "",
                                                                onClick = {
                                                                    try {
                                                                        navController.navigate("mod_detail/${dep.projectId}?source=${dep.source}")
                                                                    } catch (_: Exception) {}
                                                                }
                                                            )
                                                            Spacer(Modifier.height(4.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(8.dp))
                                            }
                                        }
                                        item {
                                            if (proj.body.isBlank()) {
                                                Text(
                                                    "暂无描述",
                                                    color = VerseColors.PcTextMuted,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            } else {
                                                MarkdownBody(
                                                    body = proj.body,
                                                    textColor = VerseColors.PcTextSecondary,
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 仅描述/画廊 tab 显示；压缩到 200dp 让主区（描述正文/画廊）占主要宽度
                        if (detailTab != "versions") {
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 4.dp, end = 12.dp)
                        ) {
                            MdSidebarSection("兼容性") {
                                MdSidebarTags(proj.loaders, emptyAsDash = true)
                                Spacer(Modifier.height(6.dp))
                                val gvs = proj.gameVersions
                                MdSidebarTags(gvs.take(16) + if (gvs.size > 16) listOf("+${gvs.size - 16}") else emptyList(), emptyAsDash = true)
                            }
                            MdSidebarSection("分类") {
                                MdSidebarTags(proj.categories, emptyAsDash = true)
                            }
                            MdSidebarSection("链接") {
                                MdSidebarLinks(
                                    listOfNotNull(
                                        if (proj.sourceUrl.isNotBlank()) "源码" to proj.sourceUrl else null,
                                        if (proj.issuesUrl.isNotBlank()) "问题" to proj.issuesUrl else null,
                                        if (proj.wikiUrl.isNotBlank()) "Wiki" to proj.wikiUrl else null,
                                        if (proj.discordUrl.isNotBlank()) "Discord" to proj.discordUrl else null
                                    )
                                )
                            }
                            MdSidebarSection("详情") {
                                val rows = mutableListOf<Pair<String, String>>()
                                rows.add("类型" to typeLabel)
                                rows.add("下载量" to formatCount(proj.downloads))
                                rows.add("关注数" to formatCount(proj.followers))
                                if (proj.dateCreated.isNotBlank()) rows.add("创建" to mdFormatDate(proj.dateCreated))
                                if (proj.dateModified.isNotBlank()) rows.add("更新" to mdFormatDate(proj.dateModified))
                                rows.forEach { (k, v) -> MdSidebarDetailRow(k, v) }
                            }
                            MdSidebarSection("许可证") {
                                MdSidebarDetailRow("许可证", proj.license.ifBlank { "未知" })
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        }
                    }
                }
            }
        }

        if (showVersionPicker) {
            // 带加载器的版本排前面（模组通常需要加载器），原版同样可选并在列表里标注；
            // 材质包/光影与加载器无关，顺序不变
            val installableVersions = if (projectType == "mod") {
                installedVersions.sortedByDescending { it.loaderHint != null }
            } else {
                installedVersions
            }
            VersionPickerDialog(
                versions = installableVersions.map {
                    VerseVersionItem(
                        id = it.id,
                        typeLabel = if (projectType == "mod" && it.loaderHint == null) {
                            "原版（无加载器）"
                        } else when (it.type) {
                            VersionType.RELEASE -> "正式版"
                            VersionType.SNAPSHOT -> "快照版"
                            VersionType.OLD_ALPHA -> "旧版 Alpha"
                            VersionType.OLD_BETA -> "旧版 Beta"
                            else -> "特殊版本"
                        },
                        // 带加载器的版本显示加载器图标（fabric/forge/neoforge/quilt/optifine），
                        // 原版显示版本类型图标（正式版草方块 / 快照与特殊圆石 / 旧版 legacy）
                        iconAsset = it.loaderHint?.let { l -> "icons/loader_icon_$l.png" }
                            ?: when (it.type) {
                                VersionType.SPECIAL, VersionType.SNAPSHOT -> "icons/version_icon_cobblestone.png"
                                VersionType.OLD_ALPHA, VersionType.OLD_BETA -> "icons/version_icon_old.png"
                                else -> "icons/version_icon_grass.png"
                            },
                        iconBg = when (it.type) {
                            VersionType.SPECIAL, VersionType.SNAPSHOT -> VerseColors.PcOrange.copy(alpha = 0.15f)
                            VersionType.OLD_ALPHA, VersionType.OLD_BETA -> VerseColors.PcPurple.copy(alpha = 0.15f)
                            else -> VerseColors.PcGreen.copy(alpha = 0.15f)
                        }
                    )
                },
                defaultSelectedId = SettingsRepository.getCustomSetting("last_version"),
                title = when (projectType) {
                    "mod" -> "为哪个游戏版本下载这个模组？"
                    "modpack" -> "为哪个游戏版本下载这个整合包？"
                    "resourcepack" -> "为哪个游戏版本下载这个材质包？"
                    else -> "为哪个游戏版本下载这个光影？"
                },
                hint = if (projectType == "mod") {
                    "带加载器的版本可直接使用；原版版本没有加载器，模组放入后不会被加载，仅作为文件下载。"
                } else null,
                onDismiss = { showVersionPicker = false },
                onConfirm = { picked ->
                    showVersionPicker = false
                    val targets = picked.mapNotNull { item ->
                        installedVersions.find { v -> v.id == item.id }
                    }
                    if (pickerMode == "deps") startDepsForVersions(targets) else startDownloadForVersions(targets)
                }
            )
        }

        if (showNoVersionsWarning) {
            NoInstalledVersionsDialog(onDismiss = { showNoVersionsWarning = false })
        }
    }

    val viewUrl = galleryView
    if (viewUrl != null) {
        Dialog(onDismissRequest = { galleryView = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerseColors.PcBgCard)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { galleryView = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = viewUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }
    }

    if (showModpackNamingDialog) {
        VersionNamingDialog(
            gameVersion = project?.title?.trim()?.takeIf { it.isNotBlank() }
                ?: selectedVersion?.versionNumber ?: "",
            selectedLoaderId = null,
            iconUrl = project?.iconUrl?.takeIf { it.isNotBlank() },
            conflictsWith = { VersionManager(GameConfig.getGameDir(context)).versionIdConflicts(it) },
            onDismiss = { showModpackNamingDialog = false },
            onConfirm = { name ->
                showModpackNamingDialog = false
                modpackConfirmName = name
                val ver = selectedVersion ?: return@VersionNamingDialog
                startModpackInstall(ver, name)
            }
        )
    }

    // 移动网络确认（下载大文件前先确认）
    if (showMobileDataConfirm) {
        AlertDialog(
            onDismissRequest = {
                showMobileDataConfirm = false
                pendingModpackInstall = null
            },
            title = { Text("警告") },
            text = { Text("您正在使用移动数据网络，继续下载将消耗流量。") },
            confirmButton = {
                TextButton(onClick = {
                    val pending = pendingModpackInstall
                    showMobileDataConfirm = false
                    pendingModpackInstall = null
                    pending?.let { (ver, name) -> startModpackDownload(ver, name, skipMobileCheck = true) }
                }) {
                    Text("坚持使用", color = VerseColors.PcAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMobileDataConfirm = false
                    pendingModpackInstall = null
                }) {
                    Text("取消", color = VerseColors.PcTextSecondary)
                }
            }
        )
    }

    // ===== 下载/安装结果弹窗（完成或失败 + 错误原因）=====
    downloadResult?.let { (success, message) ->
        Dialog(onDismissRequest = { downloadResult = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VerseColors.PcBgActive,
                modifier = Modifier.width(360.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    (if (success) VerseColors.PcGreen else VerseColors.PcRed).copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = null,
                                tint = if (success) VerseColors.PcGreen else VerseColors.PcRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (success) "下载完成" else "下载失败",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VerseColors.PcTextPrimary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        color = if (success) VerseColors.PcTextSecondary else VerseColors.PcRed
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        PcButton(
                            text = "知道了",
                            onClick = { downloadResult = null },
                            variant = PcButtonVariant.PRIMARY
                        )
                    }
                }
            }
        }
    }

    // ===== 整合包下载/安装弹窗（对齐版本下载：分阶段任务流）=====
    // 弹窗状态在 ModpackInstallSession；最小化后可从下载小窗点回本页恢复
    val modpackMinimized = ModpackInstallSession.dialogKey?.let { it in minimizedKeys } == true
    if (modpackState.visible && ModpackInstallSession.belongsTo(projectId) && !modpackMinimized) {
        val modpackRunning = modpackState.phase == ModpackInstallPhase.DOWNLOAD_FILE ||
            modpackState.phase == ModpackInstallPhase.INSTALLING
        Dialog(onDismissRequest = {
            // 下载/安装进行中不允许直接关闭（与版本下载弹窗一致）
            if (!modpackRunning) ModpackInstallSession.closeDialog()
        }) {
            ModpackInstallDialog(
                state = modpackState,
                onCancel = { cancelModpackInstall() },
                onRetry = { retryModpackInstall() },
                onClose = { ModpackInstallSession.closeDialog() },
                onMinimize = {
                    // 最小化下载：弹窗收起，任务转入下载小窗，可随时点回恢复
                    ModpackInstallSession.dialogKey?.let { DownloadCenter.minimize(it) }
                }
            )
        }
    }
}
