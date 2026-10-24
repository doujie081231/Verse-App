package com.verse.explorer.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/**
 * 七套命名色板的行数据：每行承载同一色彩角色在七套色板下的取值，
 * 顺序固定为 烬火 / 绯玫 / 雾波 / 冰川 / 翠野 / 都市灰 / 翠晨。
 */
data class PaletteRow(
    val embermire: Color,
    val velvetRose: Color,
    val mistwave: Color,
    val glacier: Color,
    val verdantField: Color,
    val urbanAsh: Color,
    val verdantDawn: Color
)

/**
 * 颜色主题选择类型：深色/浅色为黑白主题两项（沿用原有行为），
 * 七套命名色板 + 动态取色（Android 12+ 系统取色）+ 自定义取色构成多选网格。
 */
enum class ColorThemeType {
    DARK,
    LIGHT,
    DYNAMIC,
    EMBERMIRE,
    VELVET_ROSE,
    MISTWAVE,
    GLACIER,
    VERDANT_FIELD,
    URBAN_ASH,
    VERDANT_DAWN,
    CUSTOM
}

/** 主题明暗三态：深色 / 浅色 / 跟随系统 */
enum class DarkMode {
    /** 强制深色外观 */
    ENABLE,

    /** 强制浅色外观 */
    DISABLE,

    /** 跟随系统深浅变化 */
    FOLLOW_SYSTEM
}

/** 主界面操作菜单的停靠侧 */
enum class ActionMenuSide {
    /** 停靠于阅读方向起始侧（左） */
    START,

    /** 停靠于阅读方向末尾侧（右） */
    END
}

// ==================== 七套命名色板完整色值（light/dark 成对）====================

