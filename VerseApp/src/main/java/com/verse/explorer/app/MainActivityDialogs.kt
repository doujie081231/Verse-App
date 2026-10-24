package com.verse.explorer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
internal fun EulaAgreementDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.PcBgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.72f),
            shape = RoundedCornerShape(20.dp),
            color = VerseColors.PcBgCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "用户协议与免责声明",
                    style = MaterialTheme.typography.titleLarge,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请仔细阅读以下协议，继续使用即表示您同意所有条款",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 协议内容（可滑动：竖屏小屏下内容可能超出容器高度）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .background(
                            VerseColors.PcBgHover,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = """
本软件为 Minecraft 第三方启动器，与 Mojang / Microsoft 无关。

• 本软件不会修改 Minecraft 的任何源代码
• 本软件不提供任何盗版 Minecraft 副本
• 使用本软件需要拥有合法的 Minecraft 账号
• 本软件开源免费，禁止用于商业用途
• 运行 Minecraft 需要安装 Java 运行时环境
• 下载的游戏文件来自 Mojang 官方服务器
• 用户需自行承担使用本软件的所有风险
• 开发者不对任何使用本软件造成的损失负责

继续使用即表示您已阅读并同意以上条款。
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = VerseColors.PcTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDisagree,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = VerseColors.PcTextSecondary
                        )
                    ) {
                        Text("不同意", fontSize = 14.sp)
                    }
                    Button(
                        onClick = onAgree,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VerseColors.PcAccent,
                            contentColor = VerseColors.PcAccentText
                        )
                    ) {
                        Text("同意并继续", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}


@Composable
internal fun PermissionRequestDialog(
    onRequest: () -> Unit,
    onRecheck: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.PcBgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = VerseColors.PcBgCard
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "存储权限",
                    style = MaterialTheme.typography.titleLarge,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = """
Verse 需要存储权限来：

• 保存 Minecraft 游戏文件和存档
• 管理模组、光影和资源包
• 下载和安装游戏版本

请授予"所有文件访问权限"以确保软件正常运行。
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = VerseColors.PcTextSecondary
                        )
                    ) {
                        Text("退出", fontSize = 14.sp)
                    }
                    Button(
                        onClick = onRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VerseColors.PcAccent,
                            contentColor = VerseColors.PcAccentText
                        )
                    ) {
                        Text("授予权限", fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onRecheck,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "已授权？点此重新检测",
                        fontSize = 13.sp,
                        color = VerseColors.PcAccent
                    )
                }
            }
        }
    }
}
