package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.PlayDao
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayDaoTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var dao: PlayDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.playDao()
    }

    @After
    fun tearDown() = database.close()

    private fun play(
        id: String,
        hero: String = "Spider-Man",
        heroCode: String = "01001a",
        scenario: String = "Rhino",
        scenarioCode: String = "01097",
        difficulty: String = "standard",
        aspects: String = "Justice",
        won: Boolean = true,
        at: Long = 1L,
    ) = PlayEntity(
        id = id,
        playedAt = at,
        scenarioCode = scenarioCode,
        scenarioName = scenario,
        difficulty = difficulty,
        heroCode = heroCode,
        heroName = hero,
        aspects = aspects,
        won = won,
    )

    @Test
    fun `plays come back newest first`() = runTest {
        dao.insert(play("old", at = 100))
        dao.insert(play("new", at = 300))
        dao.insert(play("middle", at = 200))

        assertEquals(
            listOf("new", "middle", "old"),
            dao.observePlays().first().map { it.id },
        )
    }

    @Test
    fun `re-inserting the same play does not overwrite a recorded game`() = runTest {
        // A double tap or a retry must not rewrite history.
        dao.insert(play("p1", won = true))
        dao.insert(play("p1", won = false, hero = "Iron Man"))

        val stored = dao.observePlays().first().single()
        assertTrue(stored.won)
        assertEquals("Spider-Man", stored.heroName)
    }

    @Test
    fun `win rates are grouped by hero and by scenario`() = runTest {
        dao.insert(play("a", hero = "Spider-Man", heroCode = "h1", won = true))
        dao.insert(play("b", hero = "Spider-Man", heroCode = "h1", won = false))
        dao.insert(play("c", hero = "She-Hulk", heroCode = "h2", won = true))

        val byHero = dao.observeByHero().first().associateBy { it.key }
        assertEquals(2, byHero.getValue("Spider-Man").played)
        assertEquals(1, byHero.getValue("Spider-Man").won)
        assertEquals(1, byHero.getValue("She-Hulk").won)

        // All three used the same scenario fixture.
        val byScenario = dao.observeByScenario().first().single()
        assertEquals(3, byScenario.played)
        assertEquals(2, byScenario.won)
    }

    @Test
    fun `grouping follows the code, and shows the name it was recorded under`() = runTest {
        // Two rows for one hero whose name changed between them — a
        // translation switch, say. They are one hero, not two.
        dao.insert(play("a", hero = "Spider-Man", heroCode = "h1"))
        dao.insert(play("b", hero = "L'Araignée", heroCode = "h1"))

        assertEquals(1, dao.observeByHero().first().size)
        assertEquals(2, dao.observeByHero().first().single().played)
    }

    @Test
    fun `a play is only marked as reported once it has been`() = runTest {
        dao.insert(play("p1"))
        assertFalse(dao.getPlay("p1")!!.reportedToBgg)

        dao.markReported("p1")
        assertTrue(dao.getPlay("p1")!!.reportedToBgg)
    }

    @Test
    fun `deleting a play removes it from the statistics too`() = runTest {
        dao.insert(play("a", won = true))
        dao.insert(play("b", won = false))

        dao.delete("b")

        assertEquals(1, dao.observeCount().first())
        assertEquals(1, dao.observeByHero().first().single().won)
    }
}
