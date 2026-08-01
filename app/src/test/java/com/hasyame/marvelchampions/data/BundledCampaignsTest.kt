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
    fun `bundled campaigns carry no long prose`() {
        // The rule this project works to: mechanics, not rules text. A long
        // sentence in a setup step or a flavour field is the signal that book
        // text has crept in.
        templates().forEach { (name, template) ->
            template.scenarios.forEach { scenario ->
                assertTrue(
                    "$name/${scenario.id} has flavour text",
                    scenario.flavour == null,
                )
                scenario.campaignSetup.forEach { step ->
                    listOfNotNull(step.text.fr, step.text.en).forEach { text ->
                        assertTrue(
                            "$name/${scenario.id} setup step looks like prose: \"$text\"",
                            text.length <= MAX_STEP_LENGTH,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** Long enough for a mechanical instruction, short enough to exclude a rule. */
        const val MAX_STEP_LENGTH = 80
    }
}
