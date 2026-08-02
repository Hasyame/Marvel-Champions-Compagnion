package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A run stores its template as JSON so it stays readable if the bundled file
 * changes, which means the template survives a serialize/deserialize round trip
 * that the asset itself never goes through. Anything lost in that round trip
 * disappears from a campaign already in progress.
 */
@RunWith(RobolectricTestRunner::class)
class CampaignRoundTripTest {

    // The same configuration DataModule provides.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun bundled(): CampaignTemplate {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("campaigns/gmw.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(CampaignTemplate.serializer(), text)
    }

    private fun roundTrip(template: CampaignTemplate): CampaignTemplate =
        json.decodeFromString(
            CampaignTemplate.serializer(),
            json.encodeToString(CampaignTemplate.serializer(), template),
        )

    @Test
    fun `the bundled template has victory prompts to begin with`() {
        val scenario = bundled().scenarios.first { it.id == "s1_badoon" }

        assertTrue(
            "no prompts in the asset itself",
            scenario.onVictory?.prompts.orEmpty().isNotEmpty(),
        )
    }

    @Test
    fun `victory prompts survive being stored on a run`() {
        val before = bundled().scenarios.first { it.id == "s1_badoon" }
        val after = roundTrip(bundled()).scenarios.first { it.id == "s1_badoon" }

        assertEquals(
            before.onVictory?.prompts?.map { it.id },
            after.onVictory?.prompts?.map { it.id },
        )
    }

    @Test
    fun `effects and setup survive being stored on a run`() {
        val before = bundled().scenarios.first { it.id == "s1_badoon" }
        val after = roundTrip(bundled()).scenarios.first { it.id == "s1_badoon" }

        assertEquals(before.onVictory?.effects, after.onVictory?.effects)
        assertEquals(before.campaignSetup, after.campaignSetup)
        assertEquals(before.baseSetup, after.baseSetup)
        assertEquals(before.flavour, after.flavour)
    }

    @Test
    fun `the whole template is unchanged by the round trip`() {
        assertEquals(bundled(), roundTrip(bundled()))
    }
}
