package com.hasyame.marvelchampions.domain.campaign.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A campaign, entirely as data.
 *
 * Nothing in this file is specific to any published campaign. Adding one is a
 * JSON file; if you find yourself editing Kotlin to support a campaign, the
 * schema is missing something and that is the thing to fix.
 *
 * Template files are **not** shipped in the APK: they contain verbatim campaign
 * book text, so they are imported from device storage at runtime. See the Legal
 * section of the README.
 */
@Serializable
data class CampaignTemplate(
    val id: String,
    val schemaVersion: Int,
    val name: LocalizedText,
    /** MarvelCDB pack code, so a campaign is only offered if the box is owned. */
    val packCode: String? = null,
    val difficulties: List<String> = listOf("standard", "expert"),
    val counters: List<CounterDefinition> = emptyList(),
    val flagSets: List<FlagSetDefinition> = emptyList(),
    val cardLists: List<CardListDefinition> = emptyList(),
    val market: MarketDefinition? = null,
    val scenarios: List<ScenarioTemplate> = emptyList(),
    /** Scenario id the campaign starts on. Defaults to the first. */
    val startScenarioId: String? = null,
)

/**
 * Content is French first. English can be added later, and a missing string
 * falls back rather than showing a blank.
 */
@Serializable
data class LocalizedText(
    val fr: String? = null,
    val en: String? = null,
) {
    fun resolve(preferred: String): String =
        when (preferred) {
            "en" -> en ?: fr
            else -> fr ?: en
        } ?: ""
}

enum class CounterScope { CAMPAIGN, HERO }

@Serializable
data class CounterDefinition(
    val id: String,
    /** `campaign` or `hero`. Credits belong to each player, not to the group. */
    val scope: String = "campaign",
    val initial: Int = 0,
    val min: Int? = 0,
    val max: Int? = null,
    /**
     * Caps the counter at a value read from the card database rather than
     * stored here. Only `heroCard.health` is understood.
     */
    val maxFrom: String? = null,
    val activeWhen: Condition? = null,
) {
    val counterScope: CounterScope
        get() = if (scope.equals("hero", ignoreCase = true)) CounterScope.HERO else CounterScope.CAMPAIGN
}

@Serializable
data class FlagSetDefinition(
    val id: String,
    /** `perScenario` means one flag per scenario, which conditions can count. */
    val scope: String = "campaign",
)

@Serializable
data class CardListDefinition(
    val id: String,
    /** `campaign`, `hero` or `perScenario`. */
    val scope: String = "campaign",
)

@Serializable
data class MarketDefinition(
    /** Counter spent when buying. Per hero. */
    val counterId: String = "credits",
    val entries: List<MarketEntry> = emptyList(),
)

@Serializable
data class MarketEntry(
    val cardCode: String,
    val cost: Int,
    /** Card list the purchase is added to. */
    val cardListId: String = "purchases",
)

@Serializable
data class ScenarioTemplate(
    val id: String,
    val name: LocalizedText? = null,
    val flavour: LocalizedText? = null,
    val baseSetup: BaseSetup? = null,
    val campaignSetup: List<SetupStep> = emptyList(),
    val onVictory: Outcome? = null,
    val onDefeat: Outcome? = null,
    /**
     * Bespoke mechanics that the declarative schema genuinely cannot express,
     * resolved against a registry of Kotlin handlers. A last resort: if the same
     * shape appears twice it belongs in the schema instead.
     */
    val handlerId: String? = null,
)

@Serializable
data class BaseSetup(
    /** Villain stages per difficulty, e.g. `standard` to `["drang_1","drang_2"]`. */
    val villainDeck: Map<String, List<String>> = emptyMap(),
    val mainScheme: List<String> = emptyList(),
    val encounterSets: List<String> = emptyList(),
    val modularSets: List<String> = emptyList(),
)

/**
 * One instruction of the campaign-specific setup.
 *
 * With [action] set it is a button rather than a bullet: something the player
 * may choose to do that changes state, such as spending a credit to heal.
 */
@Serializable
data class SetupStep(
    val text: LocalizedText,
    @SerialName("when") val condition: Condition? = null,
    val action: SetupAction? = null,
)

@Serializable
data class SetupAction(
    val id: String,
    val label: LocalizedText,
    /** Only offered when this holds. */
    val enabledWhen: Condition? = null,
    /** Per-hero cost paid from a counter. */
    val cost: ActionCost? = null,
    val effects: List<Effect> = emptyList(),
    /** True when each hero may take it independently. */
    val perHero: Boolean = false,
    /** True when it may be taken more than once. */
    val repeatable: Boolean = false,
)

@Serializable
data class ActionCost(
    val counterId: String,
    val amount: Int,
)

@Serializable
data class Outcome(
    val prompts: List<Prompt> = emptyList(),
    val effects: List<Effect> = emptyList(),
    /** Guarded and evaluated in order; the first match wins. */
    val next: List<NextStep> = emptyList(),
)

@Serializable
data class NextStep(
    val goto: String? = null,
    @SerialName("when") val condition: Condition? = null,
    /** Ends the campaign rather than moving on. */
    val end: Boolean = false,
)

enum class PromptType {
    NUMBER,
    BOOLEAN,
    PER_HERO_NUMBER,
    PER_HERO_BOOLEAN,
    CARD_LIST,
    CHOICE,
    UNKNOWN,
}

@Serializable
data class Prompt(
    val id: String,
    val type: String,
    val label: LocalizedText? = null,
    @SerialName("when") val condition: Condition? = null,
    val min: Int? = null,
    val max: Int? = null,
    /** For `choice`. */
    val options: List<PromptOption> = emptyList(),
) {
    val promptType: PromptType
        get() = when (type.lowercase().replace("_", "")) {
            "number" -> PromptType.NUMBER
            "boolean" -> PromptType.BOOLEAN
            "perheronumber" -> PromptType.PER_HERO_NUMBER
            "perheroboolean" -> PromptType.PER_HERO_BOOLEAN
            "cardlist" -> PromptType.CARD_LIST
            "choice" -> PromptType.CHOICE
            else -> PromptType.UNKNOWN
        }

    val isPerHero: Boolean
        get() = promptType == PromptType.PER_HERO_NUMBER ||
            promptType == PromptType.PER_HERO_BOOLEAN
}

@Serializable
data class PromptOption(
    val id: String,
    val label: LocalizedText? = null,
)
