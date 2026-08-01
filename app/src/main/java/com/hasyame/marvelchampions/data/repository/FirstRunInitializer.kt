package com.hasyame.marvelchampions.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's physical collection, pre-seeded on first launch so the app is
 * useful immediately. Every code was resolved against `/api/public/packs/` on
 * 2026-08-01 — see `docs/DATA_SOURCES.md`.
 *
 * Elektra, Iron Fist and Shadowland are deliberately absent: they are announced
 * or on pre-order and MarvelCDB has no pack for them yet, so there is no code to
 * put here. They can be ticked in the collection screen once MarvelCDB adds
 * them.
 */
internal val PRESEEDED_COLLECTION: List<String> = listOf(
    // Core
    "core",
    // Hero packs
    "msm", "magneto", "drs", "wonder_man", "gambit", "deadpool", "hercules",
    // Scenario packs
    "gob", "sm",
    // Campaign boxes
    "fne", "aoa", "gmw", "mts",
)

/** What the app needs to do before showing anything on first launch. */
enum class FirstRunOutcome {
    /** Database was already populated. Go to the normal start destination. */
    ALREADY_READY,

    /** Seeded from assets. Send the user to the collection screen to confirm it. */
    SEEDED,

    /** No bundled seed in this build. The user has to run a sync from Settings. */
    NEEDS_SYNC,
}

/**
 * Populates an empty install from the bundled seed.
 *
 * The collection is pre-filled at the same time, because an empty collection
 * makes the randomiser useless and the campaign tab unavailable — better to
 * offer a starting point the user corrects than an empty screen they have to
 * discover.
 */
@Singleton
class FirstRunInitializer @Inject constructor(
    private val cardDataRepository: CardDataRepository,
    private val collectionRepository: CollectionRepository,
) {

    suspend fun initialize(): FirstRunOutcome {
        val wasEmpty = cardDataRepository.isEmpty()
        if (!wasEmpty) {
            return FirstRunOutcome.ALREADY_READY
        }

        val seeded = cardDataRepository.seedIfEmpty()

        // Only ever pre-seed a collection the user has never touched, so a
        // deliberately emptied collection is not silently refilled.
        if (collectionRepository.isEmpty()) {
            collectionRepository.setOwnedBulk(PRESEEDED_COLLECTION, owned = true)
        }

        return if (seeded) FirstRunOutcome.SEEDED else FirstRunOutcome.NEEDS_SYNC
    }
}
