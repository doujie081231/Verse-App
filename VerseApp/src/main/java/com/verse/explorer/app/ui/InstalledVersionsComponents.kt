package com.verse.explorer.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.verse.explorer.app.ui.components.LittleTextLabel
import com.verse.explorer.app.ui.components.MarqueeText
import com.verse.explorer.app.ui.components.TextRailItem
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.theme.cardColor
import com.verse.explorer.app.ui.theme.onCardColor
import com.verse.explorer.app.ui.theme.onItemColor
import com.verse.explorer.app.ui.theme.itemColor
import com.verse.explorer.app.data.VersePersonalization
import com.verse.explorer.core.data.VersionMetaRepository
import com.verse.explorer.core.version.MinecraftVersion
import java.io.File

/**
 * 版本条目。
 *
 * RadioButton（选中态） + 版本图标 + 名称/摘要/信息行 + 置顶 + 设置 + 更多菜单。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VersionItemLayout(
    version: MinecraftVersion,
    selected: Boolean,
    meta: VersionMetaRepository.VersionMeta,
    pinnedToHome: Boolean,
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    color: Color = cardColor(influencedByBackground),
    contentColor: Color = onCardColor(),
    blur: Int = VersePersonalization.backgroundBlur,
    onSelected: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onToggleHome: (Boolean) -> Unit = {},
    onRenameClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onOpenFolder: (String) -> Unit = {},
    onTogglePin: (Boolean) -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    // 列表行不做入场缩放：滚动时每行重新组合都会起一个缩放动画并额外建图层，
    // 快速滑动时多行动画叠加会掉帧
    Surface(
        modifier = modifier,
        color = color,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        onClick = {
            if (selected) return@Surface
            onSelected()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = MaterialTheme.shapes.large)
                .backgroundGlass(blur, color, influencedByBackground)
                .padding(all = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = {
                    if (selected) return@RadioButton
                    onSelected()
                }
            )

            // 版本信息
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                VersionIcon(version = version, size = 34.dp)

                Column(modifier = Modifier.weight(1f)) {
                    MarqueeText(
                        text = version.id,
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (meta.summary.isNotBlank()) {
                        MarqueeText(
                            text = meta.summary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    FlowRow(
                        modifier = Modifier.alpha(0.7f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val baseVersion = version.inheritsFrom
                        if (!baseVersion.isNullOrBlank()) {
                            Text(
                                text = baseVersion,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        val hint = version.loaderHint
                        if (!hint.isNullOrBlank()) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (baseVersion.isNullOrBlank() && hint.isNullOrBlank()) {
                            LittleTextLabel(
                                text = versionTypeLabel(version),
                                textStyle = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // 置顶（图钉图标旋转 45°）
            IconButton(
                onClick = { onTogglePin(!meta.pinned) }
            ) {
                Crossfade(targetState = meta.pinned) { pinned ->
                    Icon(
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(45.0f),
                        imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "置顶"
                    )
                }
            }

            // 版本设置
            IconButton(onClick = onSettingsClick) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "版本设置"
                )
            }

            // 更多操作
            Box {
                var menuExpanded by remember { mutableStateOf(false) }

                IconButton(onClick = { menuExpanded = !menuExpanded }) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "更多"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    shape = MaterialTheme.shapes.large,
                    shadowElevation = 3.dp,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp)) },
                        onClick = {
                            onRenameClick()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (pinnedToHome) "从主页移除" else "添加到主页卡片") },
                        leadingIcon = {
                            Icon(
                                if (pinnedToHome) Icons.Filled.Home else Icons.Outlined.AddBox,
                                null, Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            onToggleHome(!pinnedToHome)
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出 (.zip)") },
                        leadingIcon = { Icon(Icons.Outlined.FolderZip, null, Modifier.size(20.dp)) },
                        onClick = {
                            onExportClick()
                            menuExpanded = false
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("打开版本目录") },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp)) },
                        onClick = {
                            onOpenFolder("")
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("打开 mods") },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp)) },
                        onClick = {
                            onOpenFolder("mods")
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("打开 saves") },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp)) },
                        onClick = {
                            onOpenFolder("saves")
                            menuExpanded = false
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("删除版本", color = VerseColors.PcRed) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(20.dp), tint = VerseColors.PcRed) },
                        onClick = {
                            onDeleteClick()
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

/** 版本分类条目 */
@Composable
internal fun VersionCategoryItem(
    modifier: Modifier = Modifier,
    value: VersionCategory,
    versionsCount: Int,
    selected: Boolean,
    shape: Shape = MaterialTheme.shapes.large,
    selectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    TextRailItem(
        modifier = modifier,
        text = {
            Text(text = value.label, style = style)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "($versionsCount)", style = style)
        },
        onClick = onClick,
        selected = selected,
        shape = shape,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
        enabled = enabled
    )
}

/** 左栏游戏目录胶囊：显示当前激活目录，⋯ 菜单切换 / 移除 / 打开 */
@Composable
internal fun GameDirPill(
    activePath: String?,
    defaultPath: String,
    addedDirs: List<String>,
    onSwitch: (String?) -> Unit,
    onRemove: (String) -> Unit,
    onOpen: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val currentPath = activePath ?: defaultPath
    val title = if (activePath == null) "默认游戏目录" else File(currentPath).name.ifBlank { currentPath }
    val color = itemColor()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        contentColor = onItemColor(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .backgroundGlass(VersePersonalization.backgroundBlur, color)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(text = title, style = MaterialTheme.typography.labelLarge)
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = currentPath,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "游戏目录管理"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    shape = MaterialTheme.shapes.large,
                    shadowElevation = 3.dp,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    GameDirMenuItem(
                        title = "默认游戏目录",
                        path = defaultPath,
                        selected = activePath == null,
                        onClick = {
                            menuExpanded = false
                            onSwitch(null)
                        }
                    )
                    addedDirs.forEach { path ->
                        GameDirMenuItem(
                            title = File(path).name.ifBlank { path },
                            path = path,
                            selected = activePath == path,
                            onClick = {
                                menuExpanded = false
                                onSwitch(path)
                            },
                            onRemove = {
                                menuExpanded = false
                                onRemove(path)
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("打开游戏目录") },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp)) },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                }
            }
        }
    }
}

/** 游戏目录菜单条目：名称 + 路径，附加目录可单独移除 */
@Composable
internal fun GameDirMenuItem(
    title: String,
    path: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(text = title, style = MaterialTheme.typography.labelLarge)
                Text(
                    modifier = Modifier.alpha(0.6f),
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, null, Modifier.size(20.dp)) }
        } else null,
        trailingIcon = if (onRemove == null) null else {
            {
                IconButton(onClick = onRemove) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "移除该目录"
                    )
                }
            }
        },
        onClick = onClick
    )
}

/** 左栏药丸按钮 */
@Composable
internal fun RailPillButton(text: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
