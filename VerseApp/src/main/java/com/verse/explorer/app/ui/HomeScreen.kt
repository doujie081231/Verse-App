package com.verse.explorer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.verse.explorer.app.data.SettingsRepository
import com.verse.explorer.app.launch.GameLaunchController
import com.verse.explorer.app.launch.LaunchPhase
import com.verse.explorer.app.ui.components.AccountAvatar
import com.verse.explorer.app.ui.components.PcLaunchOverlay
import com.verse.explorer.app.ui.home.VersionCardManager
import com.verse.explorer.app.ui.theme.VerseColors
import com.verse.explorer.app.viewmodel.AccountsViewModel
import com.verse.explorer.core.account.AccountType
import com.verse.explorer.core.config.GameConfig
import com.verse.explorer.core.version.MinecraftVersion
import com.verse.explorer.core.version.VersionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * ??:?? 1/3 ????????(????:? 1/3 ?????????),
 * ?? 2/3 ??????? + ??????(????,????)?
 */
@Composable
fun HomeScreen(
    navController: NavHostController = rememberNavController(),
    onLaunchGame: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val launchController = remember { GameLaunchController(context) }
    val launchState by launchController.launchState.collectAsState()
    val scope = rememberCoroutineScope()

    var localVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var selectedVersionId by remember { mutableStateOf<String?>(settingsLastVersion()) }
    var selectedAccount by remember { mutableStateOf<com.verse.explorer.core.account.Account?>(null) }
    var runningInstance by remember { mutableStateOf<RunningGame?>(null) }

    // ?????? -> ?????
    LaunchedEffect(launchState.phase) {
        when (launchState.phase) {
            LaunchPhase.LAUNCHING -> {
                if (runningInstance == null && selectedVersionId != null) {
                    runningInstance = RunningGame(
                        versionId = selectedVersionId!!,
                        pid = android.os.Process.myPid(),
                        startTime = System.currentTimeMillis()
                    )
                }
            }
            LaunchPhase.IDLE, LaunchPhase.FAILED -> runningInstance = null
            else -> {}
        }
    }

    // ????
    LaunchedEffect(Unit) {
        val vm = AccountsViewModel(context.applicationContext as android.app.Application)
        vm.loadAccounts()
        vm.selectedAccount.collect { selectedAccount = it }
    }

    // ??????(???????????,?????????? last_version)
    LaunchedEffect(navController.currentBackStackEntry) {
        val route = navController.currentBackStackEntry?.destination?.route
        scope.launch {
            try {
                val vm = VersionManager(GameConfig.getGameDir(context))
                val versions = withContext(Dispatchers.IO) { vm.getLocalVersions() }
                localVersions = versions
                // ?????????,??????????
                if (route == Screen.Home.route) {
                    selectedVersionId = settingsLastVersion() ?: selectedVersionId
                }
                if (selectedVersionId == null && versions.isNotEmpty()) {
                    selectedVersionId = versions.first().id
                }
                // ?????;????????????
                VersionCardManager.initialize(context)
                VersionCardManager.refresh(context, versions)
                if (!VersionCardManager.hasSeeded(context) && versions.isNotEmpty()) {
                    val seed = settingsLastVersion()?.takeIf { last -> versions.any { it.id == last } }
                        ?: versions.first().id
                    VersionCardManager.addCard(context, seed)
                    VersionCardManager.markSeeded(context)
                }
            } catch (_: Exception) {}
        }
    }

    val selected = localVersions.find { it.id == selectedVersionId }

    Box(modifier = Modifier.fillMaxSize()) {
        HomePageContent(
            account = selectedAccount,
            selected = selected,
            isLaunching = launchState.isLaunching,
            onGoAccounts = { navController.navigate(Screen.Accounts.route) },
            onOpenVersions = { navController.navigate("installed_versions") },
            onLaunch = { if (!launchState.isLaunching) launchController.launchGame(selectedVersionId) }
        )

        // ??????(?? + ????,????)
        if (launchState.isLaunching || launchState.errors.isNotEmpty() || launchState.phase == LaunchPhase.FAILED) {
            PcLaunchOverlay(
                launchState = launchState
            )
        }
    }
}