val primaryLight = PaletteRow(Color(0xFFA63A17), Color(0xFF723D57), Color(0xFF426469), Color(0xFF006684), Color(0xFF676014), Color(0xFF5E5E5F), Color(0xFF004814))
val onPrimaryLight = PaletteRow(Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
val primaryContainerLight = PaletteRow(Color(0xFFFE7A52), Color(0xFF9B607C), Color(0xFFB1D5DB), Color(0xFF4CAFD6), Color(0xFFEFE58B), Color(0xFFAEAEAE), Color(0xFF276E31))
val onPrimaryContainerLight = PaletteRow(Color(0xFF2F0700), Color(0xFFFFFFFF), Color(0xFF1E4146), Color(0xFF001B26), Color(0xFF1F1C00), Color(0xFF222324), Color(0xFFFFFFFF))
val secondaryLight = PaletteRow(Color(0xFF894F3D), Color(0xFF725762), Color(0xFF546163), Color(0xFF426372), Color(0xFF635F41), Color(0xFF5F5E5E), Color(0xFF4A6548))
val onSecondaryLight = PaletteRow(Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
val secondaryContainerLight = PaletteRow(Color(0xFFFFBBA7), Color(0xFFFFDAE8), Color(0xFFD8E6E9), Color(0xFFCAECFE), Color(0xFFEAE3BE), Color(0xFFE8E6E5), Color(0xFFCFEFCA))
val onSecondaryContainerLight = PaletteRow(Color(0xFF5C2B1C), Color(0xFF5B414D), Color(0xFF3E4B4D), Color(0xFF2E4F5D), Color(0xFF1E1C05), Color(0xFF4A4A4A), Color(0xFF365135))
val tertiaryLight = PaletteRow(Color(0xFF6E5E00), Color(0xFF773F27), Color(0xFF655975), Color(0xFF794C91), Color(0xFF406653), Color(0xFF615D5F), Color(0xFF003C77))
val onTertiaryLight = PaletteRow(Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
val tertiaryContainerLight = PaletteRow(Color(0xFFBAA015), Color(0xFFA26247), Color(0xFFD8C9E9), Color(0xFFC390DB), Color(0xFFC1ECD4), Color(0xFFB2ADAF), Color(0xFF2960A6))
val onTertiaryContainerLight = PaletteRow(Color(0xFF1A1500), Color(0xFFFFFFFF), Color(0xFF423751), Color(0xFF2B0041), Color(0xFF002114), Color(0xFF252324), Color(0xFFFFFFFF))
val errorLight = PaletteRow(Color(0xFFBA1A1A), Color(0xFFBA1A1A), Color(0xFFBA1A1A), Color(0xFFBA1A1A), Color(0xFFBA1A1A), Color(0xFFBA1A1A), Color(0xFFBA1A1A))
val onErrorLight = PaletteRow(Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
val errorContainerLight = PaletteRow(Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6))
val onErrorContainerLight = PaletteRow(Color(0xFF410002), Color(0xFF410002), Color(0xFF410002), Color(0xFF410002), Color(0xFF410002), Color(0xFF410002), Color(0xFF410002))
val backgroundLight = PaletteRow(Color(0xFFFFF8F6), Color(0xFFFFF8F8), Color(0xFFFAF9F9), Color(0xFFF6FAFD), Color(0xFFFEF9EB), Color(0xFFFCF8F8), Color(0xFFF7FBF2))
val onBackgroundLight = PaletteRow(Color(0xFF241916), Color(0xFF1F1A1C), Color(0xFF1A1C1C), Color(0xFF181C1F), Color(0xFF1D1C14), Color(0xFF1C1B1B), Color(0xFF181D18))
val surfaceLight = PaletteRow(Color(0xFFFFF8F6), Color(0xFFFFF8F8), Color(0xFFFAF9F9), Color(0xFFF6FAFD), Color(0xFFFEF9EB), Color(0xFFFCF8F8), Color(0xFFF7FBF2))
val onSurfaceLight = PaletteRow(Color(0xFF241916), Color(0xFF1F1A1C), Color(0xFF1A1C1C), Color(0xFF181C1F), Color(0xFF1D1C14), Color(0xFF1C1B1B), Color(0xFF181D18))
val surfaceVariantLight = PaletteRow(Color(0xFFFCDBD3), Color(0xFFF1DEE4), Color(0xFFDDE4E5), Color(0xFFDAE4EA), Color(0xFFE7E3D0), Color(0xFFE0E3E3), Color(0xFFDCE5D7))
val onSurfaceVariantLight = PaletteRow(Color(0xFF58423B), Color(0xFF504348), Color(0xFF414849), Color(0xFF3E484E), Color(0xFF49473A), Color(0xFF444748), Color(0xFF40493E))
val outlineLight = PaletteRow(Color(0xFF8B716A), Color(0xFF827378), Color(0xFF71787A), Color(0xFF6E797E), Color(0xFF7A7768), Color(0xFF747878), Color(0xFF707A6D))
val outlineVariantLight = PaletteRow(Color(0xFFDFC0B7), Color(0xFFD4C2C8), Color(0xFFC1C8C9), Color(0xFFBEC8CE), Color(0xFFCBC7B5), Color(0xFFC4C7C7), Color(0xFFC0C9BB))
val scrimLight = PaletteRow(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000))
val inverseSurfaceLight = PaletteRow(Color(0xFF3B2D2A), Color(0xFF352F31), Color(0xFF2F3131), Color(0xFF2D3134), Color(0xFF323128), Color(0xFF313030), Color(0xFF2D322C))
val inverseOnSurfaceLight = PaletteRow(Color(0xFFFFEDE8), Color(0xFFF9EEF0), Color(0xFFF1F1F0), Color(0xFFEEF1F4), Color(0xFFF5F0E3), Color(0xFFF4F0EF), Color(0xFFEEF2E9))
val inversePrimaryLight = PaletteRow(Color(0xFFFFB59F), Color(0xFFF9B2D2), Color(0xFFA9CDD3), Color(0xFF73D2FB), Color(0xFFD2C972), Color(0xFFC7C6C6), Color(0xFF8ED88E))
val surfaceDimLight = PaletteRow(Color(0xFFEBD5D0), Color(0xFFE2D7DA), Color(0xFFDADADA), Color(0xFFD7DADE), Color(0xFFDEDACC), Color(0xFFDDD9D9), Color(0xFFD8DBD3))
val surfaceBrightLight = PaletteRow(Color(0xFFFFF8F6), Color(0xFFFFF8F8), Color(0xFFFAF9F9), Color(0xFFF6FAFD), Color(0xFFFEF9EB), Color(0xFFFCF8F8), Color(0xFFF7FBF2))
val surfaceContainerLowestLight = PaletteRow(Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
val surfaceContainerLowLight = PaletteRow(Color(0xFFFFF1ED), Color(0xFFFCF1F3), Color(0xFFF4F3F3), Color(0xFFF0F4F7), Color(0xFFF8F3E6), Color(0xFFF7F3F2), Color(0xFFF1F5EC))
val surfaceContainerLight = PaletteRow(Color(0xFFFFE9E4), Color(0xFFF7EBED), Color(0xFFEEEEEE), Color(0xFFEBEEF1), Color(0xFFF2EEE0), Color(0xFFF1EDEC), Color(0xFFECEFE6))
val surfaceContainerHighLight = PaletteRow(Color(0xFFFAE3DD), Color(0xFFF1E5E8), Color(0xFFE8E8E8), Color(0xFFE5E9EC), Color(0xFFEDE8DA), Color(0xFFEBE7E7), Color(0xFFE6E9E1))
val surfaceContainerHighestLight = PaletteRow(Color(0xFFF4DED8), Color(0xFFEBE0E2), Color(0xFFE3E2E2), Color(0xFFDFE3E6), Color(0xFFE7E2D5), Color(0xFFE5E2E1), Color(0xFFE0E4DB))

val primaryDark = PaletteRow(Color(0xFFFFB59F), Color(0xFFF9B2D2), Color(0xFFCEF3F9), Color(0xFF73D2FB), Color(0xFFD2C972), Color(0xFFC7C6C6), Color(0xFF8ED88E))
val onPrimaryDark = PaletteRow(Color(0xFF5F1600), Color(0xFF4F2039), Color(0xFF11353A), Color(0xFF003546), Color(0xFF353100), Color(0xFF2F3131), Color(0xFF00390F))
val primaryContainerDark = PaletteRow(Color(0xFFC4502B), Color(0xFF915873), Color(0xFFA5C9CF), Color(0xFF007EA2), Color(0xFF4E4800), Color(0xFF9B9B9B), Color(0xFF005219))
val onPrimaryContainerDark = PaletteRow(Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFF14383D), Color(0xFFFFFFFF), Color(0xFFEFE58B), Color(0xFF0B0C0D), Color(0xFFA6F2A5))
val secondaryDark = PaletteRow(Color(0xFFFFB59F), Color(0xFFE0BDCB), Color(0xFFBBC9CB), Color(0xFFAACBDD), Color(0xFFCDC7A3), Color(0xFFC8C6C6), Color(0xFFB0CFAB))
val onSecondaryDark = PaletteRow(Color(0xFF512214), Color(0xFF412A34), Color(0xFF263335), Color(0xFF113442), Color(0xFF343117), Color(0xFF303030), Color(0xFF1C361D))
val secondaryContainerDark = PaletteRow(Color(0xFF643122), Color(0xFF4F3641), Color(0xFF333F41), Color(0xFF204250), Color(0xFF4B482C), Color(0xFF3D3D3D), Color(0xFF294329))
val onSecondaryContainerDark = PaletteRow(Color(0xFFFFC8B9), Color(0xFFEBC7D5), Color(0xFFC5D3D6), Color(0xFFB4D6E8), Color(0xFFEAE3BE), Color(0xFFD3D0D0), Color(0xFFB9D9B5))
val tertiaryDark = PaletteRow(Color(0xFFF0D44C), Color(0xFFFFB598), Color(0xFFF5E9FF), Color(0xFFE7B3FF), Color(0xFFA6D0B9), Color(0xFFCBC5C7), Color(0xFFA7C8FF))
val onTertiaryDark = PaletteRow(Color(0xFF393000), Color(0xFF52220C), Color(0xFF362C45), Color(0xFF471C5F), Color(0xFF0F3727), Color(0xFF323031), Color(0xFF003061))
val tertiaryContainerDark = PaletteRow(Color(0xFFC4AA22), Color(0xFF97593F), Color(0xFFCCBDDD), Color(0xFFAF7EC7), Color(0xFF284E3C), Color(0xFFA09B9D), Color(0xFF004688))
val onTertiaryContainerDark = PaletteRow(Color(0xFF272000), Color(0xFFFFFFFF), Color(0xFF392F48), Color(0xFF000000), Color(0xFFC1ECD4), Color(0xFF0F0D0F), Color(0xFFD1E0FF))
val errorDark = PaletteRow(Color(0xFFFFB4AB), Color(0xFFFFB4AB), Color(0xFFFFB4AB), Color(0xFFFFB4AB), Color(0xFFFFB4AB), Color(0xFFFFB4AB), Color(0xFFFFB4AB))
val onErrorDark = PaletteRow(Color(0xFF690005), Color(0xFF690005), Color(0xFF690005), Color(0xFF690005), Color(0xFF690005), Color(0xFF690005), Color(0xFF690005))
val errorContainerDark = PaletteRow(Color(0xFF93000A), Color(0xFF93000A), Color(0xFF93000A), Color(0xFF93000A), Color(0xFF93000A), Color(0xFF93000A), Color(0xFF93000A))
val onErrorContainerDark = PaletteRow(Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6), Color(0xFFFFDAD6))
val backgroundDark = PaletteRow(Color(0xFF1C110E), Color(0xFF171214), Color(0xFF121414), Color(0xFF101416), Color(0xFF15140C), Color(0xFF141313), Color(0xFF101410))
val onBackgroundDark = PaletteRow(Color(0xFFF4DED8), Color(0xFFEBE0E2), Color(0xFFE3E2E2), Color(0xFFDFE3E6), Color(0xFFE7E2D5), Color(0xFFE5E2E1), Color(0xFFE0E4DB))
val surfaceDark = PaletteRow(Color(0xFF1C110E), Color(0xFF171214), Color(0xFF121414), Color(0xFF101416), Color(0xFF15140C), Color(0xFF141313), Color(0xFF101410))
val onSurfaceDark = PaletteRow(Color(0xFFF4DED8), Color(0xFFEBE0E2), Color(0xFFE3E2E2), Color(0xFFDFE3E6), Color(0xFFE7E2D5), Color(0xFFE5E2E1), Color(0xFFE0E4DB))
val surfaceVariantDark = PaletteRow(Color(0xFF58423B), Color(0xFF504348), Color(0xFF414849), Color(0xFF3E484E), Color(0xFF49473A), Color(0xFF444748), Color(0xFF40493E))
val onSurfaceVariantDark = PaletteRow(Color(0xFFDFC0B7), Color(0xFFD4C2C8), Color(0xFFC1C8C9), Color(0xFFBEC8CE), Color(0xFFCBC7B5), Color(0xFFC4C7C7), Color(0xFFC0C9BB))
val outlineDark = PaletteRow(Color(0xFFA68B83), Color(0xFF9D8C92), Color(0xFF8B9293), Color(0xFF889298), Color(0xFF949181), Color(0xFF8E9192), Color(0xFF8A9386))
val outlineVariantDark = PaletteRow(Color(0xFF58423B), Color(0xFF504348), Color(0xFF414849), Color(0xFF3E484E), Color(0xFF49473A), Color(0xFF444748), Color(0xFF40493E))
val scrimDark = PaletteRow(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000), Color(0xFF000000))
val inverseSurfaceDark = PaletteRow(Color(0xFFF4DED8), Color(0xFFEBE0E2), Color(0xFFE3E2E2), Color(0xFFDFE3E6), Color(0xFFE7E2D5), Color(0xFFE5E2E1), Color(0xFFE0E4DB))
val inverseOnSurfaceDark = PaletteRow(Color(0xFF3B2D2A), Color(0xFF352F31), Color(0xFF2F3131), Color(0xFF2D3134), Color(0xFF323128), Color(0xFF313030), Color(0xFF2D322C))
val inversePrimaryDark = PaletteRow(Color(0xFFA63A17), Color(0xFF854D68), Color(0xFF426469), Color(0xFF006684), Color(0xFF676014), Color(0xFF5E5E5F), Color(0xFF246C2F))
val surfaceDimDark = PaletteRow(Color(0xFF1C110E), Color(0xFF171214), Color(0xFF121414), Color(0xFF101416), Color(0xFF15140C), Color(0xFF141313), Color(0xFF101410))
val surfaceBrightDark = PaletteRow(Color(0xFF443632), Color(0xFF3E373A), Color(0xFF38393A), Color(0xFF353A3C), Color(0xFF3B3930), Color(0xFF3A3939), Color(0xFF363A34))
val surfaceContainerLowestDark = PaletteRow(Color(0xFF160C09), Color(0xFF120D0F), Color(0xFF0D0E0F), Color(0xFF0A0F11), Color(0xFF0F0E07), Color(0xFF0E0E0E), Color(0xFF0B0F0B))
val surfaceContainerLowDark = PaletteRow(Color(0xFF241916), Color(0xFF1F1A1C), Color(0xFF1A1C1C), Color(0xFF181C1F), Color(0xFF1D1C14), Color(0xFF1C1B1B), Color(0xFF181D18))
val surfaceContainerDark = PaletteRow(Color(0xFF291D19), Color(0xFF241E20), Color(0xFF1E2020), Color(0xFF1C2023), Color(0xFF212017), Color(0xFF201F1F), Color(0xFF1C211B))
val surfaceContainerHighDark = PaletteRow(Color(0xFF342723), Color(0xFF2E282A), Color(0xFF292A2A), Color(0xFF262B2D), Color(0xFF2C2A21), Color(0xFF2A2A2A), Color(0xFF272B26))
val surfaceContainerHighestDark = PaletteRow(Color(0xFF3F322E), Color(0xFF393335), Color(0xFF333535), Color(0xFF313538), Color(0xFF37352C), Color(0xFF353434), Color(0xFF323630))

