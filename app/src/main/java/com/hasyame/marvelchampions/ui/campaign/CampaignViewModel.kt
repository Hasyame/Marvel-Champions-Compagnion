package com.hasyame.marvelchampions.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.data.repository.TemplateImportResult
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.campaign.template.TemplateError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CampaignListUiState(
    val runs: List<CampaignRunEntity> = emptyList(),
    val importErrors: List<TemplateError> = emptyList(),
    val importMessage: String? = null,
    val pendingTemplateId: String? = null,
)

@HiltViewModel
class CampaignListViewModel @Inject constructor(
    private val repository: CampaignRepository,
) : ViewModel() {

    private val importErrors = MutableStateFlow<List<TemplateError>>(emptyList())
    private val importMessage = MutableStateFlow<String?>(null)

    val runs: StateFlow<List<CampaignRunEntity>> = repository.observeRuns().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    val errors: StateFlow<List<TemplateError>> = importErrors
    val message: StateFlow<String?> = importMessage

    /** Holds a template that validated, until difficulty and decks are chosen. */
    private val _importedTemplate = MutableStateFlow<ImportedTemplate?>(null)
    val importedTemplate: StateFlow<ImportedTemplate?> = _importedTemplate

    fun importTemplate(uri: android.net.Uri) {
        viewModelScope.launch {
            when (val result = repository.importTemplate(uri)) {
                is TemplateImportResult.Success -> {
                    importErrors.value = emptyList()
                    importMessage.value = null
                    _importedTemplate.value = ImportedTemplate(result.template)
                }

                is TemplateImportResult.Invalid -> {
                    importErrors.value = result.errors
                    importMessage.value = null
                }

                is TemplateImportResult.Unreadable -> {
                    importErrors.value = emptyList()
                    importMessage.value = result.message
                }
            }
        }
    }

    fun startRun(difficulty: String, deckIds: List<String>, onStarted: (String) -> Unit) {
        val template = _importedTemplate.value?.template ?: return
        viewModelScope.launch {
            val runId = repository.startRun(template, difficulty, deckIds)
            _importedTemplate.value = null
            onStarted(runId)
        }
    }

    fun deleteRun(runId: String) {
        viewModelScope.launch { repository.deleteRun(runId) }
    }

    fun dismissMessages() {
        importErrors.value = emptyList()
        importMessage.value = null
    }

    fun cancelImport() {
        _importedTemplate.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class ImportedTemplate(
    val template: com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate,
)

data class CampaignRunUiState(
    val run: CampaignRun? = null,
    val elapsedMillis: Long = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class CampaignRunViewModel @Inject constructor(
    private val repository: CampaignRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val state = MutableStateFlow(CampaignRunUiState())
    val uiState: StateFlow<CampaignRunUiState> = state

    private var runId: String? = null

    fun load(id: String) {
        runId = id
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val id = runId ?: return
        val run = repository.load(id, preferences.currentCardLocale())
        state.value = CampaignRunUiState(
            run = run,
            elapsedMillis = run?.timer?.elapsedAt(System.currentTimeMillis()) ?: 0,
            isLoading = false,
        )
    }

    /** Recomputes the displayed time without touching storage. */
    fun tick() {
        val timer = state.value.run?.timer ?: return
        state.value = state.value.copy(elapsedMillis = timer.elapsedAt(System.currentTimeMillis()))
    }

    fun startTimer() = updateTimer { it.start(System.currentTimeMillis()) }

    fun pauseTimer() = updateTimer { it.pause(System.currentTimeMillis()) }

    fun resetTimer() = updateTimer { it.reset() }

    fun setElapsed(millis: Long) = updateTimer { it.setElapsed(millis, System.currentTimeMillis()) }

    private fun updateTimer(transform: (TimerState) -> TimerState) {
        val id = runId ?: return
        val run = state.value.run ?: return
        viewModelScope.launch {
            val next = transform(run.timer)
            repository.updateTimer(id, next, run.state.currentScenarioId)
            reload()
        }
    }

    fun completeScenario(victory: Boolean, answers: AnswerSet) {
        val id = runId ?: return
        val run = state.value.run ?: return
        val scenarioId = run.state.currentScenarioId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.ScenarioCompleted(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    scenarioId = scenarioId,
                    victory = victory,
                    answers = answers,
                    elapsedMillis = run.timer.elapsedAt(System.currentTimeMillis()),
                ),
            )
            repository.updateTimer(id, TimerState(), scenarioId)
            reload()
            state.value.run?.state?.finished?.let { repository.markFinished(id, it) }
        }
    }

    fun purchase(heroId: String, cardCode: String, cost: Int, cardListId: String) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.MarketPurchase(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    heroId = heroId,
                    cardCode = cardCode,
                    cost = cost,
                    cardListId = cardListId,
                ),
            )
            reload()
        }
    }

    fun refund(purchaseEventId: String) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.MarketRefund(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    purchaseEventId = purchaseEventId,
                ),
            )
            reload()
        }
    }

    fun takeSetupAction(actionId: String, heroId: String?) {
        val id = runId ?: return
        val scenarioId = state.value.run?.state?.currentScenarioId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.SetupActionTaken(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    scenarioId = scenarioId,
                    actionId = actionId,
                    heroId = heroId,
                ),
            )
            reload()
        }
    }

    /** Any hand adjustment, logged as such so it never looks like a rules result. */
    fun adjust(counterId: String?, heroId: String?, value: Int?, note: String?) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.ManualAdjustment(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    counterId = counterId,
                    heroId = heroId,
                    value = value,
                    note = note,
                ),
            )
            reload()
        }
    }

    fun revoke(eventId: String, note: String?) {
        val id = runId ?: return
        viewModelScope.launch {
            repository.append(
                id,
                CampaignEvent.EventRevoked(
                    id = repository.newEventId(),
                    timestamp = System.currentTimeMillis(),
                    revokedEventId = eventId,
                    note = note,
                ),
            )
            reload()
        }
    }
}
