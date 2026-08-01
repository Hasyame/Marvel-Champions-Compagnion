package com.hasyame.marvelchampions.data.marvelcdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A deck as MarvelCDB returns it. `/decklist/{id}` and `/deck/{id}` share this
 * shape; only `problem` is exclusive to personal decks.
 *
 * [meta] is a **JSON string**, not an object — `"{\"aspect\":\"leadership\"}"` —
 * so it needs a second parse. [DeckMetaDto] is what it decodes to.
 */
@Serializable
data class DeckDto(
    val id: Long,
    val name: String,
    @SerialName("date_creation") val dateCreation: String? = null,
    @SerialName("date_update") val dateUpdate: String? = null,
    @SerialName("description_md") val descriptionMd: String? = null,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("hero_code") val heroCode: String,
    @SerialName("hero_name") val heroName: String,
    /** Card code to quantity. */
    val slots: Map<String, Int> = emptyMap(),
    @SerialName("ignoreDeckLimitSlots") val ignoreDeckLimitSlots: Map<String, Int>? = null,
    val version: String? = null,
    val meta: String? = null,
    val tags: String? = null,
    val problem: String? = null,
)

/** The decoded [DeckDto.meta]. A deck can carry two aspects. */
@Serializable
data class DeckMetaDto(
    val aspect: String? = null,
    val aspect2: String? = null,
) {
    val aspects: List<String> get() = listOfNotNull(aspect, aspect2)
}
