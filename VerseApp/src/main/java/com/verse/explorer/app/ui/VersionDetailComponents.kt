package com.verse.explorer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verse.explorer.core.domain.model.version.DetailLoaderOption
import com.verse.explorer.core.domain.model.version.LoaderVersion
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.ui.components.RoseLoader

@Composable
fun VersionTypeIcon(versionType: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bg = when {
        versionType.equals("snapshot", ignoreCase = true) -> VerseColors.PcOrange.copy(alpha = 0.15f)
        versionType.contains("old", ignoreCase = true) -> VerseColors.PcTextMuted.copy(alpha = 0.15f)
        else -> VerseColors.PcGreen.copy(alpha = 0.12f)
    }
    val iconPath = when {
        versionType.equals("snapshot", ignoreCase = true) -> "icons/version_icon_cobblestone.png"
        versionType.contains("old", ignoreCase = true) -> "icons/version_icon_old.png"
        else -> "icons/version_icon_grass.png"
    }
    val iconBitmap = remember(iconPath) {
        try {
            context.assets.open(iconPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun VersionDetailSectionTitle(title: String) {
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = VerseColors.PcTextPrimary
    )
}

@Composable
fun DownloadSourceOption(
    id: String,
    name: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VerseColors.PcAccent.copy(alpha = 0.06f) else VerseColors.PcBgCard,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, VerseColors.PcAccent.copy(alpha = 0.4f))
        else androidx.compose.foundation.BorderStroke(1.dp, VerseColors.PcBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = VerseColors.PcAccent)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.PcTextPrimary)
                Text(desc, fontSize = 11.sp, color = VerseColors.PcTextMuted)
            }
            if (selected) {
                Icon(Icons.Filled.Check, null, Modifier.size(16.dp), VerseColors.PcAccent)
            }
        }
    }
}

@Composable
fun LoaderCard(
    loader: DetailLoaderOption,
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(loader.iconAssetPath) {
        try {
            context.assets.open(loader.iconAssetPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VerseColors.PcAccent.copy(alpha = 0.06f) else VerseColors.PcBgCard,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, VerseColors.PcAccent.copy(alpha = 0.4f))
        else androidx.compose.foundation.BorderStroke(1.dp, VerseColors.PcBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VerseColors.PcBgHover),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    RoseLoader(
                        modifier = Modifier.size(18.dp),
                        size = 18.dp,
                        color = VerseColors.PcAccent
                    )
                } else if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(loader.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VerseColors.PcTextPrimary, maxLines = 1)
                Text(loader.desc, fontSize = 11.sp, color = VerseColors.PcTextMuted, maxLines = 1)
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), VerseColors.PcAccent)
            } else {
                Box(
                    Modifier
                        .size(20.dp)
                        .border(1.5.dp, VerseColors.PcBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {}
            }
        }
    }
}

@Composable
fun LoaderVersionCard(
    loader: DetailLoaderOption,
    version: LoaderVersion,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(loader.iconAssetPath) {
        try {
            context.assets.open(loader.iconAssetPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) VerseColors.PcAccent.copy(alpha = 0.06f) else VerseColors.PcBgCard,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, VerseColors.PcAccent.copy(alpha = 0.4f))
        else androidx.compose.foundation.BorderStroke(1.dp, VerseColors.PcBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(VerseColors.PcBgHover),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${loader.name} ${version.version}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = VerseColors.PcTextPrimary
                )
                if (version.type.isNotEmpty()) {
                    Text(version.type, fontSize = 10.sp, color = VerseColors.PcTextMuted)
                }
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), VerseColors.PcAccent)
            } else {
                Box(
                    Modifier
                        .size(18.dp)
                        .border(1.5.dp, VerseColors.PcBorder, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {}
            }
        }
    }
}
