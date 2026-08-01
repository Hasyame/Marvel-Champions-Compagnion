package com.hasyame.marvelchampions.ui.campaign

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.engine.MarketRules
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.campaign.template.CounterScope
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignRunScreen(
    runId: String,
    onBack: () -> Unit,
    viewModel: CampaignRunViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var questionnaireVictory by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(runId) { viewModel.load(runId) }

    // Only ticks while something is running, so a paused run costs nothing.
    LaunchedEffect(state.run?.timer?.isRunning) {
        while (state.run?.timer?.isRunning == true) {
            viewModel.tick()
            delay(1_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.run?.entity?.templateName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val run = state.run
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            run == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.campaign_run_not_found)) }

            else -> RunBody(
                run = run,
                elapsedMillis = state.elapsedMillis,
                viewModel = viewModel,
                padding = padding,
                onFinishScenario = { questionnaireVictory = it },
            )
        }
    }

    val run = state.run
    val victory = questionnaireVictory
    if (run != null && victory != null) {
        val scenario = run.template.scenarios.firstOrNull { it.id == run.state.currentScenarioId }
        ScenarioOutcomeDialog(
            scenario = scenario,
            victory = victory,
            heroes = run.state.heroes,
            difficulty = run.state.difficulty,
            state = run.state,
            onDismiss = { questionnaireVictory = null },
            onConfirm = { answers ->
                viewModel.completeScenario(victory, answers)
                questionnaireVictory = null
            },
        )
    }
}

