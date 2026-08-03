package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import com.hasyame.marvelchampions.domain.campaign.template.SetupStep
import com.hasyame.marvelchampions.domain.campaign.template.TemplateValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Setup steps shared between scenarios.
 *
 * A campaign repeats itself, and copies drift. Writing the shared part once and
 * including it means a correction lands everywhere it belongs rather than
 * everywhere somebody remembered.
 */
class SetupFragmentTest {

    private fun step(text: String) = SetupStep(text = LocalizedText(fr = text, en = text))

    private fun template(scenarioSetup: List<SetupStep>, fragments: Map<String, List<SetupStep>>) =
        CampaignTemplate(
            id = "t",
            schemaVersion = 1,
            name = LocalizedText(fr = "T"),
            setupFragments = fragments,
            scenarios = listOf(ScenarioTemplate(id = "s1", campaignSetup = scenarioSetup)),
        )

    @Test
    fun `an include is replaced by the fragment, in place`() {
        val expanded = template(
            scenarioSetup = listOf(step("before"), SetupStep(include = "shared"), step("after")),
            fragments = mapOf("shared" to listOf(step("one"), step("two"))),
        ).expanded()

        assertEquals(
            listOf("before", "one", "two", "after"),
            expanded.scenarios.single().campaignSetup.map { it.text.fr },
        )
    }

    @Test
    fun `expanding twice changes nothing`() {
        // Every decode path expands, and a stored run may be expanded already.
        val once = template(
            scenarioSetup = listOf(SetupStep(include = "shared")),
            fragments = mapOf("shared" to listOf(step("one"))),
        ).expanded()

        assertEquals(once.scenarios, once.expanded().scenarios)
    }

    @Test
    fun `an include naming nothing is reported rather than silently dropped`() {
        val errors = TemplateValidator.validate(
            template(
                scenarioSetup = listOf(SetupStep(include = "missing")),
                fragments = mapOf("shared" to listOf(step("one"))),
            ),
        )

        assertTrue(
            "a typo in an include must not just remove the step: $errors",
            errors.any { "missing" in it.toString() },
        )
    }

    @Test
    fun `steps inside a fragment are validated like any other`() {
        // The rules must not be dodgeable by moving a step into a fragment.
        val errors = TemplateValidator.validate(
            template(
                scenarioSetup = listOf(SetupStep(include = "shared")),
                fragments = mapOf(
                    "shared" to listOf(
                        SetupStep(
                            text = LocalizedText(fr = "draw"),
                            draw = com.hasyame.marvelchampions.domain.campaign.template.DrawDefinition(
                                id = "d",
                                from = listOf("1"),
                                excluding = "nosuchlist",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(
            "a bad card list inside a fragment must still be caught: $errors",
            errors.any { "nosuchlist" in it.toString() },
        )
    }
}
