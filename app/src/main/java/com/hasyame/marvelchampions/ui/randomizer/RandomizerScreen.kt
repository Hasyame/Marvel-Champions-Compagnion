package com.hasyame.marvelchampions.ui.randomizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.RandomizerRepository
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.domain.randomizer.DrawField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomizerScreen(viewModel: RandomizerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.destination_randomizer)) },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.hasNoOwnedPacks -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.randomizer_no_packs)) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    DrawCard(state = state, viewModel = viewModel)
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = viewModel::rollAll, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.randomizer_roll))
                        }
                        OutlinedButton(
                            onClick = viewModel::saveDraw,
                            enabled = state.draw.isComplete,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.randomizer_save_draw))
                        }
                    }
                }
                item { FiltersCard(state = state, viewModel = viewModel) }

                if (state.history.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.randomizer_history),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    items(state.history, key = { it.id }) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(state.names.scenarios[entry.scenarioCode] ?: entry.scenarioCode)
                            },
                            supportingContent = {
                                Text(
                                    RandomizerRepository.parseHeroes(entry.heroes)
                                        .joinToString(", ") { assignment ->
                                            val hero = state.names.heroes[assignment.heroCode]
                                                ?: assignment.heroCode
                                            "$hero (${assignment.aspect})"
                                        },
                                )
                            },
                            trailingContent = {
                                Row {
                                    FilterChip(
                                        selected = entry.beaten,
                                        onClick = { viewModel.setBeaten(entry.id, !entry.beaten) },
                                        label = {
                                            Text(stringResource(R.string.randomizer_beaten))
                                        },
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteHistoryEntry(entry.id) },
                                    ) {
                                        Icon(
                                            Icons.Filled.Clear,
                                            contentDescription = stringResource(R.string.action_delete),
                                        )
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawCard(state: RandomizerUiState, viewModel: RandomizerViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            DrawRow(
                label = stringResource(R.string.randomizer_scenario),
                value = state.draw.scenarioCode
                    ?.let { state.names.scenarios[it] ?: it }
                    ?: stringResource(R.string.randomizer_none),
                field = DrawField.SCENARIO,
                state = state,
                viewModel = viewModel,
            )
            if (state.scenarioNeedsReview) {
                Text(
                    text = stringResource(R.string.randomizer_needs_review),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            DrawRow(
                label = stringResource(R.string.randomizer_difficulty),
                value = state.draw.difficulty?.let { difficultyLabel(it) }
                    ?: stringResource(R.string.randomizer_none),
                field = DrawField.DIFFICULTY,
                state = state,
                viewModel = viewModel,
            )
            DrawRow(
                label = stringResource(R.string.randomizer_modular_sets),
                value = state.draw.modularSetCodes
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { code ->
                        val name = state.names.modularSets[code] ?: code
                        // Mandatory sets are marked so it is obvious which ones
                        // rerolling cannot change.
                        if (code in state.draw.mandatoryModularCodes) "$name*" else name
                    }
                    ?: stringResource(R.string.randomizer_none),
                field = DrawField.MODULAR_SETS,
                state = state,
                viewModel = viewModel,
            )
            DrawRow(
                label = stringResource(R.string.randomizer_players),
                value = state.draw.playerCount.toString(),
                field = DrawField.PLAYER_COUNT,
                state = state,
                viewModel = viewModel,
            )
            DrawRow(
                label = stringResource(R.string.randomizer_heroes),
                value = state.draw.heroes
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { state.names.heroes[it.heroCode] ?: it.heroCode }
                    ?: stringResource(R.string.randomizer_none),
                field = DrawField.HEROES,
                state = state,
                viewModel = viewModel,
            )
            DrawRow(
                label = stringResource(R.string.randomizer_aspects),
                // map is inline so it can host the composable label lookup;
                // joinToString is not, hence the two steps.
                value = state.draw.heroes
                    .map { aspectLabel(it.aspect) }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
                    ?: stringResource(R.string.randomizer_none),
                field = DrawField.ASPECTS,
                state = state,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun DrawRow(
    label: String,
    value: String,
    field: DrawField,
    state: RandomizerUiState,
    viewModel: RandomizerViewModel,
) {
    val isLocked = field in state.locked
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value, style = MaterialTheme.typography.titleMedium) },
        trailingContent = {
            Row {
                IconButton(onClick = { viewModel.toggleLock(field) }) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(
                            if (isLocked) R.string.randomizer_unlock else R.string.randomizer_lock,
                        ),
                        tint = if (isLocked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = { viewModel.reroll(field) }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.randomizer_reroll),
                    )
                }
            }
        },
    )
}

@Composable
private fun FiltersCard(state: RandomizerUiState, viewModel: RandomizerViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.cards_filters),
                style = MaterialTheme.typography.titleMedium,
            )

            FilterChip(
                selected = state.excludeBeaten,
                onClick = { viewModel.setExcludeBeaten(!state.excludeBeaten) },
                label = { Text(stringResource(R.string.randomizer_exclude_beaten)) },
            )

            Text(
                text = stringResource(R.string.randomizer_difficulty),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { difficulty ->
                    val selected = difficulty in state.filters.allowedDifficulties
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = if (selected) {
                                state.filters.allowedDifficulties - difficulty
                            } else {
                                state.filters.allowedDifficulties + difficulty
                            }
                            viewModel.setAllowedDifficulties(next)
                        },
                        label = { Text(difficultyLabel(difficulty)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.randomizer_excluded_aspects),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RandomizerRepository.ASPECTS.forEach { aspect ->
                    FilterChip(
                        selected = aspect in state.filters.excludedAspects,
                        onClick = { viewModel.toggleExcludedAspect(aspect) },
                        label = { Text(aspectLabel(aspect)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.randomizer_players),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { count ->
                    val selected = state.filters.minPlayers <= count &&
                        state.filters.maxPlayers >= count
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setPlayerRange(count, count) },
                        label = { Text(count.toString()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: Difficulty): String = stringResource(
    when (difficulty) {
        Difficulty.STANDARD_I -> R.string.difficulty_standard_i
        Difficulty.STANDARD_II -> R.string.difficulty_standard_ii
        Difficulty.EXPERT_I -> R.string.difficulty_expert_i
        Difficulty.EXPERT_II -> R.string.difficulty_expert_ii
    },
)

@Composable
private fun aspectLabel(aspect: String): String = stringResource(
    when (aspect) {
        "aggression" -> R.string.aspect_aggression
        "justice" -> R.string.aspect_justice
        "leadership" -> R.string.aspect_leadership
        "protection" -> R.string.aspect_protection
        "pool" -> R.string.aspect_pool
        else -> R.string.randomizer_none
    },
)
