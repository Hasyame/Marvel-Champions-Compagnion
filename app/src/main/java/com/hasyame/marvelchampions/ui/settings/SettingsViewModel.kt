package com.hasyame.marvelchampions.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.data.sync.CardSyncManager
import com.hasyame.marvelchampions.data.sync.CardSyncState
import com.hasyame.marvelchampions.data.bgg.BggAccount
import com.hasyame.marvelchampions.data.bgg.BggAccountState
import com.hasyame.marvelchampions.data.bgg.BggClient
import com.hasyame.marvelchampions.data.bgg.BggResult
import com.hasyame.marvelchampions.domain.model.BggReportingMode
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.model.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val cardLocale: CardLocale = CardLocale.FRENCH,
    val lastCardSync: Long? = null,
    val syncState: CardSyncState = CardSyncState.Idle,
    val musicUrl: String = AppPreferences.DEFAULT_MUSIC_URL,
    val themeChoice: ThemeChoice = ThemeChoice.DARK,
    val bgg: BggAccountState = BggAccountState(),
    val bggVerifying: Boolean = false,
    val bggError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val bggAccount: BggAccount,
    private val bggClient: BggClient,
    private val syncManager: CardSyncManager,
) : ViewModel() {

    /** Verifying and error are moments, not settings, so they are not persisted. */
    private val bggTransient = MutableStateFlow(BggTransient())

    private val storedSettings = combine(
        preferences.cardLocale,
        preferences.lastCardSync,
        syncManager.observeState(),
        preferences.musicUrl,
        preferences.themeChoice,
    ) { locale, lastSync, syncState, musicUrl, theme ->
        SettingsUiState(
            cardLocale = locale,
            lastCardSync = lastSync,
            syncState = syncState,
            musicUrl = musicUrl,
            themeChoice = theme,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        storedSettings,
        bggAccount.state,
        bggTransient,
    ) { stored, bgg, transient ->
        stored.copy(
            bgg = bgg,
            bggVerifying = transient.verifying,
            bggError = transient.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    /**
     * Checks the credentials against BoardGameGeek before storing them, so a
     * typo is caught while the player is still looking at the form rather than
     * silently failing after a game weeks later.
     */
    fun connectBgg(username: String, password: String) {
        viewModelScope.launch {
            bggTransient.value = BggTransient(verifying = true)
            val result = bggClient.verify(username, password)
            bggTransient.value = when (result) {
                is BggResult.Success -> {
                    if (bggAccount.connect(username, password)) {
                        // Connecting without choosing when to report would do
                        // nothing, so asking is the useful default.
                        bggAccount.setMode(BggReportingMode.ASK)
                        BggTransient()
                    } else {
                        BggTransient(error = "could not store the credentials securely")
                    }
                }

                is BggResult.BadCredentials -> BggTransient(error = "username or password rejected")
                is BggResult.Rejected -> BggTransient(error = "BoardGameGeek refused: ${result.detail}")
                is BggResult.Offline -> BggTransient(error = "could not reach BoardGameGeek: ${result.detail}")
            }
        }
    }

    fun disconnectBgg() {
        viewModelScope.launch {
            bggAccount.disconnect()
            bggTransient.value = BggTransient()
        }
    }

    fun setBggMode(mode: BggReportingMode) {
        viewModelScope.launch { bggAccount.setMode(mode) }
    }

    fun setCardLocale(locale: CardLocale) {
        viewModelScope.launch { preferences.setCardLocale(locale) }
    }

    fun setThemeChoice(choice: ThemeChoice) {
        viewModelScope.launch { preferences.setThemeChoice(choice) }
    }

    fun setMusicUrl(url: String) {
        viewModelScope.launch { preferences.setMusicUrl(url) }
    }

    fun syncCards() = syncManager.start()

    fun cancelSync() = syncManager.cancel()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The parts of the BoardGameGeek form that exist only while it is on screen. */
private data class BggTransient(
    val verifying: Boolean = false,
    val error: String? = null,
)
