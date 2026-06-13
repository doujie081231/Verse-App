package com.verse.explorer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Stripe 设计系统灵感 - 融合金融级精致与现代科技感
// 核心特色：Signature Purple (#533afd)、Deep Navy (#061b31)、蓝调阴影系统

object VerseColors {

    // ==================== 基础背景色 ====================
    val BgPrimary = Color(0xFFFFFFFF)
    val BgSecondary = Color(0xFFF8F9FC)
    val BgTertiary = Color(0xFFF1F3F8)
    val BgCard = Color(0xFFFFFFFF)
    val BgHover = Color(0xFFF4F6FB)
    val BgActive = Color(0xFFEEF1F7)

    // ==================== 深色主题背景 ====================
    val DarkBgPrimary = Color(0xFF0D0E14)
    val DarkBgSecondary = Color(0xFF13141D)
    val DarkBgTertiary = Color(0xFF1A1B26)
    val DarkBgCard = Color(0xFF1E1F2A)
    val DarkBgHover = Color(0xFF252635)
    val DarkBgActive = Color(0xFF2E3042)
    val SurfaceDark = Color(0xFF0D0E14)

    // ==================== 主色调 (Stripe Purple) ====================
    val PrimaryPurple = Color(0xFF533AFD)
    val PrimaryPurpleHover = Color(0xFF4434D4)
    val PrimaryPurpleLight = Color(0xFFB9B9F9)
    val PrimaryPurpleSoft = Color(0xFFD6D9FC)
    val PrimaryPurpleMid = Color(0xFF665EFD)

    // ==================== 强调色 ====================
    val AccentBlue = Color(0xFF2563EB)
    val AccentHover = Color(0xFF1D4ED8)
    val AccentGlow = Color(0x262563EB)

    // ==================== 深海军蓝 (Stripe Heading) ====================
    val DeepNavy = Color(0xFF061B31)
    val NavyLabel = Color(0xFF273951)
    val NavyBody = Color(0xFF64748B)

    // ==================== 功能色 ====================
    val GreenSuccess = Color(0xFF15BE53)
    val GreenSuccessBg = Color(0x3315BE53)
    val GreenSuccessText = Color(0xFF108C3D)
    val GreenSuccessBorder = Color(0x6615BE53)

    val RedDanger = Color(0xFFDC2626)
    val RedDangerBg = Color(0x33DC2626)
    val RedDangerHover = Color(0xFFB91C1C)
    val RedRuby = Color(0xFFEA2261)
    val RedMagenta = Color(0xFFF96BEE)

    val OrangeWarning = Color(0xFFEA580C)
    val OrangeWarningBg = Color(0x33EA580C)
    val Yellow = Color(0xFFB8860B)
    val YellowBg = Color(0x33B8860B)
    val PurpleAccent = Color(0xFF7C3AED)
    val PurpleAccentBg = Color(0x337C3AED)

    // ==================== 文字色 ====================
    val TextPrimary = Color(0xFF061B31)
    val TextSecondary = Color(0xFF4A4A64)
    val TextMuted = Color(0xFF7A7A94)
    val TextDim = Color(0xFFA0A0B4)
    val TextDisabled = Color(0xFFC5C8D4)

    val DarkTextPrimary = Color(0xFFF8F9FC)
    val DarkTextSecondary = Color(0xFFB8BCC8)
    val DarkTextMuted = Color(0xFF8B8FA8)
    val DarkTextDim = Color(0xFF6B6F84)

    // ==================== 边框色 ====================
    val BorderColor = Color(0xFFE5EDF5)
    val BorderPurple = Color(0xFFB9B9F9)
    val BorderSoftPurple = Color(0xFFD6D9FC)
    val BorderMagenta = Color(0xFFFFD7EF)
    val BorderDashed = Color(0xFF362BAA)
    val BorderSubtle = Color(0x0D000000)

    val DarkBorderColor = Color(0xFF2A2D3A)
    val DarkBorderSubtle = Color(0x1AFFFFFF)

    // ==================== 阴影色 (Stripe 多层蓝调阴影) ====================
    val ShadowBlue = Color(0x4032325D)  // 主阴影 - 蓝紫色调
    val ShadowDarkBlue = Color(0x40030327)  // 深色阴影
    val ShadowBlack = Color(0x1A000000)  // 纯黑阴影
    val ShadowAmbient = Color(0x140F0F17)  // 环境阴影
    val ShadowSoft = Color(0x0F0F0F17)  // 柔和阴影

    // ==================== 玻璃态 ====================
    val GlassPanelBg = Color(0xF5F5F5F7)
    val GlassCardBg = Color(0xE5FFFFFF)
    val GlassBorder = Color(0x33000000)

    val DarkGlassPanelBg = Color(0xF51E1F2A)
    val DarkGlassCardBg = Color(0xE5262835)
    val DarkGlassBorder = Color(0x33FFFFFF)