/** PaletteRow 的字段选择器：用于把色板行映射到 ColorScheme 角色 */
private typealias RowSelector = (PaletteRow) -> Color

/** 七套命名色板在 PaletteRow 中的字段顺序（按角色取值用） */
private val rowFields: List<RowSelector> = listOf(
    { it.embermire }, { it.velvetRose }, { it.mistwave }, { it.glacier },
    { it.verdantField }, { it.urbanAsh }, { it.verdantDawn }
)

/** 命名色板类型到 PaletteRow 列索引的映射 */
private val namedTypes = listOf(
    ColorThemeType.EMBERMIRE,
    ColorThemeType.VELVET_ROSE,
    ColorThemeType.MISTWAVE,
    ColorThemeType.GLACIER,
    ColorThemeType.VERDANT_FIELD,
    ColorThemeType.URBAN_ASH,
    ColorThemeType.VERDANT_DAWN
)

/** 命名色板在行内的列索引 */
private fun rowIndex(type: ColorThemeType): Int = namedTypes.indexOf(type).coerceAtLeast(0)

/** 组装命名色板的浅色 / 深色 Material 配色方案 */
fun paletteColorScheme(type: ColorThemeType, dark: Boolean): ColorScheme {
    val i = rowIndex(type)
    return if (dark) {
        darkColorScheme(
            primary = rowFields[i](primaryDark),
            onPrimary = rowFields[i](onPrimaryDark),
            primaryContainer = rowFields[i](primaryContainerDark),
            onPrimaryContainer = rowFields[i](onPrimaryContainerDark),
            secondary = rowFields[i](secondaryDark),
            onSecondary = rowFields[i](onSecondaryDark),
            secondaryContainer = rowFields[i](secondaryContainerDark),
            onSecondaryContainer = rowFields[i](onSecondaryContainerDark),
            tertiary = rowFields[i](tertiaryDark),
            onTertiary = rowFields[i](onTertiaryDark),
            tertiaryContainer = rowFields[i](tertiaryContainerDark),
            onTertiaryContainer = rowFields[i](onTertiaryContainerDark),
            error = rowFields[i](errorDark),
            onError = rowFields[i](onErrorDark),
            errorContainer = rowFields[i](errorContainerDark),
            onErrorContainer = rowFields[i](onErrorContainerDark),
            background = rowFields[i](backgroundDark),
            onBackground = rowFields[i](onBackgroundDark),
            surface = rowFields[i](surfaceDark),
            onSurface = rowFields[i](onSurfaceDark),
            surfaceVariant = rowFields[i](surfaceVariantDark),
            onSurfaceVariant = rowFields[i](onSurfaceVariantDark),
            outline = rowFields[i](outlineDark),
            outlineVariant = rowFields[i](outlineVariantDark),
            scrim = rowFields[i](scrimDark),
            inverseSurface = rowFields[i](inverseSurfaceDark),
            inverseOnSurface = rowFields[i](inverseOnSurfaceDark),
            inversePrimary = rowFields[i](inversePrimaryDark),
            surfaceDim = rowFields[i](surfaceDimDark),
            surfaceBright = rowFields[i](surfaceBrightDark),
            surfaceContainerLowest = rowFields[i](surfaceContainerLowestDark),
            surfaceContainerLow = rowFields[i](surfaceContainerLowDark),
            surfaceContainer = rowFields[i](surfaceContainerDark),
            surfaceContainerHigh = rowFields[i](surfaceContainerHighDark),
            surfaceContainerHighest = rowFields[i](surfaceContainerHighestDark)
        )
    } else {
        lightColorScheme(
            primary = rowFields[i](primaryLight),
            onPrimary = rowFields[i](onPrimaryLight),
            primaryContainer = rowFields[i](primaryContainerLight),
            onPrimaryContainer = rowFields[i](onPrimaryContainerLight),
            secondary = rowFields[i](secondaryLight),
            onSecondary = rowFields[i](onSecondaryLight),
            secondaryContainer = rowFields[i](secondaryContainerLight),
            onSecondaryContainer = rowFields[i](onSecondaryContainerLight),
            tertiary = rowFields[i](tertiaryLight),
            onTertiary = rowFields[i](onTertiaryLight),
            tertiaryContainer = rowFields[i](tertiaryContainerLight),
            onTertiaryContainer = rowFields[i](onTertiaryContainerLight),
            error = rowFields[i](errorLight),
            onError = rowFields[i](onErrorLight),
            errorContainer = rowFields[i](errorContainerLight),
            onErrorContainer = rowFields[i](onErrorContainerLight),
            background = rowFields[i](backgroundLight),
            onBackground = rowFields[i](onBackgroundLight),
            surface = rowFields[i](surfaceLight),
            onSurface = rowFields[i](onSurfaceLight),
            surfaceVariant = rowFields[i](surfaceVariantLight),
            onSurfaceVariant = rowFields[i](onSurfaceVariantLight),
            outline = rowFields[i](outlineLight),
            outlineVariant = rowFields[i](outlineVariantLight),
            scrim = rowFields[i](scrimLight),
            inverseSurface = rowFields[i](inverseSurfaceLight),
            inverseOnSurface = rowFields[i](inverseOnSurfaceLight),
            inversePrimary = rowFields[i](inversePrimaryLight),
            surfaceDim = rowFields[i](surfaceDimLight),
            surfaceBright = rowFields[i](surfaceBrightLight),
            surfaceContainerLowest = rowFields[i](surfaceContainerLowestLight),
            surfaceContainerLow = rowFields[i](surfaceContainerLowLight),
            surfaceContainer = rowFields[i](surfaceContainerLight),
            surfaceContainerHigh = rowFields[i](surfaceContainerHighLight),
            surfaceContainerHighest = rowFields[i](surfaceContainerHighestLight)
        )
    }
}

