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

    suspend fun currentCardLocale(): CardLocale = cardLocale.first()

    suspend fun setCardLocale(locale: CardLocale) {
        context.dataStore.edit { it[KEY_CARD_LOCALE] = locale.code }
    }

    suspend fun setLastCardSync(epochMillis: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC] = epochMillis }
    }

    private companion object {
        val KEY_CARD_LOCALE = stringPreferencesKey("card_locale")
        val KEY_LAST_SYNC = longPreferencesKey("last_card_sync")
    }
}
