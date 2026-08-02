package com.hasyame.marvelchampions.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.window.core.layout.WindowWidthSizeClass
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.ComicLoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    onCardClick: (String) -> Unit,
    viewModel: CardsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filtersOpen by remember { mutableStateOf(false) }

    // A 12-inch tablet gets list and detail side by side; a phone navigates to
    // the detail as its own screen.
    val isWide = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass ==
        WindowWidthSizeClass.EXPANDED

    // This was the one tab without a title bar, so its search field started
    // hard against the status bar while every other screen began below one.
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.destination_cards)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchBar(
                query = state.filter.query,
                activeFilterCount = state.filter.activeCount,
                onQueryChange = viewModel::onQueryChange,
                onFiltersClick = { filtersOpen = true },
            )

            Box(Modifier.fillMaxSize()) {
                when {
                    state.isDatabaseEmpty ->
                        EmptyMessage(stringResource(R.string.cards_database_empty))

                    state.isLoading && state.results.isEmpty() ->
                        ComicLoadingScreen(message = stringResource(R.string.cards_loading))

                    state.results.isEmpty() ->
                        EmptyMessage(stringResource(R.string.cards_no_results))

                    isWide -> Row(Modifier.fillMaxSize()) {
                        CardList(
                            state = state,
                            onCardClick = viewModel::onCardSelected,
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider()
                        Box(Modifier.weight(1.2f)) {
                            val selected = state.selectedCode
                            if (selected == null) {
                                EmptyMessage(stringResource(R.string.cards_select_a_card))
                            } else {
                                CardDetailPane(code = selected)
                            }
                        }
                    }

                    else -> CardList(
                        state = state,
                        onCardClick = onCardClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (filtersOpen) {
        CardFilterSheet(
            filter = state.filter,
            options = state.options,
            onFilterChange = viewModel::onFilterChange,
            onClear = viewModel::clearFilters,
            onDismiss = { filtersOpen = false },
        )
    }
}

@Composable
private fun CardList(
    state: CardsUiState,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(state.results, key = { it.code }) { card ->
            CardListItem(
                card = card,
                selected = card.code == state.selectedCode,
                onClick = { onCardClick(card.code) },
            )
        }
    }
}

/** The detail pane of the two-pane layout, with its own view model instance. */
@Composable
private fun CardDetailPane(code: String) {
    val viewModel: CardDetailViewModel = hiltViewModel(key = "detail-pane")
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(code) { viewModel.load(code) }

    val card = state.card
    if (card == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        CardDetailContent(
            card = card,
            pack = state.pack,
            linkedCard = state.linkedCard,
            locale = state.locale,
            onLocaleToggle = viewModel::toggleLocale,
            onLinkedCardClick = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    activeFilterCount: Int,
    onQueryChange: (String) -> Unit,
    onFiltersClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(stringResource(R.string.cards_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                        )
                    }
                }
            },
        )
        BadgedBox(
            badge = {
                if (activeFilterCount > 0) {
                    Badge { Text(activeFilterCount.toString()) }
                }
            },
        ) {
            IconButton(onClick = onFiltersClick) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.cards_filters),
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
