package com.verse.explorer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.verse.explorer.app.data.VersePersonalization

/**
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
    // 默认白色主题（对齐 DEFAULT_THEME_COLOR = "white"），启动后 MainActivity 会按设置覆盖
    private var isDarkState = mutableStateOf(false)

    /** 当前是否为黑（深色）主题 */
    var isDark: Boolean
        get() = isDarkState.value
        set(value) {
            isDarkState.value = value
        }

    // 自定义强调色（null=跟随黑白主题默认），设置页取色器/启动加载写入；
    // 仅在黑白主题（深色/浅色两项）选择下生效，彩色主题下强调色由主题配色方案提供
    private var customAccentState = mutableStateOf<Color?>(null)

    /** 自定义强调色（null=使用当前黑白主题的默认强调色） */
    var customAccent: Color?
        get() = customAccentState.value
        set(value) {
            customAccentState.value = value
        }

    // ==================== 彩色主题强调色（非黑白主题选择时由主题层写入）====================
    // primary/secondary/tertiary 分别映射 PcAccent/PcOrange/PcPurple 系列；黑白主题时为 null
    private var themedPrimaryState = mutableStateOf<Color?>(null)
    private var themedSecondaryState = mutableStateOf<Color?>(null)
    private var themedTertiaryState = mutableStateOf<Color?>(null)

    /** 主题层写入彩色主题的强调色三元组（黑白主题时传 null 全部回落原行为） */
    fun applySchemeAccents(primary: Color?, secondary: Color?, tertiary: Color?) {
        themedPrimaryState.value = primary
        themedSecondaryState.value = secondary
        themedTertiaryState.value = tertiary
    }

    // ==================== 自定义主题表面色（仅自定义取色时由主题层写入）====================
    // 自定义取色的配色方案同时驱动背景与 UI 组件容器色：背景取 scheme.background，
    // 组件容器取 surfaceContainer 阶梯（比背景浅一档），深浅由当前深浅模式对应方案决定
    private data class SchemeSurfaces(
        val bgPrimary: Color,
        val bgSecondary: Color,
        val bgTertiary: Color,
        val bgCard: Color,
        val bgHover: Color,
        val bgActive: Color,
        val glassBg: Color,
        val glassCardBg: Color
    )

    private var themedSurfacesState = mutableStateOf<SchemeSurfaces?>(null)

    /** 应用自定义主题表面色（非自定义取色时由 clearSchemeSurfaces 回落黑白主题原色） */
    fun applySchemeSurfaces(
        bgPrimary: Color,
        bgSecondary: Color,
        bgTertiary: Color,
        bgCard: Color,
        bgHover: Color,
        bgActive: Color,
        glassBg: Color,
        glassCardBg: Color
    ) {
        themedSurfacesState.value = SchemeSurfaces(
            bgPrimary, bgSecondary, bgTertiary, bgCard, bgHover, bgActive, glassBg, glassCardBg
        )
    }

    /** 清除自定义主题表面色（回落黑白主题原色） */
    fun clearSchemeSurfaces() {
        themedSurfacesState.value = null
    }

    fun applyTheme(color: String) {
        isDark = color != "white"
    }

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

    // 自定义取色主题激活时，背景与容器色改由配色方案的表面色阶梯提供（比黑白主题浅一档的组件色）
    val PcBgPrimary: Color
        get() = themedSurfacesState.value?.bgPrimary ?: if (isDarkTheme) Dark.bgPrimary else Light.bgPrimary
    val PcBgSecondary: Color
        get() = themedSurfacesState.value?.bgSecondary ?: if (isDarkTheme) Dark.bgSecondary else Light.bgSecondary
    val PcBgTertiary: Color
        get() = themedSurfacesState.value?.bgTertiary ?: if (isDarkTheme) Dark.bgTertiary else Light.bgTertiary
    val PcBgCard: Color
        get() = themedSurfacesState.value?.bgCard ?: if (isDarkTheme) Dark.bgCard else Light.bgCard
    val PcBgHover: Color
        get() = themedSurfacesState.value?.bgHover ?: if (isDarkTheme) Dark.bgHover else Light.bgHover
    val PcBgActive: Color
        get() = themedSurfacesState.value?.bgActive ?: if (isDarkTheme) Dark.bgActive else Light.bgActive

    val PcTextPrimary: Color get() = if (isDarkTheme) Dark.textPrimary else Light.textPrimary
    val PcTextSecondary: Color get() = if (isDarkTheme) Dark.textSecondary else Light.textSecondary
    val PcTextMuted: Color get() = if (isDarkTheme) Dark.textMuted else Light.textMuted

    val PcAccent: Color
        get() {
            // 彩色主题：强调色取主题配色方案的 primary；黑白主题：保留自定义强调色覆盖行为
            val themed = themedPrimaryState.value
            if (themed != null) return themed
            return customAccent ?: if (isDarkTheme) Dark.accent else Light.accent
        }
    val PcAccentHover: Color
        get() {
            val themed = themedPrimaryState.value
            if (themed != null) {
                return if (isDarkTheme) lerp(themed, Color.White, 0.20f) else lerp(themed, Color.Black, 0.20f)
            }
            return customAccent?.let {
                if (isDarkTheme) lerp(it, Color.White, 0.20f) else lerp(it, Color.Black, 0.20f)
            } ?: if (isDarkTheme) Dark.accentHover else Light.accentHover
        }
    val PcAccentText: Color
        get() {
            // 彩色主题：按主题 primary 的亮度决定文字深浅，保证对比可读
            val themed = themedPrimaryState.value
            if (themed != null) {
                return if (themed.luminance() > 0.5f) Color(0xFF0A0A0A) else Color.White
            }
            return customAccent?.let {
                if (it.luminance() > 0.5f) Color(0xFF0A0A0A) else Color.White
            } ?: if (isDarkTheme) Dark.accentText else Light.accentText
        }
    val PcAccentGlow: Color
        get() = PcAccent.copy(alpha = 0.15f)

    val PcBorder: Color get() = if (isDarkTheme) Dark.border else Light.border
    val PcBorderSubtle: Color get() = if (isDarkTheme) Dark.borderSubtle else Light.borderSubtle

    val PcGreen: Color get() = if (isDarkTheme) Dark.green else Light.green
    val PcRed: Color get() = if (isDarkTheme) Dark.red else Light.red
    // 彩色主题下 Orange/Purple 取主题配色方案的 secondary/tertiary；黑白主题保持原色
    val PcOrange: Color get() = themedSecondaryState.value ?: if (isDarkTheme) Dark.orange else Light.orange
    val PcPurple: Color get() = themedTertiaryState.value ?: if (isDarkTheme) Dark.purple else Light.purple
    val PcYellow: Color get() = if (isDarkTheme) Dark.yellow else Light.yellow

    val PcShadow: Color get() = if (isDarkTheme) Dark.shadow else Light.shadow
    val PcShadowLarge: Color get() = if (isDarkTheme) Dark.shadowLarge else Light.shadowLarge

    val PcGlassBg: Color
        get() = themedSurfacesState.value?.glassBg ?: if (isDarkTheme) Dark.glassBg else Light.glassBg
    val PcGlassCardBg: Color
        get() = themedSurfacesState.value?.glassCardBg ?: if (isDarkTheme) Dark.glassCardBg else Light.glassCardBg

    // 启动按钮渐变：dark 白渐变黑字 / light 深渐变白字；
    // 彩色主题/自定义强调色时按亮度决定渐变方向，保证与 PcAccentText 对比可读
    val PcLaunchGradientStart: Color
        get() = themedPrimaryState.value ?: customAccent
            ?: if (isDarkTheme) Dark.launchGradientStart else Light.launchGradientStart
    val PcLaunchGradientEnd: Color
        get() {
            val base = themedPrimaryState.value ?: customAccent
            if (base != null) {
                return if (base.luminance() > 0.5f) lerp(base, Color.White, 0.20f) else lerp(base, Color.Black, 0.20f)
            }
            return if (isDarkTheme) Dark.launchGradientEnd else Light.launchGradientEnd
        }
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

