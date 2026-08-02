package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.TemplateValidator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Validates every campaign shipped in `assets/campaigns/`.
 *
 * These are hand-written, so a typo is the normal failure. Now that they are
 * committed rather than kept on one machine, CI can catch a broken one before
 * it ever reaches a device.
 */
@RunWith(RobolectricTestRunner::class)
class BundledCampaignsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun templates(): List<Pair<String, CampaignTemplate>> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.assets.list("campaigns").orEmpty()
            .filter { it.endsWith(".json") }
            .map { name ->
                val text = context.assets.open("campaigns/$name").bufferedReader()
                    .use { it.readText() }
                name to json.decodeFromString(CampaignTemplate.serializer(), text)
            }
    }

    @Test
    fun `at least one campaign is bundled`() {
        assertTrue(templates().isNotEmpty())
    }

    @Test
    fun `every bundled campaign validates`() {
        templates().forEach { (name, template) ->
            val errors = TemplateValidator.validate(template)
            assertTrue("$name:\n" + errors.joinToString("\n"), errors.isEmpty())
        }
    }

    @Test
    fun `every campaign names a pack so it is only offered when owned`() {
        templates().forEach { (name, template) ->
            assertTrue("$name has no packCode", !template.packCode.isNullOrBlank())
        }
    }

    @Test
    fun `every scenario is reachable from the start`() {
        // A scenario nothing branches to is almost always a typo in a goto.
        templates().forEach { (name, template) ->
            val start = template.startScenarioId ?: template.scenarios.first().id
            val reachable = mutableSetOf(start)
            var changed = true
            while (changed) {
                changed = false
                template.scenarios.filter { it.id in reachable }.forEach { scenario ->
                    listOfNotNull(scenario.onVictory, scenario.onDefeat)
                        .flatMap { it.next }
                        .mapNotNull { it.goto }
                        .forEach { if (reachable.add(it)) changed = true }
                }
            }
            assertEquals(
                "$name has unreachable scenarios",
                template.scenarios.map { it.id }.toSet(),
                reachable,
            )
        }
    }

    @Test
    fun `galaxys most wanted has its five scenarios in order`() {
        val gmw = templates().map { it.second }.single { it.id == "gmw" }

        assertEquals(
            listOf("s1_badoon", "s2_museum", "s3_escape", "s4_nebula", "s5_ronan"),
            gmw.scenarios.map { it.id },
        )
        assertEquals(28, gmw.market?.entries?.size)
    }

    @Test
    fun `scenario blurbs are written for the app, not copied from the book`() {
        // The blurbs were once whole passages lifted from the campaign book —
        // several hundred characters each — while the README claimed the
        // templates held no flavour text. A blurb says where you are in the
        // story; two sentences do that, and anything at paragraph length is
        // somebody else's writing.
        templates().forEach { (name, template) ->
            template.scenarios.forEach { scenario ->
                listOfNotNull(scenario.flavour?.fr, scenario.flavour?.en).forEach { text ->
                    assertTrue(
                        "$name/${scenario.id} blurb is ${text.length} chars, " +
                            "which is passage length rather than a blurb: \"$text\"",
                        text.length <= MAX_FLAVOUR_LENGTH,
                    )
                }
            }
        }
    }

    @Test
    fun `setup steps stay mechanical rather than restating the rules`() {
        // The line this project works to: no rules text. A short blurb is fine
        // — it tells nobody how to play — but a setup step long enough to be a
        // paragraph is a rule copied from the book, and someone without the
        // book must not be able to play from the app alone.
        templates().forEach { (name, template) ->
            template.scenarios.forEach { scenario ->
                scenario.campaignSetup.forEach { step ->
                    listOfNotNull(step.text.fr, step.text.en).forEach { text ->
                        assertTrue(
                            "$name/${scenario.id} setup step reads like a rule: \"$text\"",
                            text.length <= MAX_STEP_LENGTH,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `setup steps point at cards by code rather than naming them in the text`() {
        // A code in the prose would be unreadable and would not translate; the
        // app resolves the code to the card's real name in the reader's
        // language, so the reference belongs in `cards`.
        val codePattern = Regex("""\b\d{5}[a-z]?\b""")
        templates().forEach { (name, template) ->
            template.scenarios.forEach { scenario ->
                scenario.campaignSetup.forEach { step ->
                    listOfNotNull(step.text.fr, step.text.en).forEach { text ->
                        assertTrue(
                            "$name/${scenario.id} has a raw card code in its text: \"$text\"",
                            !codePattern.containsMatchIn(text),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /**
         * Long enough for a mechanical instruction that names a condition from
         * the campaign log, short enough that a paragraph of book text cannot
         * hide in one. A step that needs more than this is doing too much and
         * should be split — which is how the four Galactic Artifacts
         * instructions became four steps rather than one.
         */
        const val MAX_STEP_LENGTH = 140

        /**
         * Two sentences of scene-setting. The copied passages this replaced ran
         * from 383 to 659 characters, so the cap is well clear of anything
         * written for the app and well under anything lifted from the book.
         */
        const val MAX_FLAVOUR_LENGTH = 250
    }
}
