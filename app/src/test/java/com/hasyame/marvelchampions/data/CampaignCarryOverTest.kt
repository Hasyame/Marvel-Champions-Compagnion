package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What one scenario records has to change what the next one tells the player to
 * do. That linkage is the whole reason the campaign log exists, and it is
 * invisible until played through several scenarios, so it is asserted here
 * against the campaign actually shipped rather than a fixture.
 */
@RunWith(RobolectricTestRunner::class)
class CampaignCarryOverTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val engine = CampaignEngine()

    private fun gmw(): CampaignTemplate {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("campaigns/gmw.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(CampaignTemplate.serializer(), text)
    }

    private val heroes = listOf(
        CampaignHero(id = "h1", deckId = "d1", heroCardCode = "01001", name = "Spider-Man"),
    )

    private fun started(template: CampaignTemplate) = CampaignEvent.CampaignStarted(
        id = "start",
        timestamp = 0,
        templateId = template.id,
        difficulty = "standard",
        heroes = heroes,
        startScenarioId = template.startScenarioId ?: template.scenarios.first().id,
    )

    /** A victory for [scenarioId], with the Headhunter box marked or not. */
    private fun won(scenarioId: String, headhunter: Boolean, at: Long) =
        CampaignEvent.ScenarioCompleted(
            id = "win-$scenarioId",
            timestamp = at,
            scenarioId = scenarioId,
            victory = true,
            answers = AnswerSet(
                numbers = mapOf("vp" to 0, "artifactCount" to 0, "evasionCount" to 0),
                booleans = mapOf("headhunterInVictoryDisplay" to headhunter),
            ),
            elapsedMillis = 1000,
        )

    /** The campaign setup steps the app would actually print for [scenarioId]. */
    private fun visibleSetup(
        template: CampaignTemplate,
        state: CampaignState,
        scenarioId: String,
    ) = template.scenarios.first { it.id == scenarioId }.campaignSetup
        .filter { ConditionEvaluator.evaluate(it.condition, EvaluationContext(state, scenarioId)) }

    /** The cards those steps name. */
    private fun setupCards(
        template: CampaignTemplate,
        state: CampaignState,
        scenarioId: String,
    ): List<String> = visibleSetup(template, state, scenarioId).flatMap { it.cards }

    @Test
    fun `the headhunter ladder grows one rung per scenario it was defeated in`() {
        val template = gmw()

        // Never defeated: no rung is shown, however far into the campaign.
        val never = engine.fold(
            template,
            listOf(
                started(template),
                won("s1_badoon", headhunter = false, at = 1),
                won("s2_museum", headhunter = false, at = 2),
                won("s3_escape", headhunter = false, at = 3),
                won("s4_nebula", headhunter = false, at = 4),
            ),
        )
        assertEquals(0, never.countTrue("headhunterDefeated"))
        assertEquals(
            emptyList<String>(),
            setupCards(template, never, "s5_ronan").filter { it in LADDER },
        )

        // Defeated in the first two scenarios only: two rungs by scenario 4,
        // and still two by scenario 5. Each card is named once, not once per
        // scenario it was recorded in.
        val twice = engine.fold(
            template,
            listOf(
                started(template),
                won("s1_badoon", headhunter = true, at = 1),
                won("s2_museum", headhunter = true, at = 2),
                won("s3_escape", headhunter = false, at = 3),
            ),
        )
        assertEquals(2, twice.countTrue("headhunterDefeated"))
        assertEquals(
            listOf(ON_THE_HUNT, DEAD_TO_RIGHTS),
            setupCards(template, twice, "s4_nebula").filter { it in LADDER },
        )

        // Every scenario: all four rungs by the finale.
        val always = engine.fold(
            template,
            listOf(
                started(template),
                won("s1_badoon", headhunter = true, at = 1),
                won("s2_museum", headhunter = true, at = 2),
                won("s3_escape", headhunter = true, at = 3),
                won("s4_nebula", headhunter = true, at = 4),
            ),
        )
        assertEquals(4, always.countTrue("headhunterDefeated"))
        assertEquals(
            listOf(ON_THE_HUNT, DEAD_TO_RIGHTS, HENCHMAN, FUGITIVE_RECOVERY),
            setupCards(template, always, "s5_ronan").filter { it in LADDER },
        )
    }

    @Test
    fun `the ladder grows one rung at a time as the campaign is played`() {
        val template = gmw()
        val log = mutableListOf<CampaignEvent>(started(template))

        // Scenario 1 has nothing recorded before it, so it never shows a rung.
        assertEquals(
            emptyList<String>(),
            setupCards(template, engine.fold(template, log), "s1_badoon").filter { it in LADDER },
        )

        val expected = listOf(
            "s2_museum" to listOf(ON_THE_HUNT),
            "s3_escape" to listOf(ON_THE_HUNT, DEAD_TO_RIGHTS),
            "s4_nebula" to listOf(ON_THE_HUNT, DEAD_TO_RIGHTS, HENCHMAN),
            "s5_ronan" to listOf(ON_THE_HUNT, DEAD_TO_RIGHTS, HENCHMAN, FUGITIVE_RECOVERY),
        )
        val played = listOf("s1_badoon", "s2_museum", "s3_escape", "s4_nebula")

        played.forEachIndexed { index, scenarioId ->
            log += won(scenarioId, headhunter = true, at = index + 1L)
            val (nextId, cards) = expected[index]
            assertEquals(
                "after winning $scenarioId",
                cards,
                setupCards(template, engine.fold(template, log), nextId).filter { it in LADDER },
            )
        }
    }

    @Test
    fun `replaying a scenario after defeat does not double-count the headhunter`() {
        val template = gmw()
        // A defeat sends the player back to the same scenario. Winning it on the
        // second attempt is still one box, not two.
        val state = engine.fold(
            template,
            listOf(
                started(template),
                CampaignEvent.ScenarioCompleted(
                    id = "loss",
                    timestamp = 1,
                    scenarioId = "s1_badoon",
                    victory = false,
                    elapsedMillis = 1000,
                ),
                won("s1_badoon", headhunter = true, at = 2),
            ),
        )
        assertEquals(1, state.countTrue("headhunterDefeated"))
    }

    @Test
    fun `only the artifacts recorded in scenario 3 change scenario 4's setup`() {
        val template = gmw()
        val state = engine.fold(
            template,
            listOf(
                started(template),
                won("s1_badoon", headhunter = false, at = 1),
                won("s2_museum", headhunter = false, at = 2),
                CampaignEvent.ScenarioCompleted(
                    id = "win-s3",
                    timestamp = 3,
                    scenarioId = "s3_escape",
                    victory = true,
                    answers = AnswerSet(
                        numbers = mapOf("vp" to 0, "artifactCount" to 2),
                        booleans = mapOf("headhunterInVictoryDisplay" to false),
                        cardLists = mapOf("artifactNames" to listOf(EGG, TEAPOT)),
                    ),
                    elapsedMillis = 1000,
                ),
            ),
        )

        val cards = setupCards(template, state, "s4_nebula")
        assertEquals(listOf(EGG, TEAPOT), cards.filter { it in ARTIFACTS })
    }

    @Test
    fun `a step about recorded cards is hidden when nothing was recorded`() {
        val template = gmw()

        // Nothing put into The Collection and no artifacts claimed: neither
        // scenario should tell the player to act on an empty list.
        val nothing = engine.fold(
            template,
            listOf(
                started(template),
                won("s1_badoon", headhunter = false, at = 1),
                won("s2_museum", headhunter = false, at = 2),
                won("s3_escape", headhunter = false, at = 3),
            ),
        )
        assertTrue(
            "The Collection is empty, so scenario 3 must not mention removing cards",
            visibleSetup(template, nothing, "s3_escape").none { it.showCardList == "collection" },
        )
        assertTrue(
            "no artifacts were recorded, so scenario 4 must not mention shuffling them",
            visibleSetup(template, nothing, "s4_nebula").none { it.showCardList == "artifacts" },
        )

        // With artifacts recorded, the step comes back.
        val withArtifacts = engine.fold(
            template,
            listOf(
                started(template),
                CampaignEvent.ScenarioCompleted(
                    id = "win-s3",
                    timestamp = 1,
                    scenarioId = "s3_escape",
                    victory = true,
                    answers = AnswerSet(
                        numbers = mapOf("vp" to 0, "artifactCount" to 1),
                        cardLists = mapOf("artifactNames" to listOf(CRYSTAL_BALL)),
                    ),
                    elapsedMillis = 1000,
                ),
            ),
        )
        assertTrue(
            visibleSetup(template, withArtifacts, "s4_nebula").any { it.showCardList == "artifacts" },
        )
    }

    @Test
    fun `the Power Stone step only appears once a hero is recorded holding it`() {
        val template = gmw()
        val upToRonan = listOf(
            started(template),
            won("s1_badoon", headhunter = false, at = 1),
            won("s2_museum", headhunter = false, at = 2),
            won("s3_escape", headhunter = false, at = 3),
        )

        fun holdsPowerStone(state: CampaignState) =
            visibleSetup(template, state, "s5_ronan").any { it.showHeroesWith == "powerStone" }

        val noHolder = engine.fold(
            template,
            upToRonan + won("s4_nebula", headhunter = false, at = 4),
        )
        assertTrue("nobody holds it, so nothing should be dealt", !holdsPowerStone(noHolder))

        val holder = engine.fold(
            template,
            upToRonan + CampaignEvent.ScenarioCompleted(
                id = "win-s4",
                timestamp = 4,
                scenarioId = "s4_nebula",
                victory = true,
                answers = AnswerSet(
                    numbers = mapOf("vp" to 0, "evasionCount" to 0),
                    perHeroBooleans = mapOf("powerStoneHolder" to mapOf("h1" to true)),
                ),
                elapsedMillis = 1000,
            ),
        )
        assertTrue("h1 was recorded holding it", holdsPowerStone(holder))
    }

    @Test
    fun `no setup step asks the player to check the campaign log itself`() {
        // A step is only shown once its condition holds, so restating the
        // condition in the text makes the app look as though it never read the
        // log — which is exactly how this was first reported.
        val asksTheReader = listOf(
            "boxes are marked", "or more boxes", "cases sont cochées", "case ou plus",
        )
        gmw().scenarios.forEach { scenario ->
            scenario.campaignSetup.forEach { step ->
                listOfNotNull(step.text.en, step.text.fr).forEach { text ->
                    asksTheReader.forEach { phrase ->
                        assertTrue(
                            "${scenario.id} still asks the reader to check: $text",
                            !text.contains(phrase, ignoreCase = true),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val ON_THE_HUNT = "16184"
        const val DEAD_TO_RIGHTS = "16185"
        const val HENCHMAN = "16186"
        const val FUGITIVE_RECOVERY = "16187"
        val LADDER = setOf(ON_THE_HUNT, DEAD_TO_RIGHTS, HENCHMAN, FUGITIVE_RECOVERY)

        const val EGG = "16127"
        const val TEAPOT = "16128"
        const val STONE = "16129"
        const val CRYSTAL_BALL = "16130"
        val ARTIFACTS = setOf(EGG, TEAPOT, STONE, CRYSTAL_BALL)
    }
}
