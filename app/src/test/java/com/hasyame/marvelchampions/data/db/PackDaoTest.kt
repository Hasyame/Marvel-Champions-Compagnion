package com.hasyame.marvelchampions.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hasyame.marvelchampions.data.db.dao.OwnedPackDao
import com.hasyame.marvelchampions.data.db.dao.PackDao
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.db.entity.PackTranslationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PackDaoTest {

    private lateinit var database: MarvelChampionsDatabase
    private lateinit var packDao: PackDao
    private lateinit var ownedPackDao: OwnedPackDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarvelChampionsDatabase::class.java,
        ).allowMainThreadQueries().build()
        packDao = database.packDao()
        ownedPackDao = database.ownedPackDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a pack carries one name per locale`() = runTest {
        packDao.replaceAll(
            packs = listOf(pack("core", wave = 1)),
            translations = listOf(
                PackTranslationEntity("core", "en", "Core Set"),
                PackTranslationEntity("core", "fr", "Boîte de base"),
            ),
        )

        assertEquals("Boîte de base", packDao.getTranslations("fr").single().name)
        assertEquals("Core Set", packDao.getTranslations("en").single().name)
    }

    @Test
    fun `packs are ordered by wave then position`() = runTest {
        packDao.replaceAll(
            packs = listOf(
                pack("magneto", wave = 8, position = 50),
                pack("core", wave = 1, position = 1),
                pack("gob", wave = 1, position = 2),
            ),
            translations = emptyList(),
        )

        assertEquals(listOf("core", "gob", "magneto"), packDao.getPacks().map { it.code })
    }

    @Test
    fun `replacing packs cascades to translations`() = runTest {
        packDao.replaceAll(
            packs = listOf(pack("core", wave = 1)),
            translations = listOf(PackTranslationEntity("core", "fr", "Boîte de base")),
        )

        packDao.replaceAll(
            packs = listOf(pack("gob", wave = 1)),
            translations = listOf(PackTranslationEntity("gob", "fr", "Le Bouffon vert")),
        )

        val french = packDao.getTranslations("fr")
        assertEquals(listOf("gob"), french.map { it.packCode })
    }

    @Test
    fun `the collection can hold a pack marvelcdb does not know yet`() = runTest {
        // Elektra, Iron Fist and Shadowland are on pre-order and have no
        // pack_code. owned_packs has no foreign key precisely so this works.
        ownedPackDao.upsert(OwnedPackEntity("shadowland", quantity = 1))

        assertEquals(listOf("shadowland"), ownedPackDao.getOwnedCodes())
    }

    @Test
    fun `a second core set is recorded as a quantity`() = runTest {
        ownedPackDao.upsert(OwnedPackEntity("core", quantity = 2))

        assertEquals(2, ownedPackDao.getOwned().single().quantity)
    }

    @Test
    fun `a pack with quantity zero is not owned`() = runTest {
        ownedPackDao.upsert(OwnedPackEntity("core", quantity = 0))

        assertTrue(ownedPackDao.getOwned().isEmpty())
        assertEquals(0, ownedPackDao.countOwned())
    }

    @Test
    fun `replacing the collection drops what is not in the import`() = runTest {
        ownedPackDao.upsertAll(
            listOf(OwnedPackEntity("core", 1), OwnedPackEntity("gob", 1)),
        )

        ownedPackDao.replaceAll(listOf(OwnedPackEntity("magneto", 1)))

        assertEquals(listOf("magneto"), ownedPackDao.getOwnedCodes())
    }

    private fun pack(code: String, wave: Int, position: Int = 1) = PackEntity(
        code = code,
        marvelCdbId = 1,
        position = position,
        available = "2019-11-01",
        known = 10,
        total = 10,
        url = null,
        type = "CORE",
        wave = wave,
    )
}