@Composable
private fun RunBody(
    run: CampaignRun,
    elapsedMillis: Long,
    viewModel: CampaignRunViewModel,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onFinishScenario: (Boolean) -> Unit,
) {
    val scenario = run.template.scenarios.firstOrNull { it.id == run.state.currentScenarioId }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CountersCard(run) }

        if (run.state.finished) {
            item {
                Text(
                    text = stringResource(R.string.campaign_complete),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        scenario?.let {
            item { ScenarioSetupCard(run, it, viewModel) }
            item {
                TimerCard(
                    elapsedMillis = elapsedMillis,
                    isRunning = run.timer.isRunning,
                    onStart = viewModel::startTimer,
                    onPause = viewModel::pauseTimer,
                    onReset = viewModel::resetTimer,
                    onVictory = { onFinishScenario(true) },
                    onDefeat = { onFinishScenario(false) },
                )
            }
        }

        if (run.template.market != null) {
            item { MarketCard(run, viewModel) }
        }

        if (run.state.completedScenarios.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.campaign_log),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(run.state.completedScenarios, key = { it.eventId }) { result ->
                ListItem(
                    headlineContent = {
                        Text(
                            scenarioName(run, result.scenarioId) + " — " +
                                stringResource(
                                    if (result.victory) {
                                        R.string.campaign_victory
                                    } else {
                                        R.string.campaign_defeat
                                    },
                                ),
                        )
                    },
                    supportingContent = {
                        // Recorded answers are shown here because some of them —
                        // victory points above all — are a per-scenario record
                        // that nothing carries forward, so the log is the only
                        // place they exist.
                        val recorded = result.answers.numbers.entries
                            .joinToString(" · ") { "${it.key} ${it.value}" }
                        Text(
                            listOfNotNull(
                                TimerState.format(result.elapsedMillis),
                                recorded.takeIf { it.isNotBlank() },
                            ).joinToString(" — "),
                        )
                    },
                    trailingContent = {
                        // Editing a past result is a revocation, logged as such
                        // rather than a silent rewrite.
                        OutlinedButton(onClick = { viewModel.revoke(result.eventId, null) }) {
                            Text(stringResource(R.string.campaign_undo))
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CountersCard(run: CampaignRun) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = run.state.difficulty.replaceFirstChar(Char::uppercase),
                style = MaterialTheme.typography.labelLarge,
            )
            run.template.counters.forEach { counter ->
                when (counter.counterScope) {
                    CounterScope.CAMPAIGN -> Text("${counter.id}: ${run.state.counter(counter.id)}")
                    CounterScope.HERO -> {
                        val active = counter.activeWhen == null ||
                            ConditionEvaluator.evaluate(
                                counter.activeWhen,
                                EvaluationContext(run.state),
                            )
                        if (active) {
                            Text(
                                text = counter.id + ": " + run.state.heroes.joinToString(", ") {
                                    "${it.name} ${run.state.heroCounter(counter.id, it.id)}"
                                },
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(
                    R.string.campaign_total_time,
                    TimerState.format(run.state.totalPlayTimeMillis),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ScenarioSetupCard(
    run: CampaignRun,
    scenario: ScenarioTemplate,
    viewModel: CampaignRunViewModel,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = scenario.name?.resolve("fr").orEmpty().ifBlank { scenario.id },
                style = MaterialTheme.typography.titleLarge,
            )
            scenario.flavour?.resolve("fr")?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            scenario.baseSetup?.let { setup ->
                setup.villainDeck[run.state.difficulty]?.takeIf { it.isNotEmpty() }?.let {
                    Text(stringResource(R.string.campaign_villain_deck, it.joinToString(", ")))
                }
                setup.mainScheme.takeIf { it.isNotEmpty() }?.let {
                    Text(stringResource(R.string.campaign_main_scheme, it.joinToString(", ")))
                }
                (setup.encounterSets + setup.modularSets).takeIf { it.isNotEmpty() }?.let {
                    Text(stringResource(R.string.campaign_encounter_sets, it.joinToString(", ")))
                }
            }

            val context = EvaluationContext(run.state, scenario.id)
            scenario.campaignSetup
                .filter { ConditionEvaluator.evaluate(it.condition, context) }
                .forEach { step ->
                    Text("• " + step.text.resolve("fr"))
                    step.action?.let { action ->
                        val enabled = ConditionEvaluator.evaluate(action.enabledWhen, context)
                        if (action.perHero) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                run.state.heroes.forEach { hero ->
                                    OutlinedButton(
                                        onClick = { viewModel.takeSetupAction(action.id, hero.id) },
                                        enabled = enabled,
                                    ) { Text("${action.label.resolve("fr")} — ${hero.name}") }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.takeSetupAction(action.id, null) },
                                enabled = enabled,
                            ) { Text(action.label.resolve("fr")) }
                        }
                    }
                }
        }
    }
}

@Composable
private fun TimerCard(
    elapsedMillis: Long,
    isRunning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onVictory: () -> Unit,
    onDefeat: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(TimerState.format(elapsedMillis), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (isRunning) onPause else onStart) {
                    Text(
                        stringResource(
                            if (isRunning) R.string.campaign_pause else R.string.campaign_play,
                        ),
                    )
                }
                OutlinedButton(onClick = onReset) { Text(stringResource(R.string.campaign_reset)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onVictory, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.campaign_victory))
                }
                OutlinedButton(onClick = onDefeat, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.campaign_defeat))
                }
            }
        }
    }
}

@Composable
private fun MarketCard(run: CampaignRun, viewModel: CampaignRunViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.campaign_market),
                style = MaterialTheme.typography.titleMedium,
            )
            run.state.heroes.forEach { hero ->
                Text(
                    text = hero.name + " — " + run.state.heroCounter(
                        run.template.market?.counterId ?: "credits",
                        hero.id,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                MarketRules.offersFor(run.template, run.state, hero.id).forEach { offer ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${offer.entry.cardCode} (${offer.entry.cost})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = {
                                viewModel.purchase(
                                    hero.id,
                                    offer.entry.cardCode,
                                    offer.entry.cost,
                                    offer.entry.cardListId,
                                )
                            },
                            enabled = offer.canBuy,
                        ) { Text(stringResource(R.string.campaign_buy)) }
                    }
                }
            }
            if (run.state.purchases.isNotEmpty()) {
                HorizontalDivider()
                run.state.purchases.forEach { purchase ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(purchase.cardCode, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { viewModel.refund(purchase.eventId) }) {
                            Text(stringResource(R.string.campaign_refund))
                        }
                    }
                }
            }
        }
    }
}

private fun scenarioName(run: CampaignRun, scenarioId: String): String =
    run.template.scenarios.firstOrNull { it.id == scenarioId }
        ?.name?.resolve("fr")
        ?.takeIf { it.isNotBlank() }
        ?: scenarioId
