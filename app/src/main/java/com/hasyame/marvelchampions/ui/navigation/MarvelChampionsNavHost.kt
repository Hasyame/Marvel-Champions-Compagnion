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
import com.hasyame.marvelchampions.ui.decks.DecksScreen
import com.hasyame.marvelchampions.ui.randomizer.RandomizerScreen
import com.hasyame.marvelchampions.ui.settings.SettingsScreen

@Composable
fun MarvelChampionsNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
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
            composable<DecksRoute> { DecksScreen() }
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
