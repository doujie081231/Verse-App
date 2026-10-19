package com.verse.explorer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.ui.influencedByBackgroundColor

/**
 * ZL2 语义色 token 层（对位 ZalithLauncher2 的 ui/theme/Palette.kt）。
 *
 * 这些函数是 ZL2 全部页面与组件的取色入口：页面不再直接读取 MaterialTheme.colorScheme，
 * 而是通过 backgroundColor / cardColor / itemColor / cardTitleColor 取得语义色，
 * 以保证「背景卡片 / 卡片标题栏 / 卡片内条目」三层容器色在 ZL2 中一致。
 */

/** 应用整体背景的颜色 */
@Composable
fun backgroundColor(): Color = MaterialTheme.colorScheme.surfaceContainer

/** 背景之上的正文颜色 */
@Composable
fun onBackgroundColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * 卡片背景颜色（对位 ZL2 cardColor）
 * @param influencedByBackground 是否受背景内容影响，更改自身不透明度
 */
@Composable
fun cardColor(
    influencedByBackground: Boolean = true
): Color = influencedByBackgroundColor(
    color = MaterialTheme.colorScheme.surfaceBright,
    enabled = influencedByBackground
)

/** 卡片内容颜色 */
@Composable
fun onCardColor(): Color = MaterialTheme.colorScheme.onSurface

/**
 * 卡片顶部 Title 的背景颜色，半透明的 surface
 */
@Composable
fun cardTitleColor(
    alpha: Float = 0.5f
): Color = MaterialTheme.colorScheme.surface.copy(alpha = alpha)

/**
 * 卡片上的 Item 背景颜色（对位 ZL2 itemColor）
 * @param influencedByBackground 是否受背景内容影响，更改自身不透明度
 */
@Composable
fun itemColor(
    influencedByBackground: Boolean = true,
    isDark: Boolean = isVerseInDarkTheme()
): Color {
    return influencedByBackgroundColor(
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        },
        enabled = influencedByBackground
    )
}

/** 卡片上的 Item 内容颜色 */
@Composable
fun onItemColor(): Color = MaterialTheme.colorScheme.onSurface
