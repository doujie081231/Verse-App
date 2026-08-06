package com.verse.explorer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VerseColors {

    // ==================== 游戏平台色 ====================
    val FabricGreen = Color(0xFF30B25A)
    val ForgeColor = Color(0xFFEA580C)
    val NeoForgeRed = Color(0xFFEF4444)
    val QuiltPurple = Color(0xFFA855F7)

    // ==================== Minecraft 主题色（仅保留实际被引用的项）====================
    val McGreen = Color(0xFF5D8C3E)
    val McGreenDark = Color(0xFF4A7030)
    val McGreenLight = Color(0xFF7BAF58)
    val McLava = Color(0xFFCF4913)
    val McGold = Color(0xFFFFAA00)
    val McRedstone = Color(0xFFAA0000)
    val McWater = Color(0xFF3F76E4)

    val McBg = Color(0xFF1E1E2A)
    val McDarkBg = Color(0xFF14141E)
    val McCardBg = Color(0xFF262638)
    val McCardBgLight = Color(0xFF2E2E44)
    val McCardBorder = Color(0xFF3A3A52)
    val McOverlayContentBg = Color(0xFF262638)

    val McTextPrimary = Color(0xFFE8E8E8)
    val McTextSecondary = Color(0xFFB0B0C0)
    val McTextMuted = Color(0xFF787890)
    val McTextDim = Color(0xFF555568)

    // ==================== PC 端主题色（对齐 VersePC light 主题）====================
    val PcBgPrimary = Color(0xFFFFFFFF)       // 主背景
    val PcBgSecondary = Color(0xFFF5F5F7)     // 次背景
    val PcBgTertiary = Color(0xFFEEEEF0)      // 三级背景（输入框、tab 组）
    val PcBgCard = Color(0xFFFAFAFA)          // 卡片背景
    val PcBgHover = Color(0xFFE8E8ED)         // 悬停背景
    val PcBgActive = Color(0xFFDDDDE5)        // 激活背景

    val PcTextPrimary = Color(0xFF1A1A1A)     // 主文字
    val PcTextSecondary = Color(0xFF4A4A4A)   // 次文字
    val PcTextMuted = Color(0xFF8A8A8A)       // 弱文字

    val PcAccent = Color(0xFF1A1A1A)          // 强调色（黑色，非品牌色）
    val PcAccentHover = Color(0xFF333333)     // 强调悬停
    val PcAccentText = Color(0xFFFFFFFF)      // 强调色上的文字
    val PcAccentGlow = Color(0x1A000000)      // 强调光晕（10% 黑）

    val PcBorder = Color(0x1A000000)          // 边框（10% 黑）
    val PcBorderSubtle = Color(0x0D000000)    // 细边框（5% 黑）

    val PcGreen = Color(0xFF16A34A)           // 成功/启动按钮
    val PcRed = Color(0xFFDC2626)             // 危险/错误
    val PcOrange = Color(0xFFEA580C)          // 警告
    val PcPurple = Color(0xFF7C3AED)          // 实验性/外置登录
    val PcYellow = Color(0xFFB8860B)          // 黄色

    val PcShadow = Color(0x0F000000)          // 主阴影（6% 黑）
    val PcShadowLarge = Color(0x14000000)     // 大阴影（8% 黑）

    val PcGlassBg = Color(0xF5F5F5F7)         // 玻璃拟态背景（侧边栏/标题栏）
    val PcGlassCardBg = Color(0xB3FAFAFA)     // 玻璃卡片背景（70% 不透明）

    // 启动按钮渐变色
    val PcLaunchGradientStart = Color(0xFF22C55E)
    val PcLaunchGradientEnd = Color(0xFF16A34A)
    val PcLaunchRunningStart = Color(0xFFEF5350)
    val PcLaunchRunningEnd = Color(0xFFC62828)
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
