package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.HeroCardStats
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.TemplateValidator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The campaign log: answers recorded in one scenario and read by a later one.
 *
 * These are the mechanics the Galaxy's Most Wanted book needs beyond a single
 * scenario — carried counters, recorded card lists, and a marker for which hero
 * holds something.
 */
class CampaignLogTest {

    private val engine = CampaignEngine()
    private val json = Json { ignoreUnknownKeys = true }

    private val template: CampaignTemplate = json.decodeFromString(
        CampaignTemplate.serializer(),
        """
        {
          "id": "log", "schemaVersion": 1, "name": { "fr": "Log" },
          "difficulties": ["standard", "expert"],
          "counters": [
            { "id": "credits", "scope": "hero", "initial": 0, "min": 0 },
            { "id": "evasion", "scope": "campaign", "initial": 0, "min": 0 },
            { "id": "pincerThreat", "scope": "campaign", "initial": 0, "min": 0 },
            { "id": "powerStone", "scope": "hero", "initial": 0, "min": 0 }
          ],
          "cardLists": [
            { "id": "collection", "scope": "campaign" },
            { "id": "artifacts", "scope": "campaign" }
          ],
          "startScenarioId": "a",
          "scenarios": [
            {
              "id": "a",
              "onVictory": {
                "prompts": [
                  { "id": "artifactCount", "type": "number" },
                  { "id": "names", "type": "cardList" },
                  { "id": "evasionCount", "type": "number" },
                  { "id": "holder", "type": "perHeroBoolean" }
                ],
                "effects": [
                  { "op": "addCounter", "counter": "credits", "from": "artifactCount", "divideBy": 2 },
                  { "op": "addCardsFromAnswer", "cardList": "artifacts", "from": "names" },
                  { "op": "setCounter", "counter": "evasion", "from": "evasionCount" },
                  { "op": "setCounter", "counter": "pincerThreat", "value": 3 },
                  { "op": "subtractCounter", "counter": "pincerThreat", "from": "evasionCount", "min": 0 },
                  { "op": "setHeroCounter", "counter": "powerStone", "from": "holder" }
                ],
                "next": [{ "goto": "b" }]
              },
              "onDefeat": { "next": [{ "goto": "a" }] }
            },
            {
              "id": "b",
              "onVictory": { "next": [{ "end": true }] },
              "onDefeat": {
                "next": [
                  { "end": true, "when": { "difficulty": "expert" } },
                  { "goto": "b" }
                ]
              }
            }
          ]
        }
        """.trimIndent(),
    )

    private val heroes = listOf(
        CampaignHero("h1", null, "a", "Groot"),
        CampaignHero("h2", null, "b", "Rocket"),
    )
    private val stats = mapOf(
        "h1" to HeroCardStats("h1", 14),
        "h2" to HeroCardStats("h2", 8),
    )

    private fun start(difficulty: String = "standard") =
        CampaignEvent.CampaignStarted("e0", 1, "log", difficulty, heroes, "a")

    private fun victory(answers: AnswerSet) =
        CampaignEvent.ScenarioCompleted("e1", 2, "a", victory = true, answers = answers)

    @Test
    fun `the template as written validates`() {
        assertTrue(TemplateValidator.validate(template).toString(), TemplateValidator.validate(template).isEmpty())
    }

    @Test
    fun `for every two of something, one credit`() {
        // 5 artifacts is 2 credits, not 2.5 and not 5.
        val state = engine.fold(
            template,
            listOf(start(), victory(AnswerSet(numbers = mapOf("artifactCount" to 5)))),
            stats,
        )

        assertEquals(2, state.heroCounter("credits", "h1"))
    }

    @Test
    fun `a recorded card list survives into later scenarios`() {
        val state = engine.fold(
            template,
            listOf(
                start(),
                victory(AnswerSet(cardLists = mapOf("names" to listOf("Crystal Ball", "Magical Teapot")))),
            ),
            stats,
        )

        assertEquals(listOf("Crystal Ball", "Magical Teapot"), state.cardLists["artifacts"])
    }

    @Test
    fun `three minus what was recorded, as two small steps`() {
        val two = engine.fold(
            template,
            listOf(start(), victory(AnswerSet(numbers = mapOf("evasionCount" to 2)))),
            stats,
        )
        assertEquals(2, two.counter("evasion"))
        assertEquals(1, two.counter("pincerThreat"))

        // And it floors at zero rather than going negative.
        val many = engine.fold(
            template,
            listOf(start(), victory(AnswerSet(numbers = mapOf("evasionCount" to 9)))),
            stats,
        )
        assertEquals(0, many.counter("pincerThreat"))
    }

    @Test
    fun `a yes-no per hero records which hero holds something`() {
        val state = engine.fold(
            template,
            listOf(
                start(),
                victory(
                    AnswerSet(
                        perHeroBooleans = mapOf("holder" to mapOf("h1" to true, "h2" to false)),
                    ),
                ),
            ),
            stats,
        )

        assertEquals(1, state.heroCounter("powerStone", "h1"))
        assertEquals(0, state.heroCounter("powerStone", "h2"))
    }

    @Test
    fun `a defeat ends the campaign on expert but replays on standard`() {
        val loss = CampaignEvent.ScenarioCompleted("e2", 3, "b", victory = false)
        val reachB = listOf(start(), victory(AnswerSet()))

        val standard = engine.fold(template, reachB + loss, stats)
        assertEquals("b", standard.currentScenarioId)
        assertTrue(!standard.finished)

        val expert = engine.fold(
            template,
            listOf(start("expert"), victory(AnswerSet()), loss),
            stats,
        )
        assertTrue(expert.finished)
    }
}
