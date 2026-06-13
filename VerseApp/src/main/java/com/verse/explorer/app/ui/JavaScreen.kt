package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.launch.JvmArgsBuilder
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.java.JavaRuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavaScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()

    var installedRuntimes by remember { mutableStateOf(loadInstalledRuntimes(context)) }
    var selectedJavaVersion by remember { mutableStateOf(settingsRepo.getJavaVersion()) }
    var selectedGcType by remember { mutableStateOf(settingsRepo.getGcType()) }
    var customJvmArgs by remember { mutableStateOf(settingsRepo.getCustomJvmArgs()) }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
    var installingVersion by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        installedRuntimes = loadInstalledRuntimes(context)
    }

    val javaVersions = listOf(
        Triple("8", "Java 8", "Minecraft 1.7 - 1.16"),
        Triple("17", "Java 17", "Minecraft 1.18 - 1.20.4"),
        Triple("21", "Java 21", "Minecraft 1.20.5 - 1.21.x"),
        Triple("25", "Java 25", "Minecraft 1.22+, 快照"),
    )

    val availableGcTypes = remember(selectedJavaVersion) {
        val versionNum = selectedJavaVersion.toIntOrNull() ?: 17
        JvmArgsBuilder.SUPPORTED_GC[versionNum] ?: listOf("G1GC")
    }

    LaunchedEffect(installingVersion) {
        selectedGcType = settingsRepo.getGcType()
        if (availableGcTypes.none { it == selectedGcType }) {
            selectedGcType = availableGcTypes.first()
            settingsRepo.setGcType(selectedGcType)
        }
    }

    val jvmArgsPreview = remember(selectedJavaVersion, selectedGcType) {
        val versionNum = selectedJavaVersion.toIntOrNull() ?: 17
        JvmArgsBuilder.getJvmArgsPreview(versionNum, selectedGcType)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.McBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PageHeader(
                    title = "Java 管理",
                    subtitle = "管理 Java 运行时和 JVM 参数"
                )
            }

            item {
                SectionTitle("已安装的 Java 运行时")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val installedCount = installedRuntimes.distinctBy { it.version }.size
                    Text(
                        text = "已安装 $installedCount/4 · 当前默认: Java $selectedJavaVersion",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McTextSecondary,
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                installedRuntimes = loadInstalledRuntimes(context)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = VerseColors.McCardBg,
                                contentColor = VerseColors.McTextSecondary
                            )
                        ) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("刷新", fontSize = 11.sp)
                        }
                        if (installedCount < 4) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val allVersions = javaVersions.map { "java${it.first}" }
                                            var installedCount2 = 0
                                            for (versionKey in allVersions) {
                                                val verNum = versionKey.removePrefix("java").toIntOrNull() ?: continue
                                                val current = loadInstalledRuntimes(context)
                                                if (current.any { it.version == verNum }) continue
                                                installingVersion = verNum.toString()
                                                installProgress = "正在安装 Java $verNum..."
                                                val result = JavaRuntimeManager.installJre(
                                                    context,
                                                    versionKey
                                                ) { progress ->
                                                    installProgress = progress.message
                                                }
                                                if (result.isSuccess) {
                                                    installedCount2++
                                                    installedRuntimes = loadInstalledRuntimes(context)
                                                }
                                            }
                                            installProgress = if (installedCount2 > 0) "全部安装完成 (新增 $installedCount2 个)" else "全部已安装"
                                        } catch (e: Exception) {
                                            installProgress = "安装异常: ${e.message}"
                                        } finally {
                                            installingVersion = null
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = VerseColors.McGreen.copy(alpha = 0.12f),
                                    contentColor = VerseColors.McGreen
                                )
                            ) {
                                Icon(Icons.Filled.Download, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("一键安装所有", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        javaVersions.forEachIndexed { index, (version, displayName, mcVersions) ->
                            val verNum = version.toIntOrNull() ?: 8
                            val installed = installedRuntimes.any { it.version == verNum }
                            val installedRuntime = installedRuntimes.firstOrNull { it.version == verNum }
                            val isInstalling = installingVersion == version

                            JavaVersionRow(
                                version = version,
                                displayName = displayName,
                                mcVersions = mcVersions,
                                installed = installed,
                                isInstalling = isInstalling,
                                installProgress = installProgress,
                                isDefault = selectedJavaVersion == version,
                                runtimePath = installedRuntime?.path?.absolutePath,
                                onInstall = {
                                    installingVersion = version
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            installProgress = "正在安装..."
                                            val result = JavaRuntimeManager.installJre(
                                                context,
                                                "java$version"
                                            ) { progress ->
                                                installProgress = progress.message
                                            }
                                            if (result.isSuccess) {
                                                settingsRepo.setJavaVersion(version)
                                                selectedJavaVersion = version
                                                installedRuntimes = loadInstalledRuntimes(context)
                                                installProgress = ""
                                            } else {
                                                installProgress = "安装失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"
                                            }
                                        } catch (e: Exception) {
                                            installProgress = "安装异常: ${e.message}"
                                        } finally {
                                            installingVersion = null
                                        }
                                    }
                                },
                                onSetDefault = {
                                    settingsRepo.setJavaVersion(version)
                                    selectedJavaVersion = version
                                },
                                onDelete = {
                                    showDeleteConfirm = verNum
                                }
                            )

                            if (index < javaVersions.size - 1) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.04f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SectionTitle("JVM 参数配置 (Java $selectedJavaVersion)")
            }

            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "垃圾回收器 (GC)",
                            style = MaterialTheme.typography.labelMedium,
                            color = VerseColors.McTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableGcTypes.forEach { gc ->
                                val isGcSelected = selectedGcType == gc
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isGcSelected) VerseColors.McGreen.copy(alpha = 0.12f) else VerseColors.McCardBg,
                                    modifier = Modifier
                                        .clickable {
                                            selectedGcType = gc
                                            settingsRepo.setGcType(gc)
                                        }
                                ) {
                                    Text(
                                        text = when (gc) {
                                            "Shenandoah" -> "Shenandoah (低延迟)"
                                            "ZGC" -> "ZGC (大内存)"
                                            "SerialGC" -> "Serial (单线程)"
                                            "ParallelGC" -> "Parallel (多线程)"
                                            else -> "G1GC (默认)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isGcSelected) VerseColors.McGreen else VerseColors.McTextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "JVM 参数预览",
                            style = MaterialTheme.typography.labelMedium,
                            color = VerseColors.McTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VerseColors.McBg
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                jvmArgsPreview.forEach { arg ->
                                    Text(
                                        text = arg,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (arg.startsWith("-X")) VerseColors.McGreen.copy(alpha = 0.8f) else VerseColors.McTextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "自定义 JVM 参数",
                            style = MaterialTheme.typography.labelMedium,
                            color = VerseColors.McTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customJvmArgs,
                            onValueChange = {
                                customJvmArgs = it
                                settingsRepo.setCustomJvmArgs(it)
                            },
                            placeholder = {
                                Text(
                                    "-XX:+AggressiveOpts -Dcustom.prop=value",
                                    color = VerseColors.McTextDim,
                                    fontSize = 12.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VerseColors.McGreen.copy(alpha = 0.3f),
                                unfocusedBorderColor = VerseColors.McCardBorder,
                                focusedContainerColor = VerseColors.McCardBg,
                                unfocusedContainerColor = VerseColors.McCardBg,
                                focusedTextColor = VerseColors.McTextPrimary,
                                unfocusedTextColor = VerseColors.McTextPrimary,
                                cursorColor = VerseColors.McGreen
                            ),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    customJvmArgs = ""
                                    settingsRepo.setCustomJvmArgs("")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = VerseColors.McCardBg,
                                    contentColor = VerseColors.McTextSecondary
                                )
                            ) {
                                Text("清空", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    selectedGcType = "G1GC"
                                    customJvmArgs = ""
                                    settingsRepo.setGcType("G1GC")
                                    settingsRepo.setCustomJvmArgs("")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VerseColors.McGreen.copy(alpha = 0.12f),
                                    contentColor = VerseColors.McGreen
                                )
                            ) {
                                Text("恢复默认", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            containerColor = VerseColors.McOverlayContentBg,
            titleContentColor = VerseColors.McTextPrimary,
            textContentColor = VerseColors.McTextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text("删除 Java $showDeleteConfirm", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("确定要删除 Java $showDeleteConfirm 运行时吗？所有需要此版本的 Minecraft 版本将无法启动。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val v = showDeleteConfirm
                        showDeleteConfirm = null
                        if (v != null) {
                            installedRuntimes.filter { it.version == v }.forEach { rt ->
                                try { rt.path.deleteRecursively() } catch (_: Exception) {}
                            }
                        }
                        installedRuntimes = loadInstalledRuntimes(context)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = VerseColors.McRedstone)
                ) {
                    Text("删除", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消", color = VerseColors.McTextSecondary)
                }
            }
        )
    }
}

private fun loadInstalledRuntimes(context: android.content.Context): List<com.verse.explorer.core.java.JavaRuntime> {
    return try {
        JavaRuntimeManager.getInstalledRuntimes(context)
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
private fun JavaVersionRow(
    version: String,
    displayName: String,
    mcVersions: String,
    installed: Boolean,
    isInstalling: Boolean,
    installProgress: String,
    isDefault: Boolean,
    runtimePath: String? = null,
    onInstall: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        if (installed) listOf(VerseColors.McGreen, VerseColors.McGreen.copy(alpha = 0.6f))
                        else listOf(VerseColors.McGold, VerseColors.McGold.copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isInstalling) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (installed) Icons.Filled.Check else Icons.Filled.Code,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.McTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                if (isDefault && installed) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = VerseColors.McGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "默认",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerseColors.McGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "支持: $mcVersions",
                style = MaterialTheme.typography.labelSmall,
                color = VerseColors.McTextSecondary,
                fontSize = 11.sp
            )
            if (installed && runtimePath != null) {
                Text(
                    text = runtimePath.let { if (it.length > 30) "...${it.takeLast(30)}" else it },
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McTextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isInstalling) {
                Text(
                    text = installProgress,
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.McGold,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        if (installed) {
            if (!isDefault) {
                TextButton(
                    onClick = onSetDefault,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "设为默认",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McGreen,
                        fontSize = 10.sp
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = VerseColors.McTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = VerseColors.McGreen.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "已安装",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.McGreen,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            FilledTonalButton(
                onClick = onInstall,
                enabled = !isInstalling,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = VerseColors.McGold.copy(alpha = 0.12f),
                    contentColor = VerseColors.McGold,
                    disabledContainerColor = VerseColors.McCardBg,
                    disabledContentColor = VerseColors.McTextDim
                )
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = VerseColors.McGold,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Icon(Icons.Filled.Download, null, Modifier.size(12.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isInstalling) "安装中" else "安装",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}