/**
 * 主题明暗三态解析：深色 / 浅色 / 跟随系统（key 包裹保证切换设置时强制重组）。
 * 黑白主题（深色/浅色两项）保留各自固有的明暗倾向：
 * 三态强制深/浅时覆盖该倾向，跟随系统时按倾向取值（与历史行为一致）；
 * 彩色主题按三态在自身 light/dark 色板间切换（跟随系统即 isSystemInDarkTheme()）。
 */
@Composable
fun isVerseInDarkTheme(): Boolean {
    val mode = VersePersonalization.darkMode
    val selection = colorThemeTypeFrom(VersePersonalization.colorTheme)
    return key(mode to selection) {
        when (mode) {
            DarkMode.ENABLE.name -> true
            DarkMode.DISABLE.name -> false
            else -> when (selection) {
                ColorThemeType.DARK -> true
                ColorThemeType.LIGHT -> false
                else -> isSystemInDarkTheme()
            }
        }
    }
}

@Composable
fun VerseExplorerTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = isVerseInDarkTheme()
    val context = LocalContext.current
    val selection = colorThemeTypeFrom(VersePersonalization.colorTheme)
    val customSeed = Color(VersePersonalization.themeCustomColor)
    val customStyle = paletteStyleFrom(VersePersonalization.paletteStyle)

    // 按选择组装配色方案：黑白两项沿用原有配色，命名色板/动态/自定义按深浅取对应方案
    val colorScheme = remember(darkTheme, selection, customSeed, customStyle, context) {
        when (selection) {
            ColorThemeType.DARK, ColorThemeType.LIGHT -> if (darkTheme) DarkColorScheme else LightColorScheme
            ColorThemeType.DYNAMIC -> dynamicColorSchemeCompat(context, darkTheme)
            ColorThemeType.CUSTOM -> customColorScheme(customSeed, customStyle, darkTheme)
            else -> paletteColorScheme(selection, darkTheme)
        }
    }

    // 彩色主题的强调色/次色/三色取自配色方案的 primary/secondary/tertiary；
    // 自定义取色额外把背景与组件容器色切换到配色方案的表面色阶梯
    // （背景=scheme.background，组件取 surfaceContainer 阶梯，深浅随当前深浅模式自动适配）
    val isMonochrome = selection == ColorThemeType.DARK || selection == ColorThemeType.LIGHT
    VerseColors.isDark = darkTheme
    if (isMonochrome) {
        VerseColors.applySchemeAccents(primary = null, secondary = null, tertiary = null)
        VerseColors.clearSchemeSurfaces()
    } else {
        VerseColors.applySchemeAccents(
            primary = colorScheme.primary,
            secondary = colorScheme.secondary,
            tertiary = colorScheme.tertiary
        )
        if (selection == ColorThemeType.CUSTOM) {
            VerseColors.applySchemeSurfaces(
                bgPrimary = colorScheme.background,
                bgSecondary = colorScheme.surfaceContainerLowest,
                bgTertiary = colorScheme.surfaceContainerLow,
                bgCard = colorScheme.surfaceContainerLow,
                bgHover = colorScheme.surfaceContainer,
                bgActive = colorScheme.surfaceContainerHigh,
                glassBg = colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                glassCardBg = colorScheme.surfaceContainerLow.copy(alpha = 0.70f)
            )
        } else {
            VerseColors.clearSchemeSurfaces()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VerseTypography,
        content = content
    )
}
