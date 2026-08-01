package com.hasyame.marvelchampions.data.seed

import android.content.Context
import com.hasyame.marvelchampions.data.marvelcdb.dto.CardDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackMetadataFileDto
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the card snapshot bundled in `assets/seed/`, so the app is usable
 * offline on first launch.
 *
 * The seed is produced by `./gradlew fetchCardSeed` and is **not committed** —
 * it is Fantasy Flight's card text. A build without it still works; the app
 * falls back to downloading on first sync. That is exactly the state CI builds
 * in, so [cardsAvailable] must be checked rather than assumed.
 */
@Singleton
class CardSeedSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun cardsAvailable(): Boolean = withContext(ioDispatcher) {
        assetExists(cardsAsset(CardLocale.ENGLISH))
    }

    suspend fun readCards(locale: CardLocale): List<CardDto>? =
        readAsset(cardsAsset(locale))

    suspend fun readPacks(locale: CardLocale): List<PackDto>? =
        readAsset(packsAsset(locale))

    /**
     * The curated pack type and wave table. Unlike the card seed this **is**
     * committed: it is our own classification, not MarvelCDB content, and the
     * app is wrong without it.
     */
    suspend fun readPackMetadata(): PackMetadataFileDto = withContext(ioDispatcher) {
        context.assets.open(PACK_METADATA_ASSET).use { stream ->
            @Suppress("OPT_IN_USAGE")
            json.decodeFromStream<PackMetadataFileDto>(stream)
        }
    }

    private suspend inline fun <reified T> readAsset(path: String): T? =
        withContext(ioDispatcher) {
            try {
                context.assets.open(path).use { stream ->
                    @Suppress("OPT_IN_USAGE")
                    json.decodeFromStream<T>(stream)
                }
            } catch (_: FileNotFoundException) {
                null
            }
        }

    private fun assetExists(path: String): Boolean = try {
        context.assets.open(path).close()
        true
    } catch (_: FileNotFoundException) {
        false
    }

    private fun cardsAsset(locale: CardLocale) = "$SEED_DIR/cards_${locale.code}.json"

    private fun packsAsset(locale: CardLocale) = "$SEED_DIR/packs_${locale.code}.json"

    private companion object {
        const val SEED_DIR = "seed"
        const val PACK_METADATA_ASSET = "pack_metadata.json"
    }
}
