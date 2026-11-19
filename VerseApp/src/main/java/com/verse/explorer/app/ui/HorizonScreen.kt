package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.components.navButtonAvoid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.verse.explorer.app.horizon.HorizonCatalog
import com.verse.explorer.app.horizon.HorizonPortRepository
import com.verse.explorer.app.horizon.HorizonRelease
import com.verse.explorer.app.ui.theme.VerseColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「Rust 版」页签：Horizon 安卓移植的下载 / 安装 / 启动。
 *
 * 三个入口状态：下载（首次）→ 安装（APK 就绪后交给系统安装器）→ 打开（装好后拉起移植版）。
 * 安装成功与否靠回到前台时查询 getLaunchIntentForPackage 判定——系统安装器没有可靠的结果回调。
 * 首次约 500MB 的游戏资源由移植版在自己应用内下载，与本启动器无关。
 */
@Composable
internal fun HorizonScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val deviceAbi = remember { HorizonCatalog.primaryAbi(android.os.Build.SUPPORTED_ABIS.toList()) }

    var releases by remember { mutableStateOf<List<HorizonRelease>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // 每个版本的本地下载进度；key = tag
    val progressMap = remember { mutableStateMapOf<String, Pair<Long, Long>>() }
    // 正在安装的 (tag, packageName)：回前台后检查是否真的装上了
    var pendingInstall by remember { mutableStateOf<Pair<HorizonRelease, String>?>(null) }
    var installedTag by remember { mutableStateOf(HorizonPortRepository.installedTag(context)) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        errorText = null
        val result = HorizonPortRepository.fetchReleases(deviceAbi, force = refreshKey > 0, context = context)
        releases = result
        loading = result.isEmpty()
        if (result.isEmpty()) errorText = "获取 Rust 版列表失败：请检查网络后点右上角刷新重试"
    }

    // 系统安装器把前台让出去又还回来：这时才有机会确认"装好了"
    DisposableEffect(pendingInstall) {
        val observer = LifecycleEventObserver { _, event ->
            val pending = pendingInstall ?: return@LifecycleEventObserver
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val pkg = pending.second
            val launched = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launched != null) {
                HorizonPortRepository.recordInstalled(context, pending.first, pkg)
                installedTag = pending.first.tag
                pendingInstall = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        // 顶部说明 + 刷新
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .navButtonAvoid(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Rust 版 · Horizon（Java 26.3 原生重写）",
                fontSize = 11.sp,
                color = VerseColors.PcTextMuted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "刷新",
                fontSize = 11.sp,
                color = VerseColors.PcAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !loading) { refreshKey++ }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        HorizonNoticeCard()

        Spacer(Modifier.height(8.dp))

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "正在获取 Rust 版列表...",
                        fontSize = 12.sp,
                        color = VerseColors.PcTextMuted
                    )
                }
            }
            errorText != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorText!!, fontSize = 12.sp, color = VerseColors.PcRed)
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
                ) {
                    items(releases, key = { "${it.repo}:${it.tag}" }) { release ->
                        HorizonReleaseCard(
                            release = release,
                            installed = installedTag == release.tag &&
                                HorizonPortRepository.launchIntent(context) != null,
                            progress = progressMap[release.tag],
                            onDownload = {
                                if (!progressMap.containsKey(release.tag)) {
                                    progressMap[release.tag] = 0L to release.apkSize
                                    CoroutineScope(Dispatchers.IO).launch {
                                        runCatching {
                                            HorizonPortRepository.downloadApk(context, release) { done, total ->
                                                progressMap[release.tag] = done to total
                                            }
                                        }.onSuccess { apk ->
                                            val pkg = HorizonPortRepository.resolvePackageName(context, apk)
                                            if (pkg != null && HorizonPortRepository.startInstall(context, apk)) {
                                                pendingInstall = release to pkg
                                            } else {
                                                errorText = "无法启动系统安装器，请检查存储权限"
                                            }
                                        }.onFailure { e ->
                                            errorText = "下载失败：${e.message}"
                                        }
                                        progressMap.remove(release.tag)
                                    }
                                }
                            },
                            onInstall = { apk ->
                                val pkg = HorizonPortRepository.resolvePackageName(context, apk)
                                if (pkg != null && HorizonPortRepository.startInstall(context, apk)) {
                                    pendingInstall = release to pkg
                                }
                            },
                            onOpen = {
                                HorizonPortRepository.launchIntent(context)?.let { context.startActivity(it) }
                            }
                        )
                    }
                    item {
                        Text(
                            text = "列表来自社区移植项目的 GitHub Releases，仅分发其开源构建；" +
                                "Minecraft 本体资源由移植版应用自行下载与校验。",
                            fontSize = 10.sp,
                            color = VerseColors.PcTextMuted,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 合规与预期说明：非官方项目、需要正版账号、Java 模组不运行 */
@Composable
private fun HorizonNoticeCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(VerseColors.PcBgTertiary.copy(alpha = 0.6f))
            .border(1.dp, VerseColors.PcBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            "什么是 Rust 版？",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = VerseColors.PcTextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Horizon 是社区用 Rust 从零重写的 Minecraft Java 26.3（非官方、与 Mojang/微软无关）。" +
                "安装后首次进入游戏会自动下载约 500MB 官方资源；单人模式不需要账号，" +
                "加入验证账号的服务器需要 Java Edition 正版。Fabric / Forge 模组不支持。",
            fontSize = 10.sp,
            color = VerseColors.PcTextSecondary
        )
    }
}

@Composable
private fun HorizonReleaseCard(
    release: HorizonRelease,
    installed: Boolean,
    progress: Pair<Long, Long>?,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val apkReady = remember(release.tag) {
        HorizonPortRepository.apkFile(context, release).let { it.exists() && it.length() == release.apkSize }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(VerseColors.PcBgCard)
            .border(1.dp, VerseColors.PcBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = release.sourceLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerseColors.PcTextPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = release.versionName,
                        fontSize = 11.sp,
                        color = VerseColors.PcAccent
                    )
                    if (release.experimental) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "实验性",
                            fontSize = 9.sp,
                            color = VerseColors.PcOrange,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(VerseColors.PcOrange.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatApkMeta(release),
                    fontSize = 10.sp,
                    color = VerseColors.PcTextMuted,
                    maxLines = 1
                )
            }
            HorizonActionButton(
                release = release,
                installed = installed,
                progress = progress,
                apkReady = apkReady,
                onDownload = onDownload,
                onInstall = onInstall,
                onOpen = onOpen
            )
        }

        // 下载进度条
        progress?.let { (done, total) ->
            Spacer(Modifier.height(8.dp))
            val fraction = if (total > 0) done.toFloat() / total else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VerseColors.PcBgTertiary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(VerseColors.PcAccent)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${done / (1024 * 1024)} MB / ${total / (1024 * 1024)} MB",
                fontSize = 9.sp,
                color = VerseColors.PcTextMuted
            )
        }
    }
}

@Composable
private fun HorizonActionButton(
    release: HorizonRelease,
    installed: Boolean,
    progress: Pair<Long, Long>?,
    apkReady: Boolean,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val enabled = progress == null
    val label = when {
        progress != null -> "下载中"
        installed -> "打开"
        apkReady -> "安装"
        else -> "下载"
    }
    val filled = progress == null
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (filled) VerseColors.PcBgPrimary else VerseColors.PcTextMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (filled) VerseColors.PcTextPrimary else VerseColors.PcBgTertiary.copy(alpha = 0.6f)
            )
            .clickable(enabled = enabled) {
                when {
                    installed -> onOpen()
                    apkReady -> onInstall(HorizonPortRepository.apkFile(context, release))
                    else -> onDownload()
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

private fun formatApkMeta(release: HorizonRelease): String {
    val sizeMb = release.apkSize / (1024 * 1024)
    val date = runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
            format.parse(release.publishedAt) ?: Date()
        )
    }.getOrDefault(release.publishedAt)
    return "${sizeMb}MB · $date · ${release.repo}"
}
