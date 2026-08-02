package com.hasyame.marvelchampions.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Red and gold: hot-rod lacquer over polished brass.
 *
 * The four chosen colours are the whole identity; everything else here is a
 * tint or shade derived from them so that Material's containers and disabled
 * states stay in the same family rather than drifting to stock purple.
 */

/** The primary red. Anything that acts is this colour. */
val IronRed = Color(0xFFE30022)
val IronRedDeep = Color(0xFFCC0000)
val IronRedTint = Color(0xFFFFD9D5)
val IronRedBright = Color(0xFFFF5A49)
val IronRedInk = Color(0xFF480007)

/** Brushed gold, for secondary emphasis. Warmer and duller than the highlight. */
val BrassGold = Color(0xFFD3AF37)
val BrassGoldTint = Color(0xFFFFE9AC)
val BrassGoldDeep = Color(0xFF8A7020)
val BrassGoldInk = Color(0xFF3A2D00)

/** The bright highlight. Reserved for accents, never a large fill. */
val ArcGold = Color(0xFFFCC200)
val ArcGoldTint = Color(0xFFFFE08A)
val ArcGoldDeep = Color(0xFF7A5F00)

/** Ink, for panel borders and outlines — what makes a card read as drawn. */
val PanelInk = Color(0xFF1A1113)
val PanelInkSoft = Color(0xFF534342)

/** Warm off-white rather than plain white, so the red is not glaring. */
val PaperWarm = Color(0xFFFFF8F6)
val PaperShade = Color(0xFFF3DDDB)

/** Night pages. Tinted towards red so the dark theme is not neutral grey. */
val NightLacquer = Color(0xFF161011)
val NightRaised = Color(0xFF2A211F)
val NightOutline = Color(0xFFE8D8D5)
