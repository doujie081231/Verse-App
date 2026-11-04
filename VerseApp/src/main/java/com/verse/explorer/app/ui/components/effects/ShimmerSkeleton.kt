package com.verse.explorer.app.ui.components.effects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.Shimmer
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 资源列表骨架屏。
 *
 * 样式刻意与 `ResourceSearchCard` 对齐（同高卡片 / 36dp 图标 / 两行文字），
 * 这样数据到手后列表不会发生整页跳动，骨架到内容的过渡是"同一块区域填充"。
 *
 * 只在「首屏加载且没有任何数据」时使用：已有数据时继续显示旧列表 + 底部小进度圈，
 * 避免翻页或搜索时把已有结果擦掉。
 */
@Composable
fun ResourceListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 9
) {
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.Window)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(rows) { index ->
            ResourceRowPlaceholder(shimmer, index)
        }
    }
}

@Composable
private fun ResourceRowPlaceholder(shimmer: Shimmer, index: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = VerseColors.PcBgCard,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcBgActive)
                    .shimmer(shimmer)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 标题行长度做轻微错落，避免一整列完全等宽显得像渲染错误
                val titleFraction = when (index % 4) {
                    0 -> 0.62f
                    1 -> 0.78f
                    2 -> 0.52f
                    else -> 0.70f
                }
                SkeletonBar(
                    widthFraction = titleFraction,
                    height = 12.dp,
                    shimmer = shimmer
                )
                Spacer(Modifier.height(6.dp))
                SkeletonBar(
                    widthFraction = 0.32f,
                    height = 9.dp,
                    shimmer = shimmer
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(VerseColors.PcBgActive)
                    .shimmer(shimmer)
            )
        }
    }
}

/** 版本列表 / 已装模组这类"图标 + 两行"的密集列表骨架 */
@Composable
fun CompactListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 6
) {
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.Window)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(rows) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcBgCard)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(VerseColors.PcBgActive)
                        .shimmer(shimmer)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBar(
                        widthFraction = if (index % 2 == 0) 0.55f else 0.42f,
                        height = 11.dp,
                        shimmer = shimmer
                    )
                    Spacer(Modifier.height(5.dp))
                    SkeletonBar(widthFraction = 0.26f, height = 8.dp, shimmer = shimmer)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    widthFraction: Float,
    height: Dp,
    shimmer: Shimmer
) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(VerseColors.PcBgTertiary)
            .shimmer(shimmer)
    )
}
