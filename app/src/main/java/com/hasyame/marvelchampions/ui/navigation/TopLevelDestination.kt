package com.hasyame.marvelchampions.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.hasyame.marvelchampions.R
import kotlin.reflect.KClass

/**
 * The five top level destinations, in the order they appear in the navigation bar
 * (or the navigation rail on a wide screen).
 *
 * Each one owns its own back stack: switching tabs saves the outgoing stack and
 * restores the incoming one, so leaving Cards for Settings and coming back lands
 * on the card that was open.
 */
enum class TopLevelDestination(
    val route: KClass<*>,
    val graphRoute: KClass<*>,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    CARDS(
        route = CardsRoute::class,
        graphRoute = CardsGraph::class,
        icon = Icons.Filled.Search,
        labelRes = R.string.destination_cards,
    ),
    DECKS(
        route = DecksRoute::class,
        graphRoute = DecksGraph::class,
        icon = Icons.AutoMirrored.Filled.List,
        labelRes = R.string.destination_decks,
    ),
    CAMPAIGN(
        route = CampaignRoute::class,
        graphRoute = CampaignGraph::class,
        icon = Icons.Filled.Star,
        labelRes = R.string.destination_campaign,
    ),
    RANDOMIZER(
        route = RandomizerRoute::class,
        graphRoute = RandomizerGraph::class,
        icon = Icons.Filled.Refresh,
        labelRes = R.string.destination_randomizer,
    ),
    SETTINGS(
        route = SettingsRoute::class,
        graphRoute = SettingsGraph::class,
        icon = Icons.Filled.Settings,
        labelRes = R.string.destination_settings,
    ),
}
