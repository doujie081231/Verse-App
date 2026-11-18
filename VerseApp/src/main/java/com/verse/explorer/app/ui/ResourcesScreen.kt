package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.theme.VerseColors

/**
 * 资源中心：左侧分类栏 + 右侧原有页面。
 *
 * 原来的「下载」入口并进来，作为第一个分类；右侧直接复用各页
 * （版本下载 / 整合包 / 模组 / 材质包 / 光影），保持用户已经熟悉的内容与交互，
 * 只把「在哪下载」这件事收敛成一处。
 *
 * 视觉上刻意只用黑白灰：底色卡片 + 1dp 描边，选中项「前景色填充 + 底色文字」反相，
 * 不引入强调色，和新顶部导航保持一致。
 */
private data class ResourceEntry(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val RESOURCE_ENTRIES = listOf(
    ResourceEntry("版本", Icons.Outlined.Download, "resources_versions"),
    ResourceEntry("整合包", Icons.Outlined.Inventory2, "resources_modpacks"),
    ResourceEntry("模组", Icons.Outlined.Extension, "resources_mods"),
    ResourceEntry("材质包", Icons.Outlined.Palette, "resources_resourcepacks"),
    ResourceEntry("光影", Icons.Outlined.Flare, "resources_shaders")
)

@Composable
fun ResourcesScreen(navController: NavHostController) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左栏：分类
        Column(
            modifier = Modifier
                .width(132.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "资源中心",
                color = VerseColors.PcTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )
            RESOURCE_ENTRIES.forEachIndexed { index, entry ->
                ResourceRailItem(
                    entry = entry,
                    active = selected == index,
                    onClick = { selected = index }
                )
            }
            Spacer(Modifier.height(8.dp))
            // 分隔线：把「下载类」与将来的收藏/历史留出位置（当前只做分隔，不放占位项）
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VerseColors.PcBorderSubtle)
            )
        }

        // 右栏：原有页面，按分类复用
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (selected) {
                0 -> VersionsScreen(navController)
                1 -> ModpacksScreen(navController)
                2 -> ModsScreen(navController)
                3 -> ResourcePacksScreen(navController)
                4 -> ShadersScreen(navController)
            }
        }
    }
}

@Composable
private fun ResourceRailItem(
    entry: ResourceEntry,
    active: Boolean,
    onClick: () -> Unit
) {
    val background = if (active) VerseColors.PcTextPrimary else Color.Transparent
    val foreground = if (active) VerseColors.PcBgPrimary else VerseColors.PcTextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(
                if (active) Modifier
                else Modifier.border(1.dp, VerseColors.PcBorderSubtle, RoundedCornerShape(10.dp))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(entry.icon, entry.label, Modifier.size(16.dp), tint = foreground)
        Text(
            text = entry.label,
            color = foreground,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