    // ==================== 覆盖层 ====================
    val OverlayBg = Color(0x99000000)
    val OverlayContentBg = Color(0xFFFFFFFF)
    val OverlayDarkContentBg = Color(0xFF1E1F2A)

    // ==================== 渐变色 ====================
    val GradientPurpleStart = Color(0xFF533AFD)
    val GradientPurpleEnd = Color(0xFF7C3AED)

    val GradientBlueStart = Color(0xFF2563EB)
    val GradientBlueEnd = Color(0xFF7C3AED)

    val GradientAmberStart = Color(0xFFEA580C)
    val GradientAmberEnd = Color(0xFFD97706)

    val GradientGreenStart = Color(0xFF15BE53)
    val GradientGreenEnd = Color(0xFF108C3D)

    val GradientRubyMagenta = listOf(Color(0xFFEA2261), Color(0xFFF96BEE))

    // ==================== 游戏平台色 ====================
    val FabricGreen = Color(0xFF30B25A)
    val ForgeColor = Color(0xFFEA580C)
    val NeoForgeRed = Color(0xFFEF4444)
    val QuiltPurple = Color(0xFFA855F7)

    // ==================== 徽章色 ====================
    val BadgeInstalledBg = Color(0x1A15BE53)
    val BadgeSnapshotBg = Color(0x1A533AFD)
    val BadgeAvailableBg = Color(0x1A737373)

    // ==================== 状态切换 ====================
    val SwitchTrackOff = Color(0xFFDDDDE5)
    val SwitchTrackOn = Color(0xFF533AFD)

    // ==================== 控制台 ====================
    val ConsoleBg = Color(0xFF1E1E2E)
    val ConsoleText = Color(0xFFCDD6F4)

    // ==================== 光照主题 ====================
    val SurfaceLight = Color(0xFFF8F9FC)
    val SurfaceBright = Color(0xFFFFFFFF)

    // ==================== 导航栏 ====================
    val NavBarItemActive = Color(0xFF533AFD)
    val NavBarItemInactive = Color(0xFF7A7A94)
    val NavBarIndicator = Color(0x1F533AFD)
    val NavBarBackground = Color(0xFFFFFFFF)

    // ==================== Minecraft 主题色 ====================
    val McGreen = Color(0xFF5D8C3E)
    val McGreenDark = Color(0xFF4A7030)
    val McGreenLight = Color(0xFF7BAF58)
    val McBrown = Color(0xFF8B6914)
    val McBrownDark = Color(0xFF6B4F0E)
    val McBrownLight = Color(0xFFA67F1A)
    val McDirt = Color(0xFF6B4226)
    val McDirtLight = Color(0xFF8B5A3A)
    val McStone = Color(0xFF7F7F7F)
    val McStoneLight = Color(0xFFA0A0A0)
    val McSand = Color(0xFFDBD3A0)
    val McWater = Color(0xFF3F76E4)
    val McWaterDark = Color(0xFF2A5BB0)
    val McLava = Color(0xFFCF4913)
    val McGold = Color(0xFFFFAA00)
    val McDiamond = Color(0xFF4AEDD9)
    val McRedstone = Color(0xFFAA0000)
    val McObsidian = Color(0xFF1B1028)
    val McNetherrack = Color(0xFF732828)
    val McEndStone = Color(0xFFEBEBB0)

    val McBg = Color(0xFF1E1E2A)
    val McDarkBg = Color(0xFF14141E)
    val McSidebarBg = Color(0xFF1A1A28)
    val McCardBg = Color(0xFF262638)
    val McCardBgLight = Color(0xFF2E2E44)
    val McCardBorder = Color(0xFF3A3A52)
    val McInputBg = Color(0xFF1C1C2C)

    val McTextPrimary = Color(0xFFE8E8E8)
    val McTextSecondary = Color(0xFFB0B0C0)
    val McTextMuted = Color(0xFF787890)
    val McTextDim = Color(0xFF555568)

    val McButtonBg = Color(0xFF5D8C3E)
    val McButtonHover = Color(0xFF6FA048)
    val McButtonDisabled = Color(0xFF3A3A52)
    val McButtonText = Color(0xFFFFFFFF)
    val McButtonDangerBg = Color(0xFFAA0000)
    val McButtonDangerHover = Color(0xFFCC2200)

    val McTabBg = Color(0xFF1C1C2C)
    val McTabActive = Color(0xFF5D8C3E)
    val McTabInactive = Color(0xFF787890)

    val McOverlayBg = Color(0xCC000000)
    val McOverlayContentBg = Color(0xFF262638)

    val McSwitchTrackOff = Color(0xFF3A3A52)
    val McSwitchTrackOn = Color(0xFF5D8C3E)

    val McBorder = Color(0xFF3A3A52)
    val McDivider = Color(0xFF2E2E44)

