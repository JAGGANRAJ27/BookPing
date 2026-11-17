
package com.njwebdesigning.bookping.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BookPingPrimaryDark,
    onPrimary = BookPingOnPrimaryDark,
    secondary = BookPingSecondaryDark,
    onSecondary = BookPingOnSecondaryDark,
    tertiary = BookPingTertiaryDark,
    background = BookPingBackgroundDark,
    onBackground = BookPingOnBackgroundDark,
    surface = BookPingSurfaceDark,
    onSurface = BookPingOnSurfaceDark,
    surfaceVariant = BookPingSurfaceVariantDark,
    onSurfaceVariant = BookPingOnSurfaceVariantDark,
    outline = BookPingOutlineDark,
    error = BookPingErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = BookPingPrimaryLight,
    onPrimary = BookPingOnPrimaryLight,
    secondary = BookPingSecondaryLight,
    onSecondary = BookPingOnSecondaryLight,
    tertiary = BookPingTertiaryLight,
    background = BookPingBackgroundLight,
    onBackground = BookPingOnBackgroundLight,
    surface = BookPingSurfaceLight,
    onSurface = BookPingOnSurfaceLight,
    surfaceVariant = BookPingSurfaceVariantLight,
    onSurfaceVariant = BookPingOnSurfaceVariantLight,
    outline = BookPingOutlineLight,
    error = BookPingErrorLight
)

@Composable
fun BookPingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
