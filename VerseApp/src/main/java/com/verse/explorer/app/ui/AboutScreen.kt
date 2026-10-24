package com.verse.explorer.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.BuildConfig
import com.verse.explorer.app.R
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 与 GPLv3 许可证卡片。
 */

private data class CreditItem(
    val iconRes: Int,
    val displayName: String,
    val description: String,
    val link: String
)

private val creditItems = listOf(
    CreditItem(
        R.drawable.credit_pcl,
        "Plain Craft Launcher 2",
        "多线程下载与任务调度方案",
        "https://github.com/Meloong-Git/PCL"
    ),
    CreditItem(
        R.drawable.credit_fcl,
        "Fold Craft Launcher",
        "游戏启动链路与控制布局仓库体系",
        "https://github.com/FCL-Team/FoldCraftLauncher"
    ),
    CreditItem(
        R.drawable.credit_pojav,
        "PojavLauncher",
        "Android 端 Java 运行环境与游戏渲染底层方案",
        "https://github.com/PojavLauncherTeam/PojavLauncher"
    ),
    CreditItem(
        R.drawable.credit_zalith,
        "Zalith Launcher 2",
        "控制布局格式与布局编辑交互",
        "https://github.com/ZalithLauncher/ZalithLauncher2"
    ),
    CreditItem(
        R.drawable.credit_versepc,
        "VersePC 2",
        "Verse 系列的 PC 端启动器，桌面端功能与本应用保持同一体验基准",
        "https://verselauncher.cn"
    )
)

@Composable
fun AboutContent() {
    val context = LocalContext.current

    fun openLink(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    PcCard(title = "版本") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Verse",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = VerseColors.PcTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 13.sp,
                color = VerseColors.PcTextSecondary
            )
        }
        Text(
            "面向 Android 的新一代 Minecraft 启动器",
            fontSize = 12.sp,
            color = VerseColors.PcTextMuted
        )
    }

    Spacer(Modifier.height(16.dp))

    PcCard(title = "致谢") {
        Text(
            "本应用的开发离不开以下开源项目的启发与技术支持，在此表示衷心感谢！",
            fontSize = 12.sp,
            color = VerseColors.PcTextSecondary,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        creditItems.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openLink(item.link) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(item.iconRes),
                    contentDescription = item.displayName,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VerseColors.PcTextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.description,
                        fontSize = 11.sp,
                        color = VerseColors.PcTextSecondary,
                        lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "打开项目主页",
                    tint = VerseColors.PcTextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (index != creditItems.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = VerseColors.PcBorderSubtle)
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    PcCard(title = "开源许可") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openLink("https://www.gnu.org/licenses/gpl-3.0.html") }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "GNU General Public License v3.0",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "本应用基于 GPLv3 协议开源分发，点击查看完整协议文本",
                    fontSize = 11.sp,
                    color = VerseColors.PcTextSecondary,
                    lineHeight = 16.sp
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "查看协议",
                tint = VerseColors.PcTextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "感谢每一位玩家的支持与反馈",
        fontSize = 11.sp,
        color = VerseColors.PcTextMuted,
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
