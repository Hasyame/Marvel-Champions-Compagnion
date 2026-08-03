package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.FavouriteCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favourite: FavouriteCardEntity)

    @Query("DELETE FROM favourite_cards WHERE cardCode = :cardCode")
    suspend fun remove(cardCode: String)

    @Query("SELECT cardCode FROM favourite_cards")
    fun observeCodes(): Flow<List<String>>

    @Query("SELECT * FROM favourite_cards ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavouriteCardEntity>

    /** For a restore, which replaces rather than merges. */
    @Query("DELETE FROM favourite_cards")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addAll(favourites: List<FavouriteCardEntity>)
}
