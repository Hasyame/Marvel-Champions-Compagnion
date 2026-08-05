package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.CounterDefinition
import com.hasyame.marvelchampions.domain.campaign.template.DrawDefinition
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import com.hasyame.marvelchampions.domain.campaign.template.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Counters that rise when a card is drawn.
 *
 * Fear No Evil sets a scenario's threat from how many times an environment has
 * come up across the campaign, so the tally has to be kept as it runs. A draw
 * happens during setup, where no effects run, so the draw itself carries the
 * counters it feeds.
 */
class DrawCountsTest {

    private val engine = CampaignEngine()

    private val template = CampaignTemplate(
        id = "t",
        schemaVersion = 1,
        name = LocalizedText(fr = "T"),
        counters = listOf(
            CounterDefinition(id = "racket"),
            CounterDefinition(id = "raft"),
            CounterDefinition(id = "capped", max = 2),
        ),
        scenarios = listOf(
            ScenarioTemplate(
                id = "s1",
                campaignSetup = listOf(
                    SetupStep(
                        draw = DrawDefinition(
                            id = "env",
                            from = listOf("A", "B", "C"),
                            counts = mapOf("A" to "racket", "B" to "raft", "C" to "capped"),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun drew(scenarioId: String, at: Long, vararg codes: String) = CampaignEvent.SetupDrawn(
        id = "d$at",
        timestamp = at,
        scenarioId = scenarioId,
        drawId = "env",
        cardCodes = codes.toList(),
    )

    private fun started() = CampaignEvent.CampaignStarted(
        id = "start",
        timestamp = 0,
        templateId = "t",
        difficulty = "standard",
        heroes = emptyList(),
        startScenarioId = "s1",
    )

    @Test
    fun `a drawn card raises the counter it feeds`() {
        val state = engine.fold(template, listOf(started(), drew("s1", 1, "A")))

        assertEquals(1, state.counter("racket"))
        assertEquals(0, state.counter("raft"))
    }

    @Test
    fun `drawing the same card again raises it again`() {
        // The whole point: three appearances means three threat, so the tally
        // has to accumulate across the campaign rather than reset per scenario.
        val state = engine.fold(
            template,
            listOf(started(), drew("s1", 1, "A"), drew("s2", 2, "A"), drew("s3", 3, "A")),
        )

        assertEquals(3, state.counter("racket"))
    }

    @Test
    fun `a draw of several raises each card's counter once`() {
        val state = engine.fold(template, listOf(started(), drew("s1", 1, "A", "B")))

        assertEquals(1, state.counter("racket"))
        assertEquals(1, state.counter("raft"))
    }

    @Test
    fun `a card with no counter changes nothing`() {
        val plain = template.copy(
            scenarios = listOf(
                ScenarioTemplate(
                    id = "s1",
                    campaignSetup = listOf(
                        SetupStep(draw = DrawDefinition(id = "env", from = listOf("A"))),
                    ),
                ),
            ),
        )

        val state = engine.fold(plain, listOf(started(), drew("s1", 1, "A")))

        assertEquals(0, state.counter("racket"))
    }

    @Test
    fun `the counter's own ceiling still applies`() {
        val state = engine.fold(
            template,
            listOf(started(), drew("s1", 1, "C"), drew("s2", 2, "C"), drew("s3", 3, "C")),
        )

        assertEquals(2, state.counter("capped"))
    }

    @Test
    fun `the drawn cards are still recorded`() {
        // Counting must not cost the draw itself, which the setup reads back.
        val state = engine.fold(template, listOf(started(), drew("s1", 1, "A")))

        assertEquals(listOf("A"), CampaignEngine.drawnCards(state, "s1", "env"))
    }
}
