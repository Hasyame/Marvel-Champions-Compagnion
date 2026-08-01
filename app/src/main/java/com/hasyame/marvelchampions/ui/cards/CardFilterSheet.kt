package com.hasyame.marvelchampions.ui.cards

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.repository.CardFilterOptions
import com.hasyame.marvelchampions.domain.model.CardFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFilterSheet(
    filter: CardFilter,
    options: CardFilterOptions,
    onFilterChange: (CardFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.cards_filters),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.action_clear_filters))
                }
            }

            FilterChip(
                selected = filter.ownedOnly,
                onClick = { onFilterChange(filter.copy(ownedOnly = !filter.ownedOnly)) },
                label = { Text(stringResource(R.string.cards_filter_owned_only)) },
            )

            ChipSection(
                title = stringResource(R.string.cards_filter_type),
                values = options.typeCodes,
                selected = filter.typeCodes,
                onToggle = { onFilterChange(filter.copy(typeCodes = filter.typeCodes.toggle(it))) },
            )

            ChipSection(
                title = stringResource(R.string.cards_filter_aspect),
                values = options.factionCodes,
                selected = filter.factionCodes,
                onToggle = {
                    onFilterChange(filter.copy(factionCodes = filter.factionCodes.toggle(it)))
                },
            )

            ChipSection(
                title = stringResource(R.string.cards_filter_cost),
                values = COST_VALUES.map { it.toString() },
                selected = filter.minCost?.let { setOf(it.toString()) } ?: emptySet(),
                onToggle = { value ->
                    val cost = value.toIntOrNull()
                    // Cost is a single exact value for now; a range picker is a
                    // polish job.
                    val alreadySelected = filter.minCost == cost
                    onFilterChange(
                        filter.copy(
                            minCost = if (alreadySelected) null else cost,
                            maxCost = if (alreadySelected) null else cost,
                        ),
                    )
                },
            )

            // Traits are long and numerous, so they scroll sideways rather than
            // pushing everything else off the sheet.
            if (options.traits.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.cards_filter_traits),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.traits.forEach { trait ->
                        FilterChip(
                            selected = trait in filter.traits,
                            onClick = {
                                onFilterChange(filter.copy(traits = filter.traits.toggle(trait)))
                            },
                            label = { Text(trait) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipSection(
    title: String,
    values: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (values.isEmpty()) {
        return
    }
    Text(text = title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = value in selected,
                onClick = { onToggle(value) },
                label = { Text(value.replaceFirstChar(Char::uppercase)) },
            )
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private val COST_VALUES = 0..7
