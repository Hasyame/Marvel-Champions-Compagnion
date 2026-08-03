package com.hasyame.marvelchampions.data.db.entity

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * One seat at the table: who was played, and with which aspect.
 *
 * A play used to record only `heroCode` plus a comma-separated list of the
 * other heroes' names, and every aspect at the table in one field. That threw
 * away the two things the statistics need. Win rate by hero counted the first
 * player and nobody else, so half a four-player game's heroes never appeared;
 * and hero-with-aspect paired the first hero against every aspect anyone had
 * brought, which invented combinations that were never played.
 *
 * Kept as a list on the play so a seat is a seat: hero and aspect together.
 */
@Serializable
data class PlayHero(
    val code: String,
    val name: String,
    val aspect: String,
)

/**
 * Stores the roster as JSON in a single column.
 *
 * A second table would be the textbook answer, but a roster is only ever read
 * with its play and never queried on its own, so a join buys nothing. JSON
 * rather than another delimited string because hero names are free text and a
 * delimiter in one would silently corrupt the row.
 */
class PlayHeroConverters {

    @TypeConverter
    fun toJson(heroes: List<PlayHero>): String = JSON.encodeToString(heroes)

    @TypeConverter
    fun fromJson(value: String): List<PlayHero> =
        if (value.isBlank()) {
            emptyList()
        } else {
            // A row written by a future version, or a hand-edited backup, must
            // not take the statistics screen down with it.
            runCatching { JSON.decodeFromString<List<PlayHero>>(value) }.getOrDefault(emptyList())
        }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
