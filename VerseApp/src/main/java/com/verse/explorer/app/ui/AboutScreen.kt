package com.verse.explorer.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

/** 官方爱发电主页，与官网 verselauncher.cn 上挂的是同一个地址 */
private const val SPONSOR_URL = "https://ifdian.net/a/versejava"

@Composable
fun AboutContent() {
    val context = LocalContext.current

    /**
     * 打开外部链接。之前失败时静默吞掉异常，表现就是「点了没反应」；
     * 这里补上 NEW_TASK 标志（非 Activity 上下文也能启动），
     * 并在真的没有应用能处理时给出提示。
     */
    fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "没有可以打开该链接的应用", Toast.LENGTH_SHORT).show()
        }
    }

    PcCard(title = "版本") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Verse App",
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

        // 赞助支持：并入「致谢」区块，使用爱发电官方图标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VerseColors.PcBgCard)
                .clickable { openLink(SPONSOR_URL) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.credit_afdian),
                contentDescription = "爱发电",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "爱发电",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.PcTextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "赞助支持渠道 · 服务器与开发都靠赞助者撑着",
                    fontSize = 11.sp,
                    color = VerseColors.PcTextSecondary,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "打开爱发电主页",
                tint = VerseColors.PcTextMuted,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 1.dp, color = VerseColors.PcBorderSubtle)

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

    PcCard(title = "安装包校验") {
        Text(
            "官方安装包的签名证书 SHA-256 指纹如下。若你从其他渠道下载的包指纹不同，说明已被重新打包，请勿安装。",
            fontSize = 11.sp,
            color = VerseColors.PcTextMuted,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = OFFICIAL_CERT_SHA256,
            fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = VerseColors.PcTextSecondary,
            lineHeight = 15.sp
        )
    }

    Spacer(Modifier.height(16.dp))

    PcCard(title = "引用的开源组件") {
        Text(
            "以下组件按各自许可分发，著作权归原作者所有。点击可打开项目主页。",
            fontSize = 11.sp,
            color = VerseColors.PcTextMuted,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        openSourceCredits.forEachIndexed { index, item ->
            CreditLine(
                title = item.name,
                subtitle = "${item.license} · ${item.usage}",
                link = item.link
            )
            if (index != openSourceCredits.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = VerseColors.PcBorderSubtle)
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    PcCard(title = "下载源与运行组件") {
        Text(
            "本应用不自建游戏文件与内容分发服务，以下服务与运行时组件构成了实际可用的下载与运行链路。",
            fontSize = 11.sp,
            color = VerseColors.PcTextMuted,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        serviceCredits.forEachIndexed { index, item ->
            CreditLine(
                title = item.name,
                subtitle = item.usage,
                link = item.link
            )
            if (index != serviceCredits.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = VerseColors.PcBorderSubtle)
            }
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

/** 官方签名证书指纹（与「发布流程.md → 4.1」保持一致，发版时核对） */
private const val OFFICIAL_CERT_SHA256 =
    "83:25:EF:10:56:9F:FA:E5:60:AD:1D:42:B8:D7:E0:B1:2B:B6:8B:6B:2A:2E:E0:9A:F5:9B:8A:3A:EB:9C:E7:83"

/** 一条许可/来源说明 */
private data class CreditEntry(
    val name: String,
    val license: String,
    val usage: String,
    val link: String
)

/** 下载源与运行组件（第二部分只需要名称与用途） */
private data class ServiceEntry(
    val name: String,
    val usage: String,
    val link: String
)

@Composable
private fun CreditLine(title: String, subtitle: String, link: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    com.verse.explorer.app.util.IntentHelper.openUrl(context, link)
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = VerseColors.PcTextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 10.sp,
                color = VerseColors.PcTextMuted,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = VerseColors.PcTextMuted,
            modifier = Modifier.size(13.dp)
        )
    }
}

/** 实际引用的开源组件（与 build.gradle 中的依赖一一对应） */
private val openSourceCredits = listOf(
    CreditEntry("AndroidX / Jetpack Compose", "Apache-2.0", "界面框架与状态管理", "https://developer.android.com/jetpack/androidx"),
    CreditEntry("Kotlin & kotlinx-coroutines", "Apache-2.0", "开发语言与协程", "https://github.com/Kotlin/kotlinx.coroutines"),
    CreditEntry("OkHttp", "Apache-2.0", "网络请求与镜像重试", "https://square.github.io/okhttp/"),
    CreditEntry("Gson", "Apache-2.0", "版本清单与接口数据解析", "https://github.com/google/gson"),
    CreditEntry("Coil", "Apache-2.0", "模组图标与图片加载", "https://github.com/coil-kt/coil"),
    CreditEntry("LWJGL 3", "BSD-3-Clause", "游戏窗口、输入与图形绑定", "https://www.lwjgl.org/"),
    CreditEntry("authlib-injector", "AGPL-3.0", "外置登录（皮肤站）端点改写", "https://github.com/yushijinhun/authlib-injector"),
    CreditEntry("PojavLauncher / FCL 派生代码", "GPL-3.0", "Android 端启动与 JVM 适配基础", "https://github.com/FCL-Team/FoldCraftLauncher"),
    CreditEntry("material-motion-compose", "Apache-2.0", "页面转场动效", "https://github.com/fornewid/material-motion-compose"),
    CreditEntry("backdrop / shapes (Kyant0)", "Apache-2.0", "悬浮导航的液态玻璃效果", "https://github.com/Kyant0/AndroidLiquidGlass"),
    CreditEntry("MaterialKolor", "MIT", "主题配色生成", "https://github.com/jordond/MaterialKolor"),
    CreditEntry("compose-shimmer", "Apache-2.0", "列表骨架屏", "https://github.com/valentinilk/compose-shimmer"),
    CreditEntry("Compottie", "MIT", "空态 Lottie 动效", "https://github.com/alexzhirkevich/compottie"),
    CreditEntry("ConfettiKit", "MIT", "安装成功彩带", "https://github.com/vinceglb/ConfettiKit"),
    CreditEntry("Reorderable", "Apache-2.0", "列表拖拽排序", "https://github.com/calvin-ll/Reorderable"),
    CreditEntry("AndroidSVG", "Apache-2.0", "矢量图标渲染", "https://github.com/bigfish123/androidsvg"),
    CreditEntry("Apache Commons Compress", "Apache-2.0", "整合包解压", "https://commons.apache.org/proper/commons-compress/"),
    CreditEntry("XZ for Java", "Public Domain", "xz 压缩包解压", "https://tukaani.org/xz/java.html")
)

/** 下载源与运行组件 */
private val serviceCredits = listOf(
    ServiceEntry("Mojang 官方资源服务", "游戏版本清单、客户端与库文件", "https://www.minecraft.net/"),
    ServiceEntry("BMCLAPI 镜像", "国内加速下载游戏文件", "https://bmclapi2.bangbang93.com/"),
    ServiceEntry("Modrinth", "模组 / 整合包 / 材质包 / 光影内容与检索", "https://modrinth.com/"),
    ServiceEntry("CurseForge", "模组与整合包内容（含 CDN 分发）", "https://www.curseforge.com/"),
    ServiceEntry("Microsoft / Xbox 认证", "正版账户登录", "https://login.live.com/"),
    ServiceEntry("MobileGlues", "渲染器插件（GL 转译，按需安装）", "https://github.com/MobileGLues/MobileGlues-release"),
    ServiceEntry("Mesa（Zink / Kopper / VirGL）", "OpenGL / Vulkan 驱动层", "https://www.mesa3d.org/")
)
