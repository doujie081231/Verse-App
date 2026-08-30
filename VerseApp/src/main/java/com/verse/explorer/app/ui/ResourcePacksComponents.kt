package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.verse.explorer.app.ui.components.GlassCard
import com.verse.explorer.app.ui.components.LabelWithIcon
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.core.resource.ModrinthSearchHit

private val resolutionRegex = Regex("\\d+x", RegexOption.IGNORE_CASE)

private fun extractResolution(categories: List<String>): String {
    return categories.firstOrNull { resolutionRegex.matches(it) } ?: ""
}

@Composable
internal fun ResourcePackCard(hit: ModrinthSearchHit, onClick: () -> Unit = {}) {
    val resolution = extractResolution(hit.categories)
    val gameVersion = hit.gameVersions.firstOrNull() ?: ""

    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcBgTertiary),
                contentAlignment = Alignment.Center
            ) {
                // 始终显示占位图标（加载失败时透出来）
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                // 有图标 URL 时叠加 AsyncImage 在上层
                val iconUrl = hit.iconUrl
                if (iconUrl != null && iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = VerseColors.PcTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (resolution.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VerseColors.PcPurple.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = resolution,
                                style = MaterialTheme.typography.labelSmall,
                                color = VerseColors.PcPurple,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "作者: ${hit.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcTextMuted,
                        fontSize = 11.5.sp
                    )
                    if (gameVersion.isNotEmpty()) {
                        Text(
                            text = "MC $gameVersion",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerseColors.PcTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = hit.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VerseColors.PcTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabelWithIcon(
                        icon = Icons.Outlined.Download,
                        text = formatDownloadCount(hit.downloads),
                        color = VerseColors.PcTextSecondary
                    )
                }
            }

            FilledTonalButton(
                onClick = {},
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = VerseColors.PcPurple.copy(alpha = 0.12f),
                    contentColor = VerseColors.PcPurple
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "安装",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
