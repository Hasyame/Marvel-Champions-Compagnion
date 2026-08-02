package com.hasyame.marvelchampions.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import com.hasyame.marvelchampions.data.repository.CardSearchRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardDetailUiState(
    val card: CardEntity? = null,
    val linkedCard: CardEntity? = null,
    /** The pack the card came out of, for the origin section. */
    val pack: PackEntity? = null,
    val locale: CardLocale = CardLocale.FRENCH,
    val isLoading: Boolean = true,
)

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val repository: CardSearchRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(CardDetailUiState())
    val uiState: StateFlow<CardDetailUiState> = state.asStateFlow()

    private var currentCode: String? = null

    fun load(code: String) {
        currentCode = code
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)
            // The language toggle is a per-screen override, so it starts from
            // the stored preference but does not write back to it.
            val locale = state.value.card?.let { state.value.locale }
                ?: preferences.currentCardLocale()
            show(code, locale)
        }
    }

    /**
     * Flips this screen to the other language. Deliberately does not change the
     * stored preference — it is a quick look, not a settings change.
     */
    fun toggleLocale() {
        val code = currentCode ?: return
        val next = if (state.value.locale == CardLocale.FRENCH) {
            CardLocale.ENGLISH
        } else {
            CardLocale.FRENCH
        }
        viewModelScope.launch { show(code, next) }
    }

    private suspend fun show(code: String, locale: CardLocale) {
        val card = repository.getCard(code, locale)
        state.value = CardDetailUiState(
            card = card,
            linkedCard = card?.let { repository.getLinkedCard(it, locale) },
            pack = card?.let { repository.getPack(it.packCode) },
            locale = locale,
            isLoading = false,
        )
    }
}
