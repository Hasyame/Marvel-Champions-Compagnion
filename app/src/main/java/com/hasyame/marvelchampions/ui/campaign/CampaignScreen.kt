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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.repository.CampaignSummary
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState

/**
 * The campaign tab: runs in progress, campaigns already finished, and a way to
 * start another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignScreen(
    onOpenRun: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onStartCampaign: () -> Unit,
    viewModel: CampaignListViewModel = hiltViewModel(),
) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<CampaignSummary?>(null) }

    val inProgress = summaries.filterNot { it.entity.finished }
    val finished = summaries.filter { it.entity.finished }

    Scaffold(
        topBar = { TopAppBar(
            colors = comicTopBarColors(),title = { Text(stringResource(R.string.destination_campaign)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartCampaign,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.campaign_start)) },
            )
        },
    ) { padding ->
        if (summaries.isEmpty()) {
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
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (inProgress.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.campaign_in_progress_section)) }
                items(inProgress, key = { it.entity.id }) { summary ->
                    RunRow(
                        summary = summary,
                        subtitle = summary.entity.difficulty.replaceFirstChar(Char::uppercase),
                        onClick = { onOpenRun(summary.entity.id) },
                        onDelete = { confirmDelete = summary },
                    )
                }
            }

            if (finished.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.campaign_finished_section)) }
                items(finished, key = { it.entity.id }) { summary ->
                    RunRow(
                        summary = summary,
                        subtitle = listOf(
                            summary.entity.difficulty.replaceFirstChar(Char::uppercase),
                            TimerState.format(summary.totalTimeMillis),
                            stringResource(
                                R.string.campaign_stat_vp_short,
                                summary.totalVictoryPoints,
                            ),
                        ).joinToString(" · "),
                        // A finished campaign opens its record, not the run.
                        onClick = { onOpenRecord(summary.entity.id) },
                        onDelete = { confirmDelete = summary },
                    )
                }
            }
        }
    }

    confirmDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.campaign_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.campaign_delete_message,
                        summary.entity.name.ifBlank { summary.entity.templateName },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRun(summary.entity.id)
                        confirmDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
                    errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun RunRow(
    summary: CampaignSummary,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(summary.entity.name.ifBlank { summary.entity.templateName })
        },
        supportingContent = {
            Column {
                Text(subtitle)
                if (summary.heroNames.isNotEmpty()) {
                    Text(
                        text = summary.heroNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.action_delete),
                )
            }
        },
    )
    HorizontalDivider()
}
