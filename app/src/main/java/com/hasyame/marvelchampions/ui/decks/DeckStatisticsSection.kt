package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.aspectColor
import com.hasyame.marvelchampions.domain.deckbuilder.DeckStatistics

/**
 * What the deck is made of, at a glance.
 *
 * The three questions a deckbuilder actually asks: can I afford to play my
 * cards early, can I pay for them at all, and is the deck the shape I meant it
 * to be. Everything here counts copies rather than distinct cards, because that
 * is what the draw pile contains.
 */
@Composable
fun DeckStatisticsSection(stats: DeckStatistics, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.decks_stats_title),
            style = MaterialTheme.typography.titleSmall,
        )

        if (stats.hasCostData) {
            CostCurve(stats)
        }

        ResourceRow(stats)

        if (stats.byType.isNotEmpty()) {
            Breakdown(
                title = stringResource(R.string.decks_stats_by_type),
                entries = stats.byType,
                colorFor = { null },
            )
        }

        if (stats.byAspect.size > 1) {
            // With a single aspect the bar is just the deck size again, which
            // tells nobody anything.
            Breakdown(
                title = stringResource(R.string.decks_stats_by_aspect),
                entries = stats.byAspect,
                colorFor = { aspectColor(it.lowercase()) },
            )
        }
    }
}

/** The cost curve, as columns scaled to the tallest one. */
@Composable
private fun CostCurve(stats: DeckStatistics) {
    val tallest = stats.tallestCostColumn.coerceAtLeast(1)
    // Read from the composition rather than Locale.getDefault(), so the decimal
    // separator follows the app language and recomposes when it changes.
    val locale = LocalConfiguration.current.locales[0]

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(
                R.string.decks_stats_average_cost,
                String.format(locale, "%.1f", stats.averageCost),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Every cost from zero to the most expensive card, so a gap in the
            // curve is visible as a gap rather than closed up silently.
            val highest = stats.costCurve.keys.maxOrNull() ?: 0
            (0..highest).forEach { cost ->
                val count = stats.costCurve[cost] ?: 0
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (count > 0) count.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // A zero-count column still draws a sliver, so the
                            // axis reads as a row of costs rather than a hole.
                            .height((64.dp * count / tallest).coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                if (count > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                    )
                    Text(
                        text = cost.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Resource icons, which are what pays for everything above. */
@Composable
private fun ResourceRow(stats: DeckStatistics) {
    val resources = stats.resources

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.decks_stats_resources),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                stringResource(R.string.decks_stats_physical) to resources.physical,
                stringResource(R.string.decks_stats_mental) to resources.mental,
                stringResource(R.string.decks_stats_energy) to resources.energy,
                stringResource(R.string.decks_stats_wild) to resources.wild,
            ).forEach { (label, count) ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(count.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** A labelled proportional bar per entry, largest first. */
@Composable
private fun Breakdown(
    title: String,
    entries: List<Pair<String, Int>>,
    // Composable because the aspect palette is read from the theme.
    colorFor: @Composable (String) -> Color?,
) {
    val total = entries.sumOf { it.second }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        entries.forEach { (label, count) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(96.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / total)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                colorFor(label) ?: MaterialTheme.colorScheme.secondary,
                            ),
                    )
                }
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
