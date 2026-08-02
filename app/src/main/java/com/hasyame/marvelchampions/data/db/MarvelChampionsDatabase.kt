package com.hasyame.marvelchampions.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.hasyame.marvelchampions.data.db.dao.CampaignDao
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.dao.OwnedPackDao
import com.hasyame.marvelchampions.data.db.dao.PackDao
import com.hasyame.marvelchampions.data.db.dao.RandomizerHistoryDao
import com.hasyame.marvelchampions.data.db.dao.SavedDeckDao
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.db.entity.CardFtsEntity
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.db.entity.PackTranslationEntity
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity

/**
 * Note that this database holds two very different kinds of data:
 *
 * - **cache** — `cards`, `cards_fts`, `packs`, `pack_translations`. Rebuilt from
 *   MarvelCDB on any device, excluded from backup, never exported.
 * - **user state** — `owned_packs`, and later decks and campaign runs. Owned by
 *   the user and carried between devices in the export bundle.
 *
 * They share a file for now because the cross-device bundle is a separate
 * serialisation concern, not a storage one.
 */
@Database(
    entities = [
        CardEntity::class,
        CardFtsEntity::class,
        PackEntity::class,
        PackTranslationEntity::class,
        OwnedPackEntity::class,
        RandomizerHistoryEntity::class,
        SavedDeckEntity::class,
        CampaignRunEntity::class,
        CampaignEventEntity::class,
    ],
    version = 6,
    exportSchema = true,
    // Both migrations so far only add a table, so Room can generate them from
    // the exported schemas. Anything that alters an existing table needs a
    // handwritten migration instead.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
)
abstract class MarvelChampionsDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun packDao(): PackDao
    abstract fun ownedPackDao(): OwnedPackDao
    abstract fun randomizerHistoryDao(): RandomizerHistoryDao
    abstract fun savedDeckDao(): SavedDeckDao
    abstract fun campaignDao(): CampaignDao

    companion object {
        const val NAME: String = "marvelchampions.db"
    }
}
