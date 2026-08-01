package com.hasyame.marvelchampions.domain.campaign.engine

import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.CounterScope
import com.hasyame.marvelchampions.domain.campaign.template.Effect
import com.hasyame.marvelchampions.domain.campaign.template.EffectOp
import com.hasyame.marvelchampions.domain.campaign.template.Outcome
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate

/**
 * Extension point for the genuinely bespoke.
 *
 * A scenario names a handler id and the engine looks it up here. This is a last
 * resort — if two scenarios need the same shape, that shape belongs in the
 * declarative schema instead.
 */
interface ScenarioHandler {
    val id: String

    /** Runs after the template's own victory effects. */
    fun onVictory(state: CampaignState, scenario: ScenarioTemplate, answers: AnswerSet): CampaignState =
        state

    fun onDefeat(state: CampaignState, scenario: ScenarioTemplate, answers: AnswerSet): CampaignState =
        state
}

/**
 * Derives campaign state from the event log.
 *
 * Pure: same template, same events, same hero stats, same state. That is what
 * makes undo, replay-after-template-fix and two-device merging tractable.
 */
class CampaignEngine(
    private val handlers: Map<String, ScenarioHandler> = emptyMap(),
) {

    fun fold(
        template: CampaignTemplate,
        events: List<CampaignEvent>,
        heroStats: Map<String, HeroCardStats> = emptyMap(),
    ): CampaignState {
        // Revocations are applied first so a superseded result never takes
        // effect, however late the revocation was appended.
        val revoked = events.filterIsInstance<CampaignEvent.EventRevoked>()
            .map { it.revokedEventId }
            .toSet()
        val refunded = events.filterIsInstance<CampaignEvent.MarketRefund>()
            .map { it.purchaseEventId }
            .toSet()

        var state = CampaignState(templateId = template.id)

        for (event in events.sortedBy { it.timestamp }) {
            if (event.id in revoked) {
                continue
            }
            state = when (event) {
                is CampaignEvent.CampaignStarted -> applyStart(template, event)
                is CampaignEvent.ScenarioCompleted -> applyScenario(template, state, event, heroStats)
                is CampaignEvent.MarketPurchase ->
                    if (event.id in refunded) state else applyPurchase(state, event)

                is CampaignEvent.SetupActionTaken -> applySetupAction(template, state, event, heroStats)
                is CampaignEvent.ManualAdjustment -> applyManual(state, event)
                is CampaignEvent.TimeRecorded ->
                    state.copy(totalPlayTimeMillis = state.totalPlayTimeMillis + event.elapsedMillis)

                is CampaignEvent.EventRevoked, is CampaignEvent.MarketRefund -> state
            }
        }
        return state
    }

    private fun applyStart(
        template: CampaignTemplate,
        event: CampaignEvent.CampaignStarted,
    ): CampaignState {
        var state = CampaignState(
            templateId = template.id,
            difficulty = event.difficulty,
            heroes = event.heroes,
            started = true,
            currentScenarioId = event.startScenarioId,
        )

        for (counter in template.counters) {
            state = when (counter.counterScope) {
                CounterScope.CAMPAIGN ->
                    state.copy(counters = state.counters + (counter.id to counter.initial))

                CounterScope.HERO -> state.copy(
                    heroCounters = state.heroCounters + (
                        counter.id to event.heroes.associate { it.id to counter.initial }
                        ),
                )
            }
        }
        return state
    }

    private fun applyScenario(
        template: CampaignTemplate,
        state: CampaignState,
        event: CampaignEvent.ScenarioCompleted,
        heroStats: Map<String, HeroCardStats>,
    ): CampaignState {
        val scenario = template.scenarios.firstOrNull { it.id == event.scenarioId }
            ?: return state
        val outcome = if (event.victory) scenario.onVictory else scenario.onDefeat

        var next = state.copy(
            eliminatedInScenario = state.eliminatedInScenario + (
                event.scenarioId to eliminatedHeroes(event.answers)
                ),
        )

        if (outcome != null) {
            next = applyEffects(
                template = template,
                state = next,
                effects = outcome.effects,
                scenarioId = event.scenarioId,
                answers = event.answers,
                heroStats = heroStats,
            )
        }

        scenario.handlerId?.let { handlerId ->
            handlers[handlerId]?.let { handler ->
                next = if (event.victory) {
                    handler.onVictory(next, scenario, event.answers)
                } else {
                    handler.onDefeat(next, scenario, event.answers)
                }
            }
        }

        val advanced = resolveNext(outcome, next, event.scenarioId, event.answers)

        return next.copy(
            completedScenarios = next.completedScenarios + ScenarioResult(
                eventId = event.id,
                scenarioId = event.scenarioId,
                victory = event.victory,
                answers = event.answers,
                elapsedMillis = event.elapsedMillis,
                timestamp = event.timestamp,
            ),
            totalPlayTimeMillis = next.totalPlayTimeMillis + event.elapsedMillis,
            currentScenarioId = advanced.scenarioId,
            finished = advanced.finished,
        )
    }

    private data class Advance(val scenarioId: String?, val finished: Boolean)

    /**
     * `next` is a guarded list evaluated in order, so a branch is data. The
     * engine never assumes the next scenario in the array.
     */
    private fun resolveNext(
        outcome: Outcome?,
        state: CampaignState,
        scenarioId: String,
        answers: AnswerSet,
    ): Advance {
        val context = EvaluationContext(state = state, scenarioId = scenarioId, answers = answers)
        val step = outcome?.next?.firstOrNull { ConditionEvaluator.evaluate(it.condition, context) }
            ?: return Advance(scenarioId, finished = false)
        return when {
            step.end -> Advance(null, finished = true)
            step.goto != null -> Advance(step.goto, finished = false)
            else -> Advance(scenarioId, finished = false)
        }
    }

    private fun eliminatedHeroes(answers: AnswerSet): Set<String> =
        answers.perHeroBooleans[ELIMINATED_PROMPT_ID]
            ?.filterValues { it }
            ?.keys
            .orEmpty()

    fun applyEffects(
        template: CampaignTemplate,
        state: CampaignState,
        effects: List<Effect>,
        scenarioId: String?,
        answers: AnswerSet,
        heroStats: Map<String, HeroCardStats>,
        actingHeroId: String? = null,
    ): CampaignState {
        var current = state
        for (effect in effects) {
            current = applyEffect(
                template, current, effect, scenarioId, answers, heroStats, actingHeroId,
            )
        }
        return current
    }

    private fun applyEffect(
        template: CampaignTemplate,
        state: CampaignState,
        effect: Effect,
        scenarioId: String?,
        answers: AnswerSet,
        heroStats: Map<String, HeroCardStats>,
        actingHeroId: String?,
    ): CampaignState {
        val baseContext = EvaluationContext(
            state = state,
            scenarioId = scenarioId,
            answers = answers,
            heroId = actingHeroId,
        )

        return when (effect.operation) {
            EffectOp.ADD_COUNTER, EffectOp.SET_COUNTER -> {
                if (!ConditionEvaluator.evaluate(effect.condition, baseContext)) {
                    return state
                }
                val delta = resolveValue(effect, answers) ?: return state
                val counterDef = template.counters.firstOrNull { it.id == effect.counter }
                val isHeroScoped = counterDef?.counterScope == CounterScope.HERO
                if (isHeroScoped) {
                    applyPerHeroCounter(template, state, effect, delta, scenarioId, answers, heroStats, actingHeroId)
                } else {
                    val id = effect.counter ?: return state
                    val existing = state.counter(id)
                    val raw = if (effect.operation == EffectOp.ADD_COUNTER) existing + delta else delta
                    state.copy(counters = state.counters + (id to clamp(raw, template, id)))
                }
            }

            EffectOp.ADD_HERO_COUNTER, EffectOp.SET_HERO_COUNTER -> {
                val delta = resolveValue(effect, answers)
                applyPerHeroCounter(
                    template, state, effect, delta, scenarioId, answers, heroStats, actingHeroId,
                )
            }

            EffectOp.SET_FLAG -> {
                if (!ConditionEvaluator.evaluate(effect.condition, baseContext)) {
                    return state
                }
                val flagId = effect.flag ?: return state
                val value = effect.boolValue
                    ?: effect.from?.let { answers.booleans[it] }
                    ?: true
                val key = scenarioId ?: ""
                val existing = state.flags[flagId].orEmpty()
                state.copy(flags = state.flags + (flagId to (existing + (key to value))))
            }

            EffectOp.ADD_CARD -> {
                if (!ConditionEvaluator.evaluate(effect.condition, baseContext)) {
                    return state
                }
                val listId = effect.cardList ?: return state
                val code = effect.cardCode ?: return state
                if (effect.perHero || actingHeroId != null) {
                    val heroId = actingHeroId ?: return state
                    addHeroCards(state, listId, heroId, listOf(code))
                } else {
                    state.copy(
                        cardLists = state.cardLists +
                            (listId to (state.cardLists[listId].orEmpty() + code)),
                    )
                }
            }

            EffectOp.ADD_CARDS_FROM_ANSWER -> {
                if (!ConditionEvaluator.evaluate(effect.condition, baseContext)) {
                    return state
                }
                val listId = effect.cardList ?: return state
                val codes = answers.cardLists[effect.from].orEmpty()
                state.copy(
                    cardLists = state.cardLists +
                        (listId to (state.cardLists[listId].orEmpty() + codes)),
                )
            }

            EffectOp.ELIMINATE_HERO -> {
                val heroId = actingHeroId ?: return state
                val key = scenarioId ?: ""
                state.copy(
                    eliminatedInScenario = state.eliminatedInScenario +
                        (key to (state.eliminatedInScenario[key].orEmpty() + heroId)),
                )
            }

            EffectOp.UNKNOWN -> state
        }
    }

    /**
     * Applies a hero-scoped counter change.
     *
     * A hero eliminated in this scenario is skipped, which is the rule the brief
     * calls out: they take no part in the victory rewards but rejoin next time.
     */
    private fun applyPerHeroCounter(
        template: CampaignTemplate,
        state: CampaignState,
        effect: Effect,
        literalDelta: Int?,
        scenarioId: String?,
        answers: AnswerSet,
        heroStats: Map<String, HeroCardStats>,
        actingHeroId: String?,
    ): CampaignState {
        val counterId = effect.counter ?: return state
        val eliminated = state.eliminatedInScenario[scenarioId ?: ""].orEmpty()
        val targets = actingHeroId?.let { listOf(it) }
            ?: state.heroes.map { it.id }.filter { it !in eliminated }

        val perHeroAnswers = answers.perHeroNumbers[effect.from]
        val current = state.heroCounters[counterId].orEmpty().toMutableMap()

        for (heroId in targets) {
            val context = EvaluationContext(state, scenarioId, answers, heroId)
            if (!ConditionEvaluator.evaluate(effect.condition, context)) {
                continue
            }
            val value = perHeroAnswers?.get(heroId) ?: literalDelta ?: continue
            val capped = effect.max?.let { minOf(value, it) } ?: value
            val floored = effect.min?.let { maxOf(capped, it) } ?: capped

            val existing = current[heroId] ?: 0
            val raw = if (effect.operation == EffectOp.SET_HERO_COUNTER ||
                effect.operation == EffectOp.SET_COUNTER
            ) {
                floored
            } else {
                existing + floored
            }
            current[heroId] = clampHero(raw, template, counterId, heroStats[heroId])
        }
        return state.copy(heroCounters = state.heroCounters + (counterId to current))
    }

    private fun resolveValue(effect: Effect, answers: AnswerSet): Int? {
        val raw = effect.from?.let { answers.numbers[it] } ?: effect.value ?: return null
        val capped = effect.max?.let { minOf(raw, it) } ?: raw
        return effect.min?.let { maxOf(capped, it) } ?: capped
    }

    private fun clamp(value: Int, template: CampaignTemplate, counterId: String): Int {
        val def = template.counters.firstOrNull { it.id == counterId } ?: return value
        var result = value
        def.min?.let { result = maxOf(result, it) }
        def.max?.let { result = minOf(result, it) }
        return result
    }

    /** Hit points on Expert cap at the hero's printed health, read from the card database. */
    private fun clampHero(
        value: Int,
        template: CampaignTemplate,
        counterId: String,
        stats: HeroCardStats?,
    ): Int {
        val def = template.counters.firstOrNull { it.id == counterId } ?: return value
        var result = value
        def.min?.let { result = maxOf(result, it) }
        def.max?.let { result = minOf(result, it) }
        if (def.maxFrom == HERO_HEALTH_REFERENCE) {
            stats?.printedHealth?.let { result = minOf(result, it) }
        }
        return result
    }

    private fun addHeroCards(
        state: CampaignState,
        listId: String,
        heroId: String,
        codes: List<String>,
    ): CampaignState {
        val list = state.heroCardLists[listId].orEmpty()
        val forHero = list[heroId].orEmpty() + codes
        return state.copy(
            heroCardLists = state.heroCardLists + (listId to (list + (heroId to forHero))),
        )
    }

    private fun applyPurchase(
        state: CampaignState,
        event: CampaignEvent.MarketPurchase,
    ): CampaignState {
        val credits = state.heroCounters[MARKET_COUNTER_FALLBACK].orEmpty().toMutableMap()
        credits[event.heroId] = (credits[event.heroId] ?: 0) - event.cost
        val withCard = addHeroCards(state, event.cardListId, event.heroId, listOf(event.cardCode))
        return withCard.copy(
            heroCounters = withCard.heroCounters + (MARKET_COUNTER_FALLBACK to credits),
            purchases = withCard.purchases + Purchase(
                eventId = event.id,
                heroId = event.heroId,
                cardCode = event.cardCode,
                cost = event.cost,
                cardListId = event.cardListId,
            ),
        )
    }

    private fun applySetupAction(
        template: CampaignTemplate,
        state: CampaignState,
        event: CampaignEvent.SetupActionTaken,
        heroStats: Map<String, HeroCardStats>,
    ): CampaignState {
        val scenario = template.scenarios.firstOrNull { it.id == event.scenarioId } ?: return state
        val action = scenario.campaignSetup.mapNotNull { it.action }
            .firstOrNull { it.id == event.actionId } ?: return state

        var next = state
        action.cost?.let { cost ->
            val heroId = event.heroId
            if (heroId != null) {
                val counters = next.heroCounters[cost.counterId].orEmpty().toMutableMap()
                counters[heroId] = (counters[heroId] ?: 0) - cost.amount
                next = next.copy(
                    heroCounters = next.heroCounters + (cost.counterId to counters),
                )
            } else {
                next = next.copy(
                    counters = next.counters +
                        (cost.counterId to next.counter(cost.counterId) - cost.amount),
                )
            }
        }

        next = applyEffects(
            template = template,
            state = next,
            effects = action.effects,
            scenarioId = event.scenarioId,
            answers = AnswerSet(),
            heroStats = heroStats,
            actingHeroId = event.heroId,
        )

        val key = "${event.scenarioId}:${event.heroId.orEmpty()}"
        return next.copy(
            setupActionsTaken = next.setupActionsTaken +
                (key to (next.setupActionsTaken[key].orEmpty() + event.actionId)),
        )
    }

    private fun applyManual(
        state: CampaignState,
        event: CampaignEvent.ManualAdjustment,
    ): CampaignState {
        var next = state
        event.counterId?.let { counterId ->
            val value = event.value ?: return@let
            next = if (event.heroId != null) {
                val counters = next.heroCounters[counterId].orEmpty().toMutableMap()
                counters[event.heroId] = value
                next.copy(heroCounters = next.heroCounters + (counterId to counters))
            } else {
                next.copy(counters = next.counters + (counterId to value))
            }
        }
        event.flagId?.let { flagId ->
            val parts = flagId.split('.', limit = 2)
            val setId = parts[0]
            val key = parts.getOrNull(1) ?: ""
            val existing = next.flags[setId].orEmpty()
            next = next.copy(
                flags = next.flags + (setId to (existing + (key to (event.boolValue ?: true)))),
            )
        }
        return next
    }

    companion object {
        /** Prompt id the engine treats as "was this hero eliminated". */
        const val ELIMINATED_PROMPT_ID: String = "eliminated"
        const val HERO_HEALTH_REFERENCE: String = "heroCard.health"

        /** Counter a market purchase spends when the template does not say. */
        const val MARKET_COUNTER_FALLBACK: String = "credits"
    }
}
