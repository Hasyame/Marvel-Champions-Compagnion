package com.hasyame.marvelchampions.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.sp

/**
 * Comic lettering out of the fonts already on the device.
 *
 * A real comic face would have to be licensed and bundled, which is weight in
 * the APK and a licence to honour. Most of the effect comes from weight and
 * spacing anyway: titles are heavy, slightly condensed and widely tracked, the
 * way hand-lettered captions read, while body text stays plain so that card
 * text and rules remain easy to read.
 */

/** Display and titles: the loud voice. */
private val Lettered = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    letterSpacing = 0.8.sp,
    // Very slightly narrowed, as comic titling is: enough to feel drawn rather
    // than typed, not enough to distort.
    textGeometricTransform = TextGeometricTransform(scaleX = 0.96f),
)

val ComicTypography = Typography().run {
    copy(
        displayLarge = displayLarge.merge(Lettered),
        displayMedium = displayMedium.merge(Lettered),
        displaySmall = displaySmall.merge(Lettered),

        headlineLarge = headlineLarge.merge(Lettered),
        headlineMedium = headlineMedium.merge(Lettered),
        headlineSmall = headlineSmall.merge(Lettered),

        titleLarge = titleLarge.merge(Lettered),
        titleMedium = titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
        ),
        titleSmall = titleSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
        ),

        // Labels sit on buttons and chips, where comics shout.
        labelLarge = labelLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.9.sp,
        ),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
    )
}
