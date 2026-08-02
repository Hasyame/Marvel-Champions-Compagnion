package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.ui.decks.DecksViewModel

/** Maximum players a campaign supports. */
private const val MAX_PLAYERS = 4

/**
 * Page 0. Which campaign, what to call this run, who is playing, and how hard.
 *
 * The roster and difficulty are fixed here for the rest of the campaign, so
 * everything is on one page rather than spread across a wizard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartCampaignScreen(
    onBack: () -> Unit,
    onStarted: (String) -> Unit,
    viewModel: CampaignListViewModel = hiltViewModel(),
    decksViewModel: DecksViewModel = hiltViewModel(),
) {
    val available by viewModel.available.collectAsStateWithLifecycle()
    val decks by decksViewModel.uiState.collectAsStateWithLifecycle()
    val chosen by viewModel.importedTemplate.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("") }
    var roster by remember { mutableStateOf(emptyList<String>()) }

    val template: CampaignTemplate? = chosen?.template ?: available.firstOrNull()
    val difficulties = template?.difficulties.orEmpty()
    val effectiveDifficulty = difficulty.ifBlank { difficulties.firstOrNull().orEmpty() }
    val canStart = template != null &&
        roster.isNotEmpty() &&
        effectiveDifficulty.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.campaign_start)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(stringResource(R.string.campaign_which)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { candidate ->
                        FilterChip(
                            selected = template?.id == candidate.id,
                            onClick = { viewModel.choose(candidate) },
                            label = { Text(candidate.name.resolve("fr")) },
                        )
                    }
                }
                if (available.isEmpty()) {
                    Text(
                        text = stringResource(R.string.campaign_none_bundled),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Section(stringResource(R.string.campaign_name)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(template?.name?.resolve("fr").orEmpty()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(stringResource(R.string.campaign_roster)) {
                if (decks.decks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.campaign_no_decks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    decks.decks.forEach { deck ->
                        val selected = deck.id in roster
                        FilterChip(
                            selected = selected,
                            // Four players is the ceiling, so a fifth pick is
                            // refused rather than silently dropped later.
                            enabled = selected || roster.size < MAX_PLAYERS,
                            onClick = {
                                roster = if (selected) roster - deck.id else roster + deck.id
                            },
                            label = { Text("${deck.name} — ${deck.heroName}") },
                        )
                    }
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.campaign_player_count,
                        roster.size,
                        roster.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section(stringResource(R.string.campaign_difficulty)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    difficulties.forEach { value ->
                        FilterChip(
                            selected = effectiveDifficulty == value,
                            onClick = { difficulty = value },
                            label = { Text(value.replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.campaign_roster_locked),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    template?.let { viewModel.choose(it) }
                    viewModel.startRun(effectiveDifficulty, roster, name, onStarted)
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.campaign_lets_go)) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}
