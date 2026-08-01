package com.hasyame.marvelchampions.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.hasyame.marvelchampions.ui.campaign.CampaignScreen
import com.hasyame.marvelchampions.ui.cards.CardDetailScreen
import com.hasyame.marvelchampions.ui.cards.CardsScreen
import com.hasyame.marvelchampions.ui.collection.CollectionScreen
import com.hasyame.marvelchampions.ui.decks.DeckDetailScreen
import com.hasyame.marvelchampions.ui.decks.DeckEditorScreen
import com.hasyame.marvelchampions.ui.decks.DecksScreen
import com.hasyame.marvelchampions.ui.decks.NewDeckScreen
import com.hasyame.marvelchampions.ui.randomizer.RandomizerScreen
import com.hasyame.marvelchampions.ui.settings.SettingsScreen

@Composable
fun MarvelChampionsNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    sharedLink: String? = null,
    onSharedLinkHandled: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        navigation<CardsGraph>(startDestination = CardsRoute) {
            composable<CardsRoute> {
                CardsScreen(
                    onCardClick = { code -> navController.navigate(CardDetailRoute(code)) },
                )
            }
            composable<CardDetailRoute> { entry ->
                CardDetailScreen(
                    code = entry.toRoute<CardDetailRoute>().code,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        navigation<DecksGraph>(startDestination = DecksRoute) {
            composable<DecksRoute> {
                DecksScreen(
                    onDeckClick = { deckId -> navController.navigate(DeckDetailRoute(deckId)) },
                    onBuildDeck = { navController.navigate(NewDeckRoute) },
                    sharedLink = sharedLink,
                    onSharedLinkHandled = onSharedLinkHandled,
                )
            }
            composable<DeckDetailRoute> { entry ->
                DeckDetailScreen(
                    deckId = entry.toRoute<DeckDetailRoute>().deckId,
                    onBack = { navController.popBackStack() },
                    onCardClick = { code -> navController.navigate(CardDetailRoute(code)) },
                    onEdit = { deckId -> navController.navigate(DeckEditorRoute(deckId)) },
                )
            }
            composable<NewDeckRoute> {
                NewDeckScreen(
                    onBack = { navController.popBackStack() },
                    onDeckCreated = { deckId ->
                        // Pop the picker so the back gesture from the editor
                        // returns to the deck list, not to hero selection.
                        navController.popBackStack()
                        navController.navigate(DeckEditorRoute(deckId))
                    },
                )
            }
            composable<DeckEditorRoute> { entry ->
                DeckEditorScreen(
                    deckId = entry.toRoute<DeckEditorRoute>().deckId,
                    onBack = { navController.popBackStack() },
                )
            }
            // A card opened from a deck belongs to the Decks back stack, so it
            // is registered here too rather than jumping the user to Cards.
            composable<CardDetailRoute> { entry ->
                CardDetailScreen(
                    code = entry.toRoute<CardDetailRoute>().code,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        navigation<CampaignGraph>(startDestination = CampaignRoute) {
            composable<CampaignRoute> { CampaignScreen() }
        }
        navigation<RandomizerGraph>(startDestination = RandomizerRoute) {
            composable<RandomizerRoute> { RandomizerScreen() }
        }
        navigation<SettingsGraph>(startDestination = SettingsRoute) {
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenCollection = { navController.navigate(CollectionRoute) },
                )
            }
            composable<CollectionRoute> {
                CollectionScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
