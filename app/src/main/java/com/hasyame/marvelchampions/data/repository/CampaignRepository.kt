package com.hasyame.marvelchampions.data.repository

import android.content.Context
import android.net.Uri
import com.hasyame.marvelchampions.data.db.dao.CampaignDao
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.engine.HeroCardStats
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.TemplateError
import com.hasyame.marvelchampions.domain.campaign.template.TemplateValidationException
import com.hasyame.marvelchampions.domain.campaign.template.TemplateValidator
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Display names for everything a campaign template refers to by code.
 *
 * Templates store codes because they are stable and language-neutral; showing
 * one to a player would be unreadable, so they are resolved here against the
 * card database in the player's chosen language.
 */
data class CampaignCardNames(
    val cards: Map<String, String> = emptyMap(),
    val sets: Map<String, String> = emptyMap(),
    /** Host-relative image paths, for the market's shop layout. */
    val images: Map<String, String> = emptyMap(),
) {
    /** Falls back to the code, so a card missing from the database is still identifiable. */
    fun card(code: String): String = cards[code] ?: code

    fun set(code: String): String = sets[code] ?: code
}

/** A loaded run: the template, the derived state, and the timer. */
data class CampaignRun(
    val entity: CampaignRunEntity,
    val template: CampaignTemplate,
    val state: CampaignState,
    val events: List<CampaignEvent>,
    val timer: TimerState,
    val names: CampaignCardNames = CampaignCardNames(),
    /** Every card in every deck being played, for prompts that pick from them. */
    val deckCards: List<CampaignDeckCard> = emptyList(),
    /**
     * Campaign state as it stood before the most recent scenario result, so the
     * summary can show what that scenario alone awarded rather than a running
     * total.
     */
    val stateBeforeLastScenario: CampaignState? = null,
) {
    /** Host-relative image path for a card the template refers to. */
    fun imageSrc(code: String): String? = names.images[code]
}

/**
 * A finished or in-progress run, with what it amounted to.
 *
 * Everything here is folded from the event log rather than stored, so a
 * finished campaign's record cannot drift from what actually happened.
 */
data class CampaignSummary(
    val entity: CampaignRunEntity,
    val totalTimeMillis: Long = 0,
    val totalVictoryPoints: Int = 0,
    val heroNames: List<String> = emptyList(),
    val scenariosWon: Int = 0,
    val scenariosLost: Int = 0,
    val creditsRemaining: Int = 0,
    val cardsBought: Int = 0,
    val scenarios: List<ScenarioLogEntry> = emptyList(),
)

/** One scenario as it was played, with what was recorded for it. */
data class ScenarioLogEntry(
    val scenarioName: String,
    val victory: Boolean,
    val elapsedMillis: Long,
    /** The questionnaire, as label-and-value pairs ready to display. */
    val answers: List<Pair<String, String>> = emptyList(),
)

/**
 * One card in one player's deck, as a prompt needs to offer it.
 *
 * Held on the run rather than fetched by the questionnaire so the page stays a
 * pure function of what it is given, and so the cards are already resolved in
 * the player's language by the time the question is asked.
 */
data class CampaignDeckCard(
    val heroId: String,
    val heroName: String,
    val cardCode: String,
    val cardName: String,
    val typeName: String?,
    val quantity: Int,
)

/** A card a campaign added to a deck, and which campaign added it. */
data class CampaignGrantedCard(
    val cardCode: String,
    val campaignName: String,
    val runId: String,
    val cardListId: String,
)

sealed interface TemplateImportResult {
    data class Success(val template: CampaignTemplate) : TemplateImportResult
    data class Invalid(val errors: List<TemplateError>) : TemplateImportResult
    data class Unreadable(val message: String?) : TemplateImportResult
}

