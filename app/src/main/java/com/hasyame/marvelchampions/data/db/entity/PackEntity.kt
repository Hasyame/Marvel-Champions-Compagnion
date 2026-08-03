package com.hasyame.marvelchampions.data.db.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Locale-independent pack data.
 *
 * [type] and [wave] do not come from the API — they come from the curated
 * `assets/pack_metadata.json`. [waveInferred] and [typeManual] record that the
 * value was curated rather than derived, so the collection screen can be honest
 * about it.
 *
 * [known] and [total] are MarvelCDB's own counts: how many cards it has entered
 * versus how many the product contains. A pack with `known < total` is
 * incomplete, which matters for the randomiser.
 */
@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey val code: String,
    val marvelCdbId: Int,
    val position: Int,
    val available: String,
    val known: Int,
    val total: Int,
    val url: String? = null,
    val type: String,
    val wave: Int,
    val waveInferred: Boolean = false,
    val typeManual: Boolean = false,
)

/** A pack's name in one language. */
@Entity(
    tableName = "pack_translations",
    primaryKeys = ["packCode", "locale"],
    foreignKeys = [
        ForeignKey(
            entity = PackEntity::class,
            parentColumns = ["code"],
            childColumns = ["packCode"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("packCode"), Index("locale")],
)
data class PackTranslationEntity(
    val packCode: String,
    val locale: String,
    val name: String,
)

/**
 * The user's collection: one row per owned pack.
 *
 * [quantity] exists because a second Core Set is a real possibility and changes
 * what the randomiser may draw.
 *
 * There is deliberately **no foreign key to [PackEntity]**. Packs that are
 * announced or pre-ordered do not exist in MarvelCDB yet (Elektra, Iron Fist
 * and Shadowland as of 2026-08-01), and the collection has to be able to hold
 * them before the API catches up.
 */
@Entity(tableName = "owned_packs")
@Serializable
data class OwnedPackEntity(
    @PrimaryKey val packCode: String,
    val quantity: Int,
)
