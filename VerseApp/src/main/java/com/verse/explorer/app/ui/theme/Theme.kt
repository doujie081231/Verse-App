package com.verse.explorer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 全局色板（对齐 versepc2 themes.css 的 dark/light 两套 CSS 变量）。
 * `Pc*` 系列是响应式 getter：组合期间读取会订阅内部主题状态，
 * 切换黑白主题（VerseColors.applyTheme）时全 UI 自动重组，与 PC 即时换肤一致。
 */
object VerseColors {

    // ==================== 平台色（跨主题不变）====================
    val FabricGreen = Color(0xFF30B25A)
    val ForgeColor = Color(0xFFEA580C)
    val NeoForgeRed = Color(0xFFEF4444)
    val QuiltPurple = Color(0xFFA855F7)

    // ==================== Minecraft 主题色（历史遗留，跨主题不变）====================
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

    // ==================== 主题状态 ====================
    private var isDarkState = mutableStateOf(true)

    /** 当前是否为黑（深色）主题 */
    var isDark: Boolean
        get() = isDarkState.value
        set(value) {
            isDarkState.value = value
        }

    /** 应用个性化主题颜色（"black" / "white"），对齐 PC data-theme 切换即时生效 */
    fun applyTheme(color: String) {
        isDark = color != "white"
    }

    // ==================== PC dark（对齐 [data-theme="dark"]）====================
    private object Dark {
        val bgPrimary = Color(0xFF0A0A0A)
        val bgSecondary = Color(0xFF141414)
        val bgTertiary = Color(0xFF1C1C1C)
        val bgCard = Color(0xFF181818)
        val bgHover = Color(0xFF242424)
        val bgActive = Color(0xFF2E2E2E)
        val textPrimary = Color(0xFFF0F0F0)
        val textSecondary = Color(0xFFA0A0A0)
        val textMuted = Color(0xFF666666)
        val accent = Color(0xFFFFFFFF)
        val accentHover = Color(0xFFD0D0D0)
        val accentGlow = Color(0x26FFFFFF)
        val accentText = Color(0xFF0A0A0A)
        val border = Color(0x14FFFFFF)
        val borderSubtle = Color(0x0AFFFFFF)
        val green = Color(0xFF4CAF50)
        val red = Color(0xFFEF5350)
        val orange = Color(0xFFFF9800)
        val yellow = Color(0xFFFFC107)
        val purple = Color(0xFFAB47BC)
        val shadow = Color(0x80000000)
        val shadowLarge = Color(0x99000000)
        val glassBg = Color(0xD90A0A0A)
        val glassCardBg = Color(0xB3181818)
        val launchGradientStart = Color(0xFFF5F5F5)
        val launchGradientEnd = Color(0xFFD0D0D0)
    }

    // ==================== PC light（对齐 [data-theme="light"]）====================
    private object Light {
        val bgPrimary = Color(0xFFFFFFFF)
        val bgSecondary = Color(0xFFF5F5F7)
        val bgTertiary = Color(0xFFEEEEF0)
        val bgCard = Color(0xFFFAFAFA)
        val bgHover = Color(0xFFE8E8ED)
        val bgActive = Color(0xFFDDDDE5)
        val textPrimary = Color(0xFF1A1A1A)
        val textSecondary = Color(0xFF4A4A4A)
        val textMuted = Color(0xFF8A8A8A)
        val accent = Color(0xFF1A1A1A)
        val accentHover = Color(0xFF333333)
        val accentGlow = Color(0x1A000000)
        val accentText = Color(0xFFFFFFFF)
        val border = Color(0x1A000000)
        val borderSubtle = Color(0x0A000000)
        val green = Color(0xFF16A34A)
        val red = Color(0xFFDC2626)
        val orange = Color(0xFFEA580C)
        val yellow = Color(0xFFB8860B)
        val purple = Color(0xFF7C3AED)
        val shadow = Color(0x0F000000)
        val shadowLarge = Color(0x14000000)
        val glassBg = Color(0xD9F5F5F7)
        val glassCardBg = Color(0xB3FAFAFA)
        val launchGradientStart = Color(0xFF1A1A1A)
        val launchGradientEnd = Color(0xFF333333)
    }

    private val isDarkTheme: Boolean get() = isDark

    // ==================== PC 端主题色（响应式，对齐 VersePC 黑白主题）====================
    val PcBgPrimary: Color get() = if (isDarkTheme) Dark.bgPrimary else Light.bgPrimary
    val PcBgSecondary: Color get() = if (isDarkTheme) Dark.bgSecondary else Light.bgSecondary
    val PcBgTertiary: Color get() = if (isDarkTheme) Dark.bgTertiary else Light.bgTertiary
    val PcBgCard: Color get() = if (isDarkTheme) Dark.bgCard else Light.bgCard
    val PcBgHover: Color get() = if (isDarkTheme) Dark.bgHover else Light.bgHover
    val PcBgActive: Color get() = if (isDarkTheme) Dark.bgActive else Light.bgActive

    val PcTextPrimary: Color get() = if (isDarkTheme) Dark.textPrimary else Light.textPrimary
    val PcTextSecondary: Color get() = if (isDarkTheme) Dark.textSecondary else Light.textSecondary
    val PcTextMuted: Color get() = if (isDarkTheme) Dark.textMuted else Light.textMuted

    val PcAccent: Color get() = if (isDarkTheme) Dark.accent else Light.accent
    val PcAccentHover: Color get() = if (isDarkTheme) Dark.accentHover else Light.accentHover
    val PcAccentText: Color get() = if (isDarkTheme) Dark.accentText else Light.accentText
    val PcAccentGlow: Color get() = if (isDarkTheme) Dark.accentGlow else Light.accentGlow

    val PcBorder: Color get() = if (isDarkTheme) Dark.border else Light.border
    val PcBorderSubtle: Color get() = if (isDarkTheme) Dark.borderSubtle else Light.borderSubtle

    val PcGreen: Color get() = if (isDarkTheme) Dark.green else Light.green
    val PcRed: Color get() = if (isDarkTheme) Dark.red else Light.red
    val PcOrange: Color get() = if (isDarkTheme) Dark.orange else Light.orange
    val PcPurple: Color get() = if (isDarkTheme) Dark.purple else Light.purple
    val PcYellow: Color get() = if (isDarkTheme) Dark.yellow else Light.yellow

    val PcShadow: Color get() = if (isDarkTheme) Dark.shadow else Light.shadow
    val PcShadowLarge: Color get() = if (isDarkTheme) Dark.shadowLarge else Light.shadowLarge

    val PcGlassBg: Color get() = if (isDarkTheme) Dark.glassBg else Light.glassBg
    val PcGlassCardBg: Color get() = if (isDarkTheme) Dark.glassCardBg else Light.glassCardBg

    // 启动按钮渐变（对齐 .btn-primary background: var(--accent)：dark 白渐变黑字 / light 深渐变白字）
    val PcLaunchGradientStart: Color get() = if (isDarkTheme) Dark.launchGradientStart else Light.launchGradientStart
    val PcLaunchGradientEnd: Color get() = if (isDarkTheme) Dark.launchGradientEnd else Light.launchGradientEnd
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
