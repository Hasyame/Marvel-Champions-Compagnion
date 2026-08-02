package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.domain.model.CardFilter
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.search.CardQueryBuilder
import com.hasyame.marvelchampions.domain.search.SearchNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs the statements [CardQueryBuilder] produces against a real database.
 *
 * The builder's own tests check the SQL text; only executing it proves the
 * syntax is valid and the bindings line up.
 */
@RunWith(RobolectricTestRunner::class)
class CardQueryExecutionTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: CardDao

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.cardDao()
        dao.insertAll(
            listOf(
                card("01001a", "Spider-Man", "hero", "hero", "core", cost = null, traits = "Avenger."),
                card("01021", "Frappe Gamma", "event", "aggression", "core", cost = 3, traits = "Attaque."),
                card("14001", "Groot", "hero", "hero", "gmw", cost = null, traits = "Gardien."),
                card("14020", "Coup de Racine", "event", "justice", "gmw", cost = 1, traits = "Attaque."),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

@Test
    fun `an untranslated card appears in French search rather than vanishing`() = runTest {
        // MarvelCDB has not translated every pack, and matching the locale
        // exactly hid those cards from search altogether — which reads as a
        // broken database rather than a missing translation.
        dao.insertAll(
            listOf(
                card("45001", "Untranslated Ally", "ally", "leadership", "fne", 2, "Avenger."),
            ),
        )

        assertEquals(5, run(CardFilter()).size)
        assertEquals(
            listOf("45001"),
            run(CardFilter(query = "untranslated")).map { it.code },
        )
    }

    @Test
    fun `a translated card is returned once, not once per language`() = runTest {
        // The fallback row is used only when no translated row exists. If that
        // ever stops holding, every card in the database doubles.
        dao.insertAll(
            listOf(
                englishCard("01021", "Gamma Slam", "event", "aggression", "core"),
            ),
        )

        val results = run(CardFilter())
        assertEquals(4, results.size)
        assertEquals(
            "the French row wins for a card that has one",
            "Frappe Gamma",
            results.first { it.code == "01021" }.name,
        )
    }

    private suspend fun run(
        filter: CardFilter,
        owned: Set<String> = emptySet(),
    ): List<CardEntity> {
        val query = CardQueryBuilder.build(filter, CardLocale.FRENCH, owned)
        return dao.queryCards(SimpleSQLiteQuery(query.sql, query.args.toTypedArray()))
    }

    @Test
    fun `an empty filter returns everything in the locale`() = runTest {
        assertEquals(4, run(CardFilter()).size)
    }

    @Test
    fun `text search and a pack filter combine`() = runTest {
        val results = run(CardFilter(query = "coup", packCodes = setOf("gmw")))

        assertEquals(listOf("14020"), results.map { it.code })
    }

    @Test
    fun `owned only hides packs the user does not have`() = runTest {
        val results = run(CardFilter(ownedOnly = true), owned = setOf("core"))

        assertEquals(setOf("core"), results.map { it.packCode }.toSet())
    }

    @Test
    fun `owned only with an empty collection returns nothing and does not crash`() = runTest {
        assertTrue(run(CardFilter(ownedOnly = true), owned = emptySet()).isEmpty())
    }

    @Test
    fun `cost bounds filter on the printed cost`() = runTest {
        val results = run(CardFilter(minCost = 1, maxCost = 1))

        assertEquals(listOf("14020"), results.map { it.code })
    }

    @Test
    fun `a trait filter matches accent-insensitively`() = runTest {
        val results = run(CardFilter(traits = setOf("Gardien")))

        assertEquals(listOf("14001"), results.map { it.code })
    }

    @Test
    fun `every filter dimension at once produces valid sql`() = runTest {
        // The point of this one is that it runs at all.
        val results = run(
            CardFilter(
                query = "frappe",
                packCodes = setOf("core"),
                typeCodes = setOf("event"),
                factionCodes = setOf("aggression"),
                traits = setOf("Attaque"),
                minCost = 1,
                maxCost = 5,
                ownedOnly = true,
            ),
            owned = setOf("core"),
        )

        assertEquals(listOf("01021"), results.map { it.code })
    }

private fun englishCard(
        code: String,
        name: String,
        typeCode: String,
        factionCode: String,
        packCode: String,
    ) = card(code, name, typeCode, factionCode, packCode, cost = null, traits = null)
        .copy(locale = "en")

    private fun card(
        code: String,
        name: String,
        typeCode: String,
        factionCode: String,
        packCode: String,
        cost: Int?,
        traits: String?,
    ) = CardEntity(
        code = code,
        locale = "fr",
        name = name,
        realName = name,
        position = 1,
        quantity = 1,
        packCode = packCode,
        packName = packCode,
        packLegacy = false,
        typeCode = typeCode,
        typeName = typeCode,
        factionCode = factionCode,
        factionName = factionCode,
        cost = cost,
        traits = traits,
        searchName = SearchNormalizer.normalize(name),
        searchText = "",
        searchTraits = SearchNormalizer.normalize(traits),
    )
}
