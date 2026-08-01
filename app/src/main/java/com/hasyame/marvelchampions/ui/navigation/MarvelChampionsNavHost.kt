package com.hasyame.marvelchampions.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.hasyame.marvelchampions.ui.campaign.CampaignScreen
import com.hasyame.marvelchampions.ui.cards.CardsScreen
import com.hasyame.marvelchampions.ui.decks.DecksScreen
import com.hasyame.marvelchampions.ui.randomizer.RandomizerScreen
import com.hasyame.marvelchampions.ui.settings.SettingsScreen

@Composable
fun MarvelChampionsNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = CardsGraph,
        modifier = modifier,
    ) {
        navigation<CardsGraph>(startDestination = CardsRoute) {
            composable<CardsRoute> { CardsScreen() }
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
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}
