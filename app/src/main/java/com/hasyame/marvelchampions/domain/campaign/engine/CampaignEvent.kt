package com.hasyame.marvelchampions.domain.campaign.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything that happens in a campaign run, as an append-only log.
 *
 * All state is derived by folding this list, which buys three things at once:
 * undo and history come free, a template correction can be replayed over an
 * existing run, and merging two devices is merging two lists by [id].
 *
 * Ids are stable and generated once, so the merge is idempotent.
 */
@Serializable
sealed interface CampaignEvent {
    val id: String
    val timestamp: Long

    @Serializable
    @SerialName("setup")
    data class CampaignStarted(
        override val id: String,
        override val timestamp: Long,
        val templateId: String,
        val difficulty: String,
        /** Deck ids attached as the hero roster, fixed for the whole campaign. */
        val heroes: List<CampaignHero>,
        val startScenarioId: String,
    ) : CampaignEvent

    /** A scenario result with the raw answers that produced it. */
    @Serializable
    @SerialName("scenario_result")
    data class ScenarioCompleted(
        override val id: String,
        override val timestamp: Long,
        val scenarioId: String,
        val victory: Boolean,
        /** Raw questionnaire answers, kept so a template fix can be replayed. */
        val answers: AnswerSet = AnswerSet(),
        val elapsedMillis: Long = 0,
    ) : CampaignEvent

    @Serializable
    @SerialName("purchase")
    data class MarketPurchase(
        override val id: String,
        override val timestamp: Long,
        val heroId: String,
        val cardCode: String,
        val cost: Int,
        val cardListId: String,
    ) : CampaignEvent

    /** Undoes a purchase, so market history stays visible and reversible. */
    @Serializable
    @SerialName("purchase_refund")
    data class MarketRefund(
        override val id: String,
        override val timestamp: Long,
        val purchaseEventId: String,
    ) : CampaignEvent

    /** A setup step the player chose to take. */
    @Serializable
    @SerialName("setup_action")
    data class SetupActionTaken(
        override val id: String,
        override val timestamp: Long,
        val scenarioId: String,
        val actionId: String,
        val heroId: String? = null,
    ) : CampaignEvent

    /**
     * A card the app drew at random for a scenario's setup.
     *
     * An event rather than a value computed when the screen draws itself: a
     * random pick made during rendering would come out differently on every
     * recomposition, so the mission would change while the player was reading it.
     */
    @Serializable
    @SerialName("setup_draw")
    data class SetupDrawn(
        override val id: String,
        override val timestamp: Long,
        val scenarioId: String,
        val drawId: String,
        /** In the order drawn, which is the order they are set out in. */
        val cardCodes: List<String>,
    ) : CampaignEvent

    /**
     * Which of the offered cards the players kept.
     *
     * Recorded rather than derived: the ones not kept go back into the pool, so
     * nothing else in the state says which was chosen.
     */
    @Serializable
    @SerialName("setup_choice")
    data class SetupChoiceMade(
        override val id: String,
        override val timestamp: Long,
        val scenarioId: String,
        val drawId: String,
        val cardCode: String,
    ) : CampaignEvent

    /** The scenario the players chose to play next. */
    @Serializable
    @SerialName("scenario_chosen")
    data class ScenarioChosen(
        override val id: String,
        override val timestamp: Long,
        val scenarioId: String,
    ) : CampaignEvent

    /** Any hand adjustment. Logged as such so it never looks like a rules result. */
    @Serializable
    @SerialName("manual")
    data class ManualAdjustment(
        override val id: String,
        override val timestamp: Long,
        val counterId: String? = null,
        val flagId: String? = null,
        val heroId: String? = null,
        val value: Int? = null,
        val boolValue: Boolean? = null,
        val note: String? = null,
    ) : CampaignEvent

    /**
     * Marks an earlier [ScenarioCompleted] as superseded. The original stays in
     * the log; the fold ignores it.
     */
    @Serializable
    @SerialName("revoke")
    data class EventRevoked(
        override val id: String,
        override val timestamp: Long,
        val revokedEventId: String,
        val note: String? = null,
    ) : CampaignEvent

    @Serializable
    @SerialName("timer")
    data class TimeRecorded(
        override val id: String,
        override val timestamp: Long,
        val scenarioId: String,
        val elapsedMillis: Long,
    ) : CampaignEvent
}

@Serializable
data class CampaignHero(
    /** Stable id within the run; the deck id it was created from. */
    val id: String,
    val deckId: String?,
    val heroCardCode: String,
    val name: String,
)

/**
 * Questionnaire answers.
 *
 * Stored raw and separately from the effects they produced, so correcting a
 * template and replaying is possible without asking the player again.
 */
@Serializable
data class AnswerSet(
    val numbers: Map<String, Int> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val choices: Map<String, String> = emptyMap(),
    val cardLists: Map<String, List<String>> = emptyMap(),
    /** Prompt id to hero id to value. */
    val perHeroNumbers: Map<String, Map<String, Int>> = emptyMap(),
    val perHeroBooleans: Map<String, Map<String, Boolean>> = emptyMap(),
)
