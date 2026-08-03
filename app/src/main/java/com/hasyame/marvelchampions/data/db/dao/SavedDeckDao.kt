package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDeckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deck: SavedDeckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(decks: List<SavedDeckEntity>)

    @Query("SELECT * FROM saved_decks ORDER BY name")
    fun observeDecks(): Flow<List<SavedDeckEntity>>

    @Query("SELECT * FROM saved_decks ORDER BY name")
    suspend fun getDecks(): List<SavedDeckEntity>

    @Query("SELECT * FROM saved_decks WHERE id = :id")
    fun observeDeck(id: String): Flow<SavedDeckEntity?>

    @Query("SELECT * FROM saved_decks WHERE id = :id")
    suspend fun getDeck(id: String): SavedDeckEntity?

    @Query("DELETE FROM saved_decks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM saved_decks")
    suspend fun count(): Int

    /** For a restore, which replaces rather than merges. */
    @Query("DELETE FROM saved_decks")
    suspend fun deleteAll()
}
