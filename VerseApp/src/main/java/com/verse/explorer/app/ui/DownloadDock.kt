package com.verse.explorer.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verse.explorer.app.download.DownloadCenter
import com.verse.explorer.app.download.DownloadCenterItem
import com.verse.explorer.app.download.DownloadCenterState
import com.verse.explorer.app.ui.components.VerseProgressBar
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 全局下载浮层。样式对齐 Zalith Launcher 2 的 TaskMenu：
 *
 * 最小化态是一条「线性进度条 + 任务图标」的小块（ZL2 在顶栏右侧，这里放在底部居中），
 * 点击展开成悬浮小窗，窗内每个任务是一张 ZL2 同款卡片：
 * 左侧取消按钮、中间任务标题、进度条、百分比与速度。
 *
 * 挂在导航宿主之外（MainScreen 根 Box），任意页面可见。
 */
@Composable
fun DownloadDock(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 12.dp
) {
    val tasks by DownloadCenter.items.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(tasks.isEmpty()) {
        if (tasks.isEmpty()) expanded = false
    }
    if (tasks.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWPx = constraints.maxWidth.toFloat()
        val maxHPx = constraints.maxHeight.toFloat()

        // 竖着的长方形小窗：宽度收窄、高度拉长
        val windowWidth = 248.dp
        val windowHeight = (maxHeight - 140.dp).coerceIn(320.dp, 470.dp)
        val winWPx = with(density) { windowWidth.toPx() }
        val winHPx = with(density) { windowHeight.toPx() }
        val bottomPx = with(density) { bottomPadding.toPx() }

        // 初始位置：底部居中；之后由用户拖顶部横杠移动
        var posX by remember { mutableFloatStateOf((maxWPx - winWPx) / 2f) }
        var posY by remember { mutableFloatStateOf(maxHPx - winHPx - bottomPx) }

        fun clampX(v: Float) = v.coerceIn(0f, (maxWPx - winWPx).coerceAtLeast(0f))
        fun clampY(v: Float) = v.coerceIn(0f, (maxHPx - winHPx).coerceAtLeast(0f))

        // 展开时很轻的压暗，点空白收起
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = false }
            )
        }

        // 最小化态胶囊：底部居中
        AnimatedVisibility(
            visible = !expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.92f),
            exit = fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.92f)
        ) {
            Box(Modifier.padding(bottom = bottomPadding)) {
                DownloadPill(items = tasks, onClick = { expanded = true })
            }
        }

        // 展开态小窗：绝对定位，可拖动
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.92f),
            exit = fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.92f)
        ) {
            Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(posX.toInt(), posY.toInt()) }
                    .size(windowWidth, windowHeight)
            ) {
                DownloadWindow(
                    items = tasks,
                    onCollapse = { expanded = false },
                    dragHandleModifier = Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            posX = clampX(posX + dragAmount.x)
                            posY = clampY(posY + dragAmount.y)
                        }
                    }
                )
            }
        }
    }
}

/**
 * 最小化态：ZL2 顶栏那块任务指示的同款做法 —— 一条线性进度条 + 一个任务图标，
 * 整块可点，不使用文字，尽量不占地方。
 */
@Composable
private fun DownloadPill(
    items: List<DownloadCenterItem>,
    onClick: () -> Unit
) {
    val progress = aggregateProgress(items)
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "pillProgress"
    )
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(VerseColors.PcBgCard)
            .border(1.dp, VerseColors.PcBorder, shape)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(132.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerseProgressBar(
            progress = animated,
            modifier = Modifier.weight(1f),
            height = 8.dp,
            color = VerseColors.PcPurple,
            trackColor = VerseColors.PcBgActive
        )
        Icon(
            Icons.Outlined.CloudDownload,
            contentDescription = "展开下载任务",
            modifier = Modifier.size(20.dp),
            tint = VerseColors.PcTextSecondary
        )
    }
}

/** 展开态：悬浮小窗，内容按 ZL2 TaskMenu 的列表组织 */
@Composable
private fun DownloadWindow(
    items: List<DownloadCenterItem>,
    onCollapse: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(VerseColors.PcBgSecondary)
            .border(1.dp, VerseColors.PcBorder, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(dragHandleModifier)
                .padding(start = 12.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⠿ 下载任务",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = VerseColors.PcTextPrimary
            )
            IconButton(
                modifier = Modifier.size(28.dp),
                onClick = onCollapse
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "最小化",
                    modifier = Modifier.size(18.dp),
                    tint = VerseColors.PcTextSecondary
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 6.dp)
        ) {
            items(items, key = { it.id }) { item ->
                DownloadTaskItem(item)
            }
        }
    }
}

/**
 * 单条任务卡片，结构照 ZL2 的 TaskItem：
 * 取消按钮在最左、任务标题在上、进度条居中、百分比与速度在下。
 */
@Composable
private fun DownloadTaskItem(item: DownloadCenterItem) {
    val progress by animateFloatAsState(
        targetValue = item.progress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "taskProgress"
    )
    val waiting = item.state == DownloadCenterState.WAIT_INSTALL
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = shape,
        color = VerseColors.PcBgCard
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterVertically),
                onClick = {
                    if (waiting) DownloadCenter.discard(item) else DownloadCenter.cancel(item)
                }
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = if (waiting) "丢弃" else "取消下载",
                    modifier = Modifier.size(18.dp),
                    tint = VerseColors.PcTextSecondary
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = item.title,
                    fontSize = 11.sp,
                    color = VerseColors.PcTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (waiting) {
                    Text(
                        text = "下载完成，待安装",
                        fontSize = 10.sp,
                        color = VerseColors.PcAccent
                    )
                } else {
                    // 未知总量时用不确定进度的样式，和 ZL2 一致
                    if (item.progress < 0f) {
                        VerseProgressBar(
                            progress = null,
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 3.dp),
                            height = 6.dp,
                            color = VerseColors.PcPurple,
                            trackColor = VerseColors.PcBgActive
                        )
                    } else {
                        VerseProgressBar(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 3.dp),
                            height = 6.dp,
                            color = VerseColors.PcPurple,
                            trackColor = VerseColors.PcBgActive
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.progress >= 0f) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 10.sp,
                                color = VerseColors.PcTextMuted
                            )
                        }
                        if (item.speed > 0) {
                            Text(
                                text = "${formatBytes(item.speed)}/s",
                                fontSize = 10.sp,
                                color = VerseColors.PcTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 多个任务按已下载字节加权，得到总进度 */
private fun aggregateProgress(items: List<DownloadCenterItem>): Float {
    if (items.isEmpty()) return 0f
    var downloaded = 0.0
    var total = 0.0
    items.forEach { item ->
        if (item.total > 0) {
            downloaded += item.downloaded.toDouble()
            total += item.total.toDouble()
        } else if (item.progress >= 0f) {
            downloaded += item.progress.toDouble()
            total += 1.0
        }
    }
    if (total <= 0.0) return 0f
    return (downloaded / total).toFloat().coerceIn(0f, 1f)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
