package com.hasyame.marvelchampions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.FirstRunInitializer
import com.hasyame.marvelchampions.data.repository.FirstRunOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartupState {
    data object Loading : StartupState

    /**
     * Ready to show the app. [openCollectionFirst] sends a brand new install to
     * the collection screen: an empty collection makes the randomiser useless
     * and the campaign tab unavailable, so asking on day one beats letting the
     * user discover it.
     */
    data class Ready(val openCollectionFirst: Boolean) : StartupState
}

@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val firstRunInitializer: FirstRunInitializer,
) : ViewModel() {

    private val state = MutableStateFlow<StartupState>(StartupState.Loading)
    val startupState: StateFlow<StartupState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val outcome = firstRunInitializer.initialize()
            state.value = StartupState.Ready(
                openCollectionFirst = outcome != FirstRunOutcome.ALREADY_READY,
            )
        }
    }
}
