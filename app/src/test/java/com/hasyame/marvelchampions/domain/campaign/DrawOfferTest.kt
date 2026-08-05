package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.DrawDefinition
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import com.hasyame.marvelchampions.domain.campaign.template.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A draw that offers rather than decides.
 *
 * Fear No Evil deals two environments and keeps the one the table picks; the
 * other goes back into the pool and can come up again. That asymmetry is the
 * whole point — an offer spends nothing until a choice is made.
 */
class DrawOfferTest {

    private val engine = CampaignEngine()

    private val draw = DrawDefinition(
        id = "env",
        from = listOf("A", "B", "C"),
        excluding = "used",
        offer = 2,
    )

    private val template = CampaignTemplate(
        id = "t",
        schemaVersion = 1,
        name = LocalizedText(fr = "T"),
        scenarios = listOf(
            ScenarioTemplate(id = "s1", campaignSetup = listOf(SetupStep(draw = draw))),
        ),
    )

    private fun started() = CampaignEvent.CampaignStarted(
        id = "start",
        timestamp = 0,
        templateId = "t",
        difficulty = "standard",
        heroes = emptyList(),
        startScenarioId = "s1",
    )

    private fun offered(vararg codes: String) = CampaignEvent.SetupDrawn(
        id = "offer",
        timestamp = 1,
        scenarioId = "s1",
        drawId = "env",
        cardCodes = codes.toList(),
    )

    private fun kept(code: String) = CampaignEvent.SetupChoiceMade(
        id = "choice",
        timestamp = 2,
        scenarioId = "s1",
        drawId = "env",
        cardCode = code,
    )

    @Test
    fun `before a choice both offered cards are on the table`() {
        val state = engine.fold(template, listOf(started(), offered("A", "B")))

        assertEquals(listOf("A", "B"), CampaignEngine.drawnCards(state, "s1", "env"))
    }

    @Test
    fun `the kept card replaces the offer`() {
        // Everything downstream reads one card and never learns a choice
        // happened, which is what keeps conditions and effects unchanged.
        val state = engine.fold(template, listOf(started(), offered("A", "B"), kept("B")))

        assertEquals(listOf("B"), CampaignEngine.drawnCards(state, "s1", "env"))
    }

    @Test
    fun `the card not kept is still in the pool`() {
        // An offer spends nothing. Only what a scenario strikes leaves the pool,
        // so the card passed over must be able to come up again.
        val afterChoice = engine.fold(template, listOf(started(), offered("A", "B"), kept("B")))

        assertTrue("A" in CampaignEngine.drawPool(draw, afterChoice))
    }

    @Test
    fun `a struck card stays out while an offered one does not`() {
        val spent = CampaignState(cardLists = mapOf("used" to listOf("B")))
        val pool = CampaignEngine.drawPool(draw, spent)

        assertEquals(listOf("A", "C"), pool)
    }

    @Test
    fun `a draw with no offer still decides for itself`() {
        val decided = DrawDefinition(id = "env", from = listOf("A"))
        val plain = template.copy(
            scenarios = listOf(
                ScenarioTemplate(id = "s1", campaignSetup = listOf(SetupStep(draw = decided))),
            ),
        )

        val state = engine.fold(plain, listOf(started(), offered("A")))

        assertEquals(0, decided.offer)
        assertEquals(listOf("A"), CampaignEngine.drawnCards(state, "s1", "env"))
    }
}
