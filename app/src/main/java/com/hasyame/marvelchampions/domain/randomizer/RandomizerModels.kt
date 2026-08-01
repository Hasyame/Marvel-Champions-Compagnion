package com.hasyame.marvelchampions.domain.randomizer

/** The four difficulty levels the game offers. */
enum class Difficulty {
    STANDARD_I,
    STANDARD_II,
    EXPERT_I,
    EXPERT_II,
}

/** A field of the draw. Each one can be locked and rerolled on its own. */
enum class DrawField {
    SCENARIO,
    DIFFICULTY,
    MODULAR_SETS,
    PLAYER_COUNT,
    HEROES,
    ASPECTS,
}

/** A card set the randomiser can pick, named by the card database at runtime. */
data class SetRef(
    val code: String,
    val packCode: String,
)

data class HeroRef(
    val code: String,
    val packCode: String,
)

/**
 * What a scenario requires. Generated into `assets/scenario_rules.json` by
 * `tools/generate-scenario-rules.mjs`.
 *
 * [mandatoryModulars] must always be used. [recommendedModulars] are only a
 * printed suggestion, so they stay in the random pool rather than being forced.
 */
data class ScenarioRule(
    val code: String,
    val packCode: String,
    val modularCount: Int,
    val mandatoryModulars: List<String> = emptyList(),
    val recommendedModulars: List<String> = emptyList(),
    /** The generator could not parse this scenario with confidence. */
    val needsReview: Boolean = false,
)

/** Everything the user owns, already filtered to owned packs. */
data class RandomizerPools(
    val scenarios: List<SetRef> = emptyList(),
    val modularSets: List<SetRef> = emptyList(),
    val heroes: List<HeroRef> = emptyList(),
    val aspects: List<String> = emptyList(),
)

data class RandomizerFilters(
    val excludedScenarios: Set<String> = emptySet(),
    val excludedHeroes: Set<String> = emptySet(),
    val excludedAspects: Set<String> = emptySet(),
    val allowedDifficulties: Set<Difficulty> = Difficulty.entries.toSet(),
    val minPlayers: Int = 1,
    val maxPlayers: Int = 4,
)

/** One hero with the aspect they are playing. */
data class HeroAssignment(
    val heroCode: String,
    val aspect: String,
)

/**
 * A complete draw. Fields are nullable because a pool can be empty — an
 * unowned collection must produce a partial result the UI can explain, not a
 * crash.
 */
data class RandomizerDraw(
    val scenarioCode: String? = null,
    val difficulty: Difficulty? = null,
    val modularSetCodes: List<String> = emptyList(),
    val playerCount: Int = 1,
    val heroes: List<HeroAssignment> = emptyList(),
    /** Modular sets forced by the scenario, a subset of [modularSetCodes]. */
    val mandatoryModularCodes: List<String> = emptyList(),
) {
    val isComplete: Boolean
        get() = scenarioCode != null && difficulty != null && heroes.isNotEmpty()
}
