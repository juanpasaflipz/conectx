package app.conectx.presentation.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = ConectxGreen,
    onPrimary = ConectxOnGreen,
    primaryContainer = ConectxGreenContainer,
    onPrimaryContainer = ConectxAccent,
    secondary = ConectxAccent,
    onSecondary = ConectxOnAccent,
    tertiary = ConectxTertiary,
    onTertiary = ConectxOnTertiary,
    tertiaryContainer = ConectxTertiaryContainer,
    onTertiaryContainer = ConectxOnTertiaryContainer,
    error = ConectxError,
    onError = ConectxOnError,
    errorContainer = ConectxErrorContainer,
    onErrorContainer = ConectxOnErrorContainer,
    background = ConectxDark,
    onBackground = ConectxOnSurface,
    surface = ConectxSurface,
    onSurface = ConectxOnSurface,
    surfaceVariant = ConectxSurfaceVariant,
    onSurfaceVariant = ConectxOnSurfaceVariant,
    outline = ConectxOutline
)

private val LightColorScheme = lightColorScheme(
    primary = ConectxGreenDark,
    onPrimary = ConectxLight,
    primaryContainer = ConectxLightGreenContainer,
    onPrimaryContainer = ConectxLightOnGreenContainer,
    secondary = ConectxGreen,
    onSecondary = ConectxOnGreen,
    tertiary = ConectxTertiary,
    onTertiary = ConectxLightOnSurface,
    tertiaryContainer = ConectxLightTertiaryContainer,
    onTertiaryContainer = ConectxLightOnTertiaryContainer,
    error = ConectxError,
    onError = ConectxLight,
    errorContainer = ConectxLightErrorContainer,
    onErrorContainer = ConectxLightOnErrorContainer,
    background = ConectxLight,
    onBackground = ConectxLightOnSurface,
    surface = ConectxLightSurface,
    onSurface = ConectxLightOnSurface,
    surfaceVariant = ConectxLightSurfaceVariant,
    onSurfaceVariant = ConectxLightOnSurfaceVariant,
    outline = ConectxLightOutline
)

@Composable
fun ConectxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ConectxTypography,
        content = content
    )
}
