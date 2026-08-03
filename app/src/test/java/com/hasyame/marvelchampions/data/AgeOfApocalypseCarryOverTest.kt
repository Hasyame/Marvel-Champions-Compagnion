package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The side missions of Age of Apocalypse, played through.
 *
 * The campaign's memory is which missions and overseers are spent, and the two
 * are struck under different rules: a mission is spent whether or not it fell,
 * while an overseer is only struck when it was defeated. Getting that backwards
 * would quietly re-offer a mission the players already ran, or retire an
 * overseer that walked away — neither of which shows up until the fourth
 * scenario of a campaign.
 */
@RunWith(RobolectricTestRunner::class)
class AgeOfApocalypseCarryOverTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val engine = CampaignEngine()

    private fun template(): CampaignTemplate {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("campaigns/aoa.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(CampaignTemplate.serializer(), text)
    }

    private val heroes = listOf(
        CampaignHero(id = "h1", deckId = "d1", heroCardCode = "01001", name = "Spider-Man"),
    )

    private fun started(t: CampaignTemplate) = CampaignEvent.CampaignStarted(
        id = "start",
        timestamp = 0,
        templateId = t.id,
        difficulty = "standard",
        heroes = heroes,
        startScenarioId = t.startScenarioId ?: t.scenarios.first().id,
    )

    /** The app drawing a card for a scenario's setup. */
    private fun drew(scenarioId: String, drawId: String, code: String, at: Long) =
        CampaignEvent.SetupDrawn(
            id = "draw-$scenarioId-$drawId",
            timestamp = at,
            scenarioId = scenarioId,
            drawId = drawId,
            cardCode = code,
        )

    private fun won(scenarioId: String, overseerDefeated: Boolean, at: Long) =
        CampaignEvent.ScenarioCompleted(
            id = "win-$scenarioId",
            timestamp = at,
            scenarioId = scenarioId,
            victory = true,
            answers = AnswerSet(
                booleans = mapOf(
                    "missionDefeated" to false,
                    "overseerDefeated" to overseerDefeated,
                ),
            ),
        )

    private fun lost(scenarioId: String, at: Long) = CampaignEvent.ScenarioCompleted(
        id = "loss-$scenarioId",
        timestamp = at,
        scenarioId = scenarioId,
        victory = false,
        answers = AnswerSet(),
    )

    @Test
    fun `the drawn mission is struck whether or not it was defeated`() {
        val t = template()
        // missionDefeated is false in both: if the strike depended on the
        // reward, the log would stay empty.
        val state = engine.fold(
            t,
            listOf(
                started(t),
                drew("s1_unus", "mission", "45166a", 1),
                won("s1_unus", overseerDefeated = false, at = 2),
                drew("s2_four_horsemen", "mission", "45167a", 3),
                won("s2_four_horsemen", overseerDefeated = false, at = 4),
            ),
        )

        assertEquals(
            listOf("45166a", "45167a"),
            state.cardLists["missionsUsed"].orEmpty().sorted(),
        )
    }

    @Test
    fun `the drawn overseer is struck only when it was defeated`() {
        val t = template()
        val state = engine.fold(
            t,
            listOf(
                started(t),
                drew("s1_unus", "overseer", "45179a", 1),
                won("s1_unus", overseerDefeated = false, at = 2),
                drew("s2_four_horsemen", "overseer", "45180a", 3),
                won("s2_four_horsemen", overseerDefeated = true, at = 4),
            ),
        )

        val struck = state.cardLists["overseersDefeated"].orEmpty()
        assertEquals(listOf("45180a"), struck)
        assertFalse("an overseer that survived must stay available", "45179a" in struck)
    }

    @Test
    fun `a drawn card leaves the pool for later scenarios`() {
        val t = template()
        val missionDraw = t.scenarios.single { it.id == "s2_four_horsemen" }
            .campaignSetup.mapNotNull { it.draw }.single { it.id == "mission" }

        val state = engine.fold(
            t,
            listOf(
                started(t),
                drew("s1_unus", "mission", "45166a", 1),
                won("s1_unus", overseerDefeated = false, at = 2),
            ),
        )

        val pool = CampaignEngine.drawPool(missionDraw, state)
        assertEquals(3, pool.size)
        assertFalse("a spent mission must not come up again", "45166a" in pool)
    }

    @Test
    fun `an exhausted pool refills rather than drawing nothing`() {
        val t = template()
        val draw = t.scenarios.first().campaignSetup.mapNotNull { it.draw }.single { it.id == "mission" }
        // Every mission spent, which cannot happen in five scenarios but must
        // not leave a setup step with no card in it.
        val spent = CampaignState(cardLists = mapOf("missionsUsed" to draw.from))

        assertEquals(draw.from, CampaignEngine.drawPool(draw, spent))
    }

    @Test
    fun `losing clears the draw so a replay sets up afresh`() {
        val t = template()
        val state = engine.fold(
            t,
            listOf(
                started(t),
                drew("s1_unus", "mission", "45166a", 1),
                lost("s1_unus", at = 2),
            ),
        )

        assertNull(CampaignEngine.drawnCard(state, "s1_unus", "mission"))
        // And the mission was never spent, so the replay can draw it again.
        assertTrue(state.cardLists["missionsUsed"].orEmpty().isEmpty())
        assertEquals("s1_unus", state.currentScenarioId)
    }

    @Test
    fun `a draw survives being read back`() {
        val t = template()
        val state = engine.fold(t, listOf(started(t), drew("s1_unus", "mission", "45168a", 1)))

        assertEquals("45168a", CampaignEngine.drawnCard(state, "s1_unus", "mission"))
        assertNotNull(state.draws["s1_unus"])
    }

    @Test
    fun `Protect the Professor is reserved for the last scenario`() {
        val t = template()
        val reserved = "45170a"

        t.scenarios.filter { it.id != "s5_en_sabah_nur" }.forEach { scenario ->
            scenario.campaignSetup.forEach { step ->
                assertFalse(
                    "${scenario.id} may draw the mission reserved for En Sabah Nur",
                    reserved in step.draw?.from.orEmpty(),
                )
                assertFalse(
                    "${scenario.id} sets up the mission reserved for En Sabah Nur",
                    reserved in step.cards,
                )
            }
        }

        val last = t.scenarios.single { it.id == "s5_en_sabah_nur" }
        assertTrue("the last scenario names it outright", last.campaignSetup.any { reserved in it.cards })
        assertTrue(
            "the last scenario must not draw its mission at random",
            last.campaignSetup.none { it.draw?.id == "mission" },
        )
    }

    @Test
    fun `every scenario draws an overseer`() {
        template().scenarios.forEach { scenario ->
            assertTrue(
                "${scenario.id} does not draw an overseer",
                scenario.campaignSetup.any { it.draw?.id == "overseer" },
            )
        }
    }

    @Test
    fun `the campaign carries no market`() {
        // Only Galaxy's Most Wanted has one; a market here would offer credits
        // this campaign never awards.
        assertNull(template().market)
    }
}
