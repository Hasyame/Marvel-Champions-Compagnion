package com.hasyame.marvelchampions.data.seed

import android.content.Context
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A scenario the game has and the card database has never heard of. */
@Serializable
data class CuratedScenarioDto(
    val code: String,
    val packCode: String,
    val modularCountMin: Int = 1,
    val modularCountMax: Int = 1,
    /** Packs its modular sets may come from. Empty means anything owned. */
    val modularPacks: List<String> = emptyList(),
    val names: Map<String, String> = emptyMap(),
)

@Serializable
data class CuratedScenariosFileDto(
    /**
     * Packs whose modular sets belong to their own scenarios.
     *
     * Civil War and She-Hulk share a pool that is only legal in Civil War and
     * She-Hulk games. Without this the app would put Hell's Kitchen into Rhino,
     * which it did.
     */
    val restrictedModularPacks: List<String> = emptyList(),
    val scenarios: List<CuratedScenarioDto> = emptyList(),
)

/**
 * Scenarios that exist in the boxes and not in MarvelCDB.
 *
 * Nineteen of them as of August 2026 — Civil War, She-Hulk, Fear No Evil and
 * Shadowland — because MarvelCDB enters cards as volunteers get to them and the
 * newest boxes lag by months.
 *
 * Only the ones with rules of their own are carried here. The app has no card
 * codes for any of them, so it can name the game to play and cannot show a
 * setup; that is worth it for Civil War, where the alternative is a box the
 * randomiser pretends does not exist.
 */
@Singleton
class CuratedScenarios @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private var cached: CuratedScenariosFileDto? = null

    suspend fun load(): CuratedScenariosFileDto = withContext(ioDispatcher) {
        cached ?: runCatching {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            json.decodeFromString(CuratedScenariosFileDto.serializer(), text)
        }.getOrDefault(CuratedScenariosFileDto()).also { cached = it }
    }

    /** The name to show, falling back to English and then to the code. */
    fun name(scenario: CuratedScenarioDto, locale: CardLocale): String =
        scenario.names[locale.code] ?: scenario.names["en"] ?: scenario.code

    private companion object {
        const val ASSET = "curated_scenarios.json"
    }
}
