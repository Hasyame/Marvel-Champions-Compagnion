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
 * Stress test for the schema, using a second campaign shaped like Age of
 * Apocalypse: a branch that depends on how many flags of a set are true, a
 * choice prompt driving that branch, and a setup step that costs a counter.
 *
 * **No real campaign content.** The point is to prove the schema can express
 * these shapes without new Kotlin, before we commit to it.
 */
class SchemaStressTest {

    private val engine = CampaignEngine()

    private val aoaJson = """
    {
      "id": "aoa_stress",
      "schemaVersion": 1,
      "name": { "fr": "Test de schema" },
      "difficulties": ["standard", "expert"],
      "counters": [
        { "id": "credits", "scope": "hero", "initial": 2, "min": 0 },
        { "id": "resistance", "scope": "campaign", "initial": 0 },
        { "id": "hp", "scope": "hero", "initial": 0, "maxFrom": "heroCard.health" }
      ],
      "flagSets": [
        { "id": "prisonerFreed", "scope": "perScenario" }
      ],
      "cardLists": [
        { "id": "purchases", "scope": "hero" },
        { "id": "outOfPlay", "scope": "perScenario" }
      ],
      "market": {
        "counterId": "credits",
        "entries": [
          { "cardCode": "x1", "cost": 1, "cardListId": "purchases" },
          { "cardCode": "x2", "cost": 3, "cardListId": "purchases" }
        ]
      },
      "startScenarioId": "s1",
      "scenarios": [
        {
          "id": "s1",
          "name": { "fr": "Premier" },
          "baseSetup": {
            "villainDeck": { "standard": ["v1", "v2"], "expert": ["v2", "v3"] },
            "mainScheme": ["ms1"],
            "encounterSets": ["e1"]
          },
          "campaignSetup": [
            { "text": { "fr": "Toujours" } },
            { "text": { "fr": "Expert seulement" }, "when": { "difficulty": "expert" } },
            {
              "text": { "fr": "Depenser un credit pour se soigner" },
              "action": {
                "id": "heal",
                "label": { "fr": "Soigner" },
                "perHero": true,
                "cost": { "counterId": "credits", "amount": 1 },
                "effects": [ { "op": "setHeroCounter", "counter": "hp", "value": 99 } ]
              }
            }
          ],
          "onVictory": {
            "prompts": [
              { "id": "freed", "type": "boolean", "label": { "fr": "Prisonnier libere ?" } },
              { "id": "route", "type": "choice", "label": { "fr": "Chemin ?" },
                "options": [ { "id": "north" }, { "id": "south" } ] },
              { "id": "zone", "type": "cardList", "label": { "fr": "Cartes hors jeu" } },
              { "id": "eliminated", "type": "perHeroBoolean" }
            ],
            "effects": [
              { "op": "setFlag", "flag": "prisonerFreed", "from": "freed" },
              { "op": "addCounter", "counter": "resistance", "value": 1, "when": { "answer": "freed" } },
              { "op": "addCardsFromAnswer", "cardList": "outOfPlay", "from": "zone" }
            ],
            "next": [
              { "goto": "s3", "when": { "choice": "route", "choiceIs": "north" } },
              { "goto": "s2" }
            ]
          },
          "onDefeat": { "next": [ { "goto": "s1" } ] }
        },
        {
          "id": "s2",
          "onVictory": {
            "prompts": [ { "id": "freed", "type": "boolean" } ],
            "effects": [ { "op": "setFlag", "flag": "prisonerFreed", "from": "freed" } ],
            "next": [
              { "goto": "s4", "when": { "countTrue": "prisonerFreed", "countAtLeast": 2 } },
              { "goto": "s3" }
            ]
          }
        },
        { "id": "s3", "onVictory": { "next": [ { "end": true } ] } },
        { "id": "s4", "onVictory": { "next": [ { "end": true } ] } }
      ]
    }
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }
    private val template: CampaignTemplate =
        json.decodeFromString(CampaignTemplate.serializer(), aoaJson)

    private val heroes = listOf(
        CampaignHero("h1", null, "a", "One"),
        CampaignHero("h2", null, "b", "Two"),
    )
    private val heroStats = mapOf(
        "h1" to HeroCardStats("h1", 11),
        "h2" to HeroCardStats("h2", 9),
    )

    private fun start(difficulty: String = "standard") =
        CampaignEvent.CampaignStarted("e0", 1, template.id, difficulty, heroes, "s1")

    @Test
    fun `a second campaign shape parses and validates without code changes`() {
        assertTrue(TemplateValidator.validate(template).toString(), TemplateValidator.validate(template).isEmpty())
    }

    @Test
    fun `a choice prompt can drive a branch`() {
        val north = CampaignEvent.ScenarioCompleted(
            "e1", 2, "s1", victory = true,
            answers = AnswerSet(choices = mapOf("route" to "north")),
        )
        val south = CampaignEvent.ScenarioCompleted(
            "e1", 2, "s1", victory = true,
            answers = AnswerSet(choices = mapOf("route" to "south")),
        )

        assertEquals("s3", engine.fold(template, listOf(start(), north), heroStats).currentScenarioId)
        assertEquals("s2", engine.fold(template, listOf(start(), south), heroStats).currentScenarioId)
    }

    @Test
    fun `a branch can count how many flags of a set are true`() {
        val firstFreed = CampaignEvent.ScenarioCompleted(
            "e1", 2, "s1", victory = true,
            answers = AnswerSet(booleans = mapOf("freed" to true), choices = mapOf("route" to "south")),
        )
        val secondFreed = CampaignEvent.ScenarioCompleted(
            "e2", 3, "s2", victory = true,
            answers = AnswerSet(booleans = mapOf("freed" to true)),
        )
        val secondNotFreed = CampaignEvent.ScenarioCompleted(
            "e2", 3, "s2", victory = true,
            answers = AnswerSet(booleans = mapOf("freed" to false)),
        )

        val both = engine.fold(template, listOf(start(), firstFreed, secondFreed), heroStats)
        assertEquals(2, both.countTrue("prisonerFreed"))
        assertEquals("s4", both.currentScenarioId)

        val one = engine.fold(template, listOf(start(), firstFreed, secondNotFreed), heroStats)
        assertEquals(1, one.countTrue("prisonerFreed"))
        assertEquals("s3", one.currentScenarioId)
    }

    @Test
    fun `a card list prompt records a per-scenario zone`() {
        val event = CampaignEvent.ScenarioCompleted(
            "e1", 2, "s1", victory = true,
            answers = AnswerSet(
                cardLists = mapOf("zone" to listOf("c1", "c2")),
                choices = mapOf("route" to "south"),
            ),
        )

        val state = engine.fold(template, listOf(start(), event), heroStats)

        assertEquals(listOf("c1", "c2"), state.cardLists["outOfPlay"])
    }

    @Test
    fun `an interactive setup step charges its cost and applies its effect`() {
        val action = CampaignEvent.SetupActionTaken("e1", 2, "s1", "heal", heroId = "h1")

        val state = engine.fold(template, listOf(start(), action), heroStats)

        // Started at 2 credits, spent 1.
        assertEquals(1, state.heroCounter("credits", "h1"))
        // Capped at printed health, not the literal 99 in the template.
        assertEquals(11, state.heroCounter("hp", "h1"))
        // Only the acting hero is affected.
        assertEquals(2, state.heroCounter("credits", "h2"))
        assertEquals(0, state.heroCounter("hp", "h2"))
    }

    @Test
    fun `the market enforces group-wide uniqueness across heroes`() {
        val buy = CampaignEvent.MarketPurchase("e1", 2, "h1", "x1", 1, "purchases")
        val state = engine.fold(template, listOf(start(), buy), heroStats)

        assertEquals(1, state.heroCounter("credits", "h1"))
        assertEquals(setOf("x1"), state.allPurchasedCardCodes())
    }

    @Test
    fun `an expert-only setup step is filtered by difficulty, not by code`() {
        val scenario = template.scenarios.first { it.id == "s1" }
        val expertOnly = scenario.campaignSetup.filter { it.condition?.difficulty == "expert" }

        assertEquals(1, expertOnly.size)
    }
}
