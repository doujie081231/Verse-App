package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.verse.explorer.core.data.version.LoaderRepository
import com.verse.explorer.core.domain.model.version.*
import com.verse.explorer.app.ui.components.InstallFlowCard
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.VersionNamingDialog
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.VersionsViewModel
import com.verse.explorer.core.install.InstallStage
import com.verse.explorer.core.version.VersionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val detailLoaderOptions = listOf(
    DetailLoaderOption("", "不安装（原版）", "纯净的Minecraft，无任何模组加载器", "icons/loader_icon_vanilla.png"),
    DetailLoaderOption("forge", "Forge", "经典模组加载器，兼容性强", "icons/loader_icon_forge.png"),
    DetailLoaderOption("neoforge", "NeoForge", "新一代模组加载器，性能更优", "icons/loader_icon_neoforge.png"),
    DetailLoaderOption("fabric", "Fabric", "轻量级模组加载器，更新快", "icons/loader_icon_fabric.png"),
    DetailLoaderOption("quilt", "Quilt", "Fabric 的分支，更好的兼容性", "icons/loader_icon_quilt.png"),
    DetailLoaderOption("optifine", "OptiFine", "高清修复与性能优化", "icons/loader_icon_optifine.png")
)

@Composable
fun VersionDetailScreen(
    versionId: String,
    versionType: String,
    versionUrl: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val viewModel = VersionsViewModel.shared(context.applicationContext as android.app.Application)
    val installState by viewModel.installState.collectAsState()

    var selectedSource by remember { mutableStateOf("mojang") }
    var selectedLoader by remember { mutableStateOf("") }
    var selectedLoaderVersion by remember { mutableStateOf<String?>(null) }
    var loaderVersions by remember { mutableStateOf<List<LoaderVersion>>(emptyList()) }
    var loadingLoader by remember { mutableStateOf(false) }
    var loaderError by remember { mutableStateOf<String?>(null) }
    var isLoadingVersions by remember { mutableStateOf(false) }
    var versionInfoText by remember { mutableStateOf("") }
    var showNamingDialog by remember { mutableStateOf(false) }
    var confirmedInstallName by remember { mutableStateOf<String?>(null) }
    /** 移动网络确认（开始下载前确认，避免无意消耗流量） */
    var showMobileDataConfirm by remember { mutableStateOf(false) }
    var pendingInstallName by remember { mutableStateOf<String?>(null) }

    /**
     * 安装结果弹窗：完成弹「版本安装完成」，失败弹错误提示。
     * 「已提示」记在共享 ViewModel 里——只按本地状态判断的话，重新进入安装页会立刻再弹一次。
     */
    var installResult by remember { mutableStateOf<InstallResultUi?>(null) }

    LaunchedEffect(
        installState.versionId,
        installState.isInstalling,
        installState.isCompleted,
        installState.isFailed,
        installState.resultNotified
    ) {
        if (installState.versionId != versionId) return@LaunchedEffect
        if (installState.resultNotified) return@LaunchedEffect
        val name = installState.installName.ifBlank { versionId }
        when {
            installState.isCompleted -> {
                viewModel.markInstallResultNotified()
                installResult = InstallResultUi(success = true, versionName = name, message = null)
            }
            installState.isFailed -> {
                viewModel.markInstallResultNotified()
                installResult = InstallResultUi(
                    success = false,
                    versionName = name,
                    message = installState.error?.takeIf { it.isNotBlank() }
                )
            }
        }
    }

    /** 下发版本下载任务（命名确认或移动网络确认后调用） */
    fun beginInstall(name: String) {
        // 版本下载是独占任务：任何下载（含其他版本/整合包）进行中都不允许再开
        if (!com.verse.explorer.app.download.DownloadCenter.canStart(exclusive = true)) {
            android.widget.Toast.makeText(
                context, "有下载任务正在进行，完成后再下载版本", android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        viewModel.installVersion(
            com.verse.explorer.core.version.MinecraftVersion(
                id = versionId,
                type = VersionType.entries.find { it.name.equals(versionType, ignoreCase = true) } ?: VersionType.RELEASE,
                url = versionUrl,
                releaseTime = ""
            ),
            installName = name,
            loaderType = if (selectedLoader.isNotEmpty()) selectedLoader else null,
            loaderVersion = selectedLoaderVersion
        )
    }

    LaunchedEffect(selectedLoader) {
        if (selectedLoader.isNotEmpty()) {
            loadingLoader = true
            loaderError = null
            loaderVersions = emptyList()
            selectedLoaderVersion = null
            try {
                val loaderName = detailLoaderOptions.find { it.id == selectedLoader }?.name ?: selectedLoader
                val versions = withContext(Dispatchers.IO) {
                    when (selectedLoader) {
                        "fabric" -> LoaderRepository.fetchFabricVersions(versionId)
                        "forge" -> LoaderRepository.fetchForgeVersions(versionId)
                        "neoforge" -> LoaderRepository.fetchNeoForgeVersions(versionId)
                        "quilt" -> LoaderRepository.fetchQuiltVersions(versionId)
                        "optifine" -> LoaderRepository.fetchOptiFineVersions(versionId)
                        else -> emptyList()
                    }
                }
                loaderVersions = versions
                if (versions.isNotEmpty()) {
                    selectedLoaderVersion = versions.first().version
                    versionInfoText = "$loaderName ${versions.first().version} 可用"
                } else {
                    versionInfoText = "暂无可用版本"
                }
            } catch (e: Exception) {
                loaderError = "加载失败"
                versionInfoText = "加载失败"
            }
            loadingLoader = false
        } else {
            versionInfoText = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                color = VerseColors.PcBgSecondary,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.size(20.dp), VerseColors.PcTextPrimary)
                    }
                    Spacer(Modifier.width(8.dp))
                    VersionTypeIcon(
                        versionType = versionType,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            versionId,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = VerseColors.PcTextPrimary
                        )
                        Text(
                            getVersionTypeLabel(versionType),
                            fontSize = 12.sp,
                            color = VerseColors.PcTextMuted
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Download source section
                item {
                    VersionDetailSectionTitle("下载源")
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        DownloadSourceOption(
                            id = "mojang",
                            name = "Mojang 官方源",
                            desc = "官方直连，海外服务器",
                            selected = selectedSource == "mojang",
                            onClick = { selectedSource = "mojang" }
                        )
                        DownloadSourceOption(
                            id = "bmclapi",
                            name = "BMCLAPI 镜像",
                            desc = "BMCLAPI 国内镜像，速度更快",
                            selected = selectedSource == "bmclapi",
                            onClick = { selectedSource = "bmclapi" }
                        )
                    }
                }

                // Mod loader section
                item {
                    VersionDetailSectionTitle("模组加载器")
                    if (versionInfoText.isNotEmpty()) {
                        Text(
                            versionInfoText,
                            fontSize = 11.sp,
                            color = VerseColors.PcAccent,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        detailLoaderOptions.forEach { loader ->
                            LoaderCard(
                                loader = loader,
                                selected = selectedLoader == loader.id,
                                loading = loadingLoader && selectedLoader == loader.id,
                                onClick = { selectedLoader = loader.id }
                            )
                        }
                    }
                }

                // Loader version list
                if (selectedLoader.isNotEmpty() && loaderVersions.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        VersionDetailSectionTitle("选择版本")
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            loaderVersions.take(20).forEach { lv ->
                                LoaderVersionCard(
                                    loader = detailLoaderOptions.find { it.id == selectedLoader }!!,
                                    version = lv,
                                    selected = selectedLoaderVersion == lv.version,
                                    onClick = { selectedLoaderVersion = lv.version }
                                )
                            }
                        }
                    }
                }

                if (selectedLoader.isNotEmpty() && loadingLoader) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoseLoader(
                                    modifier = Modifier.size(18.dp),
                                    size = 18.dp,
                                    color = VerseColors.PcAccent
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("正在获取版本列表...", fontSize = 13.sp, color = VerseColors.PcTextMuted)
                            }
                        }
                    }
                }

                if (loaderError != null) {
                    item {
                        Text(loaderError!!, color = VerseColors.PcRed, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                    }
                }



                if (installState.isCompleted && installState.versionId == versionId) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VerseColors.PcGreen.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), VerseColors.PcGreen)
                                Spacer(Modifier.width(8.dp))
                                Text("$versionId 下载完成！", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = VerseColors.PcGreen)
                            }
                        }
                    }
                }

                if (installState.isFailed && installState.versionId == versionId) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VerseColors.PcRed.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Error, null, Modifier.size(18.dp), VerseColors.PcRed)
                                    Spacer(Modifier.width(8.dp))
                                    Text("下载失败", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = VerseColors.PcRed)
                                }
                                if (installState.error != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(installState.error!!, fontSize = 12.sp, color = VerseColors.PcRed)
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            // Footer
            Surface(
                color = VerseColors.PcBgSecondary,
                shadowElevation = 4.dp,
                tonalElevation = 2.dp
            ) {
                val isDownloading = installState.isInstalling && installState.versionId == versionId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (isDownloading) {
                        // 下载进度已移入弹窗安装流程卡片（InstallFlowCard），此处不再内嵌展示
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isDownloading) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.cancelInstall()
                                    viewModel.resetInstallState()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VerseColors.PcRed),
                                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                            ) {
                                Icon(Icons.Filled.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("取消下载", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VerseColors.PcTextSecondary),
                                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                            ) {
                                Text("返回", fontSize = 14.sp)
                            }
                        }
                        Button(
                            onClick = {
                                showNamingDialog = true
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VerseColors.PcAccent,
                                contentColor = VerseColors.PcAccentText
                            ),
                            enabled = !isDownloading
                        ) {
                            Text(if (isDownloading) "下载中..." else "确认下载", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    // 下载/安装进度弹窗（分阶段动画流程卡片，进度内嵌卡片已移除）
    // 最小化后弹窗收起，任务留在下载小窗；点小窗任务会跳回本页并恢复弹窗
    val minimizedKeys by com.verse.explorer.app.download.DownloadCenter.minimizedKeys.collectAsState()
    val versionInstallMinimized = minimizedKeys.any { it.startsWith("version-install:") }
    if (installState.isInstalling && installState.versionId == versionId && !versionInstallMinimized) {
        Dialog(onDismissRequest = {}) {
            InstallFlowCard(
                state = installState,
                onCancel = {
                    viewModel.cancelInstall()
                    viewModel.resetInstallState()
                },
                onRetry = {
                    viewModel.cancelInstall()
                    viewModel.resetInstallState()
                    beginInstall(confirmedInstallName ?: versionId)
                },
                onMinimize = {
                    com.verse.explorer.app.download.DownloadCenter.minimize(
                        "version-install:${installState.installName.ifBlank { installState.versionId }}"
                    )
                }
            )
        }
    }

    // 安装结果弹窗：成功给出明确完成提示，失败给出可读的错误原因与重试入口
    installResult?.let { result ->
        Dialog(onDismissRequest = {
            installResult = null
            // 失败结果关闭后清掉状态，避免回到本页时重复弹出
            if (!result.success) viewModel.resetInstallState()
        }) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VerseColors.PcBgSecondary
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (result.success) VerseColors.PcGreen else VerseColors.PcRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (result.success) "版本安装完成" else "安装失败",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (result.success) VerseColors.PcGreen else VerseColors.PcRed
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (result.success) {
                        Text(
                            text = "「${result.versionName}」已安装完成，可以返回主页开始游戏。",
                            fontSize = 13.sp,
                            color = VerseColors.PcTextPrimary
                        )
                    } else {
                        Text(
                            text = "「${result.versionName}」安装没有完成。",
                            fontSize = 13.sp,
                            color = VerseColors.PcTextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = result.message ?: "未知错误，请检查网络后重试",
                            fontSize = 12.sp,
                            color = VerseColors.PcRed
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (!result.success) {
                            OutlinedButton(
                                onClick = {
                                    installResult = null
                                    viewModel.resetInstallState()
                                    beginInstall(confirmedInstallName ?: versionId)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("重试", fontSize = 13.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Button(
                            onClick = {
                                installResult = null
                                // 失败结果关闭后清掉状态，避免回到本页时重复弹出
                                if (!result.success) viewModel.resetInstallState()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VerseColors.PcAccent,
                                contentColor = VerseColors.PcAccentText
                            )
                        ) {
                            Text(if (result.success) "完成" else "关闭", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showNamingDialog) {
        VersionNamingDialog(
            gameVersion = versionId,
            selectedLoaderId = selectedLoader.takeIf { it.isNotEmpty() },
            conflictsWith = {
                // 已安装版本重名，或带加载器时与基础原版目录（真实 MC 版本号）同名：
                // 后者会让实例 versions/<name>/ 覆盖基础版本目录，必须禁止
                viewModel.versionIdConflicts(it) ||
                    (selectedLoader.isNotEmpty() && it == versionId)
            },
            onDismiss = { showNamingDialog = false },
            onConfirm = { name ->
                showNamingDialog = false
                confirmedInstallName = name
                // 开始下载前确认移动网络，用户取消则不下发下载任务
                if (com.verse.explorer.core.util.NetworkUtils.isMobileDataConnected(context)) {
                    pendingInstallName = name
                    showMobileDataConfirm = true
                } else {
                    beginInstall(name)
                }
            }
        )
    }

    // 移动网络确认（版本下载流程）
    if (showMobileDataConfirm) {
        AlertDialog(
            onDismissRequest = {
                showMobileDataConfirm = false
                pendingInstallName = null
            },
            title = { Text("警告") },
            text = { Text("您正在使用移动数据网络，继续下载将消耗流量。") },
            confirmButton = {
                TextButton(onClick = {
                    val name = pendingInstallName
                    showMobileDataConfirm = false
                    pendingInstallName = null
                    name?.let { beginInstall(it) }
                }) {
                    Text("坚持使用", color = VerseColors.PcAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMobileDataConfirm = false
                    pendingInstallName = null
                }) {
                    Text("取消", color = VerseColors.PcTextSecondary)
                }
            }
        )
    }
}

fun getVersionTypeLabel(type: String): String {
    return when {
        type.equals("snapshot", ignoreCase = true) -> "快照版"
        type.equals("special", ignoreCase = true) -> "愚人节版"
        type.equals("old_beta", ignoreCase = true) -> "旧测试版"
        type.equals("old_alpha", ignoreCase = true) -> "旧内测版"
        else -> "正式版"
    }
}

/** 安装结果弹窗数据 */
private data class InstallResultUi(
    val success: Boolean,
    val versionName: String,
    val message: String?
)
