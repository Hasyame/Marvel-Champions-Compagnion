package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The side missions of Age of Apocalypse, played through.
 *
 * The campaign's one piece of memory is which missions and overseers have been
 * struck from the log, and the two are struck under different rules: a mission
 * is spent whether or not it fell, while an overseer is only struck when it was
 * defeated. Getting that backwards would quietly re-offer a mission the players
 * already ran, or retire an overseer that walked away — neither of which shows
 * up until the fourth scenario of a campaign.
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

    /**
     * A won scenario. [overseer] is empty when the overseer survived, which is
     * how the questionnaire records "not defeated".
     */
    private fun won(scenarioId: String, mission: String, overseer: List<String>, at: Long) =
        CampaignEvent.ScenarioCompleted(
            id = "win-$scenarioId",
            timestamp = at,
            scenarioId = scenarioId,
            victory = true,
            answers = AnswerSet(
                booleans = mapOf("missionDefeated" to false),
                cardLists = mapOf("mission" to listOf(mission), "overseer" to overseer),
            ),
        )

    @Test
    fun `a mission is struck whether or not it was defeated`() {
        val t = template()
        // Both scenarios record missionDefeated = false, so if the strike
        // depended on the reward the log would stay empty.
        val state = engine.fold(
            t,
            listOf(
                started(t),
                won("s1_unus", mission = "45166a", overseer = emptyList(), at = 1),
                won("s2_four_horsemen", mission = "45167a", overseer = emptyList(), at = 2),
            ),
        )

        assertEquals(
            listOf("45166a", "45167a"),
            state.cardLists["missionsUsed"].orEmpty().sorted(),
        )
    }

    @Test
    fun `an overseer is only struck when it was defeated`() {
        val t = template()
        val state = engine.fold(
            t,
            listOf(
                started(t),
                // Survived: nothing named, so nothing struck.
                won("s1_unus", mission = "45166a", overseer = emptyList(), at = 1),
                // Defeated: named, and struck.
                won("s2_four_horsemen", mission = "45167a", overseer = listOf("45180a"), at = 2),
            ),
        )

        val struck = state.cardLists["overseersDefeated"].orEmpty()
        assertEquals(listOf("45180a"), struck)
        assertFalse("an overseer that survived must stay available", struck.contains("45179a"))
    }

    @Test
    fun `by the last scenario every mission run is on the log`() {
        val t = template()
        val state = engine.fold(
            t,
            listOf(
                started(t),
                won("s1_unus", "45166a", listOf("45179a"), 1),
                won("s2_four_horsemen", "45167a", emptyList(), 2),
                won("s3_apocalypse", "45168a", listOf("45181a"), 3),
                won("s4_dark_beast", "45169a", emptyList(), 4),
            ),
        )

        // All four of the selectable missions are spent, which is what leaves
        // scenario 5 with only Protect the Professor.
        assertEquals(4, state.cardLists["missionsUsed"].orEmpty().size)
        assertEquals(2, state.cardLists["overseersDefeated"].orEmpty().size)
    }

    @Test
    fun `Protect the Professor is reserved for the last scenario`() {
        val t = template()
        val reserved = "45170a"

        t.scenarios.filter { it.id != "s5_en_sabah_nur" }.forEach { scenario ->
            scenario.onVictory?.prompts.orEmpty()
                .filter { it.id == "mission" }
                .forEach { prompt ->
                    assertFalse(
                        "${scenario.id} offers the mission reserved for En Sabah Nur",
                        reserved in prompt.cards,
                    )
                }
            scenario.campaignSetup.forEach { step ->
                assertFalse(
                    "${scenario.id} sets up the mission reserved for En Sabah Nur",
                    reserved in step.cards,
                )
            }
        }

        // And the last scenario names it rather than drawing at random.
        val last = t.scenarios.single { it.id == "s5_en_sabah_nur" }
        assertTrue(last.campaignSetup.any { reserved in it.cards })
    }

    @Test
    fun `the campaign carries no market`() {
        // Only Galaxy's Most Wanted has one; a market here would offer credits
        // the campaign never awards.
        assertEquals(null, template().market)
    }
}
