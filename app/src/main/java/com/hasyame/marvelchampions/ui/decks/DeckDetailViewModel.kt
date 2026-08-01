package com.hasyame.marvelchampions.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.DeckContents
import com.hasyame.marvelchampions.data.repository.DeckImportError
import com.hasyame.marvelchampions.data.repository.DeckImportResult
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckDetailUiState(
    val contents: DeckContents? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DeckImportError? = null,
)

@HiltViewModel
class DeckDetailViewModel @Inject constructor(
    private val repository: DeckRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(DeckDetailUiState())
    val uiState: StateFlow<DeckDetailUiState> = state.asStateFlow()

    private var deckId: String? = null

    fun load(id: String) {
        deckId = id
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)
            val contents = repository.contents(id, preferences.currentCardLocale())
            state.value = DeckDetailUiState(contents = contents, isLoading = false)
        }
    }

    fun refresh() {
        val id = deckId ?: return
        viewModelScope.launch {
            state.value = state.value.copy(isRefreshing = true, error = null)
            val result = repository.refresh(id)
            val error = (result as? DeckImportResult.Failure)?.error
            val contents = repository.contents(id, preferences.currentCardLocale())
            state.value = DeckDetailUiState(
                contents = contents,
                isLoading = false,
                isRefreshing = false,
                error = error,
            )
        }
    }
}