/** 命名色板浅色模式的主色（用于主题选择网格的色块预览） */
fun paletteSwatch(type: ColorThemeType): Color = rowFields[rowIndex(type)](primaryLight)

/**
 * 自定义取色：以种子颜色按指定配色风格生成完整配色方案（深浅两套）。
 */
fun customColorScheme(seed: Color, style: PaletteStyle, dark: Boolean): ColorScheme =
    dynamicColorScheme(
        seedColor = seed,
        isDark = dark,
        style = style
    )

/**
 * 动态取色（Android 12+ 系统壁纸取色）；低版本回落到第一套命名色板。
 */
fun dynamicColorSchemeCompat(context: Context, dark: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return paletteColorScheme(ColorThemeType.EMBERMIRE, dark)
}

/** 从持久化名称解析颜色主题类型（未知值回落浅色黑白主题） */
fun colorThemeTypeFrom(name: String): ColorThemeType =
    runCatching { ColorThemeType.valueOf(name) }.getOrDefault(ColorThemeType.LIGHT)

/** 从持久化名称解析配色风格（未知值回落 TonalSpot） */
fun paletteStyleFrom(name: String): PaletteStyle =
    runCatching { PaletteStyle.valueOf(name) }.getOrDefault(PaletteStyle.TonalSpot)

/** PaletteStyle 的中文显示名（未知条目回落枚举名） */
fun paletteStyleLabel(style: PaletteStyle): String = when (style) {
    PaletteStyle.TonalSpot -> "色调点（默认）"
    PaletteStyle.Vibrant -> "鲜艳"
    PaletteStyle.Expressive -> "表现"
    PaletteStyle.Rainbow -> "彩虹"
    PaletteStyle.FruitSalad -> "果盘"
    PaletteStyle.Content -> "内容"
    PaletteStyle.Monochrome -> "单色"
    else -> style.name
}
