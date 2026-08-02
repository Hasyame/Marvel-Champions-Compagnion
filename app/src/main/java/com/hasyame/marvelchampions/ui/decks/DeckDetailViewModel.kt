package com.hasyame.marvelchampions.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.CardSearchRepository
import com.hasyame.marvelchampions.data.repository.DeckContents
import com.hasyame.marvelchampions.data.repository.DeckImportError
import com.hasyame.marvelchampions.data.repository.DeckImportResult
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.model.CardLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A card a campaign granted, resolved for display. */
data class CampaignCardRow(
    val cardCode: String,
    val name: String,
    val campaignName: String,
)

data class DeckDetailUiState(
    val contents: DeckContents? = null,
    val campaignCards: List<CampaignCardRow> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: DeckImportError? = null,
)

@HiltViewModel
class DeckDetailViewModel @Inject constructor(
    private val repository: DeckRepository,
    private val campaignRepository: CampaignRepository,
    private val cardSearchRepository: CardSearchRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(DeckDetailUiState())
    val uiState: StateFlow<DeckDetailUiState> = state.asStateFlow()

    private var deckId: String? = null

    fun load(id: String) {
        deckId = id
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)
            val locale = preferences.currentCardLocale()
            val contents = repository.contents(id, locale)
            state.value = DeckDetailUiState(
                contents = contents,
                campaignCards = campaignCards(id, locale),
                isLoading = false,
            )
        }
    }

    /**
     * Cards campaigns have added to this deck. They are stored on the campaign
     * run, never merged into the deck, so the deck stays exactly as imported.
     */
    private suspend fun campaignCards(deckId: String, locale: CardLocale): List<CampaignCardRow> =
        campaignRepository.campaignCardsForDeck(deckId).map { granted ->
            CampaignCardRow(
                cardCode = granted.cardCode,
                name = cardSearchRepository.getCard(granted.cardCode, locale)?.name
                    ?: granted.cardCode,
                campaignName = granted.campaignName,
            )
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
