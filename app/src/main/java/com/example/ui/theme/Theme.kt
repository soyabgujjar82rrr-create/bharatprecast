package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GalleryDarkColorScheme = darkColorScheme(
    primary = IndigoPrimaryVariant,
    onPrimary = DarkBackground,
    primaryContainer = IndigoPrimary,
    onPrimaryContainer = TextPrimary,
    secondary = RoseSecondaryVariant,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceContainerHigh,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanTertiary,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkSurfaceVariant,
    error = RoseSecondary,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek modern dark mode
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = GalleryDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DarkBackground.toArgb()
                window.navigationBarColor = DarkBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
