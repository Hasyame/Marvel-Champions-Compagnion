package com.hasyame.marvelchampions.ui.plays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.data.repository.PlayRecorded
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import kotlinx.coroutines.delay

/**
 * Set up a game yourself, then have the app time it.
 *
 * The randomiser answers "what shall I play"; this answers "I already know what
 * I am playing, record it properly". They are different acts, so this is its
 * own screen rather than a mode of the draw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSessionScreen(
    onBack: () -> Unit,
    scenarioCode: String? = null,
    difficulty: String? = null,
    heroes: String? = null,
    onOpenPlays: () -> Unit,
    viewModel: GameSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recorded by viewModel.recorded.collectAsStateWithLifecycle()

    // Leaving mid-game throws away a running clock and everything set up, so
    // it asks first. Only while playing: backing out of the setup loses nothing
    // worth confirming.
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = {
        if (state.phase == SessionPhase.PLAYING) confirmLeave = true else onBack()
    }

    // A draw handed over from the randomiser, applied once.
    LaunchedEffect(scenarioCode, difficulty, heroes) {
        viewModel.prefill(scenarioCode, difficulty, heroes)
    }

    // Only while the clock is actually running, so a paused or finished game
    // is not waking the composition once a second for nothing.
    LaunchedEffect(state.phase, state.timer.isRunning) {
        while (state.phase == SessionPhase.PLAYING && state.timer.isRunning) {
            viewModel.tick()
            delay(TICK_MILLIS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.session_title)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.phase == SessionPhase.SETUP -> SetupPhase(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )

            else -> PlayingPhase(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.session_leave_title)) },
            text = { Text(stringResource(R.string.session_leave_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onBack()
                    },
                ) { Text(stringResource(R.string.session_leave_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(R.string.session_leave_no))
                }
            },
        )
    }

    recorded?.let { outcome ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRecorded,
            title = { Text(stringResource(R.string.session_saved_title)) },
            text = {
                Text(
                    when (outcome) {
                        is PlayRecorded.SavedOnly -> stringResource(R.string.session_saved)
                        is PlayRecorded.SavedAndReported ->
                            stringResource(R.string.session_saved_sent)

                        is PlayRecorded.SavedAskToReport ->
                            stringResource(R.string.session_saved_can_send)

                        is PlayRecorded.SavedReportFailed ->
                            stringResource(R.string.session_saved_not_sent, outcome.detail)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reset()
                        onOpenPlays()
                    },
                ) { Text(stringResource(R.string.session_see_stats)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::reset) {
                    Text(stringResource(R.string.session_another))
                }
            },
        )
    }
}

@Composable
private fun SetupPhase(
    state: GameSessionUiState,
    viewModel: GameSessionViewModel,
    modifier: Modifier = Modifier,
) {
    var pendingHero by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PickerSection(stringResource(R.string.session_scenario)) {
            state.pools.scenarios.forEach { scenario ->
                FilterChip(
                    selected = state.scenarioCode == scenario.code,
                    onClick = { viewModel.setScenario(scenario.code) },
                    label = {
                        Text(state.names.scenarios[scenario.code] ?: scenario.code)
                    },
                )
            }
        }

        PickerSection(stringResource(R.string.session_difficulty)) {
            DIFFICULTIES.forEach { difficulty ->
                FilterChip(
                    selected = state.difficulty == difficulty,
                    onClick = { viewModel.setDifficulty(difficulty) },
                    label = { Text(difficulty.replaceFirstChar(Char::uppercase)) },
                )
            }
        }

        // Hero then aspect, in that order: picking a hero first and an aspect
        // second is how a player actually decides, and it keeps the chip list
        // to one long list rather than every hero-aspect pairing.
        PickerSection(stringResource(R.string.session_hero)) {
            state.pools.heroes.forEach { hero ->
                FilterChip(
                    selected = pendingHero == hero.code,
                    onClick = { pendingHero = if (pendingHero == hero.code) null else hero.code },
                    label = { Text(state.names.heroes[hero.code] ?: hero.code) },
                )
            }
        }

        pendingHero?.let { heroCode ->
            PickerSection(stringResource(R.string.session_aspect)) {
                state.pools.aspects.forEach { aspect ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            viewModel.addHero(heroCode, aspect)
                            pendingHero = null
                        },
                        label = { Text(aspect.replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
        }

        if (state.heroes.isNotEmpty()) {
            Text(
                text = stringResource(R.string.session_table),
                style = MaterialTheme.typography.titleSmall,
            )
            state.heroes.forEachIndexed { index, hero ->
                ListItem(
                    headlineContent = {
                        Text(state.names.heroes[hero.heroCode] ?: hero.heroCode)
                    },
                    supportingContent = { Text(hero.aspect.replaceFirstChar(Char::uppercase)) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeHero(index) }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    },
                )
            }
        }

        Button(
            onClick = viewModel::start,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.session_start)) }
    }
}

@Composable
private fun PickerSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun PlayingPhase(
    state: GameSessionUiState,
    viewModel: GameSessionViewModel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .halftone(MaterialTheme.colorScheme.onBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = state.scenarioCode
                            ?.let { state.names.scenarios[it] ?: it }
                            .orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.heroes.joinToString(", ") {
                            state.names.heroes[it.heroCode] ?: it.heroCode
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = TimerState.format(state.elapsedMillis),
                style = MaterialTheme.typography.displayLarge,
            )

            OutlinedButton(
                onClick = if (state.timer.isRunning) viewModel::pause else viewModel::resume,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.timer.isRunning) {
                            R.string.session_pause
                        } else {
                            R.string.session_resume
                        },
                    ),
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.finish(won = true) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.session_won)) }
                OutlinedButton(
                    onClick = { viewModel.finish(won = false) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.session_lost)) }
            }
        }
    }
}

/** Difficulties as the play log records them, lower case for grouping. */
private val DIFFICULTIES = listOf("standard", "expert", "heroic")

private const val TICK_MILLIS = 1_000L
