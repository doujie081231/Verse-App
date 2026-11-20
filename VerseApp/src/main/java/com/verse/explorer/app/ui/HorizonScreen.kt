package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.verse.explorer.app.horizon.HorizonLauncher
import com.verse.explorer.app.horizon.HorizonPrep
import com.verse.explorer.app.ui.components.navButtonAvoid
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 「Rust 版」页签：只放一张 26.3 的版本卡片。
 *
 * 卡片结构与版本列表一致（图标块 + 名称 + 副标题 + 右侧动作），名称后带 Rust 标签：
 * - 未安装：右侧「点击下载」→ 进入准备页（派生数据 + 游戏文件，带阶段进度）
 * - 已安装：右侧「启动」→ 直接进游戏，副标题显示内嵌引擎版本
 */
@Composable
internal fun HorizonScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val meta = remember { HorizonLauncher.readMeta(context) }
    // 每次进入页签重算：文件被清掉时能自愈回「未安装」
    var installed by remember { mutableStateOf(HorizonPrep.isInstalled(context)) }
    val engineOk = remember { HorizonLauncher.isEnginePackaged(context) }

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VerseColors.PcBgCard)
                .border(1.dp, VerseColors.PcBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(VerseColors.PcTextPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "R",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerseColors.PcBgPrimary
                )
            }
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "26.3",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerseColors.PcTextPrimary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Rust",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerseColors.PcBgPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VerseColors.PcTextPrimary)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        !engineOk -> "引擎缺失，请重新安装本版本启动器"
                        installed -> "已安装 · 引擎 ${meta?.version ?: "?"}"
                        else -> "Horizon 原生重写，非官方 · 首次需准备约 700MB"
                    },
                    fontSize = 10.sp,
                    color = VerseColors.PcTextMuted,
                    maxLines = 2
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = if (installed) "启动" else "点击下载",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (engineOk) VerseColors.PcBgPrimary else VerseColors.PcTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (engineOk) VerseColors.PcTextPrimary else VerseColors.PcBgTertiary
                    )
                    .clickable(enabled = engineOk) {
                        HorizonLauncher.start(context)
                        if (installed) installed = HorizonPrep.isInstalled(context)
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
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
                "首次进入需要准备设备上的 26.3 数据（启动器内自动完成），单人不需要账号，" +
                "加入验证账号的服务器需要 Java Edition 正版。Fabric / Forge 模组不支持。",
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
                            context, com.verse.explorer.app.horizon.HorizonCatalog.OFFICIAL_SITE_URL
                        )
                    }
                }
            )
        }
    }
}
