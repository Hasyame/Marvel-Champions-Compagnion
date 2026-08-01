package com.hasyame.marvelchampions.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type safe Navigation Compose routes.
 *
 * Every tab is a nested graph ([CardsGraph] and friends) wrapping a start
 * destination ([CardsRoute] and friends). The extra level of nesting is what
 * gives each tab an independent back stack.
 */

@Serializable
data object CardsGraph

@Serializable
data object CardsRoute

/** A single card. Reached from the card list on a narrow screen. */
@Serializable
data class CardDetailRoute(val code: String)

@Serializable
data object DecksGraph

@Serializable
data object DecksRoute

@Serializable
data object CampaignGraph

@Serializable
data object CampaignRoute

@Serializable
data object RandomizerGraph

@Serializable
data object RandomizerRoute

@Serializable
data object SettingsGraph

@Serializable
data object SettingsRoute

/**
 * The collection. A full screen of its own rather than a section inside the
 * settings list, because it is the source of truth for the randomiser and for
 * deck legality.
 */
@Serializable
data object CollectionRoute
