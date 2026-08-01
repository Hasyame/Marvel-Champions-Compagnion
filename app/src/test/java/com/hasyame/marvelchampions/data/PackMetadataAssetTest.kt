package com.hasyame.marvelchampions.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackMetadataFileDto
import com.hasyame.marvelchampions.domain.model.PackType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the curated pack table. It is hand-maintained, so a typo in a pack
 * code or type would otherwise only surface as a silently miscategorised pack
 * in the collection screen.
 */
@RunWith(RobolectricTestRunner::class)
class PackMetadataAssetTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun readMetadata(): PackMetadataFileDto {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val text = context.assets.open("pack_metadata.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(PackMetadataFileDto.serializer(), text)
    }

    @Test
    fun `covers every pack marvelcdb currently publishes`() {
        // 61 packs as of 2026-08-01. A new MarvelCDB pack makes this fail,
        // which is the intended reminder to curate its type and wave.
        assertEquals(61, readMetadata().packs.size)
    }

    @Test
    fun `every pack code is unique`() {
        val codes = readMetadata().packs.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `every type is a known PackType and never UNKNOWN`() {
        readMetadata().packs.forEach { pack ->
            val type = PackType.fromName(pack.type)
            assertTrue(
                "${pack.code} has unrecognised type ${pack.type}",
                type != PackType.UNKNOWN,
            )
        }
    }

    @Test
    fun `every pack has a positive wave`() {
        readMetadata().packs.forEach { pack ->
            assertTrue("${pack.code} has wave ${pack.wave}", pack.wave >= 1)
        }
    }

    @Test
    fun `the packs the user owns are all present`() {
        val owned = listOf(
            "core",
            "msm", "magneto", "drs", "wonder_man", "hercules", "gambit", "deadpool",
            "gob", "sm",
            "fne", "aoa", "gmw", "mts",
        )
        val known = readMetadata().packs.map { it.code }.toSet()
        owned.forEach { assertTrue("$it missing from pack_metadata.json", it in known) }
    }

    @Test
    fun `exactly one core set is declared`() {
        val cores = readMetadata().packs.filter { it.type == PackType.CORE.name }
        assertEquals(listOf("core"), cores.map { it.code })
    }
}
