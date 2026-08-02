package com.hasyame.marvelchampions.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * A newsstand comic palette: saturated primaries, heavy black line, and paper
 * that is cream rather than white.
 *
 * These are ordinary comic-printing colours — the four-colour process plus an
 * ink black — and not any publisher's trade dress. Nothing here reproduces a
 * logo, a character or a house style; the app has to look like a comic without
 * borrowing one.
 */

/** Cover red. The app's primary, used for anything that acts. */
val ComicRed = Color(0xFFD62828)
val ComicRedDark = Color(0xFF8E1616)
val ComicRedLight = Color(0xFFFF6B5E)

/** Cape blue, for secondary emphasis and selected state. */
val ComicBlue = Color(0xFF1D4E9B)
val ComicBlueDark = Color(0xFF0F2E5E)
val ComicBlueLight = Color(0xFF6C9BE0)

/** Sound-effect yellow. Reserved for highlights, never for large fills. */
val ComicYellow = Color(0xFFF6BE00)
val ComicYellowDark = Color(0xFF9A7500)

/** Ink. Panel borders, outlines and body text on paper. */
val ComicInk = Color(0xFF14131A)
val ComicInkSoft = Color(0xFF3A3844)

/** Newsprint. Warmer than white, which is most of the comic feel. */
val ComicPaper = Color(0xFFFBF4E4)
val ComicPaperDim = Color(0xFFEFE4CC)

/** Night pages, for the dark theme. */
val ComicNight = Color(0xFF141118)
val ComicNightRaised = Color(0xFF221D2B)

/** Kept for the two states that must read as status, not as decoration. */
val ComicGreen = Color(0xFF2E7D4F)
