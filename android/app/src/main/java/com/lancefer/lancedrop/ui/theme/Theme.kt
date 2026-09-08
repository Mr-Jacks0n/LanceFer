package com.lancefer.lancedrop.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LanceDropBlue,
    onPrimary = LanceDropCardLight,
    primaryContainer = LanceDropBlueLight,
    onPrimaryContainer = LanceDropBlueDark,
    secondary = LanceDropGreen,
    onSecondary = LanceDropCardLight,
    background = LanceDropBgLight,
    onBackground = LanceDropTextMainLight,
    surface = LanceDropCardLight,
    onSurface = LanceDropTextMainLight,
    surfaceVariant = LanceDropBlueSubtle,
    onSurfaceVariant = LanceDropTextMutedLight,
    outline = LanceDropCardBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = LanceDropBlue,
    onPrimary = LanceDropCardLight,
    primaryContainer = LanceDropBlueDark,
    onPrimaryContainer = LanceDropBlueLight,
    secondary = LanceDropGreen,
    onSecondary = LanceDropCardLight,
    background = LanceDropBgDark,
    onBackground = LanceDropTextMainDark,
    surface = LanceDropCardDark,
    onSurface = LanceDropTextMainDark,
    surfaceVariant = LanceDropCardDark,
    onSurfaceVariant = LanceDropTextMutedDark,
    outline = LanceDropCardBorderDark
)

@Composable
fun LanceDropTheme(
    darkTheme: Boolean = false, // Mode clair par défaut conforme à la maquette
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode && view.context is Activity) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = (if (darkTheme) LanceDropBgDark else LanceDropBgLight).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
