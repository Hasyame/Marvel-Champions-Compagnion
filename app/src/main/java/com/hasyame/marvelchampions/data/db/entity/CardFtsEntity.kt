package com.hasyame.marvelchampions.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Search index over [CardEntity].
 *
 * FTS4 rather than FTS5: Room's `@Fts4` is first-class and FTS5 is not
 * guaranteed to be present in the SQLite bundled with every supported Android
 * version.
 *
 * This is an **external content** table — it stores no copy of the text, it
 * points at the `cards` rows. Room keeps the two in sync, so nothing here has
 * to be maintained by hand.
 *
 * Accent and case insensitivity is not done here. It is done by
 * `SearchNormalizer` when the `search*` columns are written, and again on the
 * query. That is what lets `strategie` match `Stratégie` with the stock
 * tokeniser.
 */
@Fts4(contentEntity = CardEntity::class)
@Entity(tableName = "cards_fts")
data class CardFtsEntity(
    val searchName: String,
    val searchText: String,
    val searchTraits: String,
)
