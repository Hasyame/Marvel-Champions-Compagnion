package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.core.designsystem.component.ComicPanel
import com.hasyame.marvelchampions.core.designsystem.component.halftone
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import com.hasyame.marvelchampions.ui.util.openExternalUrl
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignRunScreen(
    runId: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: CampaignRunViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(runId) { viewModel.load(runId) }

    LaunchedEffect(state.run?.timer?.isRunning) {
        while (state.run?.timer?.isRunning == true) {
            viewModel.tick()
            delay(1_000)
        }
    }

    val run = state.run
    val scenario = run?.template?.scenarios?.firstOrNull { it.id == run.state.currentScenarioId }

    Scaffold(
        topBar = {
            TopAppBar(
            colors = comicTopBarColors(),
                title = {
                    Text(
                        scenario?.name?.resolve("fr")?.takeIf { it.isNotBlank() }
                            ?: run?.entity?.name.orEmpty(),
                    )
                },
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
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            run == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.campaign_run_not_found)) }

            // The campaign tab is the part of the app that is looked at rather
            // than read, so it gets the printed-paper texture. Card and deck
            // lists deliberately stay plain.
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .halftone(MaterialTheme.colorScheme.onBackground),
            ) {
                when (state.page) {
                    RunPage.BRIEFING -> BriefingPage(
                        run = run,
                        scenario = scenario,
                        onReady = viewModel::beginScenario,
                        onNotReady = onBack,
                        onCardClick = onCardClick,
                        onSetupAction = viewModel::takeSetupAction,
                    )

                    RunPage.PLAYING -> PlayingPage(
                        isSubmitting = state.isSubmitting,
                        run = run,
                        scenario = scenario,
                        elapsedMillis = state.elapsedMillis,
                        musicUrl = state.musicUrl,
                        onVictory = viewModel::declareVictory,
                        onDefeat = viewModel::declareDefeat,
                        onPause = viewModel::pauseTimer,
                        onResume = viewModel::resumeTimer,
                    )

                    RunPage.QUESTIONS -> QuestionsPage(
                        isSubmitting = state.isSubmitting,
                        run = run,
                        scenario = scenario,
                        onSubmit = viewModel::submitAnswers,
                    )

                    RunPage.RESULT -> ResultPage(
                        run = run,
                        summary = state.summary,
                        onNext = viewModel::continueToNextScenario,
                        onMarket = { viewModel.goTo(RunPage.MARKET) },
                        onBreak = onBack,
                        onForget = { viewModel.forgetCampaign(onBack) },
                    )

                    RunPage.DEFEAT -> DefeatPage(
                        summary = state.summary,
                        onRestart = viewModel::replayScenario,
                        onBreak = onBack,
                    )

                    RunPage.MARKET -> MarketPage(
                        run = run,
                        onBuy = viewModel::purchase,
                        onRefund = viewModel::refund,
                        onCardClick = onCardClick,
                        onDone = { viewModel.goTo(RunPage.BRIEFING) },
                    )
                }
            }
        }
    }
}

/** Page 1. Title, story, what to put on the table. */
@Composable
private fun BriefingPage(
    run: CampaignRun,
    scenario: ScenarioTemplate?,
    onReady: () -> Unit,
    onNotReady: () -> Unit,
    onCardClick: (String) -> Unit,
    onSetupAction: (String, String?) -> Unit,
) {
    if (scenario == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.campaign_complete))
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The story sits in a caption box, which is both what a comic does with
        // narration and what makes it readable: italic body text straight on
        // the halftone had the dots showing through every line.
        scenario.flavour?.resolve("fr")?.takeIf { it.isNotBlank() }?.let {
            ComicPanel(Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        scenario.baseSetup?.let { setup ->
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.campaign_pre_setup),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    setup.villainDeck[run.state.difficulty]?.takeIf { it.isNotEmpty() }?.let {
                        CardChips(
                            label = stringResource(R.string.campaign_villain_deck_label),
                            codes = it,
                            run = run,
                            onCardClick = onCardClick,
                        )
                    }
                    setup.mainScheme.takeIf { it.isNotEmpty() }?.let {
                        CardChips(
                            label = stringResource(R.string.campaign_main_scheme_label),
                            codes = it,
                            run = run,
                            onCardClick = onCardClick,
                        )
                    }
                    (setup.encounterSets + setup.modularSets).takeIf { it.isNotEmpty() }?.let { sets ->
                        Column {
                            Text(
                                text = stringResource(R.string.campaign_encounter_sets_label),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(sets.joinToString(", ") { run.names.set(it) })
                        }
                    }
                }
            }
        }

        if (scenario.campaignSetup.isNotEmpty()) {
            val context = EvaluationContext(run.state, scenario.id)
            ComicPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.campaign_setup_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    scenario.campaignSetup
                        .filter { ConditionEvaluator.evaluate(it.condition, context) }
                        .forEach { step ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("• " + step.text.resolve("fr"))

                                // Values the campaign log carries forward from
                                // earlier scenarios, so the step can be
                                // followed without leafing back through it.
                                step.showCounter?.let { counterId ->
                                    Text(
                                        text = "$counterId: ${run.state.counter(counterId)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                step.showCardList?.let { listId ->
                                    val recorded = run.state.cardLists[listId].orEmpty()
                                    Text(
                                        // Entries may be card codes or free
                                        // text; a code resolves to its name,
                                        // anything else shows as typed.
                                        text = recorded.takeIf { it.isNotEmpty() }
                                            ?.joinToString(", ") { run.names.card(it) }
                                            ?: stringResource(R.string.campaign_nothing_recorded),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                step.showHeroesWith?.let { counterId ->
                                    val holders = run.state.heroes
                                        .filter { run.state.heroCounter(counterId, it.id) > 0 }
                                    Text(
                                        text = holders.takeIf { it.isNotEmpty() }
                                            ?.joinToString(", ") { it.name }
                                            ?: stringResource(R.string.campaign_nobody),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                if (step.cards.isNotEmpty()) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        step.cards.forEach { code ->
                                            AssistChip(
                                                onClick = { onCardClick(code) },
                                                label = { Text(run.names.card(code)) },
                                            )
                                        }
                                    }
                                }
                                step.action?.let { action ->
                                    val enabled =
                                        ConditionEvaluator.evaluate(action.enabledWhen, context)
                                    if (action.perHero) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            run.state.heroes.forEach { hero ->
                                                OutlinedButton(
                                                    onClick = { onSetupAction(action.id, hero.id) },
                                                    enabled = enabled,
                                                ) {
                                                    Text(
                                                        action.label.resolve("fr") +
                                                            " — " + hero.name,
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onSetupAction(action.id, null) },
                                            enabled = enabled,
                                        ) { Text(action.label.resolve("fr")) }
                                    }
                                }
                            }
                        }
                }
            }
        }

        Button(onClick = onReady, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_im_ready))
        }
        OutlinedButton(onClick = onNotReady, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_not_ready))
        }
    }
}

