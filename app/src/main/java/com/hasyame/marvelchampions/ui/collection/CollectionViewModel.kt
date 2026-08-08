package com.hasyame.marvelchampions.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hasyame.marvelchampions.data.repository.CollectionRepository
import com.hasyame.marvelchampions.data.repository.ModularSet
import com.hasyame.marvelchampions.data.repository.PackOwnership
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.domain.model.PackType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    /** The modular sets each pack contains, keyed by pack code. */
    val modularSetsByPack: Map<String, List<ModularSet>> = emptyMap(),
    /** Sets the user has said they cannot field. Absence means owned. */
    val excludedModularSets: Set<String> = emptySet(),
    /** The scenarios each pack contains, keyed by pack code. */
    val scenariosByPack: Map<String, List<ModularSet>> = emptyMap(),
    val excludedScenarios: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    /**
     * Which sets belong to which pack, reloaded when the card language changes.
     *
     * It comes from the card cache rather than the collection, so it is read
     * once per locale instead of being folded into the collection flow.
     */
    private val contentsByPack = preferences.cardLocale.map { locale ->
        repository.modularSetsByPack(locale) to repository.scenariosByPack(locale)
    }

    val uiState: StateFlow<CollectionUiState> = combine(
        preferences.cardLocale.flatMapLatest { locale -> repository.observeCollection(locale) },
        contentsByPack,
        repository.observeExcludedModularSets(),
        repository.observeExcludedScenarios(),
    ) { collection, contents, excludedSets, excludedScenarios ->
        CollectionUiState(
            waves = collection
                .groupBy { it.pack.wave }
                .toSortedMap()
                .map { (wave, packs) -> WaveGroup(wave, packs.sortedBy { it.pack.position }) },
            ownedCount = collection.count { it.isOwned },
            totalCount = collection.size,
            isLoading = false,
            modularSetsByPack = contents.first,
            excludedModularSets = excludedSets,
            scenariosByPack = contents.second,
            excludedScenarios = excludedScenarios,
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

    /**
     * Records that a set inside an owned pack is or is not on the shelf.
     *
     * [owned] is the player's answer to the tick box, so it is inverted here:
     * only what is missing is stored.
     */
    fun setModularSetOwned(setCode: String, owned: Boolean) {
        viewModelScope.launch { repository.setModularSetExcluded(setCode, excluded = !owned) }
    }

    /** Same meaning as a modular set: unticked is "I have not got it". */
    fun setScenarioOwned(scenarioCode: String, owned: Boolean) {
        viewModelScope.launch { repository.setScenarioExcluded(scenarioCode, excluded = !owned) }
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
