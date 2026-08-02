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
     * That folder is **gitignored**, so the templates are baked into the APK on
     * the machine that builds it and never reach the repository — the same
     * arrangement as the card seed. It means a campaign is ready to start
     * without importing anything, while Fantasy Flight's text stays out of
     * version control.
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
     * Reads a campaign template the user picked from device storage.
     *
     * Templates are never bundled in the APK: they contain verbatim campaign
     * book text. Validation is strict and every problem is reported at once, so
     * a hand-written file can be fixed in one pass rather than one error at a
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
        campaignDao.upsertRun(
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
        val template = runCatching {
            json.decodeFromString(CampaignTemplate.serializer(), entity.templateJson)
        }.getOrNull() ?: return@withContext null

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

        CampaignRun(
            entity = entity,
            template = template,
            state = engine.fold(template, events, heroStats),
            events = events,
            timer = TimerState(
                accumulatedMillis = entity.timerAccumulatedMillis,
                runningSinceEpochMillis = entity.timerRunningSince,
            ),
            names = resolveNames(template, locale),
            stateBeforeLastScenario = before,
        )
    }

    /**
     * Looks up every card and card set the template names, in one pass, so the
     * campaign screens can show names rather than codes.
     */
    private suspend fun resolveNames(
        template: CampaignTemplate,
        locale: CardLocale,
    ): CampaignCardNames {
        val cardCodes = buildSet {
            template.market?.entries?.forEach { add(it.cardCode) }
            template.scenarios.forEach { scenario ->
                scenario.baseSetup?.let { setup ->
                    setup.villainDeck.values.forEach { addAll(it) }
                    addAll(setup.mainScheme)
                }
                scenario.campaignSetup.forEach { addAll(it.cards) }
            }
        }
        val setCodes = buildSet {
            template.scenarios.forEach { scenario ->
                scenario.baseSetup?.let { addAll(it.encounterSets); addAll(it.modularSets) }
            }
        }

        val resolved = cardCodes.mapNotNull { code ->
            cardDao.getCard(code, locale.code)?.let { code to it }
        }

        val setNames = SET_TYPES.flatMap { type -> cardDao.getCardSets(type, locale.code) }
            .mapNotNull { summary -> summary.name?.let { summary.code to it } }
            .toMap()
            .filterKeys { it in setCodes }

        return CampaignCardNames(
            cards = resolved.associate { (code, card) -> code to card.name },
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
            campaignDao.getRun(runId)?.let { run ->
                campaignDao.upsertRun(
                    run.copy(
                        timerAccumulatedMillis = timer.accumulatedMillis,
                        timerRunningSince = timer.runningSinceEpochMillis,
                        timerScenarioId = scenarioId,
                    ),
                )
            }
        }

    suspend fun markFinished(runId: String, finished: Boolean) = withContext(ioDispatcher) {
        campaignDao.getRun(runId)?.let { campaignDao.upsertRun(it.copy(finished = finished)) }
    }

    suspend fun deleteRun(runId: String) = withContext(ioDispatcher) {
        campaignDao.deleteRun(runId)
    }

    fun newEventId(): String = UUID.randomUUID().toString()

    private companion object {
        const val CAMPAIGN_ASSET_DIR = "campaigns"

        /** Card set kinds a scenario's encounter and modular sets can come from. */
        val SET_TYPES = listOf("villain", "modular", "standard", "expert", "nemesis")
    }
}
