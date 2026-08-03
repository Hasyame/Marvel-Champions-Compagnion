package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.db.entity.PackTranslationEntity
import kotlinx.coroutines.flow.Flow

/** A pack with its name resolved for one locale. */
data class NamedPack(
    @Embedded val pack: PackEntity,
    val localizedName: String?,
)

@Dao
interface PackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacks(packs: List<PackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslations(translations: List<PackTranslationEntity>)

    @Query("DELETE FROM packs")
    suspend fun deleteAllPacks()

    /**
     * Replaces the whole pack table in one transaction. `pack_translations`
     * cascades on delete, so it does not need clearing separately.
     */
    @Transaction
    suspend fun replaceAll(
        packs: List<PackEntity>,
        translations: List<PackTranslationEntity>,
    ) {
        deleteAllPacks()
        insertPacks(packs)
        insertTranslations(translations)
    }

    @Query("SELECT * FROM packs ORDER BY wave, position")
    fun observePacks(): Flow<List<PackEntity>>

    /**
     * Packs with their name in [locale].
     *
     * A LEFT JOIN so a pack whose translation is missing still appears — the
     * newest packs are not translated for months, and hiding them would be
     * worse than showing an English name.
     */
    @Query(
        """
        SELECT packs.*, pack_translations.name AS localizedName
        FROM packs
        LEFT JOIN pack_translations
          ON pack_translations.packCode = packs.code AND pack_translations.locale = :locale
        ORDER BY packs.wave, packs.position
        """,
    )
    fun observeNamedPacks(locale: String): Flow<List<NamedPack>>

    @Query("SELECT * FROM packs ORDER BY wave, position")
    suspend fun getPacks(): List<PackEntity>

    @Query("SELECT * FROM packs WHERE code = :code")
    suspend fun getPack(code: String): PackEntity?

    @Query("SELECT * FROM pack_translations WHERE locale = :locale")
    suspend fun getTranslations(locale: String): List<PackTranslationEntity>

    @Query("SELECT COUNT(*) FROM packs")
    suspend fun countPacks(): Int
}

@Dao
interface OwnedPackDao {

    @Query("SELECT * FROM owned_packs WHERE quantity > 0")
    fun observeOwned(): Flow<List<OwnedPackEntity>>

    @Query("SELECT * FROM owned_packs WHERE quantity > 0")
    suspend fun getOwned(): List<OwnedPackEntity>

    @Query("SELECT packCode FROM owned_packs WHERE quantity > 0")
    suspend fun getOwnedCodes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ownedPack: OwnedPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(ownedPacks: List<OwnedPackEntity>)

    @Query("DELETE FROM owned_packs WHERE packCode = :packCode")
    suspend fun remove(packCode: String)

    @Query("DELETE FROM owned_packs")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM owned_packs WHERE quantity > 0")
    suspend fun countOwned(): Int

    /** Replaces the whole collection, for the import path. */
    @Transaction
    suspend fun replaceAll(ownedPacks: List<OwnedPackEntity>) {
        clear()
        upsertAll(ownedPacks)
    }

    /** For a restore, which replaces rather than merges. */
    @Query("DELETE FROM owned_packs")
    suspend fun clearOwned()
}
