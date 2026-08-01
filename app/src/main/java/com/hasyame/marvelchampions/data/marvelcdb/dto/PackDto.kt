package com.hasyame.marvelchampions.data.marvelcdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A pack as MarvelCDB returns it. This is the complete set of fields — note
 * that there is **no type and no wave**; both come from the curated
 * `assets/pack_metadata.json`.
 *
 * [known] is how many cards MarvelCDB has entered, [total] how many the product
 * contains. They differ for most packs, so a pack can be legitimately
 * incomplete.
 */
@Serializable
data class PackDto(
    val id: Int,
    val code: String,
    val name: String,
    val position: Int,
    val available: String,
    val known: Int,
    val total: Int,
    val url: String? = null,
)

/** One entry of the curated `assets/pack_metadata.json`. */
@Serializable
data class PackMetadataDto(
    val code: String,
    val type: String,
    val wave: Int,
    @SerialName("waveInferred") val waveInferred: Boolean = false,
    @SerialName("typeManual") val typeManual: Boolean = false,
)

/** Root of the curated `assets/pack_metadata.json`. */
@Serializable
data class PackMetadataFileDto(
    val schemaVersion: Int,
    val note: String? = null,
    val packs: List<PackMetadataDto>,
)
