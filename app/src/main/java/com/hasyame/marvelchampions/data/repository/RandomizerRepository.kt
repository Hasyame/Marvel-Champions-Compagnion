package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.dao.RandomizerHistoryDao
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.seed.CardSeedSource
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.randomizer.Difficulty
import com.hasyame.marvelchampions.domain.randomizer.HeroAssignment
import com.hasyame.marvelchampions.domain.randomizer.HeroRef
import com.hasyame.marvelchampions.domain.randomizer.RandomizerDraw
import com.hasyame.marvelchampions.domain.randomizer.RandomizerPools
import com.hasyame.marvelchampions.domain.randomizer.ScenarioRule
import com.hasyame.marvelchampions.domain.randomizer.SetRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Display names for the codes a draw contains, in the user's card language. */
data class RandomizerNames(
    val scenarios: Map<String, String> = emptyMap(),
    val modularSets: Map<String, String> = emptyMap(),
    val heroes: Map<String, String> = emptyMap(),
)

@Singleton
class RandomizerRepository @Inject constructor(
    private val cardDao: CardDao,
    private val historyDao: RandomizerHistoryDao,
    private val collectionRepository: CollectionRepository,
    private val seed: CardSeedSource,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Builds the draw pools from the packs the user owns.
     *
     * Which scenarios and modular sets exist is derived from the card database
     * rather than curated — only the relationship between them needs a file.
     */
    suspend fun loadPools(locale: CardLocale): RandomizerPools = withContext(ioDispatcher) {
        val owned = collectionRepository.getOwnedCodes()
        val scenarios = cardDao.getPlayableScenarios(locale.code)
        val modulars = cardDao.getCardSets(MODULAR_SET, locale.code)
        val heroes = cardDao.getHeroes(locale.code)

        RandomizerPools(
            scenarios = scenarios.filter { it.packCode in owned }
                .map { SetRef(it.code, it.packCode) },
            modularSets = modulars.filter { it.packCode in owned }
                .map { SetRef(it.code, it.packCode) },
            heroes = heroes.filter { it.packCode in owned }
                .map { HeroRef(it.code, it.packCode) },
            // Aspects are the primary factions. 'basic' is not an aspect you
            // choose, and 'pool' is filtered per hero by the randomiser itself.
            aspects = ASPECTS,
        )
    }

    /**
     * Modular sets the user has told the collection they cannot field.
     *
     * A flow rather than a one-shot read: the collection screen is a tap away
     * from the draw, and coming back to a stale pool would be a bug the player
     * could see.
     */
    fun observeExcludedModularSets(): Flow<Set<String>> =
        collectionRepository.observeExcludedModularSets()

    suspend fun getExcludedModularSets(): Set<String> =
        collectionRepository.getExcludedModularSets()

    suspend fun loadRules(): Map<String, ScenarioRule> = withContext(ioDispatcher) {
        seed.readScenarioRules().scenarios.associate { dto ->
            dto.code to ScenarioRule(
                code = dto.code,
                packCode = dto.packCode,
                modularCount = dto.modularCount,
                mandatoryModulars = dto.mandatoryModulars,
                recommendedModulars = dto.recommendedModulars,
                needsReview = dto.needsReview,
            )
        }
    }

    suspend fun loadNames(locale: CardLocale): RandomizerNames = withContext(ioDispatcher) {
        RandomizerNames(
            scenarios = cardDao.getPlayableScenarios(locale.code)
                .mapNotNull { s -> s.name?.let { s.code to it } }.toMap(),
            modularSets = cardDao.getCardSets(MODULAR_SET, locale.code)
                .mapNotNull { s -> s.name?.let { s.code to it } }.toMap(),
            heroes = cardDao.getHeroes(locale.code)
                .mapNotNull { s -> s.name?.let { s.code to it } }.toMap(),
        )
    }

    fun observeHistory(): Flow<List<RandomizerHistoryEntity>> = historyDao.observeHistory()

    fun observeBeatenScenarios(): Flow<List<String>> = historyDao.observeBeatenScenarios()

    suspend fun save(draw: RandomizerDraw) {
        val scenario = draw.scenarioCode ?: return
        val difficulty = draw.difficulty ?: return
        historyDao.insert(
            RandomizerHistoryEntity(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                scenarioCode = scenario,
                difficulty = difficulty.name,
                playerCount = draw.playerCount,
                heroes = draw.heroes.joinToString(",") { "${it.heroCode}:${it.aspect}" },
                modularSetCodes = draw.modularSetCodes.joinToString(","),
            ),
        )
    }

    suspend fun setBeaten(id: String, beaten: Boolean) = historyDao.setBeaten(id, beaten)

    suspend fun deleteHistoryEntry(id: String) = historyDao.delete(id)

    companion object {
        private const val MODULAR_SET = "modular"

        /** Primary factions that are playable aspects. */
        val ASPECTS: List<String> =
            listOf("aggression", "justice", "leadership", "protection", "pool")

        /** Rebuilds the hero assignments stored in a history row. */
        fun parseHeroes(stored: String): List<HeroAssignment> = stored
            .split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { pair ->
                val parts = pair.split(':')
                if (parts.size == 2) HeroAssignment(parts[0], parts[1]) else null
            }

        fun parseDifficulty(stored: String): Difficulty? =
            Difficulty.entries.firstOrNull { it.name == stored }
    }
}
