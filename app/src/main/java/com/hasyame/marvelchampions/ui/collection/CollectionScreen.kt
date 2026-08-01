package com.hasyame.marvelchampions.ui.collection

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.repository.PackOwnership
import com.hasyame.marvelchampions.domain.model.PackType

/**
 * F1. A full screen of its own rather than a section inside settings, because
 * it is the source of truth for the randomiser and for deck legality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collection_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    text = pluralStringResource(
                        R.plurals.collection_owned_count,
                        state.totalCount,
                        state.ownedCount,
                        state.totalCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                BulkActions(
                    onSelectType = { type -> viewModel.selectAllOfType(type, owned = true) },
                    onSelectAll = { viewModel.selectAll(owned = true) },
                    onClearAll = { viewModel.selectAll(owned = false) },
                )
                HorizontalDivider()
            }

            state.waves.forEach { group ->
                item(key = "wave-${group.wave}") {
                    Text(
                        text = stringResource(R.string.collection_wave, group.wave),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(group.packs, key = { it.pack.code }) { ownership ->
                    PackRow(
                        ownership = ownership,
                        onToggle = { viewModel.setOwned(ownership.pack.code, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkActions(
    onSelectType: (PackType) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onSelectAll,
            label = { Text(stringResource(R.string.collection_select_all)) },
        )
        AssistChip(
            onClick = { onSelectType(PackType.CORE) },
            label = { Text(stringResource(R.string.collection_select_core)) },
        )
        AssistChip(
            onClick = { onSelectType(PackType.HERO_PACK) },
            label = { Text(stringResource(R.string.collection_select_hero_packs)) },
        )
        AssistChip(
            onClick = { onSelectType(PackType.SCENARIO_PACK) },
            label = { Text(stringResource(R.string.collection_select_scenario_packs)) },
        )
        AssistChip(
            onClick = { onSelectType(PackType.CAMPAIGN_BOX) },
            label = { Text(stringResource(R.string.collection_select_campaign_boxes)) },
        )
        AssistChip(
            onClick = onClearAll,
            label = { Text(stringResource(R.string.collection_clear_all)) },
        )
    }
}

@Composable
private fun PackRow(
    ownership: PackOwnership,
    onToggle: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(ownership.name) },
        supportingContent = {
            Text(
                text = buildString {
                    append(packTypeLabel(ownership.pack.type))
                    if (ownership.pack.known < ownership.pack.total) {
                        append(" · ")
                        append(
                            pluralStringResource(
                                R.plurals.collection_partial_pack,
                                ownership.pack.total,
                                ownership.pack.known,
                                ownership.pack.total,
                            ),
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Switch(checked = ownership.isOwned, onCheckedChange = onToggle)
        },
    )
}

@Composable
private fun packTypeLabel(type: String): String = when (PackType.fromName(type)) {
    PackType.CORE -> stringResource(R.string.pack_type_core)
    PackType.HERO_PACK -> stringResource(R.string.pack_type_hero)
    PackType.SCENARIO_PACK -> stringResource(R.string.pack_type_scenario)
    PackType.CAMPAIGN_BOX -> stringResource(R.string.pack_type_campaign_box)
    PackType.MODULAR_SET -> stringResource(R.string.pack_type_modular_set)
    PackType.UNKNOWN -> stringResource(R.string.pack_type_unknown)
}
