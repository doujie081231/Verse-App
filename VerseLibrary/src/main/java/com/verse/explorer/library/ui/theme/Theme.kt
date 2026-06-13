package com.verse.explorer.library.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = PrimaryVariant,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onBackground = OnBackground,
    onSurface = OnSurface,
    error = Error,
    onError = OnError,
    primaryContainer = PrimaryVariant,
    secondaryContainer = SecondaryVariant,
    onPrimaryContainer = OnPrimary,
    onSecondaryContainer = OnSecondary,
    surfaceVariant = Surface,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = PrimaryVariant,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    error = Error,
    onError = OnError,
    primaryContainer = PrimaryVariant,
    secondaryContainer = SecondaryVariant,
)

@Composable
fun VerseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
