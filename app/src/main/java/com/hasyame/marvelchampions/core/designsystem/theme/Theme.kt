package com.hasyame.marvelchampions.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The comic look, applied through the colour scheme rather than by styling
 * screens one at a time.
 *
 * Dynamic colour is deliberately off by default now. It is the right default
 * for an app with no identity of its own, but here it repaints everything from
 * the wallpaper, and an app that comes out lilac on one phone and sage on
 * another has no identity left. It stays available as a parameter.
 */
private val LightScheme = lightColorScheme(
    primary = ComicRed,
    onPrimary = ComicPaper,
    primaryContainer = ComicRedLight,
    onPrimaryContainer = ComicInk,

    secondary = ComicBlue,
    onSecondary = ComicPaper,
    secondaryContainer = ComicBlueLight,
    onSecondaryContainer = ComicInk,

    // Yellow is what a comic reserves for sound effects and starbursts, so it is
    // the tertiary accent and never a page fill.
    tertiary = ComicYellow,
    onTertiary = ComicInk,
    tertiaryContainer = ComicYellow,
    onTertiaryContainer = ComicInk,

    background = ComicPaper,
    onBackground = ComicInk,
    surface = ComicPaper,
    onSurface = ComicInk,
    surfaceVariant = ComicPaperDim,
    onSurfaceVariant = ComicInkSoft,

    // A heavy outline is most of what makes a panel read as drawn, not as a box.
    outline = ComicInk,
    outlineVariant = ComicInkSoft,

    error = ComicRedDark,
    onError = ComicPaper,
)

private val DarkScheme = darkColorScheme(
    primary = ComicRedLight,
    onPrimary = ComicInk,
    primaryContainer = ComicRedDark,
    onPrimaryContainer = ComicPaper,

    secondary = ComicBlueLight,
    onSecondary = ComicInk,
    secondaryContainer = ComicBlueDark,
    onSecondaryContainer = ComicPaper,

    tertiary = ComicYellow,
    onTertiary = ComicInk,
    tertiaryContainer = ComicYellowDark,
    onTertiaryContainer = ComicPaper,

    background = ComicNight,
    onBackground = ComicPaper,
    surface = ComicNight,
    onSurface = ComicPaper,
    surfaceVariant = ComicNightRaised,
    onSurfaceVariant = ComicPaperDim,

    outline = ComicPaperDim,
    outlineVariant = ComicInkSoft,

    error = ComicRedLight,
    onError = ComicInk,
)

@Composable
fun MarvelChampionsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ComicTypography,
        content = content,
    )
}
