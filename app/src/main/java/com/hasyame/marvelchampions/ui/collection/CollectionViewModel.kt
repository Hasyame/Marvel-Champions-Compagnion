package com.hasyame.marvelchampions.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.CollectionRepository
import com.hasyame.marvelchampions.data.repository.PackOwnership
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.model.PackType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Packs of one wave, which is how the collection screen groups them. */
data class WaveGroup(
    val wave: Int,
    val packs: List<PackOwnership>,
)

data class CollectionUiState(
    val waves: List<WaveGroup> = emptyList(),
    val ownedCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    val uiState: StateFlow<CollectionUiState> = preferences.cardLocale.flatMapLatest { locale ->
        repository.observeCollection(locale)
    }.map { collection ->
        CollectionUiState(
            waves = collection
                .groupBy { it.pack.wave }
                .toSortedMap()
                .map { (wave, packs) -> WaveGroup(wave, packs.sortedBy { it.pack.position }) },
            ownedCount = collection.count { it.isOwned },
            totalCount = collection.size,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CollectionUiState(),
    )

    fun setOwned(packCode: String, owned: Boolean) {
        viewModelScope.launch { repository.setOwned(packCode, owned) }
    }

    fun setQuantity(packCode: String, quantity: Int) {
        viewModelScope.launch { repository.setQuantity(packCode, quantity) }
    }

    /** "Select all hero packs" and friends. */
    fun selectAllOfType(type: PackType, owned: Boolean) {
        viewModelScope.launch {
            val codes = uiState.value.waves
                .flatMap { it.packs }
                .filter { PackType.fromName(it.pack.type) == type }
                .map { it.pack.code }
            repository.setOwnedBulk(codes, owned)
        }
    }

    fun selectAll(owned: Boolean) {
        viewModelScope.launch {
            val codes = uiState.value.waves.flatMap { it.packs }.map { it.pack.code }
            repository.setOwnedBulk(codes, owned)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
