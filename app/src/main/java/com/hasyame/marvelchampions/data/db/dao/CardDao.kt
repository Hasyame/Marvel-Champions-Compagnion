package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import kotlinx.coroutines.flow.Flow

/**
 * SQLite's default bound-variable limit is 999 and each card binds well over a
 * hundred columns, so inserts are chunked well below it.
 */
private const val INSERT_CHUNK_SIZE = 200

/** A card set or hero, with its name in the requested locale. */
data class CardSetSummary(
    val code: String,
    val name: String?,
    val packCode: String,
)

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>)

    @Query("DELETE FROM cards WHERE locale = :locale")
    suspend fun deleteLocale(locale: String)

    /**
     * Replaces every card of one locale.
     *
     * Delete and insert happen in one SQLite transaction, so a sync that fails
     * or is cancelled part way leaves the previous data untouched rather than a
     * half-written database.
     */
    @Transaction
    suspend fun replaceLocale(locale: String, cards: List<CardEntity>) {
        deleteLocale(locale)
        cards.chunked(INSERT_CHUNK_SIZE).forEach { insertAll(it) }
    }

    @Query("SELECT COUNT(*) FROM cards WHERE locale = :locale")
    suspend fun countForLocale(locale: String): Int

    @Query("SELECT COUNT(*) FROM cards")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM cards WHERE code = :code AND locale = :locale")
    suspend fun getCard(code: String, locale: String): CardEntity?

    @Query("SELECT * FROM cards WHERE code = :code AND locale = :locale")
    fun observeCard(code: String, locale: String): Flow<CardEntity?>

    @Query(
        """
        SELECT * FROM cards
        WHERE locale = :locale
        ORDER BY packCode, position
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getPage(locale: String, limit: Int, offset: Int): List<CardEntity>

    /**
     * Full text search.
     *
     * [matchQuery] must already be normalised and turned into an FTS
     * expression by `SearchNormalizer.toPrefixMatchQuery`.
     */
    @Query(
        """
        SELECT cards.* FROM cards
        JOIN cards_fts ON cards_fts.rowid = cards.rowid
        WHERE cards_fts MATCH :matchQuery
          AND cards.locale = :locale
        ORDER BY cards.packCode, cards.position
        LIMIT :limit
        """,
    )
    suspend fun search(matchQuery: String, locale: String, limit: Int = 200): List<CardEntity>

    /**
     * Filtered card list. The statement comes from
     * [com.hasyame.marvelchampions.domain.search.CardQueryBuilder], which is
     * the only thing allowed to construct it.
     */
    @RawQuery
    suspend fun queryCards(query: SupportSQLiteQuery): List<CardEntity>

    /** Distinct values for the filter sheet, in the current locale. */
    @Query("SELECT DISTINCT typeCode FROM cards WHERE locale = :locale ORDER BY typeCode")
    suspend fun distinctTypeCodes(locale: String): List<String>

    @Query("SELECT DISTINCT factionCode FROM cards WHERE locale = :locale ORDER BY factionCode")
    suspend fun distinctFactionCodes(locale: String): List<String>

    @Query(
        """
        SELECT DISTINCT traits FROM cards
        WHERE locale = :locale AND traits IS NOT NULL AND traits != ''
        """,
    )
    suspend fun distinctTraitStrings(locale: String): List<String>

    /** Every card of a pack, for the "cards I am missing" view later on. */
    @Query("SELECT * FROM cards WHERE packCode = :packCode AND locale = :locale ORDER BY position")
    suspend fun getPackCards(packCode: String, locale: String): List<CardEntity>

    /**
     * Distinct card sets of one kind (`villain`, `modular`, `hero`), with their
     * localised name and owning pack.
     *
     * This is what lets the randomiser build its pools without a second curated
     * file: which scenarios and modular sets exist is already in the card data.
     */
    @Query(
        """
        SELECT cardSetCode AS code,
               MIN(cardSetName) AS name,
               MIN(packCode) AS packCode
        FROM cards
        WHERE locale = :locale
          AND cardSetTypeNameCode = :setType
          AND cardSetCode IS NOT NULL
        GROUP BY cardSetCode
        ORDER BY name
        """,
    )
    suspend fun getCardSets(setType: String, locale: String): List<CardSetSummary>

    /** Hero identities, which are cards rather than sets. */
    @Query(
        """
        SELECT cardSetCode AS code,
               MIN(name) AS name,
               MIN(packCode) AS packCode
        FROM cards
        WHERE locale = :locale
          AND typeCode = 'hero'
          AND cardSetCode IS NOT NULL
        GROUP BY cardSetCode
        ORDER BY name
        """,
    )
    suspend fun getHeroes(locale: String): List<CardSetSummary>

    /** Cards of a set, used to resolve a scenario's encounter sets. */
    @Query(
        """
        SELECT * FROM cards
        WHERE cardSetCode = :cardSetCode AND locale = :locale
        ORDER BY setPosition, position
        """,
    )
    suspend fun getCardSet(cardSetCode: String, locale: String): List<CardEntity>
}
