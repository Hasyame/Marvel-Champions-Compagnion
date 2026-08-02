package com.hasyame.marvelchampions.domain.campaign.template

/** A problem with a template, named precisely enough to fix by hand. */
data class TemplateError(val path: String, val message: String) {
    override fun toString(): String = "$path: $message"
}

class TemplateValidationException(val errors: List<TemplateError>) :
    IllegalArgumentException(
        "Campaign template is invalid:\n" + errors.joinToString("\n") { "  - $it" },
    )

/**
 * Validates a template at load time and fails loudly.
 *
 * A template is hand-written from a campaign book, so typos are the normal
 * case, not the exception. Silently ignoring an unknown effect would mean a
 * campaign quietly not awarding credits, which is far worse than refusing to
 * load.
 */
object TemplateValidator {

    private const val SUPPORTED_SCHEMA_VERSION = 1

    fun validate(template: CampaignTemplate): List<TemplateError> {
        val errors = mutableListOf<TemplateError>()

        if (template.id.isBlank()) {
            errors += TemplateError("id", "must not be blank")
        }
        if (template.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            errors += TemplateError(
                "schemaVersion",
                "is ${template.schemaVersion}, this build understands $SUPPORTED_SCHEMA_VERSION",
            )
        }
        if (template.scenarios.isEmpty()) {
            errors += TemplateError("scenarios", "a campaign needs at least one scenario")
        }

        val counterIds = template.counters.map { it.id }.toSet()
        val flagSetIds = template.flagSets.map { it.id }.toSet()
        val cardListIds = template.cardLists.map { it.id }.toSet()
        val scenarioIds = template.scenarios.map { it.id }.toSet()

        template.counters.duplicates { it.id }.forEach {
            errors += TemplateError("counters", "duplicate counter id '$it'")
        }
        template.flagSets.duplicates { it.id }.forEach {
            errors += TemplateError("flagSets", "duplicate flag set id '$it'")
        }
        template.scenarios.duplicates { it.id }.forEach {
            errors += TemplateError("scenarios", "duplicate scenario id '$it'")
        }

        template.startScenarioId?.let {
            if (it !in scenarioIds) {
                errors += TemplateError("startScenarioId", "unknown scenario '$it'")
            }
        }

        template.market?.let { market ->
            if (market.counterId !in counterIds) {
                errors += TemplateError("market.counterId", "unknown counter '${market.counterId}'")
            }
            market.entries.forEachIndexed { index, entry ->
                if (entry.cost < 0) {
                    errors += TemplateError("market.entries[$index]", "cost must not be negative")
                }
                if (entry.cardListId !in cardListIds) {
                    errors += TemplateError(
                        "market.entries[$index].cardListId",
                        "unknown card list '${entry.cardListId}'",
                    )
                }
            }
            market.entries.duplicates { it.cardCode }.forEach {
                errors += TemplateError("market.entries", "duplicate card '$it'")
            }
        }

        template.scenarios.forEach { scenario ->
            val path = "scenarios.${scenario.id}"
            scenario.campaignSetup.forEachIndexed { index, step ->
                validateCondition(step.condition, "$path.campaignSetup[$index].when", counterIds, flagSetIds, errors)
                step.action?.let { action ->
                    action.cost?.let { cost ->
                        if (cost.counterId !in counterIds) {
                            errors += TemplateError(
                                "$path.campaignSetup[$index].action.cost",
                                "unknown counter '${cost.counterId}'",
                            )
                        }
                    }
                    action.effects.forEachIndexed { effectIndex, effect ->
                        validateEffect(
                            effect,
                            "$path.campaignSetup[$index].action.effects[$effectIndex]",
                            counterIds, flagSetIds, cardListIds, errors,
                        )
                    }
                }
            }
            validateOutcome(scenario.onVictory, "$path.onVictory", counterIds, flagSetIds, cardListIds, scenarioIds, errors)
            validateOutcome(scenario.onDefeat, "$path.onDefeat", counterIds, flagSetIds, cardListIds, scenarioIds, errors)
        }

        return errors
    }

    fun validateOrThrow(template: CampaignTemplate): CampaignTemplate {
        val errors = validate(template)
        if (errors.isNotEmpty()) {
            throw TemplateValidationException(errors)
        }
        return template
    }

