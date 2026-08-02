package com.hasyame.marvelchampions.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The loading state, as a sound-effect burst over halftone paper.
 *
 * The words are original comic exclamations written for this app, not lines
 * quoted from Marvel comics or films: a quotation would be someone else's text
 * shipped inside the APK, which is the one thing this project has been careful
 * not to do. Generic onomatopoeia carries the same feeling and belongs to
 * nobody.
 *
 * [progress] draws a determinate bar when the work reports how far along it is,
 * and an indeterminate one when it cannot.
 */
@Composable
fun ComicLoadingScreen(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    message: String? = null,
) {
    val exclamations = stringArrayResource(R.array.loading_exclamations)
    // Picked once per appearance: a word that changed every frame would be
    // unreadable, and one that never changed would be wallpaper.
    val word = remember(exclamations) { exclamations.random() }

    Box(
        modifier
            .fillMaxSize()
            .halftone(MaterialTheme.colorScheme.onBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StarburstBadge(word)

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
            }

            if (progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.loading_percent,
                        (progress.coerceIn(0f, 1f) * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** The spiky panel a sound effect sits in, pulsing the way one is drawn to. */
@Composable
private fun StarburstBadge(word: String) {
    val transition = rememberInfiniteTransition(label = "burst")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val fill = MaterialTheme.colorScheme.tertiary
    val ink = MaterialTheme.colorScheme.outline

    Box(
        Modifier.size(220.dp).scale(pulse),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension / 2f
            val inner = outer * 0.72f
            val points = 14

            val path = Path()
            repeat(points * 2) { index ->
                // Alternating radii around the circle is what makes the spikes.
                val radius = if (index % 2 == 0) outer else inner
                val angle = PI * index / points - PI / 2
                val point = Offset(
                    x = centre.x + (radius * cos(angle)).toFloat(),
                    y = centre.y + (radius * sin(angle)).toFloat(),
                )
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            path.close()

            drawPath(path, color = fill)
            drawPath(path, color = ink, style = Stroke(width = 3.dp.toPx()))
        }

        Text(
            text = word,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
    }
}
