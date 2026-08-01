package com.hasyame.marvelchampions.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hasyame.marvelchampions.ui.navigation.MarvelChampionsNavHost
import com.hasyame.marvelchampions.ui.navigation.TopLevelDestination
import com.hasyame.marvelchampions.ui.navigation.isOn
import com.hasyame.marvelchampions.ui.navigation.navigateToTopLevelDestination

/**
 * [NavigationSuiteScaffold] picks the navigation container from the window size
 * class on its own: a bottom bar on a phone, a rail on the tablet.
 */
@Composable
fun MarvelChampionsApp() {
    val navController = rememberNavController()
    val currentDestination by navController.currentBackStackEntryAsState()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination?.destination.isOn(destination),
                    onClick = { navController.navigateToTopLevelDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            // The label is right there next to it, so the icon
                            // itself carries no extra information for a screen reader.
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        MarvelChampionsNavHost(navController = navController)
    }
}