    private fun validateOutcome(
        outcome: Outcome?,
        path: String,
        counterIds: Set<String>,
        flagSetIds: Set<String>,
        cardListIds: Set<String>,
        scenarioIds: Set<String>,
        errors: MutableList<TemplateError>,
    ) {
        if (outcome == null) {
            return
        }
        val promptIds = outcome.prompts.map { it.id }.toSet()

        outcome.prompts.duplicates { it.id }.forEach {
            errors += TemplateError("$path.prompts", "duplicate prompt id '$it'")
        }
        outcome.prompts.forEachIndexed { index, prompt ->
            if (prompt.promptType == PromptType.UNKNOWN) {
                errors += TemplateError("$path.prompts[$index]", "unknown prompt type '${prompt.type}'")
            }
            if (prompt.promptType == PromptType.CHOICE && prompt.options.isEmpty()) {
                errors += TemplateError("$path.prompts[$index]", "a choice prompt needs options")
            }
            if (prompt.promptType == PromptType.CARD_SELECT && prompt.cards.isEmpty()) {
                errors += TemplateError("$path.prompts[$index]", "a cardSelect prompt needs cards")
            }
        }

        outcome.effects.forEachIndexed { index, effect ->
            validateEffect(effect, "$path.effects[$index]", counterIds, flagSetIds, cardListIds, errors)
            effect.from?.let {
                if (it !in promptIds) {
                    errors += TemplateError(
                        "$path.effects[$index].from",
                        "no prompt with id '$it' in this outcome",
                    )
                }
            }
        }

        outcome.next.forEachIndexed { index, step ->
            if (step.goto != null && step.goto !in scenarioIds) {
                errors += TemplateError("$path.next[$index].goto", "unknown scenario '${step.goto}'")
            }
            if (step.goto == null && !step.end) {
                errors += TemplateError("$path.next[$index]", "needs either goto or end")
            }
            validateCondition(step.condition, "$path.next[$index].when", counterIds, flagSetIds, errors)
        }
    }

    private fun validateEffect(
        effect: Effect,
        path: String,
        counterIds: Set<String>,
        flagSetIds: Set<String>,
        cardListIds: Set<String>,
        errors: MutableList<TemplateError>,
    ) {
        if (!EffectOp.isKnown(effect.op)) {
            errors += TemplateError(path, "unknown effect op '${effect.op}'")
            return
        }
        effect.counter?.let {
            if (it !in counterIds) errors += TemplateError("$path.counter", "unknown counter '$it'")
        }
        effect.flag?.let {
            if (it.substringBefore('.') !in flagSetIds) {
                errors += TemplateError("$path.flag", "unknown flag set '$it'")
            }
        }
        effect.cardList?.let {
            if (it !in cardListIds) {
                errors += TemplateError("$path.cardList", "unknown card list '$it'")
            }
        }
        when (effect.operation) {
            EffectOp.ADD_COUNTER, EffectOp.SUBTRACT_COUNTER, EffectOp.SET_COUNTER,
            EffectOp.ADD_HERO_COUNTER, EffectOp.SET_HERO_COUNTER,
            -> {
                if (effect.counter == null) {
                    errors += TemplateError(path, "${effect.op} needs a counter")
                }
                if (effect.value == null && effect.from == null) {
                    errors += TemplateError(path, "${effect.op} needs either value or from")
                }
            }

            EffectOp.SET_FLAG -> if (effect.flag == null) {
                errors += TemplateError(path, "setFlag needs a flag")
            }

            EffectOp.ADD_CARD -> if (effect.cardCode == null) {
                errors += TemplateError(path, "addCard needs a cardCode")
            }

            else -> Unit
        }
        validateCondition(effect.condition, "$path.when", counterIds, flagSetIds, errors)
    }

    private fun validateCondition(
        condition: Condition?,
        path: String,
        counterIds: Set<String>,
        flagSetIds: Set<String>,
        errors: MutableList<TemplateError>,
    ) {
        if (condition == null) {
            return
        }
        condition.counter?.let {
            if (it !in counterIds) errors += TemplateError("$path.counter", "unknown counter '$it'")
        }
        condition.countTrue?.let {
            if (it !in flagSetIds) {
                errors += TemplateError("$path.countTrue", "unknown flag set '$it'")
            }
        }
        listOfNotNull(condition.flag, condition.notFlag).forEach {
            if (it.substringBefore('.') !in flagSetIds) {
                errors += TemplateError("$path.flag", "unknown flag set '$it'")
            }
        }
        condition.all.forEach { validateCondition(it, "$path.all", counterIds, flagSetIds, errors) }
        condition.any.forEach { validateCondition(it, "$path.any", counterIds, flagSetIds, errors) }
    }

    private fun <T, K> List<T>.duplicates(key: (T) -> K): List<K> =
        groupBy(key).filterValues { it.size > 1 }.keys.toList()
}
