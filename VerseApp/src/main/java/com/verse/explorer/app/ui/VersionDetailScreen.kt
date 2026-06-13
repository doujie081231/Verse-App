package com.verse.explorer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.VersionsViewModel
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.install.InstallStage
import com.verse.explorer.core.version.VersionType
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class DetailLoaderOption(
    val id: String,
    val name: String,
    val desc: String,
    val iconAssetPath: String
)

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

    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    val gson = remember { GsonBuilder().setLenient().create() }

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
                        "fabric" -> fetchFabricVersions(versionId, httpClient, gson)
                        "forge" -> fetchForgeVersions(versionId, httpClient, gson)
                        "neoforge" -> fetchNeoForgeVersions(versionId, httpClient, gson)
                        "quilt" -> fetchQuiltVersions(versionId, httpClient, gson)
                        "optifine" -> fetchOptiFineVersions(versionId, httpClient, gson)
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
            .background(VerseColors.McBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                color = VerseColors.McBg,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.size(20.dp), VerseColors.McTextPrimary)
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
                            color = VerseColors.McTextPrimary
                        )
                        Text(
                            getVersionTypeLabel(versionType),
                            fontSize = 12.sp,
                            color = VerseColors.McTextMuted
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
                            color = VerseColors.McGreen,
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
                                    color = VerseColors.McGreen,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("正在获取版本列表...", fontSize = 13.sp, color = VerseColors.McTextMuted)
                            }
                        }
                    }
                }

                if (loaderError != null) {
                    item {
                        Text(loaderError!!, color = VerseColors.McRedstone, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                    }
                }

                if (installState.isInstalling && installState.versionId == versionId) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VerseColors.McGreen.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        Modifier.size(16.dp),
                                        color = VerseColors.McGreen,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "正在下载 $versionId",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = VerseColors.McGreen
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                if (installState.message.isNotEmpty()) {
                                    Text(
                                        installState.message,
                                        fontSize = 12.sp,
                                        color = VerseColors.McTextMuted
                                    )
                                }
                                if (installState.progress > 0) {
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { installState.progress },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = VerseColors.McGreen,
                                        trackColor = VerseColors.McGreen.copy(alpha = 0.15f),
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${(installState.progress * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = VerseColors.McTextMuted
                                    )
                                }
                                if (installState.currentFile.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        installState.currentFile,
                                        fontSize = 10.sp,
                                        color = VerseColors.McTextDim,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                if (installState.isCompleted && installState.versionId == versionId) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VerseColors.McGreen.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), VerseColors.McGreen)
                                Spacer(Modifier.width(8.dp))
                                Text("$versionId 下载完成！", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = VerseColors.McGreen)
                            }
                        }
                    }
                }

                if (installState.isFailed && installState.versionId == versionId) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VerseColors.McRedstone.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Error, null, Modifier.size(18.dp), VerseColors.McRedstone)
                                    Spacer(Modifier.width(8.dp))
                                    Text("下载失败", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = VerseColors.McRedstone)
                                }
                                if (installState.error != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(installState.error!!, fontSize = 12.sp, color = VerseColors.McRedstone)
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            // Footer
            Surface(
                color = VerseColors.McBg,
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
                                color = VerseColors.McTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (installState.progress > 0f) {
                                Text(
                                    text = "${(installState.progress * 100).toInt()}%",
                                    color = VerseColors.McGreen,
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
                            color = VerseColors.McGreen,
                            trackColor = VerseColors.McCardBgLight,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = installState.message.ifEmpty { "准备中..." },
                            color = VerseColors.McTextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                        if (installState.currentFile.isNotEmpty()) {
                            Text(
                                text = installState.currentFile,
                                color = VerseColors.McTextMuted,
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VerseColors.McRedstone),
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VerseColors.McTextSecondary),
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
                            colors = ButtonDefaults.buttonColors(containerColor = VerseColors.McGreen),
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

@Composable
fun VersionTypeIcon(versionType: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bg = when {
        versionType.equals("snapshot", ignoreCase = true) -> Color(0xFFFF9800).copy(alpha = 0.15f)
        versionType.contains("old", ignoreCase = true) -> Color(0xFF9E9E9E).copy(alpha = 0.15f)
        else -> Color(0xFF4CAF50).copy(alpha = 0.12f)
    }
    val iconPath = when {
        versionType.equals("snapshot", ignoreCase = true) -> "icons/version_icon_cobblestone.png"
        versionType.contains("old", ignoreCase = true) -> "icons/version_icon_old.png"
        else -> "icons/version_icon_grass.png"
    }
    val iconBitmap = remember(iconPath) {
        try {
            context.assets.open(iconPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun VersionDetailSectionTitle(title: String) {
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = VerseColors.McTextPrimary
    )
}

@Composable
fun DownloadSourceOption(
    id: String,
    name: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VerseColors.McGreen.copy(alpha = 0.06f) else VerseColors.McCardBg,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, VerseColors.McGreen.copy(alpha = 0.4f))
        else androidx.compose.foundation.BorderStroke(1.dp, VerseColors.McCardBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = VerseColors.McGreen)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.McTextPrimary)
                Text(desc, fontSize = 11.sp, color = VerseColors.McTextMuted)
            }
            if (selected) {
                Icon(Icons.Filled.Check, null, Modifier.size(16.dp), VerseColors.McGreen)
            }
        }
    }
}

@Composable
fun LoaderCard(
    loader: DetailLoaderOption,
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(loader.iconAssetPath) {
        try {
            context.assets.open(loader.iconAssetPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VerseColors.McGreen.copy(alpha = 0.06f) else VerseColors.McCardBg,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, VerseColors.McGreen.copy(alpha = 0.4f))
        else androidx.compose.foundation.BorderStroke(1.dp, VerseColors.McCardBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = VerseColors.McGreen,
                        strokeWidth = 2.dp
                    )
                } else if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(loader.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = VerseColors.McTextPrimary)
                Text(loader.desc, fontSize = 11.sp, color = VerseColors.McTextMuted)
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), VerseColors.McGreen)
            } else {
                Box(
                    Modifier
                        .size(20.dp)
                        .border(1.5.dp, VerseColors.McCardBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {}
            }
        }
    }
}

