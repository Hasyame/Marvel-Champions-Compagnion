package com.hasyame.marvelchampions.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.SavedDeckEntity
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.DeckBuilderRepository
import com.hasyame.marvelchampions.data.repository.DeckRepository
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.deckbuilder.DeckProblem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A deck offered for the roster, with whether it may actually be used. */
data class RosterCandidate(
    val deck: SavedDeckEntity,
    val problems: List<DeckProblem> = emptyList(),
) {
    val isLegal: Boolean get() = problems.isEmpty()
}

data class StartCampaignUiState(
    val templates: List<CampaignTemplate> = emptyList(),
    val candidates: List<RosterCandidate> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class StartCampaignViewModel @Inject constructor(
    private val campaignRepository: CampaignRepository,
    private val deckRepository: DeckRepository,
    private val builderRepository: DeckBuilderRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(StartCampaignUiState())
    val uiState: StateFlow<StartCampaignUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val locale = preferences.currentCardLocale()
            val decks = deckRepository.getDecks()

            state.value = StartCampaignUiState(
                templates = campaignRepository.bundledTemplates(),
                candidates = decks.map { deck ->
                    val rules = builderRepository.heroRules(deck.heroCode, locale)
                    RosterCandidate(
                        deck = deck,
                        // Cards missing from the collection are deliberately
                        // not a problem here: a campaign is about deck
                        // legality, and owning the cards is a separate matter.
                        problems = rules?.let {
                            builderRepository.validate(
                                rules = it,
                                aspects = DeckRepository.parseAspects(deck.aspects),
                                slots = DeckRepository.parseSlots(deck.slots),
                                locale = locale,
                            ).problems
                        }.orEmpty(),
                    )
                },
                isLoading = false,
            )
        }
    }

    fun start(
        template: CampaignTemplate,
        difficulty: String,
        deckIds: List<String>,
        name: String,
        onStarted: (String) -> Unit,
    ) {
        // Illegal decks cannot reach here through the UI; refusing again keeps
        // that true if the screen ever changes.
        val legal = state.value.candidates.filter { it.isLegal }.map { it.deck.id }
        val roster = deckIds.filter { it in legal }
        if (roster.isEmpty()) {
            return
        }
        viewModelScope.launch {
            onStarted(campaignRepository.startRun(template, difficulty, roster, name))
        }
    }
}