    // ==================== White Theme (亮色主题) ====================
    val WhiteBg = Color(0xFFFAFAFA)
    val WhiteBgSecondary = Color(0xFFF5F5F5)
    val WhiteBgCard = Color(0xFFFFFFFF)
    val WhiteBgHover = Color(0xFFF0F4F0)
    val WhiteBgInput = Color(0xFFF0F2F5)

    val WhiteTextPrimary = Color(0xFF1A1A2E)
    val WhiteTextSecondary = Color(0xFF4A4A5E)
    val WhiteTextMuted = Color(0xFF787890)
    val WhiteTextDim = Color(0xFFA0A0B4)

    val WhiteBorder = Color(0xFFE2E5EA)
    val WhiteBorderLight = Color(0xFFEEF0F4)
    val WhiteBorderFocus = Color(0xFF5D8C3E)

    val WhiteAccent = Color(0xFF5D8C3E)
    val WhiteAccentHover = Color(0xFF4A7030)
    val WhiteAccentLight = Color(0xFFE8F5E0)
    val WhiteAccentSoft = Color(0xFFF0F7EC)

    val WhiteDanger = Color(0xFFDC3545)
    val WhiteDangerLight = Color(0xFFFEE8EB)
    val WhiteSuccess = Color(0xFF28A745)
    val WhiteWarning = Color(0xFFFFC107)
    val WhiteInfo = Color(0xFF3F76E4)

    val WhiteCardShadow = Color(0x0D000000)
    val WhiteCardShadowHover = Color(0x1A000000)

    val WhiteSidebarBg = Color(0xFFF0F2F5)
    val WhiteSidebarActive = Color(0xFF5D8C3E)
    val WhiteSidebarText = Color(0xFF4A4A5E)

    val WhiteChipBg = Color(0xFFF0F2F5)
    val WhiteChipSelected = Color(0xFF5D8C3E)
    val WhiteChipText = Color(0xFF4A4A5E)

    val WhiteProgressTrack = Color(0xFFE2E5EA)
    val WhiteProgressFill = Color(0xFF5D8C3E)
}

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF533AFD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E4FF),
    onPrimaryContainer = Color(0xFF1A0050),
    secondary = Color(0xFF565E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF715573),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCD7FC),
    onTertiaryContainer = Color(0xFF29132E),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF061B31),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF061B31),
    surfaceVariant = Color(0xFFF1F3F8),
    onSurfaceVariant = Color(0xFF4A4A64),
    outline = Color(0xFFE5EDF5),
    outlineVariant = Color(0xFFE5EDF5),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF061B31),
    inverseOnSurface = Color(0xFFF8F9FC),
    inversePrimary = Color(0xFFB9B9F9),
    surfaceTint = Color(0xFF533AFD),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB9B9F9),
    onPrimary = Color(0xFF1A0050),
    primaryContainer = Color(0xFF2E2B8C),
    onPrimaryContainer = Color(0xFFE8E4FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283041),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDEBBDD),
    onTertiary = Color(0xFF3F2843),
    tertiaryContainer = Color(0xFF573E5B),
    onTertiaryContainer = Color(0xFFFCD7FC),
    background = Color(0xFF0D0E14),
    onBackground = Color(0xFFF8F9FC),
    surface = Color(0xFF1E1F2A),
    onSurface = Color(0xFFF8F9FC),
    surfaceVariant = Color(0xFF252635),
    onSurfaceVariant = Color(0xFFB8BCC8),
    outline = Color(0xFF2A2D3A),
    outlineVariant = Color(0xFF2A2D3A),
    error = Color(0xFFEF5350),
    onError = Color.White,
    errorContainer = Color(0xFF3C1111),
    onErrorContainer = Color(0xFFEF5350),
    inverseSurface = Color(0xFFF8F9FC),
    inverseOnSurface = Color(0xFF061B31),
    inversePrimary = Color(0xFF533AFD),
    surfaceTint = Color(0xFFB9B9F9),
)

object VerseTextStyles {
    val DisplayHero = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 50.sp,
        letterSpacing = (-0.96).sp
    )

    val DisplayLarge = TextStyle(
        fontSize = 40.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 46.sp,
        letterSpacing = (-0.8).sp
    )

    val SectionHeading = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 35.sp,
        letterSpacing = (-0.64).sp
    )

    val SubheadingLarge = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 29.sp,
        letterSpacing = (-0.26).sp
    )

    val Subheading = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 24.sp,
        letterSpacing = (-0.22).sp
    )

    val BodyLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 25.sp,
        letterSpacing = 0.sp
    )

    val Body = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )

    val BodySmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )

    val Button = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )

    val ButtonSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )

    val Link = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )

    val Caption = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )

    val CaptionSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )

    val LabelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
    )

    val LabelMicro = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 12.sp,
        letterSpacing = 0.1.sp
    )
}

@Composable
fun VerseExplorerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VerseTypography,
        content = content
    )
}