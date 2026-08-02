package com.hasyame.marvelchampions.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.data.sync.CardSyncManager
import com.hasyame.marvelchampions.data.sync.CardSyncState
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val syncManager: CardSyncManager,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.cardLocale,
        preferences.lastCardSync,
        syncManager.observeState(),
        preferences.musicUrl,
    ) { locale, lastSync, syncState, musicUrl ->
        SettingsUiState(
            cardLocale = locale,
            lastCardSync = lastSync,
            syncState = syncState,
            musicUrl = musicUrl,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    fun setCardLocale(locale: CardLocale) {
        viewModelScope.launch { preferences.setCardLocale(locale) }
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
