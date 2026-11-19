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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.horizon.HorizonCatalog
import com.verse.explorer.app.horizon.HorizonLauncher
import com.verse.explorer.app.horizon.HorizonPortRepository
import com.verse.explorer.app.horizon.HorizonRelease
import com.verse.explorer.app.ui.components.navButtonAvoid
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 「Rust 版」页签：引擎已内置 APK（见 tools/horizon_sync.py 收编脚本），
 * 这里是收编状态管理页——内嵌版本信息 + 启动入口 + 上游新版本提示。
 *
 * 游戏本体资源（约 500MB）由壳层 Setup 向导在首次启动时从 Mojang 官方端点下载并校验，
 * 与本启动器的下载管线无关。
 */
@Composable
internal fun HorizonScreen(modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current
    val meta = remember { HorizonLauncher.readMeta(context) }
    val engineOk = remember { HorizonLauncher.isEnginePackaged(context) }
    val deviceAbi = remember { HorizonCatalog.primaryAbi(android.os.Build.SUPPORTED_ABIS.toList()) }

    var upstream by remember { mutableStateOf<HorizonRelease?>(null) }
    var upstreamState by remember { mutableStateOf("正在检查上游版本...") }

    LaunchedEffect(Unit) {
        val result = HorizonPortRepository.fetchReleases(deviceAbi, context = context)
        upstream = result.firstOrNull { it.repo == "Rayan-XOO/horizon" } ?: result.firstOrNull()
        upstreamState = if (result.isEmpty()) "上游检查失败（网络）" else ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
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
                color = VerseColors.PcTextMuted
            )
        }

        Spacer(Modifier.height(8.dp))

        // 内嵌状态卡
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(VerseColors.PcBgCard)
                .border(1.dp, VerseColors.PcBorder, RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = HorizonLauncher.VERSION_TITLE,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (engineOk) "引擎已内置" else "引擎缺失",
                    fontSize = 10.sp,
                    color = if (engineOk) VerseColors.PcGreen else VerseColors.PcRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            (if (engineOk) VerseColors.PcGreen else VerseColors.PcRed).copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "引擎 ${meta?.version ?: "?"}（${meta?.tag ?: "?"}，${meta?.publishedAt ?: ""}）· ${meta?.abi ?: ""}",
                fontSize = 10.sp,
                color = VerseColors.PcTextMuted
            )
            Spacer(Modifier.height(12.dp))

            val updateHint = when {
                upstream == null -> upstreamState
                meta != null && upstream!!.versionName != meta.version ->
                    "上游有新版本 ${upstream!!.versionName}，等待收编更新"
                else -> "已是最新收编版本"
            }
            Text(
                text = updateHint,
                fontSize = 10.sp,
                color = VerseColors.PcTextMuted
            )
            Spacer(Modifier.height(14.dp))

            Text(
                text = "启动 Rust 版",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (engineOk) VerseColors.PcBgPrimary else VerseColors.PcTextMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (engineOk) VerseColors.PcTextPrimary else VerseColors.PcBgTertiary)
                    .clickable(enabled = engineOk) { HorizonLauncher.start(context) }
                    .padding(vertical = 13.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "首次启动会进入资源向导，下载约 500MB 官方资源（Mojang 官方端点，可断点续传）。",
                fontSize = 10.sp,
                color = VerseColors.PcTextMuted
            )
        }

        Spacer(Modifier.height(10.dp))

        HorizonNoticeCard()
    }
}

/** 合规与预期说明：非官方项目、需要正版账号、Java 模组不运行 */
@Composable
private fun HorizonNoticeCard() {
    val context = LocalContext.current
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
                "单人模式不需要账号，加入验证账号的服务器需要 Java Edition 正版。Fabric / Forge 模组不支持。",
            fontSize = 10.sp,
            color = VerseColors.PcTextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                text = "项目主页",
                fontSize = 10.sp,
                color = VerseColors.PcAccent,
                modifier = Modifier.clickable {
                    runCatching {
                        com.verse.explorer.app.util.IntentHelper.openUrl(
                            context, HorizonCatalog.OFFICIAL_SITE_URL
                        )
                    }
                }
            )
        }
    }
}
