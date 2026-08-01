package com.hasyame.marvelchampions.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.ui.util.stripHtml

/**
 * The card detail body, shared by the full screen on a phone and the detail
 * pane on a tablet.
 */
@Composable
fun CardDetailContent(
    card: CardEntity,
    linkedCard: CardEntity?,
    locale: CardLocale,
    onLocaleToggle: () -> Unit,
    onLinkedCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.headlineSmall)
                card.subname?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
            }
            // Reading a card in the other language is genuinely useful when a
            // rules discussion is in English but the cards on the table are in
            // French, so the toggle is on the detail screen itself.
            FilterChip(
                selected = false,
                onClick = onLocaleToggle,
                label = { Text(locale.other().code.uppercase()) },
            )
        }

        MarvelCdbUrls.cardImage(card.imageSrc)?.let { imageUrl ->
            Card {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(
            text = listOfNotNull(
                card.typeName,
                card.factionName,
                card.packName,
                card.cardSetName,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
        )

        card.traits?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleSmall)
        }

        StatRow(card)

        card.text?.takeIf { it.isNotBlank() }?.let {
            HorizontalDivider()
            Text(stripHtml(it), style = MaterialTheme.typography.bodyMedium)
        }

        card.backText?.takeIf { it.isNotBlank() }?.let {
            HorizontalDivider()
            card.backName?.let { name ->
                Text(name, style = MaterialTheme.typography.titleSmall)
            }
            Text(stripHtml(it), style = MaterialTheme.typography.bodyMedium)
        }

        card.flavor?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = stripHtml(it),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
        }

        card.errata?.takeIf { it.isNotBlank() }?.let {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.card_errata, stripHtml(it)),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        linkedCard?.let {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.card_linked_card),
                style = MaterialTheme.typography.titleSmall,
            )
            CardListItem(
                card = it,
                selected = false,
                onClick = { onLinkedCardClick(it.code) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        card.illustrator?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = stringResource(R.string.card_illustrator, it),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun StatRow(card: CardEntity) {
    val stats = buildList {
        card.cost?.let { add(stringResource(R.string.card_stat_cost) to it.toString()) }
        card.attack?.let { add(stringResource(R.string.card_stat_attack) to it.toString()) }
        card.thwart?.let { add(stringResource(R.string.card_stat_thwart) to it.toString()) }
        card.defense?.let { add(stringResource(R.string.card_stat_defense) to it.toString()) }
        card.recover?.let { add(stringResource(R.string.card_stat_recover) to it.toString()) }
        card.health?.let { add(stringResource(R.string.card_stat_health) to it.toString()) }
        card.handSize?.let { add(stringResource(R.string.card_stat_hand_size) to it.toString()) }
        card.scheme?.let { add(stringResource(R.string.card_stat_scheme) to it.toString()) }
        card.boost?.let { add(stringResource(R.string.card_stat_boost) to it.toString()) }
        card.threat?.let { add(stringResource(R.string.card_stat_threat) to it.toString()) }
    }
    if (stats.isEmpty()) {
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        stats.forEach { (label, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleMedium)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun CardLocale.other(): CardLocale =
    if (this == CardLocale.FRENCH) CardLocale.ENGLISH else CardLocale.FRENCH
