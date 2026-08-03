package com.hasyame.marvelchampions.ui.plays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.db.dao.WinRateRow
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.repository.PlayRecorded
import com.hasyame.marvelchampions.data.repository.PlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaysUiState(
    val plays: List<PlayEntity> = emptyList(),
    val byHero: List<WinRateRow> = emptyList(),
    val byScenario: List<WinRateRow> = emptyList(),
    val byAspect: List<WinRateRow> = emptyList(),
    val byDifficulty: List<WinRateRow> = emptyList(),
) {
    val totalPlayed: Int get() = plays.size
    val totalWon: Int get() = plays.count { it.won }
}

/** A play saved but not yet sent, while the app asks whether to send it. */
data class PendingReport(val playId: String, val summary: String)

@HiltViewModel
class PlaysViewModel @Inject constructor(
    private val repository: PlayRepository,
) : ViewModel() {

    val uiState: StateFlow<PlaysUiState> = combine(
        repository.observePlays(),
        repository.observeByHero(),
        repository.observeByScenario(),
        repository.observeByAspect(),
        repository.observeByDifficulty(),
    ) { plays, byHero, byScenario, byAspect, byDifficulty ->
        PlaysUiState(plays, byHero, byScenario, byAspect, byDifficulty)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlaysUiState(),
    )

    private val pending = MutableStateFlow<PendingReport?>(null)
    val pendingReport: StateFlow<PendingReport?> = pending.asStateFlow()

    private val messages = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = messages.asStateFlow()

    fun record(play: PlayEntity) {
        viewModelScope.launch {
            when (val outcome = repository.record(play)) {
                is PlayRecorded.SavedOnly -> Unit
                is PlayRecorded.SavedAndReported -> messages.value = SENT
                is PlayRecorded.SavedAskToReport ->
                    pending.value = PendingReport(play.id, play.scenarioName)

                is PlayRecorded.SavedReportFailed ->
                    messages.value = "Saved. Not sent to BoardGameGeek: ${outcome.detail}"
            }
        }
    }

    /** The answer to "send this one?" — the play is already saved either way. */
    fun confirmReport(playId: String) {
        pending.value = null
        viewModelScope.launch {
            messages.value = when (val outcome = repository.report(playId)) {
                is PlayRecorded.SavedAndReported -> SENT
                is PlayRecorded.SavedReportFailed ->
                    "Not sent to BoardGameGeek: ${outcome.detail}"

                else -> null
            }
        }
    }

    fun dismissReport() {
        pending.value = null
    }

    fun dismissMessage() {
        messages.value = null
    }

    fun delete(playId: String) {
        viewModelScope.launch { repository.delete(playId) }
    }

    /** Sends a play that was skipped or failed earlier. */
    fun reportLater(playId: String) = confirmReport(playId)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SENT = "Sent to BoardGameGeek."
    }
}