private fun settingsLastVersion(): String? = SettingsRepository.getCustomSetting("last_version")

@Composable
fun SectionTitle(title: String) {
    Text(title, color = VerseColors.PcTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
}

/** ??????? */
data class RunningGame(
    val versionId: String,
    val pid: Int,
    val startTime: Long
)

/**
 * ??????:
 * - ?? 2/3(??):?????? + ??????,????,??????????
 * - ?? 1/3:???????,?????? 1/3 ?????????,?????????
 */
@Composable
private fun HomePageContent(
    account: com.verse.explorer.core.account.Account?,
    selected: MinecraftVersion?,
    isLaunching: Boolean,
    onGoAccounts: () -> Unit,
    onOpenVersions: () -> Unit,
    onLaunch: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val avatarSize = 96.dp
        val density = LocalDensity.current
        val avatarSizePx = with(density) { avatarSize.toPx() }

        // ===== ?? 2/3:?????? + ??????(??) =====
        Row(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .padding(end = 28.dp),
                verticalArrangement = Arrangement.Center
            ) {
                HomeVersionCardLarge(selected = selected, onClick = onOpenVersions)
                Spacer(Modifier.height(18.dp))
                HomeLaunchButtonLarge(isLaunching = isLaunching, enabled = selected != null, onClick = onLaunch)
            }
        }

        // ===== ?? 1/3:?????(??:? 1/3 ?????????) =====
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        val baseX = screenW / 6f - avatarSizePx / 2f
        val baseY = screenH / 2f - avatarSizePx / 2f
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (baseX + dragOffset.x).roundToInt(),
                        (baseY + dragOffset.y).roundToInt()
                    )
                }
                .size(avatarSize)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset = Offset(
                                (dragOffset.x + dragAmount.x)
                                    .coerceIn(-baseX, screenW - avatarSizePx - baseX),
                                (dragOffset.y + dragAmount.y)
                                    .coerceIn(-baseY, screenH - avatarSizePx - baseY)
                            )
                        }
                    )
                }
        ) {
            HomeAvatar(account = account, onClick = onGoAccounts)
        }
    }
}

/**
 * ???:???????"????"?;?????????
 */
@Composable
internal fun HomeAvatar(
    account: com.verse.explorer.core.account.Account?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val sizeModifier = Modifier.fillMaxSize()
    if (account == null) {
        // ???:??? + ?? + ????
        Box(
            modifier = sizeModifier
                .clip(shape)
                .background(Color(0x0BFFFFFF))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = VerseColors.PcTextMuted,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text("????", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerseColors.PcTextMuted)
            }
            // ????
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Color.Transparent)
                    .then(Modifier.border(2.5.dp, VerseColors.PcBorder, shape))
            )
        }
    } else {
        Box(
            modifier = sizeModifier
                .clip(shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            AccountAvatar(account = account, modifier = Modifier.fillMaxSize(), shape = shape)
        }
    }
}

/**
 * ??????(???):?? + ?? + ?? + ??,???????
 * ???????????????
 */
@Composable
private fun HomeVersionCardLarge(
    selected: MinecraftVersion?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = VerseColors.PcGlassCardBg,
        border = BorderStroke(1.dp, VerseColors.PcBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selected == null) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VerseColors.PcBgTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = VerseColors.PcTextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "????",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerseColors.PcTextPrimary
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "??????????",
                        fontSize = 13.sp,
                        color = VerseColors.PcTextMuted
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp), VerseColors.PcTextMuted)
            } else {
                VersionIcon(version = selected, size = 52.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected.id,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VerseColors.PcTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    LoaderBadge(version = selected)
                }
                Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp), VerseColors.PcTextMuted)
            }
        }
    }
}

/**
 * ????:?????????(???? versions/<id>/modpack_icon.png),
 * ???????(hint/??),?????????(grass/cobblestone/old)
 */
