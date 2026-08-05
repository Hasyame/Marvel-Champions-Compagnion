package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.PromptType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Rise of Red Skull, played through.
 *
 * This campaign remembers four different things and they are not
 * interchangeable: the attachments the encounter deck keeps gaining, the
 * upgrades the players earn, the allies they rescue, and the allies they leave
 * behind a prison door. The finale reads all four plus a counter, and a
 * carry-over that silently drops one does not show up until the fifth scenario
 * of a campaign — which is a long way to walk to find a typo.
 */
@RunWith(RobolectricTestRunner::class)
class RedSkullCarryOverTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val engine = CampaignEngine()

    private fun template(): CampaignTemplate {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("campaigns/trors.json").bufferedReader()
            .use { it.readText() }
        return json.decodeFromString(CampaignTemplate.serializer(), text).expanded()
    }

    private val heroes = listOf(
        CampaignHero(id = "h1", deckId = "d1", heroCardCode = "01001", name = "Spider-Man"),
        CampaignHero(id = "h2", deckId = "d2", heroCardCode = "01029", name = "She-Hulk"),
    )

    private fun started(t: CampaignTemplate, difficulty: String = "standard") =
        CampaignEvent.CampaignStarted(
            id = "start",
            timestamp = 0,
            templateId = t.id,
            difficulty = difficulty,
            heroes = heroes,
            startScenarioId = t.startScenarioId ?: t.scenarios.first().id,
        )

    private fun won(scenarioId: String, at: Long, answers: AnswerSet = AnswerSet()) =
        CampaignEvent.ScenarioCompleted(
            id = "win-$scenarioId",
            timestamp = at,
            scenarioId = scenarioId,
            victory = true,
            answers = answers,
        )

    /** Crossbones: two attachments seen, one TECH upgrade taken. */
    private fun crossbones(at: Long = 1) = won(
        "s1_crossbones",
        at,
        AnswerSet(
            numbers = mapOf("vp" to 3),
            cardLists = mapOf("experimental" to listOf("04072", "04073")),
            // Both players took the Laser Cannon. A shared list could not hold
            // it twice, which is why this prompt is per hero.
            perHeroCards = mapOf(
                "tech" to mapOf("h1" to listOf("04158"), "h2" to listOf("04158")),
            ),
        ),
    )

    /** Absorbing Man: four delay counters, one Basic condition taken. */
    private fun absorbingMan(at: Long = 2, delay: Int = 4) = won(
        "s2_absorbing_man",
        at,
        AnswerSet(
            numbers = mapOf("vp" to 2, "delayCounters" to delay),
            cardLists = mapOf("conditions" to listOf("04159a")),
        ),
    )

    private fun taskmaster(at: Long = 3) = won(
        "s3_taskmaster",
        at,
        AnswerSet(
            numbers = mapOf("vp" to 1),
            cardLists = mapOf("rescued" to listOf("04097", "04098")),
        ),
    )

    private fun zola(at: Long = 4, prisonStanding: Boolean) = won(
        "s4_zola",
        at,
        AnswerSet(
            numbers = mapOf("vp" to 5),
            booleans = mapOf("prisonInPlay" to prisonStanding),
            cardLists = if (prisonStanding) {
                mapOf("imprisoned" to listOf("04099", "04100"))
            } else {
                emptyMap()
            },
            perHeroBooleans = mapOf("engaged" to mapOf("h1" to true, "h2" to false)),
        ),
    )

    private fun fullRun(prisonStanding: Boolean, difficulty: String = "standard"): CampaignState {
        val t = template()
        return engine.fold(
            t,
            listOf(
                started(t, difficulty),
                crossbones(),
                absorbingMan(),
                taskmaster(),
                zola(prisonStanding = prisonStanding),
            ),
        )
    }

    @Test
    fun `the four kinds of memory stay apart`() {
        val state = fullRun(prisonStanding = true)

        // Rescued and imprisoned are both Taskmaster's captives. Folding them
        // into one list would tell the finale to bar an ally you freed.
        assertEquals(listOf("04072", "04073"), state.cardLists["experimental"].orEmpty())
        assertEquals(listOf("04158"), state.cardLists["tech"].orEmpty())
        assertEquals(listOf("04159a"), state.cardLists["conditions"].orEmpty())
        assertEquals(listOf("04097", "04098"), state.cardLists["rescued"].orEmpty())
        assertEquals(listOf("04099", "04100"), state.cardLists["imprisoned"].orEmpty())
    }

    @Test
    fun `the delay Absorbing Man bought survives to the finale`() {
        val state = fullRun(prisonStanding = false)

        // Three scenarios later this is threat on Red Skull's main scheme.
        assertEquals(4, state.counters["delay"])
    }

    @Test
    fun `the experimental step shows itself once an attachment is recorded`() {
        val t = template()
        val step = t.scenarios.single { it.id == "s2_absorbing_man" }
            .campaignSetup.single { it.showCardList == "experimental" }

        val before = engine.fold(t, listOf(started(t)))
        assertFalse(
            "nothing was recorded yet, so the step must stay hidden",
            ConditionEvaluator.evaluate(step.condition, EvaluationContext(before)),
        )

        val after = engine.fold(t, listOf(started(t), crossbones()))
        assertTrue(
            "two attachments were recorded, so the step must appear",
            ConditionEvaluator.evaluate(step.condition, EvaluationContext(after)),
        )
    }

    @Test
    fun `a prison left standing withholds the improved upgrade`() {
        val t = template()
        val step = t.scenarios.single { it.id == "s5_red_skull" }
            .campaignSetup.single { it.showCardList == "conditions" }

        // The reward is for tearing the prison down, so the step is the
        // negative of the flag, not the flag itself.
        assertFalse(
            ConditionEvaluator.evaluate(step.condition, EvaluationContext(fullRun(prisonStanding = true))),
        )
        assertTrue(
            ConditionEvaluator.evaluate(step.condition, EvaluationContext(fullRun(prisonStanding = false))),
        )
    }

    @Test
    fun `allies left behind are only named when there are some`() {
        val t = template()
        val step = t.scenarios.single { it.id == "s5_red_skull" }
            .campaignSetup.single { it.showCardList == "imprisoned" }

        assertTrue(
            ConditionEvaluator.evaluate(step.condition, EvaluationContext(fullRun(prisonStanding = true))),
        )
        assertFalse(
            "no ally was left behind, so the finale must not raise it",
            ConditionEvaluator.evaluate(step.condition, EvaluationContext(fullRun(prisonStanding = false))),
        )
    }

    @Test
    fun `a choice the campaign compels is marked compulsory, one it offers is not`() {
        val t = template()
        val prompts = { id: String ->
            t.scenarios.single { it.id == id }.onVictory?.prompts.orEmpty()
        }

        // "Each player chooses one of the TECH upgrades" — every later scenario
        // is written assuming the upgrade is in the deck, so the questions page
        // must not file without it.
        val tech = prompts("s1_crossbones").single { it.id == "tech" }
        assertEquals(1, tech.min)
        assertEquals(PromptType.PER_HERO_CARD_SELECT, tech.promptType)

        // "Each player MAY choose one of the Basic Condition upgrades" — here
        // declining is a legal answer, and requiring one would invent a rule.
        assertEquals(null, prompts("s2_absorbing_man").single { it.id == "conditions" }.min)

        // A record of what happened, not a choice: no attachment may have
        // entered play at all.
        assertEquals(null, prompts("s1_crossbones").single { it.id == "experimental" }.min)
    }

    @Test
    fun `the finale keeps the villain stages the difficulty asked for`() {
        val t = template()
        val finale = t.scenarios.single { it.id == "s5_red_skull" }

        // Expert drops stage I and adds stage III rather than adding a third
        // card, which is the mistake this shape invites.
        assertEquals(listOf("04125", "04126"), finale.baseSetup?.villainDeck?.get("standard"))
        assertEquals(listOf("04126", "04127"), finale.baseSetup?.villainDeck?.get("expert"))
    }
}
