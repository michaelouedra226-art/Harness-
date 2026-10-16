package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NuruDarkColorScheme = darkColorScheme(
    primary = NuruViolet,
    onPrimary = PureWhite,
    primaryContainer = SlateElevated,
    onPrimaryContainer = PureWhite,
    secondary = NebulaBlue,
    onSecondary = PureWhite,
    secondaryContainer = Graphite,
    onSecondaryContainer = PureWhite,
    tertiary = SolarGold,
    onTertiary = DeepSpace,
    background = DeepSpace,
    onBackground = PureWhite,
    surface = Obsidian,
    onSurface = PureWhite,
    surfaceVariant = Graphite,
    onSurfaceVariant = Silver,
    outline = GlassBorder,
    outlineVariant = SlateElevated,
    error = CoralError,
    onError = PureWhite
)

@Composable
fun NuruStudioTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DeepSpace.toArgb()
                window.navigationBarColor = DeepSpace.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = NuruDarkColorScheme,
        typography = Typography,
        content = content
    )
}
