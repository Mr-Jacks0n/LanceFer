package com.lancefer.lancedrop.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = LanceDropBlueLightAAA,
    onPrimary = Color.White,
    primaryContainer = LanceDropBlueLight,
    onPrimaryContainer = LanceDropBlueLightAAA,
    secondary = LanceDropSuccessTextLightAAA,
    onSecondary = Color.White,
    secondaryContainer = LanceDropGreenLight,
    onSecondaryContainer = LanceDropSuccessTextLightAAA,
    error = LanceDropErrorIconLight,
    onError = Color.White,
    background = LanceDropBgLight,
    onBackground = LanceDropTextMainLightAAA,
    surface = LanceDropCardLight,
    onSurface = LanceDropTextMainLightAAA,
    surfaceVariant = LanceDropBlueSubtle,
    onSurfaceVariant = LanceDropTextSecondaryLightAAA,
    outline = LanceDropCardBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = LanceDropBlueDarkAAA,
    onPrimary = LanceDropBlueOnPrimaryDark,
    primaryContainer = ElectricBlueLight,
    onPrimaryContainer = LanceDropBlueDarkAAA,
    secondary = Color(0xFF86EFAC),
    onSecondary = Color(0xFF070C14),
    secondaryContainer = Color(0xFF14532D),
    onSecondaryContainer = Color(0xFF86EFAC),
    error = LanceDropErrorIconDark,
    onError = Color(0xFF070C14),
    background = CyberSlateBg,
    onBackground = LanceDropTextMainDarkAAA,
    surface = CyberSlateCard,
    onSurface = LanceDropTextMainDarkAAA,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = LanceDropTextSecondaryDarkAAA,
    outline = CyberSlateCardBorder
)

@Composable
fun LanceDropTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode && view.context is Activity) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = (if (darkTheme) CyberSlateBg else LanceDropBgLight).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
