package com.hasyame.marvelchampions.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.repository.DeckRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    deckId: String,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: DeckDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(deckId) { viewModel.load(deckId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.contents?.deck?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.decks_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val contents = state.contents
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            contents == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.decks_not_found)) }

            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (state.isRefreshing) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                state.error?.let { error ->
                    item {
                        Text(
                            text = importErrorMessage(error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                item {
                    ListItem(
                        overlineContent = { Text(stringResource(R.string.decks_hero)) },
                        headlineContent = { Text(contents.deck.heroName) },
                        supportingContent = {
                            Text(
                                DeckRepository.parseAspects(contents.deck.aspects)
                                    .joinToString(" / ") { it.replaceFirstChar(Char::uppercase) },
                            )
                        },
                        modifier = Modifier.clickable { onCardClick(contents.deck.heroCode) },
                    )
                    HorizontalDivider()
                    Text(
                        text = pluralStringResource(
                            R.plurals.decks_card_count,
                            contents.totalCards,
                            contents.totalCards,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                    if (contents.missingCards.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.decks_missing_count,
                                contents.missingCards.size,
                                contents.missingCards.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (contents.unknownCardCodes.isNotEmpty()) {
                        // Happens when the deck uses a pack MarvelCDB has added
                        // since the last card sync.
                        Text(
                            text = pluralStringResource(
                                R.plurals.decks_unknown_cards,
                                contents.unknownCardCodes.size,
                                contents.unknownCardCodes.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                contents.cardsByType.forEach { (typeName, cards) ->
                    item(key = "type-$typeName") {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    items(cards, key = { it.card.code }) { deckCard ->
                        ListItem(
                            modifier = Modifier.clickable { onCardClick(deckCard.card.code) },
                            headlineContent = {
                                Text("${deckCard.quantity}× ${deckCard.card.name}")
                            },
                            supportingContent = {
                                Text(
                                    text = if (deckCard.missingFromCollection) {
                                        stringResource(
                                            R.string.decks_card_missing,
                                            deckCard.card.packCode.uppercase(),
                                        )
                                    } else {
                                        deckCard.card.packCode.uppercase()
                                    },
                                    color = if (deckCard.missingFromCollection) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