@Singleton
class CampaignRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val campaignDao: CampaignDao,
    private val cardDao: CardDao,
    private val deckRepository: DeckRepository,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val engine = CampaignEngine()

    fun observeRuns(): Flow<List<CampaignRunEntity>> = campaignDao.observeRuns()

    /**
     * Campaign templates bundled into this build, from `assets/campaigns/`.
     *
     * Templates describe mechanics only — counters, rewards, setup steps — and
     * never reproduce campaign book text, so they ship with the app and a
     * campaign is ready to start without importing anything.
     *
     * An invalid file is skipped rather than crashing the tab; [importTemplate]
     * is the path that reports problems, because there the user is watching.
     */
    suspend fun bundledTemplates(): List<CampaignTemplate> = withContext(ioDispatcher) {
        val names = runCatching { context.assets.list(CAMPAIGN_ASSET_DIR) }.getOrNull().orEmpty()
        names.filter { it.endsWith(".json", ignoreCase = true) }
            .mapNotNull { name ->
                runCatching {
                    val text = context.assets.open("$CAMPAIGN_ASSET_DIR/$name").use {
                        it.readBytes().decodeToString()
                    }
                    TemplateValidator.validateOrThrow(
                        json.decodeFromString(CampaignTemplate.serializer(), text),
                    )
                }.getOrNull()
            }
            .sortedBy { it.name.resolve("fr") }
    }

    /**
     * Reads a campaign template the user picked from device storage, for
     * campaigns [bundledTemplates] does not cover yet.
     *
     * Validation is strict and every problem is reported at once, so a
     * hand-written file can be fixed in one pass rather than one error at a
     * time.
     */
    suspend fun importTemplate(uri: Uri): TemplateImportResult = withContext(ioDispatcher) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: return@withContext TemplateImportResult.Unreadable("could not open file")

            val template = json.decodeFromString(CampaignTemplate.serializer(), text)
            TemplateImportResult.Success(TemplateValidator.validateOrThrow(template))
        } catch (invalid: TemplateValidationException) {
            TemplateImportResult.Invalid(invalid.errors)
        } catch (error: Exception) {
            TemplateImportResult.Unreadable(error.message)
        }
    }

    suspend fun startRun(
        template: CampaignTemplate,
        difficulty: String,
        deckIds: List<String>,
        name: String = "",
    ): String = withContext(ioDispatcher) {
        val heroes = deckIds.mapNotNull { deckId ->
            deckRepository.getDeck(deckId)?.let { deck ->
                CampaignHero(
                    id = deck.id,
                    deckId = deck.id,
                    heroCardCode = deck.heroCode,
                    name = deck.heroName,
                )
            }
        }
        val runId = UUID.randomUUID().toString()
        campaignDao.insertRun(
            CampaignRunEntity(
                id = runId,
                templateId = template.id,
                templateName = template.name.resolve("fr"),
                name = name.ifBlank { template.name.resolve("fr") },
                difficulty = difficulty,
                createdAt = System.currentTimeMillis(),
                // The template travels with the run so it stays readable even
                // if the source file is moved or deleted.
                templateJson = json.encodeToString(CampaignTemplate.serializer(), template),
            ),
        )
        append(
            runId,
            CampaignEvent.CampaignStarted(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                templateId = template.id,
                difficulty = difficulty,
                heroes = heroes,
                startScenarioId = template.startScenarioId
                    ?: template.scenarios.firstOrNull()?.id.orEmpty(),
            ),
        )
        runId
    }

    suspend fun load(runId: String, locale: CardLocale): CampaignRun? = withContext(ioDispatcher) {
        val entity = campaignDao.getRun(runId) ?: return@withContext null
        val stored = runCatching {
            json.decodeFromString(CampaignTemplate.serializer(), entity.templateJson)
        }.getOrNull() ?: return@withContext null

        // A run stores its own copy of the template so it survives the file
        // moving, but a bundled campaign of the same id is the newer rules. The
        // log is the history and the template is only how it is interpreted, so
        // re-folding against the corrected template is exactly the replay the
        // design is built for — and it stops a run being stuck on whichever
        // version happened to be installed the day it started.
        val template = bundledTemplates().firstOrNull { it.id == stored.id } ?: stored
        if (template != stored) {
            campaignDao.setTemplateJson(
                runId,
                json.encodeToString(CampaignTemplate.serializer(), template),
            )
        }

        val events = campaignDao.getEvents(runId).mapNotNull { row ->
            runCatching { json.decodeFromString(CampaignEvent.serializer(), row.payload) }.getOrNull()
        }
        val heroStats = heroStats(events, locale)

        // Folding the log twice — once without the latest scenario result — is
        // how the summary reports what that scenario awarded. Nothing extra is
        // stored, so it cannot drift from the log.
        val lastScenarioEvent = events.filterIsInstance<CampaignEvent.ScenarioCompleted>()
            .maxByOrNull { it.timestamp }
        val before = lastScenarioEvent?.let { last ->
            engine.fold(template, events.filterNot { it.id == last.id }, heroStats)
        }
        val state = engine.fold(template, events, heroStats)

        CampaignRun(
            entity = entity,
            template = template,
            state = state,
            events = events,
            timer = TimerState(
                accumulatedMillis = entity.timerAccumulatedMillis,
                runningSinceEpochMillis = entity.timerRunningSince,
            ),
            // Cards recorded during play are named too, so a step that reads
            // back a list shows titles rather than codes.
            names = resolveNames(template, locale, state.cardLists.values.flatten().toSet()),
            deckCards = deckCards(state, locale),
            stateBeforeLastScenario = before,
        )
    }

    /**
     * The contents of every deck in the run, flattened and labelled by hero.
     *
     * A deck that has since been deleted simply contributes nothing: the run
     * has to stay openable, and a campaign log is worth more than the prompt
     * that can no longer be offered.
     */
    private suspend fun deckCards(
        state: CampaignState,
        locale: CardLocale,
    ): List<CampaignDeckCard> = state.heroes.flatMap { hero ->
        val deckId = hero.deckId ?: return@flatMap emptyList()
        val contents = deckRepository.contents(deckId, locale) ?: return@flatMap emptyList()
        contents.cardsByType.values.flatten().map { entry ->
            CampaignDeckCard(
                heroId = hero.id,
                heroName = hero.name,
                cardCode = entry.card.code,
                cardName = entry.card.name.orEmpty().ifBlank { entry.card.code },
                typeName = entry.card.typeName,
                quantity = entry.quantity,
            )
        }
    }

    /**
     * Looks up every card and card set the template names, in one pass, so the
     * campaign screens can show names rather than codes.
     */
    private suspend fun resolveNames(
        template: CampaignTemplate,
        locale: CardLocale,
        recordedCodes: Set<String> = emptySet(),
    ): CampaignCardNames {
        val cardCodes = buildSet {
            addAll(recordedCodes)
            template.market?.entries?.forEach { add(it.cardCode) }
            template.scenarios.forEach { scenario ->
                scenario.baseSetup?.let { setup ->
                    setup.villainDeck.values.forEach { addAll(it) }
                    addAll(setup.mainScheme)
                }
                scenario.campaignSetup.forEach { addAll(it.cards) }
                listOfNotNull(scenario.onVictory, scenario.onDefeat).forEach { outcome ->
                    outcome.prompts.forEach { addAll(it.cards) }
                }
            }
        }
        val setCodes = buildSet {
            template.scenarios.forEach { scenario ->
                scenario.baseSetup?.let { addAll(it.encounterSets); addAll(it.modularSets) }
            }
        }

        val resolved = cardCodes.mapNotNull { code ->
            cardDao.getCardPreferringLocale(code, locale.code)?.let { code to it }
        }

        val setNames = SET_TYPES.flatMap { type -> cardDao.getCardSets(type, locale.code) }
            .mapNotNull { summary -> summary.name?.let { summary.code to it } }
            .toMap()
            .filterKeys { it in setCodes }

        return CampaignCardNames(
            // The stage is part of the identity, not decoration: Drang I, II
            // and III are all called "Drang", so a villain deck would otherwise
            // read "Drang, Drang".
            cards = resolved.associate { (code, card) ->
                code to card.stage?.takeIf { it.isNotBlank() }
                    ?.let { stage -> "${card.name} ($stage)" }
                    .orEmpty()
                    .ifEmpty { card.name }
            },
            sets = setNames,
            images = resolved.mapNotNull { (code, card) ->
                card.imageSrc?.let { code to it }
            }.toMap(),
        )
    }

    /**
     * Printed health per hero, so `maxFrom: "heroCard.health"` caps hit points
     * without the template restating a number that is already on the card.
     */
    private suspend fun heroStats(
        events: List<CampaignEvent>,
        locale: CardLocale,
    ): Map<String, HeroCardStats> {
        val start = events.filterIsInstance<CampaignEvent.CampaignStarted>().firstOrNull()
            ?: return emptyMap()
        return start.heroes.associate { hero ->
            hero.id to HeroCardStats(
                heroId = hero.id,
                printedHealth = cardDao.getCard(hero.heroCardCode, locale.code)?.health,
            )
        }
    }

    suspend fun append(runId: String, event: CampaignEvent) = withContext(ioDispatcher) {
        campaignDao.appendEvent(
            CampaignEventEntity(
                id = event.id,
                runId = runId,
                timestamp = event.timestamp,
                payload = json.encodeToString(CampaignEvent.serializer(), event),
            ),
        )
    }

    suspend fun updateTimer(runId: String, timer: TimerState, scenarioId: String?) =
        withContext(ioDispatcher) {
            campaignDao.updateTimer(
                runId = runId,
                accumulated = timer.accumulatedMillis,
                runningSince = timer.runningSinceEpochMillis,
                scenarioId = scenarioId,
            )
        }

    suspend fun markFinished(runId: String, finished: Boolean) = withContext(ioDispatcher) {
        campaignDao.setFinished(runId, finished)
    }

    suspend fun deleteRun(runId: String) = withContext(ioDispatcher) {
        campaignDao.deleteRun(runId)
    }

    /**
     * Cards a campaign has granted to a deck, across every run it appears in.
     *
     * A campaign never edits the deck itself: the grant lives in the run's event
     * log and is applied on top, so the imported deck stays the source of truth
     * and it is always visible exactly what a campaign added.
     */
    suspend fun campaignCardsForDeck(deckId: String): List<CampaignGrantedCard> =
        withContext(ioDispatcher) {
            campaignDao.getRuns().flatMap { entity ->
                val template = runCatching {
                    json.decodeFromString(CampaignTemplate.serializer(), entity.templateJson)
                }.getOrNull() ?: return@flatMap emptyList()

                val events = campaignDao.getEvents(entity.id).mapNotNull { row ->
                    runCatching {
                        json.decodeFromString(CampaignEvent.serializer(), row.payload)
                    }.getOrNull()
                }
                val state = engine.fold(template, events)

                // Hero ids in a run are the deck ids they were built from.
                state.heroCardLists.flatMap { (listId, byHero) ->
                    byHero[deckId].orEmpty().map { code ->
                        CampaignGrantedCard(
                            cardCode = code,
                            campaignName = entity.name.ifBlank { entity.templateName },
                            runId = entity.id,
                            cardListId = listId,
                        )
                    }
                }
            }
        }

    /** Every run with its statistics, newest first, unfinished ones on top. */
    suspend fun summaries(): List<CampaignSummary> = withContext(ioDispatcher) {
        campaignDao.getRuns().map { entity ->
            val template = runCatching {
                json.decodeFromString(CampaignTemplate.serializer(), entity.templateJson)
            }.getOrNull() ?: return@map CampaignSummary(entity)

            val events = campaignDao.getEvents(entity.id).mapNotNull { row ->
                runCatching {
                    json.decodeFromString(CampaignEvent.serializer(), row.payload)
                }.getOrNull()
            }
            val state = engine.fold(template, events)
            val creditsCounter = template.market?.counterId ?: CampaignEngine.MARKET_COUNTER_FALLBACK

            CampaignSummary(
                entity = entity,
                totalTimeMillis = state.totalPlayTimeMillis,
                // Victory points are recorded per scenario and never carried,
                // so the campaign total is the sum of what was recorded.
                totalVictoryPoints = state.completedScenarios
                    .filter { it.victory }
                    .sumOf { it.answers.numbers["vp"] ?: 0 },
                heroNames = state.heroes.map { it.name },
                scenariosWon = state.completedScenarios.count { it.victory },
                scenariosLost = state.completedScenarios.count { !it.victory },
                creditsRemaining = state.heroes.sumOf { state.heroCounter(creditsCounter, it.id) },
                cardsBought = state.purchases.size,
                scenarios = state.completedScenarios.map { result ->
                    val scenario = template.scenarios.firstOrNull { it.id == result.scenarioId }
                    ScenarioLogEntry(
                        scenarioName = scenario?.name?.resolve("fr")?.takeIf { it.isNotBlank() }
                            ?: result.scenarioId,
                        victory = result.victory,
                        elapsedMillis = result.elapsedMillis,
                        answers = describeAnswers(scenario, result.answers, state),
                    )
                },
            )
        }
    }

    /**
     * Turns raw answers into label-and-value pairs.
     *
     * Labels come from the template's prompts, so the record reads as the
     * questions that were asked rather than as internal ids.
     */
    private fun describeAnswers(
        scenario: com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate?,
        answers: com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet,
        state: CampaignState,
    ): List<Pair<String, String>> {
        val prompts = scenario?.onVictory?.prompts.orEmpty()
        fun label(id: String) =
            prompts.firstOrNull { it.id == id }?.label?.resolve("fr")?.takeIf { it.isNotBlank() }
                ?: id

        fun heroName(heroId: String) =
            state.heroes.firstOrNull { it.id == heroId }?.name ?: heroId

        return buildList {
            answers.numbers.forEach { (id, value) -> add(label(id) to value.toString()) }
            answers.booleans.forEach { (id, value) -> add(label(id) to if (value) "✔" else "✘") }
            answers.choices.forEach { (id, value) -> add(label(id) to value) }
            answers.cardLists.forEach { (id, codes) ->
                add(label(id) to codes.joinToString(", "))
            }
            answers.perHeroNumbers.forEach { (id, byHero) ->
                add(label(id) to byHero.entries.joinToString(", ") { "${heroName(it.key)} ${it.value}" })
            }
            answers.perHeroBooleans.forEach { (id, byHero) ->
                val yes = byHero.filterValues { it }.keys.map(::heroName)
                add(label(id) to yes.takeIf { it.isNotEmpty() }?.joinToString(", ").orEmpty())
            }
        }.filter { it.second.isNotBlank() }
    }

    fun newEventId(): String = UUID.randomUUID().toString()

    private companion object {
        const val CAMPAIGN_ASSET_DIR = "campaigns"

        /** Card set kinds a scenario's encounter and modular sets can come from. */
        val SET_TYPES = listOf("villain", "modular", "standard", "expert", "nemesis")
    }
}
