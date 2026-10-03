package com.verse.explorer.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.domain.model.setup.ComponentState
import com.verse.explorer.app.setup.RuntimeSetupManager
import com.verse.explorer.app.setup.RuntimeSetupManager.RuntimeComponent
import com.verse.explorer.app.ui.theme.VerseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FCL 风格的前置运行时环境安装界面
 *
 * 照搬 FCL RuntimeFragment 的 UI 逻辑：
 * - 显示 10 个运行时组件的安装状态（含可选组件）
 * - 一键安装所有缺失组件
 * - 实时进度反馈
 * - 安装完毕后自动进入启动器
 *
 * 与 FCL 一致的可选组件：JAVA_25
 * （LWJGL_BOAT 已移除：FCL 上游无此目录，且 boat/pojav 启动方式共用同一份 lwjgl.jar）
 * （JAVA_11 已移除：MC 1.16-1.17 实际使用 Java 8/17 兼容，无需单独 JRE 11）
 */
@Composable
fun RuntimeSetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val componentStates = remember {
        mutableStateMapOf<RuntimeComponent, ComponentState>()
    }

    var isChecking by remember { mutableStateOf(true) }
    var isInstallingAll by remember { mutableStateOf(false) }
    var allCompleted by remember { mutableStateOf(false) }

    // 首次检查所有组件状态
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                RuntimeSetupManager.loadPaths(context)
                val status = RuntimeSetupManager.checkAllComponents(context)
                status.forEach { (component, ready) ->
                    componentStates[component] = ComponentState(
                        isReady = ready,
                        isCompleted = ready
                    )
                }
            }
            isChecking = false

            val requiredReady = componentStates.all { (component, state) ->
                component.isOptional || state.isReady
            }
            if (requiredReady) {
                allCompleted = true
            }
        } catch (e: Exception) {
            isChecking = false
        }
    }

    // 安装所有缺失组件
    fun installAll() {
        isInstallingAll = true
        scope.launch(Dispatchers.IO) {
            RuntimeSetupManager.installMissingComponents(
                context,
                componentStates.mapValues { it.value.isReady }
            ) { component, status ->
                when {
                    status == "正在安装..." -> {
                        componentStates[component] = ComponentState(isInstalling = true)
                    }
                    status == "安装完成" -> {
                        componentStates[component] = ComponentState(isReady = true, isCompleted = true)
                    }
                    status.startsWith("安装失败") -> {
                        componentStates[component] = ComponentState(errorMessage = status)
                    }
                }
            }

            val verifyStatus = RuntimeSetupManager.checkAllComponents(context)
            verifyStatus.forEach { (component, ready) ->
                componentStates[component] = ComponentState(isReady = ready, isCompleted = ready)
            }

            val requiredReady = verifyStatus.all { (component, ready) ->
                component.isOptional || ready
            }

            isInstallingAll = false
            allCompleted = requiredReady
        }
    }

    // 全部安装完毕后自动进入启动器
    LaunchedEffect(allCompleted) {
        if (allCompleted) {
            onSetupComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VerseColors.PcBgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 头部：单行标题 + 同行统计，压缩头部占比让组件列表占主要空间
            val readyCount = componentStates.count { it.value.isReady }
            val totalCount = RuntimeComponent.entries.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "安装前置环境",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (!isChecking) {
                    Text(
                        text = "已就绪 $readyCount/$totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (readyCount == totalCount) VerseColors.PcAccent else VerseColors.PcOrange,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "正在为 Minecraft 准备必要的运行时组件",
                style = MaterialTheme.typography.bodyMedium,
                color = VerseColors.PcTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 组件列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    RuntimeComponent.entries.forEach { component ->
                        val state = componentStates[component] ?: ComponentState()
                        RuntimeComponentRow(
                            component = component,
                            state = state,
                            isChecking = isChecking
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 底部按钮
            if (!allCompleted) {
                Button(
                    onClick = { installAll() },
                    enabled = !isChecking && !isInstallingAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VerseColors.PcAccent,
                        contentColor = Color.White,
                        disabledContainerColor = VerseColors.PcBgCard,
                        disabledContentColor = VerseColors.PcTextMuted
                    )
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = VerseColors.PcTextMuted,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("正在检查运行环境...", fontSize = 13.sp)
                    } else if (isInstallingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("正在安装中...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("安装所有运行时组件", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // 全部安装完毕
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = VerseColors.PcAccent.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = VerseColors.PcAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "环境就绪，正在进入启动器...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VerseColors.PcAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RuntimeComponentRow(
    component: RuntimeComponent,
    state: ComponentState,
    isChecking: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VerseColors.PcBgCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            VerseColors.PcBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            val (icon, iconTint, bgColor) = when {
                isChecking -> Triple(Icons.Outlined.HourglassEmpty, VerseColors.PcTextMuted, VerseColors.PcBgHover)
                state.isInstalling -> Triple(Icons.Filled.Sync, VerseColors.PcAccent, VerseColors.PcAccent.copy(alpha = 0.15f))
                state.isReady || state.isCompleted -> Triple(Icons.Filled.CheckCircle, VerseColors.PcAccent, VerseColors.PcAccent.copy(alpha = 0.08f))
                state.errorMessage != null -> Triple(Icons.Filled.Error, VerseColors.PcRed, VerseColors.PcRed.copy(alpha = 0.08f))
                else -> Triple(Icons.Outlined.RadioButtonUnchecked, VerseColors.PcOrange, VerseColors.PcOrange.copy(alpha = 0.08f))
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                if (state.isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = VerseColors.PcAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 组件信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = component.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = component.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcRed,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 状态标签
            when {
                isChecking -> {
                    Text(
                        "检查中",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcTextMuted,
                        fontSize = 11.sp
                    )
                }
                state.isInstalling -> {
                    Text(
                        "安装中",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                state.isReady || state.isCompleted -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = VerseColors.PcAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "已就绪",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerseColors.PcAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                state.errorMessage != null -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = VerseColors.PcRed.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "失败",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerseColors.PcRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                else -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = VerseColors.PcOrange.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "待安装",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerseColors.PcOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}