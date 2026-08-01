package com.hasyame.marvelchampions.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.deckbuilder.HeroDeckRulesParser
import com.hasyame.marvelchampions.data.deckbuilder.toDeckCardInfo
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidation
import com.hasyame.marvelchampions.domain.deckbuilder.DeckValidator
import com.hasyame.marvelchampions.domain.deckbuilder.HeroDeckRules
import com.hasyame.marvelchampions.domain.model.CardFilter
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.search.CardQueryBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A hero that can be picked when starting a deck. */
data class HeroChoice(
    val card: CardEntity,
    val owned: Boolean,
)

@Singleton
class DeckBuilderRepository @Inject constructor(
    private val cardDao: CardDao,
    private val collectionRepository: CollectionRepository,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun heroes(locale: CardLocale): List<HeroChoice> = withContext(ioDispatcher) {
        val owned = collectionRepository.getOwnedCodes()
        cardDao.getHeroes(locale.code).mapNotNull { summary ->
            // getHeroes returns one row per hero set; the identity card itself
            // is what carries the deck building rules.
            val card = cardDao.getCard(summary.code, locale.code)
                ?: findHeroCard(summary.code, locale)
            card?.let { HeroChoice(card = it, owned = it.packCode in owned) }
        }.sortedWith(compareByDescending<HeroChoice> { it.owned }.thenBy { it.card.name })
    }

    suspend fun heroRules(heroCode: String, locale: CardLocale): HeroDeckRules? =
        withContext(ioDispatcher) {
            val hero = cardDao.getCard(heroCode, locale.code) ?: return@withContext null
            HeroDeckRulesParser.parse(hero, json)
        }

    /**
     * Cards that can go in a deck: the hero's own signature cards, the chosen
     * aspects, and basic. Encounter and campaign cards are never player cards.
     */
    suspend fun candidateCards(
        heroSetCode: String?,
        aspects: List<String>,
        locale: CardLocale,
        query: String,
        ownedOnly: Boolean,
    ): List<CardEntity> = withContext(ioDispatcher) {
        val factions = (aspects + BASIC_FACTION).toSet()
        val filter = CardFilter(
            query = query,
            factionCodes = factions,
            ownedOnly = ownedOnly,
        )
        val owned = if (ownedOnly) collectionRepository.getOwnedCodes() else emptySet()
        val built = CardQueryBuilder.build(filter, locale, owned, limit = CANDIDATE_LIMIT)
        val aspectCards = cardDao.queryCards(
            SimpleSQLiteQuery(built.sql, built.args.toTypedArray()),
        )

        val heroCards = heroSetCode?.let { setCode ->
            cardDao.getCardSet(setCode, locale.code)
                .filter { it.factionCode == HERO_FACTION && it.typeCode != HERO_TYPE }
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        }.orEmpty()

        (heroCards + aspectCards).distinctBy { it.code }
    }

    suspend fun validate(
        rules: HeroDeckRules,
        aspects: List<String>,
        slots: Map<String, Int>,
        locale: CardLocale,
    ): DeckValidation = withContext(ioDispatcher) {
        val cards = slots.keys.mapNotNull { cardDao.getCard(it, locale.code) }
            .associate { it.code to it.toDeckCardInfo() }
        DeckValidator.validate(rules, aspects, slots, cards)
    }

    private suspend fun findHeroCard(setCode: String, locale: CardLocale): CardEntity? =
        cardDao.getCardSet(setCode, locale.code).firstOrNull { it.typeCode == HERO_TYPE }

    private companion object {
        const val BASIC_FACTION = "basic"
        const val HERO_FACTION = "hero"
        const val HERO_TYPE = "hero"
        const val CANDIDATE_LIMIT = 400
    }
}
