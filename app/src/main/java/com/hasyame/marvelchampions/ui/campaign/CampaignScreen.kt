package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R

/**
 * The campaign tab: runs in progress or finished, and a way to start another.
 *
 * Starting one is its own page rather than a dialog, because it asks for four
 * things and the roster can be up to four decks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignScreen(
    onOpenRun: (String) -> Unit,
    onStartCampaign: () -> Unit,
    viewModel: CampaignListViewModel = hiltViewModel(),
) {
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.destination_campaign)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartCampaign,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.campaign_start)) },
            )
        },
    ) { padding ->
        if (runs.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.campaign_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(runs, key = { it.id }) { run ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenRun(run.id) },
                        headlineContent = { Text(run.name.ifBlank { run.templateName }) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    run.templateName.takeIf { it != run.name },
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
        }
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
