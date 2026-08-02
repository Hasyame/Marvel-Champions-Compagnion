package com.hasyame.marvelchampions.ui.campaign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.CampaignRepository
import com.hasyame.marvelchampions.data.repository.CampaignSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CampaignRecordViewModel @Inject constructor(
    private val repository: CampaignRepository,
) : ViewModel() {

    private val state = MutableStateFlow<CampaignSummary?>(null)
    val summary: StateFlow<CampaignSummary?> = state.asStateFlow()

    fun load(runId: String) {
        viewModelScope.launch {
            state.value = repository.summaries().firstOrNull { it.entity.id == runId }
        }
    }
}
