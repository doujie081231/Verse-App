package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonParser
import com.verse.explorer.app.game.LoaderTypeResolver
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.RoseLoader
import com.verse.explorer.app.ui.components.VerseProgressBar
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.install.loader.FclLoaderInstaller
import com.verse.explorer.core.mod.ModLoader
import com.verse.explorer.core.mod.ModLoaderManager
import com.verse.explorer.core.mod.ModLoaderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 给已装实例安装/更换模组加载器。
 *
 * 实现方式是「在基础版本上新建一个加载器版本」（例如 `1.20.1-forge-47.2.0`），
 * **原版本目录原样保留**，不会原地改写玩家的实例——这也是 FCL 安装器的行为，
 * 出错时可随时切回原版本。
 *
 * 只开放安装器真正支持的五种（Forge / NeoForge / Fabric / Quilt / OptiFine）；
 * Cleanroom 底层 `FclLoaderInstaller` 未实现，这里置灰而不是点了才报错。
 */
@Composable
internal fun LoaderInstallTab(
    versionId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val gameDir = remember { GameConfig.getGameDir(context) }
    val (baseVersionId, currentLoader) = remember(versionId) {
        val jsonFile = File(File(GameConfig.getVersionsDir(gameDir), versionId), "$versionId.json")
        val json = runCatching {
            JsonParser.parseString(jsonFile.readText()).asJsonObject
        }.getOrNull()
        val info = LoaderTypeResolver.resolve(json, versionId)
        info.baseVersionId to info.loaderType
    }

    val loaderManager = remember { ModLoaderManager() }
    var selectedType by remember { mutableStateOf<ModLoaderType?>(null) }
    var versions by remember { mutableStateOf<List<ModLoader>>(emptyList()) }
    var versionLoading by remember { mutableStateOf(false) }
    var versionError by remember { mutableStateOf<String?>(null) }
    var selectedVersion by remember { mutableStateOf<String?>(null) }

    var installing by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var progressValue by remember { mutableStateOf(0f) }
    var resultText by remember { mutableStateOf<String?>(null) }

    // 切换加载器即拉取该加载器对当前基础版本的可用版本
    LaunchedEffect(selectedType, baseVersionId) {
        val type = selectedType ?: return@LaunchedEffect
        versionLoading = true
        versionError = null
        versions = emptyList()
        selectedVersion = null
        runCatching {
            withContext(Dispatchers.IO) {
                loaderManager.fetchAvailableVersions(type, baseVersionId)
            }
        }.onSuccess {
            versions = it
            selectedVersion = it.firstOrNull { v -> v.stable }?.version ?: it.firstOrNull()?.version
            if (it.isEmpty()) versionError = "该加载器没有适用于 $baseVersionId 的版本"
        }.onFailure {
            versionError = "获取版本列表失败：${it.message ?: "网络错误"}"
        }
        versionLoading = false
    }

    fun startInstall() {
        val type = selectedType ?: return
        val loaderVersion = selectedVersion ?: return
        val targetId = LoaderTypeResolver.targetVersionId(baseVersionId, type.apiId, loaderVersion)
        val targetDir = File(GameConfig.getVersionsDir(gameDir), targetId)

        resultText = null
        progressText = "准备安装…"
        progressValue = 0f
        installing = true
        scope.launch {
            // 已存在的同名加载器版本不重复安装，避免覆盖玩家可能已调好的配置
            if (targetDir.exists()) {
                installing = false
                resultText = "版本 $targetId 已存在，请直接在版本列表中切换使用"
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                FclLoaderInstaller.installFcl(
                    gameDir = gameDir,
                    versionId = baseVersionId,
                    loaderType = type.apiId,
                    loaderVersion = loaderVersion,
                    baseVersionId = baseVersionId,
                    newVersionId = targetId,
                    progress = { message, value ->
                        progressText = message
                        progressValue = value.coerceIn(0f, 1f)
                    }
                )
            }
            installing = false
            result.onSuccess {
                progressValue = 1f
                resultText = "已创建版本 $targetId，可在版本列表中切换使用"
            }.onFailure {
                resultText = "安装失败：${it.message ?: "未知错误"}"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PcCard(title = "当前版本") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "版本：$versionId",
                    fontSize = 12.sp,
                    color = VerseColors.PcTextPrimary
                )
                Text(
                    "基础版本：$baseVersionId · 已装加载器：${LoaderTypeResolver.displayName(currentLoader)}",
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted
                )
                Text(
                    "安装会在基础版本上新建一个加载器版本，原版本与存档不受影响。",
                    fontSize = 11.sp,
                    color = VerseColors.PcTextMuted
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        PcCard(title = "选择加载器") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ModLoaderType.FORGE,
                    ModLoaderType.NEOFORGE,
                    ModLoaderType.FABRIC,
                    ModLoaderType.QUILT,
                    ModLoaderType.OPTIFINE
                ).forEach { type ->
                    LoaderChip(
                        text = type.displayName,
                        selected = selectedType == type,
                        enabled = !installing,
                        onClick = { selectedType = type }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Cleanroom 仅支持识别，暂不提供自动安装。",
                fontSize = 10.sp,
                color = VerseColors.PcTextMuted
            )
        }

        Spacer(Modifier.height(10.dp))

        if (installing) {
            VerseProgressBar(
                progress = progressValue,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                height = 6.dp,
                color = VerseColors.PcAccent
            )
            Text(progressText, fontSize = 11.sp, color = VerseColors.PcTextMuted)
            Spacer(Modifier.height(8.dp))
        }

        resultText?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = if (it.startsWith("安装失败")) VerseColors.PcOrange else VerseColors.PcAccent
            )
            Spacer(Modifier.height(8.dp))
        }

        when {
            selectedType == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("请选择要安装的加载器", fontSize = 13.sp, color = VerseColors.PcTextMuted)
            }

            versionLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                RoseLoader(modifier = Modifier.size(28.dp), size = 28.dp, color = VerseColors.PcAccent)
            }

            versionError != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(versionError!!, fontSize = 12.sp, color = VerseColors.PcTextMuted)
            }

            else -> {
                Text(
                    "可选版本（${versions.size}）",
                    modifier = Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = VerseColors.PcTextMuted
                )
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(versions, key = { "${it.type.apiId}-${it.version}" }) { loader ->
                        LoaderVersionRow(
                            loader = loader,
                            selected = selectedVersion == loader.version,
                            enabled = !installing,
                            onClick = { selectedVersion = loader.version }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        val targetHint = selectedType?.let { type ->
            selectedVersion?.let { v ->
                LoaderTypeResolver.targetVersionId(baseVersionId, type.apiId, v)
            }
        }
        PcButton(
            modifier = Modifier.fillMaxWidth(),
            text = if (targetHint != null) "安装为新版本 $targetHint" else "请选择加载器与版本",
            onClick = { startInstall() },
            enabled = !installing && targetHint != null,
            variant = PcButtonVariant.PRIMARY
        )
    }
}

@Composable
private fun LoaderChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VerseColors.PcAccent.copy(alpha = 0.18f) else VerseColors.PcBgCard,
        border = BorderStroke(
            1.dp,
            if (selected) VerseColors.PcAccent else VerseColors.PcBorder
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 12.sp,
            color = when {
                !enabled -> VerseColors.PcTextMuted
                selected -> VerseColors.PcAccent
                else -> VerseColors.PcTextSecondary
            }
        )
    }
}

@Composable
private fun LoaderVersionRow(
    loader: ModLoader,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VerseColors.PcAccent.copy(alpha = 0.12f) else VerseColors.PcBgCard,
        border = BorderStroke(
            1.dp,
            if (selected) VerseColors.PcAccent else VerseColors.PcBorder
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = loader.version,
                fontSize = 13.sp,
                color = VerseColors.PcTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!loader.stable) {
                Text(
                    "测试版",
                    fontSize = 10.sp,
                    color = VerseColors.PcOrange
                )
            }
        }
    }
}
