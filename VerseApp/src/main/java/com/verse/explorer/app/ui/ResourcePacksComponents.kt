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
    ResourceSearchCard(hit, onClick = onClick)
}
