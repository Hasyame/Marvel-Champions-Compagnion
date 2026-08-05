package com.hasyame.marvelchampions.domain.randomizer

import kotlin.random.Random

/**
 * Draws a scenario setup from the packs the user owns.
 *
 * Pure and deterministic given a [Random], so every rule below is testable
 * without a device.
 *
 * The locking model is the whole point: [draw] keeps the value of every field
 * in `locked` and rerolls the rest, so "reroll just the hero" is a call with
 * every other field locked.
 */
object ScenarioRandomizer {

    /** Deadpool's aspect is playable only by Deadpool. */
    const val POOL_ASPECT: String = "pool"
    private const val DEADPOOL_HERO_CODE = "deadpool"

    fun draw(
        pools: RandomizerPools,
        rules: Map<String, ScenarioRule>,
        filters: RandomizerFilters = RandomizerFilters(),
        previous: RandomizerDraw = RandomizerDraw(),
        locked: Set<DrawField> = emptySet(),
        random: Random = Random.Default,
    ): RandomizerDraw {
        val scenarioCode = if (DrawField.SCENARIO in locked) {
            previous.scenarioCode
        } else {
            pools.scenarios
                .filter { it.code !in filters.excludedScenarios }
                // A scenario that demands a set the player cannot field is not
                // playable, so it is not offered. Drawing it and flagging the
                // gap would only make them roll again by hand.
                .filter { scenario ->
                    rules[scenario.code]?.mandatoryModulars.orEmpty()
                        .none { it in filters.excludedModularSets }
                }
                .randomOrNull(random)
                ?.code
        }

        val difficulty = if (DrawField.DIFFICULTY in locked) {
            previous.difficulty
        } else {
            Difficulty.entries
                .filter { it in filters.allowedDifficulties }
                .randomOrNull(random)
        }

        val playerCount = if (DrawField.PLAYER_COUNT in locked) {
            previous.playerCount
        } else {
            val low = filters.minPlayers.coerceAtLeast(1)
            val high = filters.maxPlayers.coerceAtLeast(low)
            random.nextInt(low, high + 1)
        }

        val rule = scenarioCode?.let { rules[it] }
        val mandatory = rule?.mandatoryModulars.orEmpty()
            // A mandatory set from a pack the user does not own cannot be
            // played, so it is dropped rather than silently pretended.
            .filter { code -> pools.modularSets.any { it.code == code } }

        val modularSetCodes = if (DrawField.MODULAR_SETS in locked && previous.modularSetCodes.isNotEmpty()) {
            previous.modularSetCodes
        } else {
            drawModularSets(
                pools.copy(
                    modularSets = pools.modularSets.filter {
                        it.code !in filters.excludedModularSets
                    },
                ),
                rule,
                mandatory,
                random,
            )
        }

        val heroes = if (DrawField.HEROES in locked && DrawField.ASPECTS in locked) {
            previous.heroes.take(playerCount)
        } else {
            drawHeroes(
                pools = pools,
                filters = filters,
                playerCount = playerCount,
                previous = previous,
                locked = locked,
                random = random,
            )
        }

        return RandomizerDraw(
            scenarioCode = scenarioCode,
            difficulty = difficulty,
            modularSetCodes = modularSetCodes,
            playerCount = playerCount,
            heroes = heroes,
            mandatoryModularCodes = mandatory,
        )
    }

    private fun drawModularSets(
        pools: RandomizerPools,
        rule: ScenarioRule?,
        mandatory: List<String>,
        random: Random,
    ): List<String> {
        if (rule == null) {
            return emptyList()
        }
        val chosen = mandatory.toMutableList()
        // Mandatory sets already count towards the scenario's total, so only
        // the shortfall is drawn at random.
        val remaining = (rule.modularCount - chosen.size).coerceAtLeast(0)
        if (remaining == 0) {
            return chosen
        }
        val available = pools.modularSets
            .map { it.code }
            .filter { it !in chosen }
            .toMutableList()
        repeat(remaining) {
            if (available.isEmpty()) {
                return chosen
            }
            chosen += available.removeAt(random.nextInt(available.size))
        }
        return chosen
    }

    private fun drawHeroes(
        pools: RandomizerPools,
        filters: RandomizerFilters,
        playerCount: Int,
        previous: RandomizerDraw,
        locked: Set<DrawField>,
        random: Random,
    ): List<HeroAssignment> {
        val heroesLocked = DrawField.HEROES in locked
        val aspectsLocked = DrawField.ASPECTS in locked

        val availableHeroes = pools.heroes
            .map { it.code }
            .filter { it !in filters.excludedHeroes }
            .toMutableList()

        val assignments = mutableListOf<HeroAssignment>()
        for (index in 0 until playerCount) {
            val previousAssignment = previous.heroes.getOrNull(index)

            val heroCode = if (heroesLocked && previousAssignment != null) {
                previousAssignment.heroCode
            } else {
                if (availableHeroes.isEmpty()) {
                    return assignments
                }
                availableHeroes.removeAt(random.nextInt(availableHeroes.size))
            }
            // A hero picked for one player must not turn up again for another.
            availableHeroes.remove(heroCode)

            val aspect = if (aspectsLocked && previousAssignment != null) {
                previousAssignment.aspect
            } else {
                pickAspect(pools, filters, heroCode, random)
            } ?: continue

            assignments += HeroAssignment(heroCode = heroCode, aspect = aspect)
        }
        return assignments
    }

    private fun pickAspect(
        pools: RandomizerPools,
        filters: RandomizerFilters,
        heroCode: String,
        random: Random,
    ): String? = pools.aspects
        .filter { it !in filters.excludedAspects }
        .filter { aspect ->
            // 'Pool cards are Deadpool-only, so the aspect is offered to nobody
            // else. Without this the randomiser produces illegal decks.
            aspect != POOL_ASPECT || heroCode == DEADPOOL_HERO_CODE
        }
        .randomOrNull(random)

    private fun <T> List<T>.randomOrNull(random: Random): T? =
        if (isEmpty()) null else this[random.nextInt(size)]
}
