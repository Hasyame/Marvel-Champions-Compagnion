package com.hasyame.marvelchampions.domain.campaign.engine

import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.MarketEntry

/** Why a hero cannot buy a market card right now. */
sealed interface PurchaseRefusal {
    data object NotEnoughCredits : PurchaseRefusal

    /** Someone in the group already bought it. One copy per campaign, group-wide. */
    data class AlreadyOwnedByGroup(val heroId: String) : PurchaseRefusal

    data object NoMarket : PurchaseRefusal
    data object UnknownCard : PurchaseRefusal
}

data class MarketOffer(
    val entry: MarketEntry,
    val affordable: Boolean,
    val refusal: PurchaseRefusal?,
) {
    val canBuy: Boolean get() = refusal == null
}

/**
 * The market between scenarios.
 *
 * The rule that is easy to get wrong: **one copy of each card per campaign
 * across the whole group**, not per hero. Credits, by contrast, are per hero.
 */
object MarketRules {

    fun offersFor(
        template: CampaignTemplate,
        state: CampaignState,
        heroId: String,
    ): List<MarketOffer> {
        val market = template.market ?: return emptyList()
        val credits = state.heroCounter(market.counterId, heroId)
        val takenBy = state.purchases.associate { it.cardCode to it.heroId }

        return market.entries.map { entry ->
            val owner = takenBy[entry.cardCode]
            val refusal = when {
                owner != null -> PurchaseRefusal.AlreadyOwnedByGroup(owner)
                credits < entry.cost -> PurchaseRefusal.NotEnoughCredits
                else -> null
            }
            MarketOffer(
                entry = entry,
                affordable = credits >= entry.cost,
                refusal = refusal,
            )
        }
    }

    fun canPurchase(
        template: CampaignTemplate,
        state: CampaignState,
        heroId: String,
        cardCode: String,
    ): PurchaseRefusal? {
        val market = template.market ?: return PurchaseRefusal.NoMarket
        val entry = market.entries.firstOrNull { it.cardCode == cardCode }
            ?: return PurchaseRefusal.UnknownCard
        state.purchases.firstOrNull { it.cardCode == cardCode }?.let {
            return PurchaseRefusal.AlreadyOwnedByGroup(it.heroId)
        }
        if (state.heroCounter(market.counterId, heroId) < entry.cost) {
            return PurchaseRefusal.NotEnoughCredits
        }
        return null
    }
}