@Composable
internal fun VersionIcon(version: MinecraftVersion, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    // ???????(???????? versions/<id>/modpack_icon.png)
    val packIconBitmap = remember(version.id) {
        try {
            val packIcon = File(
                GameConfig.getVersionsDir(GameConfig.getGameDir(context)),
                version.id
            ).resolve("modpack_icon.png")
            if (packIcon.exists() && packIcon.length() > 0) {
                android.graphics.BitmapFactory.decodeFile(packIcon.absolutePath)?.asImageBitmap()
            } else null
        } catch (e: Exception) { null }
    }
    val lower = version.id.lowercase()
    val hint = version.loaderHint?.lowercase()
    val loaderAsset = hint?.let { h ->
        when (h) {
            "fabric" -> "icons/loader_icon_fabric.png"
            "quilt" -> "icons/loader_icon_quilt.png"
            "neoforge" -> "icons/loader_icon_neoforge.png"
            "forge" -> "icons/loader_icon_forge.png"
            "optifine" -> "icons/loader_icon_optifine.png"
            else -> null
        }
    } ?: when {
        lower.contains("fabric") -> "icons/loader_icon_fabric.png"
        lower.contains("quilt") -> "icons/loader_icon_quilt.png"
        lower.contains("neoforge") -> "icons/loader_icon_neoforge.png"
        lower.contains("forge") -> "icons/loader_icon_forge.png"
        lower.contains("optifine") -> "icons/loader_icon_optifine.png"
        else -> null
    }
    val iconAssetPath = loaderAsset ?: when (version.type) {
        com.verse.explorer.core.version.VersionType.SNAPSHOT -> "icons/version_icon_cobblestone.png"
        com.verse.explorer.core.version.VersionType.OLD_ALPHA,
        com.verse.explorer.core.version.VersionType.OLD_BETA -> "icons/version_icon_old.png"
        else -> "icons/version_icon_grass.png"
    }
    val iconBg = when (version.type) {
        com.verse.explorer.core.version.VersionType.SNAPSHOT -> VerseColors.PcOrange.copy(alpha = 0.15f)
        com.verse.explorer.core.version.VersionType.OLD_ALPHA,
        com.verse.explorer.core.version.VersionType.OLD_BETA -> VerseColors.PcPurple.copy(alpha = 0.15f)
        else -> VerseColors.PcGreen.copy(alpha = 0.15f)
    }
    val assetBitmap = remember(iconAssetPath) {
        try {
            context.assets.open(iconAssetPath).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }
    val iconBitmap = packIconBitmap ?: assetBitmap
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(iconBg),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(size * 0.9f), contentScale = ContentScale.Fit)
        }
    }
}

/**
 * ?????(??/Forge/Fabric/NeoForge/???)?
 * ???? json ??????(loaderHint),???????????
 */
@Composable
internal fun LoaderBadge(version: MinecraftVersion) {
    val hint = version.loaderHint?.lowercase()
    val (badge, color) = if (!hint.isNullOrBlank()) {
        when (hint) {
            "fabric" -> "Fabric" to VerseColors.ForgeColor
            "quilt" -> "Quilt" to VerseColors.PcAccent
            "neoforge" -> "NeoForge" to VerseColors.PcOrange
            "forge" -> "Forge" to VerseColors.PcOrange
            "optifine" -> "OptiFine" to VerseColors.PcYellow
            else -> "??" to VerseColors.PcTextSecondary
        }
    } else {
        val lower = version.id.lowercase()
        when {
            lower.contains("fabric") -> "Fabric" to VerseColors.ForgeColor
            lower.contains("forge") -> "Forge" to VerseColors.PcOrange
            lower.contains("neoforge") -> "NeoForge" to VerseColors.PcOrange
            lower.contains("modpack") || lower.contains("??") -> "???" to VerseColors.PcGreen
            else -> "??" to VerseColors.PcTextSecondary
        }
    }
    Surface(shape = RoundedCornerShape(3.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text = badge,
            fontSize = 9.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

/**
 * ??????(???:?????????)
 */
@Composable
private fun HomeLaunchButtonLarge(isLaunching: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val textColor = if (enabled && !isLaunching) {
        VerseColors.PcAccentText
    } else {
        VerseColors.PcAccentText.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(listOf(VerseColors.PcLaunchGradientStart, VerseColors.PcLaunchGradientEnd))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled && !isLaunching,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = if (isLaunching) "???..." else "????",
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}
