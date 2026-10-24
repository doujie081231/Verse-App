package com.verse.explorer.app.ui

import com.verse.explorer.app.install.*
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.core.data.FavoritesRepository
import com.verse.explorer.core.data.SettingsRepository
import com.verse.explorer.app.download.DownloadCenter
import com.verse.explorer.app.download.DownloadCenterState
import com.verse.explorer.app.ui.components.ModpackInstallDialog
import com.verse.explorer.app.install.ModpackInstallPhase
import com.verse.explorer.app.install.ModpackInstallUiState
import com.verse.explorer.app.ui.components.NoInstalledVersionsDialog
import com.verse.explorer.app.install.applyTaskEvent
import com.verse.explorer.app.install.withFixedStageRows
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.VerseVersionItem
import com.verse.explorer.app.ui.components.VersionPickerDialog
import com.verse.explorer.app.ui.components.VersionNamingDialog
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.app.appContainer
import com.verse.explorer.core.resource.ResourceRepository
import com.verse.explorer.core.resource.ResourceVersion
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import com.verse.explorer.core.version.VersionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModDetailScreen(
    navController: NavHostController,
    projectId: String,
    projectType: String = "mod",
    source: String = "modrinth"
) {
    val context = LocalContext.current
    val modpackController = remember(context.applicationContext, projectId) { context.appContainer.modpackTask("detail:$projectId") }
    val installTaskState by modpackController.state.collectAsState()
    val detailViewModel = com.verse.explorer.app.viewmodel.modDetailViewModel(projectId, source)
    val detailState by detailViewModel.state.collectAsState()
    // 详情标题汉化依赖翻译表（可能未经资源列表页直接进入详情）
    LaunchedEffect(Unit) {
        com.verse.explorer.app.data.ModTranslationMapper.init(context.applicationContext)
    }
    val resourceActions = remember(context.applicationContext, projectId) { context.appContainer.resourceActions("detail:$source:$projectId") }
    val resourceInstallStatus by resourceActions.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val project = detailState.project
    val versions = detailState.versions
    val versionsError = detailState.versionsError
    val isLoading = detailState.isLoading
    val error = detailState.error
    var selectedVersion by remember { mutableStateOf<ResourceVersion?>(null) }

    // 前置依赖：从版本列表聚合（描述 tab 内嵌展示），名称/图标按依赖自身来源解析
    val deps = remember(versions) {
        versions.flatMap { it.dependencies }
            .distinctBy { it.projectId }
            .filter { it.projectId.isNotBlank() }
    }
    val depDetails = detailState.dependencies

    var detailTab by remember { mutableStateOf("description") }
    var versionFilter by remember { mutableStateOf("") }
    var versionTabMode by remember { mutableStateOf("exact") }
    var versionTabKeys by remember { mutableStateOf<List<String>>(emptyList()) }

    // 下载状态：下载任务全部提交到下载中心（应用级协程，退出页面不中断），
    // 本页只订阅队列展示进度
    val centerItems by DownloadCenter.items.collectAsState()
    var downloadText by remember { mutableStateOf("") }
    var downloadDone by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resourceInstallStatus) {
        downloadDone = resourceInstallStatus.done
        downloadText = resourceInstallStatus.text
        downloadError = resourceInstallStatus.error
    }

    // 本页提交的下载 key 前缀（整合包 zip / 单资源 / 前置模组）
    val modpackKeyPrefix = "modpack:$projectId:"
    val resourceKeyPrefix = "resource:$projectType:"
    val depKeyPrefix = "dep:$projectId:"
    val hasRunningDownload = centerItems.any { item ->
        (item.key.startsWith(modpackKeyPrefix) || item.key.startsWith(resourceKeyPrefix) || item.key.startsWith(depKeyPrefix)) &&
            item.state == DownloadCenterState.RUNNING
    }
    // 安装阶段不走下载中心（本地任务链），单独标记
    val installInProgress = installTaskState.visible && installTaskState.phase == ModpackInstallPhase.INSTALLING
    val downloading = hasRunningDownload || installInProgress

    var showVersionPicker by remember { mutableStateOf(false) }
    var showNoVersionsWarning by remember { mutableStateOf(false) }
    var installedVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }

    var pickerMode by remember { mutableStateOf("main") }

    // 整合包下载/安装弹窗状态（对齐版本下载：分阶段任务流）
    var modpackState by remember { mutableStateOf(ModpackInstallUiState()) }
    /** 本次已下载的整合包 zip（安装完成/失败/取消后删除，避免介质残留） */
    var modpackZip by remember { mutableStateOf<File?>(null) }

    var showModpackNamingDialog by remember { mutableStateOf(false) }
    var modpackConfirmName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(installTaskState) {
        if (installTaskState.visible) modpackState = installTaskState
    }

    // 整合包文件下载进度（订阅下载中心条目，任务在应用级协程中执行）
    LaunchedEffect(centerItems, selectedVersion) {
        val item = centerItems.firstOrNull { it.key.startsWith(modpackKeyPrefix) } ?: return@LaunchedEffect
        if (modpackState.visible && modpackState.phase == ModpackInstallPhase.DOWNLOAD_FILE) {
            val text = if (item.progress >= 0) "${(item.progress * 100).toInt()}%" else ""
            modpackState = modpackState.copy(
                fileProgress = item.progress.coerceAtLeast(0f),
                downloadSpeed = item.speed,
                fileText = if (item.speed > 0) "$text · ${formatSpeed(item.speed)}" else text
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

    fun computeVersionTabs(vers: List<ResourceVersion>) {
        val exactMap = linkedMapOf<String, Int>()
        val majorMap = linkedMapOf<String, Int>()
        var hasSnapshot = false
        vers.forEach { v ->
            v.gameVersions.forEach { gv ->
                if (gv.contains("w") || gv.contains("snapshot")) { hasSnapshot = true; return@forEach }
                versionToExact(gv)?.let { exactMap[it] = (exactMap[it] ?: 0) + 1 }
                versionToMajor(gv)?.let { majorMap[it] = (majorMap[it] ?: 0) + 1 }
            }
        }
        val mode = if (exactMap.size > 9) "major" else "exact"
        val map = if (mode == "exact") exactMap else majorMap
        val keys = map.keys.sortedWith(versionKeyComparator())
        // 快照版放最前
        val sorted = ArrayList<String>()
        if (hasSnapshot) sorted.add("快照版")
        sorted.addAll(keys)
        versionTabMode = mode
        versionTabKeys = sorted
    }

    LaunchedEffect(versions) {
        if (selectedVersion == null) selectedVersion = versions.firstOrNull()
        computeVersionTabs(versions)
    }

    /**
     * 整合包下载 + 安装（像下载版本一样弹出下载弹窗）：
     * 1. 整合包 .mrpack 文件提交到下载中心下载（应用级协程，退出页面不中断），
     *    弹窗第一阶段"下载整合包文件"订阅队列条目进度；
     * 2. 下载完成后走安装链路（见 runModpackInstall），每个阶段以任务事件形式
     *    逐阶段展示在弹窗中；下载源由引擎（BMCLAPI）负责，不自研。
     *
     * @param name 版本名（命名弹窗确认值；为 null 时默认取整合包标题）
     */
    fun startModpackInstall(ver: ResourceVersion, name: String?) {
        if (downloading) return
        val itemKey = "$modpackKeyPrefix${ver.id}"
        // 同一整合包已在队列中：待安装则续接安装，下载中则恢复弹窗进度
        when (DownloadCenter.findByKey(itemKey)?.state) {
            DownloadCenterState.WAIT_INSTALL -> {
                DownloadCenter.findByKey(itemKey)?.let { DownloadCenter.install(it) }
                return
            }
            DownloadCenterState.RUNNING -> {
                modpackState = ModpackInstallUiState(
                    visible = true,
                    title = project?.title ?: ver.versionNumber,
                    iconUrl = project?.iconUrl?.takeIf { it.isNotBlank() },
                    phase = ModpackInstallPhase.DOWNLOAD_FILE
                )
                return
            }
            else -> {}
        }
        downloadDone = false
        downloadError = null
        downloadText = "准备下载整合包..."
        modpackState = ModpackInstallUiState(
            visible = true,
            title = project?.title ?: ver.versionNumber,
            iconUrl = project?.iconUrl?.takeIf { it.isNotBlank() },
            phase = ModpackInstallPhase.DOWNLOAD_FILE
        )
        val appContext = context.applicationContext
        val runName = name ?: project?.title?.trim()?.takeIf { it.isNotBlank() }
        val zipRef = java.util.concurrent.atomic.AtomicReference<File?>(null)
        val installSnapshot = appContext.appContainer.installationSnapshot()
        val targetRoot = installSnapshot.paths.root
        val repository = ResourceRepository(appContext, snapshot = installSnapshot)
        val installTitle = project?.title ?: ver.versionNumber
        val installIcon = project?.iconUrl?.takeIf { it.isNotBlank() }
        DownloadCenter.submit(
            key = itemKey,
            title = project?.title ?: ver.versionNumber,
            // 待安装动作：下载完成后（本页自动续接或重新进入本页时）执行安装
            installAction = Runnable { zipRef.get()?.let { file -> modpackController.start(targetRoot, file, runName, installTitle, installIcon) } },
            cleanupAction = Runnable { runCatching { zipRef.get()?.delete() } },
            onComplete = { success, error -> if (!success) modpackController.downloadFailed(installTitle, error) },
            task = { onProgress, isCancelled ->
                val result = repository.downloadVersion("modpack", ver, isCancelled, onProgress)
                result.getOrNull()?.let { zipRef.set(it) }
                result.isSuccess
            }
        )
    }

    /** 取消整合包下载/安装：下载中取消队列任务；安装中取消安装器 */
    fun cancelModpackInstall() {
        val item = centerItems.firstOrNull { it.key.startsWith(modpackKeyPrefix) }
        if (item != null && item.state == DownloadCenterState.RUNNING) {
            DownloadCenter.cancel(item)
        } else {
            modpackController.cancel()
        }
    }

    /** 重试整合包下载/安装：先清理上次中断的半成品版本目录，真已装版本则重新命名 */
    fun retryModpackInstall() {
        val ver = selectedVersion ?: return
        val name = modpackConfirmName ?: project?.title?.trim()?.takeIf { it.isNotBlank() } ?: ver.versionNumber
        val gameDir = GameConfig.getGameDir(context.applicationContext)
        if (modpackController.requiresNewName(gameDir, name)) {
            modpackConfirmName = null
            showModpackNamingDialog = true
            return
        }
        startModpackInstall(ver, name)
    }

    /**
     * 模组/材质包/光影包等先弹出版本选择卡片，
     * 确认后安装到所选版本的对应文件夹（隔离安装，不写全局目录）。
     */
    fun beginInstall() {
        if (downloading) return
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
        val deps = versions.flatMap { it.dependencies }
            .distinctBy { it.projectId }
            .filter { it.projectId.isNotBlank() && it.type == "required" }
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

    fun startDepsForVersions(targetVersions: List<MinecraftVersion>) {
        if (!downloading) resourceActions.startDepsForVersions(projectId, versions, selectedVersion, targetVersions)
    }

    fun startDownloadForVersions(targetVersions: List<MinecraftVersion>) {
        resourceActions.startDownloadForVersions(projectType, project?.title, selectedVersion, targetVersions)
    }

    // 按当前筛选标签过滤版本
    val filteredVersions = remember(versions, versionFilter, versionTabMode) {
        if (versionFilter.isBlank()) versions
        else versions.filter { v ->
            v.gameVersions.any { gv ->
                when (versionFilter) {
                    "快照版" -> gv.contains("w") || gv.contains("snapshot")
                    else -> {
                        val exact = versionToExact(gv)
                        val major = versionToMajor(gv)
                        if (versionTabMode == "exact") exact == versionFilter else major == versionFilter
                    }
                }
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
                        OutlinedButton(onClick = { detailViewModel.reload() }, shape = RoundedCornerShape(8.dp)) {
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
                                                downloadDone = isSelected && downloadDone,
                                                downloadError = if (isSelected) downloadError else null,
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
                                        // 随页面滚动，不占用固定纵向空间
                                        if (deps.isNotEmpty()) {
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
                                                            Text("${deps.size}", fontSize = 12.sp, color = VerseColors.PcTextMuted)
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
                                                        deps.forEach { dep ->
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
            // 模组依赖加载器：只能安装到 fabric/forge/neoforge/quilt 版本，原版不可选；
            // 材质包/光影不依赖加载器，全部版本可选
            val installableVersions = if (projectType == "mod") {
                installedVersions.filter {
                    it.loaderHint in listOf("fabric", "forge", "neoforge", "quilt")
                }
            } else {
                installedVersions
            }
            VersionPickerDialog(
                versions = installableVersions.map {
                    VerseVersionItem(
                        id = it.id,
                        typeLabel = when (it.type) {
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

    // 整合包下载/安装弹窗（对齐版本下载：分阶段任务流）
    if (modpackState.visible) {
        val modpackRunning = modpackState.phase == ModpackInstallPhase.DOWNLOAD_FILE ||
            modpackState.phase == ModpackInstallPhase.INSTALLING
        Dialog(onDismissRequest = {
            // 下载/安装进行中不允许直接关闭（与版本下载弹窗一致）
            if (!modpackRunning) { modpackController.dismiss(); modpackState = modpackState.copy(visible = false) }
        }) {
            ModpackInstallDialog(
                state = modpackState,
                onCancel = { cancelModpackInstall() },
                onRetry = { retryModpackInstall() },
                onClose = { modpackController.dismiss(); modpackState = modpackState.copy(visible = false) }
            )
        }
    }
}
