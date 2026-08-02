package com.hasyame.marvelchampions.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User preferences.
 *
 * The **card data language is deliberately separate from the UI language** —
 * reading a card in English while the app is in French is a stated
 * requirement, not an accident.
 */
@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val cardLocale: Flow<CardLocale> = context.dataStore.data.map { preferences ->
        preferences[KEY_CARD_LOCALE]?.let(CardLocale::fromCode) ?: CardLocale.FRENCH
    }

    val lastCardSync: Flow<Long?> = context.dataStore.data.map { it[KEY_LAST_SYNC] }

    /**
     * Ambient music opened from the play screen.
     *
     * A link rather than in-app playback: Spotify needs its own SDK and a
     * signed-in account to play inside another app, and Melodice's playlists
     * are YouTube. Handing the URL to whichever app owns it is both simpler and
     * the only version that respects the user's subscription.
     */
    val musicUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_MUSIC_URL] ?: DEFAULT_MUSIC_URL
    }

    suspend fun currentCardLocale(): CardLocale = cardLocale.first()

    suspend fun setCardLocale(locale: CardLocale) {
        context.dataStore.edit { it[KEY_CARD_LOCALE] = locale.code }
    }

    suspend fun setLastCardSync(epochMillis: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC] = epochMillis }
    }

    suspend fun setMusicUrl(url: String) {
        context.dataStore.edit { preferences ->
            if (url.isBlank()) {
                preferences.remove(KEY_MUSIC_URL)
            } else {
                preferences[KEY_MUSIC_URL] = url.trim()
            }
        }
    }

    companion object {
        /** The Marvel Champions ambient playlist on Spotify. */
        const val DEFAULT_MUSIC_URL: String =
            "https://open.spotify.com/playlist/70oaFf8tEpbWCNcXdDVSfH"

        /** Curated ambient playlists for the game, to pick a different one from. */
        const val MELODICE_URL: String =
            "https://melodice.org/playlist/marvel-champions-the-card-game-2019/"

        private val KEY_CARD_LOCALE = stringPreferencesKey("card_locale")
        private val KEY_LAST_SYNC = longPreferencesKey("last_card_sync")
        private val KEY_MUSIC_URL = stringPreferencesKey("music_url")
    }
}
