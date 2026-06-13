package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.verse.explorer.app.ui.theme.VerseColors

data class ResourceItem(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val category: String = "mod",
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = true,
    val size: String = "",
    val downloads: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceManagementScreen(navController: NavHostController) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf("grid") }

    val tabs = listOf("模组", "整合包", "材质包", "光影")
    val categories = listOf("mod", "modpack", "resourcepack", "shader")
    val tabIcons = listOf(Icons.Filled.Extension, Icons.Filled.Inventory2, Icons.Filled.Palette, Icons.Filled.Flare)
    val tabColors = listOf(VerseColors.ForgeColor, VerseColors.McGold, VerseColors.McWater, VerseColors.PrimaryPurple)

    // Sample data - in production these would come from repositories
    val sampleMods = remember {
        listOf(
            ResourceItem("1", "OptiFine", "高清优化与光影支持", "HD U H9", "sp614x", "mod", true, true, "2.1 MB", 52000000),
            ResourceItem("2", "Just Enough Items", "物品配方查询", "12.0.0", "mezz", "mod", true, true, "1.8 MB", 48000000),
            ResourceItem("3", "Sodium", "渲染引擎优化", "0.5.8", "jellysquid3", "mod", false, false, "0.8 MB", 15000000),
            ResourceItem("4", "Create", "机械动力", "0.5.1", "simibubi", "mod", true, true, "12.5 MB", 28000000),
            ResourceItem("5", "Xaero's Minimap", "小地图", "24.1.0", "xaero96", "mod", true, true, "1.2 MB", 35000000),
            ResourceItem("6", "Iris Shaders", "光影加载器", "1.7.0", "coderbot", "mod", false, false, "1.5 MB", 12000000)
        )
    }
    val sampleModpacks = remember {
        listOf(
            ResourceItem("mp1", "Better MC", "优化整合包", "4.0", "SHXRKIE", "modpack", true, true, "350 MB", 8000000),
            ResourceItem("mp2", "RLCraft", "硬核生存整合包", "2.9.3", "Shivaxi", "modpack", false, false, "480 MB", 12000000)
        )
    }
    val sampleResourcePacks = remember {
        listOf(
            ResourceItem("rp1", "Faithful 32x", "经典高清材质", "1.21", "Faithful Team", "resourcepack", true, true, "45 MB", 22000000),
            ResourceItem("rp2", "Bare Bones", "简约卡通风格", "1.20", "RobotPants", "resourcepack", false, false, "12 MB", 5000000)
        )
    }
    val sampleShaders = remember {
        listOf(
            ResourceItem("s1", "BSL Shaders", "写实光影", "8.4", "capttatsu", "shader", true, true, "2.3 MB", 18000000),
            ResourceItem("s2", "Complementary", "高性能光影", "4.10", "EminGT", "shader", false, false, "3.1 MB", 9000000)
        )
    }

    val currentItems = remember(selectedTab) {
        when (selectedTab) { 0 -> sampleMods; 1 -> sampleModpacks; 2 -> sampleResourcePacks; 3 -> sampleShaders; else -> emptyList() }
    }
    val filteredItems = remember(currentItems, searchQuery) {
        if (searchQuery.isBlank()) currentItems
        else currentItems.filter { it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
    }
    val installedCount = currentItems.count { it.isInstalled }
    val totalCount = currentItems.size

    Box(modifier = Modifier.fillMaxSize().background(VerseColors.McBg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 12.dp)) {
            PageHeader(title = "资源管理", subtitle = "管理模组、整合包、材质和光影")

            Spacer(Modifier.height(10.dp))

            // Stats bar
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = VerseColors.McCardBg) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("已安装", "$installedCount", VerseColors.McGreen, Icons.Filled.CheckCircle)
                    StatItem("可用", "${totalCount - installedCount}", VerseColors.McWater, Icons.Filled.CloudDownload)
                    StatItem("总计", "$totalCount", VerseColors.McTextSecondary, Icons.Filled.Inventory2)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tab row
            Surface(Modifier.fillMaxWidth().wrapContentHeight(), RoundedCornerShape(10.dp), color = VerseColors.McCardBgLight) {
                Row(Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    tabs.forEachIndexed { idx, label ->
                        val isSelected = idx == selectedTab
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) tabColors[idx] else Color.Transparent)
                                .clickable { selectedTab = idx }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(tabIcons[idx], null, Modifier.size(14.dp), if (isSelected) Color.White else VerseColors.McTextSecondary)
                                Spacer(Modifier.width(4.dp))
                                Text(label, fontSize = 11.sp, color = if (isSelected) Color.White else VerseColors.McTextSecondary, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Search + View toggle
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f).height(42.dp),
                    placeholder = { Text("搜索 ${tabs[selectedTab]}...", fontSize = 12.sp, color = VerseColors.McTextDim) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), VerseColors.McTextMuted) },
                    trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(18.dp)) { Icon(Icons.Outlined.Close, null, Modifier.size(14.dp), VerseColors.McTextMuted) } },
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerseColors.McGreen.copy(alpha = 0.3f), unfocusedBorderColor = VerseColors.McCardBorder,
                        focusedContainerColor = VerseColors.McCardBg, unfocusedContainerColor = VerseColors.McCardBg,
                        focusedTextColor = VerseColors.McTextPrimary, unfocusedTextColor = VerseColors.McTextPrimary, cursorColor = VerseColors.McGreen
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
                IconButton(onClick = { viewMode = if (viewMode == "grid") "list" else "grid" }, modifier = Modifier.size(36.dp)) {
                    Icon(if (viewMode == "grid") Icons.Filled.ViewList else Icons.Filled.GridView, null, Modifier.size(20.dp), VerseColors.McTextSecondary)
                }
                FilledTonalButton(onClick = { navController.navigate(Screen.Mods.route) }, shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = VerseColors.McGreen.copy(alpha = 0.12f), contentColor = VerseColors.McGreen)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("添加", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Results count
            Text("共 ${filteredItems.size} 个结果", fontSize = 11.sp, color = VerseColors.McTextMuted, modifier = Modifier.padding(start = 4.dp))

            Spacer(Modifier.height(6.dp))

            // Content
            if (filteredItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(tabIcons[selectedTab], null, Modifier.size(48.dp), VerseColors.McTextDim)
                        Spacer(Modifier.height(12.dp))
                        Text("暂无${tabs[selectedTab]}", color = VerseColors.McTextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("点击「添加」浏览并安装", color = VerseColors.McTextMuted, fontSize = 12.sp)
                    }
                }
            } else if (viewMode == "grid") {
                LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(filteredItems, key = { it.id }) { item -> ResourceGridCard(item, tabColors[selectedTab], navController) }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
                    items(filteredItems, key = { it.id }) { item -> ResourceListItem(item, tabColors[selectedTab], navController) }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(14.dp), color)
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(value, color = VerseColors.McTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, color = VerseColors.McTextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ResourceGridCard(item: ResourceItem, accentColor: Color, navController: NavHostController) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = VerseColors.McCardBg) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(when (item.category) { "modpack" -> Icons.Filled.Inventory2; "resourcepack" -> Icons.Filled.Palette; "shader" -> Icons.Filled.Flare; else -> Icons.Filled.Extension }, null, Modifier.size(20.dp), accentColor)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.version, color = VerseColors.McTextMuted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(item.description, color = VerseColors.McTextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(4.dp), color = if (item.isInstalled) VerseColors.McGreen.copy(alpha = 0.1f) else VerseColors.McTextDim.copy(alpha = 0.1f)) {
                    Text(if (item.isInstalled) "已安装" else "可安装", fontSize = 9.sp, color = if (item.isInstalled) VerseColors.McGreen else VerseColors.McTextMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (item.size.isNotBlank()) Text(item.size, fontSize = 9.sp, color = VerseColors.McTextDim)
            }
        }
    }
}

@Composable
private fun ResourceListItem(item: ResourceItem, accentColor: Color, navController: NavHostController) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(10.dp), color = VerseColors.McCardBg) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(when (item.category) { "modpack" -> Icons.Filled.Inventory2; "resourcepack" -> Icons.Filled.Palette; "shader" -> Icons.Filled.Flare; else -> Icons.Filled.Extension }, null, Modifier.size(22.dp), accentColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, color = VerseColors.McTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (item.isInstalled) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(3.dp), color = VerseColors.McGreen.copy(alpha = 0.1f)) {
                            Text("已安装", fontSize = 8.sp, color = VerseColors.McGreen, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(item.description + " · v${item.version}", color = VerseColors.McTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), VerseColors.McTextMuted)
        }
    }
}
