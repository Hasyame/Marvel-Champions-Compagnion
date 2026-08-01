package com.hasyame.marvelchampions.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hasyame.marvelchampions.ui.navigation.CardsGraph
import com.hasyame.marvelchampions.ui.navigation.CollectionRoute
import com.hasyame.marvelchampions.ui.navigation.MarvelChampionsNavHost
import com.hasyame.marvelchampions.ui.navigation.SettingsGraph
import com.hasyame.marvelchampions.ui.navigation.TopLevelDestination
import com.hasyame.marvelchampions.ui.navigation.isOn
import com.hasyame.marvelchampions.ui.navigation.navigateToTopLevelDestination

/**
 * [NavigationSuiteScaffold] picks the navigation container from the window size
 * class on its own: a bottom bar on a phone, a rail on the tablet.
 */
@Composable
fun MarvelChampionsApp(viewModel: AppStartViewModel = hiltViewModel()) {
    val startupState by viewModel.startupState.collectAsStateWithLifecycle()

    when (val startup = startupState) {
        StartupState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is StartupState.Ready -> AppContent(openCollectionFirst = startup.openCollectionFirst)
    }
}

@Composable
private fun AppContent(openCollectionFirst: Boolean) {
    val navController = rememberNavController()
    val currentDestination by navController.currentBackStackEntryAsState()

    // A fresh install goes straight to the collection. Navigating rather than
    // making it the start destination keeps Settings underneath it, so the back
    // gesture leads somewhere sensible instead of closing the app.
    LaunchedEffect(openCollectionFirst) {
        if (openCollectionFirst) {
            navController.navigate(CollectionRoute)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination?.destination.isOn(destination),
                    onClick = { navController.navigateToTopLevelDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            // The label sits right next to it, so the icon
                            // carries no extra information for a screen reader.
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        MarvelChampionsNavHost(
            navController = navController,
            startDestination = if (openCollectionFirst) SettingsGraph else CardsGraph,
        )
    }
}
