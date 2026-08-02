package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import com.hasyame.marvelchampions.data.db.dao.CampaignDao
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Headhunter tally the whole campaign hangs off, taken the long way round:
 * answers serialised, written to SQLite, read back, decoded and folded, exactly
 * as the app does on every screen.
 *
 * Folding an in-memory log proves the arithmetic. It does not prove the answers
 * survive the trip through storage, and a boolean lost in serialisation looks
 * identical to an engine that never counted.
 */
@RunWith(RobolectricTestRunner::class)
class CampaignLogPersistenceTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: CampaignDao

    /** The same configuration the app injects, so encoding matches production. */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }
    private val engine = CampaignEngine()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.campaignDao()
    }

    @After
    fun tearDown() = database.close()

    private fun gmw(): CampaignTemplate {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("campaigns/gmw.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(CampaignTemplate.serializer(), text)
    }

    /** Events reference their run, so the row has to exist before any is stored. */
    private suspend fun insertRun(template: CampaignTemplate) {
        dao.insertRun(
            CampaignRunEntity(
                id = RUN_ID,
                templateId = template.id,
                templateName = "The Galaxy's Most Wanted",
                name = "Persistence",
                difficulty = "standard",
                createdAt = 0L,
                templateJson = json.encodeToString(CampaignTemplate.serializer(), template),
            ),
        )
    }

    private suspend fun store(event: CampaignEvent, at: Long) {
        dao.appendEvent(
            CampaignEventEntity(
                id = event.id,
                runId = RUN_ID,
                timestamp = at,
                payload = json.encodeToString(CampaignEvent.serializer(), event),
            ),
        )
    }

    /** Reads the log back out of SQLite and folds it, as every screen does. */
    private suspend fun stateFromDatabase(template: CampaignTemplate) = engine.fold(
        template,
        dao.getEvents(RUN_ID).mapNotNull {
            runCatching { json.decodeFromString(CampaignEvent.serializer(), it.payload) }.getOrNull()
        },
    )

    @Test
    fun `the headhunter tally survives storage and drives the next scenario's setup`() = runTest {
        val template = gmw()
        insertRun(template)
        store(
            CampaignEvent.CampaignStarted(
                id = "start",
                timestamp = 0,
                templateId = template.id,
                difficulty = "standard",
                heroes = listOf(
                    CampaignHero(id = "h1", deckId = "d1", heroCardCode = "01001", name = "Drax"),
                ),
                startScenarioId = template.startScenarioId ?: template.scenarios.first().id,
            ),
            at = 0,
        )

        // Mark the box in every scenario, checking after each one that the next
        // scenario's setup has grown by exactly one card.
        val expected = listOf(
            "s1_badoon" to ("s2_museum" to listOf(ON_THE_HUNT)),
            "s2_museum" to ("s3_escape" to listOf(ON_THE_HUNT, DEAD_TO_RIGHTS)),
            "s3_escape" to ("s4_nebula" to listOf(ON_THE_HUNT, DEAD_TO_RIGHTS, HENCHMAN)),
            "s4_nebula" to (
                "s5_ronan" to listOf(ON_THE_HUNT, DEAD_TO_RIGHTS, HENCHMAN, FUGITIVE_RECOVERY)
                ),
        )

        expected.forEachIndexed { index, (playedId, next) ->
            store(
                CampaignEvent.ScenarioCompleted(
                    id = "win-$playedId",
                    timestamp = index + 1L,
                    scenarioId = playedId,
                    victory = true,
                    answers = AnswerSet(
                        numbers = mapOf("vp" to 0, "artifactCount" to 0, "evasionCount" to 0),
                        booleans = mapOf("headhunterInVictoryDisplay" to true),
                    ),
                    elapsedMillis = 1000,
                ),
                at = index + 1L,
            )

            val state = stateFromDatabase(template)
            assertEquals(
                "after storing the win for $playedId",
                index + 1,
                state.countTrue("headhunterDefeated"),
            )

            val (nextId, cards) = next
            val scenario = template.scenarios.first { it.id == nextId }
            val shown = scenario.campaignSetup
                .filter {
                    ConditionEvaluator.evaluate(it.condition, EvaluationContext(state, nextId))
                }
                .flatMap { it.cards }
                .filter { it in LADDER }
            assertEquals("setup shown for $nextId", cards, shown)
        }
    }

    @Test
    fun `an unmarked box is stored as false rather than lost`() = runTest {
        val template = gmw()
        insertRun(template)
        store(
            CampaignEvent.ScenarioCompleted(
                id = "win",
                timestamp = 1,
                scenarioId = "s1_badoon",
                victory = true,
                answers = AnswerSet(booleans = mapOf("headhunterInVictoryDisplay" to false)),
                elapsedMillis = 1000,
            ),
            at = 1,
        )
        val decoded = dao.getEvents(RUN_ID).map {
            json.decodeFromString(CampaignEvent.serializer(), it.payload)
        }
        val answers = (decoded.single() as CampaignEvent.ScenarioCompleted).answers
        // `coerceInputValues` turning a missing false into a dropped key would
        // make an unmarked box indistinguishable from an unanswered question.
        assertEquals(false, answers.booleans["headhunterInVictoryDisplay"])
        assertEquals(0, stateFromDatabase(template).countTrue("headhunterDefeated"))
    }

    private companion object {
        const val RUN_ID = "run-persistence"
        const val ON_THE_HUNT = "16184"
        const val DEAD_TO_RIGHTS = "16185"
        const val HENCHMAN = "16186"
        const val FUGITIVE_RECOVERY = "16187"
        val LADDER = setOf(ON_THE_HUNT, DEAD_TO_RIGHTS, HENCHMAN, FUGITIVE_RECOVERY)
    }
}
