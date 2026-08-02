package com.hasyame.marvelchampions.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.CampaignSummary
import com.hasyame.marvelchampions.data.repository.TemplateImportResult
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.TemplateError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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

    /**
     * Runs with their statistics. Folded per run, which is cheap at this scale
     * and means a finished campaign's record cannot drift from its log.
     */
    val summaries: StateFlow<List<CampaignSummary>> = repository.observeRuns()
        .map { repository.summaries() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    val errors: StateFlow<List<TemplateError>> = importErrors
    val message: StateFlow<String?> = importMessage

    /** Holds a template that validated, until difficulty and decks are chosen. */
    private val _importedTemplate = MutableStateFlow<ImportedTemplate?>(null)
    val importedTemplate: StateFlow<ImportedTemplate?> = _importedTemplate

    /** Campaigns bundled into this build, ready to start without importing. */
    private val _available = MutableStateFlow<List<CampaignTemplate>>(emptyList())
    val available: StateFlow<List<CampaignTemplate>> = _available

    init {
        viewModelScope.launch { _available.value = repository.bundledTemplates() }
    }

    fun choose(template: CampaignTemplate) {
        _importedTemplate.value = ImportedTemplate(template)
    }

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

    fun startRun(
        difficulty: String,
        deckIds: List<String>,
        name: String,
        onStarted: (String) -> Unit,
    ) {
        val template = _importedTemplate.value?.template ?: return
        viewModelScope.launch {
            val runId = repository.startRun(template, difficulty, deckIds, name)
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

data class ImportedTemplate(val template: CampaignTemplate)
