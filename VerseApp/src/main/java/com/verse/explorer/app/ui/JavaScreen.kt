package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.data.java.JavaRuntimeRepository
import com.verse.explorer.app.launch.JvmArgsBuilder
import com.verse.explorer.app.ui.components.PageHeader
import com.verse.explorer.app.ui.components.PcButton
import com.verse.explorer.app.ui.components.PcButtonVariant
import com.verse.explorer.app.ui.components.PcCard
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
    val settingsRepo = SettingsRepository
    val scope = rememberCoroutineScope()

    var installedRuntimes by remember { mutableStateOf(JavaRuntimeRepository.loadInstalledRuntimes(context)) }
    var selectedJavaVersion by remember { mutableStateOf(settingsRepo.getJavaVersion()) }
    var selectedGcType by remember { mutableStateOf(settingsRepo.getGcType()) }
    var customJvmArgs by remember { mutableStateOf(settingsRepo.getCustomJvmArgs()) }
    var installingVersion by remember { mutableStateOf<String?>(null) }
    var installProgress by remember { mutableStateOf("") }
    var importingJava by remember { mutableStateOf(false) }

    // 导入本地 Java（zip）：SAF 选文件 → 解压到 jre 目录 → 扫描识别
    val importJavaLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                importingJava = true
                installProgress = "正在导入 Java..."
                try {
                    val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                    } ?: uri.lastPathSegment ?: "imported-java"
                    val runtime = context.contentResolver.openInputStream(uri)?.use { input ->
                        JavaRuntimeManager.importJava(context, input, displayName)
                    } ?: throw Exception("无法读取所选文件")
                    installedRuntimes = JavaRuntimeRepository.loadInstalledRuntimes(context)
                    installProgress = "导入完成：${runtime.name}（${runtime.arch}）"
                } catch (e: Exception) {
                    installProgress = "导入失败: ${e.message}"
                } finally {
                    importingJava = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        installedRuntimes = JavaRuntimeRepository.loadInstalledRuntimes(context)
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
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = VerseColors.PcTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    PageHeader(
                        title = "Java 管理",
                        modifier = Modifier.weight(1f),
                        actions = {
                            PcButton(
                                text = if (importingJava) "导入中..." else "导入",
                                onClick = {
                                    importJavaLauncher.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                variant = PcButtonVariant.GHOST,
                                icon = Icons.Outlined.UploadFile,
                                enabled = !importingJava
                            )
                            PcButton(
                                text = "刷新",
                                onClick = { installedRuntimes = JavaRuntimeRepository.loadInstalledRuntimes(context) },
                                variant = PcButtonVariant.GHOST,
                                icon = Icons.Outlined.Refresh
                            )
                            if (installedRuntimes.distinctBy { it.version }.size < 4) {
                                PcButton(
                                    text = "一键安装所有",
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val allVersions = javaVersions.map { "java${it.first}" }
                                                var installedCount2 = 0
                                                for (versionKey in allVersions) {
                                                    val verNum = versionKey.removePrefix("java").toIntOrNull() ?: continue
                                                    val current = JavaRuntimeRepository.loadInstalledRuntimes(context)
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
                                                        installedRuntimes = JavaRuntimeRepository.loadInstalledRuntimes(context)
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
                                    variant = PcButtonVariant.PRIMARY,
                                    icon = Icons.Filled.Download
                                )
                            }
                        }
                    )
                }
            }

            item {
                SectionTitle("已安装的 Java 运行时")
            }

            item {
                val installedCount = installedRuntimes.distinctBy { it.version }.size
                Text(
                    text = "已安装 $installedCount/4 · 当前默认: Java $selectedJavaVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 12.sp
                )
            }

            item {
                PcCard(
                    modifier = Modifier.fillMaxWidth()
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
                                            installedRuntimes = JavaRuntimeRepository.loadInstalledRuntimes(context)
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
                            }
                        )

                        if (index < javaVersions.size - 1) {
                            HorizontalDivider(
                                color = VerseColors.PcBorder,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
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
                PcCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "垃圾回收器 (GC)",
                        style = MaterialTheme.typography.labelMedium,
                        color = VerseColors.PcTextSecondary,
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
                                color = if (isGcSelected) VerseColors.PcGreen.copy(alpha = 0.12f) else VerseColors.PcBgCard,
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
                                    color = if (isGcSelected) VerseColors.PcGreen else VerseColors.PcTextSecondary,
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
                        color = VerseColors.PcTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VerseColors.PcBgTertiary
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            jvmArgsPreview.forEach { arg ->
                                Text(
                                    text = arg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (arg.startsWith("-X")) VerseColors.PcGreen.copy(alpha = 0.8f) else VerseColors.PcTextSecondary,
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
                        color = VerseColors.PcTextSecondary,
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
                                color = VerseColors.PcTextMuted,
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VerseColors.PcAccent.copy(alpha = 0.3f),
                            unfocusedBorderColor = VerseColors.PcBorder,
                            focusedContainerColor = VerseColors.PcBgTertiary,
                            unfocusedContainerColor = VerseColors.PcBgTertiary,
                            focusedTextColor = VerseColors.PcTextPrimary,
                            unfocusedTextColor = VerseColors.PcTextPrimary,
                            cursorColor = VerseColors.PcAccent
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, VerseColors.PcBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = VerseColors.PcBgCard,
                                contentColor = VerseColors.PcTextSecondary
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
                                containerColor = VerseColors.PcGreen.copy(alpha = 0.12f),
                                contentColor = VerseColors.PcGreen
                            )
                        ) {
                            Text("恢复默认", fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}