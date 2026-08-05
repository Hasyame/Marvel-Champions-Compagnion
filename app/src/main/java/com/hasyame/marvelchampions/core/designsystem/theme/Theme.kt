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

    // Neutral, not gold. Gold is what the game prints Justice in, so a gold
    // selection sat in the same list as cards where gold already means
    // something else.
    secondary = InkGraphite,
    onSecondary = BoneCream,
    secondaryContainer = InkGraphiteSoft,
    onSecondaryContainer = InkGraphite,

    tertiary = InkGraphite,
    onTertiary = BoneCream,
    tertiaryContainer = InkGraphiteSoft,
    onTertiaryContainer = InkGraphite,

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

/**
 * The dark scheme, and the one the app opens in.
 *
 * Red on near-black is the palette this game is drawn in, and it is what a
 * table lit for playing wants. The surfaces climb in five steps so a dialog
 * sits above a card sits above the page; with only two tones everything landed
 * at the same depth and the screen read flat.
 */
private val DarkScheme = darkColorScheme(
    primary = IronRedBright,
    onPrimary = IronRedInk,
    primaryContainer = IronRedDeep,
    onPrimaryContainer = IronRedTint,
    inversePrimary = IronRed,

    // See the light scheme: neutral so the aspect palette keeps its meaning.
    secondary = BoneCream,
    onSecondary = InkGraphite,
    secondaryContainer = BoneCreamDeep,
    onSecondaryContainer = BoneCream,

    tertiary = BoneCream,
    onTertiary = InkGraphite,
    tertiaryContainer = BoneCreamDeep,
    onTertiaryContainer = BoneCream,

    background = NightBase,
    onBackground = PaperWarm,
    surface = NightLacquer,
    onSurface = PaperWarm,
    surfaceVariant = NightRaised,
    onSurfaceVariant = PaperShade,

    // The depth ladder. Cards, sheets and dialogs each take a rung.
    surfaceContainerLowest = NightBase,
    surfaceContainerLow = NightLacquer,
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightRaisedHigh,
    surfaceContainerHighest = NightRaisedHighest,

    outline = NightOutline,
    outlineVariant = NightOutlineSoft,

    error = IronRedBright,
    onError = IronRedInk,
    errorContainer = IronRedDeep,
    onErrorContainer = IronRedTint,

    scrim = PanelShadow,
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
