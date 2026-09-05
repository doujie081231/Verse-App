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
import com.verse.explorer.app.VerseApplication
import com.verse.explorer.app.data.FavoritesRepository
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.NoInstalledVersionsDialog
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.VerseVersionItem
import com.verse.explorer.app.ui.components.VersionPickerDialog
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.install.modpack.ModpackInstaller
import com.verse.explorer.core.resource.ResourceDetail
import com.verse.explorer.core.resource.ResourceRepository
import com.verse.explorer.core.resource.ResourceVersion
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    // 整合包安装是长任务：挂到 Application 级作用域，离开详情页（Composable 销毁）不会中断
    val appScope = (context.applicationContext as VerseApplication).appScope
    val clipboardManager = LocalClipboardManager.current
    var project by remember { mutableStateOf<ResourceDetail?>(null) }
    var versions by remember { mutableStateOf<List<ResourceVersion>>(emptyList()) }
    var versionsError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedVersion by remember { mutableStateOf<ResourceVersion?>(null) }
    var retryCount by remember { mutableStateOf(0) }

    // PC 详情页 tabs：描述 / 版本 / 画廊
    var detailTab by remember { mutableStateOf("description") }
    // 版本筛选标签（对齐 PC md-vtab："" = 全部，否则分组名）
    var versionFilter by remember { mutableStateOf("") }
    var versionTabMode by remember { mutableStateOf("exact") }
    var versionTabKeys by remember { mutableStateOf<List<String>>(emptyList()) }

    // 下载状态
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadText by remember { mutableStateOf("") }
    var downloadDone by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    // 版本选择（与 ZL2 的 DownloadSingleOperation.SelectVersion 对应）
    var showVersionPicker by remember { mutableStateOf(false) }
    var showNoVersionsWarning by remember { mutableStateOf(false) }
    var installedVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }

    /** 选择器目标：main = 安装当前资源，deps = 一键安装前置模组（对应 ZL2 的依赖安装流程） */
    var pickerMode by remember { mutableStateOf("main") }

    // 画廊大图
    var galleryView by remember { mutableStateOf<String?>(null) }

    // 对齐 PC renderMdVersionTabs：exact 精确版本 / major 大版本分组，超过 9 个标签降级 major
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
        // 快照版放最前（对齐 sortFn：'快照版' 排最前）
        val sorted = ArrayList<String>()
        if (hasSnapshot) sorted.add("快照版")
        sorted.addAll(keys)
        versionTabMode = mode
        versionTabKeys = sorted
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
                // 对齐 PC renderMdVersionTabs：按 gameVersions 分组生成筛选标签（exact 超 9 个降级 major）
                computeVersionTabs(vers)
                VerseLogger.info("Versions loaded: ${vers.size}, tabs: ${versionTabKeys.size}", "ModDetail")
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

    // 对齐 PC renderMdVersionTabs 后的 LaunchedEffect
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

    fun startDownload() {
        val ver = selectedVersion ?: return
        if (downloading) return
        downloading = true
        downloadDone = false
        downloadError = null
        downloadProgress = 0f
        downloadText = "准备下载..."
        scope.launch {
            val result = repository.downloadVersion(
                projectType = projectType,
                version = ver,
                onProgress = { downloaded, total, speed ->
                    val p = if (total > 0) downloaded.toFloat() / total else 0f
                    downloadProgress = p
                    downloadText = buildString {
                        append("${formatFileSize(downloaded)}")
                        if (total > 0) append(" / ${formatFileSize(total)}")
                        if (speed > 0) append("  · ${formatSpeed(speed)}")
                    }
                }
            )
            downloading = false
            result.onSuccess { file ->
                if (projectType == "modpack") {
                    // 整合包：下载 .mrpack 完成后走 FCL 原版整合包安装链路
                    // （ModrinthInstallTask = 安装游戏本体 + 加载器 + 解压 overrides + 依赖模组）
                    // 安装是长任务，挂到 Application 级作用域：离开详情页也不会被取消
                    val gameDir = GameConfig.getGameDir(context.applicationContext)
                    downloading = true
                    downloadText = "下载完成，开始安装整合包..."
                    appScope.launch {
                        val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                        ModpackInstaller.installModpack(
                            gameDir = gameDir,
                            zipFile = file,
                            name = project?.title,
                            progress = { msg, p ->
                                uiScope.launch {
                                    downloadText = msg
                                    downloadProgress = p
                                }
                            }
                        ).onSuccess {
                            uiScope.launch {
                                downloading = false
                                downloadDone = true
                                downloadText = "整合包安装完成"
                            }
                        }.onFailure { e ->
                            uiScope.launch {
                                downloading = false
                                downloadError = e.message ?: "整合包安装失败"
                                downloadText = downloadError ?: "整合包安装失败"
                            }
                        }
                    }
                } else {
                    downloadDone = true
                    downloadText = "下载完成"
                }
            }.onFailure { e ->
                downloadError = e.message ?: "下载失败"
                downloadText = downloadError ?: "下载失败"
            }
        }
    }

    /**
     * 安装入口（与 ZL2 的 DownloadSingleOperation 流程对应）：
     * 整合包保持直接下载；模组/材质包/光影包等先弹出版本选择卡片，
     * 确认后安装到所选版本的对应文件夹（隔离安装，不写全局目录）。
     */
    fun beginInstall() {
        if (downloading) return
        if (selectedVersion == null) return
        if (projectType == "modpack") {
            startDownload()
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
     * 一键安装前置模组（与 ZL2 的依赖处理对应）：
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

    /** 将全部必需前置模组安装到选中的版本（隔离安装到各版本的 mods 文件夹） */
    fun startDepsForVersions(targetVersions: List<MinecraftVersion>) {
        if (downloading || targetVersions.isEmpty()) return
        val deps = versions.flatMap { it.dependencies }
            .distinctBy { it.projectId }
            .filter { it.projectId.isNotBlank() && it.type == "required" }
        downloading = true
        downloadDone = false
        downloadError = null
        downloadProgress = 0f
        downloadText = "准备安装前置模组..."
        val versionIds = targetVersions.map { it.id }
        scope.launch {
            var ok = 0
            var fail = 0
            deps.forEach { d ->
                runCatching {
                    val ver = repository.getVersions(d.projectId, "modrinth").firstOrNull()
                    if (ver != null) {
                        repository.downloadVersionForVersions("mod", ver, versionIds).getOrThrow()
                        ok++
                    }
                }.onFailure { fail++ }
            }
            downloading = false
            if (fail == 0) {
                downloadDone = true
                downloadText = "前置模组已安装到 ${versionIds.size} 个版本"
            } else {
                downloadError = "完成:成功 $ok / 失败 $fail"
                downloadText = downloadError ?: "下载失败"
            }
            Toast.makeText(
                context,
                if (fail == 0) "前置模组安装完成 ($ok)" else "完成:成功 $ok / 失败 $fail",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** 安装到选中的多个版本（与 ZL2 的 downloadSingleForVersions 对应） */
    fun startDownloadForVersions(targetVersions: List<MinecraftVersion>) {
        val ver = selectedVersion ?: return
        if (downloading || targetVersions.isEmpty()) return
        downloading = true
        downloadDone = false
        downloadError = null
        downloadProgress = 0f
        downloadText = "准备下载..."
        scope.launch {
            val result = repository.downloadVersionForVersions(
                projectType = projectType,
                version = ver,
                targetVersionIds = targetVersions.map { it.id },
                onProgress = { downloaded, total, speed ->
                    val p = if (total > 0) downloaded.toFloat() / total else 0f
                    downloadProgress = p
                    downloadText = buildString {
                        append("${formatFileSize(downloaded)}")
                        if (total > 0) append(" / ${formatFileSize(total)}")
                        if (speed > 0) append("  · ${formatSpeed(speed)}")
                    }
                }
            )
            downloading = false
            result.onSuccess { files ->
                downloadDone = true
                downloadText = "已安装到 ${files.size} 个版本"
            }.onFailure { e ->
                downloadError = e.message ?: "下载失败"
                downloadText = downloadError ?: "下载失败"
            }
        }
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
                    CircularProgressIndicator(color = VerseColors.PcAccent, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
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
                    // ===== 顶栏（对齐 PC .moddetail-page-header，移动端紧凑：图标 36dp、标题单行、副文本一行）=====
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
                        // 图标
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VerseColors.PcBgCard,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (!proj.iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = proj.iconUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        proj.title.firstOrNull()?.uppercase() ?: "?",
                                        color = VerseColors.PcAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                proj.title.ifEmpty { projectId },
                                color = VerseColors.PcTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // 描述 + 统计行合并为一行（移动端不占额外行高）
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    proj.description.take(56) + if (proj.description.length > 56) "…" else "",
                                    color = VerseColors.PcTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text("⬇ ${formatCount(proj.downloads)} ❤ ${formatCount(proj.followers)}",
                                    fontSize = 11.sp, color = VerseColors.PcTextMuted)
                                if (proj.dateModified.isNotBlank()) {
                                    Text("  🕐 ${formatDate(proj.dateModified)}", fontSize = 11.sp, color = VerseColors.PcTextMuted)
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

                    // ===== 操作栏（对齐 PC .md-action-bar：安装 / 收藏 / 打开源 / 复制名称）=====
                    var isFav by remember { mutableStateOf(FavoritesRepository.isFavorite(context, proj.id, source)) }
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

                    Spacer(Modifier.height(8.dp))

                    // ===== 前置依赖（对齐 PC .md-deps-section）=====
                    val deps = remember(versions) {
                        versions.flatMap { it.dependencies }
                            .distinctBy { it.projectId }
                            .filter { it.projectId.isNotBlank() }
                    }
                    var depDetails by remember {
                        mutableStateOf<Map<String, ResourceDetail>>(emptyMap())
                    }
                    LaunchedEffect(deps) {
                        val map = mutableMapOf<String, ResourceDetail>()
                        deps.forEach { d ->
                            if (d.projectId !in map) {
                                runCatching { repository.getDetail(d.projectId, "modrinth") }
                                    .onSuccess { map[d.projectId] = it }
                            }
                        }
                        depDetails = map
                    }
                    if (deps.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VerseColors.PcBgCard,
                            border = BorderStroke(1.dp, VerseColors.PcBorder),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
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
                                // 前置模组卡片（与 ZL2 的 AssetsVersionDependencyItem 对应：图标 + 标题 + 简介）
                                deps.forEach { dep ->
                                    val info = depDetails[dep.projectId]
                                    val title = info?.title?.ifEmpty { null } ?: dep.projectId
                                    val label = when (dep.type) {
                                        "required" -> "必需"
                                        "optional" -> "可选"
                                        "incompatible" -> "不兼容"
                                        else -> dep.type
                                    }
                                    val labelColor = when (dep.type) {
                                        "required" -> VerseColors.PcGreen
                                        "optional" -> VerseColors.PcOrange
                                        "incompatible" -> VerseColors.PcRed
                                        else -> VerseColors.PcTextMuted
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = VerseColors.PcBgTertiary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                try {
                                                    navController.navigate("mod_detail/${dep.projectId}?source=modrinth")
                                                } catch (_: Exception) {}
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 图标（无图标时用首字母占位，与详情页头部一致）
                                            if (!info?.iconUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = info?.iconUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(VerseColors.PcBgActive),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        title.firstOrNull()?.uppercase() ?: "?",
                                                        color = VerseColors.PcAccent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        title,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = VerseColors.PcTextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(3.dp),
                                                        color = labelColor.copy(alpha = 0.12f)
                                                    ) {
                                                        Text(
                                                            label,
                                                            fontSize = 9.sp,
                                                            color = labelColor,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                val summary = info?.description?.takeIf { it.isNotBlank() }
                                                if (summary != null) {
                                                    Text(
                                                        summary,
                                                        fontSize = 11.sp,
                                                        color = VerseColors.PcTextMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Filled.ChevronRight, null, Modifier.size(14.dp), VerseColors.PcTextMuted)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // ===== 主体：主区 + 侧栏（对齐 PC .md-layout）=====
                    Row(modifier = Modifier.fillMaxSize()) {
                        // —— 主区 ——
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            // 主区 tabs（对齐 PC .md-tabs）
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
                                    // 版本筛选 tabs（对齐 PC .md-version-tabs）
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
                                            VersionsDetailCard(
                                                version = version,
                                                isSelected = isSelected,
                                                onClick = {
                                                    selectedVersion = version
                                                    if (isSelected && !downloading) beginInstall()
                                                },
                                                selected = isSelected && !downloading && !downloadDone,
                                                downloading = isSelected && downloading,
                                                downloadProgress = if (isSelected) downloadProgress else 0f,
                                                downloadText = if (isSelected) downloadText else "",
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
                                    // 画廊（对齐 PC .md-gallery：图片网格，点击放大）
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
                                    // 描述（对齐 PC renderModDescription：body 全文；body 为空显示暂无描述）
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
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
                                                // 对齐 PC renderModDescription：body 为 CommonMark，用 Markwon 渲染富文本
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

                        // —— 侧栏（对齐 PC .md-layout-sidebar，宽度 240dp）——
                        Column(
                            modifier = Modifier
                                .width(240.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 4.dp, end = 12.dp)
                        ) {
                            MdSidebarSection("兼容性") {
                                MdSidebarTags(proj.loaders, emptyAsDash = true)
                                Spacer(Modifier.height(6.dp))
                                // 游戏版本最多 16 个（对齐 PC gvs.slice(0, 16) +N）
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
                                if (proj.dateCreated.isNotBlank()) rows.add("创建" to formatDate(proj.dateCreated))
                                if (proj.dateModified.isNotBlank()) rows.add("更新" to formatDate(proj.dateModified))
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

        // ===== 版本选择卡片（与 ZL2 的 DownloadDialog 对应）=====
        if (showVersionPicker) {
            VersionPickerDialog(
                versions = installedVersions.map {
                    VerseVersionItem(
                        id = it.id,
                        typeLabel = when (it.type) {
                            com.verse.explorer.core.version.VersionType.RELEASE -> "正式版"
                            com.verse.explorer.core.version.VersionType.SNAPSHOT -> "快照版"
                            com.verse.explorer.core.version.VersionType.OLD_ALPHA -> "旧版 Alpha"
                            com.verse.explorer.core.version.VersionType.OLD_BETA -> "旧版 Beta"
                            else -> "特殊版本"
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

    // 画廊大图（对齐 PC .md-gallery-lightbox）
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
}

/** 主区标签按钮（对齐 PC .md-tab） */
@Composable
private fun MdTabBtn(label: String, text: String, current: String, onSelect: (String) -> Unit) {
    val selected = current == label
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) VerseColors.PcAccent else VerseColors.PcBgTertiary,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onSelect(label) }
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
        )
    }
}

/** 版本筛选标签（对齐 PC .md-vtab） */
@Composable
private fun MdVTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) VerseColors.PcAccent else VerseColors.PcBgTertiary,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) VerseColors.PcAccentText else VerseColors.PcTextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** 侧栏区块（对齐 PC .md-sidebar-section） */
@Composable
private fun MdSidebarSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = VerseColors.PcTextMuted,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** 侧栏标签组（对齐 PC .md-sidebar-tags） */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MdSidebarTags(tags: List<String>, emptyAsDash: Boolean) {
    if (tags.isEmpty()) {
        if (emptyAsDash) Text("—", fontSize = 12.sp, color = VerseColors.PcTextMuted)
        return
    }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tags.forEach { tag ->
            Surface(shape = RoundedCornerShape(4.dp), color = VerseColors.PcBgTertiary) {
                Text(tag, fontSize = 10.sp, color = VerseColors.PcTextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

/** 侧栏链接（对齐 PC .md-sidebar-links） */
@Composable
private fun MdSidebarLinks(links: List<Pair<String, String>>) {
    if (links.isEmpty()) {
        Text("—", fontSize = 12.sp, color = VerseColors.PcTextMuted)
        return
    }
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        links.forEach { (label, url) ->
            Text(
                label,
                fontSize = 12.sp,
                color = VerseColors.PcAccent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "无可用浏览器", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

/** 侧栏详情行（对齐 PC .md-sidebar-detail-row） */
@Composable
private fun MdSidebarDetailRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, fontSize = 11.sp, color = VerseColors.PcTextMuted, modifier = Modifier.width(60.dp))
        Text(value, fontSize = 12.sp, color = VerseColors.PcTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ===== 版本号分组工具（对齐 PC _versionToExact / _versionToMajor / sortFn）=====

private fun versionToExact(ver: String): String? {
    if (ver.isBlank()) return null
    if (ver.contains("w") || ver.contains("snapshot")) return "快照版"
    val base = ver.split("-")[0]
    val m = Regex("""^(\d+\.\d+(?:\.\d+)?)""").find(base) ?: return null
    return m.groupValues[1]
}

private fun versionToMajor(ver: String): String? {
    if (ver.isBlank()) return null
    if (ver.contains("w") || ver.contains("snapshot")) return null
    val base = ver.split("-")[0]
    val parts = base.split(".")
    if (parts.size < 2) return null
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].toIntOrNull() ?: return null
    if (major == 1 && minor >= 14) return "1.$minor"
    if (major >= 25) return "$major.$minor"
    return null
}

private fun versionKeyComparator(): Comparator<String> = Comparator { a, b ->
    val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
    val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
    if (pa[0] != pb[0]) pb[0] - pa[0]
    else (pb.getOrNull(1) ?: 0) - (pa.getOrNull(1) ?: 0)
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}

private fun formatDate(dateStr: String): String = if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = VerseColors.PcTextMuted)
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 11.sp, color = VerseColors.PcTextMuted)
    }
}

@Composable
private fun VersionsDetailCard(
    version: ResourceVersion,
    isSelected: Boolean,
    onClick: () -> Unit,
    selected: Boolean,
    downloading: Boolean,
    downloadProgress: Float,
    downloadText: String,
    downloadDone: Boolean,
    downloadError: String?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    version.versionNumber,
                    color = if (isSelected) VerseColors.PcAccent else VerseColors.PcTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val primaryFile = version.primaryFile ?: version.files.firstOrNull()
                if (primaryFile != null) {
                    Text(
                        formatFileSize(primaryFile.size),
                        color = VerseColors.PcTextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                version.gameVersions.take(6).forEach { ver ->
                    Surface(shape = RoundedCornerShape(3.dp), color = VerseColors.PcBgSecondary) {
                        Text(ver, color = VerseColors.PcTextSecondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                version.loaders.take(3).forEach { loader ->
                    Surface(shape = RoundedCornerShape(3.dp), color = VerseColors.PcOrange.copy(alpha = 0.1f)) {
                        Text(loader, color = VerseColors.PcOrange, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            if (isSelected) {
                Spacer(Modifier.height(10.dp))
                when {
                    downloading -> {
                        Column(Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = VerseColors.PcAccent,
                                trackColor = VerseColors.PcBgSecondary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(downloadText, color = VerseColors.PcTextMuted, fontSize = 11.sp)
                        }
                    }
                    downloadDone -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = VerseColors.PcAccent.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = VerseColors.PcAccent)
                                Text(downloadText.ifBlank { "已安装到选中版本的对应文件夹" }, color = VerseColors.PcAccent, fontSize = 12.sp)
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = onDownload,
                            enabled = version.primaryFile?.url?.isNotBlank() != false,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcAccent),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("下载此版本", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (downloadError != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(downloadError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Markdown 正文渲染（对齐 PC marked 库：Modrinth body 为 CommonMark）。
 * 使用 Markwon（Android 标准 Markdown 库，内部基于 commonmark-java）在 TextView 中渲染，
 * 支持 标题/粗体/斜体/删除线/列表/引用/代码块/行内代码/链接 等 PC marked 覆盖的常用语法。
 * 解析在后台线程执行，避免大 body 阻塞 UI。
 */
@Composable
private fun MarkdownBody(
    body: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context).build()
    }
    val spanned by produceState<CharSequence>(initialValue = body, body) {
        value = try {
            withContext(Dispatchers.Default) { markwon.toMarkdown(body) }
        } catch (_: Exception) {
            // 解析失败回退纯文本（与 PC marked 异常时降级一致）
            body
        }
    }
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
            }
        },
        update = { tv ->
            tv.setTextColor(textColor.toArgb())
            tv.setTextSize(13f) // sp
            tv.setLineSpacing(0f, 1.4f)
            tv.text = spanned
        },
        modifier = modifier
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / 1024 / 1024)} MB"
    }
}

private fun formatSpeed(speed: Long): String {
    return when {
        speed < 1024 -> "$speed B/s"
        speed < 1024 * 1024 -> "${speed / 1024} KB/s"
        else -> "${"%.1f".format(speed.toDouble() / 1024 / 1024)} MB/s"
    }
}