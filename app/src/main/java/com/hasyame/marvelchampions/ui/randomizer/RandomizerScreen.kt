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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.repository.RandomizerRepository
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.domain.randomizer.DrawField
import com.hasyame.marvelchampions.ui.plays.PlaysViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomizerScreen(
    onOpenPlays: () -> Unit,
    onNewGame: () -> Unit,
    viewModel: RandomizerViewModel = hiltViewModel(),
    playsViewModel: PlaysViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var logOutcome by remember { mutableStateOf(false) }

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

                // Logging the game you just played is a different act from
                // saving the draw you are about to play, so it is its own
                // button rather than a mode of the one above.
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { logOutcome = true },
                            enabled = state.draw.isComplete,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.randomizer_log_play))
                        }
                        OutlinedButton(onClick = onNewGame, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.randomizer_new_game))
                        }
                        OutlinedButton(onClick = onOpenPlays, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.randomizer_open_plays))
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

/**
 * Asks only what the app cannot know: did you win, and how long did it take.
 *
 * Everything else — scenario, difficulty, heroes, aspects, player count — is
 * already on screen in the draw, so re-asking for it would be a form the player
 * has to fill in twice.
 */
@Composable
private fun LogPlayDialog(
    state: RandomizerUiState,
    onDismiss: () -> Unit,
    onLog: (won: Boolean, minutes: Int) -> Unit,
) {
    var minutes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plays_log_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = state.names.scenarios[state.draw.scenarioCode].orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.plays_log_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onLog(true, minutes.toIntOrNull() ?: 0) }) {
                Text(stringResource(R.string.plays_log_win))
            }
        },
        dismissButton = {
            TextButton(onClick = { onLog(false, minutes.toIntOrNull() ?: 0) }) {
                Text(stringResource(R.string.plays_log_loss))
            }
        },
    )
}

/**
 * Turns the draw on screen into a recorded play.
 *
 * The first hero is the one the statistics group by; the rest are kept as text
 * for the history to show. Names are resolved now rather than looked up later,
 * so the record stays legible whatever happens to the card database.
 */
private fun RandomizerUiState.toPlay(won: Boolean, minutes: Int, id: String): PlayEntity {
    val assignments = draw.heroes
    val first = assignments.firstOrNull()

    return PlayEntity(
        id = id,
        playedAt = System.currentTimeMillis(),
        scenarioCode = draw.scenarioCode.orEmpty(),
        scenarioName = names.scenarios[draw.scenarioCode] ?: draw.scenarioCode.orEmpty(),
        difficulty = draw.difficulty?.name?.lowercase().orEmpty(),
        heroCode = first?.heroCode.orEmpty(),
        heroName = first?.let { names.heroes[it.heroCode] ?: it.heroCode }.orEmpty(),
        aspects = assignments.map { it.aspect }.distinct().joinToString(", "),
        otherHeroes = assignments.drop(1)
            .joinToString(", ") { names.heroes[it.heroCode] ?: it.heroCode },
        players = draw.playerCount,
        won = won,
        elapsedMillis = minutes * MILLIS_PER_MINUTE,
    )
}

private const val MILLIS_PER_MINUTE = 60_000L
