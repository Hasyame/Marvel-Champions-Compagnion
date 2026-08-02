package com.hasyame.marvelchampions.data.bgg

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hasyame.marvelchampions.data.security.SecretStore
import com.hasyame.marvelchampions.domain.model.BggReportingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.bggStore: DataStore<Preferences> by preferencesDataStore(name = "bgg")

/** What the settings screen shows. Never carries the password. */
data class BggAccountState(
    val username: String = "",
    val isConnected: Boolean = false,
    val mode: BggReportingMode = BggReportingMode.OFF,
)

/**
 * The BoardGameGeek account, if the player has connected one.
 *
 * Kept in its own DataStore rather than with the app's other settings so that
 * disconnecting can drop the whole file, and so a password is never one
 * careless `preferences.asMap()` away from the rest of the app's state.
 *
 * The password is encrypted by [SecretStore] and is only ever read by the code
 * that is about to post a play. Nothing surfaces it to the UI.
 */
@Singleton
class BggAccount @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secretStore: SecretStore,
) {

    val state: Flow<BggAccountState> = context.bggStore.data.map { preferences ->
        BggAccountState(
            username = preferences[KEY_USERNAME].orEmpty(),
            // Connected means both parts are present. A password that no longer
            // decrypts — after a device restore, say — reads as disconnected,
            // which is exactly what it is.
            isConnected = !preferences[KEY_USERNAME].isNullOrBlank() &&
                preferences[KEY_PASSWORD]?.let { secretStore.decrypt(it) } != null,
            mode = BggReportingMode.fromCode(preferences[KEY_MODE]),
        )
    }

    suspend fun currentMode(): BggReportingMode = state.first().mode

    /**
     * Stores credentials. The caller is expected to have verified them first,
     * so that a typo is caught while the player is still looking at the form.
     */
    suspend fun connect(username: String, password: String): Boolean {
        val encrypted = secretStore.encrypt(password) ?: return false
        context.bggStore.edit {
            it[KEY_USERNAME] = username.trim()
            it[KEY_PASSWORD] = encrypted
        }
        return true
    }

    /** Forgets the account entirely, including the reporting mode. */
    suspend fun disconnect() {
        context.bggStore.edit { it.clear() }
    }

    suspend fun setMode(mode: BggReportingMode) {
        context.bggStore.edit { it[KEY_MODE] = mode.code }
    }

    /**
     * The credentials, for the moment a play is posted.
     *
     * Returns null when reporting is off, which makes "off" mean the password
     * is not used rather than merely that a switch is unset.
     */
    suspend fun credentialsForReporting(): Pair<String, String>? {
        val preferences = context.bggStore.data.first()
        if (BggReportingMode.fromCode(preferences[KEY_MODE]) == BggReportingMode.OFF) {
            return null
        }
        val username = preferences[KEY_USERNAME]?.takeIf { it.isNotBlank() } ?: return null
        val password = preferences[KEY_PASSWORD]?.let { secretStore.decrypt(it) } ?: return null
        return username to password
    }

    private companion object {
        val KEY_USERNAME = stringPreferencesKey("bgg_username")
        val KEY_PASSWORD = stringPreferencesKey("bgg_password")
        val KEY_MODE = stringPreferencesKey("bgg_mode")
    }
}
