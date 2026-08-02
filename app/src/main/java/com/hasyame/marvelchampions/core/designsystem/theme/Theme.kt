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
    primary = IronRed,
    onPrimary = PaperWarm,
    primaryContainer = IronRedTint,
    onPrimaryContainer = IronRedInk,

    secondary = BrassGold,
    onSecondary = BrassGoldInk,
    secondaryContainer = BrassGoldTint,
    onSecondaryContainer = BrassGoldInk,

    // The bright gold is the highlight — starbursts and accents — so it is the
    // tertiary and never a page fill.
    tertiary = ArcGold,
    onTertiary = BrassGoldInk,
    tertiaryContainer = ArcGoldTint,
    onTertiaryContainer = BrassGoldInk,

    background = PaperWarm,
    onBackground = PanelInk,
    surface = PaperWarm,
    onSurface = PanelInk,
    surfaceVariant = PaperShade,
    onSurfaceVariant = PanelInkSoft,

    // A heavy outline is most of what makes a panel read as drawn, not as a box.
    outline = PanelInk,
    outlineVariant = PanelInkSoft,

    // The deeper red, so an error still separates from the primary red.
    error = IronRedDeep,
    onError = PaperWarm,
)

private val DarkScheme = darkColorScheme(
    primary = IronRedBright,
    onPrimary = IronRedInk,
    primaryContainer = IronRedDeep,
    onPrimaryContainer = IronRedTint,

    secondary = BrassGold,
    onSecondary = BrassGoldInk,
    secondaryContainer = BrassGoldDeep,
    onSecondaryContainer = BrassGoldTint,

    tertiary = ArcGold,
    onTertiary = BrassGoldInk,
    tertiaryContainer = ArcGoldDeep,
    onTertiaryContainer = ArcGoldTint,

    background = NightLacquer,
    onBackground = PaperWarm,
    surface = NightLacquer,
    onSurface = PaperWarm,
    surfaceVariant = NightRaised,
    onSurfaceVariant = PaperShade,

    outline = NightOutline,
    outlineVariant = PanelInkSoft,

    error = IronRedBright,
    onError = IronRedInk,
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
