package com.verse.explorer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.app.ui.theme.VerseColors

@Composable
fun JavaVersionRow(
    version: String,
    displayName: String,
    mcVersions: String,
    installed: Boolean,
    isInstalling: Boolean,
    installProgress: String,
    isDefault: Boolean,
    runtimePath: String? = null,
    onInstall: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        if (installed) listOf(VerseColors.PcGreen, VerseColors.PcGreen.copy(alpha = 0.6f))
                        else listOf(VerseColors.PcOrange, VerseColors.PcOrange.copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isInstalling) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (installed) Icons.Filled.Check else Icons.Filled.Code,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VerseColors.PcTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (isDefault && installed) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = VerseColors.PcAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "默认",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerseColors.PcAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "支持: $mcVersions",
                style = MaterialTheme.typography.labelSmall,
                color = VerseColors.PcTextSecondary,
                fontSize = 11.sp
            )
            if (installed && runtimePath != null) {
                Text(
                    text = runtimePath.let { if (it.length > 30) "...${it.takeLast(30)}" else it },
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcTextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isInstalling) {
                Text(
                    text = installProgress,
                    style = MaterialTheme.typography.labelSmall,
                    color = VerseColors.PcOrange,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        if (installed) {
            if (!isDefault) {
                TextButton(
                    onClick = onSetDefault,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "设为默认",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcGreen,
                        fontSize = 10.sp
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = VerseColors.PcTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = VerseColors.PcGreen.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "已安装",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerseColors.PcGreen,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            FilledTonalButton(
                onClick = onInstall,
                enabled = !isInstalling,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = VerseColors.PcOrange.copy(alpha = 0.12f),
                    contentColor = VerseColors.PcOrange,
                    disabledContainerColor = VerseColors.PcBgCard,
                    disabledContentColor = VerseColors.PcTextMuted
                )
            ) {
                if (isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = VerseColors.PcOrange,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Icon(Icons.Filled.Download, null, Modifier.size(12.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isInstalling) "安装中" else "安装",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
