package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.RandomizerHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RandomizerHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: RandomizerHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<RandomizerHistoryEntity>)

    @Query("SELECT * FROM randomizer_history ORDER BY createdAt DESC")
    fun observeHistory(): Flow<List<RandomizerHistoryEntity>>

    @Query("SELECT * FROM randomizer_history ORDER BY createdAt DESC")
    suspend fun getHistory(): List<RandomizerHistoryEntity>

    /** Scenarios the user has marked as beaten, for the "exclude beaten" filter. */
    @Query("SELECT DISTINCT scenarioCode FROM randomizer_history WHERE beaten = 1")
    fun observeBeatenScenarios(): Flow<List<String>>

    @Query("UPDATE randomizer_history SET beaten = :beaten WHERE id = :id")
    suspend fun setBeaten(id: String, beaten: Boolean)

    @Query("DELETE FROM randomizer_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM randomizer_history")
    suspend fun clear()
}
