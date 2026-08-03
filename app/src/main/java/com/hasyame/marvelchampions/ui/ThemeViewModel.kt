package com.hasyame.marvelchampions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.model.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The theme choice, hoisted to the activity.
 *
 * It has to be read above the theme itself rather than inside Settings: the
 * whole tree is recomposed when it changes, so the value has to be owned by
 * something that outlives the screen that sets it.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    preferences: AppPreferences,
) : ViewModel() {

    val themeChoice: StateFlow<ThemeChoice> = preferences.themeChoice.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Dark from the first frame, so the app never flashes white before the
        // stored preference arrives.
        initialValue = ThemeChoice.DARK,
    )
}
