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
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.version.LoaderRepository
import com.verse.explorer.app.domain.model.version.*
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
    val viewModel = remember { VersionsViewModel(context.applicationContext as android.app.Application) }
    val installState by viewModel.installState.collectAsState()

    var selectedSource by remember { mutableStateOf("mojang") }
    var selectedLoader by remember { mutableStateOf("") }
    var selectedLoaderVersion by remember { mutableStateOf<String?>(null) }
    var loaderVersions by remember { mutableStateOf<List<LoaderVersion>>(emptyList()) }
    var loadingLoader by remember { mutableStateOf(false) }
    var loaderError by remember { mutableStateOf<String?>(null) }
    var isLoadingVersions by remember { mutableStateOf(false) }
    var versionInfoText by remember { mutableStateOf("") }

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
            .background(VerseColors.PcBgSecondary)
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
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.size(20.dp), VerseColors.PcTextPrimary)
                    }
                    Spacer(Modifier.width(8.dp))
                    VersionTypeIcon(
                        versionType = versionType,
                        modifier = Modifier.size(40.dp)
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
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    color = VerseColors.PcAccent,
                                    strokeWidth = 2.dp
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
                        // PC端风格进度信息
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when (installState.stage) {
                                    InstallStage.PREPARING -> "准备安装..."
                                    InstallStage.DOWNLOADING_VERSION -> "下载版本信息"
                                    InstallStage.DOWNLOADING_CLIENT -> "下载游戏客户端"
                                    InstallStage.DOWNLOADING_LIBRARIES -> "下载依赖库"
                                    InstallStage.DOWNLOADING_ASSETS -> "下载资源文件"
                                    InstallStage.EXTRACTING_NATIVES -> "解压原生库"
                                    InstallStage.COMPLETED -> "安装完成"
                                    InstallStage.FAILED -> "安装失败"
                                    else -> "处理中..."
                                },
                                color = VerseColors.PcTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (installState.progress > 0f) {
                                Text(
                                    text = "${(installState.progress * 100).toInt()}%",
                                    color = VerseColors.PcAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { installState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = VerseColors.PcAccent,
                            trackColor = VerseColors.PcBgHover,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = installState.message.ifEmpty { "准备中..." },
                            color = VerseColors.PcTextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                        if (installState.currentFile.isNotEmpty()) {
                            Text(
                                text = installState.currentFile,
                                color = VerseColors.PcTextMuted,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(6.dp))
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
                                viewModel.installVersion(
                                    com.verse.explorer.core.version.MinecraftVersion(
                                        id = versionId,
                                        type = VersionType.entries.find { it.name.equals(versionType, ignoreCase = true) } ?: VersionType.RELEASE,
                                        url = versionUrl,
                                        releaseTime = ""
                                    ),
                                    if (selectedLoader.isNotEmpty()) selectedLoader else null,
                                    selectedLoaderVersion
                                )
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VerseColors.PcAccent),
                            enabled = !isDownloading
                        ) {
                            Text(if (isDownloading) "下载中..." else "确认下载", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

fun getVersionTypeLabel(type: String): String {
    return when {
        type.equals("snapshot", ignoreCase = true) -> "快照版"
        type.equals("old_beta", ignoreCase = true) -> "旧测试版"
        type.equals("old_alpha", ignoreCase = true) -> "旧内测版"
        else -> "正式版"
    }
}