@Composable
fun LoaderVersionCard(
    loader: DetailLoaderOption,
    version: LoaderVersion,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(loader.iconAssetPath) {
        try {
            context.assets.open(loader.iconAssetPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VerseColors.McGreen.copy(alpha = 0.06f) else VerseColors.McCardBg,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, VerseColors.McGreen.copy(alpha = 0.4f))
        else androidx.compose.foundation.BorderStroke(1.dp, VerseColors.McCardBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${loader.name} ${version.version}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = VerseColors.McTextPrimary
                )
                if (version.type.isNotEmpty()) {
                    Text(version.type, fontSize = 10.sp, color = VerseColors.McTextMuted)
                }
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), VerseColors.McGreen)
            } else {
                Box(
                    Modifier
                        .size(18.dp)
                        .border(1.5.dp, VerseColors.McCardBorder, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {}
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

data class LoaderVersion(
    val version: String,
    val type: String = ""
)

private fun fetchFabricVersions(mcVersion: String, client: OkHttpClient, gson: com.google.gson.Gson): List<LoaderVersion> {
    val urls = listOf(
        "https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader/$mcVersion",
        "https://meta.fabricmc.net/v2/versions/loader/$mcVersion",
        "https://bmclapi.nofated.win/fabric-meta/v2/versions/loader/$mcVersion"
    )
    for (url in urls) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (body != null) {
                        val list = gson.fromJson(body, Array<FabricLoaderMeta>::class.java)
                        if (list.isNotEmpty()) return list.map { LoaderVersion(it.loader.version, "release") }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return emptyList()
}

private fun fetchForgeVersions(mcVersion: String, client: OkHttpClient, gson: com.google.gson.Gson): List<LoaderVersion> {
    val urls = listOf(
        "https://bmclapi2.bangbang93.com/forge/minecraft/$mcVersion",
        "https://bmclapi.nofated.win/forge/minecraft/$mcVersion"
    )
    for (url in urls) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (body != null) {
                        val list = gson.fromJson(body, Array<ForgeRaw>::class.java)
                        if (list.isNotEmpty()) return list.map { LoaderVersion(it.version, if (it.type == "latest") "release" else "beta") }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return emptyList()
}

private fun fetchNeoForgeVersions(mcVersion: String, client: OkHttpClient, gson: com.google.gson.Gson): List<LoaderVersion> {
    val urls = listOf(
        "https://bmclapi2.bangbang93.com/neoforge/list?mcversion=$mcVersion",
        "https://bmclapi.nofated.win/neoforge/list?mcversion=$mcVersion"
    )
    for (url in urls) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (body != null) {
                        val list = gson.fromJson(body, Array<NeoForgeEntry>::class.java)
                        if (list.isNotEmpty()) return list.map { LoaderVersion(it.version, "release") }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return emptyList()
}

private fun fetchQuiltVersions(mcVersion: String, client: OkHttpClient, gson: com.google.gson.Gson): List<LoaderVersion> {
    val urls = listOf(
        "https://bmclapi2.bangbang93.com/quilt-meta/v3/versions/loader/$mcVersion",
        "https://meta.quiltmc.org/v3/versions/loader/$mcVersion",
        "https://bmclapi.nofated.win/quilt-meta/v3/versions/loader/$mcVersion"
    )
    for (url in urls) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (body != null) {
                        val list = gson.fromJson(body, Array<FabricLoaderMeta>::class.java)
                        if (list.isNotEmpty()) return list.map { LoaderVersion(it.loader.version, "release") }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return emptyList()
}

private fun fetchOptiFineVersions(mcVersion: String, client: OkHttpClient, gson: com.google.gson.Gson): List<LoaderVersion> {
    val urls = listOf(
        "https://bmclapi2.bangbang93.com/optifine/$mcVersion",
        "https://bmclapi.nofated.win/optifine/$mcVersion"
    )
    for (url in urls) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (body != null) {
                        val list = gson.fromJson(body, Array<OptiFineEntry>::class.java)
                        if (list.isNotEmpty()) return list.map { LoaderVersion(it.fileName.removePrefix("OptiFine_").removeSuffix(".jar"), "release") }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return emptyList()
}

data class FabricLoaderMeta(val loader: FabricLoaderMetaInner)
data class FabricLoaderMetaInner(val version: String)
data class ForgeRaw(val version: String, val type: String)
data class NeoForgeEntry(val version: String)
data class OptiFineEntry(val fileName: String)