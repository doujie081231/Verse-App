package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.data.VersionSettingsRepository
import com.verse.explorer.app.ui.components.PcCard
import com.verse.explorer.app.ui.components.PcDropdownRow
import com.verse.explorer.app.ui.components.PcSwitchRow
import com.verse.explorer.app.ui.components.PcTextInputRow
import com.verse.explorer.app.ui.components.pcTextFieldColors
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.java.JavaRuntimeManager
import com.verse.explorer.core.version.VersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private typealias VS = VersionSettingsRepository.VersionSettings

/**
 * 版本级启动设置页。功能对齐 FCL 版本设置：独立设置、定向 Java、内存、
 * JVM/游戏参数、服务器直达、版本隔离、图形 API/Vulkan 驱动/渲染器、调试日志、
 * 完整性检查豁免、强制分辨率与控制布局选择。
 *
 * 未开启独立设置时展示全局值摘要（只读）；开启后逐项编辑、即改即存。
 */
@Composable
fun VersionSettingsScreen(
    versionId: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val repo = remember { VersionSettingsRepository(context) }

    var settings by remember { mutableStateOf(VS()) }
    var loaded by remember { mutableStateOf(false) }
    var loaderHint by remember { mutableStateOf<String?>(null) }
    var javaOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var controllerNames by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(versionId) {
        settings = repo.getVersionSettings(versionId)
        loaded = true
        withContext(Dispatchers.IO) {
            loaderHint = try {
                VersionManager(GameConfig.getGameDir(context))
                    .getLocalVersions().find { it.id == versionId }?.loaderHint
            } catch (_: Exception) {
                null
            }
            javaOptions = JavaRuntimeManager.getInstalledRuntimes(context).map {
                it.name to "${it.name}（Java ${it.version}）"
            }
            controllerNames = try {
                val dir = File(net.kdt.pojavlaunch.Tools.CTRLMAP_PATH)
                dir.listFiles { f -> f.isFile && f.extension == "json" }
                    ?.map { it.nameWithoutExtension }
                    ?.filter { it != "Default" }
                    ?.sorted()
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun update(transform: (VS) -> VS) {
        val next = transform(settings)
        settings = next
        repo.setVersionSettings(versionId, next)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

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
                        tint = VerseColors.PcTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = "版本设置",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerseColors.PcTextPrimary
                    )
                    Text(
                        text = versionId + (loaderHint?.let { " · $it" } ?: ""),
                        fontSize = 11.sp,
                        color = VerseColors.PcTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = VerseColors.PcAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ── 模组管理入口（显眼大按钮，置于所有设置卡片之前） ──
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Screen.Mods.route) },
                        shape = RoundedCornerShape(12.dp),
                        color = VerseColors.PcAccent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, VerseColors.PcAccent.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Extension,
                                contentDescription = null,
                                tint = VerseColors.PcAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "模组管理",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VerseColors.PcAccent
                                )
                                Text(
                                    "查看、添加此版本的模组资源",
                                    fontSize = 11.sp,
                                    color = VerseColors.PcTextSecondary
                                )
                            }
                            Icon(
                                Icons.Filled.ArrowForward,
                                contentDescription = null,
                                tint = VerseColors.PcAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // ── 独立设置 ──
                    PcCard(title = "独立设置") {
                        PcSwitchRow(
                            "启用版本独立设置",
                            if (settings.usesGlobal) "当前跟随全局启动设置" else "以下设置仅对本版本生效",
                            !settings.usesGlobal
                        ) { enable ->
                            if (enable) {
                                settings = repo.specialize(versionId)
                            } else {
                                repo.globalize(versionId)
                                settings = repo.getVersionSettings(versionId)
                            }
                        }
                        if (settings.usesGlobal) {
                            Spacer(Modifier.height(4.dp))
                            GlobalSummary()
                        }
                    }

                    if (!settings.usesGlobal) {
                        // ── Java ──
                        PcCard(title = "Java") {
                            PcDropdownRow(
                                "Java 运行时",
                                settings.java,
                                listOf(VersionSettingsRepository.JAVA_AUTO to "自动选择") + javaOptions
                            ) { update { s -> s.copy(java = it) } }
                            PcSwitchRow(
                                "不检查 JVM",
                                "跳过启动前对定向 Java 运行时的完整性校验",
                                settings.notCheckJVM
                            ) { update { s -> s.copy(notCheckJVM = it) } }
                        }

                        // ── 内存 ──
                        PcCard(title = "内存") {
                            PcSwitchRow(
                                "自动分配内存",
                                "按启动时的实时可用内存计算（FCL 算法）",
                                settings.autoMemory
                            ) { update { s -> s.copy(autoMemory = it) } }
                            if (!settings.autoMemory) {
                                MemoryField("最大内存 (MB)", settings.maxMemory) {
                                    update { s -> s.copy(maxMemory = it) }
                                }
                                MemoryField("最小内存 (MB)", settings.minMemory) {
                                    update { s -> s.copy(minMemory = it) }
                                }
                            }
                        }

                        // ── 启动参数 ──
                        PcCard(title = "启动参数") {
                            PcTextInputRow(
                                "JVM 参数（空格分隔）",
                                settings.javaArgs,
                                "-Xmx1024m ..."
                            ) { update { s -> s.copy(javaArgs = it) } }
                            PcTextInputRow(
                                "游戏参数（空格分隔）",
                                settings.minecraftArgs,
                                "--width 1280 ..."
                            ) { update { s -> s.copy(minecraftArgs = it) } }
                        }

                        // ── 游戏 ──
                        PcCard(title = "游戏") {
                            PcTextInputRow(
                                "启动后进入服务器",
                                settings.serverIp,
                                "ip 或 ip:端口"
                            ) { update { s -> s.copy(serverIp = it) } }
                            PcSwitchRow(
                                "版本隔离",
                                "该版本使用独立运行目录（saves/mods 等随版本存放）",
                                settings.isolateGameDir
                            ) { update { s -> s.copy(isolateGameDir = it) } }
                        }

                        // ── 图形 ──
                        PcCard(title = "图形") {
                            PcDropdownRow(
                                "图形 API",
                                settings.graphicsBackend,
                                listOf(
                                    "default" to "自动（默认）",
                                    "opengl" to "OpenGL",
                                    "vulkan" to "Vulkan"
                                )
                            ) { update { s -> s.copy(graphicsBackend = it) } }
                            PcDropdownRow(
                                "Vulkan 驱动器",
                                if (settings.vkDriverSystem) "system" else "turnip",
                                listOf(
                                    "system" to "系统驱动",
                                    "turnip" to "Turnip（Adreno 专用）"
                                )
                            ) { update { s -> s.copy(vkDriverSystem = it == "system") } }
                            PcDropdownRow(
                                "渲染器",
                                settings.renderer,
                                listOf(
                                    "" to "跟随全局设置",
                                    "nggl4es" to "nggl4es（Krypton）",
                                    "gl4es" to "gl4es 1.1.4",
                                    "mobileglues" to "MobileGlues"
                                )
                            ) { update { s -> s.copy(renderer = it) } }
                            PcSwitchRow(
                                "输出调试日志",
                                "游戏内输出详细调试日志，便于排查问题",
                                settings.debugLog
                            ) { update { s -> s.copy(debugLog = it) } }
                        }

                        // ── 检查 ──
                        PcCard(title = "检查") {
                            PcSwitchRow(
                                "不检查游戏完整性",
                                "跳过启动前的游戏文件完整性检查以加快启动",
                                settings.notCheckGame
                            ) { update { s -> s.copy(notCheckGame = it) } }
                            PcSwitchRow(
                                "不检查模组",
                                "启动时不对模组文件做完整性校验",
                                settings.notCheckMod
                            ) { update { s -> s.copy(notCheckMod = it) } }
                        }

                        // ── 分辨率 ──
                        PcCard(title = "分辨率") {
                            PcSwitchRow(
                                "强制分辨率",
                                "使用固定窗口尺寸启动，不跟随窗口比例缩放",
                                settings.forceResolution
                            ) { update { s -> s.copy(forceResolution = it) } }
                            if (settings.forceResolution) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = if (settings.width > 0) settings.width.toString() else "",
                                        onValueChange = { v -> update { s -> s.copy(width = v.filter { c -> c.isDigit() }.take(5).toIntOrNull() ?: 0) } },
                                        label = { Text("宽度", fontSize = 12.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = pcTextFieldColors()
                                    )
                                    OutlinedTextField(
                                        value = if (settings.height > 0) settings.height.toString() else "",
                                        onValueChange = { v -> update { s -> s.copy(height = v.filter { c -> c.isDigit() }.take(5).toIntOrNull() ?: 0) } },
                                        label = { Text("高度", fontSize = 12.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = pcTextFieldColors()
                                    )
                                }
                            }
                        }

                        // ── 控制布局 ──
                        if (controllerNames.isNotEmpty()) {
                            PcCard(title = "控制布局") {
                                PcDropdownRow(
                                    "触控布局",
                                    settings.controller,
                                    listOf("" to "默认布局") + controllerNames.map { it to it }
                                ) { update { s -> s.copy(controller = it) } }
                            }
                        }

                        // ── 重置 ──
                        PcCard(title = "重置") {
                            PcSwitchRow(
                                "恢复跟随全局",
                                "清除本版本的全部独立设置，回到全局启动设置",
                                false,
                                onToggle = { _ ->
                                    repo.globalize(versionId)
                                    settings = repo.getVersionSettings(versionId)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MemoryField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value > 0) value.toString() else "") }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = VerseColors.PcTextSecondary
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { c -> c.isDigit() }.take(5)
                onChange(text.toIntOrNull() ?: 0)
            },
            placeholder = { Text("自动", fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = pcTextFieldColors()
        )
    }
}

@Composable
private fun GlobalSummary() {
    val backend = when (SettingsRepository.getGraphicsBackend()) {
        "opengl" -> "OpenGL"
        "vulkan" -> "Vulkan"
        else -> "自动"
    }
    SummaryLine("图形 API", backend)
    SummaryLine("渲染器", SettingsRepository.getRenderer())
    val mem = SettingsRepository.getMaxMemory()
    SummaryLine("内存", if (mem > 0) "${mem} MB 上限（自动分配）" else "自动分配")
    SummaryLine("调试日志", if (SettingsRepository.getDebugLog()) "开" else "关")
}

@Composable
private fun SummaryLine(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, fontSize = 12.sp, color = VerseColors.PcTextMuted)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = VerseColors.PcTextSecondary)
    }
}