@Composable
private fun CardChips(
    label: String,
    codes: List<String>,
    run: CampaignRun,
    onCardClick: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            codes.forEach { code ->
                AssistChip(
                    onClick = { onCardClick(code) },
                    label = { Text(run.names.card(code)) },
                )
            }
        }
    }
}

/** Page 2. The clock, and the two ways a scenario ends. */
@Composable
private fun PlayingPage(
    isSubmitting: Boolean = false,
    run: CampaignRun,
    scenario: ScenarioTemplate?,
    elapsedMillis: Long,
    musicUrl: String,
    onVictory: () -> Unit,
    onDefeat: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val context = LocalContext.current
    var musicUnavailable by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = scenario?.name?.resolve("fr").orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = TimerState.format(elapsedMillis),
            style = MaterialTheme.typography.displayLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = if (run.timer.isRunning) onPause else onResume) {
                Text(
                    stringResource(
                        if (run.timer.isRunning) {
                            R.string.campaign_pause
                        } else {
                            R.string.campaign_play
                        },
                    ),
                )
            }
            // The playlist plays in Spotify or the browser, which keeps
            // playing while this screen stays on the timer.
            OutlinedButton(
                onClick = { musicUnavailable = !openExternalUrl(context, musicUrl) },
                enabled = musicUrl.isNotBlank(),
            ) { Text(stringResource(R.string.campaign_music)) }
        }
        if (musicUnavailable) {
            Text(
                text = stringResource(R.string.campaign_music_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onVictory,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                scenario?.victoryLabel?.resolve("fr")?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.campaign_victory),
            )
        }
        OutlinedButton(
            onClick = onDefeat,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                scenario?.defeatLabel?.resolve("fr")?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.campaign_defeat),
            )
        }
    }
}

/** Page 4. What the scenario awarded, and the three ways onward. */
@Composable
private fun ResultPage(
    run: CampaignRun,
    summary: ScenarioOutcomeSummary?,
    onNext: () -> Unit,
    onMarket: () -> Unit,
    onBreak: () -> Unit,
    onForget: () -> Unit,
) {
    var confirmForget by remember { mutableStateOf(false) }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.campaign_delete_title)) },
            text = { Text(stringResource(R.string.campaign_forget_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForget = false
                        onForget()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.campaign_bravo),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        val gained = summary?.creditsGained.orEmpty()
        val distinct = gained.values.distinct()
        // Prose over halftone is the one thing the texture spoils, so what the
        // scenario awarded goes in a caption box like the story does.
        ComicPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (distinct.size == 1 && run.state.heroes.size == 1) {
                        pluralStringResource(
                            R.plurals.campaign_result_solo,
                            distinct.single(),
                            distinct.single(),
                            TimerState.format(summary?.elapsedMillis ?: 0),
                            summary?.victoryPoints ?: 0,
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.campaign_result_group,
                            summary?.victoryPoints ?: 0,
                            TimerState.format(summary?.elapsedMillis ?: 0),
                            summary?.victoryPoints ?: 0,
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (run.state.heroes.size > 1) {
                    run.state.heroes.forEach { hero ->
                        Text("${hero.name}: +${gained[hero.id] ?: 0}")
                    }
                }
            }
        }

        HorizontalDivider()

        if (summary?.campaignFinished == true) {
            Text(
                text = stringResource(R.string.campaign_complete),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.campaign_keep_or_forget),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.campaign_save_it))
            }
            OutlinedButton(
                onClick = { confirmForget = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.campaign_forget_it)) }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        R.string.campaign_go_to_next,
                        summary?.nextScenarioName.orEmpty(),
                    ),
                )
            }
        }
        OutlinedButton(onClick = onMarket, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_need_to_buy))
        }
        OutlinedButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_take_a_break))
        }
    }
}

/** Page 3, the other branch. */
@Composable
private fun DefeatPage(
    summary: ScenarioOutcomeSummary?,
    onRestart: () -> Unit,
    onBreak: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.campaign_defeat_recorded),
            style = MaterialTheme.typography.headlineMedium,
        )
        ComicPanel(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    R.string.campaign_defeat_time,
                    TimerState.format(summary?.elapsedMillis ?: 0),
                ),
                modifier = Modifier.padding(16.dp),
            )
        }
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_all_day))
        }
        OutlinedButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.campaign_take_a_break))
        }
    }
}
