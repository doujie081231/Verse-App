package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.theme.VerseColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSettingsScreen(
    versionId: String,
    navController: NavHostController
) {
    var showModLoaderSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.McBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = VerseColors.McTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Minecraft $versionId 设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = VerseColors.McTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 版本信息 ──
                VersionInfoCard(versionId)

                // ── 游戏目录 ──
                SettingsCard(
                    title = "游戏目录",
                    description = "独立的游戏文件夹",
                    icon = Icons.Outlined.Folder,
                    iconTint = VerseColors.McGreen,
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VerseColors.McCardBgLight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    ".minecraft-$versionId",
                                    fontSize = 11.sp,
                                    color = VerseColors.McTextSecondary
                                )
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    null,
                                    Modifier.size(14.dp),
                                    VerseColors.McTextMuted
                                )
                            }
                        }
                    }
                )

                // ── Java 设置 ──
                SettingsCard(
                    title = "Java 运行时",
                    description = "当前版本使用的 Java",
                    icon = Icons.Outlined.Code,
                    iconTint = VerseColors.McGold,
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VerseColors.McCardBgLight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Java 21",
                                    fontSize = 11.sp,
                                    color = VerseColors.McTextSecondary
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    null,
                                    Modifier.size(14.dp),
                                    VerseColors.McTextMuted
                                )
                            }
                        }
                    }
                )

                // ── 内存设置 ──
                SettingsCard(
                    title = "内存设置",
                    description = "最大/最小内存分配",
                    icon = Icons.Outlined.Memory,
                    iconTint = VerseColors.McWater,
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VerseColors.McCardBgLight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "2 GB / 512 MB",
                                    fontSize = 11.sp,
                                    color = VerseColors.McTextSecondary
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    null,
                                    Modifier.size(14.dp),
                                    VerseColors.McTextMuted
                                )
                            }
                        }
                    }
                )

                // ── 模组加载器 ──
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showModLoaderSheet = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VerseColors.McGreen.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Extension,
                                null,
                                Modifier.size(18.dp),
                                VerseColors.McGreen
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "模组加载器",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VerseColors.McTextPrimary
                            )
                            Text(
                                "安装 Fabric / Forge / NeoForge 等加载器",
                                fontSize = 11.sp,
                                color = VerseColors.McTextMuted
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            null,
                            Modifier.size(20.dp),
                            VerseColors.McTextDim
                        )
                    }
                }

                // ── JVM 参数 ──
                SettingsCard(
                    title = "JVM 参数",
                    description = "自定义此版本的虚拟机参数",
                    icon = Icons.Outlined.Terminal,
                    iconTint = VerseColors.McTextSecondary,
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VerseColors.McCardBgLight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "默认",
                                    fontSize = 11.sp,
                                    color = VerseColors.McTextMuted
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    null,
                                    Modifier.size(14.dp),
                                    VerseColors.McTextMuted
                                )
                            }
                        }
                    }
                )

                // ── 窗口设置 ──
                SettingsCard(
                    title = "窗口设置",
                    description = "分辨率与全屏模式",
                    icon = Icons.Outlined.AspectRatio,
                    iconTint = VerseColors.McGreen,
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VerseColors.McCardBgLight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "1920×1080",
                                    fontSize = 11.sp,
                                    color = VerseColors.McTextSecondary
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    null,
                                    Modifier.size(14.dp),
                                    VerseColors.McTextMuted
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ── 危险操作 ──
                SectionDivider("危险操作")

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        DangerActionRow(
                            title = "删除版本",
                            description = "永久删除 Minecraft $versionId 及其所有数据",
                            icon = Icons.Outlined.Delete,
                            actionLabel = "删除",
                            actionColor = VerseColors.McRedstone,
                            onClick = {}
                        )

                        HorizontalDivider(
                            color = VerseColors.McCardBorder,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        DangerActionRow(
                            title = "清除 Mod 数据",
                            description = "移除已安装的所有模组和数据包",
                            icon = Icons.Outlined.CleaningServices,
                            actionLabel = "清除",
                            actionColor = VerseColors.McGold,
                            onClick = {}
                        )

                        HorizontalDivider(
                            color = VerseColors.McCardBorder,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        DangerActionRow(
                            title = "重置版本配置",
                            description = "恢复此版本所有设置为默认值",
                            icon = Icons.Outlined.RestartAlt,
                            actionLabel = "重置",
                            actionColor = VerseColors.McGold,
                            onClick = {}
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ── 模组加载器选择弹窗 ──
    if (showModLoaderSheet) {
        var selectedLoader by remember { mutableStateOf("none") }

        AlertDialog(
            onDismissRequest = { showModLoaderSheet = false },
            containerColor = VerseColors.McOverlayContentBg,
            titleContentColor = VerseColors.McTextPrimary,
            textContentColor = VerseColors.McTextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text("安装模组加载器", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "选择一个模组加载器来安装到 Minecraft $versionId 上。",
                        fontSize = 12.sp,
                        color = VerseColors.McTextMuted
                    )
                    Spacer(Modifier.height(12.dp))

                    val loaders = listOf(
                        Quad("fabric", "Fabric", "轻量高效 · 主流选择", VerseColors.McGreenLight),
                        Quad("forge", "Forge", "经典强大 · 兼容性广", VerseColors.McLava),
                        Quad("neoforge", "NeoForge", "Forge 现代化分支 · 性能优秀", VerseColors.McRedstone),
                        Quad("quilt", "Quilt", "Fabric 社区分支", VerseColors.McWater),
                        Quad("none", "无加载器", "纯净原版", VerseColors.McTextSecondary),
                    )

                    loaders.forEach { (id, name, desc, color) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedLoader == id) VerseColors.McGreen.copy(alpha = 0.06f)
                                    else Color.Transparent
                                )
                                .clickable { selectedLoader = id }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLoader == id,
                                onClick = { selectedLoader = id },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = VerseColors.McGreen,
                                    unselectedColor = VerseColors.McTextMuted
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VerseColors.McTextPrimary
                                )
                                Text(
                                    desc,
                                    fontSize = 11.sp,
                                    color = VerseColors.McTextMuted
                                )
                            }
                        }
                        if (name != "无加载器") Spacer(Modifier.height(4.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showModLoaderSheet = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = VerseColors.McGreen)
                ) {
                    Text("确认安装", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showModLoaderSheet = false }) {
                    Text("取消", color = VerseColors.McTextSecondary, fontSize = 13.sp)
                }
            }
        )
    }
}

data class Quad(val first: String, val second: String, val third: String, val fourth: Color)

@Composable
private fun VersionInfoCard(versionId: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(VerseColors.McGreen, VerseColors.McGreenLight)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Landscape,
                    null,
                    Modifier.size(30.dp),
                    Color.White
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Minecraft $versionId",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerseColors.McTextPrimary
                )
                Text(
                    "正式版 · Fabric · 已安装",
                    fontSize = 12.sp,
                    color = VerseColors.McTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    trailing: @Composable (() -> Unit)? = null
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(18.dp), iconTint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VerseColors.McTextPrimary
                )
                Text(
                    description,
                    fontSize = 11.sp,
                    color = VerseColors.McTextMuted
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SectionDivider(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = VerseColors.McTextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun DangerActionRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    actionColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(actionColor.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(16.dp), actionColor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = VerseColors.McTextPrimary
            )
            Text(
                description,
                fontSize = 10.sp,
                color = VerseColors.McTextMuted
            )
        }
        FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = actionColor.copy(alpha = 0.1f),
                contentColor = actionColor
            )
        ) {
            Text(
                actionLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}