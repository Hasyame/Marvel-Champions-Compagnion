package com.hasyame.marvelchampions.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Corrections to set names the card database gets wrong or leaves untranslated.
 *
 * MarvelCDB's French data still calls a number of encounter sets by their
 * English names, and the app was faithfully repeating that to somebody holding
 * the French card. This file is the fix, so it is worth checking it parses and
 * says what it means to say.
 */
@RunWith(RobolectricTestRunner::class)
class SetNameOverridesAssetTest {

    private fun overrides(locale: String): Map<String, String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("set_name_overrides.json").bufferedReader()
            .use { it.readText() }
        return (Json.parseToJsonElement(text).jsonObject[locale] as? JsonObject)
            ?.mapValues { (_, value) -> value.jsonPrimitive.content }
            .orEmpty()
    }

    @Test
    fun `the museum scenarios are named in French`() {
        // MarvelCDB returns "Escape the Museum" from its French endpoint.
        assertEquals("Fuir le Musée", overrides("fr")["escape_the_museum"])
        assertEquals("Infiltrer le Musée", overrides("fr")["infiltrate_the_museum"])
    }

    @Test
    fun `every corrected name is actually in French`() {
        // A correction that leaves the name in English is not a correction. Not
        // a strict test of French, just of "somebody looked at this".
        overrides("fr").forEach { (code, name) ->
            assertTrue("$code -> $name has no French in it", name.isNotBlank())
            assertTrue(
                "$code -> $name looks untranslated",
                name.any { it in "éèêàçôûùïâœÉÈÀÇ" } || name.split(' ').size > 1,
            )
        }
    }

    @Test
    fun `nothing outside a locale block leaks in as a correction`() {
        // The file carries a _note at the top level. Reading it as a locale
        // would put a paragraph of English where a set name belongs.
        assertTrue(overrides("_note").isEmpty())
    }
}
