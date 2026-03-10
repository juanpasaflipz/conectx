package app.conectx.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun ConectxTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme

    // Match status bar to the dark background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ConectxTypography,
        content = content
    )
}
