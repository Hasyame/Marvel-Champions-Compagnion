package com.hasyame.marvelchampions.ui.campaign

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.ui.decks.DecksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignScreen(
    onOpenRun: (String) -> Unit,
    viewModel: CampaignListViewModel = hiltViewModel(),
    decksViewModel: DecksViewModel = hiltViewModel(),
) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val imported by viewModel.importedTemplate.collectAsStateWithLifecycle()
    val decks by decksViewModel.uiState.collectAsStateWithLifecycle()

    // Templates hold campaign book text, so they are never bundled: the user
    // picks their own file through the storage picker.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importTemplate) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.destination_campaign)) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (runs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.campaign_my_runs),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(runs, key = { it.id }) { run ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenRun(run.id) },
                        headlineContent = { Text(run.templateName) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    run.difficulty.replaceFirstChar(Char::uppercase),
                                    if (run.finished) {
                                        stringResource(R.string.campaign_finished)
                                    } else {
                                        stringResource(R.string.campaign_in_progress)
                                    },
                                ).joinToString(" · "),
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteRun(run.id) }) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.action_delete),
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }

            item {
                Text(
                    text = stringResource(R.string.campaign_start),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (available.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.campaign_none_bundled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            items(available, key = { it.id }) { template ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.choose(template) },
                    headlineContent = { Text(template.name.resolve("fr")) },
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.campaign_scenario_count,
                                template.scenarios.size,
                                template.scenarios.size,
                            ),
                        )
                    },
                )
                HorizontalDivider()
            }

            item {
                // Still available for a template kept on the device rather than
                // built into the app — editing one without a rebuild, or moving
                // it between phone and tablet.
                TextButton(
                    onClick = { picker.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.padding(8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.campaign_import_file),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }

    imported?.let { pending ->
        StartCampaignDialog(
            templateName = pending.template.name.resolve("fr"),
            difficulties = pending.template.difficulties,
            deckNames = decks.decks.associate { it.id to it.name },
            onDismiss = viewModel::cancelImport,
            onStart = { difficulty, deckIds ->
                viewModel.startRun(difficulty, deckIds, onOpenRun)
            },
        )
    }

    if (errors.isNotEmpty() || message != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessages,
            confirmButton = {
                TextButton(onClick = viewModel::dismissMessages) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.campaign_template_invalid)) },
            text = {
                Column {
                    message?.let { Text(it) }
                    // Every problem at once, so a hand-written template can be
                    // fixed in one pass.
                    errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
        )
    }
}

@Composable
private fun StartCampaignDialog(
    templateName: String,
    difficulties: List<String>,
    deckNames: Map<String, String>,
    onDismiss: () -> Unit,
    onStart: (String, List<String>) -> Unit,
) {
    var difficulty by remember { mutableStateOf(difficulties.firstOrNull() ?: "standard") }
    var chosenDecks by remember { mutableStateOf(emptySet<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(templateName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.campaign_difficulty),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    difficulties.forEach { value ->
                        FilterChip(
                            selected = difficulty == value,
                            onClick = { difficulty = value },
                            label = { Text(value.replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.campaign_roster),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (deckNames.isEmpty()) {
                    Text(
                        text = stringResource(R.string.campaign_no_decks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    deckNames.forEach { (id, name) ->
                        FilterChip(
                            selected = id in chosenDecks,
                            onClick = {
                                chosenDecks = if (id in chosenDecks) {
                                    chosenDecks - id
                                } else {
                                    chosenDecks + id
                                }
                            },
                            label = { Text(name) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.campaign_roster_locked),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(difficulty, chosenDecks.toList()) },
                enabled = chosenDecks.isNotEmpty(),
            ) { Text(stringResource(R.string.campaign_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